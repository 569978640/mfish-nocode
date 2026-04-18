package cn.com.mfish.graph.ops;

import cn.com.mfish.graph.model.GraphEdge;
import cn.com.mfish.graph.model.GraphNode;
import lombok.Data;

import java.util.List;

/**
 * 批量操作接口
 * 支持批量节点/边操作，带分片和进度追踪
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface BatchOperations {
    /**
     * 批量操作配置
     */
    int MAX_BATCH_SIZE = 1000;

    /**
     * 批量插入节点（自动分片）
     *
     * @param nodes 节点列表
     * @return 批量结果
     */
    BatchResult batchInsertNodes(List<GraphNode> nodes);

    /**
     * 批量插入边（自动分片）
     *
     * @param edges 边列表
     * @return 批量结果
     */
    BatchResult batchInsertEdges(List<GraphEdge> edges);

    /**
     * 批量混合操作（节点+边，原子提交）
     *
     * @param nodes 节点列表
     * @param edges 边列表
     * @return 批量结果
     */
    BatchResult batchMixedOperation(List<GraphNode> nodes, List<GraphEdge> edges);

    /**
     * 获取批量操作进度
     *
     * @param batchId 批次 ID
     * @return 进度信息
     */
    BatchProgress getProgress(String batchId);

    /**
     * 重试失败分片
     *
     * @param batchId 批次 ID
     * @return 重试结果
     */
    BatchResult retryFailedShards(String batchId);

    /**
     * 批量操作分片策略
     */
    enum ShardingStrategy {
        /**
         * 按 VID 哈希分片（避免热点）
         */
        HASH_BY_VID,
        /**
         * 按固定大小分片
         */
        FIXED_SIZE,
        /**
         * 按 VID 范围分片
         */
        RANGE
    }

    /**
     * 批量操作进度
     */
    @Data
    class BatchProgress {
        private String batchId;
        private int totalShards;
        private int completedShards;
        private int failedShards;
        private long startTime;
        private long lastUpdateTime;
        private String status;
    }

    /**
     * 批量操作结果
     */
    @Data
    class BatchResult {
        private String batchId;
        private int successCount;
        private int failureCount;
        private List<String> failedItems;
        private String errorMessage;
    }
}