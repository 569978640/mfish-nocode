package cn.com.mfish.graph.query;

import cn.com.mfish.graph.pool.NebulaSessionPool;
import cn.com.mfish.graph.schema.SchemaUtils;
import com.vesoft.nebula.client.graph.data.ResultSet;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 路径查询
 * 支持点对点路径、k步查询、循环检测
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class PathQuery {
    private final NebulaSessionPool sessionPool;
    private int maxHop = 3;
    private boolean withIntermediate = false;
    private boolean withPath = true;
    private String edgeTypeFilter;
    private Long ttlMs;
    private CycleDetectionConfig cycleDetectionConfig;

    public PathQuery(NebulaSessionPool sessionPool) {
        this.sessionPool = sessionPool;
    }

    public static PathQuery of(NebulaSessionPool sessionPool) {
        return new PathQuery(sessionPool);
    }

    public PathQuery maxHop(int maxHop) {
        this.maxHop = maxHop;
        return this;
    }

    public PathQuery withIntermediate(boolean withIntermediate) {
        this.withIntermediate = withIntermediate;
        return this;
    }

    public PathQuery withPath(boolean withPath) {
        this.withPath = withPath;
        return this;
    }

    public PathQuery edgeType(String edgeType) {
        this.edgeTypeFilter = edgeType;
        return this;
    }

    public PathQuery enableResultCache(long ttlMs) {
        this.ttlMs = ttlMs;
        return this;
    }

    public PathQuery setCycleDetectionConfig(CycleDetectionConfig config) {
        this.cycleDetectionConfig = config;
        return this;
    }

    /**
     * 构建 FIND PATH nGQL 语句
     */
    private String buildFindPathNgql(String fromVid, String toVid) {
        StringBuilder ngql = new StringBuilder();
        ngql.append("FIND ALL PATH FROM ");
        ngql.append(SchemaUtils.quote(fromVid));
        ngql.append(" TO ");
        ngql.append(SchemaUtils.quote(toVid));
        ngql.append(" OVER ");
        if (edgeTypeFilter != null) {
            ngql.append(SchemaUtils.quote(edgeTypeFilter));
        } else {
            ngql.append("*");
        }
        ngql.append(" UP TO ").append(maxHop).append(" STEPS");
        ngql.append(" YIELD path AS p");
        return ngql.toString();
    }

    /**
     * 统计两点间的路径总数
     */
    private int countTotalPaths(String fromVid, String toVid) {
        String ngql = buildFindPathNgql(fromVid, toVid);
        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            return result.isSucceeded() ? result.rowsSize() : 0;
        } catch (Exception e) {
            log.error("统计路径总数失败: {} -> {}", fromVid, toVid, e);
            return 0;
        }
    }

    /**
     * 查询 k 步内的邻居节点
     *
     * @param vid 起始点 VID
     * @return 邻居 VID 列表
     */
    public List<String> findNeighbors(String vid) {
        StringBuilder ngql = new StringBuilder();
        ngql.append("GO 1 TO ");
        if (edgeTypeFilter != null) {
            ngql.append(SchemaUtils.quote(edgeTypeFilter));
        } else {
            ngql.append("*");
        }
        ngql.append(" FROM ");
        ngql.append(SchemaUtils.quote(vid));
        ngql.append(" UP TO ").append(maxHop).append(" STEPS");

        try {
            ResultSet result = sessionPool.executeQuery(ngql.toString());
            List<String> neighbors = new ArrayList<>();
            if (result.isSucceeded()) {
                for (int i = 0; i < result.rowsSize(); i++) {
                    neighbors.add(result.rowValues(i).get(0).asNode().getId().toString());
                }
            }
            return neighbors;
        } catch (Exception e) {
            log.error("查询邻居节点失败: {}", vid, e);
            throw new RuntimeException("查询邻居节点失败", e);
        }
    }

    /**
     * 循环检测配置
     */
    @Data
    public static class CycleDetectionConfig {
        private int maxVisitedCount = 100;
        private int maxHopDepth = 10;
        private boolean enableVidTracking = true;
    }
}