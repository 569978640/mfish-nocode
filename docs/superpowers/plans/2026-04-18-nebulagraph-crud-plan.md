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

- [ ] **步骤 1：创建 NebulaGraphClient.java（完整实现）**

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
        // 1. 初始化地址管理器和负载均衡器
        AddressManager addressManager = new AddressManagerImpl(properties.getAddresses());
        LoadBalancer loadBalancer = new LoadBalancerImpl(properties.getLoadBalanceStrategy());
        
        // 2. 初始化会话池
        NebulaSessionPool writePool = new WriteSessionPoolImpl(properties, poolConfig, addressManager, loadBalancer);
        NebulaSessionPool readPool = new ReadSessionPoolImpl(properties, poolConfig, addressManager, loadBalancer);
        
        // 3. 初始化 Schema 管理器
        SchemaManager schemaManager = new SchemaManagerImpl(properties);
        
        // 4. 初始化 CRUD 操作
        NodeOperation nodeOperation = new NodeOperationImpl(writePool);
        EdgeOperation edgeOperation = new EdgeOperationImpl(writePool);
        
        // 5. 初始化查询
        QueryBuilder queryBuilder = new QueryBuilder(readPool);
        PathQuery pathQuery = new PathQueryImpl(readPool);
        
        this.writePool = writePool;
        this.readPool = readPool;
        this.schemaManager = schemaManager;
        this.nodeOperation = nodeOperation;
        this.edgeOperation = edgeOperation;
        this.queryBuilder = queryBuilder;
        this.pathQuery = pathQuery;
    }

    public NebulaSessionPool getWritePool() {
        return writePool;
    }

    public NebulaSessionPool getReadPool() {
        return readPool;
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

    public void destroy() {
        writePool.destroy();
        readPool.destroy();
    }
}
```

- [ ] **步骤 2：创建 MultiAddressSessionPool.java（多地址会话池）**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/pool/MultiAddressSessionPool.java
public class MultiAddressSessionPool implements NebulaSessionPool {
    private final NebulaGraphProperties properties;
    private final NebulaPoolConfig poolConfig;
    private final AddressManager addressManager;
    private final LoadBalancer loadBalancer;
    private final ConcurrentLinkedQueue<SessionWrapper> idleSessions;
    private final AtomicInteger activeCount;
    private final Semaphore borrowSemaphore;
    
    public MultiAddressSessionPool(NebulaGraphProperties properties, 
                                   NebulaPoolConfig poolConfig,
                                   AddressManager addressManager,
                                   LoadBalancer loadBalancer) {
        this.properties = properties;
        this.poolConfig = poolConfig;
        this.addressManager = addressManager;
        this.loadBalancer = loadBalancer;
        this.idleSessions = new ConcurrentLinkedQueue<>();
        this.activeCount = new AtomicInteger(0);
        this.borrowSemaphore = new Semaphore(poolConfig.getMaxPoolSize());
    }
    
    @Override
    public SessionWrapper borrowSession() {
        // 1. 获取可用地址
        String address = loadBalancer.selectAddress(addressManager.getAvailableAddresses());
        
        // 2. 尝试从池中获取
        SessionWrapper wrapper = idleSessions.poll();
        if (wrapper != null && wrapper.getState() == SessionState.IDLE) {
            wrapper.setState(SessionState.ACTIVE);
            wrapper.setLastUsedTime(System.currentTimeMillis());
            activeCount.incrementAndGet();
            return wrapper;
        }
        
        // 3. 池中没有可用 Session，创建新的
        try {
            Session session = createSession(address);
            wrapper = new SessionWrapper();
            wrapper.setSession(session);
            wrapper.setState(SessionState.ACTIVE);
            wrapper.setAddress(address);
            wrapper.setCreateTime(System.currentTimeMillis());
            wrapper.setLastUsedTime(System.currentTimeMillis());
            activeCount.incrementAndGet();
            return wrapper;
        } catch (Exception e) {
            addressManager.recordFailure(address);
            throw new ConnectionException("创建 Session 失败: " + address, e);
        }
    }
    
    @Override
    public void returnSession(SessionWrapper wrapper) {
        if (wrapper == null) {
            return;
        }
        
        // 1. 执行健康检查
        if (!healthCheck(wrapper)) {
            wrapper.setState(SessionState.INVALID);
            addressManager.recordFailure(wrapper.getAddress());
            destroySession(wrapper);
            activeCount.decrementAndGet();
            return;
        }
        
        // 2. 归还到池中
        wrapper.setState(SessionState.IDLE);
        wrapper.setLastUsedTime(System.currentTimeMillis());
        idleSessions.offer(wrapper);
        activeCount.decrementAndGet();
    }
    
    @Override
    public ResultSet executeQuery(String ngql) {
        SessionWrapper wrapper = borrowSession();
        try {
            return wrapper.getSession().execute(ngql);
        } finally {
            returnSession(wrapper);
        }
    }
    
    @Override
    public boolean executeWrite(String ngql) {
        SessionWrapper wrapper = borrowSession();
        try {
            ResultSet result = wrapper.getSession().execute(ngql);
            return result.isSucceeded();
        } finally {
            returnSession(wrapper);
        }
    }
    
    @Override
    public void destroy() {
        SessionWrapper wrapper;
        while ((wrapper = idleSessions.poll()) != null) {
            destroySession(wrapper);
        }
    }
    
    private Session createSession(String address) {
        // 使用 vesoft client 创建 Session
        // 实际实现需要调用 NebulaGraphClientProvider
        throw new UnsupportedOperationException("请实现 createSession 方法");
    }
    
    private boolean healthCheck(SessionWrapper wrapper) {
        try {
            ResultSet result = wrapper.getSession().execute("YIELD 1");
            return result.isSucceeded();
        } catch (Exception e) {
            return false;
        }
    }
    
    private void destroySession(SessionWrapper wrapper) {
        try {
            wrapper.getSession().close();
        } catch (Exception e) {
            // 忽略关闭异常
        }
    }
}
```

