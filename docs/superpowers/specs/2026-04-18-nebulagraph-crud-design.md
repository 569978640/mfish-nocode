# NebulaGraph 3.8.0 CURD 基建设计文档

## 1. 概述

### 1.1 文档目的

本文档旨在为 mfish 平台 `mf-common-graph` 模块设计一套完整的 NebulaGraph 图数据库 CURD 基建方案，基于 vesoft client 3.8.0 原生客户端实现，覆盖 PLM 业务图谱的完整数据操作能力。

### 1.2 背景与现状

- **业务场景**：PLM 模块业务数据的图谱存储与查询
- **现有实现**：基于 HTTP REST API 的 `NebulaClient`，存在性能瓶颈、不支持事务、连接不稳定等问题
- **重构目标**：基于 vesoft client 原生 Session 彻底重写，提供生产级的图数据库操作能力

### 1.3 设计决策

| 维度 | 决策 | 说明 |
|------|------|------|
| 客户端 | vesoft client 3.8.0 | 官方原生客户端，支持 Session 池化 |
| 会话管理 | 队列池化 + 借还模式 | 废弃 ThreadLocal，线程安全队列管理 |
| Schema 设计 | 混用模式 + 版本管理 | 固定基础类型 + 动态扩展类型，扩展字段需版本管控 |
| 一致性模型 | 幂等 + 重试 + 补偿 | Nebula 3.8.0 不支持原生事务，采用最终一致性 |
| 集成方式 | 完全重构 | 移除 HTTP 实现，基于原生 client 重写 |

### 1.4 重要澄清：NebulaGraph 事务能力

**NebulaGraph 3.8.0 不支持原生事务**。本模块采用 **"幂等操作 + 重试机制 + 补偿机制"** 模式保证最终一致性：

- 写入操作使用 `IF NOT EXISTS` 语义确保幂等性
- 批量操作采用 **批量提交** 而非事务提交
- 失败后通过幂等键（IdempotentKey）重试
- 批量失败率超阈值时触发补偿机制
- 业务层需接受 **最终一致性** 而非 **强一致性**

---

## 2. 整体架构

### 2.1 目录结构

```
mf-common-graph/
├── config/
│   ├── NebulaConfig.java              # 配置属性类（保留）
│   ├── NebulaPoolConfig.java          # 连接池配置
│   ├── NebulaGraphProperties.java     # Graph 连接配置
│   ├── NebulaQuotaConfig.java         # 动态 Schema 配额配置
│   ├── RetryConfig.java               # 重试策略配置
│   ├── CircuitBreakerConfig.java     # 熔断器配置
│   └── LoadBalanceConfig.java        # 负载均衡配置
├── pool/
│   ├── SessionWrapper.java           # Session 包装器（状态管理）
│   ├── NebulaSessionPool.java        # 线程安全会话池
│   ├── WriteSessionPool.java         # 写入池（主节点）
│   ├── ReadSessionPool.java          # 读取池（从节点优先）
│   ├── SessionPoolInitializer.java   # 连接池初始化器
│   ├── SessionPoolScaler.java        # 连接池动态扩缩容
│   └── SessionPoolMonitor.java       # 连接池监控（泄漏检测）
├── client/
│   └── NebulaGraphClient.java         # 主客户端门面
├── schema/
│   ├── SchemaUtils.java               # 全局工具类：反引号、大小写校验
│   ├── FixedSchemaManager.java        # 固定 Schema 管理器
│   ├── DynamicSchemaManager.java      # 动态 Schema 管理器（含灰度/校验）
│   ├── SchemaVersionManager.java      # Schema 版本管理器
│   ├── SchemaChangeLock.java         # Schema 变更分布式锁
│   ├── FieldTypeValidator.java        # 字段类型校验器
│   └── model/
│       ├── TagDefinition.java         # Tag 定义
│       └── EdgeTypeDefinition.java    # EdgeType 定义
├── crud/
│   ├── NodeOperation.java             # 节点 CRUD
│   ├── EdgeOperation.java             # 边 CRUD
│   ├── BatchOperation.java           # 批量操作（分片 + 幂等）
│   ├── BatchConsistencyChecker.java  # 批量一致性校验器
│   ├── BatchCompensator.java        # 批量补偿器
│   ├── IdempotentKeyGenerator.java   # 幂等键生成器
│   ├── IdempotentStore.java          # 幂等键存储（Redis）
│   └── SoftDeleteCleaner.java        # 软删除清理器
├── query/
│   ├── QueryBuilder.java              # 查询构建器（参数化）
│   ├── PathQuery.java                 # 路径查询封装
│   └── VersionedQuery.java           # 版本化查询（时间轴）
├── index/
│   └── IndexManager.java              # 索引生命周期管理
├── plm/
│   ├── PLMNodeService.java            # PLM 节点业务 API
│   ├── PLMEdgeService.java            # PLM 边业务 API
│   ├── PLMPathQuery.java              # PLM 路径查询封装（含循环检测）
│   ├── BOMDiffService.java           # BOM 增量更新服务
│   ├── LargePropertyManager.java     # 大属性管理器（MinIO）
│   └── cache/
│       ├── PLMCache.java             # PLM 业务缓存
│       └── BloomFilter.java          # 布隆过滤器（防穿透）
├── model/
│   ├── node/
│   │   └── GraphNode.java            # 节点模型
│   ├── edge/
│   │   └── GraphEdge.java            # 边模型
│   ├── vid/
│   │   ├── VidMapper.java            # VID 映射管理器
│   │   └── VidVersionManager.java    # VID 版本管理器
│   └── result/
│       ├── QueryResult.java           # 查询结果封装
│       └── BatchResult.java          # 批量操作结果
├── monitor/
│   ├── SlowQueryLog.java             # 慢查询日志（含上下文）
│   ├── SlowQueryRateLimiter.java     # 慢查询限流器
│   ├── OperationMetrics.java          # 操作耗时统计
│   ├── SchemaMetrics.java             # Schema 变更指标
│   ├── IdempotentMetrics.java        # 幂等键指标
│   ├── CircuitBreaker.java           # 熔断器（多维度）
│   ├── CircuitBreakerManager.java     # 熔断器管理（人工干预）
│   ├── SessionPoolMonitor.java       # 连接池监控
│   ├── AddressManager.java           # 地址管理器（故障标记）
│   ├── AlertManager.java             # 告警管理器
│   └── NebulaMonitor.java            # 异常监控告警
├── disaster/
│   ├── BackupManager.java            # 备份管理器
│   └── DisasterRecoveryManager.java  # 灾备恢复管理器
├── remote/
│   └── ConfigCenterRefresher.java   # 配置中心刷新器（Nacos/Apollo）
└── exception/
    ├── NebulaGraphException.java     # 统一异常基类
    ├── ConnectionException.java      # 连接异常
    ├── SchemaException.java         # Schema 异常
    ├── TimeoutException.java         # 超时异常
    └── BusinessException.java       # 业务异常
```

---

## 3. 核心组件设计

### 3.1 配置模块

#### 3.1.1 NebulaGraphProperties（增强版）

```java
@Data
public class NebulaGraphProperties {
    /** 连接地址列表（支持多地址，高可用） */
    private List<String> addresses;
    /** 用户名 */
    private String username = "root";
    /** 密码 */
    private String password = "nebula";
    /** 图空间名称 */
    private String space = "plm_graph";
    /** 连接超时(ms) */
    private int connectTimeout = 3000;
    /** 读写超时(ms) */
    private int socketTimeout = 30000;
    /** 重试次数 */
    private int retry = 3;
    /** 负载均衡策略 */
    private LoadBalanceStrategy loadBalanceStrategy = LoadBalanceStrategy.FAILOVER;
    /** 地址权重配置 */
    private Map<String, Integer> addressWeights;
}
```

#### 3.1.2 LoadBalanceConfig

