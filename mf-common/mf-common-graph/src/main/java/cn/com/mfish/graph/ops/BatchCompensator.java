package cn.com.mfish.graph.ops;

/**
 * 批量操作补偿接口
 * 用于处理批量操作失败后的补偿逻辑
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface BatchCompensator {
    /**
     * 触发补偿
     *
     * @param failedBatch 失败的批量结果
     */
    void compensate(BatchOperations.BatchResult failedBatch);

    /**
     * 检查补偿状态
     *
     * @param batchId 批次 ID
     * @return 补偿状态
     */
    CompensateStatus checkStatus(String batchId);

    /**
     * 人工触发补偿
     *
     * @param batchId 批次 ID
     */
    void manualCompensate(String batchId);

    /**
     * 获取补偿失败告警阈值
     *
     * @return 阈值
     */
    double getCompensateFailureAlertThreshold();

    /**
     * 补偿状态枚举
     */
    enum CompensateStatus {
        /**
         * 待处理
         */
        PENDING,
        /**
         * 执行中
         */
        EXECUTING,
        /**
         * 成功
         */
        SUCCESS,
        /**
         * 失败
         */
        FAILED,
        /**
         * 需要人工介入
         */
        MANUAL_INTERVENTION
    }
}