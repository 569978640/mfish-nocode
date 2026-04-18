package cn.com.mfish.graph.config;

import lombok.Data;

/**
 * NebulaGraph 动态 Schema 配额配置类
 * 配置 Schema 数量限制和版本管理参数
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class NebulaQuotaConfig {
    /**
     * 单个 Space 最大 Tag 数
     */
    private int maxTagsPerSpace = 100;

    /**
     * 单个 Space 最大 EdgeType 数
     */
    private int maxEdgeTypesPerSpace = 100;

    /**
     * 单个 Tag 最大字段数
     */
    private int maxFieldsPerTag = 50;

    /**
     * 单个 EdgeType 最大字段数
     */
    private int maxFieldsPerEdgeType = 50;

    /**
     * 动态 Schema 数量上限
     */
    private int maxDynamicSchemas = 50;

    /**
     * 配额校验间隔(s)，定时检测是否超配额
     */
    private int quotaCheckInterval = 300;

    /**
     * 版本存储介质
     */
    private VersionStorageType versionStorageType = VersionStorageType.NEBULA_GRAPH;

    /**
     * 历史版本保留数量
     */
    private int retainVersionCount = 10;

    /**
     * 版本存储介质枚举
     */
    public enum VersionStorageType {
        /**
         * MySQL 存储
         */
        MYSQL,
        /**
         * Redis 存储
         */
        REDIS,
        /**
         * Nebula 自身存储
         */
        NEBULA_GRAPH
    }
}