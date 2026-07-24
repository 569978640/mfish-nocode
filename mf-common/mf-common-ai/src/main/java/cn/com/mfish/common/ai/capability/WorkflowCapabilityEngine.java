package cn.com.mfish.common.ai.capability;

import cn.com.mfish.common.ai.engine.ApiToolEngine;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工作流能力引擎
 * <p>
 * 实现 {@link CapabilitySubEngine} 接口，对接 Flowable 工作流引擎，
 * 将已发布的 BPMN 流程模型自动导出为 LLM 可调用的工具。
 * </p>
 * <p>
 * <b>架构定位</b>：
 * <pre>
 * CapabilityEngine（门面）
 *     │
 *     ├── ToolCapabilityEngine       (Feign/OpenAPI)
 *     ├── McpCapabilityEngine        (MCP 协议)
 *     ├── SkillCapabilityEngine      (提示词级 Skill，文件式)
 *     └── WorkflowCapabilityEngine   (本类，Flowable 工作流)
 * </pre>
 * </p>
 * <p>
 * <b>工作流程</b>：
 * <ol>
 *   <li>从 {@link WorkflowConfigProvider} 获取所有已发布的流程定义</li>
 *   <li>每个流程导出为一个 {@link ToolCallback}，工具名 {@code workflow.execute_{flowKey}}</li>
 *   <li>LLM 调用工具时，通过 {@link WorkflowExecutionProvider} 隐式启动 Flowable 流程实例</li>
 *   <li>返回流程实例ID和状态供 LLM 理解执行结果</li>
 * </ol>
 * </p>
 * <p>
 * <b>动作名约定</b>：{@code workflow.execute_{flowKey}}
 * <br>例如流程 key 为 {@code leave_flow}，动作名为 {@code workflow.execute_leave_flow}
 * </p>
 * <p>
 * <b>serviceId 约定</b>：统一使用 {@code workflow}，所有流程工具注册到同一个 serviceId 下，
 * 避免流程数量多时 serviceId 膨胀。
 * </p>
 * <p>
 * <b>工具入参 Schema</b>：
 * <pre>
 * {
 *   "businessKey": "业务标识(如请假单ID)",
 *   "variables": { "days": 3, "reason": "病假" }
 * }
 * </pre>
 * 若流程配置了变量定义，Schema 中会包含具体变量属性；否则 variables 为自由 object。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/23
 */
@Slf4j
public class WorkflowCapabilityEngine implements CapabilitySubEngine {

    /**
     * 工作流动作名前缀
     */
    private static final String WORKFLOW_ACTION_PREFIX = "workflow.execute_";

    /**
     * 统一 serviceId（所有流程工具注册到此 serviceId）
     */
    private static final String WORKFLOW_SERVICE_ID = "workflow";

    /**
     * 工作流配置提供者（由业务层注入，查询数据库）
     */
    private final WorkflowConfigProvider configProvider;

    /**
     * 工作流执行提供者（由业务层注入，对接 Flowable）
     */
    private final WorkflowExecutionProvider executionProvider;

    /**
     * ApiToolEngine 引用：工作流工具发现后包装为 ToolCallback 注册到 ApiToolEngine，
     * 使 BaseAssistant 能通过 apiToolEngine.getToolCallbackProvider(serviceIds) 获取工作流工具
     */
    private final ApiToolEngine apiToolEngine;

    /**
     * 流程动作注册表：actionName → WorkflowEntry
     */
    private final Map<String, WorkflowEntry> actionRegistry = new ConcurrentHashMap<>();

