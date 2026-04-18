package cn.com.mfish.graph.schema;

import cn.com.mfish.graph.schema.model.FieldDefinition;

import java.util.List;

/**
 * Schema 工具类
 * 提供标识符的安全处理（反引号包裹、大小写校验）
 * 解决 NebulaGraph 自动转小写问题
 *
 * @author mfish
 * @date 2026-04-18
 */
public class SchemaUtils {
    /**
     * 反引号包裹标识符
     * 解决 NebulaGraph 自动转小写问题
     *
     * @param identifier 标识符名称
     * @return 包裹后的标识符，如 `Product`
     */
    public static String quote(String identifier) {
        if (identifier == null) {
            return null;
        }
        return "`" + identifier + "`";
    }

    /**
     * 校验标识符是否符合规范（小写字母开头 + 字母数字下划线）
     *
     * @param identifier 标识符名称
     * @return true=合法，false=非法
     */
    public static boolean validateIdentifier(String identifier) {
        if (identifier == null) {
            return false;
        }
        return identifier.matches("^[a-z][a-z0-9_]*$");
    }

    /**
     * 强制转为小写并包裹反引号
     *
     * @param identifier 标识符名称
     * @return 转换后的标识符，如 `product`
     */
    public static String quoteLowerCase(String identifier) {
        if (identifier == null) {
            return null;
        }
        return quote(identifier.toLowerCase());
    }

    /**
     * 构建字段定义字符串
     *
     * @param fieldName 字段名
     * @param fieldType 字段类型
     * @param nullable 是否可为空
     * @param defaultValue 默认值
     * @return 字段定义字符串
     */
    public static String buildFieldDefinition(String fieldName, String fieldType, boolean nullable, String defaultValue) {
        StringBuilder sb = new StringBuilder();
        sb.append(quote(fieldName));
        sb.append(" ").append(fieldType);
        if (!nullable) {
            sb.append(" NOT NULL");
        }
        if (defaultValue != null && !defaultValue.isEmpty()) {
            sb.append(" DEFAULT ").append(defaultValue);
        }
        return sb.toString();
    }

    /**
     * 构建 CREATE TAG 语句
     *
     * @param tagName Tag 名称
     * @param fields 字段列表
     * @param comment 注释
     * @param ifNotExists 是否使用 IF NOT EXISTS
     * @return CREATE TAG 语句
     */
    public static String buildCreateTag(String tagName, List<FieldDefinition> fields, String comment, boolean ifNotExists) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TAG ");
        if (ifNotExists) {
            sb.append("IF NOT EXISTS ");
        }
        sb.append(quote(tagName));
        sb.append("(");
        if (fields != null) {
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                FieldDefinition field = fields.get(i);
                sb.append(buildFieldDefinition(field.getName(), field.getType(), field.isNullable(), field.getDefaultValue()));
            }
        }
        sb.append(")");
        if (comment != null && !comment.isEmpty()) {
            sb.append(" COMMENT ").append(quote(comment));
        }
        return sb.toString();
    }

    /**
     * 构建 CREATE EDGE 语句
     *
     * @param edgeTypeName EdgeType 名称
     * @param fields 字段列表
     * @param comment 注释
     * @param ifNotExists 是否使用 IF NOT EXISTS
     * @return CREATE EDGE 语句
     */
    public static String buildCreateEdge(String edgeTypeName, List<FieldDefinition> fields, String comment, boolean ifNotExists) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE EDGE ");
        if (ifNotExists) {
            sb.append("IF NOT EXISTS ");
        }
        sb.append(quote(edgeTypeName));
        sb.append("(");
        if (fields != null) {
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                FieldDefinition field = fields.get(i);
                sb.append(buildFieldDefinition(field.getName(), field.getType(), field.isNullable(), field.getDefaultValue()));
            }
        }
        sb.append(")");
        if (comment != null && !comment.isEmpty()) {
            sb.append(" COMMENT ").append(quote(comment));
        }
        return sb.toString();
    }
}