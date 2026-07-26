package cn.com.mfish.ai.agent;

import cn.com.mfish.common.ai.agent.EventBus;
import cn.com.mfish.common.ai.agent.TenantContext;
import cn.com.mfish.common.ai.agent.ToolCapable;
import cn.com.mfish.common.ai.entity.AgentPlan;
import cn.com.mfish.common.ai.entity.EventType;
import cn.com.mfish.common.ai.entity.PlanStep;
import cn.com.mfish.common.core.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 步骤执行器
 * <p>
 * 按 {@link PlanStep} 逐个执行，每步：
 * <ol>
 *   <li>从步骤描述构建提示词</li>
 *   <li>通过 {@link ToolCapable} 调用 LLM+工具（Spring AI 内部处理工具循环）</li>
 *   <li>流式 token 推送 {@link EventType#TOKEN_STREAM} 事件</li>
 *   <li>收集完整结果，推送 {@link EventType#STEP_COMPLETED} 事件</li>
 * </ol>
 * </p>
 * <p>
 * 每步的返回结果会作为下一步的上下文，形成"多轮调用"的链式上下文。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/17
 */
@Slf4j
@Component
public class Executor {

    private final ToolCapable toolCapable;

    public Executor(@Qualifier("agentAssistant") ToolCapable toolCapable) {
        this.toolCapable = toolCapable;
    }

    /**
     * 执行单个步骤
     *
     * @param step           要执行的步骤
     * @param stepIndex      步骤索引（从 0 开始）
     * @param sessionId      会话ID
     * @param plan           整体计划（用于构建上下文）
     * @param eventBus       事件总线
     * @param tenantContext  请求线程捕获的租户上下文快照
     * @return 步骤执行结果文本（用于后续步骤上下文）
     */
    public Mono<String> execute(PlanStep step, int stepIndex, String sessionId,
                                AgentPlan plan, EventBus eventBus, TenantContext tenantContext) {
        // 标记步骤开始
        step.setStatus("RUNNING");
        eventBus.emit(EventType.STEP_STARTED, stepIndex, step.getDescription());

        // 构建提示词：包含原始需求和当前步骤描述
        String prompt = buildStepPrompt(step, stepIndex, plan);

        StringBuilder resultBuilder = new StringBuilder();

        // serviceIds 可能为 null，做兜底
        Set<String> serviceIdSet = step.getServiceIds() != null
                ? new HashSet<>(step.getServiceIds())
                : new HashSet<>();

        // 传入租户上下文，避免异步线程拿不到 RequestAttributes
        return toolCapable.chatWithTools(sessionId, prompt, serviceIdSet, tenantContext)
                .doOnNext(chatResponse -> {
                    String token = extractText(chatResponse);
                    if (StringUtils.isNotEmpty(token)) {
                        resultBuilder.append(token);
                        eventBus.emit(EventType.TOKEN_STREAM, stepIndex, token);
                    }
                })
                .filter(this::isFinished)
                .next()  // 取第一个 finish 信号
                .map(chatResponse -> {
                    String result = resultBuilder.toString();
                    step.setStatus("COMPLETED").setResult(result);
                    String summary = result.length() > 200 ? result.substring(0, 200) + "..." : result;
                    eventBus.emit(EventType.STEP_COMPLETED, stepIndex, summary);
                    return result;
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    // 没有收到 finish 信号，用已收集的结果
                    String result = resultBuilder.toString();
                    step.setStatus("COMPLETED").setResult(result);
                    if (StringUtils.isNotEmpty(result)) {
                        String summary = result.length() > 200 ? result.substring(0, 200) + "..." : result;
                        eventBus.emit(EventType.STEP_COMPLETED, stepIndex, summary);
                    }
                    return result;
                }))
                .onErrorResume(ex -> {
                    log.error("[Executor] 步骤{}执行失败", stepIndex, ex);
                    step.setStatus("FAILED").setResult(ex.getMessage());
                    eventBus.emit(EventType.ERROR, stepIndex, "步骤" + (stepIndex + 1) + "执行失败: " + ex.getMessage());
                    return Mono.just("");
                });
    }

    /**
     * 构建步骤提示词
     * <p>
     * 把原始需求和当前步骤描述组合，让 LLM 理解这一步要做什么。
     * 强化指导：必须实际调用工具，不得仅描述步骤或声称"无法调用"。
     * </p>
     */
    private String buildStepPrompt(PlanStep step, int stepIndex, AgentPlan plan) {
        return "原始需求：" + plan.getOriginalPrompt() + "\n\n" +
                "当前是第 " + (stepIndex + 1) + " 步，共 " + plan.getSteps().size() + " 步。\n" +
                "这一步的任务：" + step.getDescription() + "\n\n" +
                """
                # 执行要求（必须严格遵守）
                1. **必须实际调用工具**：你已被注入了完成本步骤所需的全部工具，工具列表已在上方列出。必须从中选择匹配的工具并实际调用，不得仅描述"建议调用"或"需要调用"而不执行。
                2. **工具名格式**：工具名采用 `controllerName.operationId` 格式（如 `demoLeaveApply.add`、`demoLeaveApply.submit`）。请从上方工具列表中找到名称完全匹配的工具调用，不要虚构或猜测工具名。
                3. **guide 类型 Skill**：如果工具列表中包含 `skill.{code}` 开头的工具，请优先调用它获取操作指南，然后严格按指南中的步骤调用对应的业务工具（如 `demoLeaveApply.add`）完成实际操作。
                4. **禁止虚假执行**：严禁出现"我无法直接调用"、"请您手动执行"、"请在后台系统中操作"等表述。工具已就绪，你完全可以调用。
                5. **结果反馈**：调用工具后，基于工具返回的真实数据给出这一步的结论。如果工具返回失败，说明失败原因。
                """;
    }

    /**
     * 从 ChatResponse 提取文本
     */
    private String extractText(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null) {
            return "";
        }
        return Objects.requireNonNull(chatResponse.getResult().getOutput()).getText();
    }

    /**
     * 判断是否完成（finish_reason=stop）
     */
    private boolean isFinished(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null) {
            return false;
        } else {
            chatResponse.getResult();
        }
        return "STOP".equals(chatResponse.getResult().getMetadata().getFinishReason());
    }
}
