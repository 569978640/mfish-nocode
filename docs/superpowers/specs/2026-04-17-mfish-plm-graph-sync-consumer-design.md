# MFish PLM 图同步消费端通用化架构设计

## 文档信息

| 项目   | 内容                           |
| ---- | ---------------------------- |
| 项目名称 | mfish-nocode PLM 图同步消费端通用化架构 |
| 当前版本 | mf-2.3.1                     |
| 更新日期 | 2026-04-18                   |
| 文档状态 | 已更新（基于代码实现）                  |
| 适用对象 | 后端开发工程师、架构师                  |

***

## 1. 背景与目标

### 1.1 项目背景

当前 PLM 模块图同步消费端存在以下问题：

- 消息处理逻辑与业务类型耦合，不同类型需要不同的处理方法
- 批量失败时无法保证整体回滚
- 缺少幂等性保证，重复消费可能导致数据不一致
- 缺少完整的操作日志和追溯能力

### 1.2 设计目标

| 目标        | 说明                                                                         |
| --------- | -------------------------------------------------------------------------- |
| **完全通用化** | GraphNode/GraphEdge 的 type 字段直接对应 NebulaGraph Tag/Edge Type，无需针对不同类型编写处理逻辑 |
| **批量原子性** | 一批消息要么全部成功，要么全部失败                                                          |
| **幂等保证**  | 基于 eventId 的幂等表，防止重复消费                                                     |
| **可靠保证**  | PostgreSQL 可信源 + MQ 可靠 + 重试自愈 + DLQ 人工 + 对账最终一致                            |
| **完整日志**  | 记录每个批次的处理结果，支持按 eventId 查询处理状态和耗时                                          |

### 1.3 设计原则

1. **通用性优先**：消费端不感知具体业务类型，只处理节点和边的抽象数据结构
2. **批量原子性**：一批节点+边作为最小处理单元
3. **幂等性**：通过 eventId 唯一索引保证处理幂等
4. **可追溯**：完整记录处理过程，便于问题排查

***

## 2. 整体架构设计

