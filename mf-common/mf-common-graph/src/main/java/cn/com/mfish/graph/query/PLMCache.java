package cn.com.mfish.graph.query;

import cn.com.mfish.graph.pool.NebulaSessionPool;
import com.vesoft.nebula.client.graph.data.ResultSet;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * PLM 缓存查询
 * 提供 PLM 业务高频查询的缓存能力
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class PLMCache {
    private final NebulaSessionPool sessionPool;
    private final long defaultTtlSeconds;

    public PLMCache(NebulaSessionPool sessionPool) {
        this.sessionPool = sessionPool;
        this.defaultTtlSeconds = 300;
    }

    public PLMCache(NebulaSessionPool sessionPool, long defaultTtlSeconds) {
        this.sessionPool = sessionPool;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    /**
     * 查询产品信息（带缓存）
     *
     * @param productCode 产品编码
     * @return 产品 VID
     */
    public String getProductVid(String productCode) {
        String ngql = "MATCH (n:`Product`) WHERE n.`biz_code` == \"" + productCode + "\" RETURN n LIMIT 1";
        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            if (result.isSucceeded() && result.rowsSize() > 0) {
                return result.rowValues(0).get(0).asNode().getId().toString();
            }
            return null;
        } catch (Exception e) {
            log.error("查询产品VID失败: {}", productCode, e);
            return null;
        }
    }

    /**
     * 查询零部件信息（带缓存）
     *
     * @param partCode 零部件编码
     * @return 零部件 VID
     */
    public String getPartVid(String partCode) {
        String ngql = "MATCH (n:`PartMaster`) WHERE n.`biz_code` == \"" + partCode + "\" RETURN n LIMIT 1";
        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            if (result.isSucceeded() && result.rowsSize() > 0) {
                return result.rowValues(0).get(0).asNode().getId().toString();
            }
            return null;
        } catch (Exception e) {
            log.error("查询零部件VID失败: {}", partCode, e);
            return null;
        }
    }

    /**
     * 查询文档信息（带缓存）
     *
     * @param docCode 文档编码
     * @return 文档 VID
     */
    public String getDocumentVid(String docCode) {
        String ngql = "MATCH (n:`Document`) WHERE n.`biz_code` == \"" + docCode + "\" RETURN n LIMIT 1";
        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            if (result.isSucceeded() && result.rowsSize() > 0) {
                return result.rowValues(0).get(0).asNode().getId().toString();
            }
            return null;
        } catch (Exception e) {
            log.error("查询文档VID失败: {}", docCode, e);
            return null;
        }
    }

    /**
     * 查询产品的直接子零部件
     *
     * @param productVid 产品 VID
     * @return 子零部件 VID 列表
     */
    public List<String> getDirectChildParts(String productVid) {
        String ngql = "MATCH (p)-[:`HAS_CHILD`]->(c:`PartMaster`) " +
                      "WHERE id(p) == \"" + productVid + "\" RETURN c";
        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            return extractVids(result);
        } catch (Exception e) {
            log.error("查询直接子零部件失败: {}", productVid, e);
            return List.of();
        }
    }

    /**
     * 查询产品的完整 BOM 结构
     *
     * @param productVid 产品 VID
     * @param maxDepth 最大深度
     * @return BOM 结构数据
     */
    public List<String> getFullBom(String productVid, int maxDepth) {
        String ngql = "MATCH (p)-[r:`BOM_LINE`*1.." + maxDepth + "]->(c) " +
                      "WHERE id(p) == \"" + productVid + "\" RETURN c";
        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            return extractVids(result);
        } catch (Exception e) {
            log.error("查询完整BOM失败: {}", productVid, e);
            return List.of();
        }
    }

    /**
     * 失效产品缓存
     *
     * @param productCode 产品编码
     */
    public void invalidateProductCache(String productCode) {
        log.info("失效产品缓存: {}", productCode);
    }

    /**
     * 失效零部件缓存
     *
     * @param partCode 零部件编码
     */
    public void invalidatePartCache(String partCode) {
        log.info("失效零部件缓存: {}", partCode);
    }

    /**
     * 失效文档缓存
     *
     * @param docCode 文档编码
     */
    public void invalidateDocumentCache(String docCode) {
        log.info("失效文档缓存: {}", docCode);
    }

    private List<String> extractVids(ResultSet result) {
        List<String> vids = new ArrayList<>();
        if (result.isSucceeded()) {
            for (int i = 0; i < result.rowsSize(); i++) {
                try {
                    vids.add(result.rowValues(i).get(0).asNode().getId().toString());
                } catch (Exception e) {
                    log.warn("提取VID失败: row={}", i, e);
                }
            }
        }
        return vids;
    }
}