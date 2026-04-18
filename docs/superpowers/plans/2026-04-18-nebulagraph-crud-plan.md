# NebulaGraph 3.8.0 CURD 基建设计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为 mfish 平台的 mf-common-graph 模块构建完整的 NebulaGraph 3.8.0 CURD 基建，基于 vesoft client 原生 Session 实现生产级的图数据库操作能力。

**架构：** 采用分层架构设计，从配置层、会话池层、Schema 管理层、CRUD 操作层、查询层到 PLM 业务层，逐层构建并通过接口解耦。核心一致性通过幂等+重试+补偿机制实现，Session 池化确保高并发性能。

**技术栈：** vesoft client 3.8.0、Redis（幂等键/缓存）、Nacos/Apollo（配置中心）、SkyWalking/Jaeger（链路追踪）、MinIO（对象存储）

---

## 文件结构

### 现有文件（保留）

- `mf-common-graph/src/main/java/cn/com/mfish/graph/NebulaConfig.java` - 配置属性类（保留）
- `mf-common-graph/src/main/java/cn/com/mfish/graph/model/node/GraphNode.java` - 节点模型（重构）
- `mf-common-graph/src/main/java/cn/com/mfish/graph/model/edge/GraphEdge.java` - 边模型（重构）

### Phase 1: 基础架构（config + pool）

**创建文件：**

| 文件 | 职责 |
|------|------|
| `config/NebulaPoolConfig.java` | 连接池配置 |
| `config/NebulaGraphProperties.java` | Graph 连接配置 |
| `config/NebulaQuotaConfig.java` | 动态 Schema 配额配置 |
| `config/RetryConfig.java` | 重试策略配置 |
| `config/CircuitBreakerConfig.java` | 熔断器配置 |
| `config/LoadBalanceConfig.java` | 负载均衡配置 |
| `pool/SessionWrapper.java` | Session 包装器（状态管理） |
| `pool/NebulaSessionPool.java` | 会话池接口 |
| `pool/WriteSessionPool.java` | 写入池（主节点） |
| `pool/ReadSessionPool.java` | 读取池（从节点优先） |
| `pool/SessionPoolInitializer.java` | 连接池初始化器 |
| `pool/SessionPoolScaler.java` | 动态扩缩容 |
| `pool/SessionPoolMonitor.java` | 连接池监控 |
| `pool/AddressManager.java` | 地址管理器 |
| `pool/LoadBalancer.java` | 负载均衡器 |
| `client/NebulaGraphClient.java` | 主客户端门面 |

### Phase 2: Schema 管理

**创建文件：**

| 文件 | 职责 |
|------|------|
| `schema/SchemaUtils.java` | Schema 工具类 |
| `schema/FieldTypeValidator.java` | 字段类型校验器 |
| `schema/SchemaChangeLock.java` | 分布式锁 |
| `schema/FixedSchemaManager.java` | 固定 Schema 管理器 |
| `schema/DynamicSchemaManager.java` | 动态 Schema 管理器 |
| `schema/SchemaVersionManager.java` | 版本管理器 |
| `schema/model/TagDefinition.java` | Tag 定义模型 |
| `schema/model/EdgeTypeDefinition.java` | EdgeType 定义模型 |
| `index/IndexManager.java` | 索引管理器 |

### Phase 3: 核心 CRUD

**创建文件：**

| 文件 | 职责 |
|------|------|
| `crud/NodeOperation.java` | 节点 CRUD 接口 |
| `crud/EdgeOperation.java` | 边 CRUD 接口 |
| `crud/BatchOperation.java` | 批量操作接口 |
| `crud/BatchConsistencyChecker.java` | 批量一致性校验器 |
| `crud/BatchCompensator.java` | 批量补偿器 |
| `crud/IdempotentKeyGenerator.java` | 幂等键生成器 |
| `crud/IdempotentStore.java` | 幂等键存储（Redis） |
| `crud/SoftDeleteCleaner.java` | 软删除清理器 |
| `model/vid/VidMapper.java` | VID 映射管理器 |
| `model/vid/VidVersionManager.java` | VID 版本管理器 |
| `model/result/QueryResult.java` | 查询结果 |
| `model/result/BatchResult.java` | 批量结果 |

### Phase 4: 查询封装

**创建文件：**

| 文件 | 职责 |
|------|------|
| `query/QueryBuilder.java` | 查询构建器 |
| `query/PathQuery.java` | 路径查询接口 |
| `query/VersionedQuery.java` | 版本化查询接口 |

### Phase 5: PLM 业务 API

**创建文件：**

| 文件 | 职责 |
|------|------|
| `plm/PLMNodeService.java` | PLM 节点业务 API |
| `plm/PLMEdgeService.java` | PLM 边业务 API |
| `plm/PLMPathQuery.java` | PLM 路径查询 |
| `plm/BOMDiffService.java` | BOM 增量更新服务 |
| `plm/LargePropertyManager.java` | 大属性管理器 |
| `plm/cache/PLMCache.java` | PLM 缓存 |
| `plm/cache/BloomFilter.java` | 布隆过滤器 |

### Phase 6: 监控与优化

**创建文件：**

