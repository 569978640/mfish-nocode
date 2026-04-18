package cn.com.mfish.graph.index;

/**
 * 索引管理器接口
 * 管理索引的创建、重建、删除和健康检查
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface IndexManager {
    /**
     * 创建 Tag 索引
     *
     * @param tagName Tag 名称
     * @param propertyName 属性名称
     */
    void createTagIndex(String tagName, String propertyName);

    /**
     * 创建 Edge 索引
     *
     * @param edgeTypeName EdgeType 名称
     * @param propertyName 属性名称
     */
    void createEdgeIndex(String edgeTypeName, String propertyName);

    /**
     * 重建索引
     *
     * @param indexName 索引名称
     * @return 作业ID
     */
    String rebuildIndex(String indexName);

    /**
     * 等待索引就绪
     *
     * @param jobId 作业ID
     * @param timeoutSeconds 超时时间（秒）
     * @return true=就绪，false=超时
     */
    boolean waitForIndexReady(String jobId, long timeoutSeconds);

    /**
     * 删除索引
     *
     * @param indexName 索引名称
     */
    void dropIndex(String indexName);

    /**
     * 获取索引状态
     *
     * @param indexName 索引名称
     * @return 索引状态
     */
    IndexStatus getIndexStatus(String indexName);

    /**
     * 健康检查
     */
    void healthCheck();

    /**
     * 清理未使用的索引
     */
    void cleanupUnusedIndexes();
}