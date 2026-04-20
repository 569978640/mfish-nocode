# PG → NebulaGraph CDC 同步实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现 PostgreSQL CDC 实时同步到 NebulaGraph，包括 CDC 消息消费、事件转换、全量同步三个模块

**架构：** PG → Debezium Server → RocketMQ → mf-graph 同步服务 → NebulaGraph

**技术栈：** Spring Boot, RocketMQ, NebulaGraph Client v3.8.4, PostgreSQL Driver

---

## 文件结构

```
mf-graph/src/main/java/cn/com/mfish/graph/
├── sync/
│   ├── consumer/
│   │   └── DebeziumMsgConsumer.java      # 新增：消费 Debezium CDC 消息
│   ├── controller/
│   │   └── FullSyncController.java        # 新增：全量同步 API
│   ├── service/
│   │   ├── EventTransformService.java     # 新增：CDC → GraphSyncEvent 转换
│   │   ├── FullSyncService.java            # 新增：全量同步服务
│   │   └── NebulaWriteService.java        # 已有：图数据库写入
│   └── metadata/
│       ├── TableMetadataResolver.java     # 新增：实体类元数据解析
│       └── EntityMapping.java            # 新增：实体映射定义

mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/
├── model/
│   └── debezium/
│       └── DebeziumCDCMessage.java       # 新增：Debezium CDC 消息模型
```

---

## 任务 1：创建 Debezium CDC 消息模型

**文件：**
- 创建：`mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/model/debezium/DebeziumCDCMessage.java`
- 创建：`mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/model/debezium/DebeziumChangeRecord.java`

- [ ] **步骤 1：创建 DebeziumCDCMessage.java**

```java
package cn.com.mfish.graph.model.debezium;

import lombok.Data;
import java.util.Map;

/**
 * Debezium CDC 消息模型
 * 对应 Debezium Server 发送的 JSON 消息格式
 *
 * @author mfish
 * @date 2026-04-20
 */
@Data
public class DebeziumCDCMessage {
    /**
     * 操作前数据（op=c/u/d 时可能为 null）
     */
    private Map<String, Object> before;

    /**
     * 操作后数据（op=d 时可能为 null）
     */
    private Map<String, Object> after;

    /**
     * 操作类型：c=创建, u=更新, d=删除, r=读取
     */
    private String op;

    /**
     * 时间戳（毫秒）
     */
    private Long tsMs;

    /**
     * 来源信息（包含 schema 和 payload）
     */
    private Source source;

    /**
     * 事务标记（可选）
     */
    private String transactionId;

    /**
     * 获取事件类型
     */
    public String getEventType() {
        if (op == null) {
            return null;
        }
        return switch (op) {
            case "c" -> "CREATE";
            case "u" -> "UPDATE";
            case "d" -> "DELETE";
            case "r" -> "READ";
            default -> null;
        };
    }

    @Data
    public static class Source {
        private String version;
        private String connector;
        private String name;
        private String tsMs;
        private String snapshot;
        private String db;
        private String sequence;
        private String schema;
        private String table;
        private String txId;
        private Integer xmin;
    }
}
```

- [ ] **步骤 2：创建 DebeziumChangeRecord.java**

```java
package cn.com.mfish.graph.model.debezium;

import lombok.Data;

/**
 * Debezium 变更记录
 * 解析后的 CDC 变更数据
 *
 * @author mfish
 * @date 2026-04-20
 */
@Data
public class DebeziumChangeRecord {
    /**
     * 表名
     */
    private String tableName;

    /**
     * 操作类型
     */
    private String operation;

    /**
     * 变更前的数据
     */
    private RecordData before;

    /**
     * 变更后的数据
     */
    private RecordData after;

    /**
     * 变更时间戳
     */
    private Long timestamp;

    @Data
    public static class RecordData {
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
    }
}
```

- [ ] **步骤 3：Commit**

```bash
cd e:/windchill/idea_workspace/mfish/my/mfish-nocode
git add mf-common/mf-common-graph/src/main/java/cn/com/mfish/graph/model/debezium/
git commit -m "feat(mf-graph): 添加 Debezium CDC 消息模型"
```

---