| 文件 | 职责 |
|------|------|
| `monitor/SlowQueryLog.java` | 慢查询日志 |
| `monitor/SlowQueryRateLimiter.java` | 慢查询限流器 |
| `monitor/OperationMetrics.java` | 操作耗时统计 |
| `monitor/SchemaMetrics.java` | Schema 变更指标 |
| `monitor/IdempotentMetrics.java` | 幂等键指标 |
| `monitor/CircuitBreaker.java` | 熔断器 |
| `monitor/CircuitBreakerManager.java` | 熔断器管理 |
| `monitor/NebulaMonitor.java` | 统一监控器 |

### Phase 7: 灾备与配置

**创建文件：**

| 文件 | 职责 |
|------|------|
| `disaster/BackupManager.java` | 备份管理器 |
| `disaster/DisasterRecoveryManager.java` | 灾备恢复管理器 |
| `remote/ConfigCenterRefresher.java` | 配置中心刷新器 |
| `exception/NebulaGraphException.java` | 统一异常 |
| `exception/ConnectionException.java` | 连接异常 |
| `exception/SchemaException.java` | Schema 异常 |
| `exception/TimeoutException.java` | 超时异常 |
| `exception/BusinessException.java` | 业务异常 |

---

## 任务列表

### Phase 1: 基础架构

#### 任务 1.1：配置模块

- [ ] **步骤 1：创建 NebulaPoolConfig.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/config/NebulaPoolConfig.java
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

- [ ] **步骤 2：创建 NebulaGraphProperties.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/config/NebulaGraphProperties.java
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

- [ ] **步骤 3：创建 RetryConfig.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/config/RetryConfig.java
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

- [ ] **步骤 4：创建 CircuitBreakerConfig.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/config/CircuitBreakerConfig.java
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

- [ ] **步骤 5：创建 LoadBalanceConfig.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/config/LoadBalanceConfig.java
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
    /** 连续失败次数阈值（超过后标记为不可用） */
    private int failureThreshold = 3;
}

public enum LoadBalanceStrategy {
    ROUND_ROBIN,  // 轮询
    WEIGHTED,     // 加权
    FAILOVER      // 故障优先
}
```

- [ ] **步骤 6：创建 NebulaQuotaConfig.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/config/NebulaQuotaConfig.java
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
    /** 版本存储介质 */
    private VersionStorageType versionStorageType = VersionStorageType.NEBULA_GRAPH;
    /** 历史版本保留数量 */
    private int retainVersionCount = 10;
}

public enum VersionStorageType {
    MYSQL,
    REDIS,
    NEBULA_GRAPH
}
```

- [ ] **步骤 7：Commit 配置模块**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/config/
git commit -m "feat(graph): 添加 NebulaGraph 配置模块
- NebulaPoolConfig: 连接池配置
- NebulaGraphProperties: Graph 连接配置
- RetryConfig: 重试策略配置
- CircuitBreakerConfig: 熔断器配置
- LoadBalanceConfig: 负载均衡配置
- NebulaQuotaConfig: Schema 配额配置"
```

#### 任务 1.2：Session 会话池

- [ ] **步骤 1：创建 SessionWrapper.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/pool/SessionWrapper.java
@Data
public class SessionWrapper {
    /** Nebula Session 实例 */
    private Session session;
    /** Session 状态 */
    private SessionState state;
    /** 最后使用时间（用于空闲回收和泄漏检测） */
    private long lastUsedTime;
    /** Session 创建时间 */
    private long createTime;
    /** 所属 Nebula 图存储地址 */
    private String address;
    /** 所属业务线（用于熔断隔离） */
    private String businessLine;
}

public enum SessionState {
    IDLE,
    ACTIVE,
    INVALID
}
```

- [ ] **步骤 2：创建 NebulaSessionPool.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/pool/NebulaSessionPool.java
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

- [ ] **步骤 3：创建 AddressManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/pool/AddressManager.java
public interface AddressManager {
    int FAILURE_THRESHOLD = 3;
    long RECOVERY_INTERVAL = 30;

    void recordFailure(String address);
    String getAvailableAddress();
    void markAvailable(String address);
    void markUnavailable(String address);
    Set<String> getUnavailableAddresses();
}
```

- [ ] **步骤 4：创建 LoadBalancer.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/pool/LoadBalancer.java
public interface LoadBalancer {
    String selectAddress(List<String> availableAddresses);
    void updateWeight(String address, int weight);
    LoadBalanceStrategy getStrategy();
}
```

- [ ] **步骤 5：创建 SessionPoolMonitor.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/pool/SessionPoolMonitor.java
public interface SessionPoolMonitor {
    int getIdleCount();
    int getActiveCount();
    int getWaitingTasks();
    long getAvgBorrowWaitTime();
    long getConnectionCreateFailures();
    long getHeartbeatFailures();
    double getConnectionReuseRate();
    long getBorrowTimeouts();
    long getConnectionCreateTime();
    long getConnectionDestroyCount();
    long getWarmupFailures();
    List<SessionWrapper> detectLeakedSessions(long timeoutMs);
    void reclaimLeakedSessions(long timeoutMs);
}
```

- [ ] **步骤 6：创建 WriteSessionPool.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/pool/WriteSessionPool.java
public interface WriteSessionPool extends NebulaSessionPool {
    /** 写操作绑定主节点 */
}
```

- [ ] **步骤 7：创建 ReadSessionPool.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/pool/ReadSessionPool.java
public interface ReadSessionPool extends NebulaSessionPool {
    /** 读操作优先从节点，主从延迟超阈值自动切主 */
}
```

- [ ] **步骤 8：创建 SessionPoolInitializer.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/pool/SessionPoolInitializer.java
public interface SessionPoolInitializer {
    void warmUp();
    SessionWrapper lazyLoad();
    void onInitFailure(DegradationStrategy strategy);
}

public interface DegradationStrategy {
    Object degrade(String operation);
}
```

