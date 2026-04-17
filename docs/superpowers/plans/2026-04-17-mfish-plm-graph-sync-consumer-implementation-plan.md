# MFish PLM 图同步消费端通用化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 PLM 图同步消费端改造为通用化架构，支持批量节点+边处理、幂等保证、批量原子性、完整操作日志

**架构：** 消费端通过 eventId 幂等检查 + 批量预检 + NebulaGraph UPSERT 实现通用化处理；发送端支持一次性发送所有节点和边

**技术栈：** RocketMQ + NebulaGraph + MySQL (幂等表、日志表) + MyBatis-Plus

---

## 文件结构

### 新建文件

| 文件路径 | 职责 |
|---------|------|
| `mf-business/mf-graph/src/main/java/cn/com/mfish/graph/mapper/SyncIdempotentLogMapper.java` | 幂等表 Mapper |
| `mf-business/mf-graph/src/main/java/cn/com/mfish/graph/mapper/SyncOperationLogMapper.java` | 操作日志表 Mapper |
| `mf-business/mf-graph/src/main/java/cn/com/mfish/graph/entity/SyncIdempotentLog.java` | 幂等表实体 |
| `mf-business/mf-graph/src/main/java/cn/com/mfish/graph/entity/SyncOperationLog.java` | 操作日志表实体 |
| `mf-business/mf-graph/src/main/java/cn/com/mfish/graph/service/IdempotentService.java` | 幂等服务 |
| `mf-business/mf-graph/src/main/java/cn/com/mfish/graph/service/SyncLogService.java` | 操作日志服务 |
| `mf-business/mf-graph/src/main/java/cn/com/mfish/graph/service/NebulaWriteService.java` | 图库写入服务 |
| `mf-business/mf-graph/src/main/resources/mapper/SyncIdempotentLogMapper.xml` | 幂等表 Mapper XML |
| `mf-business/mf-graph/src/main/resources/mapper/SyncOperationLogMapper.xml` | 操作日志 Mapper XML |
| `mf-business/mf-graph/src/main/resources/sql/sync_tables.sql` | 数据库表 SQL |

### 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `mf-business/mf-graph/src/main/java/cn/com/mfish/graph/consumer/GraphSyncConsumer.java` | 重构为通用化消费逻辑 |
| `mf-business/mf-graph/src/main/java/cn/com/mfish/graph/client/NebulaClient.java` | 增加 batchUpsertVertices/batchUpsertEdges 方法 |
| `mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/model/node/GraphNode.java` | 增加 properties 字段 |
| `mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/model/edge/GraphEdge.java` | 增加 properties 字段 |
| `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/impl/PlmGraphSyncClientImpl.java` | 支持批量发送 |
| `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/PlmGraphSyncClient.java` | 扩展批量发送接口 |

---

## 任务分解

### 任务 1：创建数据库表 SQL

**文件：**
- 创建：`mf-business/mf-graph/src/main/resources/sql/sync_tables.sql`

- [ ] **步骤 1：编写数据库表 SQL**

```sql
-- 图同步幂等表
CREATE TABLE IF NOT EXISTS sync_idempotent_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id        VARCHAR(64) NOT NULL UNIQUE COMMENT '事件唯一ID',
    event_type      VARCHAR(20) NOT NULL COMMENT '事件类型：CREATE/UPDATE/DELETE',
    node_count      INT DEFAULT 0 COMMENT '节点数量',
    edge_count      INT DEFAULT 0 COMMENT '边数量',
    status          VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/COMPLETED/FAILED',
    retry_count     INT DEFAULT 0 COMMENT '重试次数',
    error_message   TEXT COMMENT '错误信息',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_event_id (event_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图同步幂等表';

-- 图同步操作日志表
CREATE TABLE IF NOT EXISTS sync_operation_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id        VARCHAR(64) NOT NULL COMMENT '事件唯一ID',
    operation       VARCHAR(20) NOT NULL COMMENT '操作类型：PREPARE/PROCESS/COMPLETE/FAILED',
    node_count      INT DEFAULT 0 COMMENT '节点数量',
    edge_count      INT DEFAULT 0 COMMENT '边数量',
    start_time      DATETIME NOT NULL COMMENT '开始时间',
    end_time        DATETIME COMMENT '结束时间',
    duration_ms     BIGINT COMMENT '耗时(毫秒)',
    status          VARCHAR(20) NOT NULL COMMENT '状态：SUCCESS/FAILED',
    error_message   TEXT COMMENT '错误信息',
    detail          JSON COMMENT '详细信息',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_id (event_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图同步操作日志表';
```

