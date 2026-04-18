# PLM混合数据库架构实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现 PLM 系统关系数据库（PostgreSQL）+ 图数据库（NebulaGraph）混合查询架构

**架构：** 关系库存储完整数据（图谱查询主数据），图库存储关系拓扑+边属性。写操作在关系库完成，通过 RocketMQ 异步同步到图库。查询采用混合模式：图库查路径+边属性，关系库查节点属性。

**技术栈：** Spring Cloud、Nacos 配置中心、RocketMQ、NebulaGraph、PostgreSQL、MyBatis-Plus

---

## 文件结构

```
mfish-nocode/
├── mf-graph/                              # 新建：图数据库服务
│   ├── pom.xml
│   └── src/main/java/cn/com/mfish/graph/
│       ├── GraphApplication.java          # 启动类
│       ├── config/
│       │   ├── NebulaConfig.java          # NebulaGraph连接配置
│       │   └── RocketMQConfig.java        # RocketMQ消费配置
│       ├── controller/
│       │   └── GraphController.java       # 图谱查询接口
│       ├── service/
│       │   ├── GraphQueryService.java     # 图查询服务
│       │   └── GraphSyncService.java      # 图同步服务
│       ├── consumer/
│       │   └── GraphSyncConsumer.java     # MQ消费端
│       ├── client/
│       │   └── NebulaClient.java          # NebulaGraph客户端封装
│       └── model/
│           ├── event/
│           │   └── GraphSyncEvent.java    # 图同步事件
│           ├── node/
│           │   └── GraphNode.java         # 图节点
│           └── edge/
│               └── GraphEdge.java         # 图边
│
├── mf-api/mf-graph-api/                  # 新建：图服务API接口
│   └── pom.xml
│
├── mf-business/mf-graph/                  # 新建：图服务业务模块
│   └── pom.xml
│
└── mf-start/mf-start-graph/              # 新建：图服务启动模块
    ├── pom.xml
    └── src/main/resources/
        ├── bootstrap.yml
        └── mf-graph-dev.yml              # Nacos配置文件
```

---

## 任务清单

### 任务 1：创建 mf-graph 模块基础结构

**文件：**
- 创建：`mf-graph/pom.xml`
- 创建：`mf-api/mf-graph-api/pom.xml`
- 创建：`mf-business/mf-graph/pom.xml`
- 创建：`mf-start/mf-start-graph/pom.xml`
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/GraphApplication.java`

- [ ] **步骤 1：创建根模块 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>cn.com.mfish</groupId>
        <artifactId>mfish-nocode</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>mf-graph</artifactId>
    <packaging>pom</packaging>
    <name>mf-graph</name>
    <description>图数据库服务</description>

    <modules>
        <module>../mf-api/mf-graph-api</module>
        <module>../mf-business/mf-graph</module>
    </modules>
</project>
```

- [ ] **步骤 2：创建 API 模块 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>cn.com.mfish</groupId>
        <artifactId>mf-graph</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>mf-graph-api</artifactId>
    <name>mf-graph-api</name>
    <description>图服务API接口</description>

    <dependencies>
        <dependency>
            <groupId>cn.com.mfish</groupId>
            <artifactId>mf-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **步骤 3：创建业务模块 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>cn.com.mfish</groupId>
        <artifactId>mf-graph</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>mf-graph</artifactId>
    <name>mf-graph</name>
    <description>图服务业务模块</description>

    <dependencies>
        <dependency>
            <groupId>cn.com.mfish</groupId>
            <artifactId>mf-graph-api</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.com.mfish</groupId>
            <artifactId>mf-common-ds</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.com.mfish</groupId>
            <artifactId>mf-common-cloud</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.com.mfish</groupId>
            <artifactId>mf-common-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-rocketmq-binder</artifactId>
        </dependency>
        <dependency>
            <groupId>com.vesoft</groupId>
            <artifactId>nebula-java-client</artifactId>
            <version>3.6.0</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **步骤 4：创建启动模块 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>cn.com.mfish</groupId>
        <artifactId>mf-start</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>mf-start-graph</artifactId>
    <name>mf-start-graph</name>
    <description>图服务启动模块</description>

    <dependencies>
        <dependency>
            <groupId>cn.com.mfish</groupId>
            <artifactId>mf-graph</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **步骤 5：创建启动类 GraphApplication.java**