- [ ] **步骤 9：创建 SessionPoolScaler.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/pool/SessionPoolScaler.java
public interface SessionPoolScaler {
    boolean shouldScaleUp();
    boolean shouldScaleDown();
    void scaleUp();
    void scaleDown();
}
```

- [ ] **步骤 10：Commit 会话池模块**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/pool/
git commit -m "feat(graph): 添加 Session 会话池模块
- SessionWrapper: Session 包装器
- NebulaSessionPool: 会话池接口
- WriteSessionPool: 写入池
- ReadSessionPool: 读取池
- AddressManager: 地址管理器
- LoadBalancer: 负载均衡器
- SessionPoolMonitor: 连接池监控
- SessionPoolInitializer: 初始化器
- SessionPoolScaler: 动态扩缩容"
```

#### 任务 1.3：客户端门面

- [ ] **步骤 1：创建 NebulaGraphClient.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/client/NebulaGraphClient.java
public class NebulaGraphClient {
    private final NebulaSessionPool writePool;
    private final NebulaSessionPool readPool;
    private final SchemaManager schemaManager;
    private final NodeOperation nodeOperation;
    private final EdgeOperation edgeOperation;
    private final QueryBuilder queryBuilder;
    private final PathQuery pathQuery;

    public NebulaGraphClient(NebulaGraphProperties properties, NebulaPoolConfig poolConfig) {
        this.writePool = new WriteSessionPoolImpl(properties, poolConfig);
        this.readPool = new ReadSessionPoolImpl(properties, poolConfig);
        this.schemaManager = new SchemaManagerImpl(properties);
        this.nodeOperation = new NodeOperationImpl(writePool);
        this.edgeOperation = new EdgeOperationImpl(writePool);
        this.queryBuilder = new QueryBuilder(readPool);
        this.pathQuery = new PathQueryImpl(readPool);
    }

    public NodeOperation getNodeOperation() {
        return nodeOperation;
    }

    public EdgeOperation getEdgeOperation() {
        return edgeOperation;
    }

    public QueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    public PathQuery getPathQuery() {
        return pathQuery;
    }

    public SchemaManager getSchemaManager() {
        return schemaManager;
    }
}
```

- [ ] **步骤 2：Commit 客户端门面**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/client/
git commit -m "feat(graph): 添加 NebulaGraphClient 门面
- 整合会话池、Schema 管理、CRUD 操作、查询模块"
```

---

### Phase 2: Schema 管理

#### 任务 2.1：Schema 工具与校验

- [ ] **步骤 1：创建 SchemaUtils.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/schema/SchemaUtils.java
public class SchemaUtils {
    public static String quote(String identifier) {
        return identifier == null ? null : "`" + identifier + "`";
    }

    public static boolean validateIdentifier(String identifier) {
        return identifier != null && identifier.matches("^[a-z][a-z0-9_]*$");
    }

    public static String quoteLowerCase(String identifier) {
        return quote(identifier == null ? null : identifier.toLowerCase());
    }
}
```

- [ ] **步骤 2：创建 FieldTypeValidator.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/schema/FieldTypeValidator.java
public interface FieldTypeValidator {
    List<String> ALLOWED_TYPES = Arrays.asList(
        "string", "int", "bigint", "double", "float",
        "bool", "timestamp", "datetime", "date"
    );

    boolean validate(String type);
    void validateField(String fieldName, String type);
    boolean validateFieldCompatibility(String schemaName, List<FieldDefinition> newFields);
}
```

- [ ] **步骤 3：Commit Schema 工具**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/schema/SchemaUtils.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/schema/FieldTypeValidator.java
git commit -m "feat(graph): 添加 Schema 工具与校验器
- SchemaUtils: 标识符处理工具
- FieldTypeValidator: 字段类型校验"
```

#### 任务 2.2：Schema 管理器

- [ ] **步骤 1：创建 SchemaChangeLock.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/schema/SchemaChangeLock.java
public interface SchemaChangeLock {
    String tryLock(String schemaName, long timeout);
    void unlock(String schemaName, String token);
    boolean isLocked(String schemaName);
}
```

- [ ] **步骤 2：创建 FixedSchemaManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/schema/FixedSchemaManager.java
public interface FixedSchemaManager {
    void initialize();
    Set<String> getRegisteredTags();
    Set<String> getRegisteredEdgeTypes();
    boolean isFixedTag(String tagName);
    boolean isFixedEdgeType(String edgeTypeName);
    void extendTag(String tagName, List<FieldDefinition> newFields);
    void extendEdgeType(String edgeTypeName, List<FieldDefinition> newFields);
}
```

- [ ] **步骤 3：创建 DynamicSchemaManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/schema/DynamicSchemaManager.java
public interface DynamicSchemaManager {
    void createTag(TagDefinition definition);
    void createEdgeType(EdgeTypeDefinition definition);
    void createTagWithGray(TagDefinition definition, List<String> grayBusinessLines);
    void createEdgeTypeWithGray(EdgeTypeDefinition definition, List<String> grayBusinessLines);
    void dropTag(String tagName);
    void dropEdgeType(String edgeTypeName);
    void validateQuota();
    void scheduledQuotaCheck();
    Set<String> getDynamicSchemas();
    boolean detectSchemaChangeAbnormal(String schemaName);
}
```

