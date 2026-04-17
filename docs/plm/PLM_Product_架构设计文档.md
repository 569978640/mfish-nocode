# PLM 模块 Product 架构设计文档

## 1. 概述

### 1.1 文档目的

本文档详细描述 PLM（Product Lifecycle Management，产品生命周期管理）模块中 Product（产品）相关的架构设计，包括核心组件、数据模型、服务层设计、API 接口以及图同步机制。

### 1.2 适用范围

- PLM 模块 Product 实体的设计与实现
- 基于 PlmBaseService 的服务层架构参考
- 图数据库同步机制的理解

### 1.3 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot + MyBatis-Plus |
| 消息队列 | Apache RocketMQ |
| 数据库 | PostgreSQL |
| API 文档 | Swagger/OpenAPI 3.0 |
| 权限控制 | OAuth2 + 权限注解 |
| 前端 | Vue3 + TypeScript |

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端视图层 (Vue3)                          │
│              mfish-nocode-view/src/api/plm/Product.ts            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ HTTP/REST
┌─────────────────────────────────────────────────────────────────┐
│                      Controller 层                              │
│           ProductController (REST API: /plm/product)            │
│                    权限注解: @RequiresPermissions                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Service 层                                 │
│  ProductService ←───────── 继承 ──────────► PlmBaseService<T>    │
│  ProductServiceImpl     (通用 CRUD + 图同步)      ↑              │
│                                                    │              │
│                   ┌────────────────────────────────┘              │
│                   ▼                                                │
│         PlmGraphSyncClient (图同步客户端)                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Mapper 层                                  │
│      ProductMapper (MyBatis-Plus BaseMapper<Product>)             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       数据库层                                   │
│                    PostgreSQL: product 表                        │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 模块目录结构

```
mfish-nocode/
├── mf-api/                          # API 模块
│   └── mf-plm-api/
│       └── src/main/java/
│           └── cn/com/mfish/plm/api/
│               ├── remote/RemotePlmService.java     # 远程服务调用
│               └── fallback/RemotePlmFallback.java   # 降级处理
│
├── mf-business/                     # 业务模块
│   └── mf-plm/
│       └── src/main/java/cn/com/mfish/plm/
│           └── base/bean/container/
│               ├── controller/
│               │   └── ProductController.java        # 产品控制器
│               ├── service/
│               │   ├── ProductService.java           # 产品服务接口
│               │   └── impl/
│               │       └── ProductServiceImpl.java    # 产品服务实现
│               └── req/
│                   └── ReqProduct.java               # 查询请求对象
│
├── mf-common/                       # 公共模块
│   └── mf-common-plm/
│       └── src/main/java/cn/com/mfish/plm/
│           └── base/
│               ├── bean/
│               │   └── container/
│               │       ├── entity/
│               │       │   └── Product.java          # 产品实体
│               │       └── mapper/
│               │           ├── ProductMapper.java    # MyBatis Mapper
│               │           └── xml/
│               │               └── ProductMapper.xml  # Mapper XML
│               ├── service/
│               │   ├── PlmBaseService.java           # 通用服务接口
│               │   └── impl/
│               │       ├── PlmBaseServiceImpl.java   # 通用服务实现
│               │       ├── PlmGraphSyncClient.java   # 图同步接口
│               │       └── PlmGraphSyncClientImpl.java # 图同步实现
│               └── mapper/
│                   ├── container/ContainsLinkMapper.java
│                   ├── doc/
│                   │   ├── DocumentMapper.java
│                   │   ├── DocumentMasterMapper.java
│                   │   └── DocVersionLinkMapper.java
│                   └── part/
│                       ├── PartMapper.java
│                       ├── PartMasterMapper.java
│                       └── PartVersionLinkMapper.java
│
├── mf-start/                        # 启动模块
│   └── mf-start-plm/
│       └── src/main/java/
│           └── cn/com/mfish/plm/
│               └── MfPlmApplication.java              # 启动类
│
└── db/
    └── mf_plm_init.sql                              # 数据库初始化脚本
```

---

## 3. 数据模型

### 3.1 实体类图

```
┌─────────────────────────────────────┐
│          BaseEntity<T>              │
│  (通用审计字段)                      │
├─────────────────────────────────────┤
│ - id: T                             │
│ - createBy: String                  │
│ - createTime: LocalDateTime        │
│ - updateBy: String                  │
│ - updateTime: LocalDateTime        │
└─────────────────────────────────────┘
                △
                │ 继承
┌─────────────────────────────────────┐
│            Product                  │
│  (产品实体)                          │
├─────────────────────────────────────┤
│ - id: String (主键, UUID)           │
│ - type: String (类型)               │
│ - name: String (产品名称)            │
└─────────────────────────────────────┘
```