### 2.1 架构概览

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              mfish-nocode 系统                                  │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                           PLM 模块 (mf-plm)                               │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐   │   │
│  │  │                    业务操作层                                      │   │   │
│  │  │  Product/Part/Document/Folder/ContainsLink 等业务服务           │   │   │
│  │  └────────────────────────────┬────────────────────────────────────┘   │   │
│  │                               │                                          │   │
│  │                               ▼                                          │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐   │   │
│  │  │                 GraphSyncClient (发送端)                          │   │   │
│  │  │  将业务操作转换为 GraphSyncEvent，包含所有节点和边                 │   │   │
│  │  └────────────────────────────┬────────────────────────────────────┘   │   │
│  └──────────────────────────────┼──────────────────────────────────────────┘   │
│                                  │                                              │
│                                  │ RocketMQ (Topic: plm-graph-sync)             │
│                                  ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                           Graph 模块 (mf-graph)                          │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐   │   │
│  │  │              GraphSyncConsumer (消费端入口)                        │   │   │
│  │  │  @RocketMQMessageListener + 手动 ACK                             │   │   │
│  │  └────────────────────────────┬────────────────────────────────────┘   │   │
│  │                               │                                          │   │
│  │                               ▼                                          │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐   │   │
│  │  │              IdempotentService (幂等服务)                         │   │   │
│  │  │  1. 幂等检查：eventId 是否已处理                                  │   │   │
│  │  │  2. 状态管理：PROCESSING → COMPLETED / FAILED                    │   │   │
│  │  │  3. 超时处理：PROCESSING 状态超过30分钟允许重试                   │   │   │
│  │  └────────────────────────────┬────────────────────────────────────┘   │   │
│  │                               │                                          │   │
│  │                               ▼                                          │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐   │   │
│  │  │              NebulaWriteService (图库写入服务)                   │   │   │
│  │  │  1. 预检：检查节点/边是否已存在                                    │   │   │
│  │  │  2. 批量写入：INSERT VERTEX/EDGE IF NOT EXISTS                    │   │   │
│  │  │  3. 失败回滚：标记 FAILED 状态                                    │   │   │
│  │  └────────────────────────────┬────────────────────────────────────┘   │   │
│  │                               │                                          │   │
│  │                               ▼                                          │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐   │   │
│  │  │              SyncLogService (操作日志服务)                        │   │   │
│  │  │  1. 记录操作日志：接收、处理、成功、失败                          │   │   │
│  │  │  2. 支持按 eventId 查询处理状态                                  │   │   │
│  │  │  3. 支持统计和监控                                                │   │   │
│  │  └────────────────────────────┬────────────────────────────────────┘   │   │
│  └──────────────────────────────┼──────────────────────────────────────────┘   │
│                                  │                                              │
│                                  ▼                                              │
│                           ┌─────────────┐                                       │
│                           │NebulaGraph │                                       │
│                           │  图数据库   │                                       │
│                           └─────────────┘                                       │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 核心处理流程

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         GraphSyncConsumer 处理流程                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  1. 接收 GraphSyncEvent                                                          │
│     ┌─────────────────────────────────────────────────────────────────┐        │
│     │ eventId: 事件唯一ID                                               │        │
│     │ eventType: CREATE/UPDATE/DELETE                                   │        │
│     │ nodes: List<GraphNode>  节点列表                                  │        │
│     │ edges: List<GraphEdge>  边列表                                   │        │
│     │ timestamp: 时间戳                                                  │        │
│     │ source: 来源模块                                                   │        │
│     └─────────────────────────────────────────────────────────────────┘        │
│                                      │                                           │
│                                      ▼                                           │
│  2. 幂等检查                                                                       │
│     ┌─────────────────────────────────────────────────────────────────┐        │
│     │ SELECT status FROM sync_idempotent_log WHERE event_id = ?       │        │
│     └─────────────────────────────────────────────────────────────────┘        │
│                                      │                                           │
│              ┌───────────────────────┼───────────────────────┐                │
│              ▼                       ▼                       ▼                 │
│     ┌─────────────┐          ┌─────────────────────────────────┐        ┌─────────────┐          │
│     │ COMPLETED   │          │         PROCESSING               │        │   FAILED   │          │
│     │ (已成功)    │          │           (处理中)               │        │   (失败)    │          │
│     └──────┬──────┘          └─────────────────┬───────────────┘        └──────┬──────┘          │
│            │                        ┌──────────────┴──────────────┐               │                  │
│            ▼                        ▼                              ▼               ▼                  │
│     记录日志，跳过          ┌──────────────┐              ┌──────────────┐      由补偿任务处理              │
│     (幂等保证)              │   超时内      │              │   超时后     │      (或人工干预)                │
│                             │ 记录日志，跳过 │              │  允许重试    │                              │
│                             │ (防重复消费)  │              │ (继续处理)   │                              │
│                             └──────────────┘              └──────────────┘                              │
│                                                                                 │
│  3. 创建幂等记录 (status = PROCESSING)                                         │
│     ┌─────────────────────────────────────────────────────────────────┐        │
│     │ INSERT INTO sync_idempotent_log                                 │        │
│     │ (id, event_id, event_type, node_count, edge_count,            │        │
│     │  status, retry_count, create_time, update_time)                  │        │
│     └─────────────────────────────────────────────────────────────────┘        │
│                                      │                                           │
│                                      ▼                                           │
│  4. 批量预检 (可选，优化写入性能)                                                │
│     ┌─────────────────────────────────────────────────────────────────┐        │
│     │ 检查 NebulaGraph 中节点/边是否已存在                             │        │
│     │ 用于 UPSERT 场景判断是 INSERT 还是 UPDATE                        │        │
│     └─────────────────────────────────────────────────────────────────┘        │
│                                      │                                           │
│                                      ▼                                           │
│  5. 批量写入 NebulaGraph                                                        │
│     ┌─────────────────────────────────────────────────────────────────┐        │
│     │ eventType = CREATE/UPDATE:                                      │        │
│     │   - INSERT VERTEX {tag} IF NOT EXISTS                          │        │
│     │   - INSERT EDGE {edge} IF NOT EXISTS                           │        │
│     │                                                                   │        │
│     │ eventType = DELETE:                                             │        │
│     │   - DELETE VERTEX {vid}                                         │        │
│     │   - DELETE EDGE {edge}                                          │        │
│     └─────────────────────────────────────────────────────────────────┘        │
│                                      │                                           │
│              ┌───────────────────────┼───────────────────────┐                │
│              ▼                       │                       ▼                 │
│     ┌─────────────┐                 │               ┌─────────────┐          │
│     │   全部成功   │                 │               │   任何失败   │          │
│     └──────┬──────┘                 │               └──────┬──────┘          │
│            │                        │                       │                  │
│            ▼                        │                       ▼                  │
│  6. 更新幂等记录                 │               标记 FAILED                     │
│     ┌─────────────────────────────────────────────────────────────────┐        │
│     │ UPDATE sync_idempotent_log SET status = 'COMPLETED'          │        │
│     │ WHERE event_id = ?                                            │        │
│     └─────────────────────────────────────────────────────────────────┘        │
│                                      │                                           │
│                                      ▼                                           │
│  7. 记录操作日志 + 手动 ACK                                             │
│     ┌─────────────────────────────────────────────────────────────────┐        │
│     │ INSERT INTO sync_operation_log                                  │        │
│     │ (event_id, operation, node_count, edge_count,                  │        │
│     │  start_time, end_time, duration_ms, status, error_message)      │        │
│     └─────────────────────────────────────────────────────────────────┘        │
│                                      │                                           │
│                                      ▼                                           │
│  8. 失败处理                                                                       │
│     - 标记幂等记录为 FAILED                                                      │
│     - 记录错误详情到操作日志                                                     │
│     - 抛出异常触发 MQ 重试机制                                                   │
│     - 重试次数用尽后进入 DLQ                                                     │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

***

## 3. 核心组件设计

### 3.1 消息格式：GraphSyncEvent

```java
@Data
public class GraphSyncEvent {
    private String eventId;           // 事件唯一ID（幂等键）
    private String eventType;         // 事件类型：CREATE/UPDATE/DELETE
    private Long timestamp;           // 事件时间戳
    private String source;            // 来源模块：mf-plm
    private String operator;          // 操作人
    private List<GraphNode> nodes;    // 节点列表（可为null或空）
    private List<GraphEdge> edges;    // 边列表（可为null或空）
}
```