- [ ] **步骤 4：创建 SchemaVersionManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/schema/SchemaVersionManager.java
public interface SchemaVersionManager {
    SchemaVersion createVersion(String schemaName, SchemaDefinition newDef);
    void commitVersion(String versionId);
    void rollbackVersion(String versionId);
    List<SchemaVersion> getVersionHistory(String schemaName);
    void grayPublish(String versionId, int percentage, GrayStrategy grayStrategy);
    boolean shouldAutoRollback(String versionId);
    void setVersionStorage(VersionStorageType storageType);
    void cleanExpiredVersions(int retainCount);
    void bindBusinessToVersion(String businessLine, String schemaName, String versionId);
}

public interface GrayStrategy {
    boolean isGray(String businessTag, String userId);
}
```

- [ ] **步骤 5：Commit Schema 管理器**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/schema/
git commit -m "feat(graph): 添加 Schema 管理模块
- SchemaChangeLock: 分布式锁
- FixedSchemaManager: 固定 Schema 管理
- DynamicSchemaManager: 动态 Schema 管理
- SchemaVersionManager: 版本管理"
```

#### 任务 2.3：索引管理器

- [ ] **步骤 1：创建 IndexManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/index/IndexManager.java
public interface IndexManager {
    void createTagIndex(String tagName, String propertyName);
    void createEdgeIndex(String edgeTypeName, String propertyName);
    String rebuildIndex(String indexName);
    boolean waitForIndexReady(String jobId, long timeoutSeconds);
    void dropIndex(String indexName);
    IndexStatus getIndexStatus(String indexName);
    void healthCheck();
    void cleanupUnusedIndexes();
}

public enum IndexStatus {
    CREATING,
    HEALTHY,
    DEGRADED,
    INVALID
}
```

- [ ] **步骤 2：Commit 索引管理器**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/index/
git commit -m "feat(graph): 添加 IndexManager 索引管理器
- 索引生命周期管理
- 健康检查与重建"
```

---

### Phase 3: 核心 CRUD

#### 任务 3.1：节点与边操作

- [ ] **步骤 1：创建 NodeOperation.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/crud/NodeOperation.java
public interface NodeOperation {
    boolean insertVertex(String tagName, GraphNode node);
    BatchResult batchInsertVertices(String tagName, List<GraphNode> nodes);
    boolean upsertVertex(String tagName, GraphNode node);
    boolean insertIfNotExists(String tagName, GraphNode node);
    boolean updateVertex(String tagName, String vid, Map<String, Object> properties);
    boolean patchUpdateVertex(String tagName, String vid, Map<String, Object> patchFields);
    boolean updateVertexWithExpectation(String tagName, String vid,
                                        Map<String, Object> newValues,
                                        Map<String, Object> expectedValues);
    boolean deleteVertex(String vid);
    boolean softDeleteVertex(String vid);
    boolean restoreVertex(String vid);
    BatchResult batchDeleteVertices(List<String> vids);
    Optional<GraphNode> getVertex(String vid, boolean includeDeleted);
    List<GraphNode> listVertices(String tagName, boolean includeDeleted);
}
```

- [ ] **步骤 2：创建 EdgeOperation.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/crud/EdgeOperation.java
public interface EdgeOperation {
    boolean insertEdge(String edgeTypeName, GraphEdge edge);
    BatchResult batchInsertEdges(String edgeTypeName, List<GraphEdge> edges);
    boolean upsertEdge(String edgeTypeName, GraphEdge edge);
    boolean insertEdgeIfNotExists(String edgeTypeName, GraphEdge edge);
    boolean updateEdge(String edgeTypeName, String fromVid, String toVid, Map<String, Object> properties);
    boolean patchUpdateEdge(String edgeTypeName, String fromVid, String toVid, Map<String, Object> patchFields);
    boolean deleteEdge(String fromVid, String toVid);
    boolean softDeleteEdge(String fromVid, String toVid);
    boolean restoreEdge(String fromVid, String toVid);
    BatchResult batchDeleteEdges(List<EdgeQuery> queries);
    List<GraphEdge> getOutEdges(String vid, boolean includeDeleted);
    List<GraphEdge> getInEdges(String vid, boolean includeDeleted);
}
```

- [ ] **步骤 3：Commit 节点与边操作**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/crud/NodeOperation.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/crud/EdgeOperation.java
git commit -m "feat(graph): 添加 NodeOperation 和 EdgeOperation 接口
- 节点 CRUD 操作
- 边 CRUD 操作
- 软删除与恢复支持"
```

#### 任务 3.2：批量操作

- [ ] **步骤 1：创建 BatchOperation.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/crud/BatchOperation.java
public interface BatchOperation {
    int MAX_BATCH_SIZE = 1000;

    void addVertex(String tagName, GraphNode node);
    void addEdge(String edgeTypeName, GraphEdge edge);
    void setShardingStrategy(ShardingStrategy strategy);
    BatchResult execute();
    BatchResult execute(String idempotentKey);
    BatchProgress getProgress(String batchId);
    BatchResult retryFailedShards(String batchId);
    void clear();
}

public enum ShardingStrategy {
    HASH_BY_VID,
    FIXED_SIZE,
    RANGE
}

@Data
public class BatchProgress {
    private String batchId;
    private int totalShards;
    private int completedShards;
    private int failedShards;
    private Date startTime;
    private Date lastUpdateTime;
    private List<String> failedShardIds;
}
```

