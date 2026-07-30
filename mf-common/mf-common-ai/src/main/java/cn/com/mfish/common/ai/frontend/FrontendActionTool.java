package cn.com.mfish.common.ai.frontend;

import cn.com.mfish.common.ai.entity.FrontendAction;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 前端操作工具 — 将 AI 的前端操作意图转化为 FrontendAction 指令
 * <p>
 * 注册到 {@code ApiToolEngine} 的 {@code frontend} serviceId 下，提供以下工具：
 * <ul>
 *   <li>{@code frontend.navigate} — 路由跳转</li>
 *   <li>{@code frontend.click} — 模拟点击</li>
 *   <li>{@code frontend.fill} — 表单填充</li>
 *   <li>{@code frontend.refresh} — 刷新页面/组件</li>
 *   <li>{@code frontend.openModal} — 打开模态框</li>
 * </ul>
 * </p>
 * <p>
 * <b>工作原理</b>：
 * <ol>
 *   <li>LLM 调用工具，传入 action/target/params 参数</li>
 *   <li>工具构建 {@link FrontendAction} 对象，存入 {@link FrontendActionHolder}（基于 sessionId 的 ConcurrentHashMap）</li>
 *   <li>工具返回 JSON 字符串告知 LLM 操作已下发</li>
 *   <li>{@code BaseAssistant.chat()} 在流式响应结束后，
 *       从 {@link FrontendActionHolder} 取出累积的 action 列表，
 *       作为 {@code FRONTEND_ACTION} 事件通过 SSE 下发给前端</li>
 * </ol>
 * </p>
 * <p>
 * <b>sessionId 通道设计</b>：
 * Spring AI 的 ToolCallingManager 在工具执行线程调用 {@code ToolCallback.call()}，
 * 而流式响应在 reactive 链路处理，两者可能跨线程。
 * 使用基于 sessionId 的 {@link ConcurrentHashMap} 替代 ThreadLocal，
 * 在工具调用与流式响应之间传递 action，解决响应式编程中线程切换丢失数据的问题。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/24
 */
@Slf4j
public class FrontendActionTool implements ToolCallback {

    /** serviceId 前缀，用于注册到 ApiToolEngine 和排除标准微服务过滤 */
    public static final String FRONTEND_SERVICE_ID = "frontend";

    private final String toolName;
    private final String toolDescription;
    private final String inputSchema;
    private final String defaultAction;

    /**
     * @param toolName       工具名（如 frontend.navigate）
     * @param toolDescription 工具描述（供 LLM 理解工具用途）
     * @param inputSchema    JSON Schema 输入参数定义
     * @param defaultAction  默认 action 类型（navigate/click/fill/refresh/openModal）
     */
    public FrontendActionTool(String toolName, String toolDescription, String inputSchema, String defaultAction) {
        this.toolName = toolName;
        this.toolDescription = toolDescription;
        this.inputSchema = inputSchema;
        this.defaultAction = defaultAction;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return new DefaultToolDefinition(toolName, toolDescription, inputSchema);
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return ToolMetadata.builder().build();
    }

