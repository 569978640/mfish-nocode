# PLM-Graph MQ 同步重构实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 NebulaWriteService 从 HTTP REST API (NebulaClient) 重构为使用 vesoft client 原生 Session (NebulaGraphClient)

**架构：** 复用现有的 NebulaGraphClient + NebulaSessionPool 架构，NebulaWriteService 直接使用原生 Session 执行 nGQL

**技术栈：** vesoft nebula-client, NebulaGraph, RocketMQ, Spring Boot

---

## 任务分解

### 任务 1：创建 NebulaWriteServiceTest 测试类

**文件：**
- 创建：`mf-business/mf-graph/src/test/java/cn/com/mfish/graph/sync/service/NebulaWriteServiceTest.java`
- 依赖：`NebulaWriteService.java`, `NebulaGraphClient.java`

- [ ] **步骤 1：创建测试目录结构**

```bash
mkdir -p mf-business/mf-graph/src/test/java/cn/com/mfish/graph/sync/service
```

- [ ] **步骤 2：编写 NebulaWriteServiceTest 测试类**

```java
package cn.com.mfish.graph.sync.service;

import cn.com.mfish.graph.client.NebulaGraphClient;
import cn.com.mfish.graph.model.GraphEdge;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.pool.NebulaSessionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NebulaWriteServiceTest {

    @Mock
    private NebulaGraphClient nebulaGraphClient;

    @Mock
    private NebulaSessionPool writePool;

    @InjectMocks
    private NebulaWriteService nebulaWriteService;

    private GraphSyncEvent createEvent;
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    @BeforeEach
    void setUp() {
        GraphNode node = new GraphNode();
        node.setId("test-node-001");
        node.setType("Product");
        node.setName("测试产品");
        node.setBizCode("P001");
        node.setCreateBy("admin");
        node.setCreateTime(new java.util.Date());
        nodes = Collections.singletonList(node);

        GraphEdge edge = new GraphEdge();
        edge.setId("edge-001");
        edge.setType("ContainsLink");
        edge.setFromId("node-001");
        edge.setToId("node-002");
        edge.setFromType("Product");
        edge.setToType("Part");
        edge.setLinkType("BOM");
        edge.setCreateBy("admin");
        edge.setCreateTime(new java.util.Date());
        edges = Collections.singletonList(edge);

        createEvent = new GraphSyncEvent();
        createEvent.setEventId("evt-001");
        createEvent.setEventType("CREATE");
        createEvent.setNodes(nodes);
        createEvent.setEdges(edges);
    }

    @Test
    void testUpsertNodesAndEdges_Success() {
        when(nebulaGraphClient.getWritePool()).thenReturn(writePool);
        when(writePool.executeWrite(anyString())).thenReturn(true);

        assertDoesNotThrow(() -> nebulaWriteService.upsertNodesAndEdges(createEvent));

        verify(writePool, atLeastOnce()).executeWrite(anyString());
    }

    @Test
    void testUpsertNodesAndEdges_NullEvent() {
        assertDoesNotThrow(() -> nebulaWriteService.upsertNodesAndEdges(null));
        verify(writePool, never()).executeWrite(anyString());
    }

    @Test
    void testUpsertNodesAndEdges_EmptyNodes() {
        createEvent.setNodes(Collections.emptyList());
        createEvent.setEdges(edges);

        when(nebulaGraphClient.getWritePool()).thenReturn(writePool);
        when(writePool.executeWrite(anyString())).thenReturn(true);

        assertDoesNotThrow(() -> nebulaWriteService.upsertNodesAndEdges(createEvent));
        verify(writePool, atLeastOnce()).executeWrite(anyString());
    }

    @Test
    void testDeleteNodesAndEdges_Success() {
        when(nebulaGraphClient.getWritePool()).thenReturn(writePool);
        when(writePool.executeWrite(anyString())).thenReturn(true);

        nebulaWriteService.deleteNodesAndEdges(createEvent);

        verify(writePool, atLeastOnce()).executeWrite(anyString());
    }

    @Test
    void testDeleteNodesAndEdges_NullEvent() {
        assertDoesNotThrow(() -> nebulaWriteService.deleteNodesAndEdges(null));
        verify(writePool, never()).executeWrite(anyString());
    }
}
```

