# PLM 数据同步到 NebulaGraph 图数据库设计方案

**版本**: v1.0
**日期**: 2026-04-20
**状态**: 待审核

---

## 1. 概述

### 1.1 项目背景

基于 mfish 平台的 PLM 模块，需要将存储在 PostgreSQL 16 的 PLM 业务数据同步到 NebulaGraph 3.8.0 图数据库，实现 PLM 数据的图谱化存储与查询能力。

### 1.2 同步架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           整体架构                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌──────────┐    ┌──────────────┐    ┌─────────┐    ┌──────────────┐  │
│   │    PG    │───→│ Debezium     │───→│ RocketMQ│───→│ mf-graph     │  │
│   │  16      │    │ Embedded     │    │ 4.9.7   │    │ sync 服务    │  │
│   │          │    │              │    │         │    │              │  │
│   │ mf_plm   │    │ 捕获 WAL     │    │ Topic:  │    │ 解析消息     │  │
│   │          │    │ 逻辑复制     │    │ plm-    │    │ 生成点/边    │  │
│   │          │    │              │    │ graph-  │    │ UPSERT       │  │
│   │          │    │              │    │ sync    │    │              │  │
│   └──────────┘    └──────────────┘    └─────────┘    └──────┬───────┘  │
│                                                            │          │
│                                                            ▼          │
│   ┌──────────┐    ┌──────────────┐    ┌──────────────────────────┐   │
│   │  Nebula  │←───│   NgBatis    │←───│  sync_failed_records     │   │
│   │  Graph   │    │  1.3.0-jdk17 │    │  (同步失败表)             │   │
│   │  3.8.0   │    │ +nebula-java │    │                           │   │
│   │          │    │   3.8.4      │    │                           │   │
│   └──────────┘    └──────────────┘    └──────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.3 方案要素汇总

| 要素 | 选择 |
|------|------|
| **同步范围** | 自定义：Part、Document、DocumentMaster、Folder、PartMaster、Product（点）+ ContainsLink、IteraiteLink（边） |
| **同步策略** | 全量 + 增量 CDC |
| **CDC 方案** | Debezium Embedded（嵌入 mf-start-graph 应用） |
| **消息队列** | RocketMQ（已有基础设施） |
| **PG 连接** | TCP 直连 + 逻辑复制 |
| **图数据库** | NebulaGraph 3.8.0 |
| **图客户端** | NgBatis 1.3.0-jdk17 + nebula-java 3.8.4 |
| **错误处理** | 幂等 UPSERT + sync_failed_records 失败表 |
| **Tag/Edge** | 动态 type = 类名（100+ 种类型） |

---

## 2. 同步实体定义

### 2.1 点实体（Vertex）

| 实体类名 | 表名 | type 值 | 说明 |
|----------|------|---------|------|
| Part | wPart | Part | 部件小版本 |
| Document | document | Document | 文档小版本 |
| DocumentMaster | document_master | DocumentMaster | 文档主数据 |
| Folder | folder | Folder | 文件夹 |
| PartMaster | part_master | PartMaster | 部件主数据 |
| Product | product | Product | 产品库 |

### 2.2 边实体（Edge）

| 实体类名 | 表名 | type 值 | from/to 字段 |
|----------|------|---------|--------------|
| ContainsLink | contains_link | ContainsLink | fromId/fromType → toId/toType |
| IteraiteLink | iteraite_link | IteraiteLink | fromId/fromType → toId/toType |

### 2.3 Link 表结构规范

所有 Link 表必须包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) | 唯一ID |
| type | VARCHAR(100) | 边的类型（Edge 名） |
| fromId | VARCHAR(36) | 起始点 ID |
| fromType | VARCHAR(100) | 起始点类型 |
| toId | VARCHAR(36) | 目标点 ID |
| toType | VARCHAR(100) | 目标点类型 |

---

## 3. NebulaGraph Schema 设计

### 3.1 Space 配置

```ngql
CREATE SPACE IF NOT EXISTS plm_graph (
    partition_num = 100,
    replica_factor = 1,
    charset = utf8,
    collate = utf8_bin,
    vid_type = FIXED_STRING(32)
);

```