```java
@Data
public class LoadBalanceConfig {
    /** 负载均衡策略 */
    private LoadBalanceStrategy strategy = LoadBalanceStrategy.FAILOVER;
    /** 地址权重映射 */
    private Map<String, Integer> weights;
    /** 加权模式下权重刷新间隔(s) */
    private long weightRefreshInterval = 60;
    /** 故障恢复检测间隔(s) */
    private long failureDetectionInterval = 10;
    /** 连续失败次数阈值 */
    private int failureThreshold = 3;
}

/** 负载均衡策略枚举 */
public enum LoadBalanceStrategy {
    ROUND_ROBIN,  // 轮询
    WEIGHTED,     // 加权（基于节点负载）
    FAILOVER      // 故障优先（优先可用节点）
}
```

#### 3.1.3 NebulaPoolConfig

```java
@Data
public class NebulaPoolConfig {
    /** 最小空闲连接数（池中保持的最小连接数） */
    private int minIdle = 10;
    /** 最大连接数（池中允许的最大连接数） */
    private int maxPoolSize = 100;
    /** 空闲超时(s)，超过后释放空闲连接 */
    private int idleTimeout = 60;
    /** 心跳间隔(s)，保持连接活跃 */
    private int heartbeatInterval = 30;
    /** 连接最大生命周期(s)，到达后强制销毁重建 */
    private int maxLifetime = 3600;
    /** 借取等待超时(ms)，超时抛异常 */
    private int borrowTimeout = 5000;
    /** 拒绝策略：ABORT-抛异常 / DISCARD-丢弃请求 / RETURN_NULL-返回null */
    private RejectPolicy rejectPolicy = RejectPolicy.ABORT;
    /** 初始化策略：EAGER-预热 / LAZY-懒加载 */
    private InitStrategy initStrategy = InitStrategy.EAGER;
    /** 扩缩容检测间隔(s) */
    private int scaleInterval = 60;
    /** 扩容阈值（活跃连接占比 > 此值时扩容） */
    private double scaleUpThreshold = 0.8;
    /** 缩容阈值（活跃连接占比 < 此值时缩容） */
    private double scaleDownThreshold = 0.3;
    /** 活跃会话超时检测间隔(s) */
    private int activeSessionCheckInterval = 30;
    /** 活跃会话超时时间(s)，超过后强制回收 */
    private int activeSessionTimeout = 30;
}
```

#### 3.1.4 NebulaQuotaConfig

```java
@Data
public class NebulaQuotaConfig {
    /** 单个 Space 最大 Tag 数 */
    private int maxTagsPerSpace = 100;
    /** 单个 Space 最大 EdgeType 数 */
    private int maxEdgeTypesPerSpace = 100;
    /** 单个 Tag 最大字段数 */
    private int maxFieldsPerTag = 50;
    /** 单个 EdgeType 最大字段数 */
    private int maxFieldsPerEdgeType = 50;
    /** 动态 Schema 数量上限 */
    private int maxDynamicSchemas = 50;
    /** 配额校验间隔(s)，定时检测是否超配额 */
    private int quotaCheckInterval = 300;
    /** 版本存储介质：MYSQL / REDIS / NEBULA_GRAPH */
    private VersionStorageType versionStorageType = VersionStorageType.NEBULA_GRAPH;
    /** 历史版本保留数量 */
    private int retainVersionCount = 10;
}
```

#### 3.1.5 RetryConfig

```java
@Data
public class RetryConfig {
    /** 初始重试间隔(ms)，首次失败后等待时间 */
    private long baseInterval = 1000;
    /** 最大重试间隔(ms)，防止间隔过长 */
    private long maxInterval = 4000;
    /** 最大重试次数 */
    private int maxRetries = 3;
    /** 重试指数（如 2.0 表示间隔翻倍） */
    private double multiplier = 2.0;
    /** 批量失败告警阈值（失败率超过此值时告警） */
    private double batchFailureAlertThreshold = 0.5;
    /** 幂等键续期间隔(s)，长批量操作定期续期防止过期 */
    private int idempotentRenewInterval = 30;
    /** 补偿失败告警阈值 */
    private double compensateFailureAlertThreshold = 0.3;
}
```

#### 3.1.6 CircuitBreakerConfig

```java
@Data
public class CircuitBreakerConfig {
    /** 失败率阈值（超过此值时触发熔断） */
    private double failureRateThreshold = 0.5;
    /** 最小请求数（达到此数量后才计算失败率，防止初期误判） */
    private int minRequestCount = 10;
    /** 熔断恢复时间(s)，熔断后等待多久切换到半开状态 */
    private int recoveryTimeout = 60;
    /** 慢请求阈值(ms)，超过此时间的请求视为慢请求 */
    private long slowRequestThreshold = 5000;
    /** 连接池耗尽阈值（百分比，活跃连接占比超过此值时触发熔断） */
    private int poolExhaustThreshold = 80;
    /** 半开状态放行请求数 */
    private int halfOpenRequests = 10;
    /** 半开状态成功阈值（成功率超过此值时关闭熔断） */
    private double halfOpenSuccessThreshold = 0.9;
    /** 按业务线隔离（不同业务线使用独立熔断器） */
    private boolean isolateByBusinessLine = true;
    /** 探活规则（半开状态下的探活配置） */
    private ProbeRule probeRule;
    /** 状态持久化（重启后恢复熔断状态） */
    private boolean enableStatePersistence = true;
}

/**
 * 探活规则配置
 */
@Data
public class ProbeRule {
    /** 探活请求数量（半开状态下放行的试探请求数） */
    private int probeCount = 10;
    /** 探活成功阈值（成功率超过此值时关闭熔断） */
    private double successThreshold = 0.9;
    /** 探活间隔(ms)，两次探活之间的间隔 */
    private long probeIntervalMs = 1000;
}
```

---

### 3.2 Session 会话池

#### 3.2.1 架构设计

```
┌─────────────────────────────────────────────────────────────────────┐
│                         NebulaSessionPool                            │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │         ConcurrentLinkedQueue<SessionWrapper>                 │    │
│  │  [Wrapper(S1,IDLE)] [Wrapper(S2,IDLE)] [Wrapper(S3,IDLE)]   │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                              │                                      │
│          ┌───────────────────┼───────────────────┐                  │
│          ▼                   ▼                   ▼                   │
│    ┌───────────┐      ┌───────────┐      ┌───────────┐             │
│    │ borrow() │      │ return()  │      │ health()  │             │
│    │+超时控制 │      │+状态更新  │      │+心跳检测  │             │
│    │+Semaphore│      │+有效性   │      │+失效标记  │             │
│    └───────────┘      │  校验    │      └───────────┘             │
│                       └───────────┘                                 │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  SessionPoolInitializer │ SessionPoolScaler │ LeakDetector  │    │
│  │  IdleConnectionCleaner   │ AddressManager   │ LoadBalancer  │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

#### 3.2.2 SessionWrapper

```java
@Data
public class SessionWrapper {
    /** Nebula Session 实例 */
    private Session session;
    /** Session 状态：IDLE-空闲 / ACTIVE-活跃 / INVALID-失效 */
    private SessionState state;
    /** 最后使用时间（用于空闲回收和泄漏检测） */
    private long lastUsedTime;
    /** Session 创建时间（用于生命周期管理） */
    private long createTime;
    /** 所属 Nebula 图存储地址 */
    private String address;
    /** 所属业务线（用于熔断隔离） */
    private String businessLine;
}

/**
 * Session 状态枚举
 */
