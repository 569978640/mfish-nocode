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