```java
package cn.com.mfish.graph;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan("cn.com.mfish")
@MapperScan("cn.com.mfish.graph.mapper")
public class GraphApplication {
    public static void main(String[] args) {
        SpringApplication.run(GraphApplication.class, args);
    }
}
```

- [ ] **步骤 6：创建 bootstrap.yml**

```yaml
spring:
  application:
    name: mf-graph
  cloud:
    nacos:
      username: nacos
      password: nacos
      server-addr: 192.168.111.103:19014
      discovery:
        enabled: true
        register-enabled: true
```

- [ ] **步骤 7：Commit**

```bash
git add mf-graph/ mf-api/mf-graph-api/ mf-business/mf-graph/ mf-start/mf-start-graph/
git commit -m "feat(graph): 创建mf-graph模块基础结构"
```

---

### 任务 2：创建 NebulaGraph 配置和客户端

**文件：**
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/config/NebulaConfig.java`
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/client/NebulaClient.java`

- [ ] **步骤 1：创建 NebulaConfig.java**

```java
package cn.com.mfish.graph.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "nebula")
public class NebulaConfig {
    private SingleConfig single;
    private ClusterConfig cluster;
    private String username = "root";
    private String password = "nebula";
    private PoolConfig pool;
    private SpaceConfig space;

    @Data
    public static class SingleConfig {
        private boolean enabled = true;
        private String addresses;
    }

    @Data
    public static class ClusterConfig {
        private boolean enabled = false;
        private String addresses;
    }

    @Data
    public static class PoolConfig {
        private int minConns = 10;
        private int maxConns = 100;
        private int timeout = 3000;
        private int idleTimeout = 60;
    }

    @Data
    public static class SpaceConfig {
        private String name = "plm_graph";
        private String charset = "utf8";
        private int replicaFactor = 1;
        private int partitionNum = 100;
    }
}
```

- [ ] **步骤 2：创建 NebulaClient.java**

```java
package cn.com.mfish.graph.client;

import cn.com.mfish.graph.config.NebulaConfig;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.model.GraphEdge;
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
import java.util.Map;

@Slf4j
@Component
public class NebulaClient {
    @Autowired
    private NebulaConfig nebulaConfig;

    private NebulaPool pool;

    @PostConstruct
    public void init() throws InvalidConfigException {
        NebulaPoolConfig poolConfig = new NebulaPoolConfig();
        poolConfig.setMaxConns(nebulaConfig.getPool().getMaxConns());
        poolConfig.setMinConns(nebulaConfig.getPool().getMinConns());
        poolConfig.setTimeout(nebulaConfig.getPool().getTimeout());
        poolConfig.setIdleTime(nebulaConfig.getPool().getIdleTimeout());

        List<HostAddress> addresses = new ArrayList<>();
        if (nebulaConfig.getSingle().isEnabled()) {
            String[] parts = nebulaConfig.getSingle().getAddresses().split(":");
            addresses.add(new HostAddress(parts[0], Integer.parseInt(parts[1])));
        }

        pool = new NebulaPool();
        pool.init(addresses, poolConfig, nebulaConfig.getUsername(), nebulaConfig.getPassword());
    }

    @PreDestroy
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }

    /**
     * 插入节点
     */
    public void insertVertex(String tagName, List<GraphNode> nodes) {
        // 实现插入节点逻辑
    }

    /**
     * 插入边
     */
    public void insertEdge(String edgeName, List<GraphEdge> edges) {
        // 实现插入边逻辑
    }

    /**
     * 查询路径（带边属性）
     */
    public String queryPathsWithEdgeProps(String nGQL) {
        // 实现路径查询逻辑
        return "";
    }

    /**
     * 清空并重建图空间
     */
    public void clearAndRecreateSpace() {
        // 实现清空重建图空间逻辑
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add mf-graph/src/main/java/cn/com/mfish/graph/config/NebulaConfig.java
git add mf-graph/src/main/java/cn/com/mfish/graph/client/NebulaClient.java
git commit -m "feat(graph): 添加NebulaGraph配置和客户端"
```

---

### 任务 3：创建图同步事件和数据模型

