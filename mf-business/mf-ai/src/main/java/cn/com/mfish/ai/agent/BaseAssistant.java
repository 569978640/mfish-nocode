package cn.com.mfish.ai.agent;

import cn.com.mfish.ai.service.FileParseService;
import cn.com.mfish.ai.service.LlmModelRouter;
import cn.com.mfish.common.ai.client.IClientAssistant;
import cn.com.mfish.common.ai.engine.ApiToolEngine;
import cn.com.mfish.common.ai.entity.AiRequest;
import cn.com.mfish.common.ai.entity.ChatResponseVo;
import cn.com.mfish.common.ai.agent.ToolCapable;
import cn.com.mfish.common.ai.capability.SkillCapabilityEngine;
import cn.com.mfish.common.ai.memory.ConversationMemory;
import cn.com.mfish.common.ai.memory.ConversationMemoryStore;
import cn.com.mfish.common.core.constants.RPCConstants;
import cn.com.mfish.common.core.constants.ServiceConstants;
import cn.com.mfish.common.core.utils.AuthInfoUtils;
import cn.com.mfish.common.core.utils.ServletUtils;
import cn.com.mfish.common.core.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * @description: 助手基类
 * <p>
 * 按租户路由模型：每次请求实时解析租户、从 LlmModelRouter 取该租户的ChatModel
 * （ChatModel按配置签名缓存共享，不随租户增长），并实时构建ChatClient。
 * 不缓存ChatClient，避免为每个租户常驻一份系统提示词+advisor带来的内存膨胀。
 * <p>
 * 线程安全：ChatClient每次请求独立构建、用完即弃，无共享可变状态；
 * 模型在请求线程解析（AuthInfoUtils不支持异步），构建后的引用可安全用于reactive链。
 *
 * @author: mfish
 * @date: 2025/8/22
 */
@Slf4j
public abstract class BaseAssistant implements IClientAssistant, ToolCapable {
    private final ChatMemory chatMemory;
    private final LlmModelRouter llmModelRouter;
    protected final ApiToolEngine apiToolEngine;

    /**
     * 文件解析服务：用于根据AiRequest.fileIds获取文件内容并注入提示词
     * 采用字段注入避免修改所有子类构造函数
     */
    @Autowired
    protected FileParseService fileParseService;

    /**
     * 会话记忆存储：三驾马车 Memory 模块的入口
     * <p>
     * 用于按 sessionId 获取 {@link ConversationMemory}，将文件解析结果、租户上下文、
     * 业务变量等统一收纳到 Memory，供后续 Planner 拼接上下文。
     * </p>
     * <p>
     * 字段注入避免修改所有子类构造函数。
     * </p>
     */
    @Autowired
    protected ConversationMemoryStore memoryStore;

    /**
     * Skill 能力引擎：用于查询 guide 类型 Skill 声明的依赖服务（requires）
     * <p>
     * chat 模式下，垂直助手通过 chatWithToolsAndExtensions 聚合主服务+扩展工具，
     * 但扩展工具仅含 skill-* 和 mcp-*，不含 guide skill 引用的业务微服务（如 mf-demo）。
     * 通过注入此引擎，读取扩展集合中 guide skill 的 requires 并合并到 serviceIds，
     * 使 LLM 在垂直助手下也能调用指南引用的业务工具。
     * </p>
     * <p>
     * required=false：避免在没有 SkillCapabilityEngine 的环境（如未启用 Skill）启动失败。
     * </p>
     */
    @Autowired(required = false)
    protected SkillCapabilityEngine skillCapabilityEngine;

    public BaseAssistant(ChatMemory chatMemory, LlmModelRouter llmModelRouter, ApiToolEngine apiToolEngine) {
        this.llmModelRouter = llmModelRouter;
        this.chatMemory = chatMemory;
        this.apiToolEngine = apiToolEngine;
    }

    /**
     * 子类提供系统提示词
     */
    protected abstract String getSystemPrompt();