- [ ] **步骤 2：创建 BatchConsistencyChecker.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/crud/BatchConsistencyChecker.java
public interface BatchConsistencyChecker {
    boolean checkConsistency(String batchId, int expectedCount);
    InconsistencyReport generateReport(String batchId);
}

@Data
public class InconsistencyReport {
    private String batchId;
    private int expectedCount;
    private int actualCount;
    private List<String> missingVids;
    private List<String> extraVids;
    private Date checkTime;
}
```

- [ ] **步骤 3：创建 BatchCompensator.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/crud/BatchCompensator.java
public interface BatchCompensator {
    void compensate(BatchResult failedBatch);
    CompensateStatus checkStatus(String batchId);
    void manualCompensate(String batchId);
    double getCompensateFailureAlertThreshold();
}

public enum CompensateStatus {
    PENDING,
    EXECUTING,
    SUCCESS,
    FAILED,
    MANUAL_INTERVENTION
}
```

- [ ] **步骤 4：Commit 批量操作**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/crud/BatchOperation.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/crud/BatchConsistencyChecker.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/crud/BatchCompensator.java
git commit -m "feat(graph): 添加批量操作模块
- BatchOperation: 批量操作（分片+幂等）
- BatchConsistencyChecker: 一致性校验
- BatchCompensator: 批量补偿器"
```

#### 任务 3.3：幂等与软删除

- [ ] **步骤 1：创建 IdempotentKeyGenerator.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/crud/IdempotentKeyGenerator.java
public interface IdempotentKeyGenerator {
    String generate(String operation, String vid);
}
```

- [ ] **步骤 2：创建 IdempotentStore.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/crud/IdempotentStore.java
public interface IdempotentStore {
    void record(String key, long ttl);
    boolean exists(String key);
    boolean checkAndRecord(String key, long ttl);
    void renew(String key, long additionalTtl);
    void remove(String key);
}
```

- [ ] **步骤 3：创建 SoftDeleteCleaner.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/crud/SoftDeleteCleaner.java
public interface SoftDeleteCleaner {
    void archive(int retentionDays);
    void purge(int retentionDays);
    long countPendingCleanup(int retentionDays);
    void setArchiveStorage(ArchiveStorage storage);
    boolean validateRestoreFeasibility(String vid);
    void createSoftDeleteIndex(String tagName);
}

public enum ArchiveStorage {
    OFFLINE_NEBULA,
    MINIO,
    HDFS
}
```

- [ ] **步骤 4：Commit 幂等与软删除**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/crud/IdempotentKeyGenerator.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/crud/IdempotentStore.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/crud/SoftDeleteCleaner.java
git commit -m "feat(graph): 添加幂等与软删除模块
- IdempotentKeyGenerator: 幂等键生成
- IdempotentStore: 幂等键存储（Redis）
- SoftDeleteCleaner: 软删除清理"
```

#### 任务 3.4：VID 管理

- [ ] **步骤 1：创建 VidMapper.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/model/vid/VidMapper.java
public interface VidMapper {
    String toVid(String type, String businessId);
    String parseBusinessId(String vid);
    String parseType(String vid);
    boolean exists(String vid);
}
```

- [ ] **步骤 2：创建 VidVersionManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/model/vid/VidVersionManager.java
public interface VidVersionManager {
    void recordMapping(String vid, String businessId, String type, long version);
    String getCurrentVid(String businessId, String type);
    List<String> getHistoricalVids(String businessId, String type);
    void changeBusinessId(String oldBusinessId, String newBusinessId, String type);
}
```

- [ ] **步骤 3：Commit VID 管理**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/model/vid/
git commit -m "feat(graph): 添加 VID 管理模块
- VidMapper: VID 映射管理
- VidVersionManager: VID 版本管理"
```

---

### Phase 4: 查询封装

#### 任务 4.1：查询构建器

- [ ] **步骤 1：创建 QueryBuilder.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/query/QueryBuilder.java
public class QueryBuilder {
    public QueryBuilder match(String pattern);
    public QueryBuilder match(String vertexType, Map<String, Object> whereConditions);
    public QueryBuilder where(String condition, Object... params);
    public QueryBuilder where(Map<String, Object> conditions);
    public QueryBuilder whereIn(String field, List<Object> values);
    public QueryBuilder whereOr(List<Condition> conditions);
    public QueryBuilder yield(String... fields);
    public QueryBuilder orderBy(String field, SortDirection direction);
    public QueryBuilder limit(int offset, int count);
    public QueryResult execute();
    public QueryResult executeAll(int maxResultCount);
}

public enum SortDirection {
    ASC,
    DESC
}
```

- [ ] **步骤 2：Commit 查询构建器**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/query/QueryBuilder.java
git commit -m "feat(graph): 添加 QueryBuilder 查询构建器
- 参数化查询防注入
- 链式 API"
```

#### 任务 4.2：路径查询