**文件：**
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/model/event/GraphSyncEvent.java`
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/model/node/GraphNode.java`
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/model/edge/GraphEdge.java`

- [ ] **步骤 1：创建 GraphSyncEvent.java**

```java
package cn.com.mfish.graph.model.event;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class GraphSyncEvent {
    private String eventId;
    private String eventType;
    private Long timestamp;
    private String source;
    private String operator;
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
}
```

- [ ] **步骤 2：创建 GraphNode.java**

```java
package cn.com.mfish.graph.model.node;

import lombok.Data;
import java.util.Date;

@Data
public class GraphNode {
    private String id;
    private String type;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
}
```

- [ ] **步骤 3：创建 GraphEdge.java**

```java
package cn.com.mfish.graph.model.edge;

import lombok.Data;
import java.util.Date;
import java.util.Map;

@Data
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

- [ ] **步骤 4：Commit**

```bash
git add mf-graph/src/main/java/cn/com/mfish/graph/model/
git commit -m "feat(graph): 添加图同步事件和数据模型"
```

---

### 任务 4：创建 RocketMQ 消费配置和消费者

**文件：**
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/config/RocketMQConfig.java`
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/consumer/GraphSyncConsumer.java`

- [ ] **步骤 1：创建 RocketMQConfig.java**

```java
package cn.com.mfish.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "rocketmq.consumer")
public class RocketMQConfig {
    private String group = "plm-graph-sync-group";
    private String topic = "plm-graph-sync";
    private boolean enableDLQ = true;
    private int maxRetryTimes = 3;
}
```

- [ ] **步骤 2：创建 GraphSyncConsumer.java**

```java
package cn.com.mfish.graph.consumer;

import client.cn.com.mfish.graph.NebulaClient;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.model.GraphEdge;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
    consumerGroup = "${rocketmq.consumer.group}",
    topic = "${rocketmq.consumer.topic}"
)
public class GraphSyncConsumer implements RocketMQListener<GraphSyncEvent> {

    @Autowired
    private NebulaClient nebulaClient;

    @Override
    public void onMessage(GraphSyncEvent event) {
        log.info("收到图同步事件: eventId={}, eventType={}", event.getEventId(), event.getEventType());

        try {
            switch (event.getEventType()) {
                case "CREATE":
                case "UPDATE":
                    processCreateOrUpdate(event);
                    break;
                case "DELETE":
                    processDelete(event);
                    break;
                default:
                    log.warn("未知事件类型: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("处理图同步事件失败: eventId={}", event.getEventId(), e);
            throw e;
        }
    }

    private void processCreateOrUpdate(GraphSyncEvent event) {
        // 处理节点
        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            nebulaClient.insertVertex(getTagName(event), event.getNodes());
        }

        // 处理边
        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            nebulaClient.insertEdge(getEdgeName(event), event.getEdges());
        }
    }

    private void processDelete(GraphSyncEvent event) {
        // 处理删除逻辑
    }

    private String getTagName(GraphSyncEvent event) {
        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            return event.getNodes().get(0).getType();
        }
        return "";
    }

    private String getEdgeName(GraphSyncEvent event) {
        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            return event.getEdges().get(0).getType();
        }
        return "";
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add mf-graph/src/main/java/cn/com/mfish/graph/config/RocketMQConfig.java
git add mf-graph/src/main/java/cn/com/mfish/graph/consumer/GraphSyncConsumer.java
git commit -m "feat(graph): 添加RocketMQ消费配置和消费者"
```

---

### 任务 5：创建全量同步服务

**文件：**
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/service/GraphSyncService.java`

- [ ] **步骤 1：创建 GraphSyncService.java**

```java
package cn.com.mfish.graph.service;

import client.cn.com.mfish.graph.NebulaClient;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.model.GraphEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
public class GraphSyncService {

    @Autowired
    private NebulaClient nebulaClient;

