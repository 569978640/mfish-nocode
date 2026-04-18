package cn.com.mfish.graph.schema.impl;

import cn.com.mfish.graph.exception.SchemaException;
import cn.com.mfish.graph.pool.NebulaSessionPool;
import cn.com.mfish.graph.schema.FieldTypeValidator;
import cn.com.mfish.graph.schema.FixedSchemaManager;
import cn.com.mfish.graph.schema.SchemaChangeLock;
import cn.com.mfish.graph.schema.model.FieldDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 固定 Schema 管理器实现类
 * 管理 PLM 基础类型（SsoOrg/Product/Part 等）
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class FixedSchemaManagerImpl implements FixedSchemaManager {
    private final NebulaSessionPool sessionPool;
    private final Set<String> registeredTags = ConcurrentHashMap.newKeySet();
    private final Set<String> registeredEdgeTypes = ConcurrentHashMap.newKeySet();
    private final FieldTypeValidator fieldValidator;
    private final SchemaChangeLock schemaLock;

    private static final Set<String> PLM_FIXED_TAGS = Set.of(
        "SsoOrg", "Product", "PartMaster", "Document",
        "BOM", "ECN", "ChangeOrder", "SOP"
    );

    private static final Set<String> PLM_FIXED_EDGES = Set.of(
        "HAS_CHILD", "HAS_DOC", "REFERENCES", "BOM_LINE",
        "AFFECTS", "AFFECTED_BY", "USES", "USED_BY"
    );

    public FixedSchemaManagerImpl(NebulaSessionPool sessionPool) {
        this(sessionPool, new FieldTypeValidator.DefaultFieldTypeValidator(), new SchemaChangeLock.InMemorySchemaChangeLock());
    }

    public FixedSchemaManagerImpl(NebulaSessionPool sessionPool,
                                  FieldTypeValidator fieldValidator,
                                  SchemaChangeLock schemaLock) {
        this.sessionPool = sessionPool;
        this.fieldValidator = fieldValidator;
        this.schemaLock = schemaLock;
    }

    @Override
    public void initialize() {
        for (String tagName : PLM_FIXED_TAGS) {
            createTagIfNotExists(tagName);
            registeredTags.add(tagName);
        }

        for (String edgeName : PLM_FIXED_EDGES) {
            createEdgeIfNotExists(edgeName);
            registeredEdgeTypes.add(edgeName);
        }
        log.info("固定 Schema 初始化完成: Tags={}, Edges={}", registeredTags.size(), registeredEdgeTypes.size());
    }

    @Override
    public Set<String> getRegisteredTags() {
        return Collections.unmodifiableSet(registeredTags);
    }

    @Override
    public Set<String> getRegisteredEdgeTypes() {
        return Collections.unmodifiableSet(registeredEdgeTypes);
    }

    @Override
    public boolean isFixedTag(String tagName) {
        return registeredTags.contains(tagName);
    }

    @Override
    public boolean isFixedEdgeType(String edgeTypeName) {
        return registeredEdgeTypes.contains(edgeTypeName);
    }

    @Override
    public void extendTag(String tagName, List<FieldDefinition> newFields) {
        if (!isFixedTag(tagName)) {
            throw new SchemaException("非基础 Tag 不允许扩展: " + tagName);
        }

        String lockToken = schemaLock.tryLock(tagName, 30, java.util.concurrent.TimeUnit.SECONDS);
        if (lockToken == null) {
            throw new SchemaException("无法获取 Schema 变更锁: " + tagName);
        }

        try {
            for (FieldDefinition field : newFields) {
                fieldValidator.validateField(field.getName(), field.getType());
                addFieldToTag(tagName, field);
            }
            log.info("扩展基础 Tag 成功: tag={}, 新增字段数={}", tagName, newFields.size());
        } finally {
            schemaLock.unlock(tagName, lockToken);
        }
    }

    @Override
    public void extendEdgeType(String edgeTypeName, List<FieldDefinition> newFields) {
        if (!isFixedEdgeType(edgeTypeName)) {
            throw new SchemaException("非基础 EdgeType 不允许扩展: " + edgeTypeName);
        }

        String lockToken = schemaLock.tryLock(edgeTypeName, 30, java.util.concurrent.TimeUnit.SECONDS);
        if (lockToken == null) {
            throw new SchemaException("无法获取 Schema 变更锁: " + edgeTypeName);
        }

        try {
            for (FieldDefinition field : newFields) {
                fieldValidator.validateField(field.getName(), field.getType());
                addFieldToEdge(edgeTypeName, field);
            }
            log.info("扩展基础 EdgeType 成功: edge={}, 新增字段数={}", edgeTypeName, newFields.size());
        } finally {
            schemaLock.unlock(edgeTypeName, lockToken);
        }
    }

    private void createTagIfNotExists(String tagName) {
        String ngql = "CREATE TAG IF NOT EXISTS `" + tagName + "` ()";
        try {
            sessionPool.executeWrite(ngql);
            log.debug("创建基础 Tag: {}", tagName);
        } catch (Exception e) {
            log.warn("创建基础 Tag 失败或已存在: {}, error: {}", tagName, e.getMessage());
        }
    }

    private void createEdgeIfNotExists(String edgeName) {
        String ngql = "CREATE EDGE IF NOT EXISTS `" + edgeName + "` ()";
        try {
            sessionPool.executeWrite(ngql);
            log.debug("创建基础 EdgeType: {}", edgeName);
        } catch (Exception e) {
            log.warn("创建基础 EdgeType 失败或已存在: {}, error: {}", edgeName, e.getMessage());
        }
    }

    private void addFieldToTag(String tagName, FieldDefinition field) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TAG ").append("`").append(tagName).append("`");
        sb.append(" ADD `").append(field.getName()).append("` ");
        sb.append(field.getType());
        if (field.getDefaultValue() != null) {
            sb.append(" DEFAULT ").append(field.getDefaultValue());
        }
        sessionPool.executeWrite(sb.toString());
    }

    private void addFieldToEdge(String edgeName, FieldDefinition field) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER EDGE ").append("`").append(edgeName).append("`");
        sb.append(" ADD `").append(field.getName()).append("` ");
        sb.append(field.getType());
        if (field.getDefaultValue() != null) {
            sb.append(" DEFAULT ").append(field.getDefaultValue());
        }
        sessionPool.executeWrite(sb.toString());
    }
}