### 3.2 Tag 定义（动态 type = 100+ 种）

```ngql
# 点类型示例（实际根据业务动态创建）
-- Part 部件小版本
CREATE TAG IF NOT EXISTS WPart( 
    id STRING NOT NULL, 
    type STRING, 
    create_by STRING, 
    create_time TIMESTAMP, 
    update_by STRING, 
    update_time TIMESTAMP 
);

-- PartMaster 部件主数据
CREATE TAG IF NOT EXISTS WPartMaster( 
    id STRING NOT NULL, 
    type STRING, 
    create_by STRING, 
    create_time TIMESTAMP, 
    update_by STRING, 
    update_time TIMESTAMP 
);

-- Document 文档小版本
CREATE TAG IF NOT EXISTS Document( 
    id STRING NOT NULL, 
    type STRING, 
    create_by STRING, 
    create_time TIMESTAMP, 
    update_by STRING, 
    update_time TIMESTAMP 
);

-- DocumentMaster 文档主数据
CREATE TAG IF NOT EXISTS DocumentMaster( 
    id STRING NOT NULL, 
    type STRING, 
    create_by STRING, 
    create_time TIMESTAMP, 
    update_by STRING, 
    update_time TIMESTAMP 
);

-- Folder 文件夹
CREATE TAG IF NOT EXISTS Folder( 
    id STRING NOT NULL, 
    type STRING, 
    create_by STRING, 
    create_time TIMESTAMP, 
    update_by STRING, 
    update_time TIMESTAMP 
);

-- Product 产品库
CREATE TAG IF NOT EXISTS Product( 
    id STRING NOT NULL, 
    type STRING, 
    create_by STRING, 
    create_time TIMESTAMP, 
    update_by STRING, 
    update_time TIMESTAMP 
);

CREATE TAG INDEX IF NOT EXISTS idx_wpart_id ON WPart(id(32));
CREATE TAG INDEX IF NOT EXISTS idx_wpartmaster_id ON WPartMaster(id(32));
CREATE TAG INDEX IF NOT EXISTS idx_document_id ON Document(id(32));
CREATE TAG INDEX IF NOT EXISTS idx_documentmaster_id ON DocumentMaster(id(32));
CREATE TAG INDEX IF NOT EXISTS idx_folder_id ON Folder(id(32));
CREATE TAG INDEX IF NOT EXISTS idx_product_id ON Product(id(32));

REBUILD TAG INDEX idx_wpart_id;
REBUILD TAG INDEX idx_wpartmaster_id;
REBUILD TAG INDEX idx_document_id;
REBUILD TAG INDEX idx_documentmaster_id;
REBUILD TAG INDEX idx_folder_id;
REBUILD TAG INDEX idx_product_id;

# 其他 Tag 类似...
# 总计约 100+ 个动态 Tag
```

### 3.3 Edge 定义

```ngql
-- 创建 ContainsLink 边（3.x 正确语法）
CREATE EDGE IF NOT EXISTS ContainsLink(
    id STRING NOT NULL,
    type STRING,
    from_id STRING,
    from_type STRING,
    to_id STRING,
    to_type STRING,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

-- 创建 IteraiteLink 边（3.x 正确语法）
CREATE EDGE IF NOT EXISTS IteraiteLink(
    id STRING NOT NULL,
    type STRING,
    from_id STRING,
    from_type STRING,
    to_id STRING,
    to_type STRING,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

# ContainsLink 边索引（id/fromId/toId 全部创建，字符串指定索引长度32）
CREATE EDGE INDEX IF NOT EXISTS idx_contains_id ON ContainsLink(id(32));
CREATE EDGE INDEX IF NOT EXISTS idx_contains_fromid ON ContainsLink(from_id(32));
CREATE EDGE INDEX IF NOT EXISTS idx_contains_toid ON ContainsLink(to_id(32));

# IteraiteLink 边索引
CREATE EDGE INDEX IF NOT EXISTS idx_iterate_id ON IteraiteLink(id(32));
CREATE EDGE INDEX IF NOT EXISTS idx_iterate_fromid ON IteraiteLink(from_id(32));
CREATE EDGE INDEX IF NOT EXISTS idx_iterate_toid ON IteraiteLink(to_id(32));

REBUILD EDGE INDEX idx_contains_id;
REBUILD EDGE INDEX idx_contains_fromid;
REBUILD EDGE INDEX idx_contains_toid;

REBUILD EDGE INDEX idx_iterate_id;
REBUILD EDGE INDEX idx_iterate_fromid;
REBUILD EDGE INDEX idx_iterate_toid;

```