public enum SessionState {
    IDLE,      // 空闲，在池中等待借取
    ACTIVE,    // 活跃，已被借取使用
    INVALID    // 失效，待销毁重建
}
```

#### 3.2.3 SessionPoolMonitor（增强版）

```java
public interface SessionPoolMonitor {
    /** 获取当前空闲连接数 */
    int getIdleCount();
    /** 获取当前活跃连接数 */
    int getActiveCount();
    /** 获取等待借取的任务数（队列积压） */
    int getWaitingTasks();
    /** 获取平均借取等待时间(ms) */
    long getAvgBorrowWaitTime();
    /** 获取连接创建失败次数 */
    long getConnectionCreateFailures();
    /** 获取心跳失败次数 */
    long getHeartbeatFailures();
    /** 获取连接复用率（复用量/总借取量） */
    double getConnectionReuseRate();
    /** 获取借取超时次数 */
    long getBorrowTimeouts();
    /** 获取连接创建总耗时(ms) */
    long getConnectionCreateTime();
    /** 获取连接销毁数量 */
    long getConnectionDestroyCount();
    /** 获取预热失败次数 */
    long getWarmupFailures();

    /**
     * 检测泄漏的 Session（活跃超时的 Session）
     * @param timeoutMs 超时阈值
     */
    List<SessionWrapper> detectLeakedSessions(long timeoutMs);

    /**
     * 强制回收泄漏的 Session
     * @param timeoutMs 超时阈值
     */
    void reclaimLeakedSessions(long timeoutMs);
}
```

#### 3.2.4 NebulaSessionPool

```java
public interface NebulaSessionPool {
    /** 借取 Session，超时则抛异常 */
    SessionWrapper borrowSession();
    /** 归还 Session，包含状态校验和健康检查 */
    void returnSession(SessionWrapper wrapper);
    /** 执行查询语句 */
    ResultSet executeQuery(String ngql);
    /** 执行写入语句 */
    boolean executeWrite(String ngql);
    /** 销毁连接池，释放所有资源 */
    void destroy();
}
```

#### 3.2.5 读写分离

```java
public interface WriteSessionPool extends NebulaSessionPool {
    /** 写操作绑定主节点 */
}

public interface ReadSessionPool extends NebulaSessionPool {
    /** 读操作优先从节点，主从延迟超阈值自动切主 */
}
```

#### 3.2.6 AddressManager + LoadBalancer

```java
public interface AddressManager {
    /** 连续失败次数阈值（超过此值标记为不可用） */
    int FAILURE_THRESHOLD = 3;
    /** 不可用地址恢复检测间隔(s) */
    long RECOVERY_INTERVAL = 30;

    /** 记录地址失败次数，连续失败超阈值则标记为不可用 */
    void recordFailure(String address);
    /** 获取可用地址（优先从可用地址中选择） */
    String getAvailableAddress();
    /** 标记地址为可用 */
    void markAvailable(String address);
    /** 标记地址为不可用 */
    void markUnavailable(String address);
    /** 获取所有不可用地址列表 */
    Set<String> getUnavailableAddresses();
}

public interface LoadBalancer {
    /**
     * 选择地址（根据配置的负载均衡策略）
     * @param availableAddresses 可用地址列表
     * @return 选中的地址
     */
    String selectAddress(List<String> availableAddresses);

    /**
     * 更新地址权重（用于加权策略）
     * @param address 地址
     * @param weight 权重值
     */
    void updateWeight(String address, int weight);

    /**
     * 获取当前负载均衡策略
     */
    LoadBalanceStrategy getStrategy();
}
```

---

### 3.3 Schema 管理模块

#### 3.3.1 SchemaUtils

Schema 工具类，提供标识符的安全处理（反引号包裹、大小写校验），防止 NebulaGraph 自动转小写导致的问题：

```java
public class SchemaUtils {
    /**
     * 反引号包裹标识符
     * 解决 NebulaGraph 自动转小写问题
     * @param identifier 标识符名称
     * @return 包裹后的标识符，如 `Product`
     */
    public static String quote(String identifier) {
        return identifier == null ? null : "`" + identifier + "`";
    }

    /**
     * 校验标识符是否符合规范（小写字母开头 + 字母数字下划线）
     * @param identifier 标识符名称
     * @return true=合法，false=非法
     */
    public static boolean validateIdentifier(String identifier) {
        return identifier != null && identifier.matches("^[a-z][a-z0-9_]*$");
    }

    /**
     * 强制转为小写并包裹反引号
     * @param identifier 标识符名称
     * @return 转换后的标识符，如 `product`
     */
    public static String quoteLowerCase(String identifier) {
        return quote(identifier == null ? null : identifier.toLowerCase());
    }
}
```

#### 3.3.2 FieldTypeValidator

字段类型校验器，确保新增字段符合 NebulaGraph 支持的类型规范：

```java
public interface FieldTypeValidator {
    /** NebulaGraph 支持的字段类型白名单 */
    List<String> ALLOWED_TYPES = Arrays.asList(
        "string", "int", "bigint", "double", "float",
        "bool", "timestamp", "datetime", "date"
    );

    /**
     * 校验字段类型是否在白名单内
     * @param type 字段类型
     * @return true=支持，false=不支持
     */
    boolean validate(String type);

    /**
     * 校验字段定义（类型 + 字段名）
     * @param fieldName 字段名
     * @param type 字段类型
     */
    void validateField(String fieldName, String type);

    /**
     * 校验字段兼容性（新增字段时检查是否与现有版本冲突）
     * @param schemaName Schema 名称
     * @param newFields 新增字段列表
     * @return true=兼容，false=冲突
     */
    boolean validateFieldCompatibility(String schemaName, List<FieldDefinition> newFields);
}
```

#### 3.3.3 SchemaChangeLock

Schema 变更分布式锁，防止并发变更导致 Schema 不一致：

```java
public interface SchemaChangeLock {
    /**
     * 尝试获取锁
     * @param schemaName Schema 名称
     * @param timeout 超时时间(ms)
     * @return 锁 token，null 表示获取失败
     */
    String tryLock(String schemaName, long timeout);

    /**
     * 释放锁
     * @param schemaName Schema 名称
     * @param token 锁 token
     */
    void unlock(String schemaName, String token);

    /**
     * 检查是否有变更锁
     * @param schemaName Schema 名称
     * @return true=有锁，false=无锁
     */
    boolean isLocked(String schemaName);
}
```

#### 3.3.4 FixedSchemaManager

固定 Schema 管理器，管理 PLM 基础类型（SsoOrg/Product/Part 等），运行时禁止修改基础字段，仅允许扩展新字段：

```java
public interface FixedSchemaManager {
    /** 初始化基础 Schema（应用启动时调用） */
    void initialize();
    /** 获取所有已注册的基础 Tag */
    Set<String> getRegisteredTags();
    /** 获取所有已注册的基础 EdgeType */
    Set<String> getRegisteredEdgeTypes();
    /** 检查 Tag 是否为基础类型 */
    boolean isFixedTag(String tagName);
    /** 检查 EdgeType 是否为基础类型 */
    boolean isFixedEdgeType(String edgeTypeName);
    /** 为基础 Tag 添加扩展字段（新增，不修改现有字段） */
    void extendTag(String tagName, List<FieldDefinition> newFields);
    /** 为基础 EdgeType 添加扩展字段 */
    void extendEdgeType(String edgeTypeName, List<FieldDefinition> newFields);
}
```

#### 3.3.5 DynamicSchemaManager（增强版）

```java
public interface DynamicSchemaManager {
    void createTag(TagDefinition definition);
    void createEdgeType(EdgeTypeDefinition definition);

    /**
     * 灰度创建 Schema（仅对指定业务线可见）
     */
    void createTagWithGray(TagDefinition definition, List<String> grayBusinessLines);
    void createEdgeTypeWithGray(EdgeTypeDefinition definition, List<String> grayBusinessLines);

    void dropTag(String tagName);
    void dropEdgeType(String edgeTypeName);
    void validateQuota();
    void scheduledQuotaCheck();
    Set<String> getDynamicSchemas();

