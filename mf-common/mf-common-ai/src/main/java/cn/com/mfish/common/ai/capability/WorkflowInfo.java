package cn.com.mfish.common.ai.capability;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 工作流信息（mf-common-ai 模块内的 DTO）
 * <p>
 * 与 {@code FlowManage} 实体类解耦：mf-common-ai 不依赖 mf-workflow-api 模块，
 * 通过 {@link WorkflowConfigProvider} 接口由业务层注入配置数据。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/23
 */
@Data
@Accessors(chain = true)
public class WorkflowInfo {

    /**
     * 流程定义key（唯一标识，对应 Flowable processDefinitionKey）
     */
    private String flowKey;

    /**
     * 流程名称
     */
    private String flowName;

    /**
     * 流程描述（供 LLM 理解流程用途）
     */
    private String description;

    /**
     * 流程变量定义列表（供 LLM 了解需要传什么参数）
     * 若为空，LLM 根据流程描述自由传入变量
     */
    private List<WorkflowVariable> variables;
}