---

## 4. 模块结构设计

### 4.1 目录结构

```
mf-business/mf-graph/
├── pom.xml                          # 需添加 Debezium、NgBatis 依赖
└── src/main/java/cn/com/mfish/graph/
    └── sync/                         # 同步包
        ├── cdc/                      # CDC 相关
        │   ├── DebeziumRunner.java   # 启动/停止 Debezium Embedded
        │   └── PgCdcConfig.java      # PG CDC 配置
        ├── consumer/                 # MQ 消费相关
        │   └── SyncMessageConsumer.java  # 消费 CDC 消息
        ├── service/                  # 同步核心逻辑
        │   ├── SyncService.java      # 同步服务接口
        │   ├── impl/SyncServiceImpl.java
        │   ├── full/                 # 全量同步
        │   │   └── FullSyncRunner.java
        │   └── incremental/         # 增量同步
        │       └── IncrementalSyncHandler.java
        ├── graph/                    # 图数据库操作
        │   ├── GraphOperationService.java
        │   └── impl/GraphOperationServiceImpl.java
        ├── model/                   # 数据模型
        │   ├── CdcEvent.java        # CDC 事件
        │   ├── VertexInfo.java       # 点信息
        │   └── EdgeInfo.java        # 边信息
        └── fail/                     # 失败处理
            ├── FailedRecordService.java
            └── FailedRecordMapper.java
```

### 4.2 启动模块

- **启动类**: `mf-start-graph/src/main/java/cn/com/mfish/graph/MfGraphApplication.java`
- **配置文件**: `mf-start-graph/src/main/resources/mf-graph-dev.yml`

---

## 5. 数据库设计

### 5.1 同步失败表

**表名**: `sync_failed_records`

```sql
CREATE TABLE sync_failed_records (
    id              BIGSERIAL PRIMARY KEY,
    table_name      VARCHAR(100)  NOT NULL,           -- 来源表名
    operation_type  VARCHAR(20)   NOT NULL,           -- INSERT/UPDATE/DELETE
    payload         JSONB        NOT NULL,            -- 原始 CDC 消息
    error_message   TEXT,                             -- 错误信息
    retry_count     INT         DEFAULT 0,            -- 重试次数
    status          VARCHAR(20) DEFAULT 'PENDING',    -- PENDING/PROCESSED
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    process_time    TIMESTAMP
);

COMMENT ON TABLE sync_failed_records IS 'PLM同步NebulaGraph失败记录表';
COMMENT ON COLUMN sync_failed_records.table_name IS '来源表名';
COMMENT ON COLUMN sync_failed_records.operation_type IS '操作类型';
COMMENT ON COLUMN sync_failed_records.payload IS '原始CDC消息';
COMMENT ON COLUMN sync_failed_records.error_message IS '错误信息';
COMMENT ON COLUMN sync_failed_records.retry_count IS '重试次数';
COMMENT ON COLUMN sync_failed_records.status IS '处理状态';
```

---

## 6. 配置设计

### 6.1 pom.xml 依赖

