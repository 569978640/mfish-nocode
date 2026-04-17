# MFish PLM 系统多数据库架构设计

## 文档信息

| 项目 | 内容 |
|-----|------|
| 项目名称 | mfish-nocode PLM 多数据库架构 |
| 当前版本 | mf-2.3.1 |
| 更新日期 | 2026-04-17 |
| 文档状态 | 正式发布 |
| 适用对象 | 后端开发工程师、架构师、运维工程师 |

---

## 1. 背景与目标

### 1.1 项目背景

MFish PLM系统采用多数据库混合架构设计，支撑以下业务场景：

- **复杂关系查询**：BOM层级、多跳关系追溯、影响范围分析等
- **高并发写入**：产品数据、部件数据、文档数据的高并发写入
- **跨模块数据共享**：PLM模块需要访问其它模块（OAuth、Sys等）的数据
- **事件驱动同步**：PLM数据变更后异步同步到图数据库

### 1.2 设计目标

| 目标 | 说明 |
|-----|------|
| 职责分离 | 关系库存储主数据，图库存储关系拓扑 |
| 性能优化 | 图数据库加速复杂关系查询 |
| 事件驱动 | RocketMQ异步同步，保证事务性能 |
| 最终一致 | 图库允许短暂不一致，以性能换可用性 |
| 灵活扩展 | 支持单节点/集群Nebula配置 |

### 1.3 设计原则

1. **其它模块（除PLM和Graph外）**：读写都在MySQL中自己对应的数据库
2. **PLM模块**：数据读写在PostgreSQL和NebulaGraph，对MySQL的数据读写通过调用其它模块的Feign接口实现
3. **Graph模块**：接收MQ消息后对自己的NebulaGraph图数据库进行存储
4. **公共类**：PLM与Graph的公共类都在mf-common模块中

---

## 2. 整体架构设计

### 2.1 架构概览

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                              mfish-nocode 系统                                 │
│                                                                                │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │                          第一层：其它模块 (MySQL)                         │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │  │
│  │  │   mf-oauth  │  │   mf-sys    │  │ mf-workflow │  │  mf-storage │   │  │
│  │  │  (mf_oauth) │  │(mf_system)  │  │(mf_workflow)│  │  (存储文件)  │   │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │  │
│  │                                                                         │  │
│  │  ✅ 特点：所有读写操作都在 MySQL，自己数据库                               │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                      │                                        │
│                                      │ Feign接口调用                          │
│                                      ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │                    第二层：PLM模块 (PostgreSQL + NebulaGraph)             │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐   │  │
│  │  │                        PLM业务数据层 (PostgreSQL)                 │   │  │
│  │  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────────────┐  │   │  │
│  │  │  │   Product     │  │    Part      │  │   ContainsLink        │  │   │  │
│  │  │  │  (产品库)     │  │   (部件)     │  │   (包含关系)          │  │   │  │
│  │  │  └───────────────┘  └───────────────┘  └───────────────────────┘  │   │  │
│  │  └─────────────────────────────────────────────────────────────────┘   │  │
│  │                                      │                                  │  │
│  │                    事务提交成功 ──── │ ──── RocketMQ (plm-graph-sync)    │  │
│  │                                      ▼                                  │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐   │  │
│  │  │                       图数据库层 (NebulaGraph)                     │   │  │
│  │  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────────────┐  │   │  │
│  │  │  │  精简节点     │  │   完整边     │  │   图查询服务          │  │   │  │
│  │  │  │ (公共属性)   │  │ (关系+属性)  │  │ (路径/关系探索)       │  │   │  │
│  │  │  └───────────────┘  └───────────────┘  └───────────────────────┘  │   │  │
│  │  └─────────────────────────────────────────────────────────────────┘   │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                                                                │
└────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 数据流向

