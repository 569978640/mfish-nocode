package cn.com.mfish.graph.schema;

import cn.com.mfish.graph.schema.model.FieldDefinition;

import java.util.Set;

/**
 * 固定 Schema 管理器接口
 * 管理 PLM 基础类型（SsoOrg/Product/Part 等）
 * 运行时禁止修改基础字段，仅允许扩展新字段
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface FixedSchemaManager {
    /**
     * 初始化基础 Schema（应用启动时调用）
     */
    void initialize();

    /**
     * 获取所有已注册的基础 Tag
     *
     * @return Tag 名称集合
     */
    Set<String> getRegisteredTags();

    /**
     * 获取所有已注册的基础 EdgeType
     *
     * @return EdgeType 名称集合
     */
    Set<String> getRegisteredEdgeTypes();

    /**
     * 检查 Tag 是否为基础类型
     *
     * @param tagName Tag 名称
     * @return true=基础类型
     */
    boolean isFixedTag(String tagName);

    /**
     * 检查 EdgeType 是否为基础类型
     *
     * @param edgeTypeName EdgeType 名称
     * @return true=基础类型
     */
    boolean isFixedEdgeType(String edgeTypeName);

    /**
     * 为基础 Tag 添加扩展字段（新增，不修改现有字段）
     *
     * @param tagName Tag 名称
     * @param newFields 新增字段列表
     */
    void extendTag(String tagName, java.util.List<FieldDefinition> newFields);

    /**
     * 为基础 EdgeType 添加扩展字段
     *
     * @param edgeTypeName EdgeType 名称
     * @param newFields 新增字段列表
     */
    void extendEdgeType(String edgeTypeName, java.util.List<FieldDefinition> newFields);
}