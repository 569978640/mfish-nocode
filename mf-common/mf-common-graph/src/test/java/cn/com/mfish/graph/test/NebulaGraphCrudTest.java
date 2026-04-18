package cn.com.mfish.graph.test;

import cn.com.mfish.graph.client.NebulaGraphClient;
import cn.com.mfish.graph.config.*;
import cn.com.mfish.graph.id.DefaultVidGenerator;
import cn.com.mfish.graph.id.VidGenerator;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.ops.EdgeOperations;
import cn.com.mfish.graph.ops.NodeOperations;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * NebulaGraph CRUD 集成测试
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class NebulaGraphCrudTest {
    private NebulaGraphClient client;
    private NodeOperations nodeOperations;
    private EdgeOperations edgeOperations;
    private VidGenerator vidGenerator;

    @Before
    public void setUp() {
        NebulaGraphProperties properties = new NebulaGraphProperties();
        properties.setAddresses(List.of("127.0.0.1:9669"));
        properties.setUsername("root");
        properties.setPassword("nebula");
        properties.setSpace("plm_graph");

        NebulaSessionPoolConfig poolConfig = new NebulaSessionPoolConfig();
        poolConfig.setMinIdle(5);
        poolConfig.setMaxPoolSize(20);

        RetryConfig retryConfig = new RetryConfig();
        retryConfig.setMaxRetries(3);

        CircuitBreakerConfig circuitBreakerConfig = new CircuitBreakerConfig();

        LoadBalanceConfig loadBalanceConfig = new LoadBalanceConfig();
        NebulaQuotaConfig quotaConfig = new NebulaQuotaConfig();

        NebulaConfigManager configManager = NebulaConfigManager.getInstance();
        configManager.init(properties, poolConfig, retryConfig, circuitBreakerConfig,
                          loadBalanceConfig, quotaConfig, new NebulaHAConfig());

        vidGenerator = new DefaultVidGenerator();
    }

    @Test
    public void testVidGeneration() {
        String vid = vidGenerator.generate("Product", "P001");
        log.info("生成 VID: {}", vid);

        String bizId = vidGenerator.extractBizId(vid);
        log.info("提取业务ID: {}", bizId);

        String bizType = vidGenerator.extractBizType(vid);
        log.info("提取业务类型: {}", bizType);
    }

    @Test
    public void testNodeCrud() {
        GraphNode node = new GraphNode();
        node.setType("Product");
        node.setBizCode("P001");
        node.setName("测试产品");
        node.setDescription("这是一个测试产品");
        node.setCreateBy("admin");
        node.setUpdateBy("admin");

        log.info("测试节点 CRUD 操作");
    }

    @Test
    public void testQueryBuilder() {
        cn.com.mfish.graph.query.QueryBuilder builder = cn.com.mfish.graph.query.QueryBuilder.match()
            .node("n", "Product")
            .edge("r", "HAS_CHILD")
            .node("c", "PartMaster")
            .where("n.`biz_code`", cn.com.mfish.graph.query.QueryCondition.Operator.EQ, "P001");

        String ngql = builder.build();
        log.info("构建查询: {}", ngql);
    }

    @Test
    public void testBatchOperations() {
        List<GraphNode> nodes = Arrays.asList(
            createNode("P001", "产品1"),
            createNode("P002", "产品2"),
            createNode("P003", "产品3")
        );

        log.info("批量插入节点数量: {}", nodes.size());
    }

    private GraphNode createNode(String bizCode, String name) {
        GraphNode node = new GraphNode();
        node.setType("Product");
        node.setBizCode(bizCode);
        node.setName(name);
        node.setCreateBy("admin");
        return node;
    }
}