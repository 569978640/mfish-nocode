package cn.com.mfish.graph.sync.service;

import cn.com.mfish.graph.client.NebulaGraphClient;
import cn.com.mfish.graph.model.GraphEdge;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.schema.SchemaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图数据库写入服务
 * 负责将节点和边数据写入 NebulaGraph
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
@Service
public class NebulaWriteService {

    @Autowired
    private NebulaGraphClient nebulaGraphClient;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public NebulaWriteService() {
        dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    }

    /**
     * 批量写入或更新节点和边
     */
    public void upsertNodesAndEdges(GraphSyncEvent event) {
        if (event == null) {
            return;
        }

        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            Map<String, List<GraphNode>> nodesByType = event.getNodes().stream()
                .collect(Collectors.groupingBy(GraphNode::getNodeType));

            for (Map.Entry<String, List<GraphNode>> entry : nodesByType.entrySet()) {
                String tagName = entry.getKey();
                List<GraphNode> nodeList = entry.getValue();
                batchUpsertVertices(tagName, nodeList);
                log.info("批量写入节点: tagName={}, count={}", tagName, nodeList.size());
            }
        }

        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            Map<String, List<GraphEdge>> edgesByType = event.getEdges().stream()
                .collect(Collectors.groupingBy(GraphEdge::getEdgeType));

            for (Map.Entry<String, List<GraphEdge>> entry : edgesByType.entrySet()) {
                String edgeName = entry.getKey();
                List<GraphEdge> edgeList = entry.getValue();
                batchUpsertEdges(edgeName, edgeList);
                log.info("批量写入边: edgeName={}, count={}", edgeName, edgeList.size());
            }
        }
    }

    /**
     * 批量删除节点和边
     */
    public void deleteNodesAndEdges(GraphSyncEvent event) {
        if (event == null) {
            return;
        }

        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            for (GraphEdge edge : event.getEdges()) {
                deleteEdge(edge.getEdgeType(), edge.getFromId(), edge.getToId());
            }
            log.info("批量删除边: count={}", event.getEdges().size());
        }

        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            for (GraphNode node : event.getNodes()) {
                deleteVertex(node.getNodeType(), node.getId());
            }
            log.info("批量删除节点: count={}", event.getNodes().size());
        }
    }

    /**
     * 批量 Upsert 节点
     */
    private void batchUpsertVertices(String tagName, List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        StringBuilder ngql = new StringBuilder();
        ngql.append("INSERT VERTEX ");
        ngql.append(SchemaUtils.quote(tagName));
        ngql.append("(id, create_by, create_time, update_by, update_time) VALUES ");

        boolean first = true;
        for (GraphNode node : nodes) {
            if (!first) {
                ngql.append(", ");
            }
            first = false;

            ngql.append(SchemaUtils.quote(node.getId()))
                .append(":(")
                .append(SchemaUtils.quote(node.getId())).append(", ")
                .append(node.getCreateBy() != null ? SchemaUtils.quote(node.getCreateBy()) : "NULL").append(", ")
                .append(node.getCreateTime() != null ? "datetime('" + dateFormat.format(node.getCreateTime()) + "')" : "NULL").append(", ")
                .append(node.getUpdateBy() != null ? SchemaUtils.quote(node.getUpdateBy()) : "NULL").append(", ")
                .append(node.getUpdateTime() != null ? "datetime('" + dateFormat.format(node.getUpdateTime()) + "')" : "NULL")
                .append(")");
        }

        boolean success = nebulaGraphClient.getWritePool().executeWrite(ngql.toString());
        if (!success) {
            throw new RuntimeException("批量写入节点失败: " + tagName);
        }
    }

    /**
     * 批量 Upsert 边
     */
    private void batchUpsertEdges(String edgeName, List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        StringBuilder ngql = new StringBuilder();
        ngql.append("INSERT EDGE ");
        ngql.append(SchemaUtils.quote(edgeName));
        ngql.append("(id, type, create_by, create_time, update_by, update_time, from_id, from_type, to_id, to_type) VALUES ");

        boolean first = true;
        for (GraphEdge edge : edges) {
            if (!first) {
                ngql.append(", ");
            }
            first = false;

            ngql.append(SchemaUtils.quote(edge.getFromId()))
                .append("->")
                .append(SchemaUtils.quote(edge.getToId()))
                .append(":(")
                .append(SchemaUtils.quote(edge.getId())).append(", ")
                .append(edge.getEdgeType() != null ? SchemaUtils.quote(edge.getEdgeType()) : "NULL").append(", ")
                .append(edge.getCreateBy() != null ? SchemaUtils.quote(edge.getCreateBy()) : "NULL").append(", ")
                .append(edge.getCreateTime() != null ? "datetime('" + dateFormat.format(edge.getCreateTime()) + "')" : "NULL").append(", ")
                .append(edge.getUpdateBy() != null ? SchemaUtils.quote(edge.getUpdateBy()) : "NULL").append(", ")
                .append(edge.getUpdateTime() != null ? "datetime('" + dateFormat.format(edge.getUpdateTime()) + "')" : "NULL").append(", ")
                .append(SchemaUtils.quote(edge.getFromId())).append(", ")
                .append(edge.getFromType() != null ? SchemaUtils.quote(edge.getFromType()) : "NULL").append(", ")
                .append(SchemaUtils.quote(edge.getToId())).append(", ")
                .append(edge.getToType() != null ? SchemaUtils.quote(edge.getToType()) : "NULL")
                .append(")");
        }

        boolean success = nebulaGraphClient.getWritePool().executeWrite(ngql.toString());
        if (!success) {
            throw new RuntimeException("批量写入边失败: " + edgeName);
        }
    }

    /**
     * 删除边
     */
    private void deleteEdge(String edgeName, String fromId, String toId) {
        String ngql = String.format("DELETE EDGE %s %s->%s",
            SchemaUtils.quote(edgeName),
            SchemaUtils.quote(fromId),
            SchemaUtils.quote(toId));

        boolean success = nebulaGraphClient.getWritePool().executeWrite(ngql);
        if (!success) {
            throw new RuntimeException("删除边失败: " + edgeName);
        }
    }

    /**
     * 删除节点
     */
    private void deleteVertex(String tagName, String vertexId) {
        String ngql = String.format("DELETE VERTEX %s WITH EDGES", SchemaUtils.quote(vertexId));

        boolean success = nebulaGraphClient.getWritePool().executeWrite(ngql);
        if (!success) {
            throw new RuntimeException("删除节点失败: " + tagName);
        }
    }
}