### 3.2 节点格式：GraphNode

```java
@Data
public class GraphNode {
    private String id;                // 节点ID（对应 NebulaGraph Vid）
    private String type;              // 节点类型（对应 NebulaGraph Tag Name）
    private String createBy;          // 创建人
    private Date createTime;          // 创建时间
    private String updateBy;          // 更新人
    private Date updateTime;          // 更新时间
    private Map<String, Object> properties; // 额外属性（可选）
}
```

### 3.3 边格式：GraphEdge

```java
@Data
public class GraphEdge {
    private String id;                // 边ID
    private String type;              // 边类型（对应 NebulaGraph Edge Name）
    private String fromId;           // 起始节点ID
    private String fromType;         // 起始节点类型
    private String toId;             // 目标节点ID
    private String toType;           // 目标节点类型
    private String createBy;          // 创建人
    private Date createTime;          // 创建时间
    private String updateBy;          // 更新人
    private Date updateTime;          // 更新时间
    private Map<String, Object> properties; // 额外属性（可选）
}
```

### 3.4 幂等表：sync\_idempotent\_log

**数据库**：plm 模块 PostgreSQL (mf_plm)

**实体类**：[SyncIdempotentLog.java](../../mf-business/mf-graph/src/main/java/cn/com/mfish/graph/sync/entity/SyncIdempotentLog.java)

```java
@Data
@TableName("sync_idempotent_log")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "sync_idempotent_log对象 幂等表")
public class SyncIdempotentLog extends BaseEntity<String> {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;                  // 唯一ID（UUID）
    private String eventId;             // 事件唯一ID
    private String eventType;           // 事件类型：CREATE/UPDATE/DELETE
    private Long nodeCount;             // 节点数量
    private Long edgeCount;            // 边数量
    private String status;              // 状态：PROCESSING/COMPLETED/FAILED
    private Long retryCount;            // 处理版本/轮次（每次处理递增）
    private String errorMessage;        // 错误信息
}
```

**Mapper**：[SyncIdempotentLogMapper.java](../../mf-business/mf-graph/src/main/java/cn/com/mfish/graph/sync/mapper/SyncIdempotentLogMapper.java)

继承 MyBatis-Plus `BaseMapper<SyncIdempotentLog>`，提供基础的 CRUD 操作。

**SQL Schema**：

```sql
CREATE TABLE sync_idempotent_log (
    id              VARCHAR(64) PRIMARY KEY COMMENT '唯一ID（UUID）',
    event_id        VARCHAR(64) NOT NULL COMMENT '事件唯一ID',
    event_type      VARCHAR(20) NOT NULL COMMENT '事件类型：CREATE/UPDATE/DELETE',
    node_count      BIGINT DEFAULT 0 COMMENT '节点数量',
    edge_count      BIGINT DEFAULT 0 COMMENT '边数量',
    status          VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/COMPLETED/FAILED',
    retry_count     BIGINT DEFAULT 0 COMMENT '处理版本/轮次',
    error_message   TEXT COMMENT '错误信息',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_sync_idempotent_log_event_id ON sync_idempotent_log(event_id);

CREATE INDEX idx_sync_idempotent_log_status ON sync_idempotent_log(status);
CREATE INDEX idx_sync_idempotent_log_create_time ON sync_idempotent_log(create_time);

COMMENT ON TABLE sync_idempotent_log IS '图同步幂等表';
COMMENT ON COLUMN sync_idempotent_log.id IS '唯一ID（UUID）';
COMMENT ON COLUMN sync_idempotent_log.event_id IS '事件唯一ID';
COMMENT ON COLUMN sync_idempotent_log.event_type IS '事件类型：CREATE/UPDATE/DELETE';
COMMENT ON COLUMN sync_idempotent_log.node_count IS '节点数量';
COMMENT ON COLUMN sync_idempotent_log.edge_count IS '边数量';
COMMENT ON COLUMN sync_idempotent_log.status IS '状态：PROCESSING/COMPLETED/FAILED';
COMMENT ON COLUMN sync_idempotent_log.retry_count IS '重试次数';
COMMENT ON COLUMN sync_idempotent_log.error_message IS '错误信息';
```

### 3.5 操作日志表：sync\_operation\_log

**数据库**：plm 模块 PostgreSQL (mf_plm)

**实体类**：[SyncOperationLog.java](../../mf-business/mf-graph/src/main/java/cn/com/mfish/graph/sync/entity/SyncOperationLog.java)

```java
@Data
@TableName("sync_operation_log")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "sync_operation_log对象 图同步操作日志")
public class SyncOperationLog extends BaseEntity<Long> {
    @TableId(type = IdType.AUTO)
    private Long id;                  // 唯一ID（自增）
    private String eventId;           // 事件唯一ID
    private String operation;         // 操作类型：PREPARE/PROCESS/COMPLETE/FAILED
    private Long nodeCount;           // 节点数量
    private Long edgeCount;          // 边数量
    private Date startTime;           // 开始时间
    private Date endTime;            // 结束时间
    private Long durationMs;          // 耗时(毫秒)
    private String status;            // 状态：SUCCESS/FAILED
    private String errorMessage;      // 错误信息
    private Object detail;            // 详细信息（JSON格式）
}
```

