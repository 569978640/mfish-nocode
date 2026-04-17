# MFish PLM 多数据库架构完整改造实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 按照架构设计文档，完成 PLM 模块的 PostgreSQL + NebulaGraph 多数据库架构改造

**架构：** PLM 模块数据读写在 PostgreSQL，变更通过 RocketMQ 异步同步到 NebulaGraph，跨模块调用通过 Feign 实现

**技术栈：** Spring Boot、MyBatis-Plus、PostgreSQL、NebulaGraph、RocketMQ、Feign

---

## 现状分析

### 已完成部分 ✅
1. **配置文件**：mf-plm-dev.yml（PostgreSQL）、mf-graph-dev.yml（多数据源+NebulaGraph+RocketMQ）
2. **NebulaGraph 客户端**：NebulaClient、NebulaConfig、RocketMQConfig
3. **图同步服务**：GraphSyncConsumer、GraphSyncService、GraphSyncEvent
4. **多数据源注解**：@Master、@Slave、BatchSqlInjector、MybatisInterceptor

### 待完成部分 🔄
1. **mf-plm 模块**：添加 RocketMQ Producer 依赖和配置
2. **mf-plm 模块**：添加 Feign 接口依赖（mf-oauth-api、mf-sys-api）
3. **mf-plm 模块**：创建消息发送服务 GraphSyncProducer
4. **PLM Service 层**：在保存/更新/删除操作后发送消息

---

## 文件结构

### 需要修改的文件

| 文件 | 职责 |
|-----|------|
| `mf-business/mf-plm/pom.xml` | 添加 RocketMQ Producer 和 Feign 依赖 |
| `mf-start/mf-start-plm/src/main/resources/mf-plm-dev.yml` | 添加 RocketMQ Producer 配置 |
| `mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/config/RocketMQConfig.java` | RocketMQ Producer 配置类 |

### 需要创建的文件

| 文件 | 职责 |
|-----|------|
| `mf-business/mf-plm/src/main/java/cn/com/mfish/plm/event/GraphSyncEvent.java` | 图同步事件模型 |
| `mf-business/mf-plm/src/main/java/cn/com/mfish/plm/producer/PlmGraphSyncProducer.java` | RocketMQ 消息生产者 |

---

## 任务清单

### 任务 1：修改 mf-plm 模块的 pom.xml 添加依赖

**文件：**
- 修改：`mf-business/mf-plm/pom.xml`

- [ ] **步骤 1：添加 RocketMQ Producer 依赖**

在 `</dependencies>` 前添加：

```xml
        <!-- RocketMQ Producer 依赖 -->
        <dependency>
            <groupId>org.apache.rocketmq</groupId>
            <artifactId>rocketmq-spring-boot-starter</artifactId>
            <version>${rocketmq.version}</version>
        </dependency>
        <!-- Feign 接口依赖 -->
        <dependency>
            <groupId>cn.com.mfish</groupId>
            <artifactId>mf-oauth-api</artifactId>
            <version>${mfish.version}</version>
        </dependency>
        <dependency>
            <groupId>cn.com.mfish</groupId>
            <artifactId>mf-sys-api</artifactId>
            <version>${mfish.version}</version>
        </dependency>
```

---

### 任务 2：创建图同步事件模型

**文件：**
- 创建：`mf-business/mf-plm/src/main/java/cn/com/mfish/plm/event/GraphSyncEvent.java`

- [ ] **步骤 1：创建 GraphSyncEvent 类**

```java
package cn.com.mfish.plm.event;

import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.graph.model.edge.GraphEdge;
import lombok.Data;
import java.util.List;

/**
 * PLM图同步事件
 * 用于发送PLM数据变更到RocketMQ，触发图数据库同步
 *
 * @author mfish
 * @date 2026-04-17
 */
@Data
public class GraphSyncEvent {
    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 事件类型：CREATE/UPDATE/DELETE
     */
    private String eventType;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 事件来源模块
     */
    private String source;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 节点列表
     */
    private List<GraphNode> nodes;

    /**
     * 边列表
     */
    private List<GraphEdge> edges;
}
```

---

### 任务 3：创建 RocketMQ 消息生产者

**文件：**
- 创建：`mf-business/mf-plm/src/main/java/cn/com/mfish/plm/producer/PlmGraphSyncProducer.java`

- [ ] **步骤 1：创建 PlmGraphSyncProducer 类**