- [ ] **步骤 2：Commit**

```bash
git add mf-business/mf-graph/src/main/resources/sql/sync_tables.sql
git commit -m "feat(graph): 添加图同步幂等表和操作日志表SQL"
```

---

### 任务 2：创建幂等表实体和 Mapper

**文件：**
- 创建：`mf-business/mf-graph/src/main/java/cn/com/mfish/graph/entity/SyncIdempotentLog.java`
- 创建：`mf-business/mf-graph/src/main/java/cn/com/mfish/graph/mapper/SyncIdempotentLogMapper.java`
- 创建：`mf-business/mf-graph/src/main/resources/mapper/SyncIdempotentLogMapper.xml`

- [ ] **步骤 1：编写幂等表实体 SyncIdempotentLog**

```java
package cn.com.mfish.graph.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("sync_idempotent_log")
public class SyncIdempotentLog implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String eventType;

    private Integer nodeCount;

    private Integer edgeCount;

    private String status;

    private Integer retryCount;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
```

- [ ] **步骤 2：编写幂等表 Mapper 接口 SyncIdempotentLogMapper**

```java
package cn.com.mfish.graph.mapper;

import cn.com.mfish.graph.entity.SyncIdempotentLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SyncIdempotentLogMapper extends BaseMapper<SyncIdempotentLog> {

    SyncIdempotentLog selectByEventId(@Param("eventId") String eventId);

    int updateStatusByEventId(@Param("eventId") String eventId,
                              @Param("status") String status,
                              @Param("errorMessage") String errorMessage,
                              @Param("retryCount") Integer retryCount);
}
```

- [ ] **步骤 3：编写幂等表 Mapper XML SyncIdempotentLogMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="cn.com.mfish.graph.mapper.SyncIdempotentLogMapper">

    <select id="selectByEventId" resultType="cn.com.mfish.graph.entity.SyncIdempotentLog">
        SELECT * FROM sync_idempotent_log WHERE event_id = #{eventId}
    </select>

    <update id="updateStatusByEventId">
        UPDATE sync_idempotent_log
        SET status = #{status},
            error_message = #{errorMessage},
            retry_count = #{retryCount},
            update_time = NOW()
        WHERE event_id = #{eventId}
    </update>

</mapper>
```

- [ ] **步骤 4：Commit**

```bash
git add mf-business/mf-graph/src/main/java/cn/com/mfish/graph/entity/SyncIdempotentLog.java
git add mf-business/mf-graph/src/main/java/cn/com/mfish/graph/mapper/SyncIdempotentLogMapper.java
git add mf-business/mf-graph/src/main/resources/mapper/SyncIdempotentLogMapper.xml
git commit -m "feat(graph): 添加图同步幂等表实体和Mapper"
```

---

### 任务 3：创建操作日志表实体和 Mapper

**文件：**
- 创建：`mf-business/mf-graph/src/main/java/cn/com/mfish/graph/entity/SyncOperationLog.java`
- 创建：`mf-business/mf-graph/src/main/java/cn/com/mfish/graph/mapper/SyncOperationLogMapper.java`
- 创建：`mf-business/mf-graph/src/main/resources/mapper/SyncOperationLogMapper.xml`

- [ ] **步骤 1：编写操作日志表实体 SyncOperationLog**

```java
package cn.com.mfish.graph.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("sync_operation_log")
public class SyncOperationLog implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String operation;

    private Integer nodeCount;

    private Integer edgeCount;

    private Date startTime;

    private Date endTime;

    private Long durationMs;

    private String status;

    private String errorMessage;

    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