### 3.2 Product 实体定义

**文件路径**: `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/bean/container/entity/Product.java`

```java
@Data
@TableName("product")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "product对象 产品库")
public class Product extends BaseEntity<String> {
    @ExcelProperty("唯一ID")
    @Schema(description = "唯一ID")
    @TableId(type = IdType.ASSIGN_UUID)
    @Accessors(chain = true)
    private String id;

    @ExcelProperty("类型")
    @Schema(description = "类型")
    private String type;

    @ExcelProperty("产品名称")
    @Schema(description = "产品名称")
    private String name;
}
```

### 3.3 数据库表结构

**文件路径**: `mfish-nocode/db/mf_plm_init.sql`

```sql
-- 产品表
CREATE TABLE IF NOT EXISTS product (
    id VARCHAR(64) PRIMARY KEY,                    -- 产品ID (UUID)
    type VARCHAR(100) DEFAULT 'Product',          -- 类型
    name VARCHAR(255),                            -- 产品名称
    create_by VARCHAR(64),                         -- 创建人
    create_time TIMESTAMP,                         -- 创建时间
    update_by VARCHAR(64),                         -- 更新人
    update_time TIMESTAMP                          -- 更新时间
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_product_create_time ON product(create_time);

-- 注释
COMMENT ON TABLE product IS '产品表';
COMMENT ON COLUMN product.id IS '产品ID';
COMMENT ON COLUMN product.type IS '类型';
COMMENT ON COLUMN product.name IS '产品名称';
```

### 3.4 请求对象

**ReqProduct.java** - 查询请求参数

```java
@Data
@Accessors(chain = true)
@Schema(description = "产品库请求参数")
public class ReqProduct {
    @Schema(description = "产品ID")
    private String id;

    @Schema(description = "产品名称")
    private String name;

    @Schema(description = "创建人")
    private String create_by;
}
```

---

## 4. 服务层设计

### 4.1 设计模式：模板方法模式

```
                    ┌──────────────────────────────┐
                    │      PlmBaseService<T>       │
                    │      (通用服务接口)            │
                    ├──────────────────────────────┤
                    │ + queryById(id)              │
                    │ + insert(entity)             │
                    │ + updateById(entity)         │
                    │ + deleteById(id)             │
                    │ + ...                        │
                    └──────────────────────────────┘
                              △
                              │ 继承
                    ┌──────────────────────────────┐
                    │     ProductService           │
                    │     (特定业务接口)            │
                    ├──────────────────────────────┤
                    │ + queryPageList(req, page)   │
                    │ + export(req, page)          │
                    └──────────────────────────────┘
```

### 4.2 PlmBaseService 通用服务接口

**文件路径**: `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/PlmBaseService.java`

该接口继承 MyBatis-Plus 的 `IService<T>`，并对所有方法返回类型进行了统一封装：

| 方法类别 | 说明 |
|----------|------|
| 查询方法 | 返回 `Result<T>` 或 `Result<List<T>>` |
| 插入方法 | 返回 `Result<T>` + 自动图同步 |
| 更新方法 | 返回 `Result<T>` + 自动图同步 |
| 删除方法 | 返回 `Result<Boolean>` + 自动图同步 |

**核心方法签名**:

```java
public interface PlmBaseService<T> extends IService<T> {
    // 获取节点类型，用于图同步
    String getNodeType();

    // 查询
    Result<T> queryById(Serializable id);
    Result<List<T>> queryByIds(Collection<? extends Serializable> idList);
    Result<PageResult<T>> queryPageList(ReqPage reqPage);
    Result<PageResult<T>> queryPageList(ReqPage reqPage, LambdaQueryWrapper<T> queryWrapper);

    // 插入
    Result<T> insert(T entity);
    Result<Boolean> insertBatch(Collection<T> entityList);

    // 更新
    Result<T> updateByIdReturn(T entity);

    // 删除
    Result<Boolean> deleteById(Serializable id);
    Result<Boolean> deleteByIds(Collection<? extends Serializable> idList);
}
```

### 4.3 PlmBaseServiceImpl 通用服务实现

**文件路径**: `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/impl/PlmBaseServiceImpl.java`

