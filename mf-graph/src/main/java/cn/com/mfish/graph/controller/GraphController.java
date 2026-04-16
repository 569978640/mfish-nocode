package cn.com.mfish.graph.controller;

import cn.com.mfish.graph.service.GraphQueryService;
import cn.com.mfish.graph.service.GraphSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

/**
 * 图谱查询控制器
 *
 * @author mfish
 * @date 2026-04-16
 */
@Slf4j
@RestController
@RequestMapping("/graph")
public class GraphController {

    @Autowired
    private GraphQueryService graphQueryService;

    @Autowired
    private GraphSyncService graphSyncService;

    /**
     * 路径查询
     */
    @PostMapping("/query/paths")
    public Map<String, Object> queryPaths(@RequestBody Map<String, Object> request) {
        String startId = (String) request.get("startId");
        String startType = (String) request.get("startType");
        Integer depth = request.get("depth") != null ? ((Number) request.get("depth")).intValue() : 5;
        return graphQueryService.queryProductTree(startId, depth);
    }

    /**
     * 产品结构树查询
     */
    @PostMapping("/query/product-tree")
    public Map<String, Object> queryProductTree(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        Integer depth = request.get("depth") != null ? ((Number) request.get("depth")).intValue() : 5;
        return graphQueryService.queryProductTree(productId, depth);
    }

    /**
     * 查询直接子节点
     */
    @PostMapping("/query/direct-children")
    public Map<String, Object> queryDirectChildren(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        return graphQueryService.queryDirectChildren(productId);
    }

    /**
     * 查询最短路径
     */
    @PostMapping("/query/shortest-path")
    public Map<String, Object> queryShortestPath(@RequestBody Map<String, Object> request) {
        String startId = (String) request.get("startId");
        String startType = (String) request.get("startType");
        String endId = (String) request.get("endId");
        String endType = (String) request.get("endType");
        return graphQueryService.queryShortestPath(startId, startType, endId, endType);
    }

    /**
     * 查询邻居节点
     */
    @PostMapping("/query/neighbors")
    public Map<String, Object> queryNeighbors(@RequestBody Map<String, Object> request) {
        String nodeId = (String) request.get("nodeId");
        String nodeType = (String) request.get("nodeType");
        @SuppressWarnings("unchecked")
        java.util.List<String> edgeTypes = (java.util.List<String>) request.get("edgeTypes");
        String direction = request.get("direction") != null ? (String) request.get("direction") : "BOTH";
        return graphQueryService.queryNeighbors(nodeId, nodeType, edgeTypes, direction);
    }

    /**
     * 查询子图
     */
    @PostMapping("/query/subgraph")
    public Map<String, Object> querySubgraph(@RequestBody Map<String, Object> request) {
        String startId = (String) request.get("startId");
        String startType = (String) request.get("startType");
        Integer depth = request.get("depth") != null ? ((Number) request.get("depth")).intValue() : 3;
        @SuppressWarnings("unchecked")
        java.util.List<String> edgeTypes = (java.util.List<String>) request.get("edgeTypes");
        return graphQueryService.querySubgraph(startId, startType, depth, edgeTypes);
    }

    /**
     * 全量同步
     */
    @PostMapping("/sync/full")
    public Map<String, Object> fullSync() {
        Map<String, Object> result = new HashMap<>();
        try {
            graphSyncService.fullSync();
            result.put("success", true);
            result.put("message", "全量同步完成");
        } catch (Exception e) {
            log.error("全量同步失败", e);
            result.put("success", false);
            result.put("message", "全量同步失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 同步所有节点
     */
    @PostMapping("/sync/nodes")
    public Map<String, Object> syncNodes() {
        Map<String, Object> result = new HashMap<>();
        try {
            int count = graphSyncService.syncAllNodes();
            result.put("success", true);
            result.put("message", "节点同步完成");
            result.put("count", count);
        } catch (Exception e) {
            log.error("节点同步失败", e);
            result.put("success", false);
            result.put("message", "节点同步失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 同步所有边
     */
    @PostMapping("/sync/edges")
    public Map<String, Object> syncEdges() {
        Map<String, Object> result = new HashMap<>();
        try {
            int count = graphSyncService.syncAllEdges();
            result.put("success", true);
            result.put("message", "边同步完成");
            result.put("count", count);
        } catch (Exception e) {
            log.error("边同步失败", e);
            result.put("success", false);
            result.put("message", "边同步失败: " + e.getMessage());
        }
        return result;
    }
}