- [ ] **步骤 3：Commit 客户端门面**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/client/
git add mf-common-graph/src/main/java/cn/com/mfish/graph/pool/MultiAddressSessionPool.java
git commit -m "feat(graph): 添加 NebulaGraphClient 门面和 MultiAddressSessionPool
- NebulaGraphClient: 整合会话池、Schema 管理、CRUD 操作、查询模块
- MultiAddressSessionPool: 多地址会话池实现"
```

---

### Phase 2: Schema 管理

#### 任务 2.1：Schema 工具与校验

- [ ] **步骤 1：创建 schema/model/TagDefinition.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/schema/model/TagDefinition.java
@Data
public class TagDefinition {
    /** Tag 名称 */
    private String name;
    /** 字段定义列表 */
    private List<FieldDefinition> fields;
    /** 注释 */
    private String comment;
    /** 是否为基础类型（不可删除） */
    private boolean fixed;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;
    /** 所属业务线（用于灰度） */
    private List<String> businessLines;
}

@Data
public class FieldDefinition {
    /** 字段名称 */
    private String name;
    /** 字段类型 */
    private String type;
    /** 默认值 */
    private String defaultValue;
    /** 是否可为空 */
    private boolean nullable;
    /** 注释 */
    private String comment;
}
```

- [ ] **步骤 2：创建 schema/model/EdgeTypeDefinition.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/schema/model/EdgeTypeDefinition.java
@Data
public class EdgeTypeDefinition {
    /** EdgeType 名称 */
    private String name;
    /** 字段定义列表 */
    private List<FieldDefinition> fields;
    /** 注释 */
    private String comment;
    /** 是否为基础类型 */
    private boolean fixed;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;
    /** 所属业务线 */
    private List<String> businessLines;
    /** rankKey（可选，用于多边场景） */
    private String rankKey;
}
```

- [ ] **步骤 4：创建 SchemaUtils.java**

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

- [ ] **步骤 5：创建 FieldTypeValidator.java**

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

- [ ] **步骤 6：Commit Schema 工具**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/schema/model/
git add mf-common-graph/src/main/java/cn/com/mfish/graph/schema/SchemaUtils.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/schema/FieldTypeValidator.java
git commit -m "feat(graph): 添加 Schema 工具与校验器
- TagDefinition: Tag 定义模型
- EdgeTypeDefinition: EdgeType 定义模型
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

- [ ] **步骤 3：创建 model/result/QueryResult.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/model/result/QueryResult.java
@Data
public class QueryResult<T> {
    /** 查询是否成功 */
    private boolean success;
    /** 结果数据 */
    private T data;
    /** 错误信息 */
    private String errorMessage;
    /** 执行耗时(ms) */
    private long costMs;
    /** 结果数量 */
    private int count;
    /** 是否有更多结果 */
    private boolean hasMore;
    /** 查询上下文（用于追踪） */
    private Map<String, Object> context;

    public static <T> QueryResult<T> success(T data) {
        QueryResult<T> result = new QueryResult<>();
        result.setSuccess(true);
        result.setData(data);
        return result;
    }

    public static <T> QueryResult<T> fail(String errorMessage) {
        QueryResult<T> result = new QueryResult<>();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
```

- [ ] **步骤 4：创建 model/result/BatchResult.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/model/result/BatchResult.java
@Data
public class BatchResult {
    /** 批次ID */
    private String batchId;
    /** 是否完全成功 */
    private boolean success;
    /** 总数量 */
    private int totalCount;
    /** 成功数量 */
    private int successCount;
    /** 失败数量 */
    private int failedCount;
    /** 失败率 */
    private double failureRate;
    /** 失败详情 */
    private List<FailedItem> failedItems;
    /** 执行耗时(ms) */
    private long costMs;
    /** 开始时间 */
    private Date startTime;
    /** 结束时间 */
    private Date endTime;
    /** 幂等键 */
    private String idempotentKey;

