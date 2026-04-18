package cn.com.mfish.graph.client;

import cn.com.mfish.graph.config.NebulaConfig;
import cn.com.mfish.graph.model.edge.GraphEdge;
import cn.com.mfish.graph.model.node.GraphNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * NebulaGraph HTTP API 客户端封装
 * 通过 HTTP 调用 NebulaGraph REST API 实现图数据库操作
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
@Component
public class NebulaClient {

    @Autowired
    private NebulaConfig nebulaConfig;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private static final Map<String, Object> SPACE_CACHE = new ConcurrentHashMap<>();

    public NebulaClient() {
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * 获取 NebulaGraph 服务地址
     */
    private String getNebulaAddress() {
        if (nebulaConfig.getSingle() != null && nebulaConfig.getSingle().isEnabled()) {
            return nebulaConfig.getSingle().getAddresses();
        }
        if (nebulaConfig.getCluster() != null && nebulaConfig.getCluster().isEnabled()) {
            String[] addresses = nebulaConfig.getCluster().getAddresses().split(",");
            return addresses[0];
        }
        throw new RuntimeException("NebulaGraph 配置错误：未启用单节点或集群模式");
    }

    /**
     * 获取图空间名称
     */
    private String getSpaceName() {
        if (nebulaConfig.getSpace() != null && nebulaConfig.getSpace().getName() != null) {
            return nebulaConfig.getSpace().getName();
        }
        return "plm_graph";
    }

    /**
     * 切换图空间
     */
    public void useSpace() {
        String url = String.format("http://%s/nebula", getNebulaAddress());
        String ngql = "USE " + getSpaceName();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("stmt", ngql), headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("切换图空间: {}, response: {}", getSpaceName(), response.getBody());
        } catch (Exception e) {
            log.error("切换图空间失败: {}", e.getMessage());
            throw new RuntimeException("切换图空间失败", e);
        }
    }

