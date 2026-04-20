# PG → NebulaGraph CDC 同步方案设计

**日期**: 2026-04-20
**状态**: 已批准

## 1. 背景与目标

为 PLM 系统实现 PostgreSQL 到 NebulaGraph 的实时数据同步，通过 CDC（Change Data Capture）技术捕获 PG 数据库变更，实时同步到图数据库，支持 PLM 业务数据的图谱化展示与关联分析。

## 2. 技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| 源数据库 | PostgreSQL v16 | PLM 业务库 |
| 目标数据库 | NebulaGraph v3.8.0 | 图数据库 |
| CDC 工具 | Debezium Server | 轻量级独立进程 |
| 消息中间件 | RocketMQ | 项目已有依赖 |
| 同步框架 | mf-graph 模块 | 现有图同步服务 |

**架构路线**: PG → Debezium（CDC）→ RocketMQ → 同步服务 → NebulaGraph

## 3. 数据模型规则

### 3.1 同步范围

- 同步所有继承 `cn.com.mfish.common.core.entity.BaseEntity` 的实体类
- 必须有 `@TableName` 注解
- 表名为 `@TableName` 注解的值

### 3.2 点（Vertex）规则

- 实体类名**不以** `Link` 结尾的表 → 点
- 点只同步公共字段：`id`, `type`, `create_by`, `create_time`, `update_by`, `update_time`
- `type` 字段值为节点名称（类名，小写）

### 3.3 边（Edge）规则

- 实体类名**以** `Link` 结尾的表 → 边
- 边只同步公共字段：`id`, `type`, `create_by`, `create_time`, `update_by`, `update_time`, `from_id`, `from_type`, `to_id`, `to_type`
- `type` 字段值为边名称（类名，小写）
- `from_id`/`from_type`：起始点的 ID 和类型
- `to_id`/`to_type`：终止点的 ID 和类型

## 4. PG 配置

### 4.1 逻辑复制配置

```sql
-- 修改 postgresql.conf
wal_level = logical
max_replication_slots = 10
max_wal_senders = 10

-- 创建复制槽
CREATE SLOT plm_graph_slot LOGICAL 'pgoutput';

-- 创建发布（白名单表）
CREATE PUBLICATION plm_graph_pub FOR TABLE
  part, document, product, folder,
  part_master, iterate_link, contains_link;
```

### 4.2 白名单表过滤

在 Debezium 配置中指定表名正则匹配，只同步需要的表。

## 5. 组件设计

### 5.1 组件列表

| 组件 | 职责 | 包路径 |
|------|------|--------|
| DebeziumMsgConsumer | 消费 Debezium CDC 消息，解析 before/after/op | `cn.com.mfish.graph.sync.consumer` |
| EventTransformService | CDC 消息 → GraphSyncEvent 转换，动态判断点/边 | `cn.com.mfish.graph.sync.service` |
| TableMetadataResolver | 解析实体类元数据（表名、点/边类型、字段映射） | `cn.com.mfish.graph.sync.metadata` |
| EntityMapping | 实体映射定义（类名 ↔ 表名 ↔ 图类型） | `cn.com.mfish.graph.sync.metadata` |
| FullSyncController | 全量同步手动触发 API | `cn.com.mfish.graph.sync.controller` |
| FullSyncService | 扫描实体类，执行全量同步 | `cn.com.mfish.graph.sync.service` |

### 5.2 已有组件复用

- `GraphSyncConsumer`：RocketMQ 消息消费（已有）
- `NebulaWriteService`：图数据库写入（已有）
- `IdempotentService`：幂等性检查（已有）
- `SyncLogService`：同步日志（已有）

## 6. 数据转换流程

```
Debezium CDC 消息
      │
      ▼
┌─────────────────────────────────┐
│ DebeziumMsgConsumer            │
│ - 解析 op (c/u/d/r)            │
│ - 提取 before/after 数据       │
└─────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────┐
│ EventTransformService          │
│ - 根据表名判断点/边类型         │
│ - 映射到 GraphSyncEvent        │
│ - op='c'/'u' → CREATE/UPDATE  │
│ - op='d' → DELETE              │
└─────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────┐
│ 幂等性检查 (IdempotentService)  │
│ - 检查事件是否已处理           │
└─────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────┐
│ NebulaWriteService (已有)       │
│ - upsertNodesAndEdges          │
│ - deleteNodesAndEdges          │
└─────────────────────────────────┘
```

## 7. 文件结构

```
mf-graph/src/main/java/cn/com/mfish/graph/
├── sync/
│   ├── consumer/
│   │   └── DebeziumMsgConsumer.java      # 新增：消费 Debezium 消息
│   ├── controller/
│   │   └── FullSyncController.java        # 新增：全量同步 API
│   ├── service/
│   │   ├── EventTransformService.java     # 新增：CDC → GraphSyncEvent 转换
│   │   └── FullSyncService.java          # 新增：全量同步服务
│   └── metadata/
│       ├── TableMetadataResolver.java     # 新增：实体类元数据解析
│       └── EntityMapping.java            # 新增：实体映射定义
```

## 8. 全量同步

### 8.1 触发方式

手动触发，提供 API 接口：

```
POST /api/graph/sync/full
{
  "tables": ["part", "document"],  // 可选，指定表；为空则同步所有
  "batchSize": 1000                  // 可选，每批数量，默认 1000
}
```

### 8.2 执行流程

1. 启动时检测是否需要全量同步（可配置）
2. 扫描所有继承 BaseEntity 的实体类
3. 按表分批读取 PG 数据
4. 生成 GraphSyncEvent 写入 RocketMQ
5. 由现有消费服务写入 NebulaGraph

## 9. 监控与日志

采用最小化监控策略：

- 处理失败时记录详细日志（`SyncLogService`）
- 日志包含：eventId、eventType、节点/边数量、处理时长、错误信息
- 不进行主动告警，通过日志分析问题

## 10. 依赖说明

### 10.1 已有依赖

- `postgresql` 驱动（pom.xml 已配置）
- `rocketmq-spring-boot-starter`（pom.xml 已配置）
- `vesoft/client` NebulaGraph 客户端（pom.xml 已配置）

### 10.2 新增依赖

Debezium Server 使用独立进程，无需在项目中添加 Debezium 依赖。

如需在项目中解析 Debezium 消息格式，可添加：

```xml
<dependency>
    <groupId>io.debezium</groupId>
    <artifactId>debezium-embedded</artifactId>
    <version>2.4.0.Final</version>
</dependency>
```

## 11. 配置项

```yaml
# application.yml
debezium:
  consumer:
    topic: plm-graph-cdc          # Debezium 消息 topic
    group: plm-graph-debezium-group
  server:
    host: localhost
    port: 8080

graph:
  sync:
    full:
      batch-size: 1000            # 全量同步每批数量
      enabled: false             # 启动时是否自动全量同步
```

## 12. 风险与限制

1. **Debezium Server 部署**：需额外部署 Debezium Server 进程
2. **PG 逻辑复制**：需确保 wal_level=logical
3. **初始同步**：大数据量时耗时较长，需在维护窗口执行
4. **事务边界**：CDC 消息可能乱序，消费服务需保证幂等