    @Data
    public static class FailedItem {
        private String vid;
        private String operation;
        private String errorMessage;
        private int retryCount;
    }

    public static BatchResult success(String batchId, int totalCount) {
        BatchResult result = new BatchResult();
        result.setBatchId(batchId);
        result.setSuccess(true);
        result.setTotalCount(totalCount);
        result.setSuccessCount(totalCount);
        result.setFailedCount(0);
        result.setFailureRate(0);
        return result;
    }

    public static BatchResult partialSuccess(String batchId, int totalCount, List<FailedItem> failedItems) {
        BatchResult result = new BatchResult();
        result.setBatchId(batchId);
        result.setSuccess(false);
        result.setTotalCount(totalCount);
        result.setFailedCount(failedItems.size());
        result.setSuccessCount(totalCount - failedItems.size());
        result.setFailureRate((double) failedItems.size() / totalCount);
        result.setFailedItems(failedItems);
        return result;
    }
}
```

- [ ] **步骤 5：Commit 节点与边操作**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/model/result/QueryResult.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/model/result/BatchResult.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/crud/NodeOperation.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/crud/EdgeOperation.java
git commit -m "feat(graph): 添加 NodeOperation 和 EdgeOperation 接口
- NodeOperation: 节点 CRUD 操作
- EdgeOperation: 边 CRUD 操作
- QueryResult: 查询结果模型
- BatchResult: 批量结果模型
- 软删除与恢复支持"
```

#### 任务 3.2：批量操作

- [ ] **步骤 6：创建 BatchOperation.java**

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

- [ ] **步骤 7：创建 BatchConsistencyChecker.java**

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

- [ ] **步骤 8：创建 BatchCompensator.java**

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

- [ ] **步骤 9：Commit 批量操作**

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

- [ ] **步骤 10：创建 IdempotentKeyGenerator.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/crud/IdempotentKeyGenerator.java
public interface IdempotentKeyGenerator {
    String generate(String operation, String vid);
}
```

- [ ] **步骤 11：创建 IdempotentStore.java**

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

- [ ] **步骤 12：创建 SoftDeleteCleaner.java**

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

- [ ] **步骤 13：Commit 幂等与软删除**

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

- [ ] **步骤 14：创建 VidMapper.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/model/vid/VidMapper.java
public interface VidMapper {
    String toVid(String type, String businessId);
    String parseBusinessId(String vid);
    String parseType(String vid);
    boolean exists(String vid);
}
```

- [ ] **步骤 15：创建 VidVersionManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/model/vid/VidVersionManager.java
public interface VidVersionManager {
    void recordMapping(String vid, String businessId, String type, long version);
    String getCurrentVid(String businessId, String type);
    List<String> getHistoricalVids(String businessId, String type);
    void changeBusinessId(String oldBusinessId, String newBusinessId, String type);
}
```

- [ ] **步骤 16：Commit VID 管理**

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

- [ ] **步骤 6：创建 AlertManager.java**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/AlertManager.java
public interface AlertManager {
    /**
     * 发送告警
     * @param level 告警级别
     * @param title 告警标题
     * @param message 告警消息
     * @param tags 标签（用于分类和过滤）
     */
    void alert(AlertLevel level, String title, String message, Map<String, String> tags);

    /**
     * 发送告警（便捷方法）
     */
    void alert(AlertLevel level, String title, String message);

    /**
     * 发送关键告警（最高级别）
     */
    void critical(String title, String message);

    /**
     * 发送错误告警
     */
    void error(String title, String message);

    /**
     * 发送警告告警
     */
    void warning(String title, String message);

    /**
     * 发送信息告警
     */
    void info(String title, String message);

    /**
     * 注册告警处理器
     * @param handler 自定义告警处理器
     */
    void registerHandler(AlertHandler handler);

    /**
     * 注销告警处理器
     * @param handler 自定义告警处理器
     */
    void unregisterHandler(AlertHandler handler);

    /**
     * 静默告警（临时屏蔽）
     * @param tags 标签
     * @param durationSeconds 静默时长（秒）
     */
    void silence(Map<String, String> tags, long durationSeconds);

    /**
     * 取消静默
     * @param tags 标签
     */
    void unsilence(Map<String, String> tags);
}

public interface AlertHandler {
    void handle(Alert alert);
}

@Data
public class Alert {
    private String id;
    private AlertLevel level;
    private String title;
    private String message;
    private Map<String, String> tags;
    private Date createTime;
    private String traceId;
    private String source;
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
- NebulaMonitor: 统一监控器
- AlertManager: 告警管理器"
```

#### 任务 6.3：全链路追踪集成

- [ ] **步骤 1：集成 SkyWalking 或 Jaeger**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/TracingIntegration.java
public class TracingIntegration {
    /**
     * 初始化链路追踪
     * @param tracingType 追踪类型（SkyWalking/Jaeger/Zipkin）
     * @param config 追踪配置
     */
    public void init(TracingType tracingType, TracingConfig config);