关键设计：
- 继承 MyBatis-Plus 的 `ServiceImpl<M, T>`
- 注入 `PlmGraphSyncClient` 实现图同步
- 所有写操作自动触发图同步事件

**自动图同步逻辑**:

```java
@Override
public Result<T> insert(T entity) {
    if (super.save(entity)) {
        plmGraphSyncClient.sendGraphSyncEvent(entity, getNodeType(), "CREATE");
        log.info("{}添加成功并发送图同步事件", getNodeType());
        return Result.ok(entity, "添加成功!");
    }
    return Result.fail(entity, "添加失败!");
}

@Override
public Result<T> updateByIdReturn(T entity) {
    if (super.updateById(entity)) {
        plmGraphSyncClient.sendGraphSyncEvent(entity, getNodeType(), "UPDATE");
        log.info("{}更新成功并发送图同步事件", getNodeType());
        return Result.ok(entity, "更新成功!");
    }
    return Result.fail(entity, "更新失败!");
}

@Override
public Result<Boolean> deleteById(Serializable id) {
    T entity = getById(id);
    if (super.removeById(id)) {
        if (entity != null) {
            plmGraphSyncClient.sendGraphSyncEvent(entity, getNodeType(), "DELETE");
            log.info("{}删除成功并发送图同步事件: id={}", getNodeType(), id);
        }
        return Result.ok(true, "删除成功!");
    }
    return Result.fail(false, "删除失败!");
}
```

### 4.4 ProductService 业务服务接口

**文件路径**: `mf-business/mf-plm/src/main/java/cn/com/mfish/plm/base/bean/container/service/ProductService.java`

```java
public interface ProductService extends PlmBaseService<Product> {
    /**
     * 分页列表查询
     */
    Result<PageResult<Product>> queryPageList(ReqProduct reqProduct, ReqPage reqPage);

    /**
     * 导出
     */
    void export(ReqProduct reqProduct, ReqPage reqPage) throws IOException;
}
```

### 4.5 ProductServiceImpl 业务服务实现

**文件路径**: `mf-business/mf-plm/src/main/java/cn/com/mfish/plm/base/bean/container/service/impl/ProductServiceImpl.java`

```java
@Slf4j
@Service
public class ProductServiceImpl extends PlmBaseServiceImpl<Product, ProductMapper>
        implements ProductService {

    @Override
    public String getNodeType() {
        return "Product";
    }

    @Override
    public Result<PageResult<Product>> queryPageList(ReqProduct reqProduct, ReqPage reqPage) {
        PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(!StringUtils.isEmpty(reqProduct.getId()), Product::getId, reqProduct.getId())
                .like(!StringUtils.isEmpty(reqProduct.getName()), Product::getName, reqProduct.getName())
                .eq(!StringUtils.isEmpty(reqProduct.getCreate_by()), Product::getCreateBy, reqProduct.getCreate_by());
        List<Product> list = list(wrapper);
        return Result.ok(new PageResult<>(list), "产品库-查询成功!");
    }

    @Override
    public void export(ReqProduct reqProduct, ReqPage reqPage) throws IOException {
        PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(!StringUtils.isEmpty(reqProduct.getId()), Product::getId, reqProduct.getId())
                .like(!StringUtils.isEmpty(reqProduct.getName()), Product::getName, reqProduct.getName());
        List<Product> list = list(wrapper);
        ExcelUtils.write("产品库_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), list);
    }
}
```

---

## 5. Controller 层设计

### 5.1 ProductController

**文件路径**: `mf-business/mf-plm/src/main/java/cn/com/mfish/plm/base/bean/container/controller/ProductController.java`