    @Override
    public @NonNull String call(@NonNull String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(@NonNull String toolInput, ToolContext toolContext) {
        try {
            JSONObject input = JSON.parseObject(toolInput);
            String target = input.getString("target");
            String description = input.getString("description");
            JSONObject paramsObj = input.getJSONObject("params");
            Map<String, Object> params = paramsObj != null ? new HashMap<>(paramsObj) : new HashMap<>();

            String action = input.getString("action");
            if (action == null || action.isEmpty()) {
                action = defaultAction;
            }

            FrontendAction fa = new FrontendAction()
                    .setAction(action)
                    .setName(toolName)
                    .setTarget(target)
                    .setParams(params)
                    .setDescription(description);

            // 从 ToolContext 获取 sessionId，基于 sessionId 存储（解决响应式线程切换问题）
            String sessionId = extractSessionId(toolContext);
            FrontendActionHolder.add(sessionId, fa);
            log.info("[FrontendActionTool] 工具调用 {} sessionId={} action={} target={}",
                    toolName, sessionId, action, target);

            return "{\"success\":true,\"message\":\"前端操作指令已下发: " + action + " " + target + "\"}";
        } catch (Exception e) {
            log.error("[FrontendActionTool] 工具调用失败 {} input={}", toolName, toolInput, e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 从 ToolContext 中提取 sessionId
     * <p>
     * BaseAssistant 在构建 ToolContext 时会传入 sessionId（key 为 {@link FrontendActionHolder#CTX_SESSION_ID}），
     * 用于跨线程传递前端操作指令。若未传入，返回 null（此时 FrontendActionHolder.add 为空操作）。
     * </p>
     */
    private String extractSessionId(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Object sessionId = toolContext.getContext().get(FrontendActionHolder.CTX_SESSION_ID);
        return sessionId != null ? sessionId.toString() : null;
    }

    // ===================== 预定义工具工厂方法 =====================

    /**
     * 创建所有前端操作工具集合
     */
    public static List<ToolCallback> createAll() {
        List<ToolCallback> tools = new ArrayList<>();
        tools.add(createNavigate());
        tools.add(createClick());
        tools.add(createFill());
        tools.add(createRefresh());
        tools.add(createOpenModal());
        return tools;
    }

    /**
     * frontend.navigate — 路由跳转
     * <p>
     * LLM 调用示例：{"target": "/demo-leave-apply", "description": "打开请假申请页面"}
     * </p>
     */
    public static FrontendActionTool createNavigate() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "target": {
                      "type": "string",
                      "description": "路由路径，如 /demo-leave-apply、/sys/menu、/workflow/apply"
                    },
                    "params": {
                      "type": "object",
                      "description": "路由 query 参数（可选）",
                      "additionalProperties": true
                    },
                    "description": {
                      "type": "string",
                      "description": "操作说明，供用户理解即将发生的操作（可选）"
                    }
                  },
                  "required": ["target"]
                }""";
        return new FrontendActionTool(
                "frontend.navigate",
                "跳转到指定页面路由。target 为路由路径（如 /demo-leave-apply）。适用于需要打开新页面的场景。",
                schema,
                FrontendAction.NAVIGATE);
    }

    /**
     * frontend.click — 模拟点击按钮/链接
     */
    public static FrontendActionTool createClick() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "target": {
                      "type": "string",
                      "description": "元素选择器，如 #submit-btn、.ant-btn-primary、[data-action='save']"
                    },
                    "description": {
                      "type": "string",
                      "description": "操作说明（可选）"
                    }
                  },
                  "required": ["target"]
                }""";
        return new FrontendActionTool(
                "frontend.click",
                "点击页面上指定元素。target 为 CSS 选择器。适用于触发按钮、链接等交互元素。",
                schema,
                FrontendAction.CLICK);
    }

    /**
     * frontend.fill — 表单填充
     */
    public static FrontendActionTool createFill() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "target": {
                      "type": "string",
                      "description": "表单容器选择器（可选，默认为当前页面表单）"
                    },
                    "params": {
                      "type": "object",
                      "description": "表单字段名→值的映射，如 title=请假申请, leaveType=1",
                      "additionalProperties": true
                    },
                    "description": {
                      "type": "string",
                      "description": "操作说明（可选）"
                    }
                  },
                  "required": ["params"]
                }""";
        return new FrontendActionTool(
                "frontend.fill",
                "填充表单字段。params 为字段名→值的映射。适用于帮用户预填表单数据的场景。",
                schema,
                FrontendAction.FILL);
    }

    /**
     * frontend.refresh — 刷新页面/组件
     */
    public static FrontendActionTool createRefresh() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "target": {
                      "type": "string",
                      "description": "刷新目标（可选，默认刷新当前页面）。可以是路由路径或组件选择器"
                    },
                    "description": {
                      "type": "string",
                      "description": "操作说明（可选）"
                    }
                  }
                }""";
        return new FrontendActionTool(
                "frontend.refresh",
                "刷新当前页面或指定组件的数据。适用于数据变更后需要刷新列表的场景。",
                schema,
                FrontendAction.REFRESH);
    }

    /**
     * frontend.openModal — 打开模态框
     */
    public static FrontendActionTool createOpenModal() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "target": {
                      "type": "string",
                      "description": "模态框标识或触发按钮选择器"
                    },
                    "params": {
                      "type": "object",
                      "description": "模态框参数（如标题、宽度、初始数据等）",
                      "additionalProperties": true
                    },
                    "description": {
                      "type": "string",
                      "description": "操作说明（可选）"
                    }
                  },
                  "required": ["target"]
                }""";
        return new FrontendActionTool(
                "frontend.openModal",
                "打开模态框/弹窗。target 为模态框标识或触发按钮选择器，params 携带模态框初始参数。",
                schema,
                FrontendAction.OPEN_MODAL);
    }
}
