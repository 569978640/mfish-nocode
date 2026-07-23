package cn.com.mfish.common.ai.capability;

/**
 * 能力子引擎类型
 * <p>
 * 标识 {@link CapabilitySubEngine} 的具体来源，供 Planner 决策时区分能力来源，
 * 也用于日志和调试。新增引擎类型时在此枚举扩展。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/21
 */
public enum EngineType {

    /**
     * 工具引擎：基于 Feign/OpenAPI 的微服务接口工具
     * <p>
     * 适配现有 {@code ApiToolEngine} + {@code ToolProvider} 体系，
     * 将 Spring AI {@code ToolCallback} 转换为 {@link ActionDefinition}。
     * </p>
     */
    TOOL,

    /**
     * 技能引擎：Java 编写的预置技能（如文件解析、数据格式化、SQL 生成等）
     * <p>
     * 与 TOOL 的区别：TOOL 是远程接口的包装，SKILL 是本地 Java 逻辑的包装。
     * SKILL 不依赖网络调用，执行延迟低、结果确定性强。
     * </p>
     */
    SKILL,

    /**
     * MCP 引擎：Model Context Protocol 客户端工具
     * <p>
     * 对接外部 MCP Server，将 MCP 工具转换为统一的 ActionDefinition。
     * 当前为预留类型，待 Spring AI MCP 集成后实现。
     * </p>
     */
    MCP,

    /**
     * 工作流引擎：基于 Flowable/BPMN 的流程编排能力
     * <p>
     * 将工作流的"发起流程/审批/查询任务"等操作封装为 Action，
     * 供 Planner 在需要人工审批或复杂流程编排时调用。
     * </p>
     */
    WORKFLOW;
}