**Mapper**：[SyncOperationLogMapper.java](../../mf-business/mf-graph/src/main/java/cn/com/mfish/graph/sync/mapper/SyncOperationLogMapper.java)

继承 MyBatis-Plus `BaseMapper<SyncOperationLog>`，提供基础的 CRUD 操作。

**SQL Schema**：

```sql
CREATE TABLE sync_operation_log (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(64) NOT NULL COMMENT '事件唯一ID',
    operation       VARCHAR(20) NOT NULL COMMENT '操作类型：PREPARE/PROCESS/COMPLETE/FAILED',
    node_count      BIGINT DEFAULT 0 COMMENT '节点数量',
    edge_count      BIGINT DEFAULT 0 COMMENT '边数量',
    start_time      TIMESTAMP NOT NULL COMMENT '开始时间',
    end_time        TIMESTAMP COMMENT '结束时间',
    duration_ms     BIGINT COMMENT '耗时(毫秒)',
    status          VARCHAR(20) NOT NULL COMMENT '状态：SUCCESS/FAILED',
    error_message   TEXT COMMENT '错误信息',
    detail          JSONB COMMENT '详细信息',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sync_operation_log_event_id ON sync_operation_log(event_id);
CREATE INDEX idx_sync_operation_log_status ON sync_operation_log(status);
CREATE INDEX idx_sync_operation_log_create_time ON sync_operation_log(create_time);

COMMENT ON TABLE sync_operation_log IS '图同步操作日志表';
COMMENT ON COLUMN sync_operation_log.id IS '唯一ID（自增）';
COMMENT ON COLUMN sync_operation_log.event_id IS '事件唯一ID';
COMMENT ON COLUMN sync_operation_log.operation IS '操作类型：PREPARE/PROCESS/COMPLETE/FAILED';
COMMENT ON COLUMN sync_operation_log.node_count IS '节点数量';
COMMENT ON COLUMN sync_operation_log.edge_count IS '边数量';
COMMENT ON COLUMN sync_operation_log.start_time IS '开始时间';
COMMENT ON COLUMN sync_operation_log.end_time IS '结束时间';
COMMENT ON COLUMN sync_operation_log.duration_ms IS '耗时(毫秒)';
COMMENT ON COLUMN sync_operation_log.status IS '状态：SUCCESS/FAILED';
COMMENT ON COLUMN sync_operation_log.error_message IS '错误信息';
COMMENT ON COLUMN sync_operation_log.detail IS '详细信息';
```

***

## 4. 核心服务设计

### 4.1 GraphSyncConsumer（消费端入口）

**职责**：

- 监听 RocketMQ 消息
- 协调幂等检查、状态更新、批量写入
- 手动 ACK 确认

**设计要点**：

- 实现 `RocketMQListener<GraphSyncEvent>` 接口
- 使用手动 ACK 模式（不自动 ACK）
- 失败时抛出异常触发 MQ 重试

```java
@Slf4j
@Component
@RocketMQMessageListener(
    consumerGroup = "${rocketmq.consumer.group}",
    topic = "${rocketmq.consumer.topic}",
    consumeMode = ConsumeMode.ORDERLY  // 顺序消费保证
)
public class GraphSyncConsumer implements RocketMQListener<GraphSyncEvent> {

    @Autowired
    private IdempotentService idempotentService;

    @Autowired
    private NebulaWriteService nebulaWriteService;

    @Autowired
    private SyncLogService syncLogService;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(GraphSyncEvent event, Message message, ConsumeConcurrentlyContext context) {
        String eventId = event.getEventId();
        long startTime = System.currentTimeMillis();

        // 1. 记录接收日志
        syncLogService.logReceive(event);

        // 2. 幂等检查
        SyncIdempotentLog idempotentLog = idempotentService.checkAndCreate(event);
        if (idempotentLog == null) {
            // 已处理过，跳过
            syncLogService.logSkip(event, "幂等检查跳过");
            return;
        }

        // 3. 记录处理开始
        syncLogService.logStart(event);
        int retryCount = idempotentLog.getRetryCount().intValue();

        try {
            // 4. 根据事件类型处理
            switch (event.getEventType()) {
                case "CREATE":
                case "UPDATE":
                    nebulaWriteService.upsertNodesAndEdges(event);
                    break;
                case "DELETE":
                    nebulaWriteService.deleteNodesAndEdges(event);
                    break;
                default:
                    throw new IllegalArgumentException("未知事件类型: " + event.getEventType());
            }

            // 5. 更新幂等状态为成功
            idempotentService.markCompleted(eventId);

            // 6. 记录成功日志
            long duration = System.currentTimeMillis() - startTime;
            syncLogService.logSuccess(event, duration);

            // 7. 手动 ACK
            rocketMQTemplate.ack(message, context);

        } catch (Exception e) {
            log.error("处理图同步事件失败: eventId={}", eventId, e);

            // 8. 更新幂等状态为失败
            idempotentService.markFailed(eventId, e.getMessage(), retryCount);

            // 9. 记录失败日志
            long duration = System.currentTimeMillis() - startTime;
            syncLogService.logFailed(event, duration, e.getMessage());

            // 10. 抛出异常触发 MQ 重试
            throw new RuntimeException("图同步处理失败", e);
        }
    }
}
```