```java
package cn.com.mfish.plm.producer;

import cn.com.mfish.plm.event.GraphSyncEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * PLM图同步消息生产者
 * 负责将PLM数据变更事件发送到RocketMQ
 *
 * @author mfish
 * @date 2026-04-17
 */
@Slf4j
@Component
public class PlmGraphSyncProducer {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.producer.topic:plm-graph-sync}")
    private String topic;

    /**
     * 发送图同步事件
     *
     * @param event 图同步事件
     */
    public void sendGraphSyncEvent(GraphSyncEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(System.currentTimeMillis());
        }
        if (event.getSource() == null) {
            event.setSource("mf-plm");
        }
        try {
            rocketMQTemplate.asyncSend(topic, event, new org.apache.rocketmq.spring.core.SendCallback() {
                @Override
                public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                    log.info("图同步事件发送成功, eventId={}, result={}", event.getEventId(), sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("图同步事件发送失败, eventId={}", event.getEventId(), e);
                }
            });
        } catch (Exception e) {
            log.error("发送图同步事件异常, eventId={}", event.getEventId(), e);
        }
    }
}
```

---

### 任务 4：修改 mf-plm-dev.yml 添加 RocketMQ Producer 配置

**文件：**
- 修改：`mf-start/mf-start-plm/src/main/resources/mf-plm-dev.yml`

- [ ] **步骤 1：添加 RocketMQ Producer 配置**

在文件末尾添加：

```yaml
rocketmq:
  producer:
    nameServer: 192.168.111.103:9876
    topic: plm-graph-sync
    group: plm-producer-group
```

---

### 任务 5：创建 GraphNode 和 GraphEdge 工具类

**文件：**
- 创建：`mf-business/mf-plm/src/main/java/cn/com/mfish/plm/utils/GraphUtils.java`

- [ ] **步骤 1：创建 GraphUtils 工具类**

```java
package cn.com.mfish.plm.utils;

import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.graph.model.edge.GraphEdge;
import cn.com.mfish.plm.base.bean.container.entity.Product;
import cn.com.mfish.plm.base.bean.container.entity.ContainsLink;
import cn.com.mfish.plm.base.bean.folder.entity.Folder;
import cn.com.mfish.plm.base.bean.part.Part;
import cn.com.mfish.plm.base.bean.part.PartMaster;
import cn.com.mfish.plm.base.bean.part.PartVersionLink;
import cn.com.mfish.plm.base.bean.doc.entity.Document;
import cn.com.mfish.plm.base.bean.doc.entity.DocumentMaster;
import cn.com.mfish.plm.base.bean.doc.entity.DocVersionLink;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 图数据转换工具类
 * 将PLM实体转换为图节点和边
 *
 * @author mfish
 * @date 2026-04-17
 */
public class GraphUtils {

    /**
     * 将Product转换为GraphNode
     */
    public static GraphNode toGraphNode(Product product) {
        if (product == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(product.getId());
        node.setType("Product");
        node.setCreateBy(product.getCreateBy());
        node.setCreateTime(product.getCreateTime());
        node.setUpdateBy(product.getUpdateBy());
        node.setUpdateTime(product.getUpdateTime());
        return node;
    }

    /**
     * 将Folder转换为GraphNode
     */
    public static GraphNode toGraphNode(Folder folder) {
        if (folder == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(folder.getId());
        node.setType("Folder");
        node.setCreateBy(folder.getCreateBy());
        node.setCreateTime(folder.getCreateTime());
        node.setUpdateBy(folder.getUpdateBy());
        node.setUpdateTime(folder.getUpdateTime());
        return node;
    }

    /**
     * 将Part转换为GraphNode
     */
    public static GraphNode toGraphNode(Part part) {
        if (part == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(part.getId());
        node.setType("Part");
        node.setCreateBy(part.getCreateBy());
        node.setCreateTime(part.getCreateTime());
        node.setUpdateBy(part.getUpdateBy());
        node.setUpdateTime(part.getUpdateTime());
        return node;
    }

    /**
     * 将PartMaster转换为GraphNode
     */
    public static GraphNode toGraphNode(PartMaster partMaster) {
        if (partMaster == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(partMaster.getId());
        node.setType("PartMaster");
        node.setCreateBy(partMaster.getCreateBy());
        node.setCreateTime(partMaster.getCreateTime());
        node.setUpdateBy(partMaster.getUpdateBy());
        node.setUpdateTime(partMaster.getUpdateTime());
        return node;
    }

    /**
     * 将Document转换为GraphNode
     */
    public static GraphNode toGraphNode(Document document) {
        if (document == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(document.getId());
        node.setType("Document");
        node.setCreateBy(document.getCreateBy());
        node.setCreateTime(document.getCreateTime());
        node.setUpdateBy(document.getUpdateBy());
        node.setUpdateTime(document.getUpdateTime());
        return node;
    }

    /**
     * 将DocumentMaster转换为GraphNode
     */
    public static GraphNode toGraphNode(DocumentMaster docMaster) {
        if (docMaster == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(docMaster.getId());
        node.setType("DocumentMaster");
        node.setCreateBy(docMaster.getCreateBy());
        node.setCreateTime(docMaster.getCreateTime());
        node.setUpdateBy(docMaster.getUpdateBy());
        node.setUpdateTime(docMaster.getUpdateTime());
        return node;
    }

    /**
     * 将ContainsLink转换为GraphEdge
     */
    public static GraphEdge toGraphEdge(ContainsLink link) {
        if (link == null) {
            return null;
        }
        GraphEdge edge = new GraphEdge();
        edge.setId(link.getId());
        edge.setType("CONTAINS");
        edge.setFromId(link.getParentId());
        edge.setToId(link.getChildId());
        edge.setCreateBy(link.getCreateBy());
        edge.setCreateTime(link.getCreateTime());
        return edge;
    }

    /**
     * 将PartVersionLink转换为GraphEdge
     */
    public static GraphEdge toGraphEdge(PartVersionLink link) {
        if (link == null) {
            return null;
        }
        GraphEdge edge = new GraphEdge();
        edge.setId(link.getId());
        edge.setType("VERSION_OF");
        edge.setFromId(link.getMasterId());
        edge.setToId(link.getVersionId());
        edge.setCreateBy(link.getCreateBy());
        edge.setCreateTime(link.getCreateTime());
        return edge;
    }

    /**
     * 将DocVersionLink转换为GraphEdge
     */
    public static GraphEdge toGraphEdge(DocVersionLink link) {
        if (link == null) {
            return null;
        }
        GraphEdge edge = new GraphEdge();
        edge.setId(link.getId());
        edge.setType("VERSION_OF");
        edge.setFromId(link.getMasterId());
        edge.setToId(link.getVersionId());
        edge.setCreateBy(link.getCreateBy());
        edge.setCreateTime(link.getCreateTime());
        return edge;
    }

    /**
     * 批量转换节点列表
     */
    public static List<GraphNode> toNodeList(List<?> items, String type) {
        List<GraphNode> nodes = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return nodes;
        }
        for (Object item : items) {
            GraphNode node = null;
            if (item instanceof Product) {
                node = toGraphNode((Product) item);
            } else if (item instanceof Folder) {
                node = toGraphNode((Folder) item);
            } else if (item instanceof Part) {
                node = toGraphNode((Part) item);
            } else if (item instanceof PartMaster) {
                node = toGraphNode((PartMaster) item);
            } else if (item instanceof Document) {
                node = toGraphNode((Document) item);
            } else if (item instanceof DocumentMaster) {
                node = toGraphNode((DocumentMaster) item);
            }
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }
}
```