- [ ] **步骤 1：创建 PathQuery.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/query/PathQuery.java
public interface PathQuery {
    List<QueryResult> neighbors(String vid, String vertexType, List<String> edgeTypes);
    List<QueryResult> matchPaths(String startVid, String startType, String edgeType,
                                  int minHop, int maxHop, int maxVisited,
                                  int maxResultCount);
    List<QueryResult> shortestPath(String fromVid, String toVid, String edgeType);
    List<QueryResult> allPaths(String fromVid, String toVid, String edgeType,
                                 int maxHop, int maxVisited, int maxResultCount);
    void enableResultCache(long ttlMs);
    void setCycleDetectionConfig(CycleDetectionConfig config);
    List<QueryResult> queryPathsWithPagination(String fromVid, String toVid, String edgeType,
                                                int pageNum, int pageSize);
}

@Data
public class CycleDetectionConfig {
    private int maxVisitedCount = 1000;
    private int maxHopDepth = 10;
    private boolean enableVidTracking = true;
}
```

- [ ] **步骤 2：创建 VersionedQuery.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/query/VersionedQuery.java
public interface VersionedQuery {
    BOMTree getProductBOMAt(String productId, long timestamp);
    Optional<GraphNode> getVertexAt(String vid, long version);
    Optional<GraphEdge> getEdgeAt(String fromVid, String toVid, long version);
    void setVersionGenerator(VersionGenerator generator);
    void compressHistoricalVersions(String schemaName);
    void preloadHotVersions(String schemaName, List<Long> hotVersions);
}

public enum VersionGenerator {
    TIMESTAMP,
    AUTO_INCREMENT,
    BUSINESS_SPECIFIC
}
```

- [ ] **步骤 3：Commit 路径查询**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/query/
git commit -m "feat(graph): 添加查询封装模块
- PathQuery: 路径查询（循环检测+分页）
- VersionedQuery: 版本化查询"
```

---

### Phase 5: PLM 业务 API

#### 任务 5.1：PLM 节点与边服务

- [ ] **步骤 1：创建 PLMNodeService.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/plm/PLMNodeService.java
public interface PLMNodeService {
    void createProduct(Product product);
    void createPart(Part part);
    void createDocument(Document document);
    void createFolder(Folder folder);
    Optional<Product> getProduct(String productId);
    Optional<Part> getPart(String partId);
    Optional<Document> getDocument(String docId);
}
```

- [ ] **步骤 2：创建 PLMEdgeService.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/plm/PLMEdgeService.java
public interface PLMEdgeService {
    void createContainsLink(String parentId, String parentType,
                            String childId, String childType,
                            Map<String, Object> props);
    void createVersionLink(String masterId, String versionId, String linkType);
    void deleteContainsLink(String parentId, String parentType,
                            String childId, String childType);
    List<Part> getProductChildren(String productId);
    List<Part> getProductBOM(String productId, int maxDepth, int maxVisited);
}
```

- [ ] **步骤 3：Commit PLM 节点与边服务**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/plm/PLMNodeService.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/plm/PLMEdgeService.java
git commit -m "feat(graph): 添加 PLM 节点与边服务
- PLMNodeService: PLM 节点业务 API
- PLMEdgeService: PLM 边业务 API"
```

#### 任务 5.2：PLM 路径与 BOM 服务

- [ ] **步骤 1：创建 PLMPathQuery.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/plm/PLMPathQuery.java
public interface PLMPathQuery {
    BOMTree getProductBOMTree(String productId, int maxDepth);
    FolderTree getDocumentTree(String folderId, int maxDepth);
    RelationGraph getPartRelations(String partId, int maxDepth);
    List<PathResult> traceSource(String partId, int depth, int maxVisited);
    List<PathResult> traceTarget(String partId, int depth, int maxVisited);
}
```

- [ ] **步骤 2：创建 BOMDiffService.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/plm/BOMDiffService.java
public interface BOMDiffService {
    BOMDiffResult diff(BOMTree oldBOM, BOMTree newBOM);
    BatchResult applyDiff(String productId, BOMDiffResult diff);
}

@Data
public class BOMDiffResult {
    private List<GraphNode> addedNodes;
    private List<GraphNode> removedNodes;
    private List<GraphEdge> addedEdges;
    private List<GraphEdge> removedEdges;
    private List<GraphNode> updatedNodes;
}
```

- [ ] **步骤 3：创建 LargePropertyManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/plm/LargePropertyManager.java
public interface LargePropertyManager {
    String upload(byte[] data, String contentType);
    byte[] download(String url);
    void delete(String url);
}
```

- [ ] **步骤 4：Commit PLM 路径与 BOM 服务**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/plm/PLMPathQuery.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/plm/BOMDiffService.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/plm/LargePropertyManager.java
git commit -m "feat(graph): 添加 PLM 路径与 BOM 服务
- PLMPathQuery: PLM 路径查询
- BOMDiffService: BOM 增量更新
- LargePropertyManager: 大属性管理"
```

#### 任务 5.3：PLM 缓存

- [ ] **步骤 1：创建 PLMCache.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/plm/cache/PLMCache.java
public interface PLMCache {
    Optional<Product> getProduct(String productId);
    Optional<Part> getPart(String partId);
    Optional<Document> getDocument(String docId);
    void invalidate(String key);
    void invalidateByType(String type);
}
```

- [ ] **步骤 2：创建 BloomFilter.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/plm/cache/BloomFilter.java
public interface BloomFilter {
    void add(String key);
    boolean mightContain(String key);
    void clear();
}
```