```java
@Slf4j
@Tag(name = "产品库")
@RestController
@RequestMapping("/product")
public class ProductController {
    @Resource
    private ProductService productService;

    /**
     * 分页列表查询
     */
    @Operation(summary = "产品库-分页列表查询")
    @GetMapping
    @RequiresPermissions("plm:product:query")
    public Result<PageResult<Product>> queryPageList(ReqProduct reqProduct, ReqPage reqPage) {
        return productService.queryPageList(reqProduct, reqPage);
    }

    /**
     * 添加
     */
    @Log(title = "产品库-添加", operateType = OperateType.INSERT)
    @Operation(summary = "产品库-添加")
    @PostMapping
    @RequiresPermissions("plm:product:insert")
    public Result<Product> add(@RequestBody Product product) {
        return productService.insert(product);
    }

    /**
     * 编辑
     */
    @Log(title = "产品库-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "产品库-编辑")
    @PutMapping
    @RequiresPermissions("plm:product:update")
    public Result<Product> edit(@RequestBody Product product) {
        return productService.updateByIdReturn(product);
    }

    /**
     * 通过id删除
     */
    @Log(title = "产品库-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "产品库-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("plm:product:delete")
    public Result<Boolean> delete(@PathVariable String id) {
        return productService.deleteById(id);
    }

    /**
     * 批量删除
     */
    @Log(title = "产品库-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "产品库-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("plm:product:delete")
    public Result<Boolean> deleteBatch(@PathVariable String ids) {
        return productService.deleteByIds(List.of(ids.split(",")));
    }

    /**
     * 通过id查询
     */
    @Operation(summary = "产品库-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("plm:product:query")
    public Result<Product> queryById(@PathVariable String id) {
        return productService.queryById(id);
    }

    /**
     * 导出
     */
    @Operation(summary = "导出产品库")
    @GetMapping("/export")
    @RequiresPermissions("plm:product:export")
    public void export(ReqProduct reqProduct, ReqPage reqPage) throws IOException {
        productService.export(reqProduct, reqPage);
    }
}
```

### 5.2 API 接口汇总

| 方法 | 路径 | 权限 | 功能说明 |
|------|------|------|----------|
| GET | `/plm/product` | `plm:product:query` | 分页查询产品列表 |
| POST | `/plm/product` | `plm:product:insert` | 新增产品 |
| PUT | `/plm/product` | `plm:product:update` | 修改产品 |
| DELETE | `/plm/product/{id}` | `plm:product:delete` | 删除单个产品 |
| DELETE | `/plm/product/batch/{ids}` | `plm:product:delete` | 批量删除产品 |
| GET | `/plm/product/{id}` | `plm:product:query` | 根据ID查询产品 |
| GET | `/plm/product/export` | `plm:product:export` | 导出产品Excel |

---

## 6. 图同步机制

### 6.1 架构概述

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│   PLM Service   │      │   RocketMQ      │      │   Graph 模块    │
│                 │ ───► │                 │ ───► │                 │
│ (写操作触发)     │      │  plm-graph-sync │      │ (消费同步图)    │
└─────────────────┘      └─────────────────┘      └─────────────────┘
```

### 6.2 PlmGraphSyncClient 接口

**文件路径**: `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/PlmGraphSyncClient.java`

```java
public interface PlmGraphSyncClient {
    /**
     * 发送图同步事件
     */
    <T> void sendGraphSyncEvent(T entity, String nodeType, String eventType);

    /**
     * 批量发送图同步事件
     */
    <T> void sendGraphSyncEventBatch(List<T> entities, String nodeType, String eventType);
}
```

### 6.3 PlmGraphSyncClientImpl 实现

**文件路径**: `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/impl/PlmGraphSyncClientImpl.java`

```java
@Slf4j
@Component
public class PlmGraphSyncClientImpl implements PlmGraphSyncClient {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.producer.topic:plm-graph-sync}")
    private String topic;

    @Override
    public <T> void sendGraphSyncEvent(T entity, String nodeType, String eventType) {
        if (entity == null) {
            return;
        }
        GraphNode node = convertToGraphNode(entity, nodeType);
        GraphSyncEvent event = new GraphSyncEvent();
        event.setEventType(eventType);
        event.setNodes(List.of(node));
        sendEvent(event);
    }

    @Override
    public <T> void sendGraphSyncEventBatch(List<T> entities, String nodeType, String eventType) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        List<GraphNode> nodes = new ArrayList<>();
        for (T entity : entities) {
            nodes.add(convertToGraphNode(entity, nodeType));
        }
        GraphSyncEvent event = new GraphSyncEvent();
        event.setEventType(eventType);
        event.setNodes(nodes);
        sendEvent(event);
    }

    private void sendEvent(GraphSyncEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(System.currentTimeMillis());
        }
        if (event.getSource() == null) {
            event.setSource("mf-plm");
        }
        try {
            rocketMQTemplate.asyncSend(topic, event, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("图同步事件发送成功, eventId={}", event.getEventId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("图同步事件发送失败, eventId={}", event.getEventId(), e);
                }
            });
        } catch (Exception e) {
            log.error("发送图同步事件异常, eventId={}", event.getEventId(), e);
        }
    }

    private <T> GraphNode convertToGraphNode(T entity, String nodeType) {
        GraphNode node = new GraphNode();
        BeanUtils.copyProperties(entity, node);
        node.setType(nodeType);
        return node;
    }
}
```

### 6.4 事件类型

| 事件类型 | 触发场景 |
|----------|----------|
| CREATE | 新增产品 |
| UPDATE | 修改产品 |
| DELETE | 删除产品 |

---

## 7. 前端集成

### 7.1 API 调用层

**文件路径**: `mfish-nocode-view/src/api/plm/Product.ts`

```typescript
import { defHttp } from "@mfish/core/utils/http/axios";
import { Product, ReqProduct, ProductPageModel } from "@/api/plm/model/ProductModel";