    /**
     * 异步初始化标志
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean initializing = new AtomicBoolean(false);

    public WorkflowCapabilityEngine(WorkflowConfigProvider configProvider,
                                    WorkflowExecutionProvider executionProvider,
                                    ApiToolEngine apiToolEngine) {
        this.configProvider = configProvider;
        this.executionProvider = executionProvider;
        this.apiToolEngine = apiToolEngine;
    }

    @Override
    public EngineType getEngineType() {
        return EngineType.WORKFLOW;
    }

    @Override
    public List<ActionDefinition> getActions() {
        if (!initialized.get()) {
            return Collections.emptyList();
        }
        List<ActionDefinition> actions = new ArrayList<>();
        for (WorkflowEntry entry : actionRegistry.values()) {
            actions.add(entry.actionDefinition);
        }
        return actions;
    }

    @Override
    public ActionResult execute(String actionName, Map<String, Object> params,
                                ExecutionContext ctx) {
        long start = System.currentTimeMillis();
        if (!initialized.get()) {
            return ActionResult.failure(EngineType.WORKFLOW,
                    "工作流引擎正在异步初始化中，请稍后重试: " + actionName, 0);
        }
        WorkflowEntry entry = actionRegistry.get(actionName);
        if (entry == null) {
            return ActionResult.failure(EngineType.WORKFLOW,
                    "未找到工作流动作: " + actionName, 0);
        }
        try {
            String output = doExecute(entry, params, ctx);
            return ActionResult.success(EngineType.WORKFLOW, output, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[WorkflowCapabilityEngine] 执行工作流失败 action={}", actionName, e);
            return ActionResult.failure(EngineType.WORKFLOW,
                    "工作流执行异常: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * 执行工作流：解析参数 → 启动流程实例 → 返回状态
     */
    @SuppressWarnings("unchecked")
    private String doExecute(WorkflowEntry entry, Map<String, Object> params,
                             ExecutionContext ctx) {
        // 解析 businessKey 和 variables
        String businessKey = params.containsKey("businessKey")
                ? String.valueOf(params.get("businessKey"))
                : "ai-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> variables;
        Object varsObj = params.get("variables");
        if (varsObj instanceof Map) {
            variables = (Map<String, Object>) varsObj;
        } else if (varsObj instanceof String s && !s.isEmpty()) {
            variables = JSON.parseObject(s, Map.class);
        } else {
            // 没有显式 variables，把除 businessKey 外的参数全部作为流程变量
            variables = new java.util.HashMap<>(params);
            variables.remove("businessKey");
        }

        // 解析启动人
        String startUserId = null;
        if (ctx != null && ctx.getTenantContext() != null) {
            startUserId = ctx.getTenantContext().getUserId();
        }

        WorkflowResult result = executionProvider.startProcess(
                entry.workflowInfo.getFlowKey(), businessKey, variables, startUserId);

        JSONObject output = new JSONObject();
        output.put("success", result.isSuccess());
        output.put("processInstanceId", result.getProcessInstanceId());
        output.put("status", result.getStatus());
        output.put("currentTaskName", result.getCurrentTaskName());
        output.put("message", result.getMessage());
        output.put("flowKey", entry.workflowInfo.getFlowKey());
        output.put("flowName", entry.workflowInfo.getFlowName());
        return output.toJSONString();
    }

