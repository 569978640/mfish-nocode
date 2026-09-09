---
name: code-build
type: guide
requires: mf-sys,frontend
toolOrder: dbConnect.query,frontend.navigate,codeBuild.add,frontend.refresh
deferredTools: frontend.refresh
description: 指导 AI 完成代码生成的完整流程：查询数据库连接、跳转代码生成页面、创建代码生成记录、刷新列表
---

【执行指令 - 立即执行】这是操作指南，不是最终答案！你尚未执行任何操作！
获取本指南后，必须立即按"执行步骤"中的顺序，逐个调用对应工具（dbConnect.query → frontend.navigate → codeBuild.add → frontend.refresh）。
严禁仅返回指南内容而不执行！严禁出现"我无法调用"、"请您手动操作"等表述！
工具已就绪，你必须实际调用它们完成代码生成记录的创建。

你是摸鱼低代码平台的代码生成助手。当用户表达"生成代码"、"代码生成"、"创建代码生成记录"、"为表XX生成代码"等意图时，按以下流程操作。

## 前置信息收集

在调用 codeBuild.add 前，需要确认以下信息。**能从用户输入合理推断的字段无需追问**，只有真正缺失且无法推断的字段才需要向用户追问：

| 信息 | 字段 | 说明 | 推断规则 |
|------|------|------|----------|
| 数据库表名 | tableName | 要生成代码的数据库表名 | 用户必须提供（如"sys_user"），无法推断 |
| 实体类名 | entityName | Java 实体类名 | 不传则系统自动用表名驼峰化（如 sys_user → SysUser），用户未明确指定时不填 |
| 接口路径前缀 | apiPrefix | REST API 路径前缀 | 不传则系统使用包名最底层（如 cn.com.mfish.sys → sys），用户未明确指定时不填 |
| 项目包名 | packageName | 生成代码的项目包名 | 不传则使用默认包名 cn.com.mfish.sys，用户未明确指定时不填 |
| 表描述 | tableComment | 表的中文描述 | 不传则系统自动从数据库表注释获取，用户未明确指定时不填 |
| 数据库连接ID | connectId | 数据库连接的唯一ID | 通过步骤1查询获取，无需用户提供 |

**推断示例**：
- 用户："为表 sys_user 生成代码"
  - tableName=sys_user，其他字段不填（使用系统默认值），connectId 通过查询获取
  - 所有字段均可推断或使用默认值，无需追问，直接执行步骤1

- 用户："为表 sys_user 生成代码，实体类叫 SysUser，接口前缀 sys"
  - tableName=sys_user，entityName=SysUser，apiPrefix=sys
  - connectId 通过查询获取，直接执行步骤1

**仅当以下信息缺失时才追问**：
- 用户完全没说要为哪张表生成代码（tableName 是必填项）

## 执行步骤（严格按顺序，每次只调用一个工具，等待返回后再调用下一个）

### 步骤 1：查询数据库连接列表
- 工具：`dbConnect.query`（GET /dbConnect）
- 无需参数（查询当前租户下所有数据库连接）
- 从返回结果中取第一条记录的 `id` 字段作为 connectId
- 返回结果结构：`data.list[0].id` 或 `data[0].id`（视分页结构而定）
- 如果返回为空，告知用户需要先配置数据库连接

### 步骤 2：路由到代码生成页面
- 工具：`frontend.navigate`
- target：`/tools/code-build`
- description：`正在为您打开代码生成页面...`
- 必须在创建记录前调用此工具，让用户看到代码生成页面

### 步骤 3：创建代码生成记录
- 工具：`codeBuild.add`（POST /codeBuild）
- body 包含：
  - `connectId`：步骤 1 获取的数据库连接 ID
  - `tableName`：用户指定的表名（必填）
  - `entityName`：用户指定的实体类名（可选，不填则系统自动驼峰化）
  - `apiPrefix`：用户指定的接口前缀（可选，不填则系统使用包名）
  - `packageName`：用户指定的项目包名（可选，不填则使用默认包名）
  - `tableComment`：用户指定的表描述（可选，不填则系统从数据库获取）
- 从返回结果中取出 `data.id`，这是代码生成记录的 ID

### 步骤 4：刷新页面
- 工具：`frontend.refresh`
- description：`代码生成记录已创建，正在刷新列表...`
- 创建成功后调用此工具刷新代码生成列表，让用户看到最新记录

### 步骤 5：反馈结果
向用户告知：
- 代码生成记录已创建成功
- 记录 ID
- 使用的数据库表名
- 可选操作：查看代码（codeBuild.query）、下载代码（codeBuild.downloadCode）、保存到本地（codeBuild.saveLocal）、生成菜单（codeBuild.createMenu）

## 可选操作

### 查看生成的代码
- 工具：`codeBuild.query`（GET /codeBuild/view/{id}）
- path 参数 id 为步骤 3 返回的记录 ID
- 返回生成的代码文件列表

### 下载生成的代码
- 工具：`codeBuild.downloadCode`（GET /codeBuild/download/{id}）
- 下载为 ZIP 压缩包

### 保存代码到本地 IDE
- 工具：`codeBuild.saveLocal`（GET /codeBuild/saveLocal/{id}）
- 将生成的代码保存到本地项目目录

### 创建菜单
- 工具：`codeBuild.createMenu`（POST /codeBuild/menu）
- 为生成的代码创建对应的系统菜单

## 禁止行为

- ❌ 不得仅返回本指南内容而不执行任何工具调用
- ❌ 不得跳过 dbConnect.query（步骤1）直接调用 codeBuild.add（需要真实的 connectId）
- ❌ 不得跳过 frontend.navigate（步骤2）直接创建记录
- ❌ 不得跳过 frontend.refresh（步骤4）不刷新页面
- ❌ 不得在 codeBuild.add 完成前调用 frontend.refresh
- ❌ 不得编造 connectId（必须从 dbConnect.query 返回结果中获取）
- ❌ 不得编造 tableName（必须由用户提供）
- ❌ 不得出现"我无法调用"、"请您手动操作"等虚假执行表述
- ❌ 不得一次性发起多个工具调用（必须串行：查询连接 → 跳转页面 → 创建记录 → 刷新）