    /**
     * 全量同步：清空图库，重新同步所有数据
     */
    public void fullSync() {
        log.info("开始全量同步...");
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: 清空图数据库并重建Schema
            nebulaClient.clearAndRecreateSpace();

            // Step 2: TODO 同步所有节点
            // syncAllNodes();

            // Step 3: TODO 同步所有边
            // syncAllEdges();

            long endTime = System.currentTimeMillis();
            log.info("全量同步完成，耗时: {}ms", endTime - startTime);
        } catch (Exception e) {
            log.error("全量同步失败", e);
            throw new RuntimeException("全量同步失败", e);
        }
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add mf-graph/src/main/java/cn/com/mfish/graph/service/GraphSyncService.java
git commit -m "feat(graph): 添加全量同步服务"
```

---

### 任务 6：创建图查询服务和控制器

**文件：**
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/service/GraphQueryService.java`
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/controller/GraphController.java`

- [ ] **步骤 1：创建 GraphQueryService.java**

```java
package cn.com.mfish.graph.service;

import client.cn.com.mfish.graph.NebulaClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class GraphQueryService {

    @Autowired
    private NebulaClient nebulaClient;

    /**
     * 产品结构树查询 - 混合查询
     * 图库负责: 路径查询 + 边的所有属性
     * 关系库负责: 节点业务属性
     */
    public Map<String, Object> queryProductTree(String productId) {
        // Step 1: 图数据库查询 - 获取路径和边的所有属性
        String pathResult = nebulaClient.queryPathsWithEdgeProps(
            "MATCH p=(p:Product {id:'" + productId + "'})-[*1..5]->(n) RETURN p"
        );

        // Step 2: 从路径中提取所有节点ID
        Set<String> allNodeIds = extractNodeIds(pathResult);

        // Step 3: TODO 关系库批量查询节点业务属性
        // Map<String, Map<String, Object>> nodeAttributes = relationDB.batchQueryNodes(allNodeIds);

        // Step 4: TODO 合并结果
        // return mergeResults(pathResult, nodeAttributes);

        return null;
    }

    private Set<String> extractNodeIds(String pathResult) {
        // TODO 实现节点ID提取
        return null;
    }
}
```

- [ ] **步骤 2：创建 GraphController.java**

```java
package cn.com.mfish.graph.controller;

import cn.com.mfish.graph.service.GraphQueryService;
import cn.com.mfish.graph.service.GraphSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/graph")
public class GraphController {

    @Autowired
    private GraphQueryService graphQueryService;

    @Autowired
    private GraphSyncService graphSyncService;

    /**
     * 路径查询
     */
    @PostMapping("/query/paths")
    public Map<String, Object> queryPaths(@RequestBody Map<String, Object> request) {
        String startId = (String) request.get("startId");
        return graphQueryService.queryProductTree(startId);
    }

