package cn.com.mfish.graph.ops.impl;

import cn.com.mfish.graph.config.RetryConfig;
import cn.com.mfish.graph.exception.BusinessException;
import cn.com.mfish.graph.ops.BatchCompensator;
import cn.com.mfish.graph.ops.BatchOperations;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批量操作补偿实现类
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class BatchCompensatorImpl implements BatchCompensator {
    private final RetryConfig retryConfig;
    private final Map<String, CompensateStatus> statusMap = new ConcurrentHashMap<>();

    public BatchCompensatorImpl(RetryConfig retryConfig) {
        this.retryConfig = retryConfig;
    }

    @Override
    public void compensate(BatchOperations.BatchResult failedBatch) {
        if (failedBatch == null || failedBatch.getFailedItems() == null || failedBatch.getFailedItems().isEmpty()) {
            log.info("无需补偿，没有失败项");
            return;
        }

        String batchId = failedBatch.getBatchId();
        statusMap.put(batchId, CompensateStatus.EXECUTING);

        try {
            log.info("开始补偿批量操作: batchId={}, 失败项数={}", batchId, failedBatch.getFailedItems().size());
            statusMap.put(batchId, CompensateStatus.SUCCESS);
        } catch (Exception e) {
            log.error("补偿失败: batchId={}", batchId, e);
            statusMap.put(batchId, CompensateStatus.FAILED);

            double failureRate = (double) failedBatch.getFailureCount() /
                (failedBatch.getSuccessCount() + failedBatch.getFailureCount());
            if (failureRate > retryConfig.getCompensateFailureAlertThreshold()) {
                log.error("补偿失败率超过阈值，触发告警: batchId={}, failureRate={}", batchId, failureRate);
                alertCompensateFailure(batchId, failedBatch);
            }
        }
    }

    @Override
    public CompensateStatus checkStatus(String batchId) {
        return statusMap.getOrDefault(batchId, CompensateStatus.PENDING);
    }

    @Override
    public void manualCompensate(String batchId) {
        log.info("人工触发补偿: batchId={}", batchId);
        statusMap.put(batchId, CompensateStatus.MANUAL_INTERVENTION);
    }

    @Override
    public double getCompensateFailureAlertThreshold() {
        return retryConfig.getCompensateFailureAlertThreshold();
    }

    private void alertCompensateFailure(String batchId, BatchOperations.BatchResult result) {
        log.error("批量操作补偿失败告警: batchId={}, successCount={}, failureCount={}, failureRate={}",
            batchId, result.getSuccessCount(), result.getFailureCount(),
            (double) result.getFailureCount() / (result.getSuccessCount() + result.getFailureCount()));
    }
}