```

- [ ] **步骤 2：编写操作日志 Mapper 接口 SyncOperationLogMapper**

```java
package cn.com.mfish.graph.mapper;

import cn.com.mfish.graph.entity.SyncOperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncOperationLogMapper extends BaseMapper<SyncOperationLog> {
}
```

- [ ] **步骤 3：编写操作日志 Mapper XML SyncOperationLogMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="cn.com.mfish.graph.mapper.SyncOperationLogMapper">

</mapper>
```

- [ ] **步骤 4：Commit**

```bash
git add mf-business/mf-graph/src/main/java/cn/com/mfish/graph/entity/SyncOperationLog.java
git add mf-business/mf-graph/src/main/java/cn/com/mfish/graph/mapper/SyncOperationLogMapper.java
git add mf-business/mf-graph/src/main/resources/mapper/SyncOperationLogMapper.xml
git commit -m "feat(graph): 添加图同步操作日志表实体和Mapper"
```

---

### 任务 4：实现 IdempotentService（幂等服务）

**文件：**
- 创建：`mf-business/mf-graph/src/main/java/cn/com/mfish/graph/service/IdempotentService.java`

- [ ] **步骤 1：编写 IdempotentService**

```java
package cn.com.mfish.graph.service;

import cn.com.mfish.graph.entity.SyncIdempotentLog;
import cn.com.mfish.graph.mapper.SyncIdempotentLogMapper;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;

@Slf4j
@Service
public class IdempotentService {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Autowired
    private SyncIdempotentLogMapper idempotentLogMapper;

    /**
     * 幂等检查并创建处理记录
     * @return null 表示已处理过，非 null 表示需要处理
     */
    public SyncIdempotentLog checkAndCreate(GraphSyncEvent event) {
        String eventId = event.getEventId();

        SyncIdempotentLog existing = idempotentLogMapper.selectByEventId(eventId);
        if (existing != null) {
            String status = existing.getStatus();
            if (STATUS_COMPLETED.equals(status)) {
                log.info("事件已处理完成，跳过: eventId={}", eventId);
                return null;
            }
            if (STATUS_PROCESSING.equals(status)) {
                log.warn("事件正在处理中，防止重复消费: eventId={}", eventId);
                return null;
            }
            log.info("事件处理失败，允许重试: eventId={}", eventId);
        }

        SyncIdempotentLog newLog = new SyncIdempotentLog();
        newLog.setEventId(eventId);
        newLog.setEventType(event.getEventType());
        newLog.setNodeCount(CollectionUtils.isEmpty(event.getNodes()) ? 0 : event.getNodes().size());
        newLog.setEdgeCount(CollectionUtils.isEmpty(event.getEdges()) ? 0 : event.getEdges().size());
        newLog.setStatus(STATUS_PROCESSING);
        newLog.setRetryCount(existing == null ? 0 : existing.getRetryCount() + 1);
        newLog.setCreateTime(new Date());
        newLog.setUpdateTime(new Date());

        if (existing == null) {
            idempotentLogMapper.insert(newLog);
        } else {
            newLog.setId(existing.getId());
            idempotentLogMapper.updateById(newLog);
        }

        return newLog;
    }

    /**
     * 标记处理成功
     */
    public void markCompleted(String eventId) {
        idempotentLogMapper.updateStatusByEventId(eventId, STATUS_COMPLETED, null, null);
        log.info("标记事件处理成功: eventId={}", eventId);
    }

    /**
     * 标记处理失败
     */
    public void markFailed(String eventId, String errorMessage, Integer retryCount) {
        idempotentLogMapper.updateStatusByEventId(eventId, STATUS_FAILED, errorMessage, retryCount);
        log.error("标记事件处理失败: eventId={}, error={}", eventId, errorMessage);
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add mf-business/mf-graph/src/main/java/cn/com/mfish/graph/service/IdempotentService.java
git commit -m "feat(graph): 实现IdempotentService幂等服务"
```

