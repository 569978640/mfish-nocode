package cn.com.mfish.graph.schema;

import cn.com.mfish.graph.config.NebulaQuotaConfig;

import java.util.List;

/**
 * Schema 版本管理器接口
 * 管理 Schema 的版本变更、灰度发布和回滚
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface SchemaVersionManager {
    /**
     * 创建新版本
     *
     * @param schemaName Schema 名称
     * @param newDef 新版本定义
     * @return 版本信息
     */
    SchemaVersion createVersion(String schemaName, SchemaDefinition newDef);

    /**
     * 提交版本
     *
     * @param versionId 版本ID
     */
    void commitVersion(String versionId);

    /**
     * 回滚版本
     *
     * @param versionId 版本ID
     */
    void rollbackVersion(String versionId);

    /**
     * 获取版本历史
     *
     * @param schemaName Schema 名称
     * @return 版本列表
     */
    List<SchemaVersion> getVersionHistory(String schemaName);

    /**
     * 灰度发布
     *
     * @param versionId 版本ID
     * @param percentage 灰度百分比
     * @param grayStrategy 灰度策略
     */
    void grayPublish(String versionId, int percentage, GrayStrategy grayStrategy);

    /**
     * 检查是否应自动回滚
     *
     * @param versionId 版本ID
     * @return true=应回滚
     */
    boolean shouldAutoRollback(String versionId);

    /**
     * 设置版本存储介质
     *
     * @param storageType 存储类型
     */
    void setVersionStorage(NebulaQuotaConfig.VersionStorageType storageType);

    /**
     * 清理过期版本（保留最近 N 个版本）
     *
     * @param retainCount 保留数量
     */
    void cleanExpiredVersions(int retainCount);

    /**
     * 绑定业务查询到指定版本
     *
     * @param businessLine 业务线
     * @param schemaName Schema 名称
     * @param versionId 版本ID
     */
    void bindBusinessToVersion(String businessLine, String schemaName, String versionId);

    /**
     * 灰度策略接口
     */
    interface GrayStrategy {
        /**
         * 判断是否灰度
         *
         * @param businessTag 业务标签
         * @param userId 用户ID
         * @return true=灰度
         */
        boolean isGray(String businessTag, String userId);
    }

    /**
     * Schema 版本信息
     */
    class SchemaVersion {
        private String id;
        private String schemaName;
        private String definition;
        private String status;
        private int grayPercentage;
        private long createTime;
        private long updateTime;
    }

    /**
     * Schema 定义
     */
    class SchemaDefinition {
        private String schemaName;
        private String type;
        private Object definition;
    }
}