    /**
     * 执行 nGQL 语句
     */
    public boolean execute(String ngql) {
        String url = String.format("http://%s/nebula", getNebulaAddress());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("stmt", ngql), headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            String body = response.getBody();
            if (body != null && body.contains("\"succeeded\":true")) {
                log.debug("执行nGQL成功: {}", ngql);
                return true;
            }
            log.warn("执行nGQL失败: {}, response: {}", ngql, body);
            return false;
        } catch (Exception e) {
            log.error("执行nGQL异常: {}, error: {}", ngql, e.getMessage());
            return false;
        }
    }

    /**
     * 字符串转义，防止注入
     */
    private String escapeString(String value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("\\", "\\\\")
                         .replace("'", "\\'")
                         .replace("\"", "\\\"") + "'";
    }

    /**
     * 日期时间格式化
     */
    private String formatDateTime(Date date) {
        if (date == null) {
            return "NULL";
        }
        return "\"" + dateFormat.format(date) + "\"";
    }

    /**
     * 对象转 JSON 字符串
     */
    private String toJsonString(Object obj) {
        if (obj == null) {
            return "NULL";
        }
        try {
            return "\"" + escapeString(objectMapper.writeValueAsString(obj)) + "\"";
        } catch (JsonProcessingException e) {
            log.error("对象转JSON失败", e);
            return "NULL";
        }
    }

    /**
     * 批量 Upsert 节点（INSERT VERTEX IF NOT EXISTS）
     */
    public void batchUpsertVertices(String tagName, List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        StringBuilder ngql = new StringBuilder();
        ngql.append("INSERT VERTEX ").append(tagName)
            .append("(id, create_by, create_time, update_by, update_time) VALUES ");

        boolean first = true;
        for (GraphNode node : nodes) {
            if (!first) {
                ngql.append(", ");
            }
            first = false;

            ngql.append(escapeString(node.getId()))
                .append(":( ")
                .append(escapeString(node.getId())).append(", ")
                .append(escapeString(node.getCreateBy())).append(", ")
                .append(formatDateTime(node.getCreateTime())).append(", ")
                .append(escapeString(node.getUpdateBy())).append(", ")
                .append(formatDateTime(node.getUpdateTime()))
                .append(" )");
        }

        if (execute(ngql.toString())) {
            log.info("批量Upsert节点成功: tagName={}, count={}", tagName, nodes.size());
        } else {
            throw new RuntimeException("批量写入节点失败: " + tagName);
        }
    }

    /**
     * 批量 Upsert 边（INSERT EDGE IF NOT EXISTS）
     */
    public void batchUpsertEdges(String edgeName, List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        StringBuilder ngql = new StringBuilder();
        ngql.append("INSERT EDGE ").append(edgeName)
            .append("(id, type, create_by, create_time, update_by, update_time, ")
            .append("from_id, from_type, to_id, to_type, properties) VALUES ");

        boolean first = true;
        for (GraphEdge edge : edges) {
            if (!first) {
                ngql.append(", ");
            }
            first = false;

            ngql.append(escapeString(edge.getFromId()))
                .append("->")
                .append(escapeString(edge.getToId()))
                .append(":( ")
                .append(escapeString(edge.getId())).append(", ")
                .append(escapeString(edge.getType())).append(", ")
                .append(escapeString(edge.getCreateBy())).append(", ")
                .append(formatDateTime(edge.getCreateTime())).append(", ")
                .append(escapeString(edge.getUpdateBy())).append(", ")
                .append(formatDateTime(edge.getUpdateTime())).append(", ")
                .append(escapeString(edge.getFromId())).append(", ")
                .append(escapeString(edge.getFromType())).append(", ")
                .append(escapeString(edge.getToId())).append(", ")
                .append(escapeString(edge.getToType())).append(", ")
                .append(toJsonString(edge.getProperties()))
                .append(" )");
        }

        if (execute(ngql.toString())) {
            log.info("批量Upsert边成功: edgeName={}, count={}", edgeName, edges.size());
        } else {
            throw new RuntimeException("批量写入边失败: " + edgeName);
        }
    }

    /**
     * 删除边
     */
    public void deleteEdge(String edgeName, String fromId, String toId) {
        String ngql = String.format("DELETE EDGE %s %s->%s",
            edgeName, escapeString(fromId), escapeString(toId));

        if (execute(ngql)) {
            log.info("删除边成功: edgeName={}, fromId={}, toId={}", edgeName, fromId, toId);
        } else {
            throw new RuntimeException("删除边失败: " + edgeName);
        }
    }

    /**
     * 删除节点
     */
    public void deleteVertex(String tagName, String vertexId) {
        String ngql = String.format("DELETE VERTEX %s", escapeString(vertexId));

        if (execute(ngql)) {
            log.info("删除节点成功: tagName={}, id={}", tagName, vertexId);
        } else {
            throw new RuntimeException("删除节点失败: " + tagName);
        }
    }

    /**
     * 查询路径
     */
    public List<Map<String, Object>> queryPaths(String startId, String startType, List<String> edgeTypes, int depth) {
        String edgeTypesStr = edgeTypes.stream()
            .map(e -> ":" + e)
            .collect(Collectors.joining("|"));

        String ngql = String.format(
            "MATCH p=(n:%s {id:'%s'})-[e:%s*1..%d]->(m) RETURN p",
            startType, startId, edgeTypesStr, depth);

        return executeQuery(ngql);
    }

    /**
     * 查询邻居节点
     */
    public List<Map<String, Object>> queryNeighbors(String vertexId, String vertexType, List<String> edgeTypes) {
        String edgeTypesStr = edgeTypes.stream()
            .map(e -> ":" + e)
            .collect(Collectors.joining(","));

        String ngql = String.format(
            "MATCH (n:%s {id:'%s'})-[e:%s]->(m) RETURN m, e",
            vertexType, vertexId, edgeTypesStr);

        return executeQuery(ngql);
    }

    /**
     * 执行查询语句
     */
    private List<Map<String, Object>> executeQuery(String ngql) {
        String url = String.format("http://%s/nebula", getNebulaAddress());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("stmt", ngql), headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            String body = response.getBody();

            if (body != null && body.contains("\"succeeded\":true")) {
                return parseQueryResult(body);
            }
            log.warn("查询失败: {}, response: {}", ngql, body);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("查询异常: {}, error: {}", ngql, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析查询结果
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseQueryResult(String json) {
        try {
            Map<String, Object> result = objectMapper.readValue(json, Map.class);
            List<List<Object>> data = (List<List<Object>>) result.get("data");
            List<String> columns = (List<String>) result.get("columns");

            if (data == null || columns == null) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> results = new ArrayList<>();
            for (List<Object> row : data) {
                Map<String, Object> rowMap = new HashMap<>();
                for (int i = 0; i < columns.size() && i < row.size(); i++) {
                    rowMap.put(columns.get(i), row.get(i));
                }
                results.add(rowMap);
            }
            return results;
        } catch (Exception e) {
            log.error("解析查询结果失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 清空图数据库并重建 Schema
     */
    public void clearAndRecreateSpace() {
        String spaceName = getSpaceName();

        execute("DROP SPACE IF EXISTS " + spaceName);

        String createSpaceSql = String.format(
            "CREATE SPACE %s(partition_num = %d, replica_factor = %d, charset = utf8)",
            spaceName,
            nebulaConfig.getSpace() != null ? nebulaConfig.getSpace().getPartitionNum() : 100,
            nebulaConfig.getSpace() != null ? nebulaConfig.getSpace().getReplicaFactor() : 1
        );
        execute(createSpaceSql);

        useSpace();

        execute("CREATE TAG IF NOT EXISTS SsoOrg(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)");
        execute("CREATE TAG IF NOT EXISTS Product(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)");
        execute("CREATE TAG IF NOT EXISTS Folder(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)");
        execute("CREATE TAG IF NOT EXISTS PartMaster(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)");
        execute("CREATE TAG IF NOT EXISTS Part(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)");
        execute("CREATE TAG IF NOT EXISTS DocumentMaster(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)");
        execute("CREATE TAG IF NOT EXISTS Document(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)");

        execute("CREATE EDGE IF NOT EXISTS ContainsLink(id string NOT NULL, type string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime, from_id string NOT NULL, from_type string NOT NULL, to_id string NOT NULL, to_type string NOT NULL, properties string)");
        execute("CREATE EDGE IF NOT EXISTS PartVersionLink(id string NOT NULL, type string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime, from_id string NOT NULL, from_type string NOT NULL, to_id string NOT NULL, to_type string NOT NULL, properties string)");
        execute("CREATE EDGE IF NOT EXISTS DocVersionLink(id string NOT NULL, type string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime, from_id string NOT NULL, from_type string NOT NULL, to_id string NOT NULL, to_type string NOT NULL, properties string)");

        log.info("清空并重建图数据库Schema完成: {}", spaceName);
    }
}