---

### 任务 5：实现 SyncLogService（操作日志服务）

**文件：**
- 创建：`mf-business/mf-graph/src/main/java/cn/com/mfish/graph/service/SyncLogService.java`

- [ ] **步骤 1：编写 SyncLogService**

```java
package cn.com.mfish.graph.service;

import cn.com.mfish.graph.entity.SyncOperationLog;
import cn.com.mfish.graph.mapper.SyncOperationLogMapper;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;

@Slf4j
@Service
public class SyncLogService {

    @Autowired
    private SyncOperationLogMapper operationLogMapper;

    public void logReceive(GraphSyncEvent event) {
        log.info("[MQ接收] eventId={}, eventType={}, nodes={}, edges={}",
            event.getEventId(), event.getEventType(),
            event.getNodes() == null ? 0 : event.getNodes().size(),
            event.getEdges() == null ? 0 : event.getEdges().size());
    }

    public void logSkip(GraphSyncEvent event, String reason) {
        log.info("[MQ跳过] eventId={}, reason={}", event.getEventId(), reason);
    }

    public void logStart(GraphSyncEvent event) {
        SyncOperationLog opLog = new SyncOperationLog();
        opLog.setEventId(event.getEventId());
        opLog.setOperation("PROCESS");
        opLog.setNodeCount(event.getNodes() == null ? 0 : event.getNodes().size());
        opLog.setEdgeCount(event.getEdges() == null ? 0 : event.getEdges().size());
        opLog.setStartTime(new Date());
        opLog.setStatus("PROCESSING");
        operationLogMapper.insert(opLog);
        log.debug("[处理开始] eventId={}", event.getEventId());
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

- [ ] **步骤 2：Commit**

```bash
git add mf-business/mf-graph/src/main/java/cn/com/mfish/graph/service/SyncLogService.java
git commit -m "feat(graph): 实现SyncLogService操作日志服务"
```

---

### 任务 6：扩展 NebulaClient（增加批量 UPSERT 方法）

**文件：**
- 修改：`mf-business/mf-graph/src/main/java/cn/com/mfish/graph/client/NebulaClient.java`

- [ ] **步骤 1：在 NebulaClient 中增加 batchUpsertVertices 方法（约第290行，batchInsertEdges 方法后）**

```java
/**
 * 批量 Upsert 节点（INSERT VERTEX IF NOT EXISTS）
 * 通用化：type 参数即为 NebulaGraph Tag Name
 */
