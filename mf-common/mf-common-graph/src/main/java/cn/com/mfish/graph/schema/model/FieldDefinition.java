package cn.com.mfish.graph.schema.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 字段定义模型
 * 用于定义 Tag 或 EdgeType 的字段元信息
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class FieldDefinition {
    /**
     * 字段名称
     */
    private String name;

    /**
     * 字段类型
     */
    private String type;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 是否可为空
     */
    private boolean nullable;

    /**
     * 注释
     */
    private String comment;

    /**
     * 是否为主键
     */
    private boolean primaryKey;

    /**
     * 是否为索引字段
     */
    private boolean indexed;
}