### 4.2 IdempotentService（幂等服务）

**职责**：

- eventId 幂等检查
- 幂等记录状态管理
- 处理版本/轮次管理

**设计要点**：

- 基于 eventId 唯一索引保证幂等
- 状态流转：PROCESSING → COMPLETED / FAILED
- FAILED 状态可由补偿任务或人工处理

**实体类**：使用 `SyncIdempotentLog`

```java
@Slf4j
@Service
public class IdempotentService {

    @Autowired
    private SyncIdempotentLogMapper idempotentLogMapper;

    // 处理超时时间，默认 30 分钟
    private static final long PROCESSING_TIMEOUT_MS = 30 * 60 * 1000;

    /**
     * 幂等检查并创建处理记录
     * @return null 表示已处理过，非 null 表示需要处理
     */
    public SyncIdempotentLog checkAndCreate(GraphSyncEvent event) {
        String eventId = event.getEventId();

        // 查询是否存在
        SyncIdempotentLog existing = idempotentLogMapper.selectByEventId(eventId);
        if (existing != null) {
            // 已存在，根据状态判断
            if ("COMPLETED".equals(existing.getStatus())) {
                log.info("事件已处理完成，跳过: eventId={}", eventId);
                return null;
            }
            if ("PROCESSING".equals(existing.getStatus())) {
                // 检查是否超时，允许超时后重试
                if (existing.getUpdateTime() != null &&
                    System.currentTimeMillis() - existing.getUpdateTime().getTime() < PROCESSING_TIMEOUT_MS) {
                    log.warn("事件正在处理中，防止重复消费: eventId={}", eventId);
                    return null;
                }
                log.warn("事件处理超时，允许重试: eventId={}", eventId);
            }
            // FAILED 状态，允许重试
            log.info("事件处理失败，允许重试: eventId={}", eventId);
        }

        // 创建或更新幂等记录
        SyncIdempotentLog syncLog = new SyncIdempotentLog();
        syncLog.setEventId(eventId);
        syncLog.setEventType(event.getEventType());
        syncLog.setNodeCount(event.getNodes() == null ? 0L : (long) event.getNodes().size());
        syncLog.setEdgeCount(event.getEdges() == null ? 0L : (long) event.getEdges().size());
        syncLog.setStatus("PROCESSING");
        // 处理版本：每次处理递增，用于追踪处理轮次
        syncLog.setRetryCount(existing == null ? 0L : existing.getRetryCount() + 1);
        syncLog.setUpdateTime(new Date());

        if (existing == null) {
            idempotentLogMapper.insert(syncLog);
        } else {
            syncLog.setId(existing.getId());
            idempotentLogMapper.updateById(syncLog);
        }

        return syncLog;
    }

    /**
     * 标记处理成功
     */
    public void markCompleted(String eventId) {
        SyncIdempotentLog syncLog = new SyncIdempotentLog();
        syncLog.setEventId(eventId);
        syncLog.setStatus("COMPLETED");
        syncLog.setUpdateTime(new Date());
        idempotentLogMapper.updateStatusByEventId(syncLog);
    }

    /**
     * 标记处理失败
     */
    public void markFailed(String eventId, String errorMessage, int retryCount) {
        SyncIdempotentLog syncLog = new SyncIdempotentLog();
        syncLog.setEventId(eventId);
        syncLog.setStatus("FAILED");
        syncLog.setErrorMessage(errorMessage);
        syncLog.setRetryCount((long) retryCount);
        syncLog.setUpdateTime(new Date());
        idempotentLogMapper.updateStatusByEventId(syncLog);
    }
}
```

### 4.3 NebulaWriteService（图库写入服务）

**职责**：

- 通用化批量写入节点和边
- 支持 CREATE/UPDATE/DELETE 操作
- 失败时整体回滚

**设计要点**：

- 节点写入使用 `INSERT VERTEX {tag} IF NOT EXISTS`
- 边写入使用 `INSERT EDGE {edge} IF NOT EXISTS`
- 删除使用 `DELETE VERTEX/EDGE`
- 批量执行使用事务性Session

```java
@Slf4j
@Service
public class NebulaWriteService {

    @Autowired
    private NebulaClient nebulaClient;

    /**
     * 批量写入或更新节点和边（通用化）
     */
    public void upsertNodesAndEdges(GraphSyncEvent event) {
        // 1. 写入节点
        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            // 按类型分组批量写入
            Map<String, List<GraphNode>> nodesByType = event.getNodes().stream()
                .collect(Collectors.groupingBy(GraphNode::getType));

            for (Map.Entry<String, List<GraphNode>> entry : nodesByType.entrySet()) {
                String tagName = entry.getKey();
                List<GraphNode> nodes = entry.getValue();
                nebulaClient.batchUpsertVertices(tagName, nodes);
                log.info("批量写入节点: tagName={}, count={}", tagName, nodes.size());
            }
        }

        // 2. 写入边
        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            // 按类型分组批量写入
            Map<String, List<GraphEdge>> edgesByType = event.getEdges().stream()
                .collect(Collectors.groupingBy(GraphEdge::getType));

            for (Map.Entry<String, List<GraphEdge>> entry : edgesByType.entrySet()) {
                String edgeName = entry.getKey();
                List<GraphEdge> edges = entry.getValue();
                nebulaClient.batchUpsertEdges(edgeName, edges);
                log.info("批量写入边: edgeName={}, count={}", edgeName, edges.size());
            }
        }
    }

    /**
     * 批量删除节点和边
     */
    public void deleteNodesAndEdges(GraphSyncEvent event) {
        // 1. 删除边（先删边，避免孤立节点）
        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            for (GraphEdge edge : event.getEdges()) {
                nebulaClient.deleteEdge(edge.getType(), edge.getFromId(), edge.getToId());
            }
            log.info("批量删除边: count={}", event.getEdges().size());
        }

        // 2. 删除节点
        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            for (GraphNode node : event.getNodes()) {
                nebulaClient.deleteVertex(node.getType(), node.getId());
            }
            log.info("批量删除节点: count={}", event.getNodes().size());
        }
    }
}
```

