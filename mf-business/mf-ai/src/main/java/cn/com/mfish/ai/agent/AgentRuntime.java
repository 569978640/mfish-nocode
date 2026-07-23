package cn.com.mfish.ai.agent;

import cn.com.mfish.ai.service.FileParseService;
import cn.com.mfish.ai.service.LlmModelRouter;
import cn.com.mfish.common.ai.agent.EventBus;
import cn.com.mfish.common.ai.agent.TenantContext;
import cn.com.mfish.common.ai.entity.AgentPlan;
import cn.com.mfish.common.ai.entity.AiRequest;
import cn.com.mfish.common.ai.entity.ChatResponseVo;
import cn.com.mfish.common.ai.entity.EventType;
import cn.com.mfish.common.ai.entity.PlanStep;
import cn.com.mfish.common.ai.memory.ConversationMemory;
import cn.com.mfish.common.ai.memory.ConversationMemoryStore;
import cn.com.mfish.common.ai.memory.DocumentChunk;
import cn.com.mfish.common.core.utils.AuthInfoUtils;
import cn.com.mfish.common.core.utils.ServletUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 智能体编排核心
 * <p>
 * 实现 Plan → Execute → Emit 的完整编排流程：
 * <ol>
 *   <li>调用 {@link Planner} 拆解用户需求为 {@link AgentPlan}</li>
 *   <li>推送 {@link EventType#PLAN_CREATED} 事件</li>
 *   <li>串行执行每个 {@link PlanStep}，通过 {@link Executor} 调用工具</li>
 *   <li>每步推送 {@link EventType#STEP_STARTED}/{@link EventType#TOKEN_STREAM}/{@link EventType#STEP_COMPLETED}</li>
 *   <li>全部完成后推送 {@link EventType#PLAN_COMPLETED} 并关闭事件流</li>
 * </ol>
 * </p>
 * <p>
 * 返回 {@code Flux<ChatResponseVo>} 供 SSE Controller 直接订阅，
 * 与普通聊天返回结构一致，前端按同一协议解析。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/17
 */
@Slf4j
@Component
public class AgentRuntime {

    private final Planner planner;
    private final Executor executor;
    private final LlmModelRouter llmModelRouter;
    private final FileParseService fileParseService;
    private final ConversationMemoryStore memoryStore;

    public AgentRuntime(Planner planner, Executor executor, LlmModelRouter llmModelRouter,
                        FileParseService fileParseService, ConversationMemoryStore memoryStore) {
        this.planner = planner;
        this.executor = executor;
        this.llmModelRouter = llmModelRouter;
        this.fileParseService = fileParseService;
        this.memoryStore = memoryStore;
    }

    /**
     * 运行智能体编排
     * <p>
     * 重构后流程（接入 Memory 模块）：
     * <ol>
     *   <li>获取（或创建）sessionId 对应的 {@link ConversationMemory}</li>
     *   <li>在请求线程捕获 TenantContext 并绑定到 Memory 的 Vars Context</li>
     *   <li>若请求携带 fileIds：在请求线程解析文件为 DocumentChunk 列表，
     *       注入到 Memory 的 Document Context</li>
     *   <li>从 Memory 读取 Document Context 拼接文本，注入到用户 prompt 前</li>
     *   <li>后台启动 Plan → Execute 流程</li>
     * </ol>
     * </p>
     * <p>
     * 关键点：必须在请求线程完成 Memory 写入（TenantContext 绑定 + 文件解析），
     * 因为 AuthInfoUtils 和 Feign BearerTokenInterceptor 都依赖 RequestContextHolder，
     * 切到 boundedElastic 异步线程后拿不到请求上下文。
     * </p>
     * <p>
     * 所有返回的 {@link ChatResponseVo} 的 id 字段填充为 {@link AiRequest#getId()}，
     * 与普通聊天返回结构保持一致，前端可按 id 关联请求与响应。
     * </p>
     *
     * @param aiRequest AI请求参数（含 id/sessionId/message/fileIds）
     * @return 聊天响应流（与普通聊天统一结构）
     */
    public Flux<ChatResponseVo> run(AiRequest aiRequest) {
        String requestId = aiRequest.getId();
        String sessionId = aiRequest.getSessionId();
        String prompt = aiRequest.getMessage() != null ? aiRequest.getMessage().getContent() : null;
        EventBus eventBus = new EventBus(requestId);

        // 获取（或创建）会话 Memory
        ConversationMemory memory = memoryStore.getOrCreate(sessionId);

        // 在请求线程捕获租户上下文并绑定到 Memory 的 Vars Context
        TenantContext tenantContext = captureTenantContext();
        memory.bindTenantContext(tenantContext);

        // 文件解析 + 注入 Memory 的 Document Context（必须在请求线程执行）
        List<DocumentChunk> chunks = fileParseService.loadAsChunks(aiRequest.getFileIds());
        if (!chunks.isEmpty()) {
            memory.addDocumentChunks(chunks);
        }

        // Document Context 的拼接交由 Planner 统一处理：
        // Planner.plan() 会从 Memory 读取 getSystemContext()（含 Vars + Document）拼接到 prompt 前。
        // AgentRuntime 只负责 Memory 写入（文件解析 + 租户绑定），不再拼接 DocumentContext 到 prompt，
        // 避免与 Planner 重复拼接。

        // 后台启动编排流程
        runOrchestration(sessionId, prompt, eventBus, tenantContext);

        // 返回事件流供前端订阅
        return eventBus.asFlux();
    }

    /**
     * 在请求线程捕获租户上下文快照
     * <p>
     * 包含 tenantId/userId/accessToken/RequestAttributes/ServerWebExchange，
     * 供异步线程构建 ToolContext 和 ChatClient。
     * </p>
     */
    private TenantContext captureTenantContext() {
        String tenantId = llmModelRouter.currentTenantId();
        String userId = AuthInfoUtils.getCurrentUserId();
        String accessToken = AuthInfoUtils.getAccessToken();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        ServerWebExchange serverWebExchange = ServletUtils.getExchange();
        return new TenantContext(tenantId, userId, accessToken, requestAttributes, serverWebExchange);
    }

    /**
     * 后台执行编排流程
     */
    private void runOrchestration(String sessionId, String prompt, EventBus eventBus, TenantContext tenantContext) {
        planner.plan(sessionId, prompt, tenantContext)
                .doOnNext(plan -> {
                    log.info("[AgentRuntime] 计划生成完成, 步骤数={}", plan.getSteps() != null ? plan.getSteps().size() : 0);
                    eventBus.emit(EventType.PLAN_CREATED, formatPlanSummary(plan));
                })
                .flatMap(plan -> executeAllSteps(plan, sessionId, eventBus, tenantContext))
                .doOnSuccess(finalResult -> {
                    log.info("[AgentRuntime] 编排完成");
                    eventBus.emit(ChatResponseVo.ofEvent(EventType.PLAN_COMPLETED, finalResult)
                            .setFinishReason("STOP"));
                    eventBus.complete();
                })
                .doOnError(e -> {
                    log.error("[AgentRuntime] 编排失败", e);
                    eventBus.emitError("编排失败: " + e.getMessage());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * 串行执行所有步骤
     * <p>
     * 每步执行完成后，结果作为下一步的上下文。
     * 任意步骤失败不会中断整体流程（Executor 内部已捕获异常）。
     * </p>
     *
     * @return 最终汇总结果
     */
    private Mono<String> executeAllSteps(AgentPlan plan, String sessionId, EventBus eventBus, TenantContext tenantContext) {
        List<PlanStep> steps = plan.getSteps();
        if (steps == null || steps.isEmpty()) {
            return Mono.just("无可执行步骤");
        }

        // 串行链式执行：step0 → step1 → ... → stepN
        Mono<String> chain = Mono.just("");
        for (int i = 0; i < steps.size(); i++) {
            final int index = i;
            final PlanStep step = steps.get(i);
            chain = chain.flatMap(prevResult ->
                    executor.execute(step, index, sessionId, plan, eventBus, tenantContext)
            );
        }

        // 全部步骤完成后生成汇总
        return chain.map(lastResult -> {
            StringBuilder summary = new StringBuilder();
            summary.append("共执行 ").append(steps.size()).append(" 个步骤：\n\n");
            for (int i = 0; i < steps.size(); i++) {
                PlanStep s = steps.get(i);
                summary.append("步骤").append(i + 1).append("：").append(s.getDescription());
                if ("COMPLETED".equals(s.getStatus())) {
                    summary.append(" [完成]");
                } else if ("FAILED".equals(s.getStatus())) {
                    summary.append(" [失败]");
                }
                summary.append("\n");
            }
            summary.append("\n").append(lastResult);
            return summary.toString();
        });
    }

    /**
     * 格式化计划摘要供前端展示
     */
    private String formatPlanSummary(AgentPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(plan.getSummary() != null ? plan.getSummary() : "").append("\n");
        if (plan.getSteps() != null) {
            for (int i = 0; i < plan.getSteps().size(); i++) {
                PlanStep step = plan.getSteps().get(i);
                sb.append(i + 1).append(". ").append(step.getDescription()).append("\n");
            }
        }
        return sb.toString();
    }
}
