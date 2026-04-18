package cn.com.mfish.graph.schema.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * EdgeType 定义模型
 * 用于定义 NebulaGraph 中的 EdgeType 元信息
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class EdgeTypeDefinition {
    /**
     * EdgeType 名称
     */
    private String name;

    /**
     * 字段定义列表
     */
    private List<FieldDefinition> fields;

    /**
     * 注释
     */
    private String comment;

    /**
     * 是否为基础类型
     */
    private boolean fixed;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 所属业务线
     */
    private List<String> businessLines;

    /**
     * rankKey（可选，用于多边场景）
     */
    private String rankKey;

    /**
     * 创建时间戳（毫秒）
     */
    private Long createTimeMs;

    /**
     * 更新时间戳（毫秒）
     */
    private Long updateTimeMs;
}