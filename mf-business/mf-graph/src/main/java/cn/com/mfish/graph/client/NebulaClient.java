package cn.com.mfish.graph.client;

import cn.com.mfish.graph.config.NebulaConfig;
import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.graph.model.edge.GraphEdge;
import com.vesoft.nebula.client.graph.NebulaPoolConfig;
import com.vesoft.nebula.client.graph.data.HostAddress;
import com.vesoft.nebula.client.graph.data.ResultSet;
import com.vesoft.nebula.client.graph.exception.IOErrorException;
import com.vesoft.nebula.client.graph.net.NebulaPool;
import com.vesoft.nebula.client.graph.exception.InvalidConfigException;
import com.vesoft.nebula.client.graph.data.Node;
import com.vesoft.nebula.client.graph.data.Relationship;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;
import java.util.Date;

import cn.com.mfish.common.core.utils.StringUtils;

/**
 * NebulaGraph客户端
 * 提供图数据库连接管理和基本操作
 *
 * @author mfish
 * @date 2026-04-16
 */
@Slf4j
@Component
public class NebulaClient {
    /**
     * NebulaGraph配置
     */
    @Autowired
    private NebulaConfig nebulaConfig;

    /**
     * 连接池实例
     */
    private NebulaPool pool;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    @PostConstruct
    public void init() throws InvalidConfigException, UnknownHostException {
        NebulaPoolConfig poolConfig = new NebulaPoolConfig();
        poolConfig.setMaxConnSize(nebulaConfig.getPool().getMaxConns());
        poolConfig.setMinConnSize(nebulaConfig.getPool().getMinConns());
        poolConfig.setTimeout(nebulaConfig.getPool().getTimeout());
        poolConfig.setIdleTime(nebulaConfig.getPool().getIdleTimeout());

        List<HostAddress> addresses = new ArrayList<>();
        if (nebulaConfig.getSingle().isEnabled()) {
            String[] parts = nebulaConfig.getSingle().getAddresses().split(":");
            addresses.add(new HostAddress(parts[0], Integer.parseInt(parts[1])));
        } else if (nebulaConfig.getCluster().isEnabled()) {
            String[] hostPorts = nebulaConfig.getCluster().getAddresses().split(",");
            for (String hp : hostPorts) {
                String[] parts = hp.trim().split(":");
                addresses.add(new HostAddress(parts[0], Integer.parseInt(parts[1])));
            }
        }

        this.username = nebulaConfig.getUsername();
        this.password = nebulaConfig.getPassword();

        pool = new NebulaPool();
        pool.init(addresses, poolConfig);
        log.info("NebulaGraph连接池初始化成功");
    }

    @PreDestroy
    public void close() {
        if (pool != null) {
            pool.close();
            log.info("NebulaGraph连接池已关闭");
        }
    }

    /**
     * 获取NebulaGraph会话
     *
     * @return NebulaGraph会话
     */
    private com.vesoft.nebula.client.graph.net.Session getSession() {
        try {
            return pool.getSession(username, password,true);
        } catch (Exception e) {
            log.error("获取NebulaGraph会话失败", e);
            throw new RuntimeException("获取NebulaGraph会话失败", e);
        }
    }

