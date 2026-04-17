# MFish PLM 系统多数据库架构设计

## 文档信息

| 项目 | 内容 |
|-----|------|
| 项目名称 | mfish-nocode PLM 多数据库架构 |
| 当前版本 | mf-2.3.1 |
| 更新日期 | 2026-04-17 |
| 文档状态 | 已确认，待实施 |
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

rocketmq:
  producer:
    nameServer: 192.168.111.103:9876
    topic: plm-graph-sync
    group: plm-producer-group
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
│   │   └── Slave.java        # 从库注解（读操作）
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
```

### 5.2 PLM公共实体（mf-common-plm）

#### 5.2.1 容器类

| 实体类 | 说明 | 主键 |
|-------|------|------|
| Product | 产品 | id |
| Folder | 文件夹 | id |

#### 5.2.2 部件类

| 实体类 | 说明 | 主键 |
|-------|------|------|
| Part | 部件 | id |
| PartMaster | 部件主数据 | id |
| PartVersionLink | 部件版本关系 | id |

#### 5.2.3 文档类

| 实体类 | 说明 | 主键 |
|-------|------|------|
| Document | 文档 | id |
| DocumentMaster | 文档主数据 | id |
| DocVersionLink | 文档版本关系 | id |

#### 5.2.4 关系类

| 实体类 | 说明 | 主键 |
|-------|------|------|
| ContainsLink | 包含关系 | id |

### 5.3 Graph公共类（mf-common-graph）

#### 5.3.1 图节点模型

```java
public class GraphNode {
    private String id;           // 节点ID（对应业务ID）
    private String type;         // 节点类型（Product/Part/Document/Folder）
    private Map<String, Object> properties; // 精简属性
    private Long createTime;     // 创建时间
}
```

#### 5.3.2 图边模型

```java
public class GraphEdge {
    private String sourceId;      // 源节点ID
    private String targetId;      // 目标节点ID
    private String edgeType;      // 边类型（CONTAINS/PART_OF/VERSION_OF）
    private Map<String, Object> properties; // 边属性
    private Long createTime;     // 创建时间
}
```

---

## 6. RocketMQ 消息设计

### 6.1 Topic 配置

| Topic名称 | Producer | Consumer | 说明 |
|----------|----------|----------|------|
| plm-graph-sync | mf-plm | mf-graph | PLM数据变更同步到图数据库 |

### 6.2 消息格式

```json
{
  "eventType": "CREATE/UPDATE/DELETE",
  "entityType": "Product/Part/Document/ContainsLink",
  "entityId": "xxx",
  "data": { ... },
  "timestamp": 1713340800000
}
```

### 6.3 消息发送时机

| 操作 | 发送时机 | 消息类型 |
|------|---------|---------|
| 创建实体 | 事务提交成功后 | CREATE |
| 更新实体 | 事务提交成功后 | UPDATE |
| 删除实体 | 事务提交成功后 | DELETE |
| 创建关系 | 事务提交成功后 | CREATE |
| 删除关系 | 事务提交成功后 | DELETE |

---

## 7. NebulaGraph Schema 设计

### 7.1 Space 配置

```sql
CREATE SPACE IF NOT EXISTS plm_graph
(partition_num = 100,
replica_factor = 1,
charset = utf8);
```

### 7.2 Tag 定义

```sql
-- 产品节点
CREATE TAG IF NOT EXISTS Product(
    id VARCHAR(64) NOT NULL,
    name VARCHAR(255),
    code VARCHAR(100),
    create_time TIMESTAMP
);

-- 部件节点
CREATE TAG IF NOT EXISTS Part(
    id VARCHAR(64) NOT NULL,
    name VARCHAR(255),
    code VARCHAR(100),
    create_time TIMESTAMP
);

-- 文档节点
CREATE TAG IF NOT EXISTS Document(
    id VARCHAR(64) NOT NULL,
    name VARCHAR(255),
    code VARCHAR(100),
    create_time TIMESTAMP
);

-- 文件夹节点
CREATE TAG IF NOT EXISTS Folder(
    id VARCHAR(64) NOT NULL,
    name VARCHAR(255),
    create_time TIMESTAMP
);
```

### 7.3 Edge Type 定义

```sql
-- 包含关系（容器 → 内容）
CREATE EDGE IF NOT EXISTS CONTAINS(
    id VARCHAR(64) NOT NULL,
    create_time TIMESTAMP
);

-- BOM关系（父部件 → 子部件）
CREATE EDGE IF NOT EXISTS BOM(
    id VARCHAR(64) NOT NULL,
    quantity INT DEFAULT 1,
    create_time TIMESTAMP
);

