package cn.com.mfish.common.ai.capability;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 动作执行结果
 * <p>
 * 由 {@link CapabilitySubEngine#execute} 返回，封装执行状态、输出内容和错误信息。
 * 设计为不可变值对象：执行完成后构造，调用方只读。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/21
 */
@Data
@Accessors(chain = true)
public class ActionResult {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 执行输出（LLM 可读的文本，通常为 JSON 字符串或纯文本）
     * <p>
     * 成功时填充结果内容，失败时可为 null。
     * </p>
     */
    private String output;

    /**
     * 错误信息（失败时填充，成功时为 null）
     */
    private String error;

    /**
     * 来源引擎类型（便于 Planner 日志追踪和结果分流处理）
     */
    private EngineType engineType;

    /**
     * 执行耗时（毫秒）
     */
    private long durationMs;

    /**
     * 构建成功结果
     */
    public static ActionResult success(EngineType engineType, String output, long durationMs) {
        return new ActionResult()
                .setSuccess(true)
                .setOutput(output)
                .setEngineType(engineType)
                .setDurationMs(durationMs);
    }

    /**
     * 构建失败结果
     */
    public static ActionResult failure(EngineType engineType, String error, long durationMs) {
        return new ActionResult()
                .setSuccess(false)
                .setError(error)
                .setEngineType(engineType)
                .setDurationMs(durationMs);
    }
}
