package cn.com.mfish.common.ai.capability;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 动作定义（Action Definition）
 * <p>
 * 能力引擎向 Planner 暴露的单个可执行动作的元数据，遵循 OpenAI Tool 格式。
 * 一个 {@link CapabilitySubEngine} 可暴露多个 Action，{@link CapabilityEngine}
 * 将所有子引擎的 Action 合并为统一列表供 Planner 选择。
 * </p>
 * <p>
 * <b>与 Spring AI ToolCallback 的关系</b>：
 * <ul>
 *     <li>对于 TOOL 引擎：ActionDefinition 由 {@code ToolCallback.getToolDefinition()} 转换而来</li>
 *     <li>对于 SKILL/MCP/WORKFLOW 引擎：ActionDefinition 由各引擎自行构建</li>
 *     <li>Planner 可将 ActionDefinition 转换回 ToolCallback 供 ChatClient 使用，
 *         也可通过 {@code CapabilityEngine.executeAction()} 直接执行</li>
 * </ul>
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/21
 */
@Data
@Accessors(chain = true)
public class ActionDefinition {

    /**
     * 动作名称（全局唯一，建议加引擎前缀避免冲突，如 {@code tool.getUserList}、{@code skill.parseFile}）
     */
    private String name;

    /**
     * 动作描述（供 LLM 理解动作用途，决定是否选择此动作）
     */
    private String description;

    /**
     * 输入参数 JSON Schema（OpenAI Tool 格式，描述参数类型和结构）
     * <p>
     * 示例：
     * <pre>{@code
     * {
     *   "type": "object",
     *   "properties": {
     *     "userId": { "type": "string", "description": "用户ID" }
     *   },
     *   "required": ["userId"]
     * }
     * }</pre>
     * </p>
     */
    private String inputSchema;

    /**
     * 来源引擎类型
     */
    private EngineType engineType;

    /**
     * 来源服务ID（仅 TOOL 引擎有值，标识来自哪个微服务；其他引擎为 null）
     */
    private String serviceId;
}
