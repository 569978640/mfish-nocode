package cn.com.mfish.graph.schema;

import cn.com.mfish.graph.schema.model.EdgeTypeDefinition;
import cn.com.mfish.graph.schema.model.TagDefinition;

import java.util.Set;

/**
 * 动态 Schema 管理器接口
 * 管理业务自定义扩展类型，支持灰度发布和配额校验
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface DynamicSchemaManager {
    /**
     * 创建 Tag
     *
     * @param definition Tag 定义
     */
    void createTag(TagDefinition definition);

    /**
     * 创建 EdgeType
     *
     * @param definition EdgeType 定义
     */
    void createEdgeType(EdgeTypeDefinition definition);

    /**
     * 灰度创建 Tag（仅对指定业务线可见）
     *
     * @param definition Tag 定义
     * @param grayBusinessLines 灰度业务线列表
     */
    void createTagWithGray(TagDefinition definition, java.util.List<String> grayBusinessLines);

    /**
     * 灰度创建 EdgeType
     *
     * @param definition EdgeType 定义
     * @param grayBusinessLines 灰度业务线列表
     */
    void createEdgeTypeWithGray(EdgeTypeDefinition definition, java.util.List<String> grayBusinessLines);

    /**
     * 删除 Tag
     *
     * @param tagName Tag 名称
     */
    void dropTag(String tagName);

    /**
     * 删除 EdgeType
     *
     * @param edgeTypeName EdgeType 名称
     */
    void dropEdgeType(String edgeTypeName);

    /**
     * 校验配额
     */
    void validateQuota();

    /**
     * 定时校验配额
     */
    void scheduledQuotaCheck();

    /**
     * 获取所有动态 Schema
     *
     * @return Schema 名称集合
     */
    Set<String> getDynamicSchemas();

    /**
     * 检测 Schema 变更后的异常（如查询失败率）
     *
     * @param schemaName Schema 名称
     * @return true=正常，false=异常
     */
    boolean detectSchemaChangeAbnormal(String schemaName);
}