#### 2.2.1 写操作流程

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              写操作流程                                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────┐      ┌─────────────┐      ┌────────────┐                            │
│  │  前端   │ ──── │  mf-plm    │ ──── │ PostgreSQL │      ┌─────────────┐       │
│  │        │      │  业务逻辑   │      │  (事务)     │      │             │       │
│  └─────────┘      └──────┬──────┘      └────────────┘      │             │       │
│                           │                                  │             │       │
│                           │ 事务提交成功                       │             │       │
│                           ▼                                  │             │       │
│                    ┌─────────────┐                           │             │       │
│                    │  RocketMQ   │                           │             │       │
│                    │ plm-graph   │                           │             │       │
│                    └──────┬──────┘                           │             │       │
│                           │ 异步消费                          │             │       │
│                           ▼                                  │             │       │
│                    ┌─────────────┐                           │             │       │
│                    │  mf-graph   │                           │             │       │
│                    │  图同步服务  │ ──────────────────────────┼─────────────┘       │
│                    └─────────────┘                           │                   │
│                                                              ▼                   │
│                                                   ┌─────────────────┐            │
│                                                   │  NebulaGraph    │            │
│                                                   │    图数据库     │            │
│                                                   └─────────────────┘            │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### 2.2.2 读操作流程

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              读操作流程                                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────┐      ┌─────────────┐      ┌─────────────────────────────────────┐│
│  │  前端   │ ──── │  mf-plm    │ ──── │  混合查询引擎                        ││
│  │        │      │  查询接口   │      │  ① NebulaGraph 查路径+边属性         ││
│  └─────────┘      └──────┬──────┘      │  ② PostgreSQL 查节点业务属性         ││
│                           │             │  ③ 合并返回                          ││
│                           │             └─────────────────────────────────────┘│
│                           │                              │                      │
│                           ▼                              ▼                      │
│                    ┌─────────────┐                ┌─────────────┐               │
│                    │NebulaGraph  │                │ PostgreSQL  │               │
│                    └─────────────┘                └─────────────┘               │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### 2.2.3 跨模块调用流程

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            跨模块调用流程                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────┐      ┌─────────────┐      ┌─────────────────────────────────────┐│
│  │  mf-plm │ ──── │  Feign     │ ──── │  其它模块 (OAuth/Sys/Workflow等)   ││
│  │        │      │  接口调用   │      │  MySQL 各自数据库                    ││
│  └─────────┘      └─────────────┘      └─────────────────────────────────────┘│
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 模块职责划分

### 3.1 模块层次

| 模块层次 | 模块名称 | 数据存储 | 职责说明 |
|---------|---------|---------|---------|
| **其它模块** | mf-oauth | MySQL (mf_oauth) | 统一认证、用户管理 |
| | mf-sys | MySQL (mf_system) | 系统管理、字典、配置 |
| | mf-workflow | MySQL (mf_workflow) | 工作流引擎 |
| | mf-storage | MySQL (mf_storage) | 文件存储元数据 |
| | mf-nocode | MySQL (mf_nocode) | 低代码配置 |
| | mf-scheduler | MySQL (mf_scheduler) | 定时任务 |
| **PLM模块** | mf-plm | PostgreSQL (mf_plm) | PLM业务主数据存储 |
| **Graph模块** | mf-graph | NebulaGraph (plm_graph) | 图数据存储、关系查询 |

### 3.2 服务端口规划

| 服务名 | 端口范围 | 默认端口 |
|-------|---------|---------|
| mf-gateway | 11110-11119 | 11116 |
| mf-oauth | 11120-11129 | 11122 |
| mf-sys | 11130-11139 | 11132 |
| mf-storage | 11140-11149 | 11142 |
| mf-workflow | 11150-11159 | 11152 |
| mf-scheduler | 11160-11169 | 11162 |
| mf-nocode | 11170-11179 | 11172 |
| mf-plm | 11180-11189 | 11182 |
| mf-graph | 11190-11199 | 11192 |
| mf-monitor | 11120-11129 | 11121 |

---

## 4. 数据库配置设计

### 4.1 其它模块配置（保持不变）