    /**
     * 检测 Schema 变更后的异常（如查询失败率）
     */
    boolean detectSchemaChangeAbnormal(String schemaName);
}
```

#### 3.3.6 SchemaVersionManager（增强版）

```java
public interface SchemaVersionManager {
    SchemaVersion createVersion(String schemaName, SchemaDefinition newDef);
    void commitVersion(String versionId);
    void rollbackVersion(String versionId);
    List<SchemaVersion> getVersionHistory(String schemaName);
    void grayPublish(String versionId, int percentage, GrayStrategy grayStrategy);
    boolean shouldAutoRollback(String versionId);

    /**
     * 设置版本存储介质
     */
    void setVersionStorage(VersionStorageType storageType);

    /**
     * 清理过期版本（保留最近 N 个版本）
     */
    void cleanExpiredVersions(int retainCount);

    /**
     * 绑定业务查询到指定版本
     */
    void bindBusinessToVersion(String businessLine, String schemaName, String versionId);
}

public enum VersionStorageType {
    MYSQL,      // MySQL 存储
    REDIS,      // Redis 存储
    NEBULA_GRAPH // Nebula 自身存储
}
```

---

### 3.4 模型定义

#### 3.4.1 GraphNode

图节点模型，封装 PLM 业务中的实体（如产品、零部件、文档等）：

```java
@Data
public class GraphNode {
    /** 顶点唯一标识（VID） */
    private String id;
    /** 节点类型（对应 Tag 名称，如 Product/Part/Document） */
    private String type;
    /** 创建人 */
    private String createBy;
    /** 创建时间 */
    private Date createTime;
    /** 更新人 */
    private String updateBy;
    /** 更新时间 */
    private Date updateTime;
    /** 软删除时间（为 null 表示未删除） */
    private Date deletedAt;
    /** 版本号（用于时间轴查询） */
    private Long version;
    /** 扩展属性（业务自定义字段） */
    private Map<String, Object> properties;
}
```

#### 3.4.2 GraphEdge

图边模型，封装 PLM 业务中的关系（如包含关系、版本关系等）：

```java
@Data
public class GraphEdge {
    /** 边唯一标识 */
    private String id;
    /** 边类型（对应 EdgeType 名称，如 ContainsLink/PartVersionLink） */
    private String type;
    /** 关系类型（如 PARENT_CHILD/VERSION） */
    private String linkType;
    /** 创建人 */
    private String createBy;
    /** 创建时间 */
    private Date createTime;
    /** 更新人 */
    private String updateBy;
    /** 更新时间 */
    private Date updateTime;
    /** 起始节点 VID */
    private String fromId;
    /** 起始节点类型 */
    private String fromType;
    /** 目标节点 VID */
    private String toId;
    /** 目标节点类型 */
    private String toType;
    /** 软删除时间（为 null 表示未删除） */
    private Date deletedAt;
    /** 版本号（用于时间轴查询） */
    private Long version;
    /** 扩展属性（JSON 格式） */
    private String propData;
}
```

---

### 3.5 CRUD 操作模块

#### 3.5.1 NodeOperation

节点 CRUD 操作接口，提供顶点级别的增删改查能力：

```java
public interface NodeOperation {
    /** 插入顶点（幂等） */
    boolean insertVertex(String tagName, GraphNode node);
    /** 批量插入顶点（自动分片） */
    BatchResult batchInsertVertices(String tagName, List<GraphNode> nodes);
    /** Upsert 顶点（覆盖更新） */
    boolean upsertVertex(String tagName, GraphNode node);
    /** 插入顶点（IF NOT EXISTS，幂等） */
    boolean insertIfNotExists(String tagName, GraphNode node);
    /** 全量更新顶点属性 */
    boolean updateVertex(String tagName, String vid, Map<String, Object> properties);
    /** 部分更新顶点属性（仅更新指定字段） */
    boolean patchUpdateVertex(String tagName, String vid, Map<String, Object> patchFields);
    /** 更新顶点（带预期值校验，幂等保证） */
    boolean updateVertexWithExpectation(String tagName, String vid,
                                        Map<String, Object> newValues,
                                        Map<String, Object> expectedValues);
    /** 删除顶点（物理删除） */
    boolean deleteVertex(String vid);
    /** 软删除顶点（标记 deletedAt） */
    boolean softDeleteVertex(String vid);
    /** 恢复软删除的顶点 */
    boolean restoreVertex(String vid);
    /** 批量删除顶点 */
    BatchResult batchDeleteVertices(List<String> vids);
    /** 根据 VID 查询顶点 */
    Optional<GraphNode> getVertex(String vid, boolean includeDeleted);
    /** 查询所有顶点 */
    List<GraphNode> listVertices(String tagName, boolean includeDeleted);
}
```

#### 3.5.2 EdgeOperation

边 CRUD 操作接口，提供边级别的增删改查能力：

```java
public interface EdgeOperation {
    /** 插入边（幂等） */
    boolean insertEdge(String edgeTypeName, GraphEdge edge);
    /** 批量插入边（自动分片） */
    BatchResult batchInsertEdges(String edgeTypeName, List<GraphEdge> edges);
    /** Upsert 边（覆盖更新） */
    boolean upsertEdge(String edgeTypeName, GraphEdge edge);
    /** 插入边（IF NOT EXISTS，幂等） */
    boolean insertEdgeIfNotExists(String edgeTypeName, GraphEdge edge);
    /** 全量更新边属性 */
    boolean updateEdge(String edgeTypeName, String fromVid, String toVid, Map<String, Object> properties);
    /** 部分更新边属性 */
    boolean patchUpdateEdge(String edgeTypeName, String fromVid, String toVid, Map<String, Object> patchFields);
    /** 删除边 */
    boolean deleteEdge(String fromVid, String toVid);
    /** 软删除边 */
    boolean softDeleteEdge(String fromVid, String toVid);
    /** 恢复软删除的边 */
    boolean restoreEdge(String fromVid, String toVid);
    /** 批量删除边 */
    BatchResult batchDeleteEdges(List<EdgeQuery> queries);
    /** 查询出边 */
    List<GraphEdge> getOutEdges(String vid, boolean includeDeleted);
    /** 查询入边 */
    List<GraphEdge> getInEdges(String vid, boolean includeDeleted);
}
```

#### 3.5.3 BatchOperation（增强版）

批量操作接口，支持分片、幂等、重试、进度追踪，适用于大批量数据导入场景：

```java
public interface BatchOperation {
    /** 每分片最大数据量 */
    int MAX_BATCH_SIZE = 1000;

    /** 添加顶点到批次 */
    void addVertex(String tagName, GraphNode node);
    /** 添加边到批次 */
    void addEdge(String edgeTypeName, GraphEdge edge);

    /**
     * 设置分片策略
     * @param strategy 分片策略（HASH_BY_VID / FIXED_SIZE / RANGE）
     */
    void setShardingStrategy(ShardingStrategy strategy);

    /** 执行批次（自动分片 + 幂等键 + 指数退避重试） */
    BatchResult execute();
    /** 执行批次（带自定义幂等键） */
    BatchResult execute(String idempotentKey);

    /**
     * 获取批量操作进度
     * @param batchId 批次ID
     * @return 进度信息
     */
    BatchProgress getProgress(String batchId);

    /**
     * 重试失败的分片（隔离失败分片，仅重试失败部分）
     * @param batchId 批次ID
     * @return 重试结果
     */
    BatchResult retryFailedShards(String batchId);

    /** 清空批次 */
    void clear();
}

/**
 * 分片策略枚举
 */
public enum ShardingStrategy {
    HASH_BY_VID, // 按 VID 哈希分片（避免热点）
    FIXED_SIZE,   // 按固定大小分片
    RANGE         // 按 VID 范围分片
}

/**
 * 批量操作进度
 */