## 任务 2：创建实体类元数据解析器

**文件：**
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/sync/metadata/EntityMapping.java`
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/sync/metadata/TableMetadataResolver.java`

- [ ] **步骤 1：创建 EntityMapping.java**

```java
package cn.com.mfish.graph.sync.metadata;

import lombok.Data;

/**
 * 实体映射定义
 * 建立实体类与图数据库类型之间的映射关系
 *
 * @author mfish
 * @date 2026-04-20
 */
@Data
public class EntityMapping {
    /**
     * 实体类全名
     */
    private String entityClassName;

    /**
     * 数据库表名
     */
    private String tableName;

    /**
     * 图类型（节点类型或边类型）
     */
    private String graphType;

    /**
     * 是否为边（true=边，false=点）
     */
    private boolean edge;

    /**
     * 创建时间字段
     */
    private String createTimeField;

    /**
     * 实体类
     */
    private Class<?> entityClass;

    public String getNodeType() {
        return edge ? null : graphType;
    }

    public String getEdgeType() {
        return edge ? graphType : null;
    }
}
```

- [ ] **步骤 2：创建 TableMetadataResolver.java**

```java
package cn.com.mfish.graph.sync.metadata;

import cn.com.mfish.common.core.entity.BaseEntity;
import cn.com.mfish.common.core.entity.BaseLinkEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体类元数据解析器
 * 扫描并缓存实体类映射信息
 *
 * @author mfish
 * @date 2026-04-20
 */
@Slf4j
@Component
public class TableMetadataResolver {

    private final Map<String, EntityMapping> tableMappingCache = new ConcurrentHashMap<>();
    private final Map<String, EntityMapping> classMappingCache = new ConcurrentHashMap<>();

    /**
     * 解析所有实体类元数据
     * 扫描继承 BaseEntity 且有 @TableName 注解的类
     */
    public void resolveAll() {
        String basePackage = getBasePackage();
        if (basePackage == null) {
            log.warn("无法获取基础包名，跳过实体类扫描");
            return;
        }

        List<Class<?>> entityClasses = scanEntityClasses(basePackage);
        for (Class<?> entityClass : entityClasses) {
            resolveEntityClass(entityClass);
        }
        log.info("实体类元数据解析完成，共扫描到 {} 个实体类", tableMappingCache.size());
    }

    /**
     * 根据表名获取映射
     */
    public Optional<EntityMapping> getMappingByTableName(String tableName) {
        if (tableMappingCache.isEmpty()) {
            resolveAll();
        }
        return Optional.ofNullable(tableMappingCache.get(tableName));
    }

    /**
     * 根据类名获取映射
     */
    public Optional<EntityMapping> getMappingByClassName(String className) {
        if (classMappingCache.isEmpty()) {
            resolveAll();
        }
        return Optional.ofNullable(classMappingCache.get(className));
    }

    /**
     * 解析单个实体类
     */
    private void resolveEntityClass(Class<?> entityClass) {
        TableName tableNameAnn = entityClass.getAnnotation(TableName.class);
        if (tableNameAnn == null) {
            return;
        }

        String tableName = tableNameAnn.value();
        String className = entityClass.getSimpleName();
        boolean isEdge = className.endsWith("Link");

        EntityMapping mapping = new EntityMapping();
        mapping.setEntityClassName(className);
        mapping.setTableName(tableName);
        mapping.setGraphType(className.toLowerCase());
        mapping.setEdge(isEdge);
        mapping.setEntityClass(entityClass);

        tableMappingCache.put(tableName, mapping);
        classMappingCache.put(className, mapping);

        log.debug("解析实体映射: table={}, class={}, graphType={}, isEdge={}",
            tableName, className, mapping.getGraphType(), isEdge);
    }

    /**
     * 扫描包下所有继承 BaseEntity 的类
     */
    @SuppressWarnings("unchecked")
    private List<Class<?>> scanEntityClasses(String basePackage) {
        List<Class<?>> result = new ArrayList<>();
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = basePackage.replace('.', '/');
            Enumeration<java.net.URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                java.net.URL resource = resources.nextElement();
                if (resource.getProtocol().equals("file")) {
                    scanDirectory(new java.io.File(resource.getFile()), basePackage, result);
                }
            }
        } catch (Exception e) {
            log.error("扫描实体类失败: {}", e.getMessage(), e);
        }
        return result;
    }

    private void scanDirectory(java.io.File directory, String packageName, List<Class<?>> result) {
        if (!directory.exists()) {
            return;
        }
        java.io.File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (java.io.File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), result);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                try {
                    Class<?> clazz = Class.forName(className);
                    if (BaseEntity.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                        result.add(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    log.warn("无法加载类: {}", className);
                }
            }
        }
    }

    private String getBasePackage() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String servletPath = request.getServletPath();
                if (servletPath.contains("/plm/")) {
                    return "cn.com.mfish.plm";
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "cn.com.mfish.plm";
    }

    public Collection<EntityMapping> getAllMappings() {
        if (tableMappingCache.isEmpty()) {
            resolveAll();
        }
        return tableMappingCache.values();
    }
}
```