### 4.4 NebulaClient 扩展方法

在现有 NebulaClient 基础上增加：

```java
@Slf4j
@Service
public class NebulaClient {

    @Autowired
    private NebulaPoolConfig nebulaPoolConfig;

    private NebulaPool nebulaPool;

    /**
     * 获取图数据库会话
     */
    public com.vesoft.nebula.client.graph.net.Session getSession() {
        if (nebulaPool == null) {
            nebulaPool = new NebulaPool();
            nebulaPool.init(nebulaPoolConfig);
        }
        return nebulaPool.getSession("root", "nebula", false);
    }

    /**
     * 关闭连接池，应用关闭时调用
     */
    public void close() {
        if (nebulaPool != null) {
            nebulaPool.close();
            nebulaPool = null;
        }
    }

    /**
     * 字符串转义，防止注入
     */
    private String escapeString(String value) {
        if (value == null) {
            return "NULL";
        }
        return value.replace("\\", "\\\\")
                   .replace("'", "\\'")
                   .replace("\"", "\\\"");
    }

    /**
     * 日期时间格式化
     */
    private String formatDateTime(Date date) {
        if (date == null) {
            return "NULL";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "\"" + sdf.format(date) + "\"";
    }

    /**
     * 对象转 JSON 字符串
     */
    private String toJsonString(Object obj) {
        if (obj == null) {
            return "NULL";
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return "\"" + escapeString(mapper.writeValueAsString(obj)) + "\"";
        } catch (JsonProcessingException e) {
            log.error("对象转JSON失败", e);
            return "NULL";
        }
    }

    /**
     * 批量 Upsert 节点（INSERT VERTEX IF NOT EXISTS）
     */
    public void batchUpsertVertices(String tagName, List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
            // 构建批量插入语句
            StringBuilder ngql = new StringBuilder();
            ngql.append("INSERT VERTEX ").append(tagName)
                .append("(id, create_by, create_time, update_by, update_time) VALUES ");

            boolean first = true;
            for (GraphNode node : nodes) {
                if (!first) {
                    ngql.append(",");
                }
                first = false;

                ngql.append(escapeString(node.getId()))
                    .append(":( ")
                    .append(escapeString(node.getId())).append(",")
                    .append(escapeString(node.getCreateBy())).append(",")
                    .append(formatDateTime(node.getCreateTime())).append(",")
                    .append(escapeString(node.getUpdateBy())).append(",")
                    .append(formatDateTime(node.getUpdateTime()))
                    .append(" )");
            }

            ResultSet resultSet = session.execute(ngql.toString());
            if (!resultSet.isSucceeded()) {
                throw new RuntimeException("批量写入节点失败: " + resultSet.getErrorMessage());
            }

            log.info("批量Upsert节点成功: tagName={}, count={}", tagName, nodes.size());
        }
    }

    /**
     * 批量 Upsert 边（INSERT EDGE IF NOT EXISTS）
     */
    public void batchUpsertEdges(String edgeName, List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
            StringBuilder ngql = new StringBuilder();
            ngql.append("INSERT EDGE ").append(edgeName)
                .append("(id, type, create_by, create_time, update_by, update_time, ")
                .append("from_id, from_type, to_id, to_type, properties) VALUES ");

            boolean first = true;
            for (GraphEdge edge : edges) {
                if (!first) {
                    ngql.append(",");
                }
                first = false;

                ngql.append(escapeString(edge.getFromId()))
                    .append("->")
                    .append(escapeString(edge.getToId()))
                    .append(":( ")
                    .append(escapeString(edge.getId())).append(",")
                    .append(escapeString(edge.getType())).append(",")
                    .append(escapeString(edge.getCreateBy())).append(",")
                    .append(formatDateTime(edge.getCreateTime())).append(",")
                    .append(escapeString(edge.getUpdateBy())).append(",")
                    .append(formatDateTime(edge.getUpdateTime())).append(",")
                    .append(escapeString(edge.getFromId())).append(",")
                    .append(escapeString(edge.getFromType())).append(",")
                    .append(escapeString(edge.getToId())).append(",")
                    .append(escapeString(edge.getToType())).append(",")
                    .append(toJsonString(edge.getProperties()))
                    .append(" )");
            }

            ResultSet resultSet = session.execute(ngql.toString());
            if (!resultSet.isSucceeded()) {
                throw new RuntimeException("批量写入边失败: " + resultSet.getErrorMessage());
            }

            log.info("批量Upsert边成功: edgeName={}, count={}", edgeName, edges.size());
        }
    }
}
```

