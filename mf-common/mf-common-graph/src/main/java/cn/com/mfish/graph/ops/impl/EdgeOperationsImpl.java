package cn.com.mfish.graph.ops.impl;

import cn.com.mfish.graph.exception.BusinessException;
import cn.com.mfish.graph.id.VidGenerator;
import cn.com.mfish.graph.model.GraphEdge;
import cn.com.mfish.graph.ops.EdgeOperations;
import cn.com.mfish.graph.pool.NebulaSessionPool;
import cn.com.mfish.graph.schema.SchemaUtils;
import com.vesoft.nebula.client.graph.data.ResultSet;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 边操作实现类
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class EdgeOperationsImpl implements EdgeOperations {
    private final NebulaSessionPool sessionPool;
    private final VidGenerator vidGenerator;

    public EdgeOperationsImpl(NebulaSessionPool sessionPool, VidGenerator vidGenerator) {
        this.sessionPool = sessionPool;
        this.vidGenerator = vidGenerator;
    }

    @Override
    public boolean insertEdge(GraphEdge edge) {
        StringBuilder ngql = new StringBuilder();
        ngql.append("INSERT EDGE ");
        ngql.append(SchemaUtils.quote(edge.getType()));
        ngql.append("(");

        List<String> fields = getEdgeFields(edge);
        List<String> values = getEdgeValues(edge);

        ngql.append(String.join(", ", fields));
        ngql.append(") VALUES ");
        ngql.append(SchemaUtils.quote(edge.getFromId()));
        ngql.append(" -> ");
        ngql.append(SchemaUtils.quote(edge.getToId()));

        if (edge.getRank() != null) {
            ngql.append("@").append(edge.getRank());
        }

        ngql.append(":(");
        ngql.append(String.join(", ", values));
        ngql.append(")");

        try {
            ResultSet result = sessionPool.executeWrite(ngql.toString());
            return result.isSucceeded();
        } catch (Exception e) {
            log.error("插入边失败: {} -> {}", edge.getFromId(), edge.getToId(), e);
            throw new BusinessException("插入边失败", e);
        }
    }

    @Override
    public boolean upsertEdge(GraphEdge edge) {
        StringBuilder ngql = new StringBuilder();
        ngql.append("UPSERT EDGE ");
        ngql.append(SchemaUtils.quote(edge.getFromId()));
        ngql.append(" -> ");
        ngql.append(SchemaUtils.quote(edge.getToId()));

        if (edge.getRank() != null) {
            ngql.append("@").append(edge.getRank());
        }

        ngql.append(" ON ");
        ngql.append(SchemaUtils.quote(edge.getType()));
        ngql.append(" ");

        List<String> setClauses = new ArrayList<>();
        if (edge.getLinkType() != null) {
            setClauses.add("`link_type` = \"" + escapeValue(edge.getLinkType()) + "\"");
        }
        if (edge.getPropData() != null) {
            setClauses.add("`prop_data` = \"" + escapeValue(edge.getPropData()) + "\"");
        }
        if (edge.getUpdateBy() != null) {
            setClauses.add("`update_by` = \"" + escapeValue(edge.getUpdateBy()) + "\"");
        }

        ngql.append("SET ");
        ngql.append(String.join(", ", setClauses));

        try {
            ResultSet result = sessionPool.executeWrite(ngql.toString());
            return result.isSucceeded();
        } catch (Exception e) {
            log.error("Upsert 边失败: {} -> {}", edge.getFromId(), edge.getToId(), e);
            throw new BusinessException("Upsert 边失败", e);
        }
    }

    @Override
    public boolean updateEdge(GraphEdge edge) {
        StringBuilder ngql = new StringBuilder();
        ngql.append("UPDATE EDGE ");
        ngql.append(SchemaUtils.quote(edge.getFromId()));
        ngql.append(" -> ");
        ngql.append(SchemaUtils.quote(edge.getToId()));
        ngql.append(" ON ");
        ngql.append(SchemaUtils.quote(edge.getType()));
        ngql.append(" SET ");

        List<String> setClauses = new ArrayList<>();
        if (edge.getLinkType() != null) {
            setClauses.add("`link_type` = \"" + escapeValue(edge.getLinkType()) + "\"");
        }
        if (edge.getPropData() != null) {
            setClauses.add("`prop_data` = \"" + escapeValue(edge.getPropData()) + "\"");
        }
        if (edge.getUpdateBy() != null) {
            setClauses.add("`update_by` = \"" + escapeValue(edge.getUpdateBy()) + "\"");
        }

        ngql.append(String.join(", ", setClauses));

        try {
            ResultSet result = sessionPool.executeWrite(ngql.toString());
            return result.isSucceeded();
        } catch (Exception e) {
            log.error("更新边失败: {} -> {}", edge.getFromId(), edge.getToId(), e);
            throw new BusinessException("更新边失败", e);
        }
    }

    @Override
    public boolean deleteEdge(String fromId, String edgeType, String toId) {
        String ngql = "DELETE EDGE " + SchemaUtils.quote(edgeType) + " " +
                      SchemaUtils.quote(fromId) + " -> " + SchemaUtils.quote(toId);
        try {
            ResultSet result = sessionPool.executeWrite(ngql);
            return result.isSucceeded();
        } catch (Exception e) {
            log.error("删除边失败: {} -> {}", fromId, toId, e);
            throw new BusinessException("删除边失败", e);
        }
    }

    @Override
    public boolean softDeleteEdge(String fromId, String edgeType, String toId, String deleteBy) {
        String ngql = "UPDATE EDGE " + SchemaUtils.quote(fromId) + " -> " + SchemaUtils.quote(toId) +
                      " ON " + SchemaUtils.quote(edgeType) +
                      " SET `deleted` = true, `update_by` = \"" + escapeValue(deleteBy) + "\"";
        try {
            ResultSet result = sessionPool.executeWrite(ngql);
            return result.isSucceeded();
        } catch (Exception e) {
            log.error("软删除边失败: {} -> {}", fromId, toId, e);
            throw new BusinessException("软删除边失败", e);
        }
    }

    @Override
    public GraphEdge getEdge(String fromId, String edgeType, String toId) {
        String ngql = "MATCH (a) -[r:" + SchemaUtils.quote(edgeType) + "]-> (b) " +
                      "WHERE id(a) == " + SchemaUtils.quote(fromId) +
                      " AND id(b) == " + SchemaUtils.quote(toId) +
                      " RETURN r LIMIT 1";
        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            if (result.isSucceeded() && result.rowsSize() > 0) {
                return convertToGraphEdge(result, 0);
            }
            return null;
        } catch (Exception e) {
            log.error("查询边失败: {} -> {}", fromId, toId, e);
            throw new BusinessException("查询边失败", e);
        }
    }

    @Override
    public List<GraphEdge> getOutEdges(String fromId) {
        String ngql = "MATCH (a) -[r]-> (b) WHERE id(a) == " + SchemaUtils.quote(fromId) + " RETURN r";
        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            List<GraphEdge> edges = new ArrayList<>();
            if (result.isSucceeded()) {
                for (int i = 0; i < result.rowsSize(); i++) {
                    edges.add(convertToGraphEdge(result, i));
                }
            }
            return edges;
        } catch (Exception e) {
            log.error("查询出边失败: {}", fromId, e);
            throw new BusinessException("查询出边失败", e);
        }
    }

    @Override
    public List<GraphEdge> getInEdges(String toId) {
        String ngql = "MATCH (a) -[r]-> (b) WHERE id(b) == " + SchemaUtils.quote(toId) + " RETURN r";
        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            List<GraphEdge> edges = new ArrayList<>();
            if (result.isSucceeded()) {
                for (int i = 0; i < result.rowsSize(); i++) {
                    edges.add(convertToGraphEdge(result, i));
                }
            }
            return edges;
        } catch (Exception e) {
            log.error("查询入边失败: {}", toId, e);
            throw new BusinessException("查询入边失败", e);
        }
    }

    @Override
    public int batchInsertEdges(List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (GraphEdge edge : edges) {
            try {
                if (insertEdge(edge)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("批量插入边失败: {} -> {}", edge.getFromId(), edge.getToId(), e);
            }
        }
        return successCount;
    }

    @Override
    public int batchUpdateEdges(List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (GraphEdge edge : edges) {
            try {
                if (updateEdge(edge)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("批量更新边失败: {} -> {}", edge.getFromId(), edge.getToId(), e);
            }
        }
        return successCount;
    }

    @Override
    public int batchDeleteEdges(List<String> fromIds, String edgeType, List<String> toIds) {
        if (fromIds == null || toIds == null || fromIds.size() != toIds.size()) {
            throw new IllegalArgumentException("源节点和目标节点数量不匹配");
        }

        int successCount = 0;
        for (int i = 0; i < fromIds.size(); i++) {
            try {
                if (deleteEdge(fromIds.get(i), edgeType, toIds.get(i))) {
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("批量删除边失败: {} -> {}", fromIds.get(i), toIds.get(i), e);
            }
        }
        return successCount;
    }

    private List<String> getEdgeFields(GraphEdge edge) {
        List<String> fields = new ArrayList<>();
        fields.add("`link_type`");
        if (edge.getPropData() != null) {
            fields.add("`prop_data`");
        }
        fields.add("`create_by`");
        fields.add("`create_time`");
        if (edge.getUpdateBy() != null) {
            fields.add("`update_by`");
        }
        return fields;
    }

    private List<String> getEdgeValues(GraphEdge edge) {
        List<String> values = new ArrayList<>();
        values.add("\"" + escapeValue(edge.getLinkType()) + "\"");
        if (edge.getPropData() != null) {
            values.add("\"" + escapeValue(edge.getPropData()) + "\"");
        }
        values.add("\"" + escapeValue(edge.getCreateBy()) + "\"");
        values.add(String.valueOf(System.currentTimeMillis()));
        if (edge.getUpdateBy() != null) {
            values.add("\"" + escapeValue(edge.getUpdateBy()) + "\"");
        }
        return values;
    }

    private GraphEdge convertToGraphEdge(ResultSet result, int rowIndex) {
        GraphEdge edge = new GraphEdge();
        return edge;
    }

    private String escapeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}