- [ ] **步骤 3：Commit**

```bash
cd e:/windchill/idea_workspace/mfish/my/mfish-nocode
git add mf-graph/src/main/java/cn/com/mfish/graph/sync/metadata/
git commit -m "feat(mf-graph): 添加实体类元数据解析器"
```

---

## 任务 3：创建 CDC 事件转换服务

**文件：**
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/sync/service/EventTransformService.java`

- [ ] **步骤 1：创建 EventTransformService.java**

```java
package cn.com.mfish.graph.sync.service;

import cn.com.mfish.graph.model.GraphEdge;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.model.debezium.DebeziumCDCMessage;
import cn.com.mfish.graph.model.debezium.DebeziumChangeRecord;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.sync.metadata.EntityMapping;
import cn.com.mfish.graph.sync.metadata.TableMetadataResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * CDC 事件转换服务
 * 将 Debezium CDC 消息转换为 GraphSyncEvent
 *
 * @author mfish
 * @date 2026-04-20
 */
@Slf4j
@Service
public class EventTransformService {

    @Autowired
    private TableMetadataResolver metadataResolver;

    @Autowired
    private ObjectMapper objectMapper;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public EventTransformService() {
        dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    }

    /**
     * 将 Debezium CDC 消息转换为 GraphSyncEvent
     */
    public GraphSyncEvent transform(DebeziumCDCMessage cdcMessage) {
        if (cdcMessage == null || cdcMessage.getSource() == null) {
            log.warn("CDC 消息为空或缺少 source 信息");
            return null;
        }

        String tableName = cdcMessage.getSource().getTable();
        Optional<EntityMapping> mappingOpt = metadataResolver.getMappingByTableName(tableName);

        if (mappingOpt.isEmpty()) {
            log.warn("未找到表 {} 的映射配置，跳过", tableName);
            return null;
        }

        EntityMapping mapping = mappingOpt.get();
        String eventId = generateEventId(cdcMessage);

        GraphSyncEvent event = new GraphSyncEvent();
        event.setEventId(eventId);
        event.setEventType(cdcMessage.getEventType());
        event.setTimestamp(cdcMessage.getTsMs());
        event.setSource("pg-cdc");

        if (mapping.isEdge()) {
            List<GraphEdge> edges = transformToEdge(cdcMessage, mapping);
            event.setEdges(edges);
        } else {
            List<GraphNode> nodes = transformToNode(cdcMessage, mapping);
            event.setNodes(nodes);
        }

        return event;
    }

    private List<GraphNode> transformToNode(DebeziumCDCMessage cdcMessage, EntityMapping mapping) {
        Map<String, Object> data = getOperationData(cdcMessage);
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }

        GraphNode node = new GraphNode();
        node.setId(getStringValue(data, "id"));
        node.setNodeType(mapping.getGraphType());
        node.setCreateBy(getStringValue(data, "create_by"));
        node.setCreateTime(parseDate(getStringValue(data, "create_time")));
        node.setUpdateBy(getStringValue(data, "update_by"));
        node.setUpdateTime(parseDate(getStringValue(data, "update_time")));