- [ ] **步骤 3：运行测试验证测试类编译通过**

运行：`mvn test -pl mf-business/mf-graph -Dtest=NebulaWriteServiceTest -DfailIfNoTests=false`
预期：编译成功，测试待执行

- [ ] **步骤 4：Commit**

```bash
git add mf-business/mf-graph/src/test/java/cn/com/mfish/graph/sync/service/NebulaWriteServiceTest.java
git commit -m "test: add NebulaWriteServiceTest for refactoring"
```

---

### 任务 2：重构 NebulaWriteService 使用 NebulaGraphClient

**文件：**
- 修改：`mf-business/mf-graph/src/main/java/cn/com/mfish/graph/sync/service/NebulaWriteService.java`

- [ ] **步骤 1：阅读现有 NebulaWriteService 代码**

路径：`mf-business/mf-graph/src/main/java/cn/com/mfish/graph/sync/service/NebulaWriteService.java`

- [ ] **步骤 2：重构 NebulaWriteService 使用 NebulaGraphClient**

```java
package cn.com.mfish.graph.sync.service;

import cn.com.mfish.graph.client.NebulaGraphClient;
import cn.com.mfish.graph.model.GraphEdge;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.schema.SchemaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图数据库写入服务
 * 负责将节点和边数据写入 NebulaGraph
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
@Service
public class NebulaWriteService {

    @Autowired
    private NebulaGraphClient nebulaGraphClient;

    /**
     * 批量写入或更新节点和边
     */
    public void upsertNodesAndEdges(GraphSyncEvent event) {
        if (event == null) {
            return;
        }

        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            Map<String, List<GraphNode>> nodesByType = event.getNodes().stream()
                .collect(Collectors.groupingBy(GraphNode::getType));

            for (Map.Entry<String, List<GraphNode>> entry : nodesByType.entrySet()) {
                String tagName = entry.getKey();
                List<GraphNode> nodeList = entry.getValue();
                batchUpsertVertices(tagName, nodeList);
                log.info("批量写入节点: tagName={}, count={}", tagName, nodeList.size());
            }
        }

        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            Map<String, List<GraphEdge>> edgesByType = event.getEdges().stream()
                .collect(Collectors.groupingBy(GraphEdge::getType));

            for (Map.Entry<String, List<GraphEdge>> entry : edgesByType.entrySet()) {
                String edgeName = entry.getKey();
                List<GraphEdge> edgeList = entry.getValue();
                batchUpsertEdges(edgeName, edgeList);
                log.info("批量写入边: edgeName={}, count={}", edgeName, edgeList.size());
            }
        }
    }

    /**
     * 批量删除节点和边
     */
    public void deleteNodesAndEdges(GraphSyncEvent event) {
        if (event == null) {
            return;
        }

        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            for (GraphEdge edge : event.getEdges()) {
                deleteEdge(edge.getType(), edge.getFromId(), edge.getToId());
            }
            log.info("批量删除边: count={}", event.getEdges().size());
        }

        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            for (GraphNode node : event.getNodes()) {
                deleteVertex(node.getType(), node.getId());
            }
            log.info("批量删除节点: count={}", event.getNodes().size());
        }
    }

    /**
     * 批量 Upsert 节点（INSERT VERTEX IF NOT EXISTS）
     */
    private void batchUpsertVertices(String tagName, List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        StringBuilder ngql = new StringBuilder();
        ngql.append("INSERT VERTEX ");
        ngql.append(SchemaUtils.quote(tagName));
        ngql.append("(id, biz_code, name, description, ext_data, create_by, create_time, update_by, update_time) VALUES ");

        boolean first = true;
        for (GraphNode node : nodes) {
            if (!first) {
                ngql.append(", ");
            }
            first = false;

            ngql.append(SchemaUtils.quote(node.getId()))
                .append(":(")
                .append(SchemaUtils.quote(node.getId())).append(", ")
                .append(SchemaUtils.quote(node.getBizCode())).append(", ")
                .append(SchemaUtils.quote(node.getName())).append(", ")
                .append(node.getDescription() != null ? SchemaUtils.quote(node.getDescription()) : "NULL").append(", ")
                .append(node.getExtData() != null ? SchemaUtils.quote(node.getExtData()) : "NULL").append(", ")
                .append(SchemaUtils.quote(node.getCreateBy())).append(", ")
                .append(node.getCreateTime() != null ? "datetime('" + new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(node.getCreateTime()) + "')" : "NULL").append(", ")
                .append(node.getUpdateBy() != null ? SchemaUtils.quote(node.getUpdateBy()) : "NULL").append(", ")
                .append(node.getUpdateTime() != null ? "datetime('" + new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(node.getUpdateTime()) + "')" : "NULL")
                .append(")");
        }

        boolean success = nebulaGraphClient.getWritePool().executeWrite(ngql.toString());
        if (!success) {
            throw new RuntimeException("批量写入节点失败: " + tagName);
        }
    }

    /**
     * 批量 Upsert 边（INSERT EDGE IF NOT EXISTS）
     */
    private void batchUpsertEdges(String edgeName, List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        StringBuilder ngql = new StringBuilder();
        ngql.append("INSERT EDGE ");
        ngql.append(SchemaUtils.quote(edgeName));
        ngql.append("(id, link_type, prop_data, create_by, create_time, update_by, update_time, from_id, from_type, to_id, to_type) VALUES ");

        boolean first = true;
        for (GraphEdge edge : edges) {
            if (!first) {
                ngql.append(", ");
            }
            first = false;

            ngql.append(SchemaUtils.quote(edge.getFromId()))
                .append("->")
                .append(SchemaUtils.quote(edge.getToId()))
                .append(":(")
                .append(SchemaUtils.quote(edge.getId())).append(", ")
                .append(SchemaUtils.quote(edge.getLinkType())).append(", ")
                .append(edge.getPropData() != null ? SchemaUtils.quote(edge.getPropData()) : "NULL").append(", ")
                .append(SchemaUtils.quote(edge.getCreateBy())).append(", ")
                .append(edge.getCreateTime() != null ? "datetime('" + new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(edge.getCreateTime()) + "')" : "NULL").append(", ")
                .append(edge.getUpdateBy() != null ? SchemaUtils.quote(edge.getUpdateBy()) : "NULL").append(", ")
                .append(edge.getUpdateTime() != null ? "datetime('" + new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(edge.getUpdateTime()) + "')" : "NULL").append(", ")
                .append(SchemaUtils.quote(edge.getFromId())).append(", ")
                .append(SchemaUtils.quote(edge.getFromType())).append(", ")
                .append(SchemaUtils.quote(edge.getToId())).append(", ")
                .append(SchemaUtils.quote(edge.getToType()))
                .append(")");
        }

        boolean success = nebulaGraphClient.getWritePool().executeWrite(ngql.toString());
        if (!success) {
            throw new RuntimeException("批量写入边失败: " + edgeName);
        }
    }

    /**
     * 删除边
     */
    private void deleteEdge(String edgeName, String fromId, String toId) {
        String ngql = String.format("DELETE EDGE %s %s->%s",
            SchemaUtils.quote(edgeName),
            SchemaUtils.quote(fromId),
            SchemaUtils.quote(toId));

        boolean success = nebulaGraphClient.getWritePool().executeWrite(ngql);
        if (!success) {
            throw new RuntimeException("删除边失败: " + edgeName);
        }
    }

    /**
     * 删除节点
     */
    private void deleteVertex(String tagName, String vertexId) {
        String ngql = String.format("DELETE VERTEX %s", SchemaUtils.quote(vertexId));

        boolean success = nebulaGraphClient.getWritePool().executeWrite(ngql);
        if (!success) {
            throw new RuntimeException("删除节点失败: " + tagName);
        }
    }
}
```

