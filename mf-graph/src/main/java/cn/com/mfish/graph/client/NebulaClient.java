package cn.com.mfish.graph.client;

import cn.com.mfish.graph.config.NebulaConfig;
import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.graph.model.edge.GraphEdge;
import com.vesoft.nebula.client.graph.NebulaPoolConfig;
import com.vesoft.nebula.client.graph.data.HostAddress;
import com.vesoft.nebula.client.graph.data.ResultSet;
import com.vesoft.nebula.client.graph.net.NebulaPool;
import com.vesoft.nebula.client.graph.exception.InvalidConfigException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.ArrayList;

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
     * 初始化连接池
     * 在Bean创建后自动调用，建立与NebulaGraph的连接
     *
     * @throws InvalidConfigException 配置无效时抛出
     */
    @PostConstruct
    public void init() throws InvalidConfigException {
        // 创建连接池配置
        NebulaPoolConfig poolConfig = new NebulaPoolConfig();
        poolConfig.setMaxConns(nebulaConfig.getPool().getMaxConns());
        poolConfig.setMinConns(nebulaConfig.getPool().getMinConns());
        poolConfig.setTimeout(nebulaConfig.getPool().getTimeout());
        poolConfig.setIdleTime(nebulaConfig.getPool().getIdleTimeout());

        // 解析地址列表
        List<HostAddress> addresses = new ArrayList<>();
        if (nebulaConfig.getSingle().isEnabled()) {
            // 单机模式
            String[] parts = nebulaConfig.getSingle().getAddresses().split(":");
            addresses.add(new HostAddress(parts[0], Integer.parseInt(parts[1])));
        } else if (nebulaConfig.getCluster().isEnabled()) {
            // 集群模式
            String[] hostPorts = nebulaConfig.getCluster().getAddresses().split(",");
            for (String hp : hostPorts) {
                String[] parts = hp.trim().split(":");
                addresses.add(new HostAddress(parts[0], Integer.parseInt(parts[1])));
            }
        }

        // 初始化连接池
        pool = new NebulaPool();
        pool.init(addresses, poolConfig, nebulaConfig.getUsername(), nebulaConfig.getPassword());
        log.info("NebulaGraph连接池初始化成功");
    }

    /**
     * 关闭连接池
     * 在Bean销毁前自动调用，释放资源
     */
    @PreDestroy
    public void close() {
        if (pool != null) {
            pool.close();
            log.info("NebulaGraph连接池已关闭");
        }
    }

    /**
     * 插入节点
     * 将节点列表插入到指定的标签下
     *
     * @param tagName 标签名称
     * @param nodes 节点列表
     */
    public void insertVertex(String tagName, List<GraphNode> nodes) {
        // TODO: 实现插入节点逻辑
        // 示例实现：
        // try (Session session = pool.getSession(username, password, true)) {
        //     for (GraphNode node : nodes) {
        //         String ngql = String.format("INSERT VERTEX %s(%s) VALUES \"%s\":(%s)",
        //             tagName, properties, node.getId(), values);
        //         session.execute(ngql);
        //     }
        // }
        log.debug("插入节点到标签{}，数量：{}", tagName, nodes.size());
    }

    /**
     * 插入边
     * 将边列表插入到指定的边类型下
     *
     * @param edgeName 边类型名称
     * @param edges 边列表
     */
    public void insertEdge(String edgeName, List<GraphEdge> edges) {
        // TODO: 实现插入边逻辑
        // 示例实现：
        // try (Session session = pool.getSession(username, password, true)) {
        //     for (GraphEdge edge : edges) {
        //         String ngql = String.format("INSERT EDGE %s(%s) VALUES \"%s\"->\"%s\":(%s)",
        //             edgeName, properties, edge.getSrcId(), edge.getDstId(), values);
        //         session.execute(ngql);
        //     }
        // }
        log.debug("插入边到类型{}，数量：{}", edgeName, edges.size());
    }

    /**
     * 查询路径（带边属性）
     * 执行nGQL查询语句并返回结果
     *
     * @param nGQL 查询语句
     * @return 查询结果的JSON字符串
     */
    public String queryPathsWithEdgeProps(String nGQL) {
        // TODO: 实现路径查询逻辑
        // 示例实现：
        // try (Session session = pool.getSession(username, password, true)) {
        //     ResultSet resultSet = session.execute(nGQL);
        //     if (resultSet.isSucceeded()) {
        //         return resultSet.toString();
        //     } else {
        //         log.error("查询失败：{}", resultSet.getErrorMessage());
        //         return "";
        //     }
        // }
        log.debug("执行查询：{}", nGQL);
        return "";
    }

    /**
     * 清空并重建图空间
     * 删除现有图空间并根据配置重新创建
     */
    public void clearAndRecreateSpace() {
        // TODO: 实现清空重建图空间逻辑
        // 示例实现：
        // try (Session session = pool.getSession(username, password, true)) {
        //     // 删除现有图空间
        //     session.execute("DROP SPACE IF EXISTS " + nebulaConfig.getSpace().getName());
        //
        //     // 创建新图空间
        //     String createSpace = String.format(
        //         "CREATE SPACE IF NOT EXISTS %s (partition_num=%d, replica_factor=%d, charset=%s)",
        //         nebulaConfig.getSpace().getName(),
        //         nebulaConfig.getSpace().getPartitionNum(),
        //         nebulaConfig.getSpace().getReplicaFactor(),
        //         nebulaConfig.getSpace().getCharset()
        //     );
        //     session.execute(createSpace);
        // }
        log.info("清空并重建图空间：{}", nebulaConfig.getSpace().getName());
    }
}