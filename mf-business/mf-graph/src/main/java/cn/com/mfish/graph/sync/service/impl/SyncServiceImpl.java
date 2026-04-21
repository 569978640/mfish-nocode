package cn.com.mfish.graph.sync.service.impl;

import cn.com.mfish.graph.sync.fail.FailedRecordService;
import cn.com.mfish.graph.sync.graph.*;
import cn.com.mfish.graph.sync.model.*;
import cn.com.mfish.graph.sync.service.SyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 同步服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {
    private final GraphOperationService graphService;
    private final FailedRecordService failedRecordService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> EDGE_TABLES = Set.of("contains_link", "iteraite_link");

    @Override
    public void fullSync() {
        log.info("开始全量同步...");
        List<SyncTable> tables = getSyncTables();
        for (SyncTable table : tables) {
            syncTable(table);
        }
        log.info("全量同步完成");
    }

    @Override
    public void handleCdcEvent(CdcEvent event) {
        try {
            String operationType = event.getOperationType();
            if ("DELETE".equals(operationType)) {
                Map<String, Object> data = event.getBefore();
                if (data == null || data.isEmpty()) {
                    log.warn("DELETE event has no before data, skip: {}", event.getTable());
                    return;
                }
                if (isEdgeTable(event.getTable())) {
                    handleEdgeDelete(event.getTable(), data);
                } else {
                    handleVertexDelete(event.getTable(), data);
                }
            } else {
                Map<String, Object> data = event.getAfter();
                if (data == null || data.isEmpty()) {
                    log.warn("Event has no after data, skip: {}", event.getTable());
                    return;
                }
                if (isEdgeTable(event.getTable())) {
                    handleEdgeUpsert(event.getTable(), data);
                } else {
                    handleVertexUpsert(event.getTable(), data);
                }
            }
        } catch (Exception e) {
            log.error("处理CDC事件失败: {}", event.getTable(), e);
            saveFailedRecord(event, e.getMessage());
        }
    }

    @Override
    public void handleVertexEvent(CdcEvent event) {
        handleCdcEvent(event);
    }

    @Override
    public void handleEdgeEvent(CdcEvent event) {
        handleCdcEvent(event);
    }

    private void handleVertexUpsert(String tableName, Map<String, Object> data) {
        String id = String.valueOf(data.get("id"));
        String type = String.valueOf(data.getOrDefault("type", getTypeFromTableName(tableName)));
        VertexInfo vertex = VertexInfo.of(id, type, data);
        graphService.upsertVertex(vertex);
        log.debug("顶点UPSERT完成: tag={}, id={}", type, id);
    }

    private void handleVertexDelete(String tableName, Map<String, Object> data) {
        String id = String.valueOf(data.get("id"));
        String type = String.valueOf(data.getOrDefault("type", getTypeFromTableName(tableName)));
        graphService.deleteVertex(type, id);
        log.debug("顶点DELETE完成: tag={}, id={}", type, id);
    }

    private void handleEdgeUpsert(String tableName, Map<String, Object> data) {
        String id = String.valueOf(data.get("id"));
        String type = String.valueOf(data.getOrDefault("type", getTypeFromTableName(tableName)));
        String fromId = getStringValue(data, "from_id", "fromId");
        String fromType = getStringValue(data, "from_type", "fromType");
        String toId = getStringValue(data, "to_id", "toId");
        String toType = getStringValue(data, "to_type", "toType");

        EdgeInfo edge = EdgeInfo.of(id, type, fromId, fromType, toId, toType, data);
        graphService.upsertEdge(edge);
        log.debug("边UPSERT完成: edge={}, from={}->to={}", type, fromId, toId);
    }

    private void handleEdgeDelete(String tableName, Map<String, Object> data) {
        String fromId = getStringValue(data, "from_id", "fromId");
        String fromType = getStringValue(data, "from_type", "fromType");
        String toId = getStringValue(data, "to_id", "toId");
        String toType = getStringValue(data, "to_type", "toType");
        String type = String.valueOf(data.getOrDefault("type", getTypeFromTableName(tableName)));

        graphService.deleteEdge(type, fromId, toId);
        log.debug("边DELETE完成: edge={}, from={}->to={}", type, fromId, toId);
    }

    private String getStringValue(Map<String, Object> data, String key1, String key2) {
        Object value = data.get(key1);
        if (value == null) {
            value = data.get(key2);
        }
        return value != null ? String.valueOf(value) : null;
    }

    private void syncTable(SyncTable table) {
        log.info("同步表: {}", table.getTableName());
        // TODO: 实现全量数据查询和同步
        // 1. 分批查询 PG 数据
        // 2. 转换为 VertexInfo/EdgeInfo
        // 3. 调用 graphService 写入 NebulaGraph
    }

    private List<SyncTable> getSyncTables() {
        return List.of(
                new SyncTable("part", "WPart", false, "id"),
                new SyncTable("document", "Document", false, "id"),
                new SyncTable("document_master", "DocumentMaster", false, "id"),
                new SyncTable("folder", "Folder", false, "id"),
                new SyncTable("part_master", "WPartMaster", false, "id"),
                new SyncTable("product", "Product", false, "id"),
                new SyncTable("contains_link", "ContainsLink", true, "id"),
                new SyncTable("iteraite_link", "IteraiteLink", true, "id")
        );
    }

    private boolean isEdgeTable(String tableName) {
        if (EDGE_TABLES == null || EDGE_TABLES.isEmpty()) {
            return tableName.endsWith("_link");
        }
        return EDGE_TABLES.contains(tableName.toLowerCase());
    }

    private String getTypeFromTableName(String tableName) {
        String[] parts = tableName.split("_");
        StringBuilder type = new StringBuilder();
        for (String part : parts) {
            if (type.length() > 0) type.append("_");
            type.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
        }
        return type.toString();
    }

    private void saveFailedRecord(CdcEvent event, String errorMessage) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            failedRecordService.saveFailedRecord(event.getTable(), event.getOperationType(), payload, errorMessage);
        } catch (Exception e) {
            log.error("保存失败记录异常", e);
        }
    }
}