- [ ] **步骤 3：运行测试验证重构正确性**

运行：`mvn test -pl mf-business/mf-graph -Dtest=NebulaWriteServiceTest`
预期：所有测试通过

- [ ] **步骤 4：Commit**

```bash
git add mf-business/mf-graph/src/main/java/cn/com/mfish/graph/sync/service/NebulaWriteService.java
git commit -m "refactor: use NebulaGraphClient instead of NebulaClient in NebulaWriteService"
```

---

### 任务 3：删除 NebulaClient.java

**文件：**
- 删除：`mf-common-graph/src/main/java/cn/com/mfish/graph/client/NebulaClient.java`

- [ ] **步骤 1：确认 NebulaClient 不再被引用**

运行：`grep -r "NebulaClient" --include="*.java" mf-common-graph mf-business/mf-graph`
预期：只有 NebulaClient.java 本身

- [ ] **步骤 2：删除 NebulaClient.java**

```bash
rm mf-common-graph/src/main/java/cn/com/mfish/graph/client/NebulaClient.java
```

- [ ] **步骤 3：验证编译通过**

运行：`mvn compile -pl mf-common-graph,mf-business/mf-graph -am`
预期：编译成功

- [ ] **步骤 4：Commit**

```bash
git rm mf-common-graph/src/main/java/cn/com/mfish/graph/client/NebulaClient.java
git commit -m "refactor: remove deprecated NebulaClient HTTP REST API implementation"
```

