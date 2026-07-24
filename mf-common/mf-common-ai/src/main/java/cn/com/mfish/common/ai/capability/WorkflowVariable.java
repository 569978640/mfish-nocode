package cn.com.mfish.common.ai.capability;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 工作流变量定义（供 LLM 了解流程需要什么参数）
 *
 * @author: mfish
 * @date: 2026/07/23
 */
@Data
@Accessors(chain = true)
public class WorkflowVariable {

    /**
     * 变量名
     */
    private String name;

    /**
     * 变量类型（string/integer/number/boolean）
     */
    private String type;

    /**
     * 变量描述
     */
    private String description;

    /**
     * 是否必填
     */
    private boolean required;
}