@Data
public class BatchProgress {
    /** 批次唯一标识 */
    private String batchId;
    /** 总分片数 */
    private int totalShards;
    /** 已完成分片数 */
    private int completedShards;
    /** 失败分片数 */
    private int failedShards;
    /** 开始时间 */
    private Date startTime;
    /** 最后更新时间 */
    private Date lastUpdateTime;
    /** 失败分片ID列表 */
    private List<String> failedShardIds;
}
```

#### 3.5.4 BatchConsistencyChecker

批量一致性校验器，确保批量操作的数据完整性：

```java
public interface BatchConsistencyChecker {
    /**
     * 校验批量操作的数据完整性
     * @param batchId 批次ID
     * @param expectedCount 预期数量
     * @return true=一致，false=不一致
     */
    boolean checkConsistency(String batchId, int expectedCount);

    /**
     * 生成不一致报告
     * @param batchId 批次ID
     * @return 不一致详情
     */
    InconsistencyReport generateReport(String batchId);
}

/**
 * 不一致报告
 */
@Data
public class InconsistencyReport {
    /** 批次ID */
    private String batchId;
    /** 预期数量 */
    private int expectedCount;
    /** 实际数量 */
    private int actualCount;
    /** 缺失的 VID 列表 */
    private List<String> missingVids;
    /** 多余的 VID 列表 */
    private List<String> extraVids;
    /** 检查时间 */
    private Date checkTime;
}
```

#### 3.5.5 BatchCompensator

批量补偿器，当批量失败率超过阈值时触发补偿机制：

```java
public interface BatchCompensator {
    /**
     * 触发补偿：批量失败率超过阈值时执行
     * @param failedBatch 失败的批次结果
     */
    void compensate(BatchResult failedBatch);

    /**
     * 检查补偿状态
     * @param batchId 批次ID
     * @return 补偿状态
     */
    CompensateStatus checkStatus(String batchId);

    /**
     * 人工触发补偿
     * @param batchId 批次ID
     */
    void manualCompensate(String batchId);

    /**
     * 获取补偿失败告警阈值
     * @return 阈值
     */
    double getCompensateFailureAlertThreshold();
}

/**
 * 补偿状态枚举
 */
public enum CompensateStatus {
    PENDING,           // 待补偿
    EXECUTING,         // 执行中
    SUCCESS,           // 成功
    FAILED,            // 失败
    MANUAL_INTERVENTION // 需要人工介入
}
```

#### 3.5.6 IdempotentStore（增强版）

幂等键存储，基于 Redis 实现，确保分布式场景下的幂等性：

```java
public interface IdempotentStore {
    /**
     * 记录幂等键
     * @param key 幂等键
     * @param ttl TTL（秒）
     */
    void record(String key, long ttl);

    /**
     * 检查幂等键是否存在
     * @param key 幂等键
     * @return true=存在（已执行过），false=不存在
     */
    boolean exists(String key);

    /**
     * 原子性检查并记录（SETNX）
     * @param key 幂等键
     * @param ttl TTL（秒）
     * @return true=新增成功（可执行），false=已存在（跳过执行）
     */
    boolean checkAndRecord(String key, long ttl);

    /**
     * 续期幂等键（长操作保障）
     * @param key 幂等键
     * @param additionalTtl 额外 TTL（秒）
     */
    void renew(String key, long additionalTtl);

    /**
     * 删除幂等键（操作完成后）
     * @param key 幂等键
     */
    void remove(String key);
}
```

#### 3.5.7 SoftDeleteCleaner（增强版）

软删除清理器，负责归档和清理过期数据：

```java
public interface SoftDeleteCleaner {
    /**
     * 归档软删除数据（超过保留期）
     * @param retentionDays 保留天数
     */
    void archive(int retentionDays);

    /**
     * 物理删除归档数据
     * @param retentionDays 保留天数
     */
    void purge(int retentionDays);

    /**
     * 统计待清理数据量
     * @param retentionDays 保留天数
     * @return 待清理数量
     */
    long countPendingCleanup(int retentionDays);

    /**
     * 设置归档存储位置
     * @param storage 存储类型（OFFLINE_NEBULA / MINIO / HDFS）
     */
    void setArchiveStorage(ArchiveStorage storage);

    /**
     * 校验恢复可行性（已归档则不允许直接恢复）
     * @param vid 顶点ID
     * @return true=可恢复，false=已归档需特殊处理
     */
    boolean validateRestoreFeasibility(String vid);

    /**
     * 创建软删除索引（优化查询性能）
     * @param tagName Tag 名称
     */
    void createSoftDeleteIndex(String tagName);
}

/**
 * 归档存储位置枚举
 */
public enum ArchiveStorage {
    OFFLINE_NEBULA, // 离线 Nebula 集群
    MINIO,          // MinIO 对象存储
    HDFS            // HDFS 存储
}
```

---

### 3.6 查询模块

#### 3.6.1 QueryBuilder

查询构建器，提供链式 API 构建 nGQL 查询语句，强制参数化防止 SQL 注入：

```java
public class QueryBuilder {
    // 内置软删除过滤：默认 deletedAt IS NULL
    // 使用 includeDeleted(true) 可显式关闭过滤

    /** MATCH 模式查询（禁止直接拼接，所有条件参数化） */
    public QueryBuilder match(String pattern);
    /** MATCH 模式（参数化） */
    public QueryBuilder match(String vertexType, Map<String, Object> whereConditions);
    /** WHERE 条件（参数化） */
    public QueryBuilder where(String condition, Object... params);
    /** WHERE 条件（Map 形式，自动参数化） */
    public QueryBuilder where(Map<String, Object> conditions);
    /** WHERE ... IN 条件（参数化列表） */
    public QueryBuilder whereIn(String field, List<Object> values);
    /** WHERE ... OR 条件（参数化） */
    public QueryBuilder whereOr(List<Condition> conditions);
    /** YIELD 返回字段 */
    public QueryBuilder yield(String... fields);
    /** ORDER BY 排序 */
    public QueryBuilder orderBy(String field, SortDirection direction);
    /** LIMIT 分页 */
    public QueryBuilder limit(int offset, int count);
    /** 执行查询 */
    public QueryResult execute();
    /** 执行查询（不分页，获取全部结果） */
    public QueryResult executeAll(int maxResultCount);
}
```

#### 3.6.2 PathQuery（增强版）

路径查询封装，支持循环检测、结果缓存、分页：

```java
public interface PathQuery {
    /** 查询直接邻居 */
    List<QueryResult> neighbors(String vid, String vertexType, List<String> edgeTypes);

    /**
     * 多跳路径查询（含循环检测 + 结果分页）
     * @param startVid 起始 VID
     * @param startType 起始类型
     * @param edgeType 边类型
     * @param minHop 最小跳数
     * @param maxHop 最大跳数
     * @param maxVisited 同一 VID 最大访问次数（防止死循环）
     * @param maxResultCount 最大结果数（防 OOM）
     */
    List<QueryResult> matchPaths(String startVid, String startType, String edgeType,
                                  int minHop, int maxHop, int maxVisited,
                                  int maxResultCount);

    /** 查询最短路径 */
    List<QueryResult> shortestPath(String fromVid, String toVid, String edgeType);

    /**
     * 查询所有路径（含循环检测 + 结果分页）
     */
    List<QueryResult> allPaths(String fromVid, String toVid, String edgeType,
                                 int maxHop, int maxVisited, int maxResultCount);

    /**
     * 开启查询结果缓存
     * @param ttlMs 缓存过期时间(ms)
     */
    void enableResultCache(long ttlMs);

    /**
     * 设置循环检测配置
     * @param config 循环检测配置
     */
    void setCycleDetectionConfig(CycleDetectionConfig config);

    /**
     * 分页查询路径
     */
    List<QueryResult> queryPathsWithPagination(String fromVid, String toVid, String edgeType,
                                                int pageNum, int pageSize);
}

/**
 * 循环检测配置
 */