```xml
<!-- Debezium Embedded -->
<dependency>
    <groupId>io.debezium</groupId>
    <artifactId>debezium-api</artifactId>
    <version>${debezium.version}</version>
</dependency>
<dependency>
    <groupId>io.debezium</groupId>
    <artifactId>debezium-embedded</artifactId>
    <version>${debezium.version}</version>
</dependency>
<dependency>
    <groupId>io.debezium</groupId>
    <artifactId>debezium-storage-file</artifactId>
    <version>${debezium.version}</version>
</dependency>

<!-- PostgreSQL Connector for Debezium -->
<dependency>
    <groupId>io.debezium</groupId>
    <artifactId>debezium-connector-postgres</artifactId>
    <version>${debezium.version}</version>
</dependency>

<!-- NgBatis -->
<dependency>
    <groupId>com.nebula-graph</groupId>
    <artifactId>ngbatis</artifactId>
    <version>1.3.0-jdk17</version>
</dependency>

<!-- NebulaGraph Java Client -->
<dependency>
    <groupId>com.vesoft</groupId>
    <artifactId>client</artifactId>
    <version>3.8.4</version>
</dependency>
```

### 6.2 mf-graph-dev.yml 配置

```yaml
debezium:
  enabled: true
  connector:
    host: 192.168.111.103
    port: 5432
    database: mf_plm
    username: replication_user
    password: your_password
    slot: mf_plm_slot
    publication: mf_plm_publication
    tables:
      - wPart
      - document
      - document_master
      - folder
      - part_master
      - product
      - contains_link
      - iteraite_link

rocketmq:
  name-server: 192.168.111.103:9876
  consumer:
    group: plm-graph-sync-group
    topic: plm-graph-sync
    enableDLQ: true
    maxRetryTimes: 3

nebula:
  single:
    enabled: true
    addresses: 192.168.111.103:9669
  username: root
  password: nebula
  space:
    name: plm_graph

sync:
  full:
    enabled: true
    batch-size: 1000
  retry:
    max-times: 3
    interval-ms: 5000
```

---

## 7. PostgreSQL 16 配置步骤

### 7.1 修改 postgresql.conf

```bash
# 位置: /var/lib/pgsql/data/postgresql.conf (RHEL)

wal_level = logical
max_replication_slots = 10
max_wal_senders = 10
wal_keep_size = 1GB
```

### 7.2 修改 pg_hba.conf

```bash
# 位置: /var/lib/pgsql/data/pg_hba.conf

# 允许复制连接
host    replication     all     0.0.0.0/0     md5
host    mf_plm         replication_user   0.0.0.0/0     md5
```

### 7.3 创建复制用户

```sql
-- 以 postgres 管理员执行
CREATE USER replication_user WITH REPLICATION ENCRYPTED PASSWORD 'postgres';
GRANT CONNECT ON DATABASE mf_plm TO replication_user;
GRANT USAGE ON SCHEMA public TO replication_user;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO replication_user;
```

### 7.4 创建复制槽和 Publication

```sql
-- 创建复制槽
SELECT * FROM pg_create_logical_replication_slot('mf_plm_slot', 'pgoutput');

-- 创建 Publication（包含所有需要同步的表）
CREATE PUBLICATION mf_plm_publication FOR TABLE
    w_part,
    w_part_master,
    document,
    document_master,
    folder,
    product,
    contains_link,
    iteraite_link;

ALTER PUBLICATION mf_plm_publication
ADD TABLE
  public.product (id,type,create_by,create_time,update_by,update_time),
  public.document (id,type,create_by,create_time,update_by,update_time),
  public.document_master (id,type,create_by,create_time,update_by,update_time),
  public.folder (id,type,create_by,create_time,update_by,update_time),
  public.w_part (id,type,create_by,create_time,update_by,update_time),
  public.w_part_master (id,type,create_by,create_time,update_by,update_time),
  public.contains_link (id,type,from_id,from_type,to_id,to_type,create_by,create_time,update_by,update_time),
  public.iteraite_link (id,type,from_id,from_type,to_id,to_type,create_by,create_time,update_by,update_time);

    
```

### 7.5 验证配置

