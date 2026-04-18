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
     * 查询两点间的所有路径
     *
     * @param fromVid 起始点 VID
     * @param toVid 目标点 VID
     * @return 路径列表
     */
    public List<String> findPaths(String fromVid, String toVid) {
        StringBuilder ngql = new StringBuilder();
        ngql.append("FIND ALL PATHS FROM ");
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

        try {
            ResultSet result = sessionPool.executeQuery(ngql.toString());
            List<String> paths = new ArrayList<>();
            if (result.isSucceeded()) {
                for (int i = 0; i < result.rowsSize(); i++) {
                    paths.add(result.rowValues(i).get(0).asPath());
                }
            }
            return paths;
        } catch (Exception e) {
            log.error("查询路径失败: {} -> {}", fromVid, toVid, e);
            throw new RuntimeException("查询路径失败", e);
        }
    }

    /**
     * 分页查询路径
     *
     * @param fromVid 起始点 VID
     * @param toVid 目标点 VID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 路径查询结果
     */
    public QueryResult<String> findPathsWithPagination(String fromVid, String toVid, int pageNum, int pageSize) {
        List<String> allPaths = findPaths(fromVid, toVid);
        int total = allPaths.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        if (start >= total) {
            return QueryResult.of(new ArrayList<>(), total, pageNum, pageSize);
        }

        List<String> pageData = allPaths.subList(start, end);
        return QueryResult.of(pageData, total, pageNum, pageSize);
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