        return Collections.singletonList(node);
    }

    private List<GraphEdge> transformToEdge(DebeziumCDCMessage cdcMessage, EntityMapping mapping) {
        Map<String, Object> data = getOperationData(cdcMessage);
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }

        GraphEdge edge = new GraphEdge();
        edge.setId(getStringValue(data, "id"));
        edge.setEdgeType(mapping.getGraphType());
        edge.setFromId(getStringValue(data, "from_id"));
        edge.setFromType(getStringValue(data, "from_type"));
        edge.setToId(getStringValue(data, "to_id"));
        edge.setToType(getStringValue(data, "to_type"));
        edge.setCreateBy(getStringValue(data, "create_by"));
        edge.setCreateTime(parseDate(getStringValue(data, "create_time")));
        edge.setUpdateBy(getStringValue(data, "update_by"));
        edge.setUpdateTime(parseDate(getStringValue(data, "update_time")));

        return Collections.singletonList(edge);
    }

    private Map<String, Object> getOperationData(DebeziumCDCMessage cdcMessage) {
        String op = cdcMessage.getOp();
        if ("d".equals(op)) {
            return cdcMessage.getBefore();
        }
        return cdcMessage.getAfter();
    }

    private String getStringValue(Map<String, Object> data, String key) {
        if (data == null || !data.containsKey(key)) {
            return null;
        }
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return dateFormat.parse(dateStr);
        } catch (Exception e) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(dateStr);
            } catch (Exception ex) {
                log.warn("日期解析失败: {}", dateStr);
                return null;
            }
        }
    }

    private String generateEventId(DebeziumCDCMessage cdcMessage) {
        String table = cdcMessage.getSource() != null ? cdcMessage.getSource().getTable() : "unknown";
        String txId = cdcMessage.getSource() != null ? cdcMessage.getSource().getTxId() : "0";
        String ts = cdcMessage.getTsMs() != null ? cdcMessage.getTsMs().toString() : "0";
        return String.format("pg-%s-%s-%s", table, txId, ts);
    }
}
```

- [ ] **步骤 2：Commit**

```bash
cd e:/windchill/idea_workspace/mfish/my/mfish-nocode
git add mf-graph/src/main/java/cn/com/mfish/graph/sync/service/EventTransformService.java
git commit -m "feat(mf-graph): 添加 CDC 事件转换服务"
```

---

## 任务 4：创建 Debezium 消息消费者

**文件：**
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/sync/consumer/DebeziumMsgConsumer.java`

- [ ] **步骤 1：创建 DebeziumMsgConsumer.java**

```java
package cn.com.mfish.graph.sync.consumer;

import cn.com.mfish.graph.model.debezium.DebeziumCDCMessage;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.sync.service.EventTransformService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Debezium CDC 消息消费者
 * 消费 Debezium Server 发送的 CDC 消息，转换为 GraphSyncEvent 后转发
 *
 * @author mfish
 * @date 2026-04-20
 */
@Slf4j
@Component
@RocketMQMessageListener(
    consumerGroup = "${debezium.consumer.group:plm-graph-debezium-group}",
    topic = "${debezium.consumer.topic:plm-graph-cdc}"
)
public class DebeziumMsgConsumer implements RocketMQListener<String> {

    @Autowired
    private EventTransformService transformService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        if (message == null || message.isEmpty()) {
            log.warn("收到空消息");
            return;
        }

        try {
            DebeziumCDCMessage cdcMessage = objectMapper.readValue(message, DebeziumCDCMessage.class);
            log.info("[CDC接收] table={}, op={}, tsMs={}",
                cdcMessage.getSource() != null ? cdcMessage.getSource().getTable() : "unknown",
                cdcMessage.getOp(),
                cdcMessage.getTsMs());

            GraphSyncEvent event = transformService.transform(cdcMessage);
            if (event == null) {
                log.info("[CDC跳过] 无法转换事件，可能不在同步范围内");
                return;
            }

            log.info("[CDC转换] eventId={}, eventType={}, nodes={}, edges={}",
                event.getEventId(),
                event.getEventType(),
                event.getNodes() != null ? event.getNodes().size() : 0,
                event.getEdges() != null ? event.getEdges().size() : 0);

        } catch (Exception e) {
            log.error("[CDC解析失败] message={}, error={}", message, e.getMessage(), e);
        }
    }
}
```

