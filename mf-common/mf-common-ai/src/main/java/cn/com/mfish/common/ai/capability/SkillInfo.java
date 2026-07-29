package cn.com.mfish.common.ai.capability;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Skill 配置信息（文件式加载，一 Skill 一动作）
 * <p>
 * 由 {@link SkillFileLoader} 解析 {@code skills/*.md} 文件生成。
 * 一个 .md 文件 = 一个 SkillInfo = 一个可被 LLM 调用的动作。
 * </p>
 * <p>
 * 动作名约定：{@code skill.{skillCode}}（一 Skill 一动作，不再加二级动作名）。
 * serviceId：{@code skill-{skillCode}}。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/22
 */
@Data
@Accessors(chain = true)
public class SkillInfo {

    /**
     * Skill 唯一编码（来自 frontmatter 的 name 字段）
     * <p>用于动作名前缀 skill.{code} 和 serviceId skill-{code}</p>
     */
    private String skillCode;

    /**
     * Skill 显示名称（来自 frontmatter 的 title 字段，缺省时用 skillCode）
     */
    private String skillName;

    /**
     * Skill 类型（来自 frontmatter 的 type 字段，缺省 prompt）
     * <ul>
     *   <li>{@code prompt}：提示词型，内部调 LLM 处理后返回结果（如翻译、摘要）</li>
     *   <li>{@code guide}：指南型，直接返回 markdown 内容给外层 LLM，
     *       指导其调用其他工具完成多步编排（如请假流程需先 add 再 submit）。
     *       不走内部 LLM 调用，避免幻觉（LLM 假装已执行但实际未调工具）</li>
     * </ul>
     */
    private String type = "prompt";

    /**
     * 依赖的业务服务ID列表（来自 frontmatter 的 requires 字段，逗号分隔）
     * <p>
     * guide 类型 Skill 声明其编排流程需要调用的业务服务，如 {@code mf-demo,mf-sys}。
     * Planner 规划时会将这些 serviceId 与 skill 的 serviceId 合并，
     * 确保 Executor 执行时 LLM 能看到被指南引用的业务工具。
     * </p>
     * <p>
     * prompt 类型 Skill 不需要此字段（内部调 LLM，不编排外部工具）。
     * </p>
     */
    private List<String> requires;

    /**
     * 工具执行顺序声明（来自 frontmatter 的 toolOrder 字段，逗号分隔）
     * <p>
     * guide 类型 Skill 声明其编排流程中工具的执行顺序，如：
     * {@code frontend.navigate,demoLeaveApply.add,demoLeaveApply.submit,frontend.refresh}
     * </p>
     * <p>
     * 当 LLM 在单次响应中返回多个 tool call 时，{@link cn.com.mfish.common.ai.tool.FaultTolerantToolCallingManager}
     * 会根据此顺序对 tool calls 排序，确保工具按 skill 指定的顺序执行。
     * 不在此列表中的工具保持 LLM 返回的原顺序，排到列表中已声明工具的后面。
     * </p>
     * <p>
     * 未声明 toolOrder 的 Skill 不进行重排序，按 LLM 返回顺序执行。
     * </p>
     */
    private List<String> toolOrder;

    /**
     * 延迟到文本之后下发的工具列表（来自 frontmatter 的 deferredTools 字段，逗号分隔）
     * <p>
     * guide 类型 Skill 声明哪些工具的操作结果需要延迟到 LLM 文本流完成后才下发。
     * Spring AI 机制下，LLM 调用工具后才会生成最终文本，所有工具调用都在文本之前。
     * 但某些操作（如 frontend.refresh 页面刷新）语义上应在文本反馈之后执行，
     * 通过此字段声明后，{@link cn.com.mfish.common.ai.frontend.FrontendActionHolder}
     * 会将这些工具的操作存入延迟通道，等文本流完成后再下发。
     * </p>
     * <p>
     * 例如 leave-apply.md 声明 {@code deferredTools: frontend.refresh}，
     * 则 refresh 操作会延迟到文本之后下发，实现：
     * navigate → add → submit → 文本 → refresh → STOP
     * </p>
     */
    private List<String> deferredTools;

    /**
     * 动作描述（来自 frontmatter 的 description 字段，供 LLM 理解用途）
     */
    private String description;

    /**
     * 提示词模板（.md 文件 frontmatter 之后的正文，支持 {param} 变量占位）
     */
    private String promptTemplate;

    /**
     * 指定调用的模型名称（来自 frontmatter 的 model 字段，为空则使用租户默认模型）
     */
    private String modelName;

    /**
     * 参数名列表（从 promptTemplate 的 {param} 占位符自动提取）
     */
    private List<String> params;

    /**
     * 输入参数 JSON Schema（基于 params 自动生成，所有参数为 string 类型）
     */
    private String inputSchema;

    /**
     * 源文件路径（用于日志定位，如 classpath:skills/translator.md）
     */
    private String source;
}