| 服务 | 配置文件 | 数据库 | 状态 |
|-----|---------|-------|-----|
| mf-oauth | mf-oauth-dev.yml | MySQL (mf_oauth) | ✅ 正确 |
| mf-sys | mf-sys-dev.yml | MySQL (mf_system) | ✅ 正确 |
| mf-workflow | mf-workflow-dev.yml | MySQL (mf_workflow) | ✅ 正确 |
| mf-storage | mf-storage-dev.yml | MySQL (mf_storage) | ✅ 正确 |
| mf-scheduler | mf-scheduler-dev.yml | MySQL (mf_scheduler) | ✅ 正确 |
| mf-nocode | mf-nocode-dev.yml | MySQL (mf_nocode) | ✅ 正确 |

### 4.2 PLM模块配置（PostgreSQL）

**mf-plm-dev.yml** 配置：

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      druid:
        initial-size: 10
        min-idle: 10
        maxActive: 50
        maxWait: 60000
        timeBetweenEvictionRunsMillis: 60000
        minEvictableIdleTimeMillis: 300000
        validationQuery: SELECT 1
        testWhileIdle: true
        testOnBorrow: false
        testOnReturn: false
        poolPreparedStatements: true
        maxPoolPreparedStatementPerConnectionSize: 20
        filters: stat,slf4j
        connectionProperties: druid.stat.mergeSql\=true;druid.stat.slowSqlMillis\=5000
      datasource:
        master:
          # PostgreSQL - PLM业务数据主库
          driver-class-name: org.postgresql.Driver
          url: jdbc:postgresql://192.168.111.103:5432/mf_plm
          username: postgres
          password: postgres
```

### 4.3 Graph模块配置（PostgreSQL + MySQL + NebulaGraph）

**mf-graph-dev.yml** 配置：

```yaml
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
          # PostgreSQL - 读取PLM业务数据
          driver-class-name: org.postgresql.Driver
          url: jdbc:postgresql://192.168.111.103:5432/mf_plm
          username: postgres
          password: postgres
        slave:
          # MySQL - 读取OAuth组织架构数据（用于图关联）
          driver-class-name: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://192.168.111.103:3306/mf_oauth?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
          username: root
          password: "!QAZ2wsx"

rocketmq:
  consumer:
    nameServer: 192.168.111.103:9876
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

---

## 5. 公共类设计（mf-common）

### 5.1 公共模块职责

```
mf-common/
├── mf-common-ds/              # 多数据源注解
│   ├── annotation/
│   │   ├── Master.java       # 主库注解（写操作）
│   │   └── Slave.java         # 从库注解（读操作）
│   └── config/
│       ├── BatchSqlInjector.java
│       └── MybatisInterceptor.java
│
├── mf-common-plm/            # PLM公共实体
│   ├── bean/
│   │   ├── container/         # 容器实体（Product、Folder）
│   │   ├── doc/               # 文档实体（Document、DocumentMaster）
│   │   ├── folder/            # 文件夹实体
│   │   └── part/              # 部件实体（Part、PartMaster）
│   └── entity/
│       └── BaseLinkEntity.java # 关系实体基类
│
├── mf-common-graph/           # Graph公共类
│   ├── client/
│   │   └── NebulaClient.java  # NebulaGraph客户端封装
│   ├── model/
│   │   ├── node/
│   │   │   └── GraphNode.java # 图节点模型
│   │   └── edge/
│   │       └── GraphEdge.java # 图边模型
│   └── event/
│       └── GraphSyncEvent.java # 图同步事件
│
└── mf-common-api/            # Feign接口
    └── PLM数据查询接口
```

### 5.2 多数据源使用规范

```java
// PLM业务Service示例
public class ProductServiceImpl {
    
    @Autowired
    private ProductMapper productMapper;  // 使用@Master，默认PostgreSQL
    
    @Autowired
    private NebulaClient nebulaClient;    // 图数据库客户端
    
    /**
     * 创建产品 - 写操作
     * 使用@Master注解确保写操作走主库
     */
    @Master
    @Transactional(rollbackFor = Exception.class)
    public void createProduct(Product product) {
        // 1. 写入PostgreSQL
        productMapper.insert(product);
        
        // 2. 发送MQ消息到plm-graph-sync topic
        rocketMQTemplate.convertAndSend("plm-graph-sync", buildSyncEvent(product));
    }
    
    /**
     * 查询产品列表 - 读操作
     * 默认使用@Master，PostgreSQL读
     */
    public List<Product> getProducts() {
        return productMapper.selectList(null);
    }
}
```