enum Api {
  Product = "/plm/product"
}

// 分页列表查询
export const getProductList = (reqProduct?: ReqProduct) => {
  return defHttp.get<ProductPageModel>({ url: Api.Product, params: reqProduct });
};

// 通过id查询
export function getProductById(id: string) {
  return defHttp.get<Product>({ url: `${Api.Product}/${id}` });
}

// 导出
export function exportProduct(reqProduct?: ReqProduct) {
  return defHttp.download({ url: `${Api.Product}/export`, params: reqProduct });
}

// 新增
export function insertProduct(product: Product) {
  return defHttp.post<Product>({ url: Api.Product, params: product }, { successMessageMode: "message" });
}

// 修改
export function updateProduct(product: Product) {
  return defHttp.put<Product>({ url: Api.Product, params: product }, { successMessageMode: "message" });
}

// 删除
export function deleteProduct(id: string) {
  return defHttp.delete<boolean>({ url: `${Api.Product}/${id}` }, { successMessageMode: "message" });
}

// 批量删除
export function deleteBatchProduct(ids: string) {
  return defHttp.delete<boolean>({ url: `${Api.Product}/batch/${ids}` }, { successMessageMode: "message" });
}
```

---

## 8. PLM 模块扩展

### 8.1 实体类型一览

PLM 模块采用统一架构设计，支持以下实体类型：

| 实体类型 | Master 版本 | Version 版本 | 说明 |
|----------|-------------|--------------|------|
| Product | - | - | 产品（无版本） |
| Folder | - | - | 文件夹（无版本） |
| Part | PartMaster | Part | 部件 |
| Document | DocumentMaster | Document | 文档 |

### 8.2 关系类型

| 关系类型 | 说明 |
|----------|------|
| ContainsLink | 包含关系（表达层级结构） |
| PartVersionLink | 部件版本迭代关系 |
| DocVersionLink | 文档版本迭代关系 |

### 8.3 新增实体示例

以新增 `Part` 部件为例，扩展步骤：

1. **创建实体**: `Part.java` 继承 `BaseEntity`
2. **创建 Mapper**: `PartMapper` 继承 `BaseMapper<Part>`
3. **创建服务接口**: `PartService` 继承 `PlmBaseService<Part>`
4. **创建服务实现**: `PartServiceImpl` 继承 `PlmBaseServiceImpl<Part, PartMapper>`，实现 `getNodeType()` 返回 `"Part"`
5. **创建 Controller**: `PartController` 注入 `PartService`

所有 CRUD 和图同步功能由基类自动提供。

---

## 9. 版本信息

| 项目 | 内容 |
|------|------|
| 文档版本 | V1.0 |
| 创建日期 | 2026-04-17 |
| 适用版本 | MFish PLM V2.3.1 |

---

## 10. 附录

### 10.1 相关文件路径

| 文件 | 路径 |
|------|------|
| Product 实体 | `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/bean/container/entity/Product.java` |
| ProductMapper | `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/bean/container/mapper/ProductMapper.java` |
| ProductService | `mf-business/mf-plm/src/main/java/cn/com/mfish/plm/base/bean/container/service/ProductService.java` |
| ProductServiceImpl | `mf-business/mf-plm/src/main/java/cn/com/mfish/plm/base/bean/container/service/impl/ProductServiceImpl.java` |
| ProductController | `mf-business/mf-plm/src/main/java/cn/com/mfish/plm/base/bean/container/controller/ProductController.java` |
| PlmBaseService | `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/PlmBaseService.java` |
| PlmBaseServiceImpl | `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/impl/PlmBaseServiceImpl.java` |
| PlmGraphSyncClient | `mf-common/mf-common-plm/src/main/java/cn/com/mfish/plm/base/service/PlmGraphSyncClient.java` |
| 数据库脚本 | `mfish-nocode/db/mf_plm_init.sql` |

### 10.2 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `rocketmq.producer.topic` | `plm-graph-sync` | 图同步消息 Topic |
