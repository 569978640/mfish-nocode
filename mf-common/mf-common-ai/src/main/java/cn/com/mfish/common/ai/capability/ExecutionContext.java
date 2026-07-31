package cn.com.mfish.common.ai.capability;

import cn.com.mfish.common.ai.agent.TenantContext;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

/**
 * 能力执行上下文
 * <p>
 * 封装动作执行时所需的环境信息，由调用方（Planner / Executor / BaseAssistant）构建，
 * 传递给 {@link CapabilitySubEngine#execute}。
 * </p>
 * <p>
 * <b>与 Spring AI ToolContext 的关系</b>：
 * <ul>
 *     <li>ToolContext（{@code Map<String, Object>}）是 Spring AI 的工具执行上下文，
 *         由 ChatClient 框架在调用 ToolCallback 时注入</li>
 *     <li>ExecutionContext 是能力引擎层的抽象，更结构化地封装租户信息和自定义属性</li>
 *     <li>对于 TOOL 引擎：{@link ToolCapabilityEngine} 会从 ExecutionContext 提取信息，
 *         转换为 Spring AI ToolContext 传给 ToolCallback</li>
 *     <li>对于 SKILL/MCP/WORKFLOW 引擎：直接使用 ExecutionContext 的字段</li>
 * </ul>
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/21
 */
@Data
@Accessors(chain = true)
public class ExecutionContext {

    /**
     * 租户上下文（包含 tenantId/userId/accessToken/请求上下文快照）
     * <p>
     * 由请求线程捕获，供异步执行线程恢复认证上下文。
     * 可为 null（如离线测试场景），各子引擎需做空值防御。
     * </p>
     */
    private TenantContext tenantContext;

    /**
     * 会话ID（用于日志追踪和会话级状态关联）
     */
    private String sessionId;

    /**
     * 自定义属性（扩展通道，子引擎可按约定存取特定 key）
     * <p>
     * 常用 key（可选，非强制约定）：
     * <ul>
     *     <li>{@code requestAttributes} —— Servlet RequestAttributes</li>
     *     <li>{@code serverWebExchange} —— WebFlux ServerWebExchange</li>
     *     <li>{@code actionTraceId} —— 动作执行追踪ID</li>
     * </ul>
     * </p>
     */
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * 添加自定义属性
     */
    public ExecutionContext addAttribute(String key, Object value) {
        if (this.attributes == null) {
            this.attributes = new HashMap<>();
        }
        this.attributes.put(key, value);
        return this;
    }

    /**
     * 获取自定义属性
     */
    public Object getAttribute(String key) {
        return this.attributes != null ? this.attributes.get(key) : null;
    }

    /**
     * 从 TenantContext 快速构建执行上下文
     */
    public static ExecutionContext of(TenantContext tenantContext, String sessionId) {
        return new ExecutionContext()
                .setTenantContext(tenantContext)
                .setSessionId(sessionId);
    }
}
