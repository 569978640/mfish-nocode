# PLM系统关系数据库+图数据库混合查询架构设计

## 文档信息

| 项目   | 内容                       |
| ---- | ------------------------ |
| 项目名称 | mfish-nocode PLM 混合数据库架构 |
| 当前版本 | mf-2.3.1                 |
| 更新日期 | 2026-04-16               |
| 文档状态 | 设计阶段                     |
| 适用对象 | 后端开发工程师、架构师、运维工程师        |

***

## 目录

1. [背景与目标](#1-背景与目标)
2. [架构设计](#2-架构设计)
3. [模块结构](#3-模块结构)
4. [数据模型设计](#4-数据模型设计)
5. [同步流程设计](#5-同步流程设计)
6. [混合查询流程设计](#6-混合查询流程设计)
7. [接口设计](#7-接口设计)
8. [配置设计](#8-配置设计)
9. &#x20;[NebulaGraph 数据模型](#9-nebulagraph-数据模型)
10. [错误处理与容灾](#10-错误处理与容灾)

***

## 1. 背景与目标

### 1.1 项目背景

当前 mfish-nocode PLM 系统使用单一 MySQL 数据库存储所有数据。随着 PLM 业务复杂度提升，**模型之间的关系查询**（如 BOM 层级、多跳关系追溯、影响范围分析等）成为性能瓶颈。传统关系数据库在复杂关系查询上存在局限性，需要引入图数据库来提升关系查询效率。

### 1.2 设计目标

| 目标    | 说明                                            |
| ----- | --------------------------------------------- |
| 关系库为主 | 存储模型主数据，存储完整节点属性+关系属性，所有写操作、业务逻辑、事务、回滚        |
| 图库为辅  | 存储精简节点+完整边数据(含公共属性+业务属性)，数据来自关系库异步同步，不承担业务写逻辑 |
| 数据一致性 | 图库数据来自关系库异步同步，保证最终一致性                         |
| 查询效率  | 图库查路径+边业务属性，PG库查节点业务属性，混合查询                   |
| 配置灵活  | 单节点/集群模式通过配置切换                                |

### 1.3 设计原则

1. **职责分离**：关系库存储完整数据（主数据），图库存储精简节点+完整边数据（用于关系查询）
2. **事件驱动**：通过 RocketMQ 异步同步数据到图库
3. **最终一致**：图库数据允许短暂不一致，以性能换可用性
4. **配置驱动**：单节点/集群模式通过配置切换
5. **混合查询**：图库负责路径和边业务属性查询，关系库负责节点业务属性查询

***

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

| 方向  | 流程                                  | 说明          |
| --- | ----------------------------------- | ----------- |
| 写操作 | 业务Service → 关系库事务 → RocketMQ        | 所有写操作在关系库完成 |
| 同步  | RocketMQ → mf-graph消费 → NebulaGraph | 异步写入        |
| 查询  | 图库查路径 → 关系库查属性 → 合并返回               | 混合查询        |

***

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

***

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

图节点对应 PG 库中的主数据表，extends BaseEntity：

```java
@Data
@ApiModel("图节点")
public class GraphNode {
    @ApiModelProperty("节点ID (对应关系库主键)")
    private String id;

    @ApiModelProperty("节点类型: SsoOrg/Product/Folder/PartMaster/Part/DocumentMaster/Document")
    private String type;

    @ApiModelProperty("创建用户")
    private String createBy;

    @ApiModelProperty("创建时间")
    private Date createTime;

    @ApiModelProperty("更新用户")
    private String updateBy;

    @ApiModelProperty("更新时间")
    private Date updateTime;
}
```

### 4.3 图边 (GraphEdge)

图边对应 PG 库中的关系表（后缀为Link的表），extends BaseTreeEntity：

```java
@Data
@ApiModel("图边")
public class GraphEdge {
    @ApiModelProperty("边ID (对应PG库Link表主键)")
    private String id;

    @ApiModelProperty("边类型: Contain/Iterate")
    private String type;

    @ApiModelProperty("创建用户")
    private String createBy;

    @ApiModelProperty("创建时间")
    private Date createTime;

    @ApiModelProperty("更新用户")
    private String updateBy;

    @ApiModelProperty("更新时间")
    private Date updateTime;

    @ApiModelProperty("起始节点ID")
    private String fromId;

    @ApiModelProperty("起始节点类型")
    private String fromType;

    @ApiModelProperty("目标节点ID")
    private String toId;

    @ApiModelProperty("目标节点类型")
    private String toType;

    @ApiModelProperty("PG库Link表的业务属性")
    private Map<String, Object> properties;
}
```

### 4.4 节点类型定义

| 节点类型           | 类型码            | 说明    |
| -------------- | -------------- | ----- |
| SsoOrg         | SsoOrg         | 组织    |
| Product        | Product        | 产品库   |
| Folder         | Folder         | 文件夹   |
| PartMaster     | PartMaster     | 部件主数据 |
| Part           | Part           | 部件小版本 |
| DocumentMaster | DocumentMaster | 文档主数据 |
| Document       | Document       | 文档小版本 |

### 4.5 边类型定义

| 边类型 | 说明 | 起始节点 → 目标节点 |
|--------|------|---------------------|
| ContainsLink | 包含关系 | SsoOrg→Product, Product→Folder, Folder→Folder, Product→PartMaster, Product→DocumentMaster |
| PartVersionLink | 部件版本迭代关系 | PartMaster→Part |
| DocVersionLink | 文档版本迭代关系 | DocumentMaster→Document |

### 4.6 节点属性定义

所有节点都 **extends BaseEntity (cn.com.mfish.common.core.entity.BaseEntity)**，包含以下公共属性：

| 属性         | 类型       | 说明             |
| ---------- | -------- | -------------- |
| id         | String   | 节点ID (对应关系库主键) |
| createBy   | String   | 创建用户           |
| createTime | DateTime | 创建时间           |
| updateBy   | String   | 更新用户           |
| updateTime | DateTime | 更新时间           |

### 4.7 边属性定义

所有边都 **extends BaseTreeEntity (cn.com.mfish.common.core.entity.BaseTreeEntity)**，包含以下公共属性：

| 属性         | 类型                   | 说明                                                          |
| ---------- | -------------------- | ----------------------------------------------------------- |
| id         | String               | 边ID                                                         |
| type       | String               | 边类型 (ContainsLink/PartVersionLink/DocVersionLink) |
| createBy   | String               | 创建用户                                                        |
| createTime | DateTime             | 创建时间                                                        |
| updateBy   | String               | 更新用户                                                        |
| updateTime | DateTime             | 更新时间                                                        |
| fromId     | String               | 起始节点ID                                                      |
| fromType   | String               | 起始节点类型                                                      |
| toId       | String               | 目标节点ID                                                      |
| toType     | String               | 目标节点类型                                                      |
| properties | Map<String, Object> | **PG库Link表的业务属性**（如FolderLink的folderCode、PartLink的version等） |

### 4.8 实体类继承关系

| 实体类型 | 父类 | 说明 |
|---------|------|------|
| 节点实体类 | extends BaseEntity | 如 Product, PartMaster, Document 等 |
| 边实体类 | extends BaseTreeEntity | 如 ContainsLink, PartVersionLink, DocVersionLink 等（后缀为 Link 的关系表） |

### 4.9 关系表命名规范

根据边类型定义中的起始节点到目标节点信息：

| 边类型（Java类名） | PG库表名 | 说明 |
|------------------|---------|------|
| ContainsLink | contains_link | 存储所有包含关系（SsoOrg包含Product, Product包含Folder, Folder包含Folder, Product包含PartMaster, Product包含DocumentMaster） |
| PartVersionLink | part_version_link | 存储部件主数据到部件小版本的版本迭代关系 |
| DocVersionLink | doc_version_link | 存储文档主数据到文档小版本的版本迭代关系 |

### 4.10 关系属性存储策略

| 数据库         | 存储内容                            | 说明                      |
| ----------- | ------------------------------- | ----------------------- |
| PostgreSQL  | 完整节点属性 + Link表全部字段              | 主数据存储，所有写操作，节点业务属性查询    |
| NebulaGraph | 节点公共属性 + Link表全部字段（含properties） | 图库边的数据等于PG库对应Link表的全部字段 |

**核心思路**：

- **节点**：PG库主数据表 → 图库标签（只存 BaseEntity 公共属性）
- **边**：PG库Link表 → 图库边类型（存储 Link 表的全部字段，**properties 字段存储 Link 表的业务属性**）

图数据库存储完整的边数据，查询路径时可直接获取边的所有属性（包含业务属性）。

### 4.11 示例代码

```java
// 节点实体类 - extends BaseEntity
@Data
@ApiModel("产品库节点")
public class Product extends BaseEntity<String> {
    @ApiModelProperty("产品库编号")
    private String productCode;

    @ApiModelProperty("产品库名称")
    private String productName;

    @ApiModelProperty("所属组织ID")
    private String orgId;
}

// 边实体类 - extends BaseTreeEntity
@Data
@ApiModel("部件版本迭代边")
public class PartVersionLink extends BaseTreeEntity<String> {
    @ApiModelProperty("部件主数据ID")
    private String partMasterId;

    @ApiModelProperty("部件版本ID")
    private String partId;

    @ApiModelProperty("起始节点类型")
    private String fromType;

    @ApiModelProperty("目标节点类型")
    private String toType;

    @ApiModelProperty("Link表业务属性 (版本号等)")
    private Map<String, Object> properties;
}
```

### 4.12 图同步事件模型

```java
@Data
@ApiModel("图数据库同步事件")
public class GraphSyncEvent {
    @ApiModelProperty("事件ID")
    private String eventId;

    @ApiModelProperty("事件类型: CREATE/UPDATE/DELETE/FULL_SYNC")
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

### 4.13 图节点模型

```java
@Data
@ApiModel("图节点")
public class GraphNode {
    @ApiModelProperty("节点ID (对应关系库主键)")
    private String id;

    @ApiModelProperty("节点类型: SsoOrg/Product/Folder/PartMaster/Part/DocumentMaster/Document")
    private String type;

    @ApiModelProperty("创建用户")
    private String createBy;

    @ApiModelProperty("创建时间")
    private Date createTime;

    @ApiModelProperty("更新用户")
    private String updateBy;

    @ApiModelProperty("更新时间")
    private Date updateTime;
}
```

### 4.14 图边模型

```java
@Data
@ApiModel("图边")
public class GraphEdge {
    @ApiModelProperty("边ID")
    private String id;

    @ApiModelProperty("边类型: ContainsLink/PartVersionLink/DocVersionLink")
    private String type;

    @ApiModelProperty("起始节点ID")
    private String fromId;

    @ApiModelProperty("起始节点类型")
    private String fromType;

    @ApiModelProperty("目标节点ID")
    private String toId;

    @ApiModelProperty("目标节点类型")
    private String toType;

    @ApiModelProperty("创建用户")
    private String createBy;

    @ApiModelProperty("创建时间")
    private Date createTime;

    @ApiModelProperty("更新用户")
    private String updateBy;

    @ApiModelProperty("更新时间")
    private Date updateTime;

    @ApiModelProperty("PG库Link表的业务属性")
    private Map<String, Object> properties;
}
```

***

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

### 5.3 全量同步功能

全量同步功能：清空图数据库所有数据，然后将关系数据库所有后缀为Link表的数据同步到图数据库。

**触发方式**：

- 手动触发：通过管理接口调用
- 定时触发：可配置定时任务

**同步流程**：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         全量同步流程 (FULL_SYNC)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  触发全量同步                                                                │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │  Step 1: 清空图数据库                                            │        │
│  │  nGQL: DELETE SPACE plm_graph; CREATE SPACE plm_graph;         │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │  Step 2: 重新创建图空间 Schema                                   │        │
│  │  创建所有标签 (SsoOrg, Product, Folder, PartMaster, Part,        │        │
│  │              DocumentMaster, Document)                           │        │
│  │  创建所有边类型 (Contain, Iterate)                              │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │  Step 3: 查询所有节点数据                                        │        │
│  │  SELECT * FROM sso_org UNION ALL                               │        │
│  │  SELECT * FROM product UNION ALL                                │        │
│  │  SELECT * FROM folder UNION ALL                                 │        │
│  │  SELECT * FROM part_master UNION ALL                            │        │
│  │  SELECT * FROM part UNION ALL                                   │        │
│  │  SELECT * FROM document_master UNION ALL                        │        │
│  │  SELECT * FROM document                                          │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │  Step 4: 查询所有边数据                                          │        │
│  │  SELECT * FROM contains_link UNION ALL                             │        │
│  │  SELECT * FROM part_version_link UNION ALL                       │        │
│  │  SELECT * FROM doc_version_link                                   │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │  Step 5: 批量写入图数据库                                        │        │
│  │  批量 INSERT VERTEX ... VALUES ...                              │        │
│  │  批量 INSERT EDGE ... VALUES ...                                │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │  Step 6: 记录同步状态                                            │        │
│  │  记录同步时间、同步数量、是否成功                                 │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**全量同步接口**：

| 接口路径               | 方法   | 说明     | 权限                |
| ------------------ | ---- | ------ | ----------------- |
| /graph/sync/full   | POST | 触发全量同步 | graph:sync:full   |
| /graph/sync/status | GET  | 查询同步状态 | graph:sync:status |

**全量同步服务实现**：

```java
@Service
public class GraphFullSyncService {
    @Autowired
    private NebulaClient nebulaClient;

    @Autowired
    private SsoOrgMapper ssoOrgMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private FolderMapper folderMapper;
    @Autowired
    private PartMasterMapper partMasterMapper;
    @Autowired
    private PartMapper partMapper;
    @Autowired
    private DocumentMasterMapper documentMasterMapper;
    @Autowired
    private DocumentMapper documentMapper;
    @Autowired
    private ContainsLinkMapper containsLinkMapper;
    @Autowired
    private PartVersionLinkMapper partVersionLinkMapper;
    @Autowired
    private DocVersionLinkMapper docVersionLinkMapper;

    /**
     * 全量同步：清空图库，重新同步所有数据
     */
    public void fullSync() {
        // Step 1: 清空图数据库并重建Schema
        nebulaClient.clearAndRecreateSpace();

        // Step 2: 同步所有节点
        syncAllNodes();

        // Step 3: 同步所有边
        syncAllEdges();
    }

    private void syncAllNodes() {
        // 同步 SsoOrg
        List<SsoOrg> orgs = ssoOrgMapper.selectList(null);
        nebulaClient.batchInsertVertices("SsoOrg", convertToGraphNodes(orgs, "SsoOrg"));

        // 同步 Product
        List<Product> products = productMapper.selectList(null);
        nebulaClient.batchInsertVertices("Product", convertToGraphNodes(products, "Product"));

        // 同步 Folder
        List<Folder> folders = folderMapper.selectList(null);
        nebulaClient.batchInsertVertices("Folder", convertToGraphNodes(folders, "Folder"));

        // 同步 PartMaster
        List<PartMaster> partMasters = partMasterMapper.selectList(null);
        nebulaClient.batchInsertVertices("PartMaster", convertToGraphNodes(partMasters, "PartMaster"));

        // 同步 Part
        List<Part> parts = partMapper.selectList(null);
        nebulaClient.batchInsertVertices("Part", convertToGraphNodes(parts, "Part"));

        // 同步 DocumentMaster
        List<DocumentMaster> docMasters = documentMasterMapper.selectList(null);
        nebulaClient.batchInsertVertices("DocumentMaster", convertToGraphNodes(docMasters, "DocumentMaster"));

        // 同步 Document
        List<Document> documents = documentMapper.selectList(null);
        nebulaClient.batchInsertVertices("Document", convertToGraphNodes(documents, "Document"));
    }

    private void syncAllEdges() {
        // 同步 ContainsLink -> ContainsLink 边
        List<ContainsLink> containsLinks = containsLinkMapper.selectList(null);
        nebulaClient.batchInsertEdges("ContainsLink", convertToGraphEdges(containsLinks, "ContainsLink"));

        // 同步 PartVersionLink -> PartVersionLink 边
        List<PartVersionLink> partVersionLinks = partVersionLinkMapper.selectList(null);
        nebulaClient.batchInsertEdges("PartVersionLink", convertToGraphEdges(partVersionLinks, "PartVersionLink"));

        // 同步 DocVersionLink -> DocVersionLink 边
        List<DocVersionLink> docVersionLinks = docVersionLinkMapper.selectList(null);
        nebulaClient.batchInsertEdges("DocVersionLink", convertToGraphEdges(docVersionLinks, "DocVersionLink"));
    }
}
```

***

## 6. 混合查询流程设计

### 6.1 数据存储策略

| 数据库         | 存储内容                   | 用途                   |
| ----------- | ---------------------- | -------------------- |
| PostgreSQL  | 完整节点属性 + Link表全部字段     | 主数据存储，所有写操作，节点业务属性查询 |
| NebulaGraph | 节点公共属性 + **Link表全部字段** | 图库边数据等于PG库Link表的全部字段 |

**节点类型**：SsoOrg、Product、Folder、PartMaster、Part、DocumentMaster、Document

**边类型**：ContainsLink（包含关系）、PartVersionLink（部件版本迭代）、DocVersionLink（文档版本迭代）

**核心思路**：

- **节点**：PG库主数据表 → 图库标签（只存 BaseEntity 公共属性）
- **边**：PG库Link表 → 图库边类型（存储 Link 表的全部字段，包含公共属性和业务属性）

混合查询职责划分：

- **图库负责**：路径查询 + **边的所有属性**（包含Link表业务属性）
- **关系库负责**：节点业务属性（用节点ID列表批量查询）

### 6.2 查询流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           混合查询流程                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  请求: queryProductTree(productId="product001")                            │
│                                         │                                    │
│                                         ▼                                    │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                    Step 1: 图数据库查询                          │        │
│  │  nGQL: MATCH p=(p:Product {id:'product001'})-[*1..5]->(n)     │        │
│  │        RETURN p                                                 │        │
│  │                                                                    │        │
│  │  返回: 路径信息 + 边所有属性 (直接从图库获取)                      │        │
│  │  • nodes: [{"id":"product001", "type":"Product", ...},          │        │
│  │           {"id":"folder001", "type":"Folder", ...}]             │        │
│  │  • edges: [{"id":"edge001", "type":"ContainsLink",               │        │
│  │            "fromId":"folder001", "fromType":"Folder",           │        │
│  │            "toId":"product001", "toType":"Product",              │        │
│  │            "properties":{"folderCode":"F001"}}]                  │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│                                         │                                    │
│                                         ▼                                    │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                 Step 2: 提取节点ID列表                           │        │
│  │  allNodeIds = ["product001", "folder001", "part001", ...]      │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│                                         │                                    │
│                                         ▼                                    │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                 Step 3: 关系库批量查询节点业务属性                │        │
│  │  SQL: SELECT * FROM product WHERE id IN ('product001',...)     │        │
│  │       UNION ALL SELECT * FROM folder WHERE id IN (...)          │        │
│  │                                                                    │        │
│  │  返回: 节点业务属性映射 (从PG库获取)                              │        │
│  │  {                                                                 │        │
│  │    "product001": {"productCode": "P001", "productName": "产品A"},│        │
│  │    "folder001": {"folderName": "设计文件夹", "folderCode": "F001"},│        │
│  │    "part001": {"partName": "部件A", "partCode": "PA001", ...}  │        │
│  │  }                                                                 │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│                                         │                                    │
│                                         ▼                                    │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                 Step 4: 合并结果返回                             │        │
│  │  • 节点: 图库ID + 类型 + 关系库业务属性                          │        │
│  │  • 边: 来自图库 (包含Link表所有字段)                             │        │
│  │                                                                    │        │
│  │  最终返回:                                                         │        │
│  │  {                                                                 │        │
│  │    "nodes": [                                                     │        │
│  │      {"id":"product001", "type":"Product",                        │        │
│  │       "productCode":"P001", "productName":"产品A", ...},          │        │
│  │      {"id":"folder001", "type":"Folder",                          │        │
│  │       "folderName":"设计文件夹", ...}                             │        │
│  │    ],                                                             │        │
│  │    "edges": [                                                     │        │
│  │      {"id":"edge001", "type":"ContainsLink",                       │        │
│  │       "fromId":"folder001", "fromType":"Folder",                 │        │
│  │       "toId":"product001", "toType":"Product",                    │        │
│  │       "properties":{"folderCode":"F001"}}                         │        │
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
    @Autowired
    private FolderMapper folderMapper;
    @Autowired
    private PartMasterMapper partMasterMapper;

    /**
     * 产品结构树查询 - 混合查询
     * 图库负责: 路径查询 + 边的所有属性 (包含Link表业务属性)
     * 关系库负责: 节点业务属性 (用节点ID列表批量查询)
     */
    public GraphResult queryProductTree(String productId) {
        // Step 1: 图数据库查询 - 获取路径和边的所有属性
        PathQueryResult pathResult = nebulaClient.queryPathsWithEdgeProps(
            "MATCH p=(p:Product {id:'" + productId + "'})-[*1..5]->(n) RETURN p"
        );

        // Step 2: 从路径中提取所有节点ID
        Set<String> allNodeIds = extractNodeIds(pathResult);

        // Step 3: 关系库批量查询节点业务属性
        Map<String, Map<String, Object>> nodeAttributes = relationDB.batchQueryNodes(allNodeIds);

        // Step 4: 合并结果
        // - 节点: 图库ID + 类型 + 关系库业务属性
        // - 边: 来自图库 (包含Link表所有字段)
        return mergeResults(pathResult, nodeAttributes);
    }
}
```

***

## 7. 接口设计

### 7.1 图谱查询接口

| 接口路径                   | 方法   | 说明   | 权限                    |
| ---------------------- | ---- | ---- | --------------------- |
| /graph/query/paths     | POST | 路径查询 | graph:query:paths     |
| /graph/query/neighbors | POST | 邻居查询 | graph:query:neighbors |
| /graph/query/subgraph  | POST | 子图查询 | graph:query:subgraph  |
| /graph/query/shortest  | POST | 最短路径 | graph:query:shortest  |
| /graph/sync/status     | GET  | 同步状态 | graph:sync:status     |

### 7.2 请求/响应模型

```json
// 路径查询请求
{
  "startId": "product001",
  "startType": "Product",
  "edgeTypes": ["ContainsLink", "PartVersionLink", "DocVersionLink"],
  "direction": "OUT",
  "depth": 5
}

// 路径查询响应
{
  "success": true,
  "code": 200,
  "data": {
    "nodes": [
      {
        "id": "product001",
        "type": "Product",
        "productCode": "P001",
        "productName": "产品A",
        "createBy": "admin",
        "createTime": "2026-04-16 10:00:00"
      },
      {
        "id": "folder001",
        "type": "Folder",
        "folderName": "设计文件夹",
        "createBy": "admin",
        "createTime": "2026-04-16 11:00:00"
      }
    ],
    "edges": [
      {
        "id": "edge001",
        "type": "ContainsLink",
        "fromId": "folder001",
        "fromType": "Folder",
        "toId": "product001",
        "toType": "Product",
        "createBy": "admin",
        "createTime": "2026-04-16 11:00:00",
        "properties": {"folderCode": "F001"}
      }
    ]
  }
}
```

***

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

| 模式  | 配置                             | 说明         |
| --- | ------------------------------ | ---------- |
| 单节点 | `nebula.single.enabled: true`  | 适用于开发/测试环境 |
| 集群  | `nebula.cluster.enabled: true` | 适用于生产环境    |

***

## 9. NebulaGraph 数据模型

### 9.1 设计说明

NebulaGraph 图数据库存储以下数据：

- **点 (Vertex)**：PG库主数据表的公共属性（id, createBy, createTime, updateBy, updateTime）
- **边 (Edge)**：PG库Link表的全部字段（包含公共属性 + **properties 存储业务属性**）

**节点类型**（对应PG库主数据表）：

| 节点类型           | 说明    |
| -------------- | ----- |
| SsoOrg         | 组织    |
| Product        | 产品库   |
| Folder         | 文件夹   |
| PartMaster     | 部件主数据 |
| Part           | 部件小版本 |
| DocumentMaster | 文档主数据 |
| Document       | 文档小版本 |

**边类型**（对应PG库Link表）：

| 边类型 | 说明 | PG库表名 |
|--------|------|---------|
| ContainsLink | 包含关系 | contains_link |
| PartVersionLink | 部件版本迭代关系 | part_version_link |
| DocVersionLink | 文档版本迭代关系 | doc_version_link |

**存储策略**：

- 节点只存储 BaseEntity 的公共属性
- 边存储 BaseTreeEntity 的所有字段，**properties 字段以 JSON 字符串存储 Link 表的业务属性**
- 查询路径时，图库返回完整的边数据（含业务属性），无需再查关系库获取边属性

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

-- 创建标签 (节点类型) - 存储 BaseEntity 公共属性
-- 注意: NebulaGraph标签名与Java类名一致
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

-- 创建边类型 - 存储 BaseTreeEntity 所有字段 + Link表业务属性
-- id, type, create_by, create_time, update_by, update_time, from_id, from_type, to_id, to_type, properties
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

### 9.3 nGQL 示例

```sql
-- 插入节点
INSERT VERTEX SsoOrg(id, create_by, create_time) VALUES 'org001':('org001', 'admin', NOW());
INSERT VERTEX Product(id, create_by, create_time) VALUES 'product001':('product001', 'admin', NOW());
INSERT VERTEX Folder(id, create_by, create_time) VALUES 'folder001':('folder001', 'admin', NOW());
INSERT VERTEX PartMaster(id, create_by, create_time) VALUES 'partmaster001':('partmaster001', 'admin', NOW());
INSERT VERTEX Part(id, create_by, create_time) VALUES 'part001':('part001', 'admin', NOW());
INSERT VERTEX DocumentMaster(id, create_by, create_time) VALUES 'docmaster001':('docmaster001', 'admin', NOW());
INSERT VERTEX Document(id, create_by, create_time) VALUES 'doc001':('doc001', 'admin', NOW());

-- 插入边 (包含id, type, 公共属性和业务属性)
INSERT EDGE ContainsLink(id, type, from_id, from_type, to_id, to_type, create_by, create_time, properties)
VALUES 'folder001' -> 'product001':('edge001', 'ContainsLink', 'folder001', 'Folder', 'product001', 'Product', 'admin', NOW(), '{"folderCode":"F001"}');

INSERT EDGE PartVersionLink(id, type, from_id, from_type, to_id, to_type, create_by, create_time, properties)
VALUES 'partmaster001' -> 'part001':('edge002', 'PartVersionLink', 'partmaster001', 'PartMaster', 'part001', 'Part', 'admin', NOW(), '{"version":"V1.0"}');

INSERT EDGE DocVersionLink(id, type, from_id, from_type, to_id, to_type, create_by, create_time, properties)
VALUES 'docmaster001' -> 'doc001':('edge003', 'DocVersionLink', 'docmaster001', 'DocumentMaster', 'doc001', 'Document', 'admin', NOW(), '{"version":"V1.0"}');

-- 查询路径 (多跳) - 返回节点和边的所有属性
MATCH p=(n)-[e:ContainsLink|PartVersionLink|DocVersionLink*1..3]->(m)
WHERE id(n) == 'product001'
RETURN p;

-- 查询某个产品的所有文件夹和部件
MATCH (p:Product)-[e:ContainsLink]->(n)
WHERE id(p) == 'product001'
RETURN n, e;

-- 查询某个部件主数据的版本链
MATCH (pm:PartMaster)-[e:PartVersionLink]->(p:Part)
WHERE id(pm) == 'partmaster001'
RETURN p, e;

-- 统计某个文件夹下的直接子节点数量
GO FROM 'folder001' OVER ContainsLink YIELD dst(edge);
```

***

## 10. 错误处理与容灾

### 10.1 失败处理策略

| 失败次数 | 策略                     |
| ---- | ---------------------- |
| 1-3次 | 指数退避重试 (1s, 2s, 4s...) |
| 4次+  | 转入死信队列，等待人工处理          |

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

| 指标              | 说明     |
| --------------- | ------ |
| pendingEvents   | 待处理事件数 |
| processedEvents | 已处理事件数 |
| failedEvents    | 失败事件数  |
| lastSyncTime    | 最后同步时间 |

***

## 11. 实现计划

待设计文档审批通过后，将使用 writing-plans 技能生成详细实现计划。

***

**文档编写日期**：2026-04-16
**文档版本**：v1.0
**设计者**：AI Assistant
