package cn.com.mfish.graph.sync.graph.impl;

import cn.com.mfish.graph.sync.graph.*;
import cn.com.mfish.graph.sync.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图数据库操作服务实现（NgBatis 风格）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphOperationServiceImpl implements GraphOperationService {
    private final GraphOperationDao graphOperationDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void upsertVertex(VertexInfo vertex) {
        try {
            String props = buildPropsNQL(vertex.getProperties());
            graphOperationDao.upsertVertex(vertex.getTagName(), vertex.getId(), props);
            log.debug("UPSERT vertex: tag={}, id={}", vertex.getTagName(), vertex.getId());
        } catch (Exception e) {
            log.error("UPSERT vertex failed: tag={}, id={}", vertex.getTagName(), vertex.getId(), e);
            throw new RuntimeException("UPSERT vertex failed", e);
        }
    }

    @Override
    public void upsertEdge(EdgeInfo edge) {
        try {
            String props = buildPropsNQL(edge.getProperties());
            graphOperationDao.upsertEdge(edge.getEdgeName(), edge.getFromId(), edge.getToId(), props);
            log.debug("UPSERT edge: edge={}, from={}->to={}", edge.getEdgeName(), edge.getFromId(), edge.getToId());
        } catch (Exception e) {
            log.error("UPSERT edge failed: edge={}, from={}->to={}", edge.getEdgeName(), edge.getFromId(), edge.getToId(), e);
            throw new RuntimeException("UPSERT edge failed", e);
        }
    }

    @Override
    public void deleteVertex(String tagName, String id) {
        try {
            graphOperationDao.deleteVertex(tagName, id);
            log.debug("DELETE vertex: tag={}, id={}", tagName, id);
        } catch (Exception e) {
            log.error("DELETE vertex failed: tag={}, id={}", tagName, id, e);
            throw new RuntimeException("DELETE vertex failed", e);
        }
    }

    @Override
    public void deleteEdge(String edgeName, String fromId, String toId) {
        try {
            graphOperationDao.deleteEdge(edgeName, fromId, toId);
            log.debug("DELETE edge: edge={}, from={}->to={}", edgeName, fromId, toId);
        } catch (Exception e) {
            log.error("DELETE edge failed: edge={}, from={}->to={}", edgeName, fromId, toId, e);
            throw new RuntimeException("DELETE edge failed", e);
        }
    }

    @Override
    public void batchUpsertVertex(List<VertexInfo> vertices) {
        if (vertices == null || vertices.isEmpty()) {
            return;
        }
        for (VertexInfo vertex : vertices) {
            upsertVertex(vertex);
        }
        log.debug("Batch UPSERT {} vertices", vertices.size());
    }

    @Override
    public void batchUpsertEdge(List<EdgeInfo> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        for (EdgeInfo edge : edges) {
            upsertEdge(edge);
        }
        log.debug("Batch UPSERT {} edges", edges.size());
    }

    @Override
    public boolean vertexExists(String tagName, String id) {
        try {
            Integer count = graphOperationDao.vertexExists(tagName, id);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("vertexExists failed: tag={}, id={}", tagName, id, e);
            return false;
        }
    }

    @Override
    public boolean edgeExists(String edgeName, String fromId, String toId) {
        try {
            Integer count = graphOperationDao.edgeExists(edgeName, fromId, toId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("edgeExists failed: edge={}, from={}->to={}", edgeName, fromId, toId, e);
            return false;
        }
    }

    private String buildPropsNQL(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "";
        }
        return properties.entrySet().stream()
            .map(entry -> {
                String key = entry.getKey();
                Object value = entry.getValue();
                return key + " = " + formatValue(value);
            })
            .collect(Collectors.joining(", "));
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        if (value instanceof java.util.Date) {
            return "TIMESTAMP(\"" + value + "\")";
        }
        return value.toString();
    }
}