@Data
public class CycleDetectionConfig {
    /** 最大访问节点数（防止内存爆炸） */
    private int maxVisitedCount = 1000;
    /** 最大跳数 */
    private int maxHopDepth = 10;
    /** 是否跟踪已访问 VID */
    private boolean enableVidTracking = true;
}
```

#### 3.6.3 VersionedQuery（增强版）

版本化查询，支持时间轴回溯：

```java
public interface VersionedQuery {
    /**
     * 查询指定时间点的产品 BOM 状态
     * @param productId 产品ID
     * @param timestamp 时间戳
     */
    BOMTree getProductBOMAt(String productId, long timestamp);

    /**
     * 查询指定版本的节点
     */
    Optional<GraphNode> getVertexAt(String vid, long version);

    /**
     * 查询指定版本的边
     */
    Optional<GraphEdge> getEdgeAt(String fromVid, String toVid, long version);

    /**
     * 设置版本号生成规则
     * @param generator 版本生成器
     */
    void setVersionGenerator(VersionGenerator generator);

    /**
     * 压缩历史版本数据（减少存储）
     * @param schemaName Schema 名称
     */
    void compressHistoricalVersions(String schemaName);

    /**
     * 预加载热点版本（加速高频查询）
     * @param schemaName Schema 名称
     * @param hotVersions 热点版本列表
     */
    void preloadHotVersions(String schemaName, List<Long> hotVersions);
}

/**
 * 版本号生成器枚举
 */
public enum VersionGenerator {
    TIMESTAMP,        // 时间戳版本
    AUTO_INCREMENT,   // 自增版本
    BUSINESS_SPECIFIC // 业务指定版本
}
```

---

### 3.7 PLM 业务专属 API

#### 3.7.1 BOMDiffService

BOM 增量更新服务，通过对比新旧 BOM 结构，仅生成差异操作：

```java
public interface BOMDiffService {
    /**
     * 计算 BOM 差异
     * @param oldBOM 原 BOM 结构
     * @param newBOM 新 BOM 结构
     * @return 差异操作结果
     */
    BOMDiffResult diff(BOMTree oldBOM, BOMTree newBOM);

    /**
     * 执行 BOM 增量更新
     * @param productId 产品ID
     * @param diff 差异结果
     * @return 批量操作结果
     */
    BatchResult applyDiff(String productId, BOMDiffResult diff);
}

/**
 * BOM 差异结果
 */
@Data
public class BOMDiffResult {
    /** 新增节点 */
    private List<GraphNode> addedNodes;
    /** 删除节点 */
    private List<GraphNode> removedNodes;
    /** 新增边 */
    private List<GraphEdge> addedEdges;
    /** 删除边 */
    private List<GraphEdge> removedEdges;
    /** 更新节点 */
    private List<GraphNode> updatedNodes;
}
```

#### 3.7.2 LargePropertyManager

大属性管理器，用于存储 PLM 文档的大文本/二进制内容到对象存储：

```java
public interface LargePropertyManager {
    /**
     * 上传大属性
     * @param data 二进制/大文本数据
     * @param contentType 内容类型
     * @return MinIO URL
     */
    String upload(byte[] data, String contentType);

    /**
     * 下载大属性
     * @param url MinIO URL
     * @return 二进制数据
     */
    byte[] download(String url);

    /**
     * 删除大属性
     * @param url MinIO URL
     */
    void delete(String url);
}
```

#### 3.7.3 PLMNodeService

PLM 节点业务 API：

```java
public interface PLMNodeService {
    /** 创建产品节点 */
    void createProduct(Product product);
    /** 创建零部件节点 */
    void createPart(Part part);
    /** 创建文档节点 */
    void createDocument(Document document);
    /** 创建文件夹节点 */
    void createFolder(Folder folder);
    /** 获取产品（含缓存） */
    Optional<Product> getProduct(String productId);
    /** 获取零部件（含缓存） */
    Optional<Part> getPart(String partId);
    /** 获取文档（含缓存） */
    Optional<Document> getDocument(String docId);
}
```

#### 3.7.4 PLMEdgeService

PLM 边业务 API：

```java
public interface PLMEdgeService {
    /**
     * 创建包含关系（父子）
     */
    void createContainsLink(String parentId, String parentType,
                            String childId, String childType,
                            Map<String, Object> props);

    /**
     * 创建版本关联
     */
    void createVersionLink(String masterId, String versionId, String linkType);

    /**
     * 删除包含关系
     */
    void deleteContainsLink(String parentId, String parentType,
                            String childId, String childType);

    /** 获取产品的直接子件 */
    List<Part> getProductChildren(String productId);

    /**
     * 获取产品的完整 BOM（含循环检测）
     * @param productId 产品ID
     * @param maxDepth 最大深度
     * @param maxVisited 同一节点最大访问次数
     */
    List<Part> getProductBOM(String productId, int maxDepth, int maxVisited);
}
```

#### 3.7.5 PLMCache

PLM 业务缓存，支持布隆过滤器防穿透：

```java
public interface PLMCache {
    /** 获取产品（先查本地缓存，再查分布式缓存，最后查 Nebula） */
    Optional<Product> getProduct(String productId);
    /** 获取零部件 */
    Optional<Part> getPart(String partId);
    /** 获取文档 */
    Optional<Document> getDocument(String docId);
    /** 失效缓存 */
    void invalidate(String key);
    /** 按类型批量失效 */
    void invalidateByType(String type);
}
```

---

### 3.8 生产稳定性

#### 3.8.1 CircuitBreaker（增强版）

多维度熔断器，支持按业务线隔离、状态持久化：

```java
public class CircuitBreaker {
    /** 熔断状态：CLOSED-关闭 / OPEN-打开 / HALF_OPEN-半开 */
    enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    /** 所属业务线（用于熔断隔离） */
    private String businessLine;

    /** 检查是否允许请求 */
    boolean allowRequest();
    /** 记录成功 */
    void recordSuccess();
    /** 记录失败 */
    void recordFailure();
    /** 记录慢请求 */
    void recordSlowRequest();
    /** 检查是否应触发熔断 */
    boolean shouldTrip(CircuitContext context);
    /** 执行降级策略 */
    Object degrade(String operation);

    /**
     * 设置探活规则
     * @param rule 探活规则配置
     */
    void setProbeRule(ProbeRule rule);

    /**
     * 持久化状态（重启后恢复）
     */
    void persistState();

    /**
     * 恢复状态
     */
    void restoreState();
}
```

#### 3.8.2 CircuitBreakerManager

熔断器管理，支持人工干预：

```java
public interface CircuitBreakerManager {
    /** 获取熔断器状态 */
    CircuitBreakerStatus getStatus(String name);
    /** 手动强制打开熔断器 */
    void forceOpen(String name);
    /** 手动强制关闭熔断器 */
    void forceClose(String name);
    /** 重置熔断器 */
    void reset(String name);
    /** 更新熔断阈值 */
    void updateThreshold(String name, CircuitBreakerConfig config);

    /**
     * 按业务线获取熔断状态
     * @param businessLine 业务线
     * @return 该业务线下所有熔断器状态
     */
    Map<String, CircuitBreakerStatus> getStatusByBusinessLine(String businessLine);
}

/**
 * 熔断器状态
 */
@Data
public class CircuitBreakerStatus {
    /** 熔断器名称 */
    private String name;
    /** 当前状态 */
    private CircuitState state;
    /** 失败率 */
    private double failureRate;
    /** 最后失败时间 */
    private long lastFailureTime;
    /** 总请求数 */
    private int totalRequests;
    /** 失败请求数 */
    private int failedRequests;
}
```

#### 3.8.3 SlowQueryRateLimiter

慢查询限流器，防止高频慢查询影响系统稳定性：

```java
public interface SlowQueryRateLimiter {
    /**
     * 检查是否应限流
     * @param operator 操作者标识（如 IP、业务ID）
     * @return true=允许，false=拒绝
     */
    boolean allow(String operator);

