package cn.com.mfish.common.ai.tool;

import java.util.List;

/**
 * 工具执行顺序提供者
 * <p>
 * 解耦 {@link FaultTolerantToolCallingManager} 与 SkillCapabilityEngine：
 * ToolCallingManager 位于工具调用层，不应直接依赖 Skill 能力引擎。
 * 通过此接口由上层（如 AiConfig）注入顺序来源，SkillCapabilityEngine 实现此接口。
 * </p>
 * <p>
 * 当 LLM 在单次响应中返回多个 tool call 时，{@link FaultTolerantToolCallingManager}
 * 通过此接口获取声明的工具执行顺序，对 tool calls 排序后执行。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/29
 */
public interface ToolOrderProvider {

    /**
     * 获取声明的工具执行顺序列表
     * <p>
     * 返回的列表中，靠前的工具应优先执行。不在列表中的工具保持 LLM 返回的原顺序，
     * 排到列表中已声明工具的后面。
     * </p>
     *
     * @return 工具执行顺序列表，空列表表示无顺序声明，按 LLM 返回顺序执行
     */
    List<String> getToolOrder();
}