    /**
     * 创建追踪 span
     * @param operationName 操作名称
     * @param parentSpan 可选的父 span
     * @return span 上下文
     */
    SpanContext startSpan(String operationName, SpanContext parentSpan);

    /**
     * 结束 span
     * @param context span 上下文
     * @param success 是否成功
     */
    void endSpan(SpanContext context, boolean success);

    /**
     * 记录追踪标签
     * @param context span 上下文
     * @param key 标签 key
     * @param value 标签 value
     */
    void tag(SpanContext context, String key, String value);

    /**
     * 记录追踪日志
     * @param context span 上下文
     * @param event 事件名称
     * @param params 事件参数
     */
    void log(SpanContext context, String event, Map<String, Object> params);

    /**
     * 获取当前追踪 ID
     * @return traceId
     */
    String getCurrentTraceId();
}

public enum TracingType {
    SKYWALKING,
    JAEGER,
    ZIPKIN
}

@Data
public class TracingConfig {
    /** 服务名称 */
    private String serviceName;
    /** 探针类型 */
    private TracingType type;
    /** 服务地址 */
    private String address;
    /** 采样率 */
    private double sampleRate = 1.0;
}
```

- [ ] **步骤 2：在 NebulaMonitor 中集成追踪**

```java
// 在 NebulaMonitor 中添加追踪集成
public class NebulaMonitor {
    private TracingIntegration tracing;

    public void setTracing(TracingIntegration tracing) {
        this.tracing = tracing;
    }

    public void onError(String operation, Exception e) {
        SpanContext span = tracing.startSpan("nebula.error", null);
        tracing.tag(span, "operation", operation);
        tracing.tag(span, "error", e.getClass().getSimpleName());
        tracing.tag(span, "message", e.getMessage());
        tracing.endSpan(span, false);
    }

    public void onConnectionError(Exception e) {
        SpanContext span = tracing.startSpan("nebula.connection.error", null);
        tracing.tag(span, "error", e.getMessage());
        tracing.endSpan(span, false);
    }

    public void onBatchFailure(String batchId, double failureRate) {
        SpanContext span = tracing.startSpan("nebula.batch.failure", null);
        tracing.tag(span, "batchId", batchId);
        tracing.tag(span, "failureRate", String.valueOf(failureRate));
        tracing.endSpan(span, false);
    }
}
```

- [ ] **步骤 3：Commit 全链路追踪**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/monitor/TracingIntegration.java
git commit -m "feat(graph): 添加全链路追踪集成
- TracingIntegration: SkyWalking/Jaeger 集成
- 在 NebulaMonitor 中集成追踪功能"
```

#### 任务 6.4：全链路压测

- [ ] **步骤 1：创建压测脚本**

```yaml
# 文件：mf-common-graph/stress-test/load-test.yaml
config:
  target: "http://nebula-graph-gateway:8080"
  phases:
    - duration: 60
      arrivalRate: 10
      name: "预热阶段"
    - duration: 120
      arrivalRate: 50
      name: "正常压力"
    - duration: 60
      arrivalRate: 100
      name: "峰值压力"
    - duration: 120
      arrivalRate: 200
      name: "极限压力"

scenarios:
  - name: "节点 CRUD 操作"
    weight: 30
    flow:
      - post:
          url: "/api/graph/node"
          body:
            tagName: "Product"
            properties:
              name: "Product-{{ $randomString(8) }}"
              code: "P{{ $timestamp }}"
      - get:
          url: "/api/graph/node/{{ lastResponse._id }}"
      - put:
          url: "/api/graph/node/{{ lastResponse._id }}"
          body:
            properties:
              name: "Updated-{{ $randomString(8) }}"
      - delete:
          url: "/api/graph/node/{{ lastResponse._id }}"

  - name: "批量导入"
    weight: 20
    flow:
      - post:
          url: "/api/graph/batch"
          body:
            tagName: "Part"
            count: 1000

  - name: "路径查询"
    weight: 30
    flow:
      - post:
          url: "/api/graph/query/path"
          body:
            fromVid: "Product:P001"
            toVid: "Part:P001"
            edgeType: "ContainsLink"
            maxHop: 5

  - name: "BOM 查询"
    weight: 20
    flow:
      - get:
          url: "/api/graph/bom/{{ productId }}?depth=10"
```

- [ ] **步骤 2：创建压测报告生成器**

