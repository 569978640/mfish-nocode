package cn.com.mfish.graph.schema;

import cn.com.mfish.graph.schema.model.FieldDefinition;

import java.util.Arrays;
import java.util.List;

/**
 * 字段类型校验器
 * 确保新增字段符合 NebulaGraph 支持的类型规范
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface FieldTypeValidator {
    /**
     * NebulaGraph 支持的字段类型白名单
     */
    List<String> ALLOWED_TYPES = Arrays.asList(
        "string", "int", "bigint", "double", "float",
        "bool", "timestamp", "datetime", "date"
    );

    /**
     * 校验字段类型是否在白名单内
     *
     * @param type 字段类型
     * @return true=支持，false=不支持
     */
    boolean validate(String type);

    /**
     * 校验字段定义（类型 + 字段名）
     *
     * @param fieldName 字段名
     * @param type 字段类型
     */
    void validateField(String fieldName, String type);

    /**
     * 校验字段兼容性（新增字段时检查是否与现有版本冲突）
     *
     * @param schemaName Schema 名称
     * @param newFields 新增字段列表
     * @return true=兼容，false=冲突
     */
    boolean validateFieldCompatibility(String schemaName, List<FieldDefinition> newFields);

    /**
     * 默认实现
     */
    class DefaultFieldTypeValidator implements FieldTypeValidator {
        @Override
        public boolean validate(String type) {
            if (type == null || type.isEmpty()) {
                return false;
            }
            return ALLOWED_TYPES.contains(type.toLowerCase());
        }

        @Override
        public void validateField(String fieldName, String type) {
            if (fieldName == null || fieldName.isEmpty()) {
                throw new IllegalArgumentException("字段名不能为空");
            }
            if (!validateIdentifier(fieldName)) {
                throw new IllegalArgumentException("字段名不合法: " + fieldName);
            }
            if (!validate(type)) {
                throw new IllegalArgumentException("不支持的字段类型: " + type);
            }
        }

        @Override
        public boolean validateFieldCompatibility(String schemaName, List<FieldDefinition> newFields) {
            if (newFields == null || newFields.isEmpty()) {
                return true;
            }
            for (FieldDefinition field : newFields) {
                if (!validate(field.getType())) {
                    return false;
                }
            }
            return true;
        }

        private boolean validateIdentifier(String identifier) {
            return identifier != null && identifier.matches("^[a-z][a-z0-9_]*$");
        }
    }
}