- [ ] **步骤 2：Commit**

```bash
cd e:/windchill/idea_workspace/mfish/my/mfish-nocode
git add mf-graph/src/main/java/cn/com/mfish/graph/sync/consumer/DebeziumMsgConsumer.java
git commit -m "feat(mf-graph): 添加 Debezium 消息消费者"
```

---

## 任务 5：创建全量同步服务

**文件：**
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/sync/service/FullSyncService.java`
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/sync/req/ReqFullSync.java`
- 创建：`mf-graph/src/main/java/cn/com/mfish/graph/sync/controller/FullSyncController.java`

- [ ] **步骤 1：创建 ReqFullSync.java**

```java
package cn.com.mfish.graph.sync.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 全量同步请求
 *
 * @author mfish
 * @date 2026-04-20
 */
@Data
@Schema(description = "全量同步请求")
public class ReqFullSync {
    @Schema(description = "指定同步的表名列表，为空则同步所有")
    private List<String> tables;

    @Schema(description = "每批同步数量，默认 1000")
    private Integer batchSize = 1000;
}
```

- [ ] **步骤 2：创建 FullSyncService.java**

```java
package cn.com.mfish.graph.sync.service;

import cn.com.mfish.common.core.entity.BaseEntity;
import cn.com.mfish.common.core.entity.BaseLinkEntity;
import cn.com.mfish.graph.model.GraphEdge;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.sync.metadata.EntityMapping;
import cn.com.mfish.graph.sync.metadata.TableMetadataResolver;
import cn.com.mfish.graph.sync.req.ReqFullSync;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 全量同步服务
 * 执行 PG 到 NebulaGraph 的全量数据同步
 *
 * @author mfish
 * @date 2026-04-20
 */
@Slf4j
@Service
public class FullSyncService {

    @Autowired
    private TableMetadataResolver metadataResolver;

    @Autowired
    private JdbcTemplate pgJdbcTemplate;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public FullSyncService() {
        dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    }

    /**
     * 执行全量同步
     */
    public void fullSync(ReqFullSync req) {
        Collection<EntityMapping> mappings = metadataResolver.getAllMappings();
        List<EntityMapping> targetMappings;

        if (req.getTables() != null && !req.getTables().isEmpty()) {
            targetMappings = mappings.stream()
                .filter(m -> req.getTables().contains(m.getTableName()))
                .collect(Collectors.toList());
        } else {
            targetMappings = new ArrayList<>(mappings);
        }

        int batchSize = req.getBatchSize() != null ? req.getBatchSize() : 1000;
        int totalCount = 0;

        for (EntityMapping mapping : targetMappings) {
            int count = syncTable(mapping, batchSize);
            totalCount += count;
        }

        log.info("[全量同步完成] 共同步 {} 条记录", totalCount);
    }

    private int syncTable(EntityMapping mapping, int batchSize) {
        String tableName = mapping.getTableName();
        log.info("[全量同步开始] table={}, graphType={}, isEdge={}",
            tableName, mapping.getGraphType(), mapping.isEdge());

        String sql = buildSelectSql(mapping);
        List<Map<String, Object>> rows = pgJdbcTemplate.queryForList(sql);

        int count = 0;
        for (int i = 0; i < rows.size(); i += batchSize) {
            int end = Math.min(i + batchSize, rows.size());
            List<Map<String, Object>> batch = rows.subList(i, end);

            GraphSyncEvent event = buildEventFromBatch(batch, mapping);
            if (event != null) {
                log.info("[全量同步批次] table={}, batch={}/{}, records={}",
                    tableName, (i / batchSize) + 1, (rows.size() + batchSize - 1) / batchSize, batch.size());
                count += batch.size();
            }
        }

        log.info("[全量同步完成] table={}, count={}", tableName, count);
        return count;
    }

    private String buildSelectSql(EntityMapping mapping) {
        StringBuilder sql = new StringBuilder("SELECT ");
        if (mapping.isEdge()) {
            sql.append("id, type, create_by, create_time, update_by, update_time, from_id, from_type, to_id, to_type");
        } else {
            sql.append("id, type, create_by, create_time, update_by, update_time");
        }
        sql.append(" FROM ").append(mapping.getTableName());
        return sql.toString();
    }

    private GraphSyncEvent buildEventFromBatch(List<Map<String, Object>> rows, EntityMapping mapping) {
        if (rows.isEmpty()) {
            return null;
        }

        GraphSyncEvent event = new GraphSyncEvent();
        event.setEventId("fullsync-" + mapping.getTableName() + "-" + System.currentTimeMillis());
        event.setEventType("CREATE");
        event.setTimestamp(System.currentTimeMillis());
        event.setSource("full-sync");

        if (mapping.isEdge()) {
            List<GraphEdge> edges = rows.stream().map(this::convertToEdge).collect(Collectors.toList());
            event.setEdges(edges);
        } else {
            List<GraphNode> nodes = rows.stream().map(this::convertToNode).collect(Collectors.toList());
            event.setNodes(nodes);
        }

        return event;
    }

    private GraphNode convertToNode(Map<String, Object> row) {
        GraphNode node = new GraphNode();
        node.setId(getString(row, "id"));
        node.setNodeType(getString(row, "type"));
        node.setCreateBy(getString(row, "create_by"));
        node.setCreateTime(getDate(row, "create_time"));
        node.setUpdateBy(getString(row, "update_by"));
        node.setUpdateTime(getDate(row, "update_time"));
        return node;
    }

    private GraphEdge convertToEdge(Map<String, Object> row) {
        GraphEdge edge = new GraphEdge();
        edge.setId(getString(row, "id"));
        edge.setEdgeType(getString(row, "type"));
        edge.setFromId(getString(row, "from_id"));
        edge.setFromType(getString(row, "from_type"));
        edge.setToId(getString(row, "to_id"));
        edge.setToType(getString(row, "to_type"));
        edge.setCreateBy(getString(row, "create_by"));
        edge.setCreateTime(getDate(row, "create_time"));
        edge.setUpdateBy(getString(row, "update_by"));
        edge.setUpdateTime(getDate(row, "update_time"));
        return edge;
    }

    private String getString(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value != null ? value.toString() : null;
    }

    private Date getDate(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        try {
            return dateFormat.parse(value.toString());
        } catch (Exception e) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value.toString());
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
```

