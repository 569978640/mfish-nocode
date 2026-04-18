package cn.com.mfish.graph.query;

import cn.com.mfish.graph.pool.NebulaSessionPool;
import cn.com.mfish.graph.schema.SchemaUtils;
import com.vesoft.nebula.client.graph.data.ResultSet;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 版本化查询
 * 支持历史版本查询和多版本数据管理
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class VersionedQuery {
    private final NebulaSessionPool sessionPool;
    private final Map<String, Map<Long, String>> versionCache = new ConcurrentHashMap<>();
    private VersionGenerator versionGenerator = VersionGenerator.TIMESTAMP;
    private String schemaName;

    public VersionedQuery(NebulaSessionPool sessionPool) {
        this.sessionPool = sessionPool;
    }

    public static VersionedQuery of(NebulaSessionPool sessionPool) {
        return new VersionedQuery(sessionPool);
    }

    public VersionedQuery schema(String schemaName) {
        this.schemaName = schemaName;
        return this;
    }

    public VersionedQuery setVersionGenerator(VersionGenerator generator) {
        this.versionGenerator = generator;
        return this;
    }

    /**
     * 查询指定版本的数据
     *
     * @param vid VID
     * @param version 版本号
     * @return 数据
     */
    public Map<String, Object> queryVersion(String vid, long version) {
        String ngql = "MATCH (n:" + SchemaUtils.quote(schemaName) + ") " +
                      "WHERE id(n) == " + SchemaUtils.quote(vid) + " AND n.version <= " + version + " " +
                      "RETURN n ORDER BY n.version DESC LIMIT 1";

        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            if (result.isSucceeded() && result.rowsSize() > 0) {
                return convertToMap(result, 0);
            }
            return new HashMap<>();
        } catch (Exception e) {
            log.error("查询版本数据失败: vid={}, version={}", vid, version, e);
            throw new RuntimeException("查询版本数据失败", e);
        }
    }

    /**
     * 查询历史版本列表
     *
     * @param vid VID
     * @param startVersion 起始版本
     * @param endVersion 结束版本
     * @return 版本列表
     */
    public List<Map<String, Object>> queryVersionHistory(String vid, long startVersion, long endVersion) {
        String ngql = "MATCH (n:" + SchemaUtils.quote(schemaName) + ") " +
                      "WHERE id(n) == " + SchemaUtils.quote(vid) + " " +
                      "AND n.version >= " + startVersion + " AND n.version <= " + endVersion + " " +
                      "RETURN n ORDER BY n.version DESC";

        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            List<Map<String, Object>> history = new ArrayList<>();
            if (result.isSucceeded()) {
                for (int i = 0; i < result.rowsSize(); i++) {
                    history.add(convertToMap(result, i));
                }
            }
            return history;
        } catch (Exception e) {
            log.error("查询版本历史失败: vid={}", vid, e);
            throw new RuntimeException("查询版本历史失败", e);
        }
    }

    /**
     * 查询最新版本数据
     *
     * @param vid VID
     * @return 最新数据
     */
    public Map<String, Object> queryLatestVersion(String vid) {
        String ngql = "MATCH (n:" + SchemaUtils.quote(schemaName) + ") " +
                      "WHERE id(n) == " + SchemaUtils.quote(vid) + " " +
                      "RETURN n ORDER BY n.version DESC LIMIT 1";

        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            if (result.isSucceeded() && result.rowsSize() > 0) {
                return convertToMap(result, 0);
            }
            return new HashMap<>();
        } catch (Exception e) {
            log.error("查询最新版本失败: vid={}", vid, e);
            throw new RuntimeException("查询最新版本失败", e);
        }
    }

    /**
     * 压缩历史版本数据
     *
     * @param schemaName Schema 名称
     * @param retainVersions 保留版本数
     */
    public void compressHistoricalVersions(String schemaName, int retainVersions) {
        String ngql = "MATCH (n:" + SchemaUtils.quote(schemaName) + ") " +
                      "WITH n ORDER BY n.version DESC " +
                      "SKIP " + retainVersions + " " +
                      "DETACH DELETE n";

        try {
            sessionPool.executeWrite(ngql);
            log.info("压缩历史版本完成: schema={}, retainVersions={}", schemaName, retainVersions);
        } catch (Exception e) {
            log.error("压缩历史版本失败: schema={}", schemaName, e);
            throw new RuntimeException("压缩历史版本失败", e);
        }
    }

    /**
     * 预加载热点版本
     *
     * @param schemaName Schema 名称
     * @param hotVersions 热点版本列表
     */
    public void preloadHotVersions(String schemaName, List<Long> hotVersions) {
        versionCache.putIfAbsent(schemaName, new ConcurrentHashMap<>());
        Map<Long, String> versionMap = versionCache.get(schemaName);

        for (Long version : hotVersions) {
            if (!versionMap.containsKey(version)) {
                String ngql = "MATCH (n:" + SchemaUtils.quote(schemaName) + ") " +
                              "WHERE n.version == " + version + " RETURN n LIMIT 1";
                try {
                    ResultSet result = sessionPool.executeQuery(ngql);
                    if (result.isSucceeded() && result.rowsSize() > 0) {
                        versionMap.put(version, result.rowValues(0).toString());
                    }
                } catch (Exception e) {
                    log.warn("预加载热点版本失败: schema={}, version={}", schemaName, version, e);
                }
            }
        }
    }

    private Map<String, Object> convertToMap(ResultSet result, int rowIndex) {
        Map<String, Object> map = new HashMap<>();
        return map;
    }

    /**
     * 版本号生成器
     */
    public enum VersionGenerator {
        /**
         * 时间戳版本
         */
        TIMESTAMP,
        /**
         * 自增版本
         */
        AUTO_INCREMENT,
        /**
         * 业务指定版本
         */
        BUSINESS_SPECIFIC
    }
}