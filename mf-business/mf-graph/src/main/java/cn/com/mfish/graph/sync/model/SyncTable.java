package cn.com.mfish.graph.sync.model;

import lombok.Data;

/**
 * 同步表配置
 */
@Data
public class SyncTable {
    private String tableName;
    private String type;
    private boolean isEdge;
    private String idColumn;

    public SyncTable(String tableName, String type, boolean isEdge, String idColumn) {
        this.tableName = tableName;
        this.type = type;
        this.isEdge = isEdge;
        this.idColumn = idColumn;
    }
}