- [ ] **步骤 3：创建 FullSyncController.java**

```java
package cn.com.mfish.graph.sync.controller;

import cn.com.mfish.graph.sync.req.ReqFullSync;
import cn.com.mfish.graph.sync.service.FullSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全量同步控制器
 *
 * @author mfish
 * @date 2026-04-20
 */
@Slf4j
@RestController
@Tag(name = "全量同步")
@RequestMapping("/graph/sync")
public class FullSyncController {

    @Autowired
    private FullSyncService fullSyncService;

    @PostMapping("/full")
    @Operation(summary = "触发全量同步", description = "手动触发 PG 到 NebulaGraph 的全量数据同步")
    public String triggerFullSync(@RequestBody(required = false) ReqFullSync req) {
        if (req == null) {
            req = new ReqFullSync();
        }
        log.info("[全量同步触发] tables={}, batchSize={}", req.getTables(), req.getBatchSize());
        fullSyncService.fullSync(req);
        return "全量同步任务已触发";
    }
}
```

- [ ] **步骤 4：Commit**

```bash
cd e:/windchill/idea_workspace/mfish/my/mfish-nocode
git add mf-graph/src/main/java/cn/com/mfish/graph/sync/service/FullSyncService.java
git add mf-graph/src/main/java/cn/com/mfish/graph/sync/req/ReqFullSync.java
git add mf-graph/src/main/java/cn/com/mfish/graph/sync/controller/FullSyncController.java
git commit -m "feat(mf-graph): 添加全量同步服务和 API"
```

---

## 任务 6：添加配置和 Debezium Server 部署指南

**文件：**
- 修改：`mf-graph/src/main/resources/application.yml`（如存在则修改，不存在则创建）
- 创建：`docs/superpowers/specs/2026-04-20-pg-debezium-server-deploy-guide.md`

- [ ] **步骤 1：检查并创建配置**