- [ ] **步骤 3：Commit PLM 缓存**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/plm/cache/
git commit -m "feat(graph): 添加 PLM 缓存模块
- PLMCache: PLM 业务缓存
- BloomFilter: 布隆过滤器（防穿透）"
```

---

### Phase 6: 监控与优化

#### 任务 6.1：熔断器

- [ ] **步骤 1：创建 CircuitBreaker.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/CircuitBreaker.java
public class CircuitBreaker {
    enum CircuitState { CLOSED, OPEN, HALF_OPEN }
    private String businessLine;

    boolean allowRequest();
    void recordSuccess();
    void recordFailure();
    void recordSlowRequest();
    boolean shouldTrip(CircuitContext context);
    Object degrade(String operation);
    void setProbeRule(ProbeRule rule);
    void persistState();
    void restoreState();
}
```

- [ ] **步骤 2：创建 CircuitBreakerManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/CircuitBreakerManager.java
public interface CircuitBreakerManager {
    CircuitBreakerStatus getStatus(String name);
    void forceOpen(String name);
    void forceClose(String name);
    void reset(String name);
    void updateThreshold(String name, CircuitBreakerConfig config);
    Map<String, CircuitBreakerStatus> getStatusByBusinessLine(String businessLine);
}

@Data
public class CircuitBreakerStatus {
    private String name;
    private CircuitState state;
    private double failureRate;
    private long lastFailureTime;
    private int totalRequests;
    private int failedRequests;
}
```

- [ ] **步骤 3：Commit 熔断器**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/CircuitBreaker.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/CircuitBreakerManager.java
git commit -m "feat(graph): 添加熔断器模块
- CircuitBreaker: 多维度熔断器
- CircuitBreakerManager: 熔断器管理（人工干预）"
```

#### 任务 6.2：监控指标

- [ ] **步骤 1：创建 SlowQueryLog.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/SlowQueryLog.java
public class SlowQueryLog {
    void logSlowQuery(String ngql, long costMs, String requestId, String operator,
                      String businessScene, Map<String, Object> params);
}
```

- [ ] **步骤 2：创建 SlowQueryRateLimiter.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/SlowQueryRateLimiter.java
public interface SlowQueryRateLimiter {
    boolean allow(String operator);
    void recordSlowQuery(String operator);
    SlowQueryThreshold getThreshold(String operator);
}
```

- [ ] **步骤 3：创建 OperationMetrics.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/OperationMetrics.java
public class OperationMetrics {
    void recordOperation(String operation, long costMs, boolean success);
    Map<String, MetricSummary> getMetrics();
    long getP99Latency(String operation);
}
```

- [ ] **步骤 4：创建 SchemaMetrics.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/SchemaMetrics.java
public class SchemaMetrics {
    void recordSchemaChange(String schemaName, String operation);
    Map<String, SchemaChangeMetrics> getSchemaMetrics();
}
```

- [ ] **步骤 5：创建 IdempotentMetrics.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/IdempotentMetrics.java
public class IdempotentMetrics {
    long getHitCount();
    long getMissCount();
    long getRenewCount();
    long getDuplicateWriteCount();
}
```

- [ ] **步骤 6：创建 NebulaMonitor.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/NebulaMonitor.java
public class NebulaMonitor {
    void setMetricCollectInterval(long intervalMs);
    void setMetricStorage(MetricStorage storage);
    void updateAlertRule(String ruleId, AlertRule rule);
    void integrateTracing(String traceId, Map<String, String> spanTags);
    void onError(String operation, Exception e);
    void onConnectionError(Exception e);
    void onBatchFailure(String batchId, double failureRate);
}

public enum MetricStorage {
    PROMETHEUS,
    INFLUXDB,
    ELASTICSEARCH
}

@Data
public class AlertRule {
    private String ruleId;
    private String metricName;
    private double threshold;
    private AlertLevel level;
    private long windowMs;
}

public enum AlertLevel {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}
```

- [ ] **步骤 7：Commit 监控指标**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/
git commit -m "feat(graph): 添加监控模块
- SlowQueryLog: 慢查询日志
- SlowQueryRateLimiter: 慢查询限流
- OperationMetrics: 操作指标
- SchemaMetrics: Schema 指标
- IdempotentMetrics: 幂等指标
- NebulaMonitor: 统一监控器"
```

---

### Phase 7: 灾备与配置

#### 任务 7.1：灾备恢复

- [ ] **步骤 1：创建 BackupManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/disaster/BackupManager.java
public interface BackupManager {
    BackupResult backup();
    BackupResult incrementalBackup();
    BackupResult backupWithEncryption(EncryptionConfig config);
    boolean validateBackupIntegrity(String backupId);
    List<BackupMeta> listBackups();
    void deleteExpiredBackups(int retentionDays);
    void setBackupNetworkConfig(NetworkConfig config);
}

@Data
public class BackupMeta {
    private String id;
    private BackupType type;
    private Date createTime;
    private long size;
    private String storagePath;
    private IntegrityStatus integrityStatus;
}

public enum BackupType {
    FULL,
    INCREMENTAL
}

public enum IntegrityStatus {
    UNCHECKED,
    VALID,
    CORRUPTED
}

@Data
public class EncryptionConfig {
    private String algorithm;
    private String keyId;
    private boolean encryptMetadata;
}

@Data
public class NetworkConfig {
    private boolean enableCompression = true;
    private int compressionLevel = 6;
    private int maxBandwidth = 100;
}
```

- [ ] **步骤 2：创建 DisasterRecoveryManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/disaster/DisasterRecoveryManager.java
public interface DisasterRecoveryManager {
    void restore(String backupId);
    void switchToStandby();
    boolean isPrimaryHealthy();
    RecoveryMetrics getRecoveryMetrics();
}

