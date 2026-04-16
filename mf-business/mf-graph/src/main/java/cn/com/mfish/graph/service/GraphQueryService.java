package cn.com.mfish.graph.service;

import cn.com.mfish.graph.client.NebulaClient;
import cn.com.mfish.graph.client.NebulaClient.PathQueryResult;
import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.graph.model.edge.GraphEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 图查询服务
 * 实现混合查询：图库查路径+边属性，关系库查节点属性
 *
 * @author mfish
 * @date 2026-04-16
 */
@Slf4j
@Service
public class GraphQueryService {

    @Autowired
    private NebulaClient nebulaClient;

    @Autowired
    private GraphSyncService graphSyncService;

    /**
     * 产品结构树查询 - 混合查询
     * 图库负责: 路径查询 + 边的所有属性
     * 关系库负责: 节点业务属性
     *
     * @param productId 产品ID
     * @param depth 查询深度，默认5层
     * @return 查询结果
     */
    public Map<String, Object> queryProductTree(String productId, int depth) {
        log.info("开始查询产品结构树: productId={}, depth={}", productId, depth);

        String nGQL = String.format(
                "MATCH p=(p:Product {id:'%s'})-[*1..%d]->(n) RETURN p",
                productId, depth);

        PathQueryResult pathResult = nebulaClient.queryPaths(nGQL);

        if (!pathResult.isSuccess()) {
            log.error("图库查询失败: {}", pathResult.getErrorMessage());
            return buildErrorResult(pathResult.getErrorMessage());
        }

        Map<String, String> nodeIdTypeMap = new HashMap<>();
        for (GraphNode node : pathResult.getNodes()) {
            nodeIdTypeMap.put(node.getId(), node.getType());
        }

        Map<String, Map<String, Object>> nodeAttributes = graphSyncService.batchQueryNodes(
                pathResult.getAllNodeIds(), nodeIdTypeMap);

        return mergeResults(pathResult, nodeAttributes);
    }

    /**
     * 查询产品的直接子节点
     *
     * @param productId 产品ID
     * @return 直接子节点列表
     */
    public Map<String, Object> queryDirectChildren(String productId) {
        log.info("查询产品直接子节点: productId={}", productId);

        String nGQL = String.format(
                "MATCH (p:Product {id:'%s'})-[e:ContainsLink]->(n) RETURN n, e",
                productId);

        PathQueryResult pathResult = nebulaClient.queryPaths(nGQL);

        if (!pathResult.isSuccess()) {
            log.error("图库查询失败: {}", pathResult.getErrorMessage());
            return buildErrorResult(pathResult.getErrorMessage());
        }

        Map<String, String> nodeIdTypeMap = new HashMap<>();
        for (GraphNode node : pathResult.getNodes()) {
            nodeIdTypeMap.put(node.getId(), node.getType());
        }

        Map<String, Map<String, Object>> nodeAttributes = graphSyncService.batchQueryNodes(
                pathResult.getAllNodeIds(), nodeIdTypeMap);

        return mergeResults(pathResult, nodeAttributes);
    }

    /**
     * 查询两个节点之间的最短路径
     *
     * @param startId 起始节点ID
     * @param startType 起始节点类型
     * @param endId 目标节点ID
     * @param endType 目标节点类型
     * @return 最短路径结果
     */
    public Map<String, Object> queryShortestPath(String startId, String startType, String endId, String endType) {
        log.info("查询最短路径: {}:{} -> {}:{}", startType, startId, endType, endId);

        String nGQL = String.format(
                "FIND SHORTEST PATH FROM '%s' TO '%s' OVER * WHERE src(edge).type == '%s' AND dst(edge).type == '%s' YIELD PATH AS p",
                startId, endId, startType, endType);

        PathQueryResult pathResult = nebulaClient.queryPaths(nGQL);

        if (!pathResult.isSuccess()) {
            log.error("图库查询失败: {}", pathResult.getErrorMessage());
            return buildErrorResult(pathResult.getErrorMessage());
        }

        Map<String, String> nodeIdTypeMap = new HashMap<>();
        for (GraphNode node : pathResult.getNodes()) {
            nodeIdTypeMap.put(node.getId(), node.getType());
        }

        Map<String, Map<String, Object>> nodeAttributes = graphSyncService.batchQueryNodes(
                pathResult.getAllNodeIds(), nodeIdTypeMap);

        return mergeResults(pathResult, nodeAttributes);
    }