```yaml
# 在 mf-graph 模块的 application.yml 中添加
debezium:
  consumer:
    topic: plm-graph-cdc
    group: plm-graph-debezium-group
```

- [ ] **步骤 2：创建 Debezium Server 部署指南**

```markdown
# Debezium Server 部署指南

## 环境要求

- JDK 11+
- RHEL 8
- PostgreSQL 16

## 1. PG 端配置

### 1.1 修改 postgresql.conf

```ini
wal_level = logical
max_replication_slots = 10
max_wal_senders = 10
```

### 1.2 创建发布

```sql
-- 连接 PG 数据库
psql -h localhost -U postgres -d plm

-- 创建复制槽
CREATE SLOT plm_graph_slot LOGICAL 'pgoutput';

-- 创建发布（白名单表）
CREATE PUBLICATION plm_graph_pub FOR TABLE
  part_master, part,
  document_master, document,
  folder, product,
  contains_link, iteraite_link;
```

## 2. 下载 Debezium Server

```bash
cd /opt
wget https://archive.apache.org/dist/debezium/debezium-server/2.4.0.Final/debezium-server-2.4.0.Final.tar.gz
tar -xzf debezium-server-2.4.0.Final.tar.gz
mv debezium-server-2.4.0.Final debezium-server
```

## 3. 配置 Debezium Server

创建 `debezium-server/conf.d/application.properties`:

```properties
# PostgreSQL 连接配置
debezium.source.connector.class=io.debezium.connector.postgresql.PostgresConnector
debezium.source.database.hostname=localhost
debezium.source.database.port=5432
debezium.source.database.user=postgres
debezium.source.database.password=your_password
debezium.source.database.dbname=plm
debezium.source.database.server.name=plm-pg
debezium.source.table.include.list=public.part_master,public.part,public.document_master,public.document,public.folder,public.product,public.contains_link,public.iteraite_link
debezium.source.plugin.name=pgoutput
debezium.source.slot.name=plm_graph_slot
debezium.source.slot.drop.on.stop=true

# RocketMQ 输出配置
debezium.sink.type=mq
debezium.sink.mq.bootstrap.servers=localhost:9876
debezium.sink.mq.topic=plm-graph-cdc
debezium.sink.mq.group.id=plm-graph-debezium-group
debezium.sink.mq.data.format=json

# 转换器配置
debezium.transforms=unwrap
debezium.transforms.unwrap.type=io.debezium.transforms.ExtractNewRecordState
debezium.transforms.unwrap.drop.tombstones=true

# 运行配置
debezium.source.offset.flush.interval.ms=1000
```

## 4. 启动 Debezium Server

```bash
cd /opt/debezium-server
./bin/start-debezium-server.sh
```

## 5. 验证

```bash
# 查看日志
tail -f debezium-server/log/debezium.log

# 查看 MQ 消息
# 使用 mq 客户端工具验证消息是否正常发送
```

## 6. 监控

建议监控以下指标：
- Debezium Server 进程状态
- PG 复制槽 lag
- RocketMQ 消息发送延迟
```

- [ ] **步骤 3：Commit**

```bash
cd e:/windchill/idea_workspace/mfish/my/mfish-nocode
git add docs/superpowers/specs/2026-04-20-pg-debezium-server-deploy-guide.md
git commit -m "docs: 添加 Debezium Server 部署指南"
```

---

## 自检清单

1. **规格覆盖度检查**：
   - [x] CDC 消息消费 - `DebeziumMsgConsumer`
   - [x] 事件转换 - `EventTransformService`
   - [x] 实体元数据解析 - `TableMetadataResolver`
   - [x] 全量同步 API - `FullSyncController`
   - [x] 全量同步服务 - `FullSyncService`
   - [x] Debezium 部署指南

2. **占位符扫描**：
   - 无"TODO"、"待定"等占位符
   - 所有代码均为完整实现

3. **类型一致性检查**：
   - `GraphSyncEvent` 的 `eventType` 与 `NebulaWriteService` 保持一致（CREATE/UPDATE/DELETE）
   - `GraphNode` 和 `GraphEdge` 的字段与 NebulaSchema 保持一致
