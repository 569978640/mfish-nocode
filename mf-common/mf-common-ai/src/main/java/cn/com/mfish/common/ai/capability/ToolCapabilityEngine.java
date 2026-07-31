package cn.com.mfish.common.ai.capability;

import cn.com.mfish.common.ai.agent.TenantContext;
import cn.com.mfish.common.ai.engine.ApiToolEngine;
import cn.com.mfish.common.core.constants.RPCConstants;
import cn.com.mfish.common.core.utils.AuthInfoUtils;
import cn.com.mfish.common.core.utils.StringUtils;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.server.ServerWebExchange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具能力引擎适配器
 * <p>
 * 将现有 {@link ApiToolEngine} 包装为 {@link CapabilitySubEngine}，
 * 使 Feign/OpenAPI 工具能纳入 CapabilityEngine 的统一能力发现与执行体系。
 * </p>
 * <p>
 * <b>适配逻辑</b>：
 * <ul>
 *     <li>{@link #getActions()}：遍历 ApiToolEngine 所有 ToolCallback，
 *         将 {@code ToolDefinition}（name/description/inputSchema）转换为 {@link ActionDefinition}</li>
 *     <li>{@link #execute}：按 actionName 查找 ToolCallback，
 *         将 params Map 序列化为 JSON 字符串，构建 Spring AI ToolContext，
 *         调用 {@code ToolCallback.call(toolInput, toolContext)}</li>
 * </ul>
 * </p>
 * <p>
 * <b>与 LLM 驱动模式的关系</b>：
 * <ul>
 *     <li>LLM 驱动模式：BaseAssistant 直接调用 {@code apiToolEngine.getToolCallbackProvider(serviceIds)}
 *         传给 ChatClient，由 LLM 决定调用哪个工具。此模式不经过 ToolCapabilityEngine。</li>
 *     <li>显式调用模式：Planner 通过 {@code capabilityEngine.executeAction(actionName, params, ctx)}
 *         直接执行指定动作。此模式经过 ToolCapabilityEngine。</li>
 *     <li>两种模式共享同一套 ToolCallback 实例，执行结果一致。</li>
 * </ul>
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/21
 */
@Slf4j
public class ToolCapabilityEngine implements CapabilitySubEngine {

    private final ApiToolEngine apiToolEngine;

    public ToolCapabilityEngine(ApiToolEngine apiToolEngine) {
        this.apiToolEngine = apiToolEngine;
    }

    @Override
    public EngineType getEngineType() {
        return EngineType.TOOL;
    }

    /**
     * 将 ApiToolEngine 中所有 ToolCallback 转换为 ActionDefinition
     * <p>
     * 动作名直接使用 ToolCallback 的原始 name（与 LLM 驱动模式保持一致），
     * 跨服务同名动作由 ApiToolEngine 去重策略保证唯一。
     * serviceId 字段标识动作来源服务，便于 Planner 按服务过滤。
     * </p>
     */
    @Override
    public List<ActionDefinition> getActions() {
        List<ActionDefinition> actions = new ArrayList<>();
        for (String serviceId : apiToolEngine.getAllServiceIds()) {
            ToolCallbackProvider provider =
                    apiToolEngine.getToolCallbackProvider(serviceId);
            if (provider == null) {
                continue;
            }
           ToolCallback[] callbacks = provider.getToolCallbacks();
            for (ToolCallback tc : callbacks) {
                try {
                    ToolDefinition td = tc.getToolDefinition();
                    actions.add(new ActionDefinition()
                            .setName(td.name())
                            .setDescription(td.description())
                            .setInputSchema(td.inputSchema())
                            .setEngineType(EngineType.TOOL)
                            .setServiceId(serviceId));
                } catch (Exception e) {
                    log.warn("[ToolCapabilityEngine] 转换动作失败 serviceId={}", serviceId, e);
                }
            }
        }
        return actions;
    }

    /**
     * 执行工具动作
     * <p>
     * 通过 ApiToolEngine 查找 actionName 对应的 ToolCallback，
     * 将 params 序列化为 JSON 字符串作为 toolInput，
     * 从 ExecutionContext 提取租户信息构建 ToolContext，
     * 调用 {@code ToolCallback.call(toolInput, toolContext)}。
     * </p>
     */
    @Override
    public ActionResult execute(String actionName, Map<String, Object> params, ExecutionContext ctx) {
        long start = System.currentTimeMillis();
        ToolCallback callback = apiToolEngine.findToolCallback(actionName);
        if (callback == null) {
            return ActionResult.failure(EngineType.TOOL,
                    "未找到工具动作: " + actionName, System.currentTimeMillis() - start);
        }

        // 构建 Spring AI ToolContext（复用 BaseAssistant 的 context key 约定）
        ToolContext toolContext = buildToolContext(ctx);

        // params 序列化为 JSON 字符串
        String toolInput = (params == null || params.isEmpty())
                ? "{}"
                : JSON.toJSONString(params);

        try {
            String result = callback.call(toolInput, toolContext);
            return ActionResult.success(EngineType.TOOL, result, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[ToolCapabilityEngine] 执行工具动作失败 name={} input={}",
                    actionName, toolInput, e);
            return ActionResult.failure(EngineType.TOOL,
                    "工具执行异常: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * 从 ExecutionContext 构建 Spring AI ToolContext
     * <p>
     * 复用 {@code BaseAssistant.buildToolContextFromSnapshot} 的 key 约定
     * （RPCConstants.REQ_* 常量），保证 FeignToolCallback/HttpToolCallback
     * 的内部参数自动填充逻辑一致。
     * </p>
     */
    private ToolContext buildToolContext(ExecutionContext ctx) {
        Map<String, Object> contextMap = new HashMap<>();

        if (ctx != null && ctx.getTenantContext() != null) {
            TenantContext tc = ctx.getTenantContext();
            contextMap.put(RPCConstants.REQ_USER_ID, tc.getUserId() != null ? tc.getUserId() : "");
            contextMap.put(RPCConstants.REQ_TENANT_ID,
                    tc.getTenantId() != null ? tc.getTenantId() : AuthInfoUtils.SUPER_TENANT_ID);
            contextMap.put(RPCConstants.REQ_ORIGIN, RPCConstants.AI);
            if (StringUtils.isNotEmpty(tc.getAccessToken())) {
                contextMap.put(RPCConstants.REQ_TOKEN, tc.getAccessToken());
            }
            if (tc.getRequestAttributes() instanceof RequestAttributes ra) {
                contextMap.put(RPCConstants.REQ_REQUEST_ATTRIBUTES, ra);
            }
            if (tc.getServerWebExchange() instanceof ServerWebExchange swe) {
                contextMap.put(RPCConstants.REQ_SERVER_WEB_EXCHANGE, swe);
            }
        } else {
            // 无 TenantContext 时填充默认值，避免内部参数解析失败
            contextMap.put(RPCConstants.REQ_USER_ID, "");
            contextMap.put(RPCConstants.REQ_TENANT_ID, AuthInfoUtils.SUPER_TENANT_ID);
            contextMap.put(RPCConstants.REQ_ORIGIN, RPCConstants.AI);
        }

        return new ToolContext(contextMap);
    }
}