-- 版本关系（主数据 → 版本）
CREATE EDGE IF NOT EXISTS VERSION_OF(
    id VARCHAR(64) NOT NULL,
    version VARCHAR(50),
    create_time TIMESTAMP
);
```

---

## 8. Feign 接口设计

### 8.1 PLM 调用其他模块的接口

PLM模块需要通过Feign调用其他模块获取数据，需要的接口：

| 调用方 | 被调用方 | 接口说明 |
|-------|---------|---------|
| mf-plm | mf-oauth | 获取用户信息、部门信息 |
| mf-plm | mf-sys | 获取字典数据 |

### 8.2 接口定义位置

| 接口 | 定义位置 | 使用场景 |
|-----|---------|---------|
| UserFeign | mf-api/mf-oauth-api | 获取用户详情 |
| DeptFeign | mf-api/mf-oauth-api | 获取部门信息 |
| DictFeign | mf-api/mf-sys-api | 获取字典数据 |

---

## 9. 改造步骤规划

### 阶段一：公共基础层改造

1. **mf-common-ds 模块完善**
   - 确认 @Master/@Slave 注解实现
   - 确认 BatchSqlInjector 配置
   - 确认 MybatisInterceptor 实现

2. **mf-common-graph 模块完善**
   - NebulaConfig 配置类
   - NebulaClient 客户端封装
   - RocketMQConfig 配置

3. **mf-common-plm 模块检查**
   - 检查实体类完整性
   - 检查 Mapper 接口

### 阶段二：mf-plm 模块改造

1. **pom.xml 依赖调整**
   - 添加 PostgreSQL Driver
   - 添加 RocketMQ Producer 依赖

2. **数据源配置**
   - 修改 mf-plm-dev.yml
   - 配置 PostgreSQL 数据源
   - 配置 RocketMQ Producer

3. **Feign 接口集成**
   - 引入 mf-oauth-api、mf-sys-api 依赖
   - 配置 FeignClient 扫描路径

4. **Service 层改造**
   - 添加消息发送逻辑
   - 支持多数据源查询

### 阶段三：mf-graph 模块完善

1. **pom.xml 依赖调整**
   - 确认 NebulaGraph Client 依赖
   - 确认 RocketMQ Consumer 依赖

2. **数据源配置**
   - 修改 mf-graph-dev.yml
   - 配置 PostgreSQL Master
   - 配置 MySQL Slave
   - 配置 NebulaGraph
   - 配置 RocketMQ Consumer

3. **消费者开发**
   - GraphSyncConsumer 实现
   - 消息处理逻辑

4. **图同步服务开发**
   - GraphSyncService 实现
   - NebulaGraph 写入逻辑

### 阶段四：测试验证

1. PLM CRUD 功能测试
2. RocketMQ 消息发送/消费测试
3. NebulaGraph 同步验证
4. 混合查询功能测试

---

## 10. 验收标准

### 10.1 功能验收

| 功能点 | 验收标准 |
|-------|---------|
| PLM数据持久化 | Product/Part/Document 等实体成功保存到 PostgreSQL |
| 消息发送 | 数据变更后成功发送 RocketMQ 消息 |
| 图同步 | mf-graph 成功消费消息并写入 NebulaGraph |
| 图查询 | NebulaGraph 查询返回正确的节点和关系 |
| Feign调用 | PLM 成功通过 Feign 获取其他模块数据 |

### 10.2 性能验收

| 指标 | 目标值 |
|-----|-------|
| PLM写操作响应时间 | ≤ 200ms（不含网络延迟） |
| 图同步延迟 | ≤ 5s |
| 图查询响应时间 | ≤ 100ms（单跳查询） |

---

## 11. 风险与对策

| 风险 | 影响 | 对策 |
|-----|------|------|
| NebulaGraph 单点故障 | 图查询不可用 | PLM 读操作降级为纯 PostgreSQL 查询 |
| RocketMQ 消息丢失 | 图数据不一致 | 启用 DLQ，死信队列人工处理 |
| PostgreSQL 性能瓶颈 | PLM 写入变慢 | 连接池调优，后续读写分离 |
| Feign 调用失败 | PLM 无法获取组织数据 | 降级策略，返回默认/空数据 |

---

## 12. 附录

### 12.1 中间件连接信息

| 中间件 | 地址 | 端口 | 用户名 | 密码 |
|-------|------|------|-------|------|
| PostgreSQL | 192.168.111.103 | 5432 | postgres | postgres |
| RocketMQ | 192.168.111.103 | 9876 | - | - |
| NebulaGraph | 192.168.111.103 | 9669 | root | nebula |

### 12.2 数据库列表

| 数据库名 | 类型 | 用途 |
|---------|------|------|
| mf_oauth | MySQL | 认证授权数据 |
| mf_system | MySQL | 系统管理数据 |
| mf_workflow | MySQL | 工作流数据 |
| mf_storage | MySQL | 文件存储元数据 |
| mf_scheduler | MySQL | 定时任务数据 |
| mf_nocode | MySQL | 低代码配置数据 |
| mf_plm | PostgreSQL | PLM业务主数据 |
| plm_graph | NebulaGraph | 图关系数据 |

---

**文档状态**：✅ 设计方案已确认，待实施
**最后更新**：2026-04-17