    /**
     * 实时构建当前请求租户对应的ChatClient：从路由取该租户ChatModel，包装系统提示词与advisor。
     * 不缓存——ChatClient构建本身轻量（仅包装ChatModel与提示词字符串，不重建HTTP客户端），
     * 实时构建可避免为每个租户常驻一份提示词内存。必须在请求线程调用（AuthInfoUtils不支持异步）。
     */
    protected ChatClient getChatClient() {
        String tenantId = llmModelRouter.currentTenantId();
        return getChatClient(tenantId);
    }

    /**
     * 按指定租户ID构建 ChatClient
     * <p>
     * 用于异步编排场景：请求线程捕获租户ID后传入，避免在异步线程调用 currentTenantId() 失败。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 该租户对应的 ChatClient
     */
    protected ChatClient getChatClient(String tenantId) {
        return ChatClient.builder(llmModelRouter.getChatModel(tenantId))
                .defaultSystem(getSystemPrompt())
                .defaultAdvisors(new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 构建工具上下文：捕获当前请求的认证信息和请求上下文（双栈兼容 Servlet + WebFlux）
     * <p>
     * 供FeignToolCallback在工具执行线程恢复请求上下文，使BearerTokenInterceptor和AuthInfoUtils正常工作。
     *
     * @return 工具上下文Map
     */
    protected Map<String, Object> buildToolContext() {
        String userId = AuthInfoUtils.getCurrentUserId();
        String tenantId = AuthInfoUtils.getCurrentTenantId();
        // 在请求线程捕获 token，供 HttpToolCallback/FeignToolCallback 在工具执行线程复用
        // token 不带 "Bearer " 前缀，由各 ToolCallback 在注入 header 时按需添加
        String accessToken = AuthInfoUtils.getAccessToken();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        ServerWebExchange serverWebExchange = ServletUtils.getExchange();
        return buildToolContextFromSnapshot(tenantId, userId, accessToken,
                requestAttributes, serverWebExchange);
    }

    /**
     * 从预捕获的租户上下文快照构建工具上下文
     * <p>
     * 用于异步编排场景：请求线程先捕获 {@link cn.com.mfish.common.ai.agent.TenantContext}，
     * 异步线程调用此方法构建 ToolContext，避免直接调用 AuthInfoUtils。
     * </p>
     *
     * @param ctx 租户上下文快照
     * @return 工具上下文Map
     */
    protected Map<String, Object> buildToolContext(cn.com.mfish.common.ai.agent.TenantContext ctx) {
        String tenantId = ctx != null ? ctx.getTenantId() : null;
        String userId = ctx != null ? ctx.getUserId() : null;
        String accessToken = ctx != null ? ctx.getAccessToken() : null;
        Object reqAttr = ctx != null ? ctx.getRequestAttributes() : null;
        Object exchange = ctx != null ? ctx.getServerWebExchange() : null;
        return buildToolContextFromSnapshot(tenantId, userId, accessToken, reqAttr, exchange);
    }

    /**
     * 从快照字段组装工具上下文 Map
     */
    private Map<String, Object> buildToolContextFromSnapshot(String tenantId, String userId, String accessToken,
                                                             Object requestAttributes, Object serverWebExchange) {
        Map<String, Object> toolContextMap = new HashMap<>();
        toolContextMap.put(RPCConstants.REQ_USER_ID, userId != null ? userId : "");
        toolContextMap.put(RPCConstants.REQ_TENANT_ID, tenantId != null ? tenantId : AuthInfoUtils.SUPER_TENANT_ID);
        toolContextMap.put(RPCConstants.REQ_ORIGIN, RPCConstants.AI);
        if (accessToken != null && !accessToken.isEmpty()) {
            toolContextMap.put(RPCConstants.REQ_TOKEN, accessToken);
        }
        if (requestAttributes instanceof RequestAttributes ra) {
            toolContextMap.put(RPCConstants.REQ_REQUEST_ATTRIBUTES, ra);
        }
        if (serverWebExchange instanceof ServerWebExchange swe) {
            toolContextMap.put(RPCConstants.REQ_SERVER_WEB_EXCHANGE, swe);
        }
        return toolContextMap;
    }

    /**
     * 基于工具的流式聊天模板方法（单服务重载）
     * <p>
     * 子类常用入口：传入单个 serviceId，内部委托给 {@link #chatWithTools(String, String, Set)}。
     * </p>
     *
     * @param sessionId 会话id
     * @param prompt    提示词
     * @param serviceId 服务ID
     * @return 流式聊天响应
     */
    protected Flux<ChatResponse> chatWithTools(String sessionId, String prompt, String serviceId) {
        return chatWithTools(sessionId, prompt, Collections.singleton(serviceId));
    }

    /**
     * 带扩展工具的流式聊天模板方法（主服务 + MCP/Skill 扩展工具 + guide 依赖业务服务）
     * <p>
     * 在垂直助手中使用：除了主服务（如 mf-sys）的 Feign 工具外，还聚合所有 MCP 工具和 Skill 工具，
     * 让用户在路由到垂直助手时也能调用 MCP 服务器和提示词级 Skill 能力。
     * </p>
     * <p>
     * 扩展工具集合 = ApiToolEngine 中所有已注册 serviceId - 标准微服务ID（MfService.allServiceIds()），
     * 即排除 mf-* 后剩余的 MCP 服务器名和 skill-* 前缀的 serviceId。
     * </p>
     * <p>
     * <b>guide 依赖合并</b>：扩展集合中可能包含 guide 类型 Skill（如 skill-leave-apply），
     * 这类 Skill 在 frontmatter 中通过 requires 声明了依赖的业务服务（如 mf-demo）。
     * 本方法会扫描扩展集合中的 skill-* 服务，读取其 requires 并合并到 serviceIds，
     * 使 LLM 在调用 guide skill 获取指南后，能继续调用指南引用的业务工具完成实际操作。
     * 此逻辑与 Planner.mergeGuideRequires 对齐，保证 chat 模式与 agent 模式工具可见性一致。
     * </p>
     *
     * @param sessionId      会话id
     * @param prompt         提示词
     * @param mainServiceId  主服务ID（垂直助手所属的微服务ID，如 SYS_SERVICE）
     * @return 流式聊天响应
     */
    protected Flux<ChatResponse> chatWithToolsAndExtensions(String sessionId, String prompt, String mainServiceId) {
        Set<String> serviceIds = new HashSet<>();
        serviceIds.add(mainServiceId);
        // 聚合所有扩展工具（MCP + Skill），排除标准微服务ID
        serviceIds.addAll(apiToolEngine.getExtendedServiceIds(ServiceConstants.MfService.allServiceIds()));
        // 合并 guide 类型 Skill 声明的依赖业务服务，使 LLM 能调用指南引用的业务工具
        mergeGuideRequiresIntoServiceIds(serviceIds);
        return chatWithTools(sessionId, prompt, serviceIds);
    }

    /**
     * 扫描 serviceIds 中的 skill-* 服务，读取 guide 类型 Skill 的 requires 并合并回 serviceIds
     * <p>
     * 与 Planner.mergeGuideRequires 逻辑对齐，保证 chat 模式下垂直助手也能看到
     * guide skill 引用的业务工具（如 mf-demo 的 demoLeaveApply.add）。
     * </p>
     * <p>
     * 当 skillCapabilityEngine 未注入或无 guide requires 时为空操作，不影响现有流程。
     * </p>
     */
    private void mergeGuideRequiresIntoServiceIds(Set<String> serviceIds) {
        if (skillCapabilityEngine == null || serviceIds == null || serviceIds.isEmpty()) {
            return;
        }
        // 复制一份避免遍历时修改原集合
        Set<String> skillServiceIds = new HashSet<>();
        for (String sid : serviceIds) {
            if (sid != null && sid.startsWith("skill-")) {
                skillServiceIds.add(sid);
            }
        }
        if (skillServiceIds.isEmpty()) {
            return;
        }
        for (String skillServiceId : skillServiceIds) {
            // skill-{code} → skill.{code}
            String skillCode = skillServiceId.substring("skill-".length());
            String actionName = "skill." + skillCode;
            List<String> requires = skillCapabilityEngine.getGuideRequires(actionName);
            if (requires != null && !requires.isEmpty()) {
                serviceIds.addAll(requires);
                log.info("[BaseAssistant] 合并 guide requires: skill={} requires={} -> serviceIds={}",
                        skillCode, requires, serviceIds);
            }
        }
    }

    /**
     * 基于工具的流式聊天模板方法（多服务聚合重载）
     * <p>
     * 自主规划场景下，Executor 按计划步骤指定的服务集合调用此方法。
     * 内部从 ApiToolEngine 聚合对应服务的工具，供 LLM 选择。
     * </p>
     * <p>
     * 本重载从当前请求线程获取租户信息，适用于同步请求链路。
     * 异步编排场景请使用 {@link #chatWithTools(String, String, Set, cn.com.mfish.common.ai.agent.TenantContext)}。
     * </p>
     *
     * @param sessionId  会话id
     * @param prompt    提示词
     * @param serviceIds 需要聚合工具的微服务ID集合
     * @return 流式聊天响应
     */
    @Override
    public Flux<ChatResponse> chatWithTools(String sessionId, String prompt, Set<String> serviceIds) {
        return chatWithTools(sessionId, prompt, serviceIds, null);
    }

    /**
     * 基于工具的流式聊天模板方法（带租户上下文重载）
     * <p>
     * 异步编排场景下专用：请求线程先捕获 {@link cn.com.mfish.common.ai.agent.TenantContext} 快照，
     * 异步线程用此快照构建 ChatClient 和 ToolContext，避免调用 AuthInfoUtils 时拿不到 RequestAttributes。
     * </p>
     * <p>
     * 当 {@code tenantContext} 为 null 时，回退到从当前线程获取租户信息（兼容同步请求链路）。
     * </p>
     * <p>
     * 系统提示词中注入工具使用规则，减少 LLM 幻觉工具名的概率：
     * <ol>
     *     <li>只能使用提供的工具，不能虚构工具名</li>
     *     <li>先分析用户意图，再选择最合适的工具</li>
     *     <li>工具调用失败时根据错误信息调整参数或选择其他工具</li>
     * </ol>
     * </p>
     *
     * @param sessionId       会话id
     * @param prompt          提示词
     * @param serviceIds      需要聚合工具的微服务ID集合
     * @param tenantContext   请求线程捕获的租户上下文快照，null 表示从当前线程获取
     * @return 流式聊天响应
     */
    @Override
    public Flux<ChatResponse> chatWithTools(String sessionId, String prompt, Set<String> serviceIds,
                                            cn.com.mfish.common.ai.agent.TenantContext tenantContext) {
        ToolCallbackProvider toolProvider = apiToolEngine.getToolCallbackProvider(serviceIds);
        // 诊断日志：输出当前步骤注入的工具数量和名称，便于排查 LLM "无法调用工具" 问题
        org.springframework.ai.tool.ToolCallback[] diagnosticCallbacks = toolProvider.getToolCallbacks();
        if (log.isInfoEnabled()) {
            StringBuilder toolNames = new StringBuilder();
            for (int i = 0; i < diagnosticCallbacks.length; i++) {
                if (i > 0) toolNames.append(", ");
                toolNames.append(diagnosticCallbacks[i].getToolDefinition().name());
            }
            log.info("[BaseAssistant] chatWithTools sessionId={} serviceIds={} toolCount={} tools=[{}]",
                    sessionId, serviceIds, diagnosticCallbacks.length, toolNames);
        }
        Map<String, Object> toolContextMap = tenantContext != null
                ? buildToolContext(tenantContext)
                : buildToolContext();
        // 注入 sessionId 到 ToolContext，供 FrontendActionTool 跨线程传递前端操作指令
        toolContextMap.put(cn.com.mfish.common.ai.frontend.FrontendActionHolder.CTX_SESSION_ID, sessionId);
        // 按租户上下文构建 ChatClient，避免在异步线程调用 currentTenantId()
        String tenantId = tenantContext != null && tenantContext.getTenantId() != null
                ? tenantContext.getTenantId()
                : llmModelRouter.currentTenantId();
        ChatClient chatClient = getChatClient(tenantId);
        // 构建工具使用提示词，减少LLM幻觉
        String toolHint = buildToolUsageHint(toolProvider);
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .system(getSystemPrompt() + "\n\n" + toolHint)
                .user(prompt)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .tools(toolProvider)
                .toolContext(toolContextMap);
        return requestSpec
                .stream()
                .chatResponse()
                .onErrorResume(e -> {
                    log.warn("流式调用失败，降级为非流式调用: {}", e.getMessage());
                    return Mono.fromCallable(() -> requestSpec.call().chatResponse())
                            .subscribeOn(Schedulers.boundedElastic())
                            .flux();
                });
    }

    /**
     * 构建工具使用提示词，帮助LLM理解可用工具，减少幻觉
     * <p>
     * 当检测到工具列表中同时包含 skill.* 和 frontend.* 工具时，追加特别提示，
     * 强调调用 skill 获取指南后必须继续执行指南中的所有步骤（含前端操作）。
     * </p>
     */
    private String buildToolUsageHint(ToolCallbackProvider toolProvider) {
        org.springframework.ai.tool.ToolCallback[] callbacks = toolProvider.getToolCallbacks();
        if (callbacks.length == 0) {
            return "## 工具使用规则\n当前没有可用的工具，请直接根据你的知识回答用户问题。";
        }
        StringBuilder sb = new StringBuilder("## 工具使用规则\n");
        sb.append("你只能使用以下工具，不能虚构任何工具名：\n");
        boolean hasSkill = false;
        boolean hasFrontend = false;
        for (org.springframework.ai.tool.ToolCallback tc : callbacks) {
            String name = tc.getToolDefinition().name();
            String desc = tc.getToolDefinition().description();
            if (name.startsWith("skill.")) hasSkill = true;
            if (name.startsWith("frontend.")) hasFrontend = true;
            sb.append("- ").append(name);
            if (!desc.isEmpty()) {
                sb.append(": ").append(desc);
            }
            sb.append("\n");
        }
        sb.append("\n调用规则：\n");
        sb.append("1. 先分析用户意图，从上述工具列表中选择最合适的一个\n");
        sb.append("2. 只能使用上述列表中的工具名，不要构造新的工具名\n");
        sb.append("3. 如果没有合适的工具，直接用你的知识回答\n");
        sb.append("4. 工具调用失败时，根据返回的错误信息调整参数或选择其他工具\n");
        // 当同时存在 skill 和 frontend 工具时，强调调用 skill 后必须继续执行指南步骤（含前端操作）
        if (hasSkill && hasFrontend) {
            sb.append("5. 【强制】如果工具列表中包含 skill.* 开头的工具，且用户需求属于该 skill 对应的领域（如请假、代码生成），\n");
            sb.append("   必须优先调用 skill 工具获取操作指南。获取指南后，必须立即按指南中的步骤顺序逐个调用对应工具\n");
            sb.append("   （包括 frontend.navigate 路由跳转、frontend.refresh 页面刷新等前端操作工具），\n");
            sb.append("   严禁仅返回指南内容而不执行，严禁跳过任何前端操作步骤。\n");
        }
        return sb.toString();
    }

    /**
     * 聊天返回id
     * <p>
     * 重构后流程（接入 Memory 模块）：
     * <ol>
     *   <li>获取（或创建）sessionId 对应的 {@link ConversationMemory}</li>
     *   <li>若请求携带 fileIds：通过 FileParseService 解析为 DocumentChunk 列表，
     *       注入到 Memory 的 Document Context（替代旧的字符串拼接）</li>
     *   <li>从 Memory 读取 Document Context 拼接文本，注入到用户 prompt 前</li>
     *   <li>交由子类 chat(sessionId, prompt) 处理（保持向后兼容）</li>
     * </ol>
     * <p>
     * 关键约束：文件解析必须在请求线程执行（Feign BearerTokenInterceptor 依赖
     * RequestContextHolder），Memory 写入也在请求线程完成（保证一致性）。
     *
     * @return 聊天信息
     */
    @Override
    public Flux<ChatResponseVo> chat(AiRequest aiRequest) {
        String sessionId = aiRequest.getSessionId();
        String prompt = aiRequest.getMessage().getContent();
        ConversationMemory memory = memoryStore.getOrCreate(sessionId);

        // 文件解析 + 注入 Memory 的 Document Context
        List<String> fileIds = aiRequest.getFileIds();
        if (fileIds != null && !fileIds.isEmpty()) {
            List<cn.com.mfish.common.ai.memory.DocumentChunk> chunks = fileParseService.loadAsChunks(fileIds);
            if (!chunks.isEmpty()) {
                memory.addDocumentChunks(chunks);
            }
        }

        // 从 Memory 读取文档上下文，拼接到 prompt 前
        // 当前使用 getDocumentContext() 全量注入；当文档较多、token 预算紧张时，
        // 未来可切换为 memory.searchDocumentContext(prompt, 5) 走 RAG 检索（向量库版 Memory 覆写此方法）。
        String documentContext = memory.getDocumentContext();
        if (StringUtils.isNotEmpty(documentContext)) {
            prompt = "以下是用户上传的文件内容，请基于文件内容进行分析：\n\n"
                    + documentContext
                    + "\n用户问题：" + prompt;
        }

        final String finalPrompt = prompt;
        final String messageId = aiRequest.getId();
        // 清理上次请求可能残留的前端操作指令和 emitter
        cn.com.mfish.common.ai.frontend.FrontendActionHolder.clear(sessionId);
        cn.com.mfish.common.ai.frontend.FrontendActionHolder.unregisterEmitter(sessionId);

        // 使用 Flux.create + emitter 回调实现 FRONTEND_ACTION 分通道下发：
        // - navigate/click/fill/openModal：实时下发（工具调用时立即通过 emitter 发射）
        // - refresh：延迟下发（存入 list，文本流完成后 drain 发射）
        // 最终顺序：navigate → 业务工具 → 文本 → refresh → STOP
        // 单一 Flux 源，不触发额外的 async dispatch，避免 Spring Security 异常
        return Flux.<ChatResponseVo>create(sink -> {
            // 注册 emitter：非 refresh 的操作实时发射到 sink
            cn.com.mfish.common.ai.frontend.FrontendActionHolder.registerEmitter(sessionId, fa -> {
                String json = com.alibaba.fastjson2.JSON.toJSONString(fa);
                log.info("[BaseAssistant] 实时下发前端操作: {} {}", fa.getAction(), fa.getTarget());
                sink.next(new ChatResponseVo()
                        .setId(messageId)
                        .setType(cn.com.mfish.common.ai.entity.EventType.FRONTEND_ACTION)
                        .setContent(json));
            });

            chat(sessionId, finalPrompt)
                    // 只保留有文本内容的响应（STOP 空响应在最后统一发射）
                    .filter(resp -> StringUtils.isNotEmpty(
                            Objects.requireNonNull(resp.getResult()).getOutput().getText()))
                    .map(resp -> new ChatResponseVo().setId(messageId)
                            .setContent(resp.getResult().getOutput().getText())
                            .setFinishReason(resp.getResult().getMetadata().getFinishReason()))
                    .subscribe(
                            sink::next,
                            sink::error,
                            () -> {
                                // 主流完成：注销 emitter
                                cn.com.mfish.common.ai.frontend.FrontendActionHolder.unregisterEmitter(sessionId);
                                // 下发延迟的 refresh 操作（文本之后、STOP 之前）
                                List<cn.com.mfish.common.ai.entity.FrontendAction> deferredActions =
                                        cn.com.mfish.common.ai.frontend.FrontendActionHolder.drain(sessionId);
                                for (cn.com.mfish.common.ai.entity.FrontendAction fa : deferredActions) {
                                    String json = com.alibaba.fastjson2.JSON.toJSONString(fa);
                                    log.info("[BaseAssistant] 延迟下发前端操作: {} {}", fa.getAction(), fa.getTarget());
                                    sink.next(new ChatResponseVo()
                                            .setId(messageId)
                                            .setType(cn.com.mfish.common.ai.entity.EventType.FRONTEND_ACTION)
                                            .setContent(json));
                                }
                                // 发射 STOP 信号
                                sink.next(new ChatResponseVo().setId(messageId).setFinishReason("STOP"));
                                sink.complete();
                            }
                    );
        }, reactor.core.publisher.FluxSink.OverflowStrategy.BUFFER)
                .doFinally(signal -> {
                    cn.com.mfish.common.ai.frontend.FrontendActionHolder.unregisterEmitter(sessionId);
                    cn.com.mfish.common.ai.frontend.FrontendActionHolder.clear(sessionId);
                    log.info("[BaseAssistant] 请求结束 sessionId={} signal={}", sessionId, signal);
                });
    }

}