@Data
public class RecoveryMetrics {
    private long rtoMinutes;
    private long rpoMinutes;
    private Date lastRestoreTime;
    private Date lastSwitchTime;
}
```

- [ ] **步骤 3：Commit 灾备恢复**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/disaster/
git commit -m "feat(graph): 添加灾备恢复模块
- BackupManager: 备份管理器
- DisasterRecoveryManager: 灾备恢复管理器"
```

#### 任务 7.2：配置中心

- [ ] **步骤 1：创建 ConfigCenterRefresher.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/remote/ConfigCenterRefresher.java
public interface ConfigCenterRefresher {
    void listen(String dataId, ConfigChangeListener listener);
    void refreshPoolConfig(NebulaPoolConfig config);
    void refreshCircuitBreaker(String name, CircuitBreakerConfig config);
    void refreshRetryConfig(RetryConfig config);
    void refreshConfigWithGray(String dataId, List<String> grayInstanceIds);
    void rollbackConfig(String dataId, String version);
    void enableLocalCache(long cacheTtlMs);
}

public interface ConfigChangeListener {
    void onChange(String dataId, String content);
}
```

- [ ] **步骤 2：Commit 配置中心**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/remote/
git commit -m "feat(graph): 添加配置中心模块
- ConfigCenterRefresher: 配置中心刷新器（支持灰度/回滚/本地缓存）"
```

#### 任务 7.3：异常定义

- [ ] **步骤 1：创建异常类**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/exception/NebulaGraphException.java
public class NebulaGraphException extends RuntimeException {
    public NebulaGraphException(String message) {
        super(message);
    }
}

public class ConnectionException extends NebulaGraphException {
    public ConnectionException(String message) {
        super(message);
    }
}

public class SchemaException extends NebulaGraphException {
    public SchemaException(String message) {
        super(message);
    }
}

public class TimeoutException extends NebulaGraphException {
    public TimeoutException(String message) {
        super(message);
    }
}

public class BusinessException extends NebulaGraphException {
    public BusinessException(String message) {
        super(message);
    }
}
```

- [ ] **步骤 2：Commit 异常定义**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/exception/
git commit -m "feat(graph): 添加异常定义
- NebulaGraphException: 统一异常基类
- ConnectionException: 连接异常
- SchemaException: Schema 异常
- TimeoutException: 超时异常
- BusinessException: 业务异常"
```

---

### Phase 8: 集成与测试

#### 任务 8.1：集成测试

- [ ] **步骤 1：创建集成测试**

```java
// 文件：mf-common-graph/src/test/java/cn/com/mfish/graph/NebulaSessionPoolIntegrationTest.java
public class NebulaSessionPoolIntegrationTest {
    @Test
    void testBorrowAndReturn() {
        NebulaGraphClient client = new NebulaGraphClient(properties, poolConfig);
        SessionWrapper wrapper = client.getWritePool().borrowSession();
        assertNotNull(wrapper);
        client.getWritePool().returnSession(wrapper);
    }

    @Test
    void testConcurrentBorrow() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    NebulaGraphClient client = new NebulaGraphClient(properties, poolConfig);
                    SessionWrapper wrapper = client.getWritePool().borrowSession();
                    client.getWritePool().returnSession(wrapper);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
    }
}
```

- [ ] **步骤 2：Commit 集成测试**

```bash
git add mf-common-graph/src/test/java/cn/com/mfish/graph/
git commit -m "test(graph): 添加集成测试
- NebulaSessionPoolIntegrationTest: 会话池并发测试"
```

---

## 自检清单

### 规格覆盖度

- [x] 配置模块：NebulaPoolConfig、NebulaGraphProperties、RetryConfig、CircuitBreakerConfig、LoadBalanceConfig、NebulaQuotaConfig
- [x] 会话池模块：SessionWrapper、NebulaSessionPool、WriteSessionPool、ReadSessionPool、AddressManager、LoadBalancer、SessionPoolMonitor、SessionPoolScaler
- [x] Schema 管理：SchemaUtils、FieldTypeValidator、SchemaChangeLock、FixedSchemaManager、DynamicSchemaManager、SchemaVersionManager
- [x] CRUD：NodeOperation、EdgeOperation、BatchOperation、BatchConsistencyChecker、BatchCompensator、IdempotentStore、SoftDeleteCleaner
- [x] 查询：QueryBuilder、PathQuery、VersionedQuery
- [x] PLM：PLMNodeService、PLMEdgeService、PLMPathQuery、BOMDiffService、LargePropertyManager、PLMCache
- [x] 监控：CircuitBreaker、CircuitBreakerManager、SlowQueryLog、SlowQueryRateLimiter、OperationMetrics、NebulaMonitor
- [x] 灾备：BackupManager、DisasterRecoveryManager、ConfigCenterRefresher

### 占位符扫描

- [ ] 无"TODO"、"待定"等未完成标记
- [ ] 无"后续实现"、"补充细节"等占位符
- [ ] 每个步骤都有实际代码

### 类型一致性

- [ ] SessionState 枚举定义一致
- [ ] ShardingStrategy 枚举定义一致
- [ ] VersionStorageType 枚举定义一致
- [ ] 所有类名、方法签名与规格一致

---

**计划版本**：1.0
**创建日期**：2026-04-18
**作者**：mfish