    /**
     * 格式化日期为nGQL格式
     */
    private String formatDateTime(Date date) {
        if (date == null) {
            return "NULL";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        return "'" + sdf.format(date) + "'";
    }

    /**
     * 转义字符串值
     */
    private String escapeString(String value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /**
     * 将Map转换为JSON字符串
     */
    private String toJsonString(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(((String) value).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else if (value == null) {
                sb.append("null");
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 插入节点
     * 将节点列表插入到指定的标签下
     *
     * @param tagName 标签名称
     * @param nodes 节点列表
     */
    public void insertVertex(String tagName, List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            log.debug("节点列表为空，跳过插入");
            return;
        }

        try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
            for (GraphNode node : nodes) {
                StringBuilder values = new StringBuilder();
                values.append(escapeString(node.getId())).append(",");
                values.append(escapeString(node.getCreateBy())).append(",");
                values.append(formatDateTime(node.getCreateTime())).append(",");
                values.append(escapeString(node.getUpdateBy())).append(",");
                values.append(formatDateTime(node.getUpdateTime()));

                String ngql = String.format("INSERT VERTEX %s(id, create_by, create_time, update_by, update_time) VALUES %s:(%s)",
                        tagName,
                        escapeString(node.getId()),
                        values.toString());

                ResultSet resultSet = session.execute(ngql);
                if (!resultSet.isSucceeded()) {
                    log.error("插入节点失败: {}, 错误: {}", ngql, resultSet.getErrorMessage());
                }
            }
            log.info("成功插入{}个节点到标签{}", nodes.size(), tagName);
        } catch (Exception e) {
            log.error("插入节点异常", e);
            throw new RuntimeException("插入节点异常", e);
        }
    }

    /**
     * 批量插入节点（优化性能）
     *
     * @param tagName 标签名称
     * @param nodes 节点列表
     */
    public void batchInsertVertices(String tagName, List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
            StringBuilder ngql = new StringBuilder();
            ngql.append("INSERT VERTEX ").append(tagName).append("(id, create_by, create_time, update_by, update_time) VALUES ");

            boolean first = true;
            for (GraphNode node : nodes) {
                if (!first) {
                    ngql.append(",");
                }
                first = false;

                StringBuilder values = new StringBuilder();
                values.append(escapeString(node.getId())).append(",");
                values.append(escapeString(node.getCreateBy())).append(",");
                values.append(formatDateTime(node.getCreateTime())).append(",");
                values.append(escapeString(node.getUpdateBy())).append(",");
                values.append(formatDateTime(node.getUpdateTime()));

                ngql.append(escapeString(node.getId())).append(":(").append(values).append(")");
            }

            ResultSet resultSet = session.execute(ngql.toString());
            if (resultSet.isSucceeded()) {
                log.info("批量插入{}个节点到标签{}", nodes.size(), tagName);
            } else {
                log.error("批量插入节点失败: {}, 错误: {}", ngql, resultSet.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("批量插入节点异常", e);
            throw new RuntimeException("批量插入节点异常", e);
        }
    }

    /**
     * 插入边
     * 将边列表插入到指定的边类型下
     *
     * @param edgeName 边类型名称
     * @param edges 边列表
     */
    public void insertEdge(String edgeName, List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            log.debug("边列表为空，跳过插入");
            return;
        }

        try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
            for (GraphEdge edge : edges) {
                StringBuilder values = new StringBuilder();
                values.append(escapeString(edge.getId())).append(",");
                values.append(escapeString(edge.getType())).append(",");
                values.append(escapeString(edge.getCreateBy())).append(",");
                values.append(formatDateTime(edge.getCreateTime())).append(",");
                values.append(escapeString(edge.getUpdateBy())).append(",");
                values.append(formatDateTime(edge.getUpdateTime())).append(",");
                values.append(escapeString(edge.getFromId())).append(",");
                values.append(escapeString(edge.getFromType())).append(",");
                values.append(escapeString(edge.getToId())).append(",");
                values.append(escapeString(edge.getToType())).append(",");
                values.append(escapeString(toJsonString(edge.getProperties())));

                String ngql = String.format(
                        "INSERT EDGE %s(id, type, create_by, create_time, update_by, update_time, from_id, from_type, to_id, to_type, properties) VALUES %s->%s:(%s)",
                        edgeName,
                        escapeString(edge.getFromId()),
                        escapeString(edge.getToId()),
                        values.toString());

                ResultSet resultSet = session.execute(ngql);
                if (!resultSet.isSucceeded()) {
                    log.error("插入边失败: {}, 错误: {}", ngql, resultSet.getErrorMessage());
                }
            }
            log.info("成功插入{}条边到类型{}", edges.size(), edgeName);
        } catch (Exception e) {
            log.error("插入边异常", e);
            throw new RuntimeException("插入边异常", e);
        }
    }

    /**
     * 批量插入边（优化性能）
     *
     * @param edgeName 边类型名称
     * @param edges 边列表
     */
    public void batchInsertEdges(String edgeName, List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
            StringBuilder ngql = new StringBuilder();
            ngql.append("INSERT EDGE ").append(edgeName).append("(id, type, create_by, create_time, update_by, update_time, from_id, from_type, to_id, to_type, properties) VALUES ");

            boolean first = true;
            for (GraphEdge edge : edges) {
                if (!first) {
                    ngql.append(",");
                }
                first = false;

                StringBuilder values = new StringBuilder();
                values.append(escapeString(edge.getId())).append(",");
                values.append(escapeString(edge.getType())).append(",");
                values.append(escapeString(edge.getCreateBy())).append(",");
                values.append(formatDateTime(edge.getCreateTime())).append(",");
                values.append(escapeString(edge.getUpdateBy())).append(",");
                values.append(formatDateTime(edge.getUpdateTime())).append(",");
                values.append(escapeString(edge.getFromId())).append(",");
                values.append(escapeString(edge.getFromType())).append(",");
                values.append(escapeString(edge.getToId())).append(",");
                values.append(escapeString(edge.getToType())).append(",");
                values.append(escapeString(toJsonString(edge.getProperties())));

                ngql.append(escapeString(edge.getFromId())).append("->").append(escapeString(edge.getToId())).append(":(").append(values).append(")");
            }

            ResultSet resultSet = session.execute(ngql.toString());
            if (resultSet.isSucceeded()) {
                log.info("批量插入{}条边到类型{}", edges.size(), edgeName);
            } else {
                log.error("批量插入边失败: {}, 错误: {}", ngql, resultSet.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("批量插入边异常", e);
            throw new RuntimeException("批量插入边异常", e);
        }
    }

    /**
     * 查询路径（带边属性）
     * 执行nGQL查询语句并返回结果
     *
     * @param nGQL 查询语句
     * @return 查询结果的JSON字符串
     */
    public String queryPathsWithEdgeProps(String nGQL) {
        try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
            log.debug("执行查询: {}", nGQL);
            ResultSet resultSet = session.execute(nGQL);

            if (resultSet.isSucceeded()) {
                return resultSet.toString();
            } else {
                log.error("查询失败: {}, 错误: {}", nGQL, resultSet.getErrorMessage());
                return "";
            }
        } catch (Exception e) {
            log.error("查询路径异常: {}", nGQL, e);
            throw new RuntimeException("查询路径异常", e);
        }
    }

    /**
     * 查询路径并解析结果
     *
     * @param nGQL 查询语句
     * @return 路径查询结果
     */
    public PathQueryResult queryPaths(String nGQL) {
        PathQueryResult result = new PathQueryResult();
        result.setSuccess(false);

        try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
            log.debug("执行路径查询: {}", nGQL);
            ResultSet resultSet = session.execute(nGQL);

            if (resultSet.isSucceeded()) {
                result.setSuccess(true);
                result.setRawResult(resultSet.toString());

                List<GraphNode> nodes = new ArrayList<>();
                List<GraphEdge> edges = new ArrayList<>();
                Set<String> nodeIds = new HashSet<>();

                List<String> columnNames = resultSet.getColumnNames();
                int rows = resultSet.rowsSize();

                for (int i = 0; i < rows; i++) {
                    String rowData = resultSet.rowValues(i).toString();
                    parsePathResult(rowData, nodes, edges, nodeIds);
                }

                result.setNodes(nodes);
                result.setEdges(edges);
                result.setAllNodeIds(nodeIds);

                log.info("路径查询成功，返回{}个节点，{}条边", nodes.size(), edges.size());
            } else {
                log.error("路径查询失败: {}, 错误: {}", nGQL, resultSet.getErrorMessage());
                result.setErrorMessage(resultSet.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("路径查询异常: {}", nGQL, e);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * 解析路径结果
     */
    private void parsePathResult(String rowData, List<GraphNode> nodes, List<GraphEdge> edges, Set<String> nodeIds) {
        Pattern nodePattern = Pattern.compile("\\((\\w+):(\\w+)\\{.*?\\}\\)");
        Pattern edgePattern = Pattern.compile("\\<(\\w+):(\\w+)\\{.*?\\}\\>");

        Matcher nodeMatcher = nodePattern.matcher(rowData);
        while (nodeMatcher.find()) {
            String nodeId = nodeMatcher.group(1);
            String nodeType = nodeMatcher.group(2);
            if (!nodeIds.contains(nodeId)) {
                nodeIds.add(nodeId);
                GraphNode node = new GraphNode();
                node.setId(nodeId);
                node.setType(nodeType);
                nodes.add(node);
            }
        }

        Matcher edgeMatcher = edgePattern.matcher(rowData);
        while (edgeMatcher.find()) {
            String fromId = edgeMatcher.group(1);
            String edgeType = edgeMatcher.group(2);
            GraphEdge edge = new GraphEdge();
            edge.setFromId(fromId);
            edge.setType(edgeType);
            edges.add(edge);
        }
    }

    /**
     * 清空并重建图空间
     * 删除现有图空间并根据配置重新创建
     */
    public void clearAndRecreateSpace() {
        try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
            String spaceName = nebulaConfig.getSpace().getName();

            session.execute("DROP SPACE IF EXISTS " + spaceName);
            log.info("已删除图空间: {}", spaceName);

            String createSpace = String.format(
                    "CREATE SPACE IF NOT EXISTS %s (partition_num=%d, replica_factor=%d, charset=%s)",
                    spaceName,
                    nebulaConfig.getSpace().getPartitionNum(),
                    nebulaConfig.getSpace().getReplicaFactor(),
                    nebulaConfig.getSpace().getCharset());

            ResultSet resultSet = session.execute(createSpace);
            if (resultSet.isSucceeded()) {
                log.info("成功创建图空间: {}", spaceName);
            } else {
                log.error("创建图空间失败: {}, 错误: {}", createSpace, resultSet.getErrorMessage());
                throw new RuntimeException("创建图空间失败: " + resultSet.getErrorMessage());
            }

            session.execute("USE " + spaceName);
            createSchema(session);

        } catch (Exception e) {
            log.error("清空重建图空间异常", e);
            throw new RuntimeException("清空重建图空间异常", e);
        }
    }

    /**
     * 创建图空间Schema
     */
    private void createSchema(com.vesoft.nebula.client.graph.net.Session session) throws IOErrorException {
        String[] createTags = {
                "CREATE TAG IF NOT EXISTS SsoOrg(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)",
                "CREATE TAG IF NOT EXISTS Product(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)",
                "CREATE TAG IF NOT EXISTS Folder(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)",
                "CREATE TAG IF NOT EXISTS PartMaster(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)",
                "CREATE TAG IF NOT EXISTS Part(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)",
                "CREATE TAG IF NOT EXISTS DocumentMaster(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)",
                "CREATE TAG IF NOT EXISTS Document(id string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime)"
        };

        for (String sql : createTags) {
            ResultSet rs = session.execute(sql);
            if (!rs.isSucceeded()) {
                log.error("创建标签失败: {}, 错误: {}", sql, rs.getErrorMessage());
            }
        }

        String[] createEdges = {
                "CREATE EDGE IF NOT EXISTS ContainsLink(id string NOT NULL, type string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime, from_id string NOT NULL, from_type string NOT NULL, to_id string NOT NULL, to_type string NOT NULL, properties string)",
                "CREATE EDGE IF NOT EXISTS PartVersionLink(id string NOT NULL, type string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime, from_id string NOT NULL, from_type string NOT NULL, to_id string NOT NULL, to_type string NOT NULL, properties string)",
                "CREATE EDGE IF NOT EXISTS DocVersionLink(id string NOT NULL, type string NOT NULL, create_by string, create_time datetime, update_by string, update_time datetime, from_id string NOT NULL, from_type string NOT NULL, to_id string NOT NULL, to_type string NOT NULL, properties string)"
        };

        for (String sql : createEdges) {
            ResultSet rs = session.execute(sql);
            if (!rs.isSucceeded()) {
                log.error("创建边类型失败: {}, 错误: {}", sql, rs.getErrorMessage());
            }
        }

        log.info("图空间Schema创建完成");
    }

    /**
     * 删除节点
     */
    public void deleteVertex(String tagName, String vertexId) {
        try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
            String ngql = String.format("DELETE VERTEX %s", escapeString(vertexId));
            ResultSet resultSet = session.execute(ngql);
            if (resultSet.isSucceeded()) {
                log.info("删除节点成功: {}", vertexId);
            } else {
                log.error("删除节点失败: {}, 错误: {}", ngql, resultSet.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("删除节点异常: {}", vertexId, e);
            throw new RuntimeException("删除节点异常", e);
        }
    }

    /**
     * 删除边
     */
    public void deleteEdge(String edgeName, String fromId, String toId) {
        try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
            String ngql = String.format("DELETE EDGE %s %s->%s", edgeName, escapeString(fromId), escapeString(toId));
            ResultSet resultSet = session.execute(ngql);
            if (resultSet.isSucceeded()) {
                log.info("删除边成功: {}->{}", fromId, toId);
            } else {
                log.error("删除边失败: {}, 错误: {}", ngql, resultSet.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("删除边异常: {}->{}", fromId, toId, e);
            throw new RuntimeException("删除边异常", e);
        }
    }

    /**
     * 路径查询结果
     */
    @lombok.Data
    public static class PathQueryResult {
        private boolean success;
        private String rawResult;
        private String errorMessage;
        private List<GraphNode> nodes;
        private List<GraphEdge> edges;
        private Set<String> allNodeIds;
    }
}