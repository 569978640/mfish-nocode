package cn.com.mfish.common.ai.capability;

import cn.com.mfish.common.ai.engine.ApiToolEngine;
import cn.com.mfish.common.core.utils.StringUtils;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Skill 能力引擎（文件式加载）
 * <p>
 * 实现 {@link CapabilitySubEngine} 接口，通过 {@link SkillFileLoader} 扫描
 * {@code skills/*.md} 文件加载提示词级 Skill，管理生命周期和执行。
 * </p>
 * <p>
 * <b>架构定位</b>：
 * <pre>
 * CapabilityEngine（门面）
 *     │
 *     ├── ToolCapabilityEngine   (Feign/OpenAPI)
 *     ├── McpCapabilityEngine    (MCP 协议)
 *     ├── SkillCapabilityEngine  (本类，提示词级 Skill，文件式)
 *     └── [WorkflowCapabilityEngine] (未来扩展)
 * </pre>
 * </p>
 * <p>
 * <b>Skill 来源</b>：纯文件式，不依赖数据库
 * <ul>
 *   <li>classpath:skills/*.md — 内置 Skill（打包在 jar 中）</li>
 *   <li>外部目录（mf.ai.skill.dir / MF_AI_SKILL_DIR / ${user.dir}/skills）— 用户自定义</li>
 *   <li>外部目录覆盖 classpath 同名 Skill</li>
 * </ul>
 * </p>
 * <p>
 * <b>skill.md 格式</b>：YAML frontmatter + prompt 正文，一 .md = 一 Skill = 一动作
 * <pre>
 * ---
 * name: translator
 * description: 将文本翻译为指定语言
 * model: deepseek-v3
 * ---
 * 你是一个专业翻译。将以下文本翻译为 {lang}：
 * {text}
 * </pre>
 * 参数从 {param} 占位符自动提取，无需声明。
 * </p>
 * <p>
 * <b>动作名约定</b>：{@code skill.{skillCode}}（一 Skill 一动作，无二级动作名）
 * <br><b>serviceId</b>：{@code skill-{skillCode}}
 * </p>
 * <p>
 * <b>初始化流程</b>：
 * <ol>
 *   <li>{@link SkillFileLoader#loadAll()} 扫描 .md 文件，解析 frontmatter + 提取占位符</li>
 *   <li>为每个 Skill 构建 {@link ActionDefinition}（加 skill. 前缀）</li>
 *   <li>包装为 {@link SkillToolCallback} 注册到 {@link ApiToolEngine}（与 MCP 模式一致）</li>
 *   <li>回调 {@code capabilityEngine.refreshActionIndex()} 重建动作索引</li>
 * </ol>
 * 异步执行，不阻塞 Spring Boot 启动。
 * </p>
 * <p>
 * <b>执行流程（提示词级 Skill）</b>：
 * <ol>
 *   <li>LLM 调用工具时，Spring AI 调用 {@link SkillToolCallback#call(String)}</li>
 *   <li>解析 JSON 参数，用 {param} 占位符填充 promptTemplate</li>
 *   <li>通过 {@link SkillChatClientProvider} 获取 ChatModel，构建 ChatClient</li>
 *   <li>调用 LLM 生成结果，返回文本输出</li>
 * </ol>
 * </p>
 * <p>
 * <b>注意</b>：当前 SkillInfo.modelName 字段已保留但暂未生效（SkillChatClientProvider 仅按租户路由，
 * 不支持指定模型名），后续可通过扩展 Provider 接口支持。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/22
 */
@Slf4j
public class SkillCapabilityEngine implements CapabilitySubEngine {

    /**
     * Skill 动作名前缀
     */
    private static final String SKILL_ACTION_PREFIX = "skill.";

    /**
     * serviceId 前缀
     */
    private static final String SKILL_SERVICE_PREFIX = "skill-";

    /**
     * Skill 文件加载器
     */
    private final SkillFileLoader fileLoader;

    /**
     * ChatClient 提供者（由业务层注入，按租户路由 ChatModel）
     */
    private final SkillChatClientProvider chatClientProvider;

    /**
     * ApiToolEngine 引用：Skill 工具发现后包装为 ToolCallback 注册到 ApiToolEngine
     */
    private final ApiToolEngine apiToolEngine;

    /**
     * 动作映射：actionName → SkillInfo
     */
    private final Map<String, SkillInfo> actionRegistry = new ConcurrentHashMap<>();

    /**
     * 已映射的 ActionDefinition 列表（不可变快照，getActions() 直接返回）
     */
    private volatile List<ActionDefinition> cachedActions = Collections.emptyList();

    /**
     * 初始化状态标志
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 初始化中标志：防止并发重复触发
     */
    private final AtomicBoolean initializing = new AtomicBoolean(false);

    public SkillCapabilityEngine(SkillFileLoader fileLoader,
                                  SkillChatClientProvider chatClientProvider,
                                  ApiToolEngine apiToolEngine) {
        this.fileLoader = fileLoader;
        this.chatClientProvider = chatClientProvider;
        this.apiToolEngine = apiToolEngine;
    }

    @Override
    public EngineType getEngineType() {
        return EngineType.SKILL;
    }

    @Override
    public List<ActionDefinition> getActions() {
        return cachedActions;
    }

    /**
     * 查询指定 guide 类型 Skill 声明依赖的业务服务ID列表
     * <p>
     * 供 Planner 在规划时调用：当 Planner 发现某步骤需要调用 guide 类型 Skill 时，
     * 通过此方法获取 Skill 声明的 requires（如 mf-demo），合并到步骤的 serviceIds，
     * 确保 Executor 执行时 LLM 能看到被指南引用的业务工具。
     * </p>
     * <p>
     * 非 guide 类型或未声明 requires 的 Skill 返回空列表。
     * </p>
     *
     * @param skillActionName Skill 动作名（如 skill.leave-apply）
     * @return 依赖的业务服务ID列表，可能为空
     */
    public List<String> getGuideRequires(String skillActionName) {
        if (skillActionName == null) {
            return Collections.emptyList();
        }
        SkillInfo skill = actionRegistry.get(skillActionName);
        if (skill == null || !"guide".equalsIgnoreCase(skill.getType())) {
            return Collections.emptyList();
        }
        List<String> requires = skill.getRequires();
        return requires != null ? requires : Collections.emptyList();
    }

    /**
     * 执行 Skill 动作（显式调用模式）
     * <p>
     * 通常由 LLM 驱动模式（ToolCallback）自动调用，此方法供显式调用场景使用。
     * </p>
     */
    @Override
    public ActionResult execute(String actionName, Map<String, Object> params, ExecutionContext ctx) {
        long start = System.currentTimeMillis();
        if (!initialized.get()) {
            return ActionResult.failure(EngineType.SKILL,
                    "Skill 引擎正在异步初始化中，请稍后重试: " + actionName, System.currentTimeMillis() - start);
        }
        SkillInfo skill = actionRegistry.get(actionName);
        if (skill == null) {
            return ActionResult.failure(EngineType.SKILL,
                    "未找到 Skill 动作: " + actionName, System.currentTimeMillis() - start);
        }
        try {
            String output = executePromptSkill(skill, params, ctx);
            return ActionResult.success(EngineType.SKILL, output, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[SkillCapabilityEngine] 执行 Skill 动作失败 action={}", actionName, e);
            return ActionResult.failure(EngineType.SKILL,
                    "Skill 动作执行异常: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * 异步初始化：在后台线程执行 {@link #refresh()}，不阻塞主线程
     *
     * @param capabilityEngine 能力引擎门面（初始化完成后回调重建索引）
     */
    public void refreshAsync(CapabilityEngine capabilityEngine) {
        if (!initializing.compareAndSet(false, true)) {
            log.info("[SkillCapabilityEngine] 初始化已在进行中，跳过重复触发");
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                log.info("[SkillCapabilityEngine] 异步初始化开始（不阻塞主线程）");
                refresh();
            } catch (Exception e) {
                log.error("[SkillCapabilityEngine] 异步初始化异常", e);
            } finally {
                initialized.set(true);
                initializing.set(false);
                if (capabilityEngine != null) {
                    try {
                        capabilityEngine.refreshActionIndex();
                        log.info("[SkillCapabilityEngine] 动作索引已重建，Skill 工具现已可用");
                    } catch (Exception e) {
                        log.error("[SkillCapabilityEngine] 重建动作索引失败", e);
                    }
                }
            }
        }, "skill-capability-engine-init");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 同步刷新：重新扫描 .md 文件，重建动作注册表
     * <p>
     * 供控制台手动触发（如新增 .md 文件后调用 /refresh）或初始化时调用。
     * </p>
     */
    public synchronized void refresh() {
        List<SkillInfo> skills;
        try {
            skills = fileLoader.loadAll();
        } catch (Exception e) {
            log.error("[SkillCapabilityEngine] 加载 Skill 文件失败", e);
            return;
        }

        if (skills == null || skills.isEmpty()) {
            log.info("[SkillCapabilityEngine] 无 Skill 文件（classpath:skills/*.md 和外部目录均无 .md）");
            clearAll();
            return;
        }

        clearAll();

        List<ActionDefinition> actions = new ArrayList<>();
        for (SkillInfo skill : skills) {
            String actionName = buildActionName(skill.getSkillCode());
            String serviceId = SKILL_SERVICE_PREFIX + skill.getSkillCode();
            String inputSchema = StringUtils.isNotEmpty(skill.getInputSchema())
                    ? skill.getInputSchema() : "{}";
            ActionDefinition action = new ActionDefinition()
                    .setName(actionName)
                    .setDescription(skill.getDescription())
                    .setInputSchema(inputSchema)
                    .setEngineType(EngineType.SKILL)
                    .setServiceId(serviceId);
            actions.add(action);
            actionRegistry.put(actionName, skill);

            // 包装为 ToolCallback 反向注册到 ApiToolEngine，使 BaseAssistant 可见
            ToolCallback callback = new SkillToolCallback(actionName, skill.getDescription(),
                    inputSchema, this);
            if (apiToolEngine != null) {
                apiToolEngine.replace(serviceId, List.of(callback));
            }
            log.info("[SkillCapabilityEngine] 注册 Skill 动作 action={} params={} source={}",
                    actionName, skill.getParams(), skill.getSource());
        }

        this.cachedActions = Collections.unmodifiableList(actions);
        log.info("[SkillCapabilityEngine] 刷新完成，共加载 {} 个 Skill", actions.size());
    }

    /**
     * 执行提示词级 Skill：模板填充 + LLM 调用
     * <p>
     * guide 类型 Skill 不走 LLM 调用，直接返回模板内容给外层 LLM，
     * 指导其调用其他工具完成多步编排。
     * </p>
     */
    private String executePromptSkill(SkillInfo skill, Map<String, Object> params, ExecutionContext ctx) {
        String promptTemplate = skill.getPromptTemplate();
        if (StringUtils.isEmpty(promptTemplate)) {
            throw new IllegalStateException("Skill " + skill.getSkillCode() + " 无提示词模板");
        }

        // guide 类型：直接返回模板内容，不走 LLM 调用
        // 避免内部 LLM 无工具时产生幻觉（假装已执行但实际未调工具）
        if ("guide".equalsIgnoreCase(skill.getType())) {
            String content = fillTemplate(promptTemplate, params != null ? params : Collections.emptyMap());
            log.info("[SkillCapabilityEngine] guide 类型 Skill 直接返回内容 skill={} length={}",
                    skill.getSkillCode(), content.length());
            return content;
        }

        // prompt 类型：填充模板占位符 + 调用 LLM
        String filledPrompt = fillTemplate(promptTemplate, params != null ? params : Collections.emptyMap());

        // 获取租户 ID（从执行上下文）
        String tenantId = ctx != null && ctx.getTenantContext() != null
                ? ctx.getTenantContext().getTenantId() : null;

        // 通过 Provider 获取 ChatModel 并构建 ChatClient
        ChatModel chatModel = chatClientProvider.getChatModel(tenantId);
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        // 调用 LLM（同步）
        String output = chatClient.prompt()
                .user(filledPrompt)
                .call()
                .content();

        return output != null ? output : "";
    }

    /**
     * 填充模板占位符：{param} → params.get(param)
     * <p>
     * 未匹配的占位符保持原样，不报错。
     * </p>
     */
    private String fillTemplate(String template, Map<String, Object> params) {
        String result = template;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String placeholder = "{" + e.getKey() + "}";
            String value = e.getValue() != null ? String.valueOf(e.getValue()) : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * 构建 Skill 动作名：skill.{skillCode}
     */
    private String buildActionName(String skillCode) {
        return SKILL_ACTION_PREFIX + skillCode;
    }

    /**
     * 清理所有动作注册表和 ApiToolEngine 中的 Skill 工具
     */
    private void clearAll() {
        // 清理 ApiToolEngine 中所有 Skill 相关的 serviceId
        if (apiToolEngine != null) {
            for (ActionDefinition action : cachedActions) {
                if (action.getServiceId() != null && action.getServiceId().startsWith(SKILL_SERVICE_PREFIX)) {
                    apiToolEngine.replace(action.getServiceId(), List.of());
                }
            }
        }
        actionRegistry.clear();
        cachedActions = Collections.emptyList();
    }

    /**
     * Skill 工具 → Spring AI ToolCallback 适配器
     * <p>
     * 将 Skill 动作包装为 {@link ToolCallback}，使 BaseAssistant 能通过
     * {@code apiToolEngine.getToolCallbackProvider(serviceIds)} 获取到 Skill 工具。
     * </p>
     * <p>
     * LLM 调用工具时，Spring AI 框架调用 {@link #call(String)}，
     * 本类委托 {@link SkillCapabilityEngine#execute(String, Map, ExecutionContext)} 执行。
     * </p>
     */
    private static class SkillToolCallback implements ToolCallback {

        private final String actionName;
        private final ToolDefinition toolDefinition;
        private final SkillCapabilityEngine engine;

        SkillToolCallback(String actionName, String description, String inputSchema,
                          SkillCapabilityEngine engine) {
            this.actionName = actionName;
            this.engine = engine;
            this.toolDefinition = DefaultToolDefinition.builder()
                    .name(actionName)
                    .description(description != null ? description : actionName)
                    .inputSchema(inputSchema != null ? inputSchema : "{}")
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public @NonNull String call(@NonNull String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public @NonNull String call(@NonNull String toolInput, ToolContext context) {
            try {
                Map<String, Object> params;
                if (toolInput == null || toolInput.isBlank()) {
                    params = Collections.emptyMap();
                } else {
                    params = JSON.parseObject(toolInput, Map.class);
                    if (params == null) {
                        params = Collections.emptyMap();
                    }
                }
                // Skill 执行不依赖 ExecutionContext（ToolContext 中暂未传递 TenantContext）
                ActionResult result = engine.execute(actionName, params, null);
                if (result.isSuccess()) {
                    return result.getOutput();
                }
                return "Skill 执行失败: " + result.getError();
            } catch (Exception e) {
                log.error("[SkillToolCallback] Skill 工具调用失败 action={} input={}", actionName, toolInput, e);
                return "Skill 工具调用失败: " + e.getMessage();
            }
        }
    }
}
