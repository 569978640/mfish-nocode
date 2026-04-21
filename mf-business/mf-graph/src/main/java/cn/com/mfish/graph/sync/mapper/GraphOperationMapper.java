package cn.com.mfish.graph.sync.mapper;

import cn.com.mfish.common.graph.config.NebulaConfig.NebulaTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class GraphOperationMapper {
    private final NebulaTemplate nebulaTemplate;

    public void upsertVertex(String tagName, String id, String props) {
        try {
            String nGql = String.format("UPSERT VERTEX ON %s \"%s\" SET %s", tagName, id, props);
            nebulaTemplate.executeWithoutResult(nGql);
            log.debug("UPSERT vertex success: {}", nGql);
        } catch (Exception e) {
            log.error("UPSERT vertex failed: tag={}, id={}, props={}", tagName, id, props, e);
            throw new RuntimeException("UPSERT vertex failed: " + tagName + " " + id + " " + props, e);
        }
    }

    public void upsertEdge(String edgeName, String fromId, String toId, String props) {
        try {
            String nGql = String.format("UPSERT EDGE ON %s \"%s\" -> \"%s\"@0 SET %s", edgeName, fromId, toId, props);
            nebulaTemplate.executeWithoutResult(nGql);
            log.debug("UPSERT edge success: {}", nGql);
        } catch (Exception e) {
            log.error("UPSERT edge failed: edge={}, from={}->to={}, props={}", edgeName, fromId, toId, props, e);
            throw new RuntimeException("UPSERT edge failed: " + edgeName + " " + fromId + " " + toId + " " + props, e);
        }
    }

    public void deleteVertex(String tagName, String id) {
        try {
            String nGql = String.format("DELETE VERTEX ON %s \"%s\"", tagName, id);
            nebulaTemplate.executeWithoutResult(nGql);
            log.debug("DELETE vertex success: {}", nGql);
        } catch (Exception e) {
            log.error("DELETE vertex failed: tag={}, id={}", tagName, id, e);
            throw new RuntimeException("DELETE vertex failed: " + tagName + " " + id, e);
        }
    }

    public void deleteEdge(String edgeName, String fromId, String toId) {
        try {
            String nGql = String.format("DELETE EDGE ON %s \"%s\" -> \"%s\"@0", edgeName, fromId, toId);
            nebulaTemplate.executeWithoutResult(nGql);
            log.debug("DELETE edge success: {}", nGql);
        } catch (Exception e) {
            log.error("DELETE edge failed: edge={}, from={}->to={}", edgeName, fromId, toId, e);
            throw new RuntimeException("DELETE edge failed: " + edgeName + " " + fromId + " " + toId, e);
        }
    }

    public Integer vertexExists(String tagName, String id) {
        try {
            String nGql = String.format("FETCH PROP ON %s \"%s\" YIELD vertex AS v | YIELD COUNT(*) AS cnt", tagName, id);
            var result = nebulaTemplate.execute(nGql);
            if (result != null && result.getRows() != null && result.getRows().size() > 0) {
                return (int) result.getRows().size();
            }
            return 0;
        } catch (Exception e) {
            log.error("vertexExists failed: tag={}, id={}", tagName, id, e);
            return 0;
        }
    }

    public Integer edgeExists(String edgeName, String fromId, String toId) {
        try {
            String nGql = String.format("FETCH PROP ON %s \"%s\" -> \"%s\"@0 YIELD edge AS e | YIELD COUNT(*) AS cnt", edgeName, fromId, toId);
            var result = nebulaTemplate.execute(nGql);
            if (result != null && result.getRows() != null && result.getRows().size() > 0) {
                return (int) result.getRows().size();
            }
            return 0;
        } catch (Exception e) {
            log.error("edgeExists failed: edge={}, from={}->to={}", edgeName, fromId, toId, e);
            return 0;
        }
    }
}
