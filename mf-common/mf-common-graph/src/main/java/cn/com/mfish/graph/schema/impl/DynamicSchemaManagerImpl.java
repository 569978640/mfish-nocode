package cn.com.mfish.graph.schema.impl;

import cn.com.mfish.graph.config.NebulaQuotaConfig;
import cn.com.mfish.graph.exception.SchemaException;
import cn.com.mfish.graph.pool.NebulaSessionPool;
import cn.com.mfish.graph.schema.DynamicSchemaManager;
import cn.com.mfish.graph.schema.FieldTypeValidator;
import cn.com.mfish.graph.schema.SchemaChangeLock;
import cn.com.mfish.graph.schema.SchemaUtils;
import cn.com.mfish.graph.schema.model.EdgeTypeDefinition;
import cn.com.mfish.graph.schema.model.FieldDefinition;
import cn.com.mfish.graph.schema.model.TagDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 动态 Schema 管理器实现类
 * 管理业务自定义扩展类型，支持灰度发布和配额校验
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class DynamicSchemaManagerImpl implements DynamicSchemaManager {
    private final NebulaSessionPool sessionPool;
    private final NebulaQuotaConfig quotaConfig;
    private final FieldTypeValidator fieldValidator;
    private final SchemaChangeLock schemaLock;
    private final Set<String> dynamicSchemas = ConcurrentHashMap.newKeySet();
    private final AtomicInteger tagCount = new AtomicInteger(0);
    private final AtomicInteger edgeCount = new AtomicInteger(0);
    private ScheduledExecutorService scheduler;

    public DynamicSchemaManagerImpl(NebulaSessionPool sessionPool, NebulaQuotaConfig quotaConfig) {
        this(sessionPool, quotaConfig, new FieldTypeValidator.DefaultFieldTypeValidator(),
             new SchemaChangeLock.InMemorySchemaChangeLock());
    }

    public DynamicSchemaManagerImpl(NebulaSessionPool sessionPool,
                                    NebulaQuotaConfig quotaConfig,
                                    FieldTypeValidator fieldValidator,
                                    SchemaChangeLock schemaLock) {
        this.sessionPool = sessionPool;
        this.quotaConfig = quotaConfig;
        this.fieldValidator = fieldValidator;
        this.schemaLock = schemaLock;
    }

    /**
     * 启动定时配额校验
     */
    public void startQuotaCheck() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(
                this::scheduledQuotaCheck,
                quotaConfig.getQuotaCheckInterval(),
                quotaConfig.getQuotaCheckInterval(),
                TimeUnit.SECONDS
            );
            log.info("动态 Schema 配额定时校验已启动，间隔: {}s", quotaConfig.getQuotaCheckInterval());
        }
    }

    /**
     * 停止定时配额校验
     */
    public void stopQuotaCheck() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    @Override
    public void createTag(TagDefinition definition) {
        validateQuota();
        validateTagDefinition(definition);

        String lockToken = schemaLock.tryLock(definition.getName(), 30, TimeUnit.SECONDS);
        if (lockToken == null) {
            throw new SchemaException("无法获取 Schema 变更锁: " + definition.getName());
        }

        try {
            String ngql = SchemaUtils.buildCreateTag(
                definition.getName(),
                definition.getFields(),
                definition.getComment(),
                true
            );
            sessionPool.executeWrite(ngql);
            dynamicSchemas.add(definition.getName());
            tagCount.incrementAndGet();
            createIdIndexIfNeeded(definition);
            log.info("创建动态 Tag 成功: {}", definition.getName());
        } finally {
            schemaLock.unlock(definition.getName(), lockToken);
        }
    }

    @Override
    public void createEdgeType(EdgeTypeDefinition definition) {
        validateQuota();
        validateEdgeDefinition(definition);

        String lockToken = schemaLock.tryLock(definition.getName(), 30, TimeUnit.SECONDS);
        if (lockToken == null) {
            throw new SchemaException("无法获取 Schema 变更锁: " + definition.getName());
        }

        try {
            String ngql = SchemaUtils.buildCreateEdge(
                definition.getName(),
                definition.getFields(),
                definition.getComment(),
                true
            );
            sessionPool.executeWrite(ngql);
            dynamicSchemas.add(definition.getName());
            edgeCount.incrementAndGet();
            log.info("创建动态 EdgeType 成功: {}", definition.getName());
        } finally {
            schemaLock.unlock(definition.getName(), lockToken);
        }
    }

    @Override
    public void createTagWithGray(TagDefinition definition, List<String> grayBusinessLines) {
        definition.setBusinessLines(grayBusinessLines);
        createTag(definition);
    }

    @Override
    public void createEdgeTypeWithGray(EdgeTypeDefinition definition, List<String> grayBusinessLines) {
        definition.setBusinessLines(grayBusinessLines);
        createEdgeType(definition);
    }

    @Override
    public void dropTag(String tagName) {
        if (!dynamicSchemas.contains(tagName)) {
            throw new SchemaException("非动态 Schema 不允许删除: " + tagName);
        }

        String lockToken = schemaLock.tryLock(tagName, 30, TimeUnit.SECONDS);
        if (lockToken == null) {
            throw new SchemaException("无法获取 Schema 变更锁: " + tagName);
        }

        try {
            String ngql = "DROP TAG IF EXISTS `" + tagName + "`";
            sessionPool.executeWrite(ngql);
            dynamicSchemas.remove(tagName);
            tagCount.decrementAndGet();
            log.info("删除动态 Tag 成功: {}", tagName);
        } finally {
            schemaLock.unlock(tagName, lockToken);
        }
    }

    @Override
    public void dropEdgeType(String edgeTypeName) {
        if (!dynamicSchemas.contains(edgeTypeName)) {
            throw new SchemaException("非动态 Schema 不允许删除: " + edgeTypeName);
        }

        String lockToken = schemaLock.tryLock(edgeTypeName, 30, TimeUnit.SECONDS);
        if (lockToken == null) {
            throw new SchemaException("无法获取 Schema 变更锁: " + edgeTypeName);
        }

        try {
            String ngql = "DROP EDGE IF EXISTS `" + edgeTypeName + "`";
            sessionPool.executeWrite(ngql);
            dynamicSchemas.remove(edgeTypeName);
            edgeCount.decrementAndGet();
            log.info("删除动态 EdgeType 成功: {}", edgeTypeName);
        } finally {
            schemaLock.unlock(edgeTypeName, lockToken);
        }
    }

    @Override
    public void validateQuota() {
        if (dynamicSchemas.size() >= quotaConfig.getMaxDynamicSchemas()) {
            throw new SchemaException("动态 Schema 数量超限: " + quotaConfig.getMaxDynamicSchemas());
        }
    }

    @Override
    public void scheduledQuotaCheck() {
        try {
            if (dynamicSchemas.size() > quotaConfig.getMaxDynamicSchemas() * 0.8) {
                log.warn("动态 Schema 数量接近上限: {}/{}", dynamicSchemas.size(), quotaConfig.getMaxDynamicSchemas());
            }
        } catch (Exception e) {
            log.error("配额校验异常", e);
        }
    }

    @Override
    public Set<String> getDynamicSchemas() {
        return Collections.unmodifiableSet(dynamicSchemas);
    }

    @Override
    public boolean detectSchemaChangeAbnormal(String schemaName) {
        return true;
    }

    private void validateTagDefinition(TagDefinition definition) {
        if (definition.getName() == null || definition.getName().isEmpty()) {
            throw new IllegalArgumentException("Tag 名称不能为空");
        }
        if (definition.getFields() != null && definition.getFields().size() > quotaConfig.getMaxFieldsPerTag()) {
            throw new SchemaException("Tag 字段数超限: " + quotaConfig.getMaxFieldsPerTag());
        }
        if (definition.getFields() != null) {
            for (FieldDefinition field : definition.getFields()) {
                fieldValidator.validateField(field.getName(), field.getType());
            }
        }
    }

    private void validateEdgeDefinition(EdgeTypeDefinition definition) {
        if (definition.getName() == null || definition.getName().isEmpty()) {
            throw new IllegalArgumentException("EdgeType 名称不能为空");
        }
        if (definition.getFields() != null && definition.getFields().size() > quotaConfig.getMaxFieldsPerEdgeType()) {
            throw new SchemaException("EdgeType 字段数超限: " + quotaConfig.getMaxFieldsPerEdgeType());
        }
        if (definition.getFields() != null) {
            for (FieldDefinition field : definition.getFields()) {
                fieldValidator.validateField(field.getName(), field.getType());
            }
        }
    }

    private void createIdIndexIfNeeded(TagDefinition definition) {
        boolean hasIdField = definition.getFields() != null &&
            definition.getFields().stream().anyMatch(f -> "id".equalsIgnoreCase(f.getName()));
        if (!hasIdField) {
            try {
                String ngql = "CREATE TAG INDEX IF NOT EXISTS `" + definition.getName() + "_id_index` ON `" + definition.getName() + "`(id)";
                sessionPool.executeWrite(ngql);
                log.info("为动态 Tag 创建 ID 索引: {}", definition.getName());
            } catch (Exception e) {
                log.warn("创建 ID 索引失败: {}", e.getMessage());
            }
        }
    }
}