---

## 6. MQ消息设计

### 6.1 RocketMQ Topic配置

| Topic | Producer | Consumer | 说明 |
|-------|----------|----------|------|
| plm-graph-sync | mf-plm | mf-graph | PLM数据变更同步到图数据库 |

### 6.2 消息格式设计

```java
@Data
@ApiModel("图数据库同步事件")
public class GraphSyncEvent {
    @ApiModelProperty("事件ID")
    private String eventId;
    
    @ApiModelProperty("事件类型")
    private String eventType;  // CREATE/UPDATE/DELETE/FULL_SYNC
    
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

### 6.3 同步策略

| 同步类型 | 触发条件 | 同步方式 | 数据范围 |
|---------|---------|---------|---------|
| 实时同步 | 数据变更 | RocketMQ | 单条数据 |
| 增量同步 | 定时任务 | RocketMQ | 指定时间范围内变更 |
| 全量同步 | 手动触发 | 直接调用 | 所有数据 |

---

## 7. 混合查询设计

### 7.1 查询流程

```
┌─────────────────────────────────────────────────────────────────┐
│                      混合查询流程                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 【图数据库查询】                                              │
│     ┌─────────────┐    ┌─────────────┐    ┌─────────────┐      │
│     │ NebulaGraph │───▶│ 查路径     │───▶│ 返回节点ID  │      │
│     │   查询      │    │ +边属性    │    │ +边属性     │      │
│     └─────────────┘    └─────────────┘    └──────┬──────┘      │
│                                                    │             │
│  2. 【关系数据库查询】                                     │             │
│     ┌─────────────┐    ┌─────────────┐    ┌────────▼────────┐  │
│     │ PostgreSQL │◀───│ 根据节点ID  │◀───│ 合并节点业务属性 │  │
│     │  查询       │    │ 批量查询    │    │                 │  │
│     └─────────────┘    └─────────────┘    └─────────────────┘  │
│                                                                  │
│  3. 【结果返回】                                                  │
│     ┌─────────────────────────────────────────────────────────┐ │
│     │                    完整查询结果                          │ │
│     │  路径 + 边属性 + 节点业务属性（从PG库获取）              │ │
│     └─────────────────────────────────────────────────────────┘ │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 查询服务实现

```java
@Service
public class GraphQueryService {
    
    @Autowired
    private NebulaClient nebulaClient;
    
    @Autowired
    private ProductMapper productMapper;
    
    @Autowired
    private PartMapper partMapper;
    
    /**
     * 查询产品的完整BOM层级
     * 1. NebulaGraph查路径和边属性
     * 2. PostgreSQL查节点业务属性
     * 3. 合并返回
     */
    public Object queryProductBOM(String productId) {
        // 1. 图数据库查询路径
        List<GraphPath> paths = nebulaClient.queryPaths(productId, "CONTAINS");
        
        // 2. 提取路径中的节点ID
        Set<String> nodeIds = extractNodeIds(paths);
        
        // 3. PostgreSQL批量查询节点业务属性
        Map<String, Object> nodeProperties = batchQueryNodeProperties(nodeIds);
        
        // 4. 合并边属性和节点属性
        return mergeResults(paths, nodeProperties);
    }
}
```

---

## 8. NebulaGraph数据模型

### 8.1 图空间配置

```yaml
nebula:
  space:
    name: plm_graph
    charset: utf8
    replica-factor: 1
    partition-num: 100
```

### 8.2 节点类型定义

| 节点类型 | 类型码 | 说明 |
|---------|-------|------|
| SsoOrg | SsoOrg | 组织 |
| Product | Product | 产品库 |
| Folder | Folder | 文件夹 |
| PartMaster | PartMaster | 部件主数据 |
| Part | Part | 部件小版本 |
| DocumentMaster | DocumentMaster | 文档主数据 |
| Document | Document | 文档小版本 |

### 8.3 边类型定义