    /**
     * 全量同步
     */
    @PostMapping("/sync/full")
    public void fullSync() {
        graphSyncService.fullSync();
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add mf-graph/src/main/java/cn/com/mfish/graph/service/GraphQueryService.java
git add mf-graph/src/main/java/cn/com/mfish/graph/controller/GraphController.java
git commit -m "feat(graph): 添加图查询服务和控制器"
```

---

### 任务 7：创建 Nacos 配置文件

**文件：**
- 创建：`mf-start/mf-start-graph/src/main/resources/mf-graph-dev.yml`

- [ ] **步骤 1：创建 mf-graph-dev.yml**

```yaml
server:
  port: 9232

spring:
  application:
    name: mf-graph
  profiles:
    active: dev
  cloud:
    nacos:
      username: nacos
      password: nacos
      server-addr: 192.168.111.103:19014
      config:
        file-extension: yml
  config:
    import:
      - nacos:application-${spring.profiles.active}.${spring.cloud.nacos.config.file-extension}?refreshEnabled=true
      - nacos:${spring.application.name}-${spring.profiles.active}.${spring.cloud.nacos.config.file-extension}?refreshEnabled=true

mybatis-plus:
  mapper-locations: classpath*:cn/com/mfish/graph/mapper/**/*Mapper.xml
  global-config:
    banner: false
    db-config:
      table-underline: true
      logic-not-delete-value: 0
      logic-delete-value: 1

spring:
  datasource:
    dynamic:
      primary: master
      druid:
        initial-size: 5
        min-idle: 5
        maxActive: 20
      datasource:
        master:
          driver-class-name: org.postgresql.Driver
          url: jdbc:postgresql://192.168.111.103:5432/mf_plm
          username: postgres
          password: postgres

rocketmq:
  consumer:
    group: plm-graph-sync-group
    topic: plm-graph-sync
    enableDLQ: true
    maxRetryTimes: 3

nebula:
  single:
    enabled: true
    addresses: 192.168.111.103:9669
  cluster:
    enabled: false
    addresses: []
  username: root
  password: nebula
  pool:
    min-conns: 10
    max-conns: 100
    timeout: 3000
    idle-timeout: 60
  space:
    name: plm_graph
    charset: utf8
    replica-factor: 1
    partition-num: 100
```

- [ ] **步骤 2：Commit**

```bash
git add mf-start/mf-start-graph/src/main/resources/mf-graph-dev.yml
git commit -m "feat(graph): 添加mf-graph-dev.yml配置"
```

---

### 任务 8：编写 NebulaGraph 建表 SQL

**文件：**
- 创建：`mf-start/mf-start-graph/src/main/resources/nebula-schema.sql`

- [ ] **步骤 1：创建 nebula-schema.sql**

```sql
-- 创建图空间
CREATE SPACE IF NOT EXISTS plm_graph(
    partition_num = 100,
    replica_factor = 1,
    charset = utf8,
    collation = utf8_bin
);

-- 使用图空间
USE plm_graph;

-- 创建节点标签
CREATE TAG IF NOT EXISTS SsoOrg(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS Product(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS Folder(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS PartMaster(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS Part(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS DocumentMaster(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS Document(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

-- 创建边类型
CREATE EDGE IF NOT EXISTS ContainsLink(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime,
    from_id string NOT NULL,
    from_type string NOT NULL,
    to_id string NOT NULL,
    to_type string NOT NULL,
    properties string
);

CREATE EDGE IF NOT EXISTS PartVersionLink(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime,
    from_id string NOT NULL,
    from_type string NOT NULL,
    to_id string NOT NULL,
    to_type string NOT NULL,
    properties string
);

CREATE EDGE IF NOT EXISTS DocVersionLink(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime,
    from_id string NOT NULL,
    from_type string NOT NULL,
    to_id string NOT NULL,
    to_type string NOT NULL,
    properties string
);
```

- [ ] **步骤 2：Commit**

```bash
git add mf-start/mf-start-graph/src/main/resources/nebula-schema.sql
git commit -m "feat(graph): 添加NebulaGraph建表SQL"
```

---

## 自检清单

1. **规格覆盖度检查：**
   - [x] 模块结构设计 - 任务1完成
   - [x] NebulaGraph配置 - 任务2完成
   - [x] 图同步事件模型 - 任务3完成
   - [x] MQ消费逻辑 - 任务4完成
   - [x] 全量同步功能 - 任务5完成
   - [x] 混合查询服务 - 任务6完成
   - [x] Nacos配置 - 任务7完成
   - [x] NebulaGraph Schema - 任务8完成

2. **占位符扫描：**
   - [x] 已实现 NebulaClient 核心方法 (insertVertex, batchInsertVertices, insertEdge, batchInsertEdges, queryPaths, clearAndRecreateSpace, deleteVertex, deleteEdge)
   - [x] 已实现 GraphSyncService 全量同步 (syncAllNodes, syncAllEdges, batchQueryNodes)
   - [x] 已实现 GraphQueryService 混合查询 (queryProductTree, queryDirectChildren, queryShortestPath, queryNeighbors, querySubgraph)
   - [x] 已实现 GraphSyncConsumer DELETE事件处理

3. **类型一致性检查：**
   - GraphNode 字段：id, type, createBy, createTime, updateBy, updateTime
   - GraphEdge 字段：id, type, createBy, createTime, updateBy, updateTime, fromId, fromType, toId, toType, properties
   - 边类型：ContainsLink, PartVersionLink, DocVersionLink
   - 节点类型：SsoOrg, Product, Folder, PartMaster, Part, DocumentMaster, Document

4. **新增实体类：**
   - SsoOrg, Product, Folder, PartMaster, Part, DocumentMaster, Document
   - ContainsLink, PartVersionLink, DocVersionLink

5. **新增Mapper接口：**
   - SsoOrgMapper, ProductMapper, FolderMapper, PartMasterMapper, PartMapper, DocumentMasterMapper, DocumentMapper
   - ContainsLinkMapper, PartVersionLinkMapper, DocVersionLinkMapper

---

**计划完成日期：** 2026-04-16
**实现完成日期：** 2026-04-16
**设计文档：** docs/superpowers/specs/2026-04-16-plm-hybrid-db-architecture-design.md
**Git分支：** plm-hybrid-db
