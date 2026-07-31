package cn.com.mfish.common.ai.capability;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 工作流执行结果
 *
 * @author: mfish
 * @date: 2026/07/23
 */
@Data
@Accessors(chain = true)
public class WorkflowResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 流程状态（RUNNING / COMPLETED / TERMINATED）
     */
    private String status;

    /**
     * 当前任务节点名称
     */
    private String currentTaskName;

    /**
     * 消息（成功或错误信息）
     */
    private String message;
}
