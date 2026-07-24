package cn.com.mfish.common.ai.capability;

import java.util.Map;

/**
 * 工作流执行提供者接口
 * <p>
 * 跨模块解耦：mf-common-ai 模块的 {@link WorkflowCapabilityEngine} 通过此接口启动流程实例和查询流程状态，
 * 由 mf-workflow 业务层实现（对接 Flowable RuntimeService / HistoryService）。
 * </p>
 * <p>
 * AI 启动的流程实例不携带业务回调（callback 为空），流程结束时 CompleteCallbackHandler 跳过回调。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/23
 */
public interface WorkflowExecutionProvider {

    /**
     * 启动流程实例
     * <p>
     * 内部调用 Flowable {@code runtimeService.startProcessInstanceByKey(flowKey, businessKey, variables)}。
     * AI 启动的流程不设置 callback，流程结束时跳过业务回调。
     * </p>
     *
     * @param flowKey     流程定义key
     * @param businessKey 业务标识（AI 生成或用户指定，如 "ai-leave-001"）
     * @param variables   流程变量（如 days=3, reason="病假"）
     * @param startUserId 启动人用户ID（用于流程发起人记录）
     * @return 启动结果（含流程实例ID和初始状态）
     */
    WorkflowResult startProcess(String flowKey, String businessKey,
                                Map<String, Object> variables, String startUserId);

    /**
     * 查询流程实例状态
     *
     * @param processInstanceId 流程实例ID
     * @return 流程状态（RUNNING / COMPLETED / TERMINATED），含当前任务节点名称
     */
    WorkflowResult getProcessStatus(String processInstanceId);
}
