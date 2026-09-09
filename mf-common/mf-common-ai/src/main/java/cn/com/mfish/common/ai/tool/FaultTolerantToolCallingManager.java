package cn.com.mfish.common.ai.tool;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 容错工具调用管理器 —— 解决LLM幻觉工具名问题 + 按 Skill 声明顺序排序工具调用
 * <p>
 * <b>功能一：工具名容错</b>
 * 当LLM虚构了不存在的工具名时，返回提示信息给LLM重新选择，而非抛异常中断流程。
 * </p>
 * <p>
 * <b>功能二：按 Skill 声明顺序排序</b>
 * 当LLM在单次响应中返回多个 tool call 时，根据 {@link ToolOrderProvider} 提供的顺序
 * 对 tool calls 排序后执行。顺序来源为 guide 类型 Skill 的 frontmatter {@code toolOrder} 字段。
 * 例如 leave-apply.md 声明 {@code toolOrder: frontend.navigate,demoLeaveApply.add,demoLeaveApply.submit,frontend.refresh}，
 * 即使 LLM 返回 refresh + add + submit，实际执行顺序仍为 add → submit → refresh。
 * </p>
 * <p>
 * <b>排序规则</b>：
 * <ul>
 *   <li>在 toolOrder 列表中的工具，按列表中的顺序排列</li>
 *   <li>不在 toolOrder 列表中的工具，保持 LLM 返回的原相对顺序，排到已声明工具的后面</li>
 *   <li>未注入 ToolOrderProvider 或返回空列表时，不排序，按 LLM 返回顺序执行</li>
 * </ul>
 * </p>
 * <p>
 * <b>循环依赖规避</b>：
 * ToolOrderProvider 通过 setter 注入（非构造器注入），由 {@code CapabilityAutoConfiguration}
 * 在 SkillCapabilityEngine 初始化后调用 {@link #setToolOrderProvider} 设置。
 * 这样 AiConfig 创建 ToolCallingManager 时不触发依赖链：
 * {@code llmModelRouter → openAiChatModel → toolCallingManager} 仅此一条依赖，
 * SkillCapabilityEngine 的依赖链独立解析，不形成环。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/15
 */
@Slf4j
public class FaultTolerantToolCallingManager implements ToolCallingManager {

    private final ToolCallingManager delegate;
    /**
     * 工具执行顺序提供者（可选，未注入时不进行排序）
     * <p>
     * 通过 setter 注入，避免构造器注入形成的循环依赖：
     * {@code llmModelRouter → openAiChatModel → toolCallingManager → skillCapabilityEngine
     * → skillChatClientProviderImpl → llmModelRouter}
     * </p>
     */
    private volatile ToolOrderProvider toolOrderProvider;

    public FaultTolerantToolCallingManager(ToolCallingManager delegate) {
        this(delegate, null);
    }

    public FaultTolerantToolCallingManager(ToolCallingManager delegate, ToolOrderProvider toolOrderProvider) {
        this.delegate = delegate;
        this.toolOrderProvider = toolOrderProvider;
    }

    /**
     * 设置工具执行顺序提供者（setter 注入，用于规避循环依赖）
     * <p>
     * 由 {@code CapabilityAutoConfiguration} 在 SkillCapabilityEngine 初始化后调用，
     * 将 SkillCapabilityEngine（实现了 ToolOrderProvider 接口）设置到此处。
     * </p>
     *
     * @param toolOrderProvider 工具执行顺序提供者
     */
    public void setToolOrderProvider(ToolOrderProvider toolOrderProvider) {
        this.toolOrderProvider = toolOrderProvider;
        log.info("[FaultTolerantToolCallingManager] ToolOrderProvider 已注入，启用 Skill 声明顺序排序");
    }

    @Override
    public @NonNull List<ToolDefinition> resolveToolDefinitions(@NonNull ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    /**
     * 执行工具调用
     * <p>
     * 1. 检测 tool call 数量，若 >1 且有顺序声明，按 Skill 声明顺序排序
     * 2. 捕获工具不存在异常，返回提示信息让LLM重新选择
     * 3. 捕获工具执行异常，返回错误信息给LLM而非中断流程
     * </p>
     */
    @Override
    public @NonNull ToolExecutionResult executeToolCalls(@NonNull Prompt prompt, @NonNull ChatResponse chatResponse) {
        List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(chatResponse);

        // 多个 tool call 且有顺序声明时，按 Skill 声明顺序排序
        ToolOrderProvider provider = this.toolOrderProvider;
        if (toolCalls.size() > 1 && provider != null) {
            List<String> declaredOrder = provider.getToolOrder();
            if (!declaredOrder.isEmpty()) {
                ChatResponse reorderedResponse = reorderToolCallsByDeclaredOrder(chatResponse, toolCalls, declaredOrder);
                return delegateWithFallback(prompt, reorderedResponse);
            }
        }

        return delegateWithFallback(prompt, chatResponse);
    }

    /**
     * 委托执行并处理异常（工具不存在/执行异常）
     */
    private ToolExecutionResult delegateWithFallback(Prompt prompt, ChatResponse chatResponse) {
        try {
            return delegate.executeToolCalls(prompt, chatResponse);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("No ToolCallback found for tool name")) {
                log.warn("[FaultTolerantToolCallingManager] LLM 虚构了不存在的工具: {}", e.getMessage());
                return buildToolNotFoundResult(prompt, chatResponse, e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            log.warn("[FaultTolerantToolCallingManager] 工具执行异常，返回错误信息给LLM: {}", e.getMessage());
            return buildErrorResult(prompt, chatResponse, e.getMessage());
        }
    }

    /**
     * 按 Skill 声明顺序对 tool calls 排序
     * <p>
     * 排序规则：
     * <ol>
     *   <li>在 declaredOrder 中的工具，按 declaredOrder 的顺序排列</li>
     *   <li>不在 declaredOrder 中的工具，保持 LLM 返回的原相对顺序，排到已声明工具的后面</li>
     * </ol>
     * </p>
     * <p>
     * 使用稳定排序：构建 工具名→声明顺序索引 的映射，未声明的工具索引为 Integer.MAX_VALUE，
     * 保持原顺序。相同索引的工具保持原相对顺序（稳定排序）。
     * </p>
     */
    private ChatResponse reorderToolCallsByDeclaredOrder(ChatResponse original,
                                                          List<AssistantMessage.ToolCall> toolCalls,
                                                          List<String> declaredOrder) {
        // 构建工具名→声明顺序索引的映射
        Map<String, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < declaredOrder.size(); i++) {
            String toolName = declaredOrder.get(i);
            if (!orderIndex.containsKey(toolName)) {
                orderIndex.put(toolName, i);
            }
        }

        // 按 declaredOrder 索引稳定排序（未声明的工具索引为 MAX_VALUE，保持原顺序）
        List<AssistantMessage.ToolCall> reordered = new ArrayList<>(toolCalls);
        reordered.sort((a, b) -> {
            int idxA = orderIndex.getOrDefault(a.name(), Integer.MAX_VALUE);
            int idxB = orderIndex.getOrDefault(b.name(), Integer.MAX_VALUE);
            return Integer.compare(idxA, idxB);
        });

        // 验证顺序是否发生变化
        boolean changed = false;
        for (int i = 0; i < toolCalls.size(); i++) {
            if (!toolCalls.get(i).id().equals(reordered.get(i).id())) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            return original;
        }

        log.info("[FaultTolerantToolCallingManager] 按 Skill 声明顺序重排序: {} → {}",
                toolCalls.stream().map(AssistantMessage.ToolCall::name).collect(Collectors.joining(" → ")),
                reordered.stream().map(AssistantMessage.ToolCall::name).collect(Collectors.joining(" → ")));

        // 构造新的 ChatResponse
        Generation originalGen = original.getResults().stream()
                .filter(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
                .findFirst()
                .orElseThrow();

        AssistantMessage originalMsg = originalGen.getOutput();
        AssistantMessage newMsg = AssistantMessage.builder()
                .content(originalMsg.getText())
                .properties(originalMsg.getMetadata())
                .toolCalls(reordered)
                .build();

        Generation newGen = new Generation(newMsg, ChatGenerationMetadata.NULL);
        return new ChatResponse(List.of(newGen), original.getMetadata());
    }

    /**
     * 构造"工具不存在"的结果，返回可用工具列表让LLM重新选择
     */
    private ToolExecutionResult buildToolNotFoundResult(Prompt prompt, ChatResponse chatResponse,
                                                        String errorMessage) {
        String toolName = extractToolNameFromError(errorMessage);
        String availableTools = getAvailableToolNames(prompt);
        String hint = String.format(
                "工具 [%s] 不存在。可用工具列表: [%s]。请从上述可用工具中选择一个最合适的工具重新调用。" +
                        "不要虚构工具名，只能使用上述列表中的工具。",
                toolName, availableTools);
        return buildToolResponseResult(prompt, chatResponse, toolName, hint);
    }

    /**
     * 构造通用错误结果，返回错误信息给LLM
     */
    private ToolExecutionResult buildErrorResult(Prompt prompt, ChatResponse chatResponse,
                                                 String errorMessage) {
        List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(chatResponse);
        String toolName = toolCalls.isEmpty() ? "unknown" : toolCalls.getFirst().name();
        String hint = String.format("工具 [%s] 执行失败，错误信息: %s。请根据错误信息调整参数后重试，或选择其他工具。",
                toolName, errorMessage);
        return buildToolResponseResult(prompt, chatResponse, toolName, hint);
    }

    /**
     * 构造工具响应结果（把提示信息作为 ToolResponseMessage 返回给LLM）
     */
    private ToolExecutionResult buildToolResponseResult(Prompt prompt, ChatResponse chatResponse,
                                                        String toolName, String responseData) {
        List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(chatResponse);

        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            responses.add(new ToolResponseMessage.ToolResponse(
                    toolCall.id(),
                    toolCall.name(),
                    toolCall.name().equals(toolName) ? responseData : "工具执行完成"
            ));
        }

        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(responses)
                .build();

        List<Message> conversationHistory = new ArrayList<>(prompt.getInstructions());
        chatResponse.getResults().stream()
                .map(Generation::getOutput)
                .filter(msg -> !CollectionUtils.isEmpty(msg.getToolCalls()))
                .findFirst().ifPresent(conversationHistory::add);
        conversationHistory.add(toolResponseMessage);

        return ToolExecutionResult.builder()
                .conversationHistory(conversationHistory)
                .returnDirect(false)
                .build();
    }

    private String extractToolNameFromError(String errorMessage) {
        int idx = errorMessage.lastIndexOf(':');
        if (idx > 0 && idx < errorMessage.length() - 1) {
            return errorMessage.substring(idx + 1).trim();
        }
        return "unknown";
    }

    private String getAvailableToolNames(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions chatOptions
                && !CollectionUtils.isEmpty(chatOptions.getToolCallbacks())) {
            return chatOptions.getToolCallbacks().stream()
                    .map(tc -> tc.getToolDefinition().name())
                    .collect(Collectors.joining(", "));
        }
        return "";
    }

    private List<AssistantMessage.ToolCall> extractToolCalls(ChatResponse chatResponse) {
        return chatResponse.getResults().stream()
                .map(Generation::getOutput)
                .filter(msg -> !CollectionUtils.isEmpty(msg.getToolCalls()))
                .flatMap(msg -> msg.getToolCalls().stream())
                .toList();
    }

    /**
     * 创建默认配置的容错 ToolCallingManager（不排序）
     */
    public static FaultTolerantToolCallingManager createDefault() {
        return new FaultTolerantToolCallingManager(DefaultToolCallingManager.builder().build());
    }
}