    /**
     * 记录慢查询
     * @param operator 操作者标识
     */
    void recordSlowQuery(String operator);

    /**
     * 获取限流阈值配置
     * @param operator 操作者标识
     */
    SlowQueryThreshold getThreshold(String operator);
}
```

#### 3.8.4 监控模块（增强版）

```java
/**
 * 慢查询日志（包含完整上下文信息）
 */
public class SlowQueryLog {
    /**
     * 记录慢查询
     * @param ngql 查询语句
     * @param costMs 耗时(ms)
     * @param requestId 请求ID（链路追踪）
     * @param operator 操作者
     * @param businessScene 业务场景
     * @param params 查询参数
     */
    void logSlowQuery(String ngql, long costMs, String requestId, String operator,
                      String businessScene, Map<String, Object> params);
}

/**
 * 操作耗时统计
 */
public class OperationMetrics {
    /** 记录操作 */
    void recordOperation(String operation, long costMs, boolean success);
    /** 获取所有指标 */
    Map<String, MetricSummary> getMetrics();
    /** 获取 P99 延迟 */
    long getP99Latency(String operation);
}

/**
 * Schema 变更指标
 */
public class SchemaMetrics {
    /** 记录 Schema 变更 */
    void recordSchemaChange(String schemaName, String operation);
    /** 获取 Schema 指标 */
    Map<String, SchemaChangeMetrics> getSchemaMetrics();
}

/**
 * 幂等键指标
 */
public class IdempotentMetrics {
    /** 缓存命中次数 */
    long getHitCount();
    /** 缓存未命中次数 */
    long getMissCount();
    /** 续期次数 */
    long getRenewCount();
    /** 重复写入次数 */
    long getDuplicateWriteCount();
}

/**
 * Nebula 统一监控器
 */
public class NebulaMonitor {
    /** 设置指标采集间隔(ms) */
    void setMetricCollectInterval(long intervalMs);
    /** 设置指标存储介质 */
    void setMetricStorage(MetricStorage storage);
    /** 动态更新告警规则 */
    void updateAlertRule(String ruleId, AlertRule rule);
    /** 集成全链路追踪 */
    void integrateTracing(String traceId, Map<String, String> spanTags);

    /** 记录错误 */
    void onError(String operation, Exception e);
    /** 记录连接错误 */
    void onConnectionError(Exception e);
    /** 记录批量失败 */
    void onBatchFailure(String batchId, double failureRate);
}

/**
 * 指标存储介质
 */
public enum MetricStorage {
    PROMETHEUS,   // Prometheus 存储
    INFLUXDB,     // InfluxDB 存储
    ELASTICSEARCH // Elasticsearch 存储
}

/**
 * 告警规则
 */
@Data
public class AlertRule {
    /** 规则ID */
    private String ruleId;
    /** 指标名称 */
    private String metricName;
    /** 阈值 */
    private double threshold;
    /** 告警级别 */
    private AlertLevel level;
    /** 时间窗口(ms) */
    private long windowMs;
}

/**
 * 告警级别
 */
public enum AlertLevel {
    INFO,     // 信息
    WARNING,  // 警告
    ERROR,    // 错误
    CRITICAL  // 严重
}
```

---

### 3.9 灾备与恢复

#### 3.9.1 BackupManager

```java
public interface BackupManager {
    /**
     * 执行全量备份
     */
    BackupResult backup();

    /**
     * 执行增量备份
     */
    BackupResult incrementalBackup();

    /**
     * 执行加密备份
     * @param config 加密配置（算法、密钥ID、是否加密元数据）
     */
    BackupResult backupWithEncryption(EncryptionConfig config);

    /**
     * 校验备份完整性
     * @param backupId 备份ID
     * @return true=完整，false=损坏
     */
    boolean validateBackupIntegrity(String backupId);

    /**
     * 列出可用备份
     */
    List<BackupMeta> listBackups();

    /**
     * 删除过期备份
     */
    void deleteExpiredBackups(int retentionDays);

    /**
     * 配置跨集群备份网络参数
     * @param config 网络配置（压缩、带宽限制）
     */
    void setBackupNetworkConfig(NetworkConfig config);
}

/**
 * 备份元数据
 */
@Data
public class BackupMeta {
    /** 备份唯一标识 */
    private String id;
    /** 备份类型：FULL-全量 / INCREMENTAL-增量 */
    private BackupType type;
    /** 备份创建时间 */
    private Date createTime;
    /** 备份大小（字节） */
    private long size;
    /** 备份存储路径 */
    private String storagePath;
    /** 备份校验状态 */
    private IntegrityStatus integrityStatus;
}

/**
 * 备份完整性状态
 */
public enum IntegrityStatus {
    UNCHECKED,  // 未校验
    VALID,      // 有效
    CORRUPTED   // 损坏
}

/**
 * 加密配置
 */
@Data
public class EncryptionConfig {
    /** 加密算法（如 AES-256） */
    private String algorithm;
    /** 密钥ID（对接 KMS 系统） */
    private String keyId;
    /** 是否加密元数据 */
    private boolean encryptMetadata;
}

/**
 * 网络配置（跨集群备份优化）
 */
@Data
public class NetworkConfig {
    /** 是否启用压缩传输 */
    private boolean enableCompression = true;
    /** 压缩级别（1-9，6为平衡） */
    private int compressionLevel = 6;
    /** 最大带宽限制（MB/s，避免压垮集群） */
    private int maxBandwidth = 100;
}
```

#### 3.9.2 DisasterRecoveryManager

```java
public interface DisasterRecoveryManager {
    /**
     * 从备份恢复数据
     * @param backupId 备份ID
     */
    void restore(String backupId);

    /**
     * 切换到备集群
     * 自动检测主集群健康状态，故障时切换
     */
    void switchToStandby();

    /**
     * 检查主集群健康状态
     * @return true=健康，false=异常
     */
    boolean isPrimaryHealthy();

    /**
     * 获取恢复指标
     * @return RTO/RPO 等恢复指标
     */
    RecoveryMetrics getRecoveryMetrics();
}

/**
 * 恢复指标
 */
@Data
public class RecoveryMetrics {
    /** 恢复时间目标（分钟） */
    private long rtoMinutes;
    /** 恢复点目标（分钟） */
    private long rpoMinutes;
    /** 最后一次恢复时间 */
    private Date lastRestoreTime;
    /** 最后一次切换时间 */
    private Date lastSwitchTime;
}
```

---

### 3.10 配置中心

#### 3.10.1 ConfigCenterRefresher

```java
public interface ConfigCenterRefresher {
    /**
     * 监听配置变更
     * @param dataId 配置项ID
     * @param listener 变更监听器
     */
    void listen(String dataId, ConfigChangeListener listener);

    /**
     * 刷新连接池配置
     */
    void refreshPoolConfig(NebulaPoolConfig config);

    /**
     * 刷新熔断器配置
     */
    void refreshCircuitBreaker(String name, CircuitBreakerConfig config);

    /**
     * 刷新重试配置
     */
    void refreshRetryConfig(RetryConfig config);

    /**
     * 配置灰度生效（部分实例先生效）
     * @param dataId 配置项ID
     * @param grayInstanceIds 灰度实例ID列表
     */
    void refreshConfigWithGray(String dataId, List<String> grayInstanceIds);

    /**
     * 配置变更回滚
     * @param dataId 配置项ID
     * @param version 回滚到指定版本
     */
    void rollbackConfig(String dataId, String version);

    /**
     * 启用本地配置缓存（配置中心不可用时使用本地缓存）
     * @param cacheTtlMs 缓存有效期（毫秒）
     */
    void enableLocalCache(long cacheTtlMs);
}

/**
 * 配置变更监听器
 */
public interface ConfigChangeListener {
    /**
     * 配置变更回调
     * @param dataId 配置项ID
     * @param content 变更后的配置内容
     */
    void onChange(String dataId, String content);
}