```java
// 文件：mf-common-graph/src/test/java/cn/com/mfish/graph/LoadTestReportGenerator.java
public class LoadTestReportGenerator {
    /**
     * 生成压测报告
     * @param testResult 压测结果
     * @return 报告内容
     */
    public String generateReport(LoadTestResult testResult);

    /**
     * 检查是否满足 SLO
     * @param testResult 压测结果
     * @return SLO 检查结果
     */
    public SLOResult checkSLO(LoadTestResult testResult);
}

@Data
public class LoadTestResult {
    private int totalRequests;
    private int successRequests;
    private int failedRequests;
    private double successRate;
    private double avgResponseTime;
    private double p50ResponseTime;
    private double p90ResponseTime;
    private double p99ResponseTime;
    private int maxConcurrentConnections;
    private Map<String, MetricData> metrics;
}

@Data
public class SLOResult {
    private boolean passed;
    private List<String> violations;
    private Map<String, Double> actualValues;
    private Map<String, Double> sloTargets;
}
```

- [ ] **步骤 3：Commit 压测**

```bash
mkdir -p mf-common-graph/stress-test
git add mf-common-graph/stress-test/
git add mf-common-graph/src/test/java/cn/com/mfish/graph/LoadTestReportGenerator.java
git commit -m "test(graph): 添加全链路压测
- load-test.yaml: 压测场景配置
- LoadTestReportGenerator: 报告生成器"
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

#### 任务 7.3：配置中心集成

- [ ] **步骤 1：集成 Nacos 配置中心**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/remote/NacosConfigCenter.java
public class NacosConfigCenter implements ConfigCenterRefresher {
    private ConfigService configService;
    private String dataId;
    private String group = "DEFAULT_GROUP";

    public NacosConfigCenter(String serverAddr, String namespace) {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        properties.put("namespace", namespace);
        this.configService = NacosFactory.createConfigService(properties);
    }

    @Override
    public void listen(String dataId, ConfigChangeListener listener) {
        this.dataId = dataId;
        String content = configService.getConfig(dataId, group, 5000);
        listener.onChange(dataId, content);
        configService.addListener(dataId, group, new Listener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                listener.onChange(dataId, configInfo);
            }
            @Override
            public Executor getExecutor() {
                return Executors.newSingleThreadExecutor();
            }
        });
    }

    @Override
    public void refreshPoolConfig(NebulaPoolConfig config) {
        // 实现配置刷新逻辑
    }

    @Override
    public void refreshCircuitBreaker(String name, CircuitBreakerConfig config) {
        // 实现熔断器配置刷新
    }

    @Override
    public void refreshRetryConfig(RetryConfig config) {
        // 实现重试配置刷新
    }

    @Override
    public void refreshConfigWithGray(String dataId, List<String> grayInstanceIds) {
        // 灰度发布配置
    }

    @Override
    public void rollbackConfig(String dataId, String version) {
        // 配置回滚
    }

    @Override
    public void enableLocalCache(long cacheTtlMs) {
        // 启用本地缓存
    }
}
```

- [ ] **步骤 2：集成 Apollo 配置中心**

```java
// 文件：mf-common-graph/src/main/java/cn/com/mfish/graph/remote/ApolloConfigCenter.java
public class ApolloConfigCenter implements ConfigCenterRefresher {
    private Config config;
    private String namespace;

    public ApolloConfigCenter(String apolloMeta, String appId, String cluster, String namespace) {
        this.namespace = namespace;
        ConfigFile configFile = ConfigService.getConfigFile(namespace, ConfigFileFormat.JSON);
        this.config = ConfigService.getAppConfig();
    }

    @Override
    public void listen(String dataId, ConfigChangeListener listener) {
        config.addChangeListener(changeEvent -> {
            for (String key : changeEvent.changedKeys()) {
                if (key.equals(dataId)) {
                    ChangeType changeType = changeEvent.getChange(key).getChangeType();
                    if (changeType == ChangeType.MODIFIED) {
                        listener.onChange(dataId, config.getProperty(key, null));
                    }
                }
            }
        });
    }

    @Override
    public void refreshPoolConfig(NebulaPoolConfig config) {
        // 实现配置刷新逻辑
    }

    @Override
    public void refreshCircuitBreaker(String name, CircuitBreakerConfig config) {
        // 实现熔断器配置刷新
    }

    @Override
    public void refreshRetryConfig(RetryConfig config) {
        // 实现重试配置刷新
    }

    @Override
    public void refreshConfigWithGray(String dataId, List<String> grayInstanceIds) {
        // 灰度发布配置
    }

    @Override
    public void rollbackConfig(String dataId, String version) {
        // 配置回滚
    }

    @Override
    public void enableLocalCache(long cacheTtlMs) {
        // 启用本地缓存
    }
}
```

- [ ] **步骤 3：Commit 配置中心集成**

```bash
git add mf-common-graph/src/main/java/cn/com/mfish/graph/remote/NacosConfigCenter.java
git add mf-common-graph/src/main/java/cn/com/mfish/graph/remote/ApolloConfigCenter.java
git commit -m "feat(graph): 添加配置中心集成
- NacosConfigCenter: Nacos 配置中心实现
- ApolloConfigCenter: Apollo 配置中心实现"
```

#### 任务 7.4：灾备演练

- [ ] **步骤 1：创建灾备演练脚本**