| 边类型 | 说明 | 起始节点 → 目标节点 |
|-------|------|-------------------|
| ContainsLink | 包含关系 | SsoOrg→Product, Product→Folder, Folder→Folder, Product→PartMaster, Product→DocumentMaster |
| PartVersionLink | 部件版本迭代关系 | PartMaster→Part |
| DocVersionLink | 文档版本迭代关系 | DocumentMaster→Document |

### 8.4 节点属性定义

所有节点都 extends BaseEntity，包含以下公共属性：

| 属性 | 类型 | 说明 |
|-----|------|------|
| id | String | 节点ID (对应关系库主键) |
| type | String | 节点类型 |
| createBy | String | 创建用户 |
| createTime | DateTime | 创建时间 |
| updateBy | String | 更新用户 |
| updateTime | DateTime | 更新时间 |

### 8.5 边属性定义

所有边都 extends BaseLinkEntity，包含以下公共属性：

| 属性 | 类型 | 说明 |
|-----|------|------|
| id | String | 边ID |
| type | String | 边类型 |
| fromId | String | 起始节点ID |
| fromType | String | 起始节点类型 |
| toId | String | 目标节点ID |
| toType | String | 目标节点类型 |
| createBy | String | 创建用户 |
| createTime | DateTime | 创建时间 |
| updateBy | String | 更新用户 |
| updateTime | DateTime | 更新时间 |
| properties | Map | PG库Link表的业务属性 |

---

## 9. 错误处理与容灾

### 9.1 消息队列容灾

| 策略 | 说明 |
|-----|------|
| 死信队列 | enableDLQ: true，失败消息进入DLQ |
| 重试机制 | maxRetryTimes: 3，最多重试3次 |
| 定时重试 | 失败消息定时重新消费 |

### 9.2 图数据库容灾

| 策略 | 说明 |
|-----|------|
| 单节点模式 | single.enabled: true |
| 集群模式 | cluster.enabled: true，配置多个地址 |
| 连接池 | min-conns: 10, max-conns: 100 |
| 超时配置 | timeout: 3000ms |

### 9.3 数据一致性策略

| 策略 | 说明 |
|-----|------|
| 最终一致 | 图库允许短暂不一致 |
| 补偿机制 | 全量同步可手动触发 |
| 监控告警 | 同步失败告警通知 |

---

## 10. 配置检查清单

### 10.1 当前配置状态

| 检查项 | 当前状态 | 期望状态 | 是否正确 |
|-------|---------|---------|---------|
| mf-start-graph PostgreSQL配置 | PostgreSQL | PostgreSQL | ✅ |
| mf-start-graph MySQL配置 | MySQL OAuth (slave) | MySQL OAuth | ✅ |
| mf-start-graph Nebula配置 | NebulaGraph | NebulaGraph | ✅ |
| mf-start-plm PostgreSQL配置 | PostgreSQL | PostgreSQL | ✅ |
| RocketMQ配置 | plm-graph-sync | plm-graph-sync | ✅ |

### 10.2 验证步骤

1. **启动其它模块服务**：验证MySQL数据库连接正常
2. **启动PLM模块**：验证PostgreSQL数据库连接正常
3. **启动Graph模块**：验证PostgreSQL、NebulaGraph、RocketMQ连接正常
4. **数据写入测试**：PLM写入数据，验证MQ消息发送
5. **图同步测试**：Graph消费MQ消息，验证图数据写入
6. **混合查询测试**：验证图库+关系库混合查询结果

---

## 11. 架构优势总结

| 特性 | 说明 |
|-----|------|
| **职责分离** | 关系库存主数据，图库存关系拓扑 |
| **性能优化** | 图数据库加速复杂关系查询 |
| **事件驱动** | RocketMQ异步同步，保证事务性能 |
| **最终一致** | 图库允许短暂不一致，以性能换可用性 |
| **灵活扩展** | 支持单节点/集群Nebula配置 |
| **模块解耦** | PLM与Graph通过MQ解耦 |
| **跨模块协作** | 通过Feign接口调用其它模块 |

---

**文档编写日期**：2026-04-17
**文档版本**：v1.0
