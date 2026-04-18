package cn.com.mfish.graph.index;

/**
 * 索引状态枚举
 */
public enum IndexStatus {
    /**
     * 创建中
     */
    CREATING,
    /**
     * 健康
     */
    HEALTHY,
    /**
     * 降级
     */
    DEGRADED,
    /**
     * 失效
     */
    INVALID
}