---

### 任务 6：在 PLM Service 层集成消息发送

**文件：**
- 修改：`mf-business/mf-plm/src/main/java/cn/com/mfish/plm/base/service/impl/ProductServiceImpl.java`（示例）

- [ ] **步骤 1：在 ProductServiceImpl 中注入 PlmGraphSyncProducer**

在类中添加：

```java
    @Autowired(required = false)
    private PlmGraphSyncProducer graphSyncProducer;
```

- [ ] **步骤 2：修改 save 方法添加消息发送**

```java
    @Override
    public Product save(Product product) {
        // 原有保存逻辑
        boolean result = saveOrUpdate(product);
        if (result && graphSyncProducer != null) {
            // 发送图同步事件
            GraphSyncEvent event = new GraphSyncEvent();
            event.setEventType("CREATE");
            event.setOperator(product.getCreateBy());
            event.setNodes(Collections.singletonList(GraphUtils.toGraphNode(product)));
            graphSyncProducer.sendGraphSyncEvent(event);
        }
        return product;
    }
```

- [ ] **步骤 3：修改 removeById 方法添加消息发送**

```java
    @Override
    public boolean removeById(Serializable id) {
        // 查询要删除的节点
        Product product = getById(id);
        boolean result = super.removeById(id);
        if (result && graphSyncProducer != null && product != null) {
            // 发送图同步事件
            GraphSyncEvent event = new GraphSyncEvent();
            event.setEventType("DELETE");
            event.setOperator(product.getUpdateBy());
            event.setNodes(Collections.singletonList(GraphUtils.toGraphNode(product)));
            graphSyncProducer.sendGraphSyncEvent(event);
        }
        return result;
    }
```

---

### 任务 7：检查并完善 RocketMQConfig

**文件：**
- 检查：`mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/config/RocketMQConfig.java`

- [ ] **步骤 1：检查 RocketMQConfig 是否包含 Producer 配置**

如果不存在或配置不完整，添加以下配置：