### 4.5 SyncLogService（操作日志服务）

**职责**：

- 记录操作日志
- 支持按 eventId 查询处理状态
- 支持统计和监控

**实体类**：使用 `SyncOperationLog`

```java
@Slf4j
@Service
public class SyncLogService {

    @Autowired
    private SyncOperationLogMapper operationLogMapper;

    public void logReceive(GraphSyncEvent event) {
        log.info("[MQ接收] eventId={}, eventType={}, nodes={}, edges={}",
            event.getEventId(), event.getEventType(),
            event.getNodes() == null ? 0L : (long) event.getNodes().size(),
            event.getEdges() == null ? 0L : (long) event.getEdges().size());
    }

    public void logSkip(GraphSyncEvent event, String reason) {
        log.info("[MQ跳过] eventId={}, reason={}", event.getEventId(), reason);
    }

    public void logStart(GraphSyncEvent event) {
        SyncOperationLog opLog = new SyncOperationLog();
        opLog.setEventId(event.getEventId());
        opLog.setOperation("PROCESS");
        opLog.setNodeCount(event.getNodes() == null ? 0L : (long) event.getNodes().size());
        opLog.setEdgeCount(event.getEdges() == null ? 0L : (long) event.getEdges().size());
        opLog.setStartTime(new Date());
        opLog.setStatus("PROCESSING");
        operationLogMapper.insert(opLog);
    }

    public void logSuccess(GraphSyncEvent event, long durationMs) {
        SyncOperationLog opLog = new SyncOperationLog();
        opLog.setEventId(event.getEventId());
        opLog.setOperation("COMPLETE");
        opLog.setStartTime(new Date(System.currentTimeMillis() - durationMs));
        opLog.setEndTime(new Date());
        opLog.setDurationMs(durationMs);
        opLog.setStatus("SUCCESS");
        operationLogMapper.insert(opLog);

        log.info("[处理成功] eventId={}, duration={}ms", event.getEventId(), durationMs);
    }

    public void logFailed(GraphSyncEvent event, long durationMs, String errorMessage) {
        SyncOperationLog opLog = new SyncOperationLog();
        opLog.setEventId(event.getEventId());
        opLog.setOperation("FAILED");
        opLog.setStartTime(new Date(System.currentTimeMillis() - durationMs));
        opLog.setEndTime(new Date());
        opLog.setDurationMs(durationMs);
        opLog.setStatus("FAILED");
        opLog.setErrorMessage(errorMessage);
        operationLogMapper.insert(opLog);

        log.error("[处理失败] eventId={}, duration={}ms, error={}",
            event.getEventId(), durationMs, errorMessage);
    }
}
```

***

## 5. 可靠性设计

### 5.1 可靠性五要素

| 要素                 | 实现方式                                 |
| ------------------ | ------------------------------------ |
| **PostgreSQL 可信源** | 图库只存储关系拓扑，节点完整业务属性以 PostgreSQL 为准    |
| **MQ 可靠**          | RocketMQ 事务消息保证发送可靠，消费端手动 ACK        |
| **重试自愈**           | MQ 重试机制（默认3次），临时故障自动恢复               |
| **DLQ 人工**         | 重试用尽后进入 Dead Letter Queue，由人工处理      |
| **对账最终一致**         | 定期对账任务对比 PostgreSQL 和 NebulaGraph 数据 |

### 5.2 失败处理流程

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           失败处理流程                                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  消费处理失败                                                                    │
│       │                                                                         │
│       ▼                                                                         │
│  抛出 RuntimeException ─────────────────────────────────────────────┐           │
│       │                                                                     │           │
│       ▼                                                                     │           │
│  MQ 自动重试（maxRetryTimes=3）                                              │           │
│       │                                                                     │           │
│       ├─── 重试成功 ──▶ ACK ──▶ COMPLETED                                  │           │
│       │                                                                     │           │
│       └─── 重试失败 ──▶ 进入 DLQ ──▶ FAILED ──▶ 人工干预                    │           │
│                                                                                 │           │
│  ┌───────────────────────────────────────────────────────────────────────────┘  │
│  │                                                                                │
│  ▼                                                                                │
│  补偿任务（定时扫描 FAILED 状态）                                                │
│       │                                                                                │
│       ├─── 可自动补偿 ──▶ 重新处理 ──▶ COMPLETED                                │
│       │                                                                                │
│       └─── 无法自动补偿 ──▶ 保持 FAILED ──▶ 人工处理                            │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 5.3 幂等保证

1. **eventId 唯一索引**：防止同一事件重复处理
2. **PROCESSING 状态**：防止并发重复消费
3. **COMPLETED 状态**：跳过已处理事件
4. **FAILED 状态**：允许重试

***

## 6. 发送端改造（PLM 模块）

### 6.1 发送端改造思路

PLM 端需要将一批业务操作打包成一条消息发送，包括：

- 所有涉及的业务实体（节点）
- 所有涉及的关联关系（边）

