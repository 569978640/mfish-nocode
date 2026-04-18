package cn.com.mfish.graph.sync.service;

import cn.com.mfish.graph.client.NebulaClient;
import cn.com.mfish.graph.model.edge.GraphEdge;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.model.node.GraphNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private NebulaClient nebulaClient;

    /**
     * 批量写入或更新节点和边（通用化）
     */
    public void upsertNodesAndEdges(GraphSyncEvent event) {
        if (event == null) {
            return;
        }

        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            Map<String, List<GraphNode>> nodesByType = event.getNodes().stream()
                .collect(Collectors.groupingBy(GraphNode::getType));

            for (Map.Entry<String, List<GraphNode>> entry : nodesByType.entrySet()) {
                String tagName = entry.getKey();
                List<GraphNode> nodes = entry.getValue();
                nebulaClient.batchUpsertVertices(tagName, nodes);
                log.info("批量写入节点: tagName={}, count={}", tagName, nodes.size());
            }
        }

        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            Map<String, List<GraphEdge>> edgesByType = event.getEdges().stream()
                .collect(Collectors.groupingBy(GraphEdge::getType));

            for (Map.Entry<String, List<GraphEdge>> entry : edgesByType.entrySet()) {
                String edgeName = entry.getKey();
                List<GraphEdge> edges = entry.getValue();
                nebulaClient.batchUpsertEdges(edgeName, edges);
                log.info("批量写入边: edgeName={}, count={}", edgeName, edges.size());
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
                nebulaClient.deleteEdge(edge.getType(), edge.getFromId(), edge.getToId());
            }
            log.info("批量删除边: count={}", event.getEdges().size());
        }

        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            for (GraphNode node : event.getNodes()) {
                nebulaClient.deleteVertex(node.getType(), node.getId());
            }
            log.info("批量删除节点: count={}", event.getNodes().size());
        }
    }
}