---

### 任务 4：为 model/node/GraphNode 和 model/edge/GraphEdge 添加 @Deprecated

**文件：**
- 修改：`mf-common-graph/src/main/java/cn/com/mfish/graph/model/node/GraphNode.java`
- 修改：`mf-common-graph/src/main/java/cn/com/mfish/graph/model/edge/GraphEdge.java`

- [ ] **步骤 1：为 model/node/GraphNode 添加 @Deprecated**

```java
package cn.com.mfish.graph.model.node;

import cn.com.mfish.common.core.entity.BaseEntity;
import lombok.Data;

@Data
@Deprecated
public class GraphNode extends BaseEntity<String> {
    private String type;
}
```

- [ ] **步骤 2：为 model/edge/GraphEdge 添加 @Deprecated**

```java
package cn.com.mfish.graph.model.edge;

import lombok.Data;
import java.util.Date;
import java.util.Map;

@Data
@Deprecated
public class GraphEdge {
    private String id;
    private String type;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String fromId;
    private String fromType;
    private String toId;
    private String toType;
    private Map<String, Object> properties;
}
```

- [ ] **步骤 3：验证编译通过**

运行：`mvn compile -pl mf-common-graph`
预期：编译成功

- [ ] **步骤 4：Commit**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/model/node/GraphNode.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/model/edge/GraphEdge.java
git commit -m "refactor: mark deprecated model classes @Deprecated"
```

---

### 任务 5：验证测试

**文件：**
- 测试：`mf-business/mf-graph/src/test/java/cn/com/mfish/graph/sync/service/NebulaWriteServiceTest.java`

- [ ] **步骤 1：运行 NebulaWriteServiceTest**

运行：`mvn test -pl mf-business/mf-graph -Dtest=NebulaWriteServiceTest`
预期：所有测试通过

- [ ] **步骤 2：验证整体编译**

运行：`mvn compile -pl mf-common-graph,mf-business/mf-graph -am`
预期：编译成功，无错误

- [ ] **步骤 3：Commit**

```bash
git add -A
git commit -m "test: verify NebulaWriteService refactoring tests pass"
```

---

## 自检清单

- [ ] NebulaWriteService 已改用 NebulaGraphClient
- [ ] NebulaClient.java 已删除
- [ ] model/node/GraphNode 和 model/edge/GraphEdge 已标记 @Deprecated
- [ ] NebulaWriteServiceTest 所有测试通过
- [ ] 整体编译成功
- [ ] 所有变更已 commit
