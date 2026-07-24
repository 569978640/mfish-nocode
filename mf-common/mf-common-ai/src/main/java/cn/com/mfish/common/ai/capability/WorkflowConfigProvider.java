package cn.com.mfish.common.ai.capability;

import java.util.List;

/**
 * 工作流配置提供者接口
 * <p>
 * 跨模块解耦：mf-common-ai 模块的 {@link WorkflowCapabilityEngine} 通过此接口获取已发布的工作流配置，
 * 由 mf-workflow 业务层实现（查询 flw_mf_manage 表 released=1 的记录）。
 * </p>
 * <p>
 * 与 {@code FlowManageService} 解耦：mf-common-ai 不依赖 mf-workflow-api 模块。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/23
 */
public interface WorkflowConfigProvider {

    /**
     * 获取所有已发布的工作流配置
     * <p>
     * 对应数据库 flw_mf_manage 表中 released=1 且 del_flag=0 的记录。
     * </p>
     *
     * @return 已发布的工作流列表，无配置时返回空列表
     */
    List<WorkflowInfo> getActiveWorkflows();
}