public void batchUpsertVertices(String tagName, List<GraphNode> nodes) {
    if (nodes == null || nodes.isEmpty()) {
        return;
    }

    try (com.vesoft.nebula.client.graph.net.Session session = getSession()) {
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
            throw new RuntimeException("批量Upsert节点失败: " + resultSet.getErrorMessage());
        }

        log.info("批量Upsert节点成功: tagName={}, count={}", tagName, nodes.size());
    } catch (Exception e) {
        log.error("批量Upsert节点异常: tagName={}", tagName, e);
        throw new RuntimeException("批量Upsert节点异常", e);
    }
}
```

- [ ] **步骤 2：在 NebulaClient 中增加 batchUpsertEdges 方法**

```java
/**
 * 批量 Upsert 边（INSERT EDGE IF NOT EXISTS）
 * 通用化：type 参数即为 NebulaGraph Edge Name
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
                .append(escapeString(toJsonString(edge.getProperties())))
                .append(" )");
        }

        ResultSet resultSet = session.execute(ngql.toString());
        if (!resultSet.isSucceeded()) {
            throw new RuntimeException("批量Upsert边失败: " + resultSet.getErrorMessage());
        }

        log.info("批量Upsert边成功: edgeName={}, count={}", edgeName, edges.size());
    } catch (Exception e) {
        log.error("批量Upsert边异常: edgeName={}", edgeName, e);
        throw new RuntimeException("批量Upsert边异常", e);
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add mf-business/mf-graph/src/main/java/cn/com/mfish/graph/client/NebulaClient.java
git commit -m "feat(graph): NebulaClient增加batchUpsertVertices和batchUpsertEdges方法"
```

---

### 任务 7：实现 NebulaWriteService（图库写入服务）

**文件：**
- 创建：`mf-business/mf-graph/src/main/java/cn/com/mfish/graph/service/NebulaWriteService.java`

- [ ] **步骤 1：编写 NebulaWriteService**

```java
package cn.com.mfish.graph.service;

import cn.com.mfish.graph.client.NebulaClient;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.model.edge.GraphEdge;
import cn.com.mfish.graph.model.node.GraphNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NebulaWriteService {

    @Autowired
    private NebulaClient nebulaClient;

    /**
     * 批量写入或更新节点和边（通用化）
     */
    public void upsertNodesAndEdges(GraphSyncEvent event) {
        if ("DELETE".equals(event.getEventType())) {
            deleteNodesAndEdges(event);
            return;
        }

        // 1. 写入节点（按类型分组）
        if (!CollectionUtils.isEmpty(event.getNodes())) {
            Map<String, List<GraphNode>> nodesByType = event.getNodes().stream()
                .collect(Collectors.groupingBy(GraphNode::getType));

            for (Map.Entry<String, List<GraphNode>> entry : nodesByType.entrySet()) {
                String tagName = entry.getKey();
                List<GraphNode> nodes = entry.getValue();
                nebulaClient.batchUpsertVertices(tagName, nodes);
                log.info("批量Upsert节点: tagName={}, count={}", tagName, nodes.size());
            }
        }

        // 2. 写入边（按类型分组）
        if (!CollectionUtils.isEmpty(event.getEdges())) {
            Map<String, List<GraphEdge>> edgesByType = event.getEdges().stream()
                .collect(Collectors.groupingBy(GraphEdge::getType));

            for (Map.Entry<String, List<GraphEdge>> entry : edgesByType.entrySet()) {
                String edgeName = entry.getKey();
                List<GraphEdge> edges = entry.getValue();
                nebulaClient.batchUpsertEdges(edgeName, edges);
                log.info("批量Upsert边: edgeName={}, count={}", edgeName, edges.size());
            }
        }
    }

    /**
     * 批量删除节点和边
     */
    public void deleteNodesAndEdges(GraphSyncEvent event) {
        // 1. 删除边（先删边，避免孤立节点）
        if (!CollectionUtils.isEmpty(event.getEdges())) {
            for (GraphEdge edge : event.getEdges()) {
                nebulaClient.deleteEdge(edge.getType(), edge.getFromId(), edge.getToId());
            }
            log.info("批量删除边: count={}", event.getEdges().size());
        }

        // 2. 删除节点
        if (!CollectionUtils.isEmpty(event.getNodes())) {
            for (GraphNode node : event.getNodes()) {
                nebulaClient.deleteVertex(node.getType(), node.getId());
            }
            log.info("批量删除节点: count={}", event.getNodes().size());
        }
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add mf-business/mf-graph/src/main/java/cn/com/mfish/graph/service/NebulaWriteService.java
git commit -m "feat(graph): 实现NebulaWriteService图库写入服务"
```

---

### 任务 8：重构 GraphSyncConsumer（消费端入口）

**文件：**
- 修改：`mf-business/mf-graph/src/main/java/cn/com/mfish/graph/consumer/GraphSyncConsumer.java`

- [ ] **步骤 1：重构 GraphSyncConsumer 为通用化消费逻辑**

```java
package cn.com.mfish.graph.consumer;

import cn.com.mfish.graph.entity.SyncIdempotentLog;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.service.IdempotentService;
import cn.com.mfish.graph.service.NebulaWriteService;
import cn.com.mfish.graph.service.SyncLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
    consumerGroup = "${rocketmq.consumer.group}",
    topic = "${rocketmq.consumer.topic}",
    consumeMode = ConsumeMode.ORDERLY
)
public class GraphSyncConsumer implements RocketMQListener<GraphSyncEvent> {

