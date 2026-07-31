package cn.com.mfish.common.ai.capability;

import java.util.List;
import java.util.Map;

/**
 * 能力子引擎接口
 * <p>
 * 屏蔽异构执行细节的统一抽象。每种能力来源（Tool / Skill / MCP / Workflow）
 * 实现此接口，由 {@link CapabilityEngine} 门面组合所有子引擎实例。
 * </p>
 * <p>
 * <b>三种执行模式的关系</b>：
 * <ul>
 *     <li><b>LLM 驱动模式</b>：Planner 将 {@link #getActions()} 的结果作为工具列表传给 ChatClient，
 *         由 LLM 决定调用哪个动作、传什么参数。TOOL 引擎目前走此模式。</li>
 *     <li><b>显式调用模式</b>：Planner 直接通过 {@link #execute} 指定 actionName 和参数执行。
 *         SKILL/MCP/WORKFLOW 引擎适合此模式。</li>
 *     <li><b>混合模式</b>：Planner 先用 getActions() 让 LLM 决策，
 *         再用 execute() 执行 LLM 选择的动作。两种模式可共存。</li>
 * </ul>
 * </p>
 * <p>
 * <b>线程安全</b>：实现类需保证 getActions() 和 execute() 线程安全，
 * 因为可能被多个请求线程并发调用。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/21
 */
public interface CapabilitySubEngine {

    /**
     * 引擎类型识别
     *
     * @return 引擎类型枚举（TOOL / SKILL / MCP / WORKFLOW）
     */
    EngineType getEngineType();

    /**
     * 向 Planner 暴露的可用动作元数据
     * <p>
     * 遵循 OpenAI Tool 格式，{@link CapabilityEngine} 将所有子引擎的返回值合并为统一列表。
     * 动作名需全局唯一（建议加引擎前缀，如 {@code tool.xxx}、{@code skill.xxx}）。
     * </p>
     *
     * @return 动作定义列表，无可用动作时返回空列表
     */
    List<ActionDefinition> getActions();

    /**
     * 执行动作
     * <p>
     * 显式调用入口：按 actionName 查找并执行对应动作。
     * 实现类需处理 actionName 不存在的情况（返回 failure 结果，不抛异常）。
     * </p>
     *
     * @param actionName 动作名称（需与 {@link ActionDefinition#getName()} 一致）
     * @param params     动作参数（key 为参数名，value 为参数值）
     * @param ctx        执行上下文（租户信息、会话ID、自定义属性）
     * @return 执行结果
     */
    ActionResult execute(String actionName, Map<String, Object> params, ExecutionContext ctx);
}