/**
 * 配置元数据
 */
@Data
public class ConfigMeta {
    /** 配置项ID */
    private String dataId;
    /** 配置版本号 */
    private String version;
    /** 配置内容 */
    private String content;
    /** 灰度实例列表 */
    private List<String> grayInstances;
    /** 生效时间 */
    private Date effectiveTime;
    /** 创建时间 */
    private Date createTime;
}
```

---

## 4. 实现步骤

### Phase 1: 基础架构（3天）

1. 创建目录结构
2. 实现 `SessionWrapper` + `NebulaSessionPool`
3. 实现 `SessionPoolInitializer`（预热/懒加载/兜底）
4. 实现 `SessionPoolScaler`（动态扩缩容）
5. 实现 `SessionPoolMonitor`（泄漏检测）
6. 实现 `AddressManager` + `LoadBalancer`
7. 实现 `MultiAddressSessionPool`
8. 实现 `SchemaUtils`
9. 重构配置类 + 新增 `LoadBalanceConfig`
10. **单元测试**：会话池并发测试 + 泄漏检测测试

### Phase 2: Schema 管理（2天）

1. 实现 `FieldTypeValidator`
2. 实现 `SchemaChangeLock`
3. 实现 `FixedSchemaManager`
4. 实现 `DynamicSchemaManager`（含灰度/校验）
5. 实现 `SchemaVersionManager`（含版本存储介质）
6. 实现 `IndexManager`
7. **单元测试**：Schema 操作测试

### Phase 3: 核心 CRUD（3天）

1. 实现 `NodeOperation`
2. 实现 `EdgeOperation`
3. 实现 `IdempotentStore`
4. 实现 `BatchOperation`（含分片策略/进度追踪）
5. 实现 `BatchConsistencyChecker`
6. 实现 `BatchCompensator`
7. 实现 `VidMapper` + `VidVersionManager`
8. 实现 `SoftDeleteCleaner`
9. **单元测试**：CRUD 测试
10. **集成测试**：批量操作失败重试 + 补偿测试

### Phase 4: 查询封装（2天）

1. 实现 `QueryBuilder`（参数化 + 软删除过滤）
2. 实现 `VersionedQuery`（含版本生成器/压缩/预热）
3. 实现 `PathQuery`（含循环检测/缓存/分页）
4. 实现 `WriteSessionPool` + `ReadSessionPool`
5. **单元测试**：查询测试
6. **性能测试**：路径查询性能测试

### Phase 5: PLM 业务 API（3天）

1. 实现 `BOMDiffService`
2. 实现 `LargePropertyManager`
3. 实现 `PLMNodeService`
4. 实现 `PLMEdgeService`
5. 实现 `PLMPathQuery`
6. 实现 `PLMCache` + `BloomFilter`
7. **单元测试**：PLM 业务测试

### Phase 6: 监控与优化（2天）

1. 实现 `CircuitBreaker`（多维度 + 业务线隔离 + 状态持久化）
2. 实现 `CircuitBreakerManager`（人工干预）
3. 实现 `SlowQueryRateLimiter`
4. 实现 `SessionPoolMonitor`
5. 实现 `SchemaMetrics` + `IdempotentMetrics`
6. 实现 `AlertManager` + `SlowQueryLog` + `OperationMetrics`
7. 集成全链路追踪（SkyWalking/Jaeger）
8. **集成测试**：全链路压测

### Phase 7: 灾备与配置（2天）

1. 实现 `BackupManager`
2. 实现 `DisasterRecoveryManager`
3. 实现 `ConfigCenterRefresher`
4. 集成 Nacos/Apollo 配置中心
5. **灾备演练**

### Phase 8: 灰度发布（2天）

1. 部署新客户端，流量切 10% 验证
2. 逐步扩量至 50% → 100%
3. 保留旧 HTTP 实现 1 周作为回滚方案
4. **监控验证**

---

## 5. 风险与约束

### 5.1 约束说明

- **事务**：Nebula 3.8.0 不支持原生事务，采用幂等 + 重试 + 补偿模式保证最终一致性
- **Schema 管控**：基础字段不可删除，可扩展；动态 Schema 需配额校验；版本存储介质可配置
- **连接限制**：会话池有最大并发限制，高并发场景需评估容量
- **循环引用**：路径查询需设置 `maxVisited` 防止死循环
- **幂等键**：基于 Redis 存储，TTL 设为 1 小时，长操作续期
- **软删除**：默认保留 6 个月，定时归档清理，归档位置可配置

### 5.2 已知风险

| 风险 | 应对措施 |
|------|---------|
| Session 连接泄漏 | 泄漏检测 + 超时回收 + 状态管理 + 监控 |
| 批量部分失败 | 分片提交 + 幂等键 + 续期 + 指数退避 + 一致性校验 + 补偿机制 |
| Schema 膨胀 | 配额管控 + 定时校验 + 告警 |
| 慢查询影响性能 | 熔断降级 + 超时控制 + 读写分离 + 慢查询限流 |
| 主节点故障 | 多地址配置 + 自动切换 + AddressManager + 灾备切换 |
| BOM 循环引用 | maxVisited 循环检测 + 结果分页 |
| 缓存穿透 | 布隆过滤器 + 空值缓存 |
| 故障扩散 | 多维度熔断器 + 业务线隔离 + 降级策略 |
| 配置变更需重启 | 配置中心动态刷新 |
| 多业务线熔断影响 | 按业务线隔离熔断器 |
| 熔断器状态丢失 | 状态持久化（Redis/DB） |

---

## 6. 附录

### 6.1 NebulaGraph 3.8.0 限制

```
1. 不支持事务
2. 不支持回滚
3. 不支持 Savepoint
4. INSERT 操作幂等（IF NOT EXISTS）
5. UPDATE/DELETE 操作幂等
```

### 6.2 幂等操作示例

```java
// 插入（幂等）
INSERT VERTEX IF NOT EXISTS `Product`(id) VALUES "Product:P001":("P001");

// 更新（幂等，带预期值校验）
UPDATE VERTEX IF EXISTS "Product:P001"
SET create_by = "admin"
WHERE create_by == "old_admin";

// 删除（幂等）
DELETE VERTEX IF EXISTS "Product:P001";
```

### 6.3 VID 生成规则

| 模式 | 格式 | 示例 | 适用场景 |
|------|------|------|---------|
| 字符串模式 | `{type}:{id}` | Product:P001 | 调试方便、人类可读 |
| 数值模式 | 雪花算法 | 6897654321090993 | 高性能、大数据量 |

### 6.4 重试策略

```
初始间隔：1s
指数退避：1s → 2s → 4s
最大间隔：4s
最大重试：3次
失败告警阈值：50%
幂等键续期：30s
补偿失败告警阈值：30%
```

### 6.5 熔断阈值

```
失败率阈值：50%
最小请求数：10
恢复时间：60s
慢请求阈值：5000ms
连接池耗尽阈值：80%
半开放行：10请求
半开成功阈值：90%
按业务线隔离：是
状态持久化：是
```

### 6.6 分片策略

```
策略：HASH_BY_VID（按 VID 哈希分片）
分片大小：1000条/片
隔离失败分片：仅重试失败分片
```

### 6.7 日志标准

```
必需字段：traceId, operation, costMs, success, businessScene
可选字段：operator, requestId, params, errorMessage
全链路追踪：SkyWalking/Jaeger
```

### 6.8 版本存储

```
存储介质：NEBULA_GRAPH（默认）
历史版本保留数：10个
清理策略：定时清理（每月）
```

### 6.9 软删除归档

```
保留期：6个月
归档位置：OFFLINE_NEBULA（默认）
清理策略：先归档到离线表，再物理删除
```

---

**文档版本**：1.6
**创建日期**：2026-04-18
**更新日期**：2026-04-18
**作者**：mfish