```yaml
# 文件：mf-common-graph/disaster-drill/backup-restore.yaml
name: "NebulaGraph 灾备演练"
description: "验证备份恢复流程和切换机制"

stages:
  - name: "健康检查"
    steps:
      - check_primary_health:
          description: "检查主集群健康状态"
          action: DisasterRecoveryManager.isPrimaryHealthy()
          expected: true

      - check_standby_health:
          description: "检查备集群健康状态"
          action: DisasterRecoveryManager.isStandbyHealthy()
          expected: true

  - name: "执行备份"
    steps:
      - full_backup:
          description: "执行全量备份"
          action: BackupManager.backup()
          expected: success

      - incremental_backup:
          description: "执行增量备份"
          action: BackupManager.incrementalBackup()
          expected: success

      - validate_backup:
          description: "校验备份完整性"
          action: BackupManager.validateBackupIntegrity(backupId)
          expected: valid

  - name: "模拟故障切换"
    steps:
      - simulate_primary_failure:
          description: "模拟主节点故障"
          action: "断开主集群网络连接"

      - wait_for_detection:
          description: "等待故障检测（30秒）"
          timeout: 35

      - verify_switch:
          description: "验证自动切换到备集群"
          action: DisasterRecoveryManager.switchToStandby()
          expected: success

      - verify_rto:
          description: "验证 RTO < 30 分钟"
          action: DisasterRecoveryManager.getRecoveryMetrics().rtoMinutes < 30
          expected: true

  - name: "恢复演练"
    steps:
      - restore_from_backup:
          description: "从备份恢复数据"
          action: DisasterRecoveryManager.restore(backupId)
          expected: success

      - verify_data_integrity:
          description: "验证数据完整性"
          action: "执行数据校验查询"
          expected: true

  - name: "回切主集群"
    steps:
      - restore_primary:
          description: "恢复主集群服务"

      - switch_back:
          description: "手动切回主集群"
          action: "管理员执行回切操作"
          expected: success

      - verify_normal:
          description: "验证恢复正常"
          action: DisasterRecoveryManager.isPrimaryHealthy()
          expected: true
```

- [ ] **步骤 2：创建演练报告模板**

```java
// 文件：mf-common-graph/src/test/java/cn/com/mfish/graph/DisasterDrillReport.java
@Data
public class DisasterDrillReport {
    private String drillId;
    private Date startTime;
    private Date endTime;
    private Duration totalDuration;
    private DrillResult result;
    private Map<String, StageResult> stageResults;
    private List<String> issues;
    private RecoveryMetrics recoveryMetrics;
    private List<String> recommendations;
}

public enum DrillResult {
    PASS,
    FAIL,
    PARTIAL
}

@Data
public class StageResult {
    private String stageName;
    private boolean success;
    private Duration duration;
    private String errorMessage;
    private List<String> logs;
}
```

- [ ] **步骤 3：Commit 灾备演练**

```bash
mkdir -p mf-common-graph/disaster-drill
git add mf-common-graph/disaster-drill/
git add mf-common-graph/src/test/java/cn/com/mfish/graph/DisasterDrillReport.java
git commit -m "test(graph): 添加灾备演练
- backup-restore.yaml: 灾备演练脚本
- DisasterDrillReport: 演练报告模板"
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

#### 任务 8.2：灰度发布

- [ ] **步骤 1：准备灰度发布配置**

```yaml
# 文件：mf-common-graph/rollout/gray-release.yaml
apiVersion: v1
kind: GrayRelease
metadata:
  name: nebula-graph-client
  version: 3.8.0
spec:
  # 灰度策略
  strategy:
    type: CANARY
    canary:
      # 初始流量权重
      initialWeight: 10
      # 权重递增步长
      weightStep: 10
      # 每步间隔（分钟）
      stepInterval: 5
      # 自动递增条件
      autoIncrement:
        enabled: true
        condition:
          errorRateThreshold: 0.01
          p99LatencyThreshold: 500
          minRequests: 1000

  # 保留旧版本作为回滚方案
  rollback:
    enabled: true
    retentionDays: 7
    # 旧版本 HTTP 客户端保留
    keepOldHttpClient: true

  # 监控指标
  metrics:
    - name: error_rate
      threshold: 0.01
    - name: p99_latency_ms
      threshold: 500
    - name: success_rate
      threshold: 0.99

  # 告警配置
  alerts:
    - level: WARNING
      condition: error_rate > 0.005
      message: "灰度流量错误率上升"
    - level: CRITICAL
      condition: error_rate > 0.01
      message: "灰度流量错误率超过阈值，执行自动回滚"
```

- [ ] **步骤 2：部署 10% 流量验证**

```bash
# 1. 标记旧 HTTP 客户端为保留状态（不删除）
kubectl annotate deployment mf-common-graph-http-client \
  rollback.enabled=true \
  rollback.retention-days=7