```sql
-- 检查复制槽
SELECT * FROM pg_replication_slots;

-- 检查复制状态
SELECT * FROM pg_stat_replication;

-- 检查 Publication
SELECT * FROM pg_publication_tables;
-- 检查 wPart 表的 REPLICA IDENTITY
ALTER TABLE w_part REPLICA IDENTITY DEFAULT;
ALTER TABLE w_part_master REPLICA IDENTITY DEFAULT;
ALTER TABLE document REPLICA IDENTITY DEFAULT;
ALTER TABLE document_master REPLICA IDENTITY DEFAULT;
ALTER TABLE folder REPLICA IDENTITY DEFAULT;
ALTER TABLE product REPLICA IDENTITY DEFAULT;
ALTER TABLE contains_link REPLICA IDENTITY DEFAULT;
ALTER TABLE iteraite_link REPLICA IDENTITY DEFAULT;



-- 修复
-- 停止应用

-- 删除旧复制槽
SELECT pg_drop_replication_slot('mf_plm_slot');

-- 重新创建复制槽（从最新位置开始）
SELECT * FROM pg_create_logical_replication_slot('mf_plm_slot', 'pgoutput');

-- 确认
SELECT slot_name, confirmed_flush_lsn FROM pg_replication_slots WHERE slot_name = 'mf_plm_slot';
```

---

## 8. 核心流程设计

### 8.1 全量同步流程

```
1. 启动应用
2. 检查是否需要全量同步（配置 sync.full.enabled）
3. 遍历所有同步表
4. 分批查询 PG 数据（batch-size=1000）
5. 转换为 VertexInfo/EdgeInfo
6. 执行 UPSERT 到 NebulaGraph
7. 记录同步进度
8. 全量同步完成后，开启增量 CDC
```

### 8.2 增量 CDC 流程

```
1. Debezium Embedded 连接到 PG
2. 监听 WAL 日志变化
3. 捕获 INSERT/UPDATE/DELETE 事件
4. 序列化为 CDC 消息
5. 发送到 RocketMQ
6. SyncMessageConsumer 消费消息
7. 解析事件类型和数据
8. 调用 GraphOperationService 执行 UPSERT
9. 失败则写入 sync_failed_records
```

### 8.3 幂等处理

- **点（Vertex）**: 使用 `UPSERT VERTEX` 语法，相同 ID 会被覆盖
- **边（Edge）**: 使用 `UPSERT EDGE` 语法，唯一索引为 `fromId + toId + rank`

---

## 9. 错误处理设计

### 9.1 重试机制

| 场景 | 策略 |
|------|------|
| 网络瞬断 | 重试 3 次，间隔 5 秒 |
| 图数据库不可用 | 暂停 CDC，触发告警 |
| 消息解析失败 | 直接写入失败表，不重试 |

### 9.2 失败表处理

- 管理员定期查询 `sync_failed_records` 表
- 分析错误原因
- 手工修复后更新 status 为 PROCESSED
- 或编写修复脚本批量处理

---

## 10. 风险与注意事项

### 10.1 NebulaGraph Schema 管理

- 100+ 个动态 Tag 需要预先创建
- 建议编写脚本自动同步 PG 表结构到 NebulaGraph
- 新增 type 类型需要同时更新 Schema

### 10.2 数据一致性

- Debezium 提供 at-least-once 语义
- 通过幂等 UPSERT 保证最终一致性
- 极端情况需要人工介入

### 10.3 性能考虑

- 全量同步建议在业务低峰期执行
- 批处理大小可根据集群性能调整
- 监控 NebulaGraph 查询延迟

---

## 11. 实现计划概要

| 阶段 | 内容 |
|------|------|
| **阶段一** | PG 环境配置（逻辑复制、用户、Publication） |
| **阶段二** | NebulaGraph Schema 初始化（100+ Tag + 2 Edge） |
| **阶段三** | mf-graph 同步服务开发（Debezium + RocketMQ Consumer） |
| **阶段四** | NgBatis 图数据库操作层开发 |
| **阶段五** | 全量同步功能开发 |
| **阶段六** | 增量 CDC 同步功能开发 |
| **阶段七** | 失败记录表及告警机制 |
| **阶段八** | 测试与调优 |

---

## 12. 参考资料

- [Debezium Embedded Documentation](https://debezium.io/documentation/reference/stable/embedded.html)
- [Debezium PostgreSQL Connector](https://debezium.io/documentation/reference/stable/connectors/postgresql.html)
- [NebulaGraph Schema Design](https://docs.nebula-graph.com.cn/)
- [NgBatis Documentation](https://ngbatis.apache.org/)
