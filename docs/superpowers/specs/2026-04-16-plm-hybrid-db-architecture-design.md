# PLM系统关系数据库+图数据库混合查询架构设计

## 文档信息

| 项目 | 内容 |
|-----|------|
| 项目名称 | mfish-nocode PLM 混合数据库架构 |
| 当前版本 | mf-2.3.1 |
| 更新日期 | 2026-04-16 |
| 文档状态 | 设计阶段 |
| 适用对象 | 后端开发工程师、架构师、运维工程师 |

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [架构设计](#2-架构设计)
3. [模块结构](#3-模块结构)
4. [数据模型设计](#4-数据模型设计)
5. [同步流程设计](#5-同步流程设计)
6. [混合查询流程设计](#6-混合查询流程设计)
7. [接口设计](#7-接口设计)
8. [配置设计](#8-配置设计)
9. [ NebulaGraph 数据模型](#9-nebulagraph-数据模型)
10. [错误处理与容灾](#10-错误处理与容灾)

---

## 1. 背景与目标

### 1.1 项目背景

当前 mfish-nocode PLM 系统使用单一 MySQL 数据库存储所有数据。随着 PLM 业务复杂度提升，**模型之间的关系查询**（如 BOM 层级、多跳关系追溯、影响范围分析等）成为性能瓶颈。传统关系数据库在复杂关系查询上存在局限性，需要引入图数据库来提升关系查询效率。

### 1.2 设计目标

| 目标 | 说明 |
|-----|------|
| 关系库为主 | 存储模型主数据，存储完整节点属性+关系属性，所有写操作、业务逻辑、事务、回滚 |
| 图库为辅 | 存储精简节点+关系拓扑+关系属性，数据来自关系库异步同步，不承担业务写逻辑 |
| 数据一致性 | 图库数据来自关系库异步同步，保证最终一致性 |
| 查询效率 | 图库查路径+关系属性，关系库查节点属性，混合查询 |
| 配置灵活 | 单节点/集群模式通过配置切换 |

### 1.3 设计原则

1. **职责分离**：关系库存储完整数据（主数据），图库存储精简数据（用于关系查询）
2. **事件驱动**：通过 RocketMQ 异步同步数据到图库
3. **最终一致**：图库数据允许短暂不一致，以性能换可用性
4. **配置驱动**：单节点/集群模式通过配置切换
5. **混合查询**：图库负责路径和关系属性查询，关系库负责节点属性查询

---

## 2. 架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PLM 服务层 (mf-plm)                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐  │
│  │  业务Service    │  │  事件发布Service │  │  混合查询Service            │  │
│  │  (写操作)       │  │  (发送MQ消息)    │  │ (图库查关系+关系库查属性)   │  │
│  └────────┬────────┘  └────────┬────────┘  └──────────────┬──────────────┘  │
└───────────┼────────────────────┼──────────────────────────┼─────────────────┘
            │                    │                          │
            ▼                    ▼                          │
┌───────────────────────────────────────┐                   │
│           关系库 (PostgreSQL)          │                   │
│  ┌─────────────────────────────────┐  │                   │
│  │  模型主数据表  │  关系+关系属性表 │  │                   │
│  └─────────────────────────────────┘  │                   │
│              │                        │                   │
│              │ 事务提交成功            │                   │
│              ▼                        │                   │
│  ┌─────────────────────────────────┐  │                   │
│  │      RocketMQ (plm-graph-sync)  │─────────────────────┘
│  └─────────────────────────────────┘
│              │
│              ▼
│  ┌─────────────────────────────────┐
│  │    图数据库服务 (mf-graph)       │
│  │  ┌─────────────────────────┐   │
│  │  │  MQ消费端                │   │
│  │  │  • 解析事件               │   │
│  │  │  • 写入NebulaGraph       │   │
│  │  │  • 失败重试/死信队列      │   │
│  │  └─────────────────────────┘   │
│  │  ┌─────────────────────────┐   │
│  │  │  图查询API               │   │
│  │  │  • nGQL查询              │   │
│  │  │  • 路径分析              │   │
│  │  │  • 关系探索              │   │
│  │  └─────────────────────────┘   │
│  └─────────────────────────────────┘
│              │
│              ▼
│  ┌─────────────────────────────────┐
│  │      NebulaGraph 图数据库        │
│  │  • 点 (精简节点)                │
│  │  • 边 (关系拓扑+属性)           │
│  └─────────────────────────────────┘
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 数据流向

| 方向 | 流程 | 说明 |
|-----|------|------|
| 写操作 | 业务Service → 关系库事务 → RocketMQ | 所有写操作在关系库完成 |
| 同步 | RocketMQ → mf-graph消费 → NebulaGraph | 异步写入 |
| 查询 | 图库查路径 → 关系库查属性 → 合并返回 | 混合查询 |

---

## 3. 模块结构

### 3.1 新增模块

```
mfish-nocode/
├── mf-graph/                              # 新建：图数据库服务
│   ├── pom.xml
│   └── src/main/java/cn/com/mfish/graph/
│       ├── GraphApplication.java          # 启动类
│       ├── config/                        # 配置类
│       │   ├── NebulaConfig.java          # NebulaGraph连接配置
│       │   └── RocketMQConfig.java        # RocketMQ消费配置
│       ├── controller/                    # 图查询API
│       │   └── GraphQueryController.java  # 图谱查询接口
│       ├── service/                       # 服务层
│       │   ├── GraphQueryService.java     # 图查询服务
│       │   └── GraphSyncService.java      # 图同步服务
│       ├── consumer/                      # MQ消费端
│       │   └── GraphSyncConsumer.java     # 图数据同步消费者
│       ├── client/                        # 图数据库客户端
│       │   └── NebulaClient.java           # NebulaGraph客户端封装
│       └── model/                         # 数据模型
│           ├── event/                     # 同步事件模型
│           │   └── GraphSyncEvent.java     # 图同步事件
│           ├── node/                       # 图节点模型
│           │   └── GraphNode.java          # 图节点
│           └── edge/                       # 图边模型
│               └── GraphEdge.java          # 图边
│
├── mf-api/
│   └── mf-graph-api/                      # 新建：图服务API接口
│       └── pom.xml
│
├── mf-business/
│   └── mf-graph/                          # 新建：图服务业务模块
│       └── pom.xml
│
└── mf-start/
    └── mf-start-graph/                    # 新建：图服务启动模块
        ├── pom.xml
        └── src/main/resources/
            ├── bootstrap.yml
            └── mf-graph-dev.yml           # Nacos配置文件
```

### 3.2 依赖关系

```
mf-start-graph
    ├── mf-graph (business)
    │     ├── mf-graph-api
    │     │     └── mf-common (传递)
    │     ├── mf-common-ds
    │     ├── mf-common-cloud
    │     └── mf-common-redis
    └── mf-common RocketMQ (传递)
```

---

## 4. 数据模型设计

### 4.1 图同步事件 (GraphSyncEvent)

```java
@Data
@ApiModel("图数据库同步事件")
public class GraphSyncEvent {
    @ApiModelProperty("事件ID")
    private String eventId;

    @ApiModelProperty("事件类型: CREATE/UPDATE/DELETE")
    private String eventType;

    @ApiModelProperty("时间戳")
    private Long timestamp;

    @ApiModelProperty("业务来源服务")
    private String source;

    @ApiModelProperty("操作用户")
    private String operator;

    @ApiModelProperty("节点变更列表")
    private List<GraphNode> nodes;

    @ApiModelProperty("边变更列表")
    private List<GraphEdge> edges;
}
```

### 4.2 图节点 (GraphNode)

```java
@Data
@ApiModel("图节点")
public class GraphNode {
    @ApiModelProperty("节点ID (对应关系库主键)")
    private String id;

    @ApiModelProperty("节点类型: Product/Part/Material/Vendor等")
    private String type;

    @ApiModelProperty("节点名称")
    private String name;

    @ApiModelProperty("精简属性 (只存图库需要的字段)")
    private Map<String, Object> properties;
}
```

### 4.3 图边 (GraphEdge) - 关系属性

```java
@Data
@ApiModel("图边")
public class GraphEdge {
    @ApiModelProperty("起始节点ID")
    private String srcId;

    @ApiModelProperty("起始节点类型")
    private String srcType;

    @ApiModelProperty("目标节点ID")
    private String dstId;

    @ApiModelProperty("目标节点类型")
    private String dstType;

    @ApiModelProperty("关系类型: CONTAIN/USE/REPLACE/SUPPLY等")
    private String edgeType;

    @ApiModelProperty("关系属性 (如数量、单位、版本等)")
    private Map<String, Object> properties;
}
```

### 4.4 节点类型定义

| 节点类型 | 类型码 | 说明 |
|---------|--------|------|
| SsoOrg | SSO_ORG | 组织 |
| Product | PRODUCT | 产品库 |
| Folder | FOLDER | 文件夹 |
| PartMaster | PART_MASTER | 部件主数据 |
| Part | PART | 部件小版本 |
| DocumentMaster | DOCUMENT_MASTER | 文档主数据 |
| Document | DOCUMENT | 文档小版本 |

### 4.5 边类型定义

| 边类型 | 说明 | 起始节点 → 目标节点 |
|--------|------|---------------------|
| CONTAIN | 包含关系 | SsoOrg→Product, Product→Folder, Folder→Folder, Product→PartMaster, Product→DocumentMaster |
| ITERATE | 版本迭代关系 | PartMaster→Part, DocumentMaster→Document |

### 4.6 节点属性定义

所有节点都 extends `cn.com.mfish.common.core.entity.BaseEntity`，包含以下公共属性：

| 属性 | 类型 | 说明 |
|-----|------|------|
| id | T | 节点ID (对应关系库主键) |
| createBy | String | 创建用户 |
| createTime | Date | 创建时间 |
| updateBy | String | 更新用户 |
| updateTime | Date | 更新时间 |

### 4.7 边属性定义

所有边都 extends `cn.com.mfish.common.core.entity.BaseEntity`，包含以下属性：

| 属性 | 类型 | 说明 |
|-----|------|------|
| id | String | 边ID |
| type | String | 边类型 (CONTAIN/ITERATE) |
| fromId | String | 起始节点ID |
| fromType | String | 起始节点类型 |
| toId | String | 目标节点ID |
| toType | String | 目标节点类型 |
| createBy | String | 创建用户 |
| createTime | Date | 创建时间 |
| updateBy | String | 更新用户 |
| updateTime | Date | 更新时间 |

### 4.8 关系属性存储策略

| 数据库 | 存储内容 | 说明 |
|-------|---------|------|
| PostgreSQL | 完整节点属性 + 关系属性 | 主数据存储，包含所有业务字段 |
| NebulaGraph | 精简节点 + 边属性 | 只存储节点ID、类型和公共属性，不存储具体业务属性 |

---

## 5. 同步流程设计

### 5.1 同步流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           同步流程                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  业务Service ──▶ 关系库事务 ──▶ 提交成功 ──▶ 发送MQ消息                     │
│       │                                     │                              │
│       │                                     ▼                              │
│       │                           ┌─────────────────┐                       │
│       │                           │  GraphSyncEvent │                       │
│       │                           │  • eventType    │                       │
│       │                           │  • nodes[]      │                       │
│       │                           │  • edges[]      │                       │
│       │                           └────────┬────────┘                       │
│       │                                    │                                 │
│       ▼                                    ▼                                 │
│  (业务处理完成)                    RocketMQ Topic: plm-graph-sync            │
│                                                                             │
│                                    MQ Consumer                               │
│                                         │                                   │
│                                         ▼                                   │
│                              ┌─────────────────────┐                        │
│                              │  解析GraphSyncEvent │                        │
│                              └──────────┬──────────┘                        │
│                                         │                                    │
│         ┌───────────────────────────────┼───────────────────────────────┐   │
│         ▼                               ▼                               ▼   │
│  ┌─────────────┐              ┌─────────────┐              ┌─────────────┐│
│  │ 写入节点    │              │ 写入边      │              │ 删除节点/边  ││
│  │ INSERT VID  │              │ INSERT EDGE │              │ DELETE VID/EDGE│
│  └──────┬──────┘              └──────┬──────┘              └──────┬──────┘│
│         │                           │                           │       │
│         └───────────────────────────┼───────────────────────────┘       │
│                                     ▼                                    │
│                            ┌─────────────────┐                          │
│                            │ NebulaGraph     │                          │
│                            │ 执行 nGQL       │                          │
│                            └────────┬────────┘                          │
│                                     │                                    │
│                    ┌────────────────┼────────────────┐                  │
│                    ▼                                 ▼                  │
│            ┌───────────────┐                   ┌───────────────┐         │
│            │   成功        │                   │   失败        │         │
│            │   ACK         │                   │   重试/死信   │         │
│            └───────────────┘                   └───────────────┘         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 事件发送示例

```java
@Service
public class ProductServiceImpl {
    @Autowired
    private GraphEventPublisher eventPublisher;

    public void createProduct(Product product) {
        // 1. 业务处理
        productMapper.insert(product);

        // 2. 构建事件
        GraphSyncEvent event = GraphSyncEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("CREATE")
            .timestamp(System.currentTimeMillis())
            .source("mf-plm")
            .operator(SecurityUtils.getUsername())
            .nodes(buildProductNode(product))
            .edges(buildBomEdges(product))
            .build();

        // 3. 发送MQ消息 (事务成功后)
        eventPublisher.publishEvent(event);
    }
}
```

---

## 6. 混合查询流程设计

### 6.1 数据存储策略

| 数据库 | 存储内容 | 用途 |
|-------|---------|------|
| PostgreSQL | 完整节点属性 + 关系属性 | 主数据存储，所有写操作，节点业务属性查询 |
| NebulaGraph | 精简节点 + 边属性 | 关系查询，路径查询，边公共属性查询 |

**节点类型**：SsoOrg、Product、Folder、PartMaster、Part、DocumentMaster、Document

**边类型**：CONTAIN（包含关系）、ITERATE（版本迭代关系）

**核心思路**：图数据库存储节点和边的公共属性，查询时图库直接返回路径+边属性，无需再查关系库。

混合查询职责划分：
- **图库负责**：路径查询 + 边公共属性（fromId, toId, fromType, toType, createBy, createTime等）
- **关系库负责**：节点业务属性（用节点ID列表批量查询）

### 6.2 查询流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           混合查询流程                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  请求: queryBomChain(partId="P001")                                         │
│                                         │                                    │
│                                         ▼                                    │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                    Step 1: 图数据库查询                          │        │
│  │  nGQL: MATCH p=(p:Part {id:'P001'})-[*1..5]->(n) RETURN p       │        │
│  │                                                                    │        │
│  │  返回: 路径信息 + 关系属性 (直接从图库获取)                         │        │
│  │  • nodes: [{"id":"P001", "type":"PART"}, {"id":"M001", ...}]    │        │
│  │  • edges: [{"type":"CONTAIN", "src":"P001", "dst":"M001",        │        │
│  │            "properties":{"quantity":10, "unit":"个"}}]            │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│                                         │                                    │
│                                         ▼                                    │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                 Step 2: 提取节点ID列表                           │        │
│  │  allIds = ["P001", "M001", "M002", "S001"]                      │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│                                         │                                    │
│                                         ▼                                    │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                 Step 3: 关系库批量查询节点属性                    │        │
│  │  SQL: SELECT * FROM plm_model WHERE id IN ('P001','M001',...)   │        │
│  │                                                                    │        │
│  │  返回: 节点属性映射 (只查节点属性，关系属性已从图库获取)            │        │
│  │  {                                                                 │        │
│  │    "P001": {"name": "装配体A", "spec": "规格1", "material": ...},│        │
│  │    "M001": {"name": "螺丝M3", "material": "不锈钢", "size": ...} │        │
│  │  }                                                                 │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│                                         │                                    │
│                                         ▼                                    │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                 Step 4: 合并结果返回                             │        │
│  │  • 节点属性: 来自关系库                                           │        │
│  │  • 关系属性: 来自图库 (已在Step1获取)                              │        │
│  │                                                                    │        │
│  │  最终返回:                                                         │        │
│  │  {                                                                 │        │
│  │    "nodes": [                                                     │        │
│  │      {"id":"P001", "type":"PART", "name":"装配体A", "spec":"..."},│        │
│  │      {"id":"M001", "type":"MATERIAL", "name":"螺丝M3", ...}      │        │
│  │    ],                                                              │        │
│  │    "edges": [                                                     │        │
│  │      {"type":"CONTAIN", "src":"P001", "dst":"M001",              │        │
│  │       "properties":{"quantity":10, "unit":"个"}}                  │        │
│  │    ]                                                              │        │
│  │  }                                                                 │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.3 查询服务实现

```java
@Service
public class GraphQueryServiceImpl {
    @Autowired
    private NebulaClient nebulaClient;

    @Autowired
    private ProductMapper productMapper;

    /**
     * BOM链查询 - 混合查询
     * 图库负责: 路径查询 + 关系属性查询
     * 关系库负责: 节点属性查询
     */
    public GraphResult queryBomChain(String partId) {
        // Step 1: 图数据库查询 - 获取路径和关系属性 (关系属性直接从图库获取)
        PathQueryResult pathResult = nebulaClient.queryPathsWithEdgeProps(
            "MATCH p=(p:Part {id:'" + partId + "'})-[*1..5]->(n) RETURN p"
        );

        // Step 2: 从路径中提取所有节点ID
        Set<String> allNodeIds = extractNodeIds(pathResult);

        // Step 3: 关系库批量查询节点属性 (只查节点属性，关系属性已在Step1获取)
        Map<String, NodeAttributes> nodeAttributes = relationDB.batchQueryNodes(allNodeIds);

        // Step 4: 合并结果
        // - 节点: 图库ID + 关系库属性
        // - 边: 关系属性 (直接从图库返回)
        return mergeResults(pathResult, nodeAttributes);
    }
}
```

---

## 7. 接口设计

### 7.1 图谱查询接口

| 接口路径 | 方法 | 说明 | 权限 |
|---------|------|------|-----|
| /graph/query/paths | POST | 路径查询 | graph:query:paths |
| /graph/query/neighbors | POST | 邻居查询 | graph:query:neighbors |
| /graph/query/subgraph | POST | 子图查询 | graph:query:subgraph |
| /graph/query/shortest | POST | 最短路径 | graph:query:shortest |
| /graph/sync/status | GET | 同步状态 | graph:sync:status |

### 7.2 请求/响应模型

```json
// 路径查询请求
{
  "startId": "P001",
  "startType": "PART",
  "edgeTypes": ["CONTAIN", "USE"],
  "direction": "OUT",
  "depth": 5
}

// 路径查询响应
{
  "success": true,
  "code": 200,
  "data": {
    "paths": [
      {
        "nodes": [
          {"id": "P001", "type": "PART", "name": "装配体A", ...},
          {"id": "M001", "type": "MATERIAL", "name": "螺丝M3", ...}
        ],
        "edges": [
          {"type": "CONTAIN", "src": "P001", "dst": "M001", "properties": {"quantity": 10}}
        ]
      }
    ]
  }
}
```

---

## 8. 配置设计

### 8.1 Nacos配置结构

```
Nacos 配置中心
├── application-dev.yml              # 公共配置 (Redis/Feign/日志)
├── application.yml                 # 主配置
├── mf-gateway-dev.yml              # 网关配置
├── mf-oauth-dev.yml                # 认证服务配置
├── mf-sys-dev.yml                  # 系统服务配置
├── mf-plm-dev.yml                  # PLM服务配置 (关系库PG)
├── mf-graph-dev.yml                # ★ 新增：图数据库服务配置
└── sentinel-mf-gateway             # Sentinel限流规则
```

### 8.2 mf-graph-dev.yml 配置

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

# PostgreSQL 关系库配置
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

# RocketMQ 消费配置
rocketmq:
  consumer:
    group: plm-graph-sync-group
    topic: plm-graph-sync
    enableDLQ: true
    maxRetryTimes: 3

# NebulaGraph 配置
nebula:
  # 单节点配置
  single:
    enabled: true
    addresses: 192.168.111.103:9669
  # 集群配置
  cluster:
    enabled: false
    addresses:
      - 192.168.1.101:9669
      - 192.168.1.102:9669
      - 192.168.1.103:9669
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

### 8.3 配置切换说明

| 模式 | 配置 | 说明 |
|-----|------|------|
| 单节点 | `nebula.single.enabled: true` | 适用于开发/测试环境 |
| 集群 | `nebula.cluster.enabled: true` | 适用于生产环境 |

---

## 9. NebulaGraph 数据模型

### 9.1 设计说明

NebulaGraph 图数据库存储以下数据：
- **点 (Vertex)**：精简节点信息（ID、类型、公共属性）
- **边 (Edge)**：关系拓扑 + 公共属性（fromId, toId, fromType, toType等）

**节点类型**：SsoOrg、Product、Folder、PartMaster、Part、DocumentMaster、Document

**边类型**：CONTAIN（包含关系）、ITERATE（版本迭代关系）

所有节点和边都包含公共属性：id、type、createBy、createTime、updateBy、updateTime
所有边还包含：fromId、fromType、toId、toType

关系属性存储在边上，查询路径时可直接获取关系属性，无需再查关系库。

### 9.2 图空间创建

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

-- 创建标签 (节点类型)
CREATE TAG IF NOT EXISTS PRODUCT();
CREATE TAG IF NOT EXISTS PART();
CREATE TAG IF NOT EXISTS MATERIAL();
CREATE TAG IF NOT EXISTS VENDOR();
CREATE TAG IF NOT EXISTS DOCUMENT();

-- 创建边类型
CREATE EDGE IF NOT EXISTS CONTAIN();
CREATE EDGE IF NOT EXISTS USE();
CREATE EDGE IF NOT EXISTS REPLACE();
CREATE EDGE IF NOT EXISTS SUPPLY();
CREATE EDGE IF NOT EXISTS VERSION();
CREATE EDGE IF NOT EXISTS CHANGE();
```

### 9.2 图空间创建

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

-- 创建标签 (节点类型) - 包含公共属性
CREATE TAG IF NOT EXISTS SSO_ORG(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS PRODUCT(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS FOLDER(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS PART_MASTER(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS PART(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS DOCUMENT_MASTER(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS DOCUMENT(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

-- 创建边类型 - 包含公共属性
CREATE EDGE IF NOT EXISTS CONTAIN(
    from_id string NOT NULL,
    from_type string NOT NULL,
    to_id string NOT NULL,
    to_type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE EDGE IF NOT EXISTS ITERATE(
    from_id string NOT NULL,
    from_type string NOT NULL,
    to_id string NOT NULL,
    to_type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);
```

### 9.3 nGQL 示例

```sql
-- 插入节点
INSERT VERTEX SSO_ORG(id, type, create_by, create_time) VALUES 'org001':('org001', 'SSO_ORG', 'admin', NOW());
INSERT VERTEX PRODUCT(id, type, create_by, create_time) VALUES 'product001':('product001', 'PRODUCT', 'admin', NOW());
INSERT VERTEX PART_MASTER(id, type, create_by, create_time) VALUES 'partmaster001':('partmaster001', 'PART_MASTER', 'admin', NOW());
INSERT VERTEX PART(id, type, create_by, create_time) VALUES 'part001':('part001', 'PART', 'admin', NOW());

-- 插入边 (包含from/to类型和公共属性)
INSERT EDGE CONTAIN(from_id, from_type, to_id, to_type, create_by, create_time)
VALUES 'org001' -> 'product001':('org001', 'SSO_ORG', 'product001', 'PRODUCT', 'admin', NOW());

INSERT EDGE ITERATE(from_id, from_type, to_id, to_type, create_by, create_time)
VALUES 'partmaster001' -> 'part001':('partmaster001', 'PART_MASTER', 'part001', 'PART', 'admin', NOW());

-- 查询路径 (多跳) - 返回节点和边的所有属性
MATCH p=(n)-[e:CONTAIN|ITERATE*1..3]->(m)
WHERE id(n) == 'product001'
RETURN p;

-- 查询某个产品的所有文件夹和部件
MATCH (p:PRODUCT)-[e:CONTAIN]->(n)
WHERE id(p) == 'product001'
RETURN n, e;

-- 查询某个部件主数据的版本链
MATCH (pm:PART_MASTER)-[e:ITERATE]->(p:PART)
WHERE id(pm) == 'partmaster001'
RETURN p, e;

-- 统计某个文件夹下的直接子节点数量
GO FROM 'folder001' OVER CONTAIN YIELD dst(edge);
```

---

## 10. 错误处理与容灾

### 10.1 失败处理策略

| 失败次数 | 策略 |
|---------|------|
| 1-3次 | 指数退避重试 (1s, 2s, 4s...) |
| 4次+ | 转入死信队列，等待人工处理 |

### 10.2 死信队列配置

```yaml
rocketmq:
  consumer:
    # 死信队列配置
    dlq:
      enabled: true
      topic: plm-graph-sync-dlq
      maxAttempts: 3
```

### 10.3 同步状态监控

| 指标 | 说明 |
|-----|------|
| pendingEvents | 待处理事件数 |
| processedEvents | 已处理事件数 |
| failedEvents | 失败事件数 |
| lastSyncTime | 最后同步时间 |

---

## 11. 实现计划

待设计文档审批通过后，将使用 writing-plans 技能生成详细实现计划。

---

**文档编写日期**：2026-04-16
**文档版本**：v1.0
**设计者**：AI Assistant