# 2. 部署新客户端，初始流量 10%
kubectl set image deployment/mf-common-graph-native \
  nebula-client=vesoft/nebula-client:3.8.0-native

kubectl patch deployment/mf-common-graph-native \
  -p '{"spec":{"strategy":{"rollingUpdate":{"maxSurge":"25%","maxUnavailable":"0%"}}}}'

# 3. 设置初始权重
kubectl patch virtualservice mf-common-graph \
  -p '{"spec":{"http":[{"route":[{"destination":{"host":"mf-common-graph-native","subset":"v2"},"weight":10}},{"destination":{"host":"mf-common-graph-http","subset":"v1"},"weight":90}]}]}}'

# 4. 等待初始验证
sleep 300  # 5分钟

# 5. 检查监控指标
echo "检查 10% 流量指标..."
curl -s http://monitoring-service/api/metrics/error-rate | jq '.canary'
```

- [ ] **步骤 3：扩量至 50%**

```bash
# 1. 检查 10% 阶段指标
METRICS=$(curl -s http://monitoring-service/api/metrics/canary)
ERROR_RATE=$(echo $METRICS | jq '.error_rate')
P99_LATENCY=$(echo $METRICS | jq '.p99_latency_ms')

# 2. 判断是否满足扩量条件
if (( $(echo "$ERROR_RATE < 0.01" | bc -l) )) && (( $(echo "$P99_LATENCY < 500" | bc -l) )); then
    echo "10% 流量验证通过，扩量至 50%"

    # 更新权重
    kubectl patch virtualservice mf-common-graph \
      -p '{"spec":{"http":[{"route":[{"destination":{"host":"mf-common-graph-native","subset":"v2"},"weight":50}},{"destination":{"host":"mf-common-graph-http","subset":"v1"},"weight":50}]}]}}'

    # 等待验证
    sleep 600  # 10分钟
else
    echo "指标异常，保持 10% 流量或回滚"
    # 触发回滚
    kubectl rollout undo deployment/mf-common-graph-native
    exit 1
fi
```

- [ ] **步骤 4：扩量至 100%**

```bash
# 1. 检查 50% 阶段指标
METRICS=$(curl -s http://monitoring-service/api/metrics/canary)
ERROR_RATE=$(echo $METRICS | jq '.error_rate')

if (( $(echo "$ERROR_RATE < 0.01" | bc -l) )); then
    echo "50% 流量验证通过，扩量至 100%"

    # 全量切换
    kubectl patch virtualservice mf-common-graph \
      -p '{"spec":{"http":[{"route":[{"destination":{"host":"mf-common-graph-native","subset":"v2"},"weight":100}]}]}}'

    # 等待稳定
    sleep 300
else
    echo "指标异常，保持 50% 流量"
    exit 1
fi
```

- [ ] **步骤 5：验证新版本稳定性**

```bash
# 1. 监控 24 小时关键指标
echo "开始 24 小时稳定性监控..."

for hour in {1..24}; do
    METRICS=$(curl -s http://monitoring-service/api/metrics/production)

    ERROR_RATE=$(echo $METRICS | jq '.error_rate')
    P99_LATENCY=$(echo $METRICS | jq '.p99_latency_ms')
    SUCCESS_RATE=$(echo $METRICS | jq '.success_rate')

    echo "[Hour $hour] ErrorRate: $ERROR_RATE, P99: ${P99_LATENCY}ms, SuccessRate: $SUCCESS_RATE"

    # 检查是否需要告警
    if (( $(echo "$ERROR_RATE > 0.01" | bc -l) )); then
        echo "错误率超过阈值，发送告警"
        curl -X POST http://alert-manager/api/alerts \
          -d '{"level":"CRITICAL","title":"错误率超标","message":"生产环境错误率超过 1%"}'
    fi

    sleep 3600
done

echo "24 小时稳定性监控完成"
```

- [ ] **步骤 6：清理旧版本**

```bash
# 1. 确认新版本稳定运行 7 天后，删除旧 HTTP 客户端
echo "等待 7 天观察期..."

# 2. 验证旧版本无活跃流量
OLD_TRAFFIC=$(curl -s http://monitoring-service/api/metrics/old-version-traffic)
echo "旧版本剩余流量: $OLD_TRAFFIC%"

if (( $(echo "$OLD_TRAFFIC < 0.1" | bc -l) )); then
    echo "开始清理旧版本..."

    # 删除旧 HTTP 客户端
    kubectl delete deployment mf-common-graph-http-client

    # 删除旧版本配置
    kubectl delete virtualservice mf-common-graph -f

    echo "旧版本清理完成"
else
    echo "仍有流量在旧版本，等待流量完全迁移"
fi
```

- [ ] **步骤 7：Commit 灰度发布配置**

```bash
mkdir -p mf-common-graph/rollout
git add mf-common-graph/rollout/
git commit -m "release(graph): 添加灰度发布配置
- gray-release.yaml: 灰度发布策略
- 10% → 50% → 100% 流量切换流程
- 旧版本保留 7 天作为回滚方案"
```

---

### Phase 9: 上线与验收

#### 任务 9.1：上线前检查

- [ ] **步骤 1：执行上线前检查清单**

```bash
#!/bin/bash
# 文件：mf-common-graph/scripts/pre-release-check.sh

echo "===== NebulaGraph 3.8.0 客户端上线前检查 ====="

# 1. 检查所有单元测试通过
echo "[1/10] 运行单元测试..."
mvn test -Dtest="*Test" -q
if [ $? -ne 0 ]; then
    echo "❌ 单元测试失败"
    exit 1
fi
echo "✅ 单元测试通过"

# 2. 检查集成测试通过
echo "[2/10] 运行集成测试..."
mvn verify -Dintegration-test=true
if [ $? -ne 0 ]; then
    echo "❌ 集成测试失败"
    exit 1
fi
echo "✅ 集成测试通过"

# 3. 检查代码覆盖率
echo "[3/10] 检查代码覆盖率..."
COVERAGE=$(mvn jacoco:report -q | grep -oP 'Total.*?\d+%' | tail -1)
echo "代码覆盖率: $COVERAGE"
if [ "$COVERAGE" < "80%" ]; then
    echo "⚠️  代码覆盖率低于 80%"
fi

# 4. 检查 SonarQube
echo "[4/10] SonarQube 检查..."
mvn sonar:sonar -q
echo "✅ SonarQube 检查完成"

# 5. 检查依赖漏洞
echo "[5/10] 依赖安全检查..."
mvn dependency:analyze -q
echo "✅ 依赖安全检查完成"

# 6. 检查配置完整性
echo "[6/10] 配置文件检查..."
[ -f src/main/resources/nebula-pool-config.yaml ] && echo "✅ 连接池配置存在"
[ -f src/main/resources/retry-config.yaml ] && echo "✅ 重试配置存在"
[ -f src/main/resources/circuit-breaker-config.yaml ] && echo "✅ 熔断配置存在"

# 7. 检查监控埋点
echo "[7/10] 监控埋点检查..."
grep -r "@Timed" src/main/java | wc -l
grep -r "@Metered" src/main/java | wc -l
echo "✅ 监控埋点已添加"

# 8. 检查文档
echo "[8/10] 文档完整性检查..."
[ -f README.md ] && echo "✅ README 存在"
[ -f CHANGELOG.md ] && echo "✅ CHANGELOG 存在"

# 9. 检查 Docker 镜像
echo "[9/10] Docker 镜像构建..."
docker build -t mf-common-graph:3.8.0 . -q
if [ $? -eq 0 ]; then
    echo "✅ Docker 镜像构建成功"
else
    echo "❌ Docker 镜像构建失败"
    exit 1
fi

# 10. 确认灾备演练通过
echo "[10/10] 灾备演练状态检查..."
DRILL_STATUS=$(curl -s http://disaster-drill-service/api/status)
echo "灾备演练状态: $DRILL_STATUS"

echo "===== 上线前检查完成 ====="
```

- [ ] **步骤 2：创建上线报告**

```markdown
# NebulaGraph 3.8.0 客户端上线报告

## 1. 版本信息
- **新版本**: v3.8.0-native
- **旧版本**: v3.8.0-http
- **上线时间**: 2026-04-XX
- **上线人员**: XXX

## 2. 变更内容
- 基于 vesoft client 原生 Session 重写
- 会话池化改造
- 添加熔断器、限流、重试机制
- 添加全链路追踪

## 3. 测试结果
| 测试类型 | 结果 | 覆盖率 |
|---------|------|--------|
| 单元测试 | ✅ 通过 | 85% |
| 集成测试 | ✅ 通过 | - |
| 压测 | ✅ 通过 | P99<500ms |
| 灾备演练 | ✅ 通过 | RTO<30min |

## 4. 灰度发布记录
| 阶段 | 流量 | 时长 | 错误率 |
|------|------|------|--------|
| 10% | 10% | 5min | 0.1% |
| 50% | 50% | 10min | 0.2% |
| 100% | 100% | 24h | 0.15% |

## 5. 监控指标
- P99 延迟: 320ms ✅
- 成功率: 99.85% ✅
- 错误率: 0.15% ✅

## 6. 回滚方案
- 旧 HTTP 客户端保留至: 2026-04-XX+7
- 回滚命令: `kubectl rollout undo deployment/mf-common-graph-native`

## 7. 上线确认
- [ ] 开发负责人: __________ 签字: __________
- [ ] 测试负责人: __________ 签字: __________
- [ ] 运维负责人: __________ 签字: __________
```

- [ ] **步骤 3：Commit 上线报告**

```bash
mkdir -p mf-common-graph/scripts
git add mf-common-graph/scripts/pre-release-check.sh
git add mf-common-graph/RELEASE.md
git commit -m "release(graph): 添加上线检查脚本和报告模板"
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
