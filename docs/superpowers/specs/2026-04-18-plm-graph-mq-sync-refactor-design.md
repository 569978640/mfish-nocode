# PLM-Graph MQ 同步重构设计方案

## 1. 背景与目标

### 1.1 当前问题
- `NebulaClient` 基于 HTTP REST API 调用 NebulaGraph，性能和功能受限
- 存在两套 GraphNode/GraphEdge 模型，容易造成混淆
- HTTP REST API 无法使用 NebulaGraph 原生的连接池、高可用、负载均衡等特性

### 1.2 重构目标
- 基于 vesoft client 原生 Session 实现图数据库连接
- 统一使用 `cn.com.mfish.graph.model.GraphNode` 和 `cn.com.mfish.graph.model.GraphEdge`
- 完全废弃 HTTP REST API 的 `NebulaClient`
- 复用现有的 `NebulaGraphClient` + `NebulaSessionPool` 架构

## 2. 架构设计

### 2.1 重构后数据流

```
PLM模块                              Graph模块
   │                                    │
   ▼                                    │
PlmGraphSyncClient ──MQ───────────►GraphSyncConsumer
   │                                    │
   ▼                                    ▼
GraphSyncEvent                      NebulaWriteService
(统一GraphNode/GraphEdge)                  │
                                              │
                                              ▼
                                    NebulaGraphClient (复用)
                                              │
                                              ▼
                                    NebulaSessionPool (vesoft native Session)
                                              │
                                              ▼
                                         NebulaGraph
```

### 2.2 组件职责

| 组件 | 职责 | 变更 |
|------|------|------|
| `PlmGraphSyncClient` | PLM业务发送MQ消息 | 无变更 |
| `GraphSyncConsumer` | 消费MQ消息 | 无变更 |
| `NebulaWriteService` | 图数据库写入服务 | 改用 NebulaGraphClient |
| `NebulaGraphClient` | 图数据库客户端门面 | 复用现有实现 |
| `NebulaSessionPool` | Session连接池管理 | 复用现有实现 |
| `NebulaClient` | HTTP REST API客户端 | **删除** |

## 3. 模型统一

### 3.1 统一模型

使用 `cn.com.mfish.graph.model.GraphNode` 和 `cn.com.mfish.graph.model.GraphEdge`：

| 模型 | 路径 | 说明 |
|------|------|------|
| GraphNode | `mf-common-graph/.../model/GraphNode.java` | 继承 GraphBaseEntity |
| GraphEdge | `mf-common-graph/.../model/GraphEdge.java` | 继承 GraphBaseEntity |

### 3.2 待废弃模型

| 模型 | 路径 | 处理方式 |
|------|------|----------|
| GraphNode | `mf-common-graph/.../model/node/GraphNode.java` | 标记 @Deprecated |
| GraphEdge | `mf-common-graph/.../model/edge/GraphEdge.java` | 标记 @Deprecated |

## 4. 重构详细设计

### 4.1 NebulaWriteService 重构

**文件路径**: `mf-business/mf-graph/src/main/java/cn/com/mfish/graph/sync/service/NebulaWriteService.java`

**变更点**:
1. 注入 `NebulaGraphClient` 替代 `NebulaClient`
2. 使用 `nebulaGraphClient.getWritePool().executeWrite(ngql)` 执行写入
3. 使用 `nebulaGraphClient.getWritePool().executeQuery(ngql)` 执行查询
4. 根据节点类型动态构建 nGQL 语句

**核心方法签名**:
```java
@Service
public class NebulaWriteService {
    @Autowired
    private NebulaGraphClient nebulaGraphClient;

    public void upsertNodesAndEdges(GraphSyncEvent event);
    public void deleteNodesAndEdges(GraphSyncEvent event);
}
```

### 4.2 nGQL 语句构造

**Upsert 节点**:
```java
UPSERT VERTEX `<vid>` ON TAG `<tagName>`
SET `<field1>` = `<value1>`, `<field2>` = `<value2>`
```

**Upsert 边**:
```java
UPSERT EDGE `<edgeType>` `<fromVid>` -> `<toVid>`
SET `<field1>` = `<value1>`, `<field2>` = `<value2>`
```

**Delete 边**:
```java
DELETE EDGE `<edgeType>` `<fromVid>` -> `<toVid>`
```

**Delete 节点**:
```java
DELETE VERTEX `<vid>`
```

## 5. 废弃清单

### 5.1 删除文件

| 文件 | 路径 |
|------|------|
| NebulaClient | `mf-common-graph/.../client/NebulaClient.java` |

### 5.2 标记废弃

| 文件 | 处理 |
|------|------|
| `model/node/GraphNode.java` | 添加 @Deprecated 注解 |
| `model/edge/GraphEdge.java` | 添加 @Deprecated 注解 |

## 6. 测试验证

### 6.1 单元测试
- `NebulaWriteServiceTest`: 测试 upsert/delete 逻辑

### 6.2 集成测试
- MQ 消息发送和消费完整链路测试
- 图数据库 CRUD 验证

## 7. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| nGQL 构造错误 | 中 | 复用 SchemaUtils 工具类 |
| Session 连接泄漏 | 中 | 使用 try-finally 确保归还 |
| 批量操作性能 | 低 | 复用现有的批量操作实现 |

## 8. 实现步骤

1. 修改 `NebulaWriteService`，使用 `NebulaGraphClient` 替代 `NebulaClient`
2. 删除 `NebulaClient.java`
3. 为 `model/node/GraphNode` 和 `model/edge/GraphEdge` 添加 `@Deprecated` 注解
4. 更新相关依赖和配置
5. 验证测试通过

---

**文档版本**: v1.0
**创建日期**: 2026-04-18
**状态**: 已确认