    /**
     * 异步初始化：从数据库加载已发布流程，构建动作注册表
     * <p>
     * 不阻塞主线程，在 daemon 线程执行。初始化完成后回调 {@code capabilityEngine.refreshActionIndex()} 重建动作索引。
     * </p>
     *
     * @param capabilityEngine 能力引擎门面（用于初始化完成后回调重建索引）
     */
    public void refreshAsync(CapabilityEngine capabilityEngine) {
        if (initializing.getAndSet(true)) {
            log.info("[WorkflowCapabilityEngine] 异步初始化正在进行中，跳过");
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                refresh();
                if (capabilityEngine != null) {
                    capabilityEngine.refreshActionIndex();
                    log.info("[WorkflowCapabilityEngine] 动作索引已重建，工作流工具现已可用");
                }
            } catch (Exception e) {
                log.error("[WorkflowCapabilityEngine] 异步初始化失败", e);
            } finally {
                initializing.set(false);
            }
        }, "workflow-engine-init");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 同步刷新：从数据库加载已发布流程，构建动作注册表
     */
    public void refresh() {
        log.info("[WorkflowCapabilityEngine] 开始加载已发布工作流配置");
        clearAll();
        try {
            List<WorkflowInfo> workflows = configProvider.getActiveWorkflows();
            if (workflows == null || workflows.isEmpty()) {
                log.info("[WorkflowCapabilityEngine] 无已发布的工作流配置");
                initialized.set(true);
                return;
            }
            List<ToolCallback> allCallbacks = new ArrayList<>();
            for (WorkflowInfo workflow : workflows) {
                String actionName = buildActionName(workflow.getFlowKey());
                String description = buildDescription(workflow);
                String inputSchema = buildInputSchema(workflow);

                ActionDefinition action = new ActionDefinition()
                        .setName(actionName)
                        .setDescription(description)
                        .setInputSchema(inputSchema)
                        .setEngineType(EngineType.WORKFLOW)
                        .setServiceId(WORKFLOW_SERVICE_ID);

                WorkflowEntry entry = new WorkflowEntry(workflow, action);
                actionRegistry.put(actionName, entry);

                ToolCallback callback = new WorkflowToolCallback(actionName, description, inputSchema, workflow);
                allCallbacks.add(callback);

                log.info("[WorkflowCapabilityEngine] 注册工作流工具 flowKey={} flowName={} action={}",
                        workflow.getFlowKey(), workflow.getFlowName(), actionName);
            }
            // 所有流程工具统一注册到 workflow serviceId
            apiToolEngine.replace(WORKFLOW_SERVICE_ID, allCallbacks);
            initialized.set(true);
            log.info("[WorkflowCapabilityEngine] 初始化完成，共 {} 个工作流，{} 个工具",
                    workflows.size(), allCallbacks.size());
        } catch (Exception e) {
            log.error("[WorkflowCapabilityEngine] 加载工作流配置失败", e);
        }
    }

    /**
     * 构建工作流动作名：workflow.execute_{flowKey}
     */
    private String buildActionName(String flowKey) {
        return WORKFLOW_ACTION_PREFIX + flowKey;
    }

    /**
     * 构建工具描述
     */
    private String buildDescription(WorkflowInfo workflow) {
        StringBuilder sb = new StringBuilder();
        sb.append("启动工作流: ").append(workflow.getFlowName());
        if (workflow.getDescription() != null && !workflow.getDescription().isEmpty()) {
            sb.append(" — ").append(workflow.getDescription());
        }
        sb.append(" (流程key: ").append(workflow.getFlowKey()).append(")");
        return sb.toString();
    }

    /**
     * 构建 inputSchema
     * <p>
     * 固定包含 businessKey 参数；variables 参数为 object 类型。
     * 若流程配置了变量定义，将变量展开到 properties 中。
     * </p>
     */
    private String buildInputSchema(WorkflowInfo workflow) {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();

        // businessKey 参数
        JSONObject businessKeySchema = new JSONObject();
        businessKeySchema.put("type", "string");
        businessKeySchema.put("description", "业务标识（如请假单ID，为空时自动生成）");
        properties.put("businessKey", businessKeySchema);

        // 变量参数
        if (workflow.getVariables() != null && !workflow.getVariables().isEmpty()) {
            // 展开每个变量到 properties
            for (WorkflowVariable var : workflow.getVariables()) {
                JSONObject varSchema = new JSONObject();
                varSchema.put("type", var.getType() != null ? var.getType() : "string");
                if (var.getDescription() != null) {
                    varSchema.put("description", var.getDescription());
                }
                properties.put(var.getName(), varSchema);
            }
            List<String> required = new ArrayList<>();
            required.add("businessKey");
            for (WorkflowVariable var : workflow.getVariables()) {
                if (var.isRequired()) {
                    required.add(var.getName());
                }
            }
            schema.put("required", required);
        } else {
            // 无变量定义时，提供 variables 对象参数
            JSONObject variablesSchema = new JSONObject();
            variablesSchema.put("type", "object");
            variablesSchema.put("description", "流程变量键值对（如 {\"days\":3,\"reason\":\"病假\"}）");
            properties.put("variables", variablesSchema);
            schema.put("required", List.of("businessKey"));
        }

        schema.put("properties", properties);
        return schema.toJSONString();
    }

    /**
     * 清理所有注册表和 ApiToolEngine 注册
     */
    private void clearAll() {
        actionRegistry.clear();
        apiToolEngine.replace(WORKFLOW_SERVICE_ID, List.of());
    }

    /**
     * 工作流动作注册表条目
     */
    private record WorkflowEntry(WorkflowInfo workflowInfo, ActionDefinition actionDefinition) {
    }

    /**
     * 工作流工具 → Spring AI ToolCallback 适配器
     * <p>
     * 将每个已发布的 BPMN 流程包装为 {@link ToolCallback}，使 BaseAssistant 能通过
     * {@code apiToolEngine.getToolCallbackProvider(serviceIds)} 获取到工作流工具，
     * LLM 调用工具时隐式启动 Flowable 流程实例。
     * </p>
     */
    private class WorkflowToolCallback implements ToolCallback {
        private final ToolDefinition toolDefinition;
        private final WorkflowInfo workflowInfo;

        WorkflowToolCallback(String actionName, String description, String inputSchema,
                             WorkflowInfo workflowInfo) {
            this.workflowInfo = workflowInfo;
            this.toolDefinition = DefaultToolDefinition.builder()
                    .name(actionName)
                    .description(description)
                    .inputSchema(inputSchema)
                    .build();
        }

        @Override
        public @NonNull ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public @NonNull ToolMetadata getToolMetadata() {
            return ToolMetadata.builder().build();
        }

        @Override
        public @NonNull String call(@NonNull String toolInput) {
            return call(toolInput, null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public @NonNull String call(@NonNull String toolInput, ToolContext context) {
            try {
                Map<String, Object> params = JSON.parseObject(toolInput, Map.class);
                if (params == null) {
                    params = Collections.emptyMap();
                }
                // 解析 businessKey
                String businessKey = params.containsKey("businessKey")
                        ? String.valueOf(params.get("businessKey"))
                        : "ai-" + UUID.randomUUID().toString().substring(0, 8);

                // 解析 variables
                Map<String, Object> variables;
                Object varsObj = params.get("variables");
                if (varsObj instanceof Map) {
                    variables = (Map<String, Object>) varsObj;
                } else {
                    // 没有显式 variables，把除 businessKey 外的参数全部作为流程变量
                    variables = new java.util.HashMap<>(params);
                    variables.remove("businessKey");
                }

                // 解析启动人（从 ToolContext 获取）
                String startUserId = null;
                if (context != null) {
                    Object userId = context.getContext().get("userId");
                    if (userId != null) {
                        startUserId = String.valueOf(userId);
                    }
                }

                WorkflowResult result = executionProvider.startProcess(
                        workflowInfo.getFlowKey(), businessKey, variables, startUserId);

                JSONObject output = new JSONObject();
                output.put("success", result.isSuccess());
                output.put("processInstanceId", result.getProcessInstanceId());
                output.put("status", result.getStatus());
                output.put("currentTaskName", result.getCurrentTaskName());
                output.put("message", result.getMessage());
                output.put("flowKey", workflowInfo.getFlowKey());
                output.put("flowName", workflowInfo.getFlowName());
                return output.toJSONString();
            } catch (Exception e) {
                log.error("[WorkflowToolCallback] 工作流调用失败 flowKey={} input={}",
                        workflowInfo.getFlowKey(), toolInput, e);
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("message", "工作流启动失败: " + e.getMessage());
                error.put("flowKey", workflowInfo.getFlowKey());
                return error.toJSONString();
            }
        }
    }
}