### 6.2 GraphSyncClient 发送接口

```java
/**
 * 发送图同步事件（通用化）
 * @param event 包含所有节点和边的事件
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

    rocketMQTemplate.asyncSend(topic, event, new SendCallback() {
        @Override
        public void onSuccess(SendResult sendResult) {
            log.info("图同步事件发送成功, eventId={}, result={}",
                event.getEventId(), sendResult.getMsgId());
        }

        @Override
        public void onException(Throwable e) {
            log.error("图同步事件发送失败, eventId={}", event.getEventId(), e);
        }
    });
}

/**
 * 构建批量同步事件（用于一次性发送所有变更）
 */
public GraphSyncEvent buildBatchSyncEvent(
    List<GraphNode> nodes,
    List<GraphEdge> edges,
    String eventType) {

    GraphSyncEvent event = new GraphSyncEvent();
    event.setEventId(UUID.randomUUID().toString());
    event.setEventType(eventType);
    event.setTimestamp(System.currentTimeMillis());
    event.setSource("mf-plm");
    event.setNodes(nodes);
    event.setEdges(edges);
    return event;
}
```

***

## 7. 数据库 Schema

**数据库**：plm 模块 PostgreSQL (mf_plm)

### 7.1 幂等表

```sql
CREATE TABLE sync_idempotent_log (
    id              VARCHAR(64) PRIMARY KEY COMMENT '唯一ID（UUID）',
    event_id        VARCHAR(64) NOT NULL COMMENT '事件唯一ID',
    event_type      VARCHAR(20) NOT NULL COMMENT '事件类型：CREATE/UPDATE/DELETE',
    node_count      BIGINT DEFAULT 0 COMMENT '节点数量',
    edge_count      BIGINT DEFAULT 0 COMMENT '边数量',
    status          VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/COMPLETED/FAILED',
    retry_count     BIGINT DEFAULT 0 COMMENT '处理版本/轮次',
    error_message   TEXT COMMENT '错误信息',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_sync_idempotent_log_event_id ON sync_idempotent_log(event_id);

CREATE INDEX idx_sync_idempotent_log_status ON sync_idempotent_log(status);
CREATE INDEX idx_sync_idempotent_log_create_time ON sync_idempotent_log(create_time);

COMMENT ON TABLE sync_idempotent_log IS '图同步幂等表';
```

### 7.2 操作日志表

```sql
CREATE TABLE sync_operation_log (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(64) NOT NULL COMMENT '事件唯一ID',
    operation       VARCHAR(20) NOT NULL COMMENT '操作类型：PREPARE/PROCESS/COMPLETE/FAILED',
    node_count      BIGINT DEFAULT 0 COMMENT '节点数量',
    edge_count      BIGINT DEFAULT 0 COMMENT '边数量',
    start_time      TIMESTAMP NOT NULL COMMENT '开始时间',
    end_time        TIMESTAMP COMMENT '结束时间',
    duration_ms     BIGINT COMMENT '耗时(毫秒)',
    status          VARCHAR(20) NOT NULL COMMENT '状态：SUCCESS/FAILED',
    error_message   TEXT COMMENT '错误信息',
    detail          JSONB COMMENT '详细信息',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sync_operation_log_event_id ON sync_operation_log(event_id);
CREATE INDEX idx_sync_operation_log_status ON sync_operation_log(status);
CREATE INDEX idx_sync_operation_log_create_time ON sync_operation_log(create_time);

COMMENT ON TABLE sync_operation_log IS '图同步操作日志表';
```

***

## 8. 验收标准

### 8.1 功能验收

| 功能点   | 验收标准                        |
| ----- | --------------------------- |
| 通用消费  | 消费端不感知具体业务类型，根据 type 字段自动处理 |
| 批量原子性 | 一批消息全部成功或全部失败               |
| 幂等保证  | 同一 eventId 重复消费不产生副作用       |
| 失败回滚  | 任何节点/边写入失败，整体标记为 FAILED     |
| 完整日志  | 操作日志记录每个批次的处理过程             |

### 8.2 可靠性验收

| 指标   | 验收标准                                 |
| ---- | ------------------------------------ |
| 消息可靠 | 事务提交成功后才发送 MQ 消息                     |
| 重试自愈 | 临时故障通过重试恢复                           |
| 死信处理 | 重试用尽后进入 DLQ，支持人工处理                   |
| 最终一致 | 补偿任务确保 PostgreSQL 和 NebulaGraph 最终一致 |

***

## 9. 改造计划

### 阶段一：基础设施（消费端）

1. 创建 sync\_idempotent\_log 表
2. 创建 sync\_operation\_log 表
3. 实现 IdempotentService
4. 实现 SyncLogService

### 阶段二：核心服务（消费端）

1. 扩展 NebulaClient（增加 batchUpsertVertices/batchUpsertEdges）
2. 实现 NebulaWriteService
3. 重构 GraphSyncConsumer

### 阶段三：发送端改造（PLM 端）

1. 改造 GraphSyncClientImpl
2. 支持批量发送 GraphSyncEvent

### 阶段四：测试验证

1. 单元测试
2. 集成测试
3. 性能测试

***

**文档状态**：已更新（基于代码实现）
**最后更新**：2026-04-18