```java
package cn.com.mfish.graph.config;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ配置类
 * 配置Producer用于发送消息
 *
 * @author mfish
 * @date 2026-04-16
 */
@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.producer.nameServer:}")
    private String nameServer;

    @Value("${rocketmq.producer.group:}")
    private String group;

    @Bean
    public RocketMQTemplate rocketMQTemplate() {
        RocketMQTemplate template = new RocketMQTemplate();
        return template;
    }
}
```

---

### 任务 8：验证编译和配置

- [ ] **步骤 1：执行 Maven 编译**

在项目根目录执行：
```bash
cd e:\windchill\idea_workspace\mfish\my\mfish-nocode
mvn clean compile -pl mf-business/mf-plm -am -DskipTests
```

预期：编译成功，无错误

- [ ] **步骤 2：验证配置文件**

检查 `mf-plm-dev.yml` 中是否包含：
- PostgreSQL 数据源配置 ✅
- RocketMQ Producer 配置 ✅

- [ ] **步骤 3：检查依赖传递**

确认 `mf-start-plm` 启动时能正确加载：
- RocketMQ Starter 依赖
- PostgreSQL Driver
- Feign Client 依赖

---

### 任务 9：Neo4j Schema 初始化（如需要）

- [ ] **步骤 1：检查 NebulaGraph Schema**

确认 NebulaGraph 中是否已创建 Space 和 Tag/EdgeType。如果没有，创建初始化脚本：

```sql
-- 创建 Space
CREATE SPACE IF NOT EXISTS plm_graph
(partition_num = 100,
replica_factor = 1,
charset = utf8);

-- 使用 Space
USE plm_graph;

-- 创建 Tag
CREATE TAG IF NOT EXISTS Product(id VARCHAR(64) NOT NULL, name VARCHAR(255), code VARCHAR(100), create_time TIMESTAMP);
CREATE TAG IF NOT EXISTS Part(id VARCHAR(64) NOT NULL, name VARCHAR(255), code VARCHAR(100), create_time TIMESTAMP);
CREATE TAG IF NOT EXISTS PartMaster(id VARCHAR(64) NOT NULL, name VARCHAR(255), code VARCHAR(100), create_time TIMESTAMP);
CREATE TAG IF NOT EXISTS Document(id VARCHAR(64) NOT NULL, name VARCHAR(255), code VARCHAR(100), create_time TIMESTAMP);
CREATE TAG IF NOT EXISTS DocumentMaster(id VARCHAR(64) NOT NULL, name VARCHAR(255), code VARCHAR(100), create_time TIMESTAMP);
CREATE TAG IF NOT EXISTS Folder(id VARCHAR(64) NOT NULL, name VARCHAR(255), create_time TIMESTAMP);

-- 创建 EdgeType
CREATE EDGE IF NOT EXISTS CONTAINS(id VARCHAR(64) NOT NULL, create_time TIMESTAMP);
CREATE EDGE IF NOT EXISTS VERSION_OF(id VARCHAR(64) NOT NULL, version VARCHAR(50), create_time TIMESTAMP);
```

---

## 实施顺序

1. **任务 1**：修改 pom.xml 添加依赖
2. **任务 2**：创建 GraphSyncEvent 事件模型
3. **任务 3**：创建 PlmGraphSyncProducer 消息生产者
4. **任务 4**：修改 mf-plm-dev.yml 添加 RocketMQ 配置
5. **任务 5**：创建 GraphUtils 工具类
6. **任务 6**：在 Service 层集成消息发送（ProductServiceImpl 等）
7. **任务 7**：检查 RocketMQConfig
8. **任务 8**：验证编译和配置
9. **任务 9**：NebulaGraph Schema 初始化

---

## 规格覆盖检查

| 架构设计文档章节 | 对应任务 |
|-----------------|---------|
| 4.2 PLM模块配置 | 任务 4 |
| 5.1 公共模块职责 | 任务 2, 3, 5 |
| 6. RocketMQ消息设计 | 任务 2, 3, 6 |
| 8. Feign接口设计 | 任务 1 |
| 9. 改造步骤-阶段一 | 任务 7 |
| 9. 改造步骤-阶段二 | 任务 1, 4, 6 |
| 9. 改造步骤-阶段三 | mf-graph 已完成 |

---

## 注意事项

1. **消息发送时机**：所有消息发送必须在事务提交成功后进行
2. **降级策略**：如果 RocketMQ 不可用，Service 层应该捕获异常不影响主业务
3. **消息格式**：GraphSyncEvent 的 nodes 和 edges 不能同时为空
4. **事务一致性**：PLM 保存业务数据和发送消息不在同一个事务中，通过 RocketMQ 的可靠性保证最终一致

---

**计划完成时间**：2026-04-17
**文档版本**：mf-2.3.1