    /**
     * 查询邻居节点
     *
     * @param nodeId 节点ID
     * @param nodeType 节点类型
     * @param edgeTypes 边类型列表
     * @param direction 方向：OUT/IN/BOTH
     * @return 邻居节点列表
     */
    public Map<String, Object> queryNeighbors(String nodeId, String nodeType, List<String> edgeTypes, String direction) {
        log.info("查询邻居节点: {}:{}, direction={}", nodeType, nodeId, direction);

        String edgeFilter = "";
        if (edgeTypes != null && !edgeTypes.isEmpty()) {
            edgeFilter = ":" + String.join("|", edgeTypes);
        }

        String nGQL;
        switch (direction) {
            case "OUT":
                nGQL = String.format("MATCH (n:%s {id:'%s'})-[e%s]->(m) RETURN m, e", nodeType, nodeId, edgeFilter);
                break;
            case "IN":
                nGQL = String.format("MATCH (m)-[e%s]->(n:%s {id:'%s'}) RETURN m, e", edgeFilter, nodeType, nodeId);
                break;
            default:
                nGQL = String.format("MATCH (n:%s {id:'%s'})-[e%s]-(m) RETURN m, e", nodeType, nodeId, edgeFilter);
                break;
        }

        PathQueryResult pathResult = nebulaClient.queryPaths(nGQL);

        if (!pathResult.isSuccess()) {
            log.error("图库查询失败: {}", pathResult.getErrorMessage());
            return buildErrorResult(pathResult.getErrorMessage());
        }

        Map<String, String> nodeIdTypeMap = new HashMap<>();
        for (GraphNode node : pathResult.getNodes()) {
            nodeIdTypeMap.put(node.getId(), node.getType());
        }

        Map<String, Map<String, Object>> nodeAttributes = graphSyncService.batchQueryNodes(
                pathResult.getAllNodeIds(), nodeIdTypeMap);

        return mergeResults(pathResult, nodeAttributes);
    }

    /**
     * 查询子图
     *
     * @param startId 起始节点ID
     * @param startType 起始节点类型
     * @param depth 深度
     * @param edgeTypes 边类型列表
     * @return 子图结果
     */
    public Map<String, Object> querySubgraph(String startId, String startType, int depth, List<String> edgeTypes) {
        log.info("查询子图: {}:{}, depth={}", startType, startId, depth);

        String edgeFilter = "";
        if (edgeTypes != null && !edgeTypes.isEmpty()) {
            edgeFilter = ":" + String.join("|", edgeTypes);
        }

        String nGQL = String.format(
                "MATCH p=(n:%s {id:'%s'})-[e%s*1..%d]-(m) RETURN p",
                startType, startId, edgeFilter, depth);

        PathQueryResult pathResult = nebulaClient.queryPaths(nGQL);

        if (!pathResult.isSuccess()) {
            log.error("图库查询失败: {}", pathResult.getErrorMessage());
            return buildErrorResult(pathResult.getErrorMessage());
        }

        Map<String, String> nodeIdTypeMap = new HashMap<>();
        for (GraphNode node : pathResult.getNodes()) {
            nodeIdTypeMap.put(node.getId(), node.getType());
        }

        Map<String, Map<String, Object>> nodeAttributes = graphSyncService.batchQueryNodes(
                pathResult.getAllNodeIds(), nodeIdTypeMap);

        return mergeResults(pathResult, nodeAttributes);
    }

    /**
     * 合并图库和关系库的结果
     * 图库提供：路径、边属性
     * 关系库提供：节点业务属性
     */
    private Map<String, Object> mergeResults(PathQueryResult pathResult,
            Map<String, Map<String, Object>> nodeAttributes) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("nodeCount", pathResult.getNodes().size());
        result.put("edgeCount", pathResult.getEdges().size());

        List<Map<String, Object>> mergedNodes = new ArrayList<>();
        for (GraphNode node : pathResult.getNodes()) {
            Map<String, Object> mergedNode = new HashMap<>();
            mergedNode.put("id", node.getId());
            mergedNode.put("type", node.getType());
            mergedNode.put("createBy", node.getCreateBy());
            mergedNode.put("createTime", node.getCreateTime());
            mergedNode.put("updateBy", node.getUpdateBy());
            mergedNode.put("updateTime", node.getUpdateTime());

            Map<String, Object> attrs = nodeAttributes.get(node.getId());
            if (attrs != null) {
                mergedNode.putAll(attrs);
            }

            mergedNodes.add(mergedNode);
        }
        result.put("nodes", mergedNodes);

        List<Map<String, Object>> mergedEdges = new ArrayList<>();
        for (GraphEdge edge : pathResult.getEdges()) {
            Map<String, Object> mergedEdge = new HashMap<>();
            mergedEdge.put("id", edge.getId());
            mergedEdge.put("type", edge.getType());
            mergedEdge.put("fromId", edge.getFromId());
            mergedEdge.put("fromType", edge.getFromType());
            mergedEdge.put("toId", edge.getToId());
            mergedEdge.put("toType", edge.getToType());
            mergedEdge.put("createBy", edge.getCreateBy());
            mergedEdge.put("createTime", edge.getCreateTime());
            mergedEdge.put("updateBy", edge.getUpdateBy());
            mergedEdge.put("updateTime", edge.getUpdateTime());
            mergedEdge.put("properties", edge.getProperties());
            mergedEdges.add(mergedEdge);
        }
        result.put("edges", mergedEdges);

        return result;
    }

    /**
     * 构建错误结果
     */
    private Map<String, Object> buildErrorResult(String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("errorMessage", errorMessage);
        return result;
    }
}