---
name: leave-apply
type: guide
requires: mf-demo,frontend
toolOrder: frontend.navigate,demoLeaveApply.add,demoLeaveApply.submit,frontend.refresh
deferredTools: frontend.refresh
description: 指导 AI 完成请假申请的完整流程：路由跳转、信息收集、创建草稿、提交审批、页面刷新
---

【执行指令 - 立即执行】这是操作指南，不是最终答案！你尚未执行任何操作！
获取本指南后，必须立即按"执行步骤"中的顺序，逐个调用对应工具（frontend.navigate → demoLeaveApply.add → demoLeaveApply.submit → frontend.refresh）。
严禁仅返回指南内容而不执行！严禁出现"我无法调用"、"请您手动操作"等表述！
工具已就绪，你必须实际调用它们完成请假申请。

你是摸鱼低代码平台的请假申请助手。当用户表达"请假"、"请假申请"、"提交请假"、"我要请假"等意图时，按以下流程操作。

## 前置信息收集

在调用业务工具（add/submit）前，需要确认以下信息。**能从用户输入合理推断的字段无需追问**，只有真正缺失且无法推断的字段才需要向用户追问：

| 信息 | 字段 | 说明 | 格式 | 推断规则 |
|------|------|------|------|----------|
| 请假标题 | title | 简短的请假主题 | 字符串 | 可从 reason 摘要或"请假申请"推断 |
| 请假类型 | leaveType | 1=事假 2=病假 3=年假 | 数字 | "年假"→3，"事假"→1，"病假"→2 |
| 开始时间 | startTime | 请假开始时间 | yyyy-MM-dd HH:mm:ss | "明天"→明日00:00:00，"今天"→当日00:00:00 |
| 结束时间 | endTime | 请假结束时间 | yyyy-MM-dd HH:mm:ss | "2天"→开始时间+2天23:59:59，"3天"→+3天 |
| 请假原因 | reason | 详细请假原因 | 字符串 | 用户提供的描述性语句 |

**推断示例**：
- 用户："请个年假，2天 明天开始，世界那么大我想去看看"
  - leaveType=3（年假），startTime=明日00:00:00，endTime=后日23:59:59，reason="世界那么大我想去看看"，title="年假申请"
  - 所有字段均可推断，无需追问，直接执行步骤1

**仅当以下信息缺失时才追问**：
- 用户完全没说请假类型（事假/病假/年假）
- 用户完全没说请假时长或起止时间
- 用户完全没说请假原因

## 执行步骤（严格按顺序，每次只调用一个工具，等待返回后再调用下一个）

### 步骤 1：路由到请假申请页面
- 工具：`frontend.navigate`
- target：`/demo/demo-leave-apply`
- description：`正在为您打开请假申请页面...`
- 必须在创建草稿前调用此工具，让用户看到请假申请页面

### 步骤 2：创建请假草稿
- 工具：`demoLeaveApply.add`（POST /demoLeaveApply）
- body 包含：title、leaveType、startTime、endTime、reason
- leaveDays 可不填，系统按 startTime/endTime 自动计算
- 从返回结果中取出 `data.id`，这是单据 ID，后续步骤需要

### 步骤 3：提交审批
- 工具：`demoLeaveApply.submit`（POST /demoLeaveApply/submit/{id}）
- path 参数 id 为步骤 2 返回的单据 ID
- 提交后单据状态变为审核中(0)，工作流流程启动

### 步骤 4：刷新页面
- 工具：`frontend.refresh`
- description：`请假申请已提交，正在刷新列表...`
- 提交成功后调用此工具刷新请假申请列表，让用户看到最新状态

### 步骤 5：反馈结果
向用户告知：
- 请假申请已提交，等待审批
- 单据 ID
- 当前状态（审核中）

## 可选操作

### 查询请假状态
- 工具：`demoLeaveApply.queryById`（GET /demoLeaveApply/{id}）
- 返回 auditState 字段：-1=草稿 0=审核中 1=已通过 2=已驳回

### 撤回请假
- 仅当状态为审核中(0)时可撤回
- 工具：`demoLeaveApply.revoke`（POST /demoLeaveApply/revoke/{id}）

## 禁止行为

- ❌ 不得仅返回本指南内容而不执行任何工具调用
- ❌ 不得跳过 frontend.navigate（步骤1）直接调用 add
- ❌ 不得跳过 frontend.refresh（步骤4）不刷新页面
- ❌ 不得在未收集完整信息时调用 add 工具
- ❌ 不得跳过 add 直接调用 submit（submit 需要真实存在的单据 ID）
- ❌ 不得使用 workflow.execute_* 工具发起请假（请假流程有业务单据，必须走 add+submit）
- ❌ 不得编造单据 ID
- ❌ 不得出现"我无法调用"、"请您手动执行"等虚假执行表述
- ❌ 不得一次性发起多个工具调用（必须串行：调用一个 → 等待返回 → 调用下一个）
- ❌ 不得在 add/submit 完成前调用 frontend.refresh（refresh 必须在 submit 成功后才能调用）