    @Autowired
    private IdempotentService idempotentService;

    @Autowired
    private NebulaWriteService nebulaWriteService;

    @Autowired
    private SyncLogService syncLogService;

    @Override
    public void onMessage(GraphSyncEvent event) {
        String eventId = event.getEventId();
        long startTime = System.currentTimeMillis();

        // 1. 记录接收日志
        syncLogService.logReceive(event);

        // 2. 验证事件有效性
        if (eventId == null || event.getEventType() == null) {
            log.error("事件无效，缺少必要字段: eventId={}, eventType={}", eventId, event.getEventType());
            return;
        }

        // 3. 幂等检查
        SyncIdempotentLog idempotentLog = idempotentService.checkAndCreate(event);
        if (idempotentLog == null) {
            syncLogService.logSkip(event, "幂等检查跳过");
            return;
        }

        // 4. 记录处理开始
        syncLogService.logStart(event);
        int retryCount = idempotentLog.getRetryCount();

        try {
            // 5. 根据事件类型处理（通用化：自动识别 type 字段）
            nebulaWriteService.upsertNodesAndEdges(event);

            // 6. 更新幂等状态为成功
            idempotentService.markCompleted(eventId);

            // 7. 记录成功日志
            long duration = System.currentTimeMillis() - startTime;
            syncLogService.logSuccess(event, duration);

            log.info("图同步处理完成: eventId={}, duration={}ms", eventId, duration);

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

- [ ] **步骤 2：Commit**

```bash
git add mf-business/mf-graph/src/main/java/cn/com/mfish/graph/consumer/GraphSyncConsumer.java
git commit -m "refactor(graph): 重构GraphSyncConsumer为通用化消费逻辑"
```

---

### 任务 9：扩展 GraphSyncEvent 模型（增加 properties 字段）

**文件：**
- 修改：`mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/model/node/GraphNode.java`
- 修改：`mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/model/edge/GraphEdge.java`

- [ ] **步骤 1：扩展 GraphNode 增加 properties 字段**

```java
package cn.com.mfish.graph.model.node;

import cn.com.mfish.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class GraphNode extends BaseEntity<String> {
    private String type;
    private Map<String, Object> properties;
}
```

- [ ] **步骤 2：扩展 GraphEdge 增加 properties 字段**

```java
package cn.com.mfish.graph.model.edge;

import lombok.Data;

import java.util.Map;

@Data
public class GraphEdge {
    private String id;
    private String type;
    private String createBy;
    private java.util.Date createTime;
    private String updateBy;
    private java.util.Date updateTime;
    private String fromId;
    private String fromType;
    private String toId;
    private String toType;
    private Map<String, Object> properties;
}
```

- [ ] **步骤 3：Commit**

```bash
git add mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/model/node/GraphNode.java
git add mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/model/edge/GraphEdge.java
git commit -m "feat(graph): GraphNode和GraphEdge增加properties字段支持"
```

---

### 任务 10：改造 PLM 端发送接口（支持批量发送）

**文件：**
- 修改：`mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/PlmGraphSyncClient.java`
- 修改：`mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/impl/PlmGraphSyncClientImpl.java`

- [ ] **步骤 1：扩展 PlmGraphSyncClient 接口增加批量发送方法**

```java
package cn.com.mfish.plm.base.service;

import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.model.edge.GraphEdge;
import cn.com.mfish.graph.model.node.GraphNode;

import java.util.List;

public interface PlmGraphSyncClient {

    <T> void sendGraphSyncEvent(T entity, String nodeType, String eventType);

    void sendGraphSyncEventBatch(List<?> entities, String nodeType, String eventType);

    /**
     * 发送批量同步事件（通用化接口）
     * @param nodes 节点列表
     * @param edges 边列表
     * @param eventType 事件类型：CREATE/UPDATE/DELETE
     */
    void sendBatchSyncEvent(List<GraphNode> nodes, List<GraphEdge> edges, String eventType);
}
```

- [ ] **步骤 2：实现 sendBatchSyncEvent 方法**

```java
@Override
public void sendBatchSyncEvent(List<GraphNode> nodes, List<GraphEdge> edges, String eventType) {
    GraphSyncEvent event = new GraphSyncEvent();
    event.setEventId(UUID.randomUUID().toString());
    event.setEventType(eventType);
    event.setTimestamp(System.currentTimeMillis());
    event.setSource("mf-plm");
    event.setNodes(nodes);
    event.setEdges(edges);
    sendEvent(event);
    log.info("批量发送图同步事件: eventId={}, eventType={}, nodes={}, edges={}",
        event.getEventId(), eventType,
        nodes == null ? 0 : nodes.size(),
        edges == null ? 0 : edges.size());
}
```

- [ ] **步骤 3：Commit**

```bash
git add mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/PlmGraphSyncClient.java
git add mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/impl/PlmGraphSyncClientImpl.java
git commit -m "feat(plm): PlmGraphSyncClient支持批量发送GraphSyncEvent"
```

---

### 任务 11：更新 RocketMQ 配置（mf-graph-dev.yml）

**文件：**
- 修改：`mf-start/mf-start-graph/src/main/resources/mf-graph-dev.yml`

- [ ] **步骤 1：检查并确认 RocketMQ Consumer 配置**

```yaml
rocketmq:
  consumer:
    nameServer: 192.168.111.103:9876
    group: plm-graph-sync-group
    topic: plm-graph-sync
    enableDLQ: true
    maxRetryTimes: 3
```

- [ ] **步骤 2：Commit（如有修改）**

```bash
git add mf-start/mf-start-graph/src/main/resources/mf-graph-dev.yml
git commit -m "chore(graph): 确认RocketMQ Consumer配置"
```

---

### 任务 12：运行数据库 SQL 初始化表结构

- [ ] **步骤 1：执行 sync_tables.sql 创建表**

在 MySQL 中执行：
```sql
source mf-business/mf-graph/src/main/resources/sql/sync_tables.sql;
```

- [ ] **步骤 2：确认表创建成功**

```sql
SHOW TABLES LIKE 'sync_%';
```

---

### 任务 13：编译验证

- [ ] **步骤 1：编译 mf-graph 模块**

```bash
cd e:/windchill/idea_workspace/mfish/my/mfish-nocode
mvn compile -pl mf-business/mf-graph -am -DskipTests
```

- [ ] **步骤 2：编译 mf-common 模块**

```bash
mvn compile -pl mf-common/mf-common-graph,mf-common/mf-common-plm -am -DskipTests
```

- [ ] **步骤 3：确认编译无错误**

预期：无编译错误，输出 BUILD SUCCESS

---

### 任务 14：功能测试验证

- [ ] **步骤 1：启动 mf-graph 服务**

```bash
cd e:/windchill/idea_workspace/mfish/my/mfish-nocode/mf-start/mf-start-graph
mvn spring-boot:run
```

- [ ] **步骤 2：验证幂等表和日志表创建成功**

```sql
SELECT * FROM sync_idempotent_log;
SELECT * FROM sync_operation_log;
```

- [ ] **步骤 3：验证 MQ 消费功能**

模拟发送测试消息，确认日志输出正常

---

## 计划完成

**计划已保存到：** `docs/superpowers/plans/2026-04-17-mfish-plm-graph-sync-consumer-implementation-plan.md`

**执行方式选择：**

1. **子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

2. **内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

**请选择执行方式。**
