package cn.com.mfish.graph.ops.impl;

import cn.com.mfish.graph.config.RetryConfig;
import cn.com.mfish.graph.exception.BusinessException;
import cn.com.mfish.graph.model.GraphEdge;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.ops.BatchCompensator;
import cn.com.mfish.graph.ops.BatchOperations;
import cn.com.mfish.graph.ops.EdgeOperations;
import cn.com.mfish.graph.ops.NodeOperations;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批量操作实现类
 * 支持分片、进度追踪、重试
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class BatchOperationsImpl implements BatchOperations {
    private final NodeOperations nodeOperations;
    private final EdgeOperations edgeOperations;
    private final RetryConfig retryConfig;
    private final BatchCompensator compensator;
    private final Map<String, BatchProgress> progressMap = new ConcurrentHashMap<>();
    private volatile ShardingStrategy shardingStrategy = ShardingStrategy.HASH_BY_VID;

    public BatchOperationsImpl(NodeOperations nodeOperations,
                              EdgeOperations edgeOperations,
                              RetryConfig retryConfig,
                              BatchCompensator compensator) {
        this.nodeOperations = nodeOperations;
        this.edgeOperations = edgeOperations;
        this.retryConfig = retryConfig;
        this.compensator = compensator;
    }

    @Override
    public BatchResult batchInsertNodes(List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return createEmptyResult();
        }

        String batchId = generateBatchId();
        BatchProgress progress = createProgress(batchId, nodes.size());
        progressMap.put(batchId, progress);

        try {
            List<List<GraphNode>> shards = splitIntoShards(nodes);
            progress.setTotalShards(shards.size());

            int successCount = 0;
            List<String> failedItems = new ArrayList<>();

            for (int i = 0; i < shards.size(); i++) {
                List<GraphNode> shard = shards.get(i);
                progress.setCompletedShards(i);

                int retryCount = 0;
                boolean shardSuccess = false;

                while (retryCount < retryConfig.getMaxRetries() && !shardSuccess) {
                    try {
                        int count = nodeOperations.batchInsertNodes(shard);
                        if (count == shard.size()) {
                            shardSuccess = true;
                            successCount += count;
                        } else {
                            retryCount++;
                            if (retryCount < retryConfig.getMaxRetries()) {
                                Thread.sleep(retryConfig.getNextInterval(retryCount));
                            }
                        }
                    } catch (Exception e) {
                        retryCount++;
                        if (retryCount >= retryConfig.getMaxRetries()) {
                            for (GraphNode node : shard) {
                                failedItems.add(node.getId());
                            }
                            log.error("批量插入节点分片 {} 失败，已重试 {} 次", i, retryCount, e);
                        } else {
                            try {
                                Thread.sleep(retryConfig.getNextInterval(retryCount));
                            } catch (InterruptedException e) {
                                log.error("批量插入节点分片 {} 失败，已重试 {} 次，线程被中断", i, retryCount, e);
                            }
                        }
                    }
                }

                if (!shardSuccess) {
                    progress.setFailedShards(progress.getFailedShards() + 1);
                }
            }

            progress.setStatus(successCount == nodes.size() ? "COMPLETED" : "PARTIAL");
            progress.setLastUpdateTime(System.currentTimeMillis());

            BatchResult result = new BatchResult();
            result.setBatchId(batchId);
            result.setSuccessCount(successCount);
            result.setFailureCount(nodes.size() - successCount);
            result.setFailedItems(failedItems);

            if ((double) result.getFailureCount() / nodes.size() > retryConfig.getBatchFailureAlertThreshold()) {
                log.warn("批量操作失败率超过阈值: {}/{} = {}",
                    result.getFailureCount(), nodes.size(), (double) result.getFailureCount() / nodes.size());
                if (compensator != null) {
                    compensator.compensate(result);
                }
            }

            return result;
        } finally {
            progressMap.remove(batchId);
        }
    }

    @Override
    public BatchResult batchInsertEdges(List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return createEmptyResult();
        }

        String batchId = generateBatchId();
        BatchProgress progress = createProgress(batchId, edges.size());
        progressMap.put(batchId, progress);

        try {
            List<List<GraphEdge>> shards = splitEdgesIntoShards(edges);
            progress.setTotalShards(shards.size());

            int successCount = 0;
            List<String> failedItems = new ArrayList<>();

            for (int i = 0; i < shards.size(); i++) {
                List<GraphEdge> shard = shards.get(i);
                progress.setCompletedShards(i);

                int retryCount = 0;
                boolean shardSuccess = false;

                while (retryCount < retryConfig.getMaxRetries() && !shardSuccess) {
                    try {
                        int count = edgeOperations.batchInsertEdges(shard);
                        if (count == shard.size()) {
                            shardSuccess = true;
                            successCount += count;
                        } else {
                            retryCount++;
                            if (retryCount < retryConfig.getMaxRetries()) {
                                Thread.sleep(retryConfig.getNextInterval(retryCount));
                            }
                        }
                    } catch (Exception e) {
                        retryCount++;
                        if (retryCount >= retryConfig.getMaxRetries()) {
                            for (GraphEdge edge : shard) {
                                failedItems.add(edge.getFromId() + "->" + edge.getToId());
                            }
                            log.error("批量插入边分片 {} 失败，已重试 {} 次", i, retryCount, e);
                        } else {
                            Thread.sleep(retryConfig.getNextInterval(retryCount));
                        }
                    }
                }

                if (!shardSuccess) {
                    progress.setFailedShards(progress.getFailedShards() + 1);
                }
            }

            progress.setStatus(successCount == edges.size() ? "COMPLETED" : "PARTIAL");
            progress.setLastUpdateTime(System.currentTimeMillis());

            BatchResult result = new BatchResult();
            result.setBatchId(batchId);
            result.setSuccessCount(successCount);
            result.setFailureCount(edges.size() - successCount);
            result.setFailedItems(failedItems);

            return result;
        } finally {
            progressMap.remove(batchId);
        }
    }

    @Override
    public BatchResult batchMixedOperation(List<GraphNode> nodes, List<GraphEdge> edges) {
        BatchResult nodeResult = batchInsertNodes(nodes);
        BatchResult edgeResult = batchInsertEdges(edges);

        BatchResult combinedResult = new BatchResult();
        combinedResult.setBatchId(generateBatchId());
        combinedResult.setSuccessCount(nodeResult.getSuccessCount() + edgeResult.getSuccessCount());
        combinedResult.setFailureCount(nodeResult.getFailureCount() + edgeResult.getFailureCount());

        List<String> failedItems = new ArrayList<>();
        failedItems.addAll(nodeResult.getFailedItems());
        failedItems.addAll(edgeResult.getFailedItems());
        combinedResult.setFailedItems(failedItems);

        return combinedResult;
    }

    @Override
    public BatchProgress getProgress(String batchId) {
        return progressMap.get(batchId);
    }

    @Override
    public BatchResult retryFailedShards(String batchId) {
        return createEmptyResult();
    }

    public void setShardingStrategy(ShardingStrategy strategy) {
        this.shardingStrategy = strategy;
    }

    private List<List<GraphNode>> splitIntoShards(List<GraphNode> nodes) {
        List<List<GraphNode>> shards = new ArrayList<>();
        int shardSize = MAX_BATCH_SIZE;

        for (int i = 0; i < nodes.size(); i += shardSize) {
            int end = Math.min(i + shardSize, nodes.size());
            shards.add(nodes.subList(i, end));
        }

        return shards;
    }

    private List<List<GraphEdge>> splitEdgesIntoShards(List<GraphEdge> edges) {
        List<List<GraphEdge>> shards = new ArrayList<>();
        int shardSize = MAX_BATCH_SIZE;

        for (int i = 0; i < edges.size(); i += shardSize) {
            int end = Math.min(i + shardSize, edges.size());
            shards.add(edges.subList(i, end));
        }

        return shards;
    }

    private String generateBatchId() {
        return "BATCH-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private BatchProgress createProgress(String batchId, int totalItems) {
        BatchProgress progress = new BatchProgress();
        progress.setBatchId(batchId);
        progress.setTotalShards((int) Math.ceil((double) totalItems / MAX_BATCH_SIZE));
        progress.setCompletedShards(0);
        progress.setFailedShards(0);
        progress.setStartTime(System.currentTimeMillis());
        progress.setLastUpdateTime(System.currentTimeMillis());
        progress.setStatus("RUNNING");
        return progress;
    }

    private BatchResult createEmptyResult() {
        BatchResult result = new BatchResult();
        result.setBatchId(generateBatchId());
        result.setSuccessCount(0);
        result.setFailureCount(0);
        result.setFailedItems(new ArrayList<>());
        return result;
    }
}