# 摸鱼低代码平台后端

[![文档地址](https://img.shields.io/badge/docs-%E6%96%87%E6%A1%A3%E5%9C%B0%E5%9D%80-green)](http://www.mfish.com.cn)
[![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](https://github.com/mfish-qf/mfish-nocode/blob/main/LICENSE)
[![Version](https://img.shields.io/badge/version-2.4.1-brightgreen.svg)](https://github.com/mfish-qf/mfish-nocode/releases/tag/V2.3.1)

[![GitHub stars](https://img.shields.io/github/stars/mfish-qf/mfish-nocode.svg?style=social&label=Stars)](https://github.com/mfish-qf/mfish-nocode)
[![GitHub forks](https://img.shields.io/github/forks/mfish-qf/mfish-nocode.svg?style=social&label=Fork)](https://github.com/mfish-qf/mfish-nocode)
[![star](https://gitee.com/qiufeng9862/mfish-nocode/badge/star.svg?theme=white)](https://gitee.com/qiufeng9862/mfish-nocode/stargazers)
[![fork](https://gitee.com/qiufeng9862/mfish-nocode/badge/fork.svg?theme=white)](https://gitee.com/qiufeng9862/mfish-nocode/members)

## 🧱架构图

![](http://oscimg.oschina.net/AiCreationDetail/up-49fe6faf5fa60eefe9a5c2e0fa65c797.png)

## 🐟项目介绍

摸鱼低代码平台，是一款致力于 **让开发像摸鱼一样轻松** 的低代码/无代码平台。我们希望打破技术门槛，让程序员和非程序员都能快速构建业务系统，提升效率，释放创造力。

这不仅是程序员偷闲时的效率神器，更是职场小白的建站利器，甚至是领导画原型的秘密武器！

平台内置完整的 **AI 能力矩阵**，覆盖智能路由、多租户模型管理、垂直领域 AI 助手，让 AI 真正融入业务流程。

## 🚀核心特点
低代码 + 无代码统一平台：灵活切换，按需使用

即可快速生成业务代码，也可以无代码生成API接口和可视化大屏

单实例微服务一体化架构：支持单体服务和微服务两种开发部署模式，一套代码解决两种架构，开箱即用

权限解耦：企业级的权限控制，安全可靠与业务代码完全解耦，通过注解控制权限

**AI 原生**：基于 Spring AI 2.0 构建，平台内置 AI 网关路由、多租户大模型管理、7 大垂直领域 AI 助手，支持自然语言直接操作业务系统

## 🤖AI能力

<style>
:root {
  color-scheme: light;
  --surface: #F7F7F8;
  --surface-muted: #EFEFF2;
  --text: #171717;
  --text-muted: #52525B;
  --border: rgba(23, 23, 23, 0.12);
  --brand: #4B3FE3;
  --brand-soft: #F2F7FF;
  --brand-soft-strong: #E5EAFF;
  --brand-text: #1A1759;
  --brand-on: #FFFFFF;
  --chart-series-1: #3C2ECA;
  --chart-series-2: #A9AEFF;
  --chart-series-3: #6F6FFF;
  --chart-series-4: #22A5F7;
  --chart-other: #D3D4DA;
  --accent: #27D2BF;
  --accent-soft: #EAFBF8;
  --accent-text: #0F766E;
  --radius: 8px;
  --radius-card: 12px;
  --radius-full: 999px;
  --spacer-4: 4px;
  --spacer-8: 8px;
  --spacer-12: 12px;
  --spacer-16: 16px;
  --spacer-20: 20px;
  --spacer-24: 24px;
  --font-sans: "SF Pro Text", "PingFang SC", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  --font-metric: "Inter", "SF Pro Text", "PingFang SC", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  --font-mono: "JetBrains Mono", ui-monospace, "SF Mono", Menlo, Consolas, monospace;
  --weight-regular: 400;
  --weight-medium: 500;
  --weight-strong: 600;
  --text-caption: 12px/18px;
  --text-body: 14px/20px;
  --text-title: 16px/24px;
  --text-code: 13px/20px;
}
:root[data-widget-theme="dark"] {
  color-scheme: dark;
  --surface: #171717;
  --surface-muted: #262626;
  --text: #E5E5E5;
  --text-muted: #A1A1AA;
  --border: rgba(229, 229, 229, 0.12);
  --brand: #6054F1;
  --brand-soft: #1A1759;
  --brand-soft-strong: #3C2ECA;
  --brand-text: #CFD8FF;
  --chart-series-1: #4B3FE3;
  --accent-soft: #123F3C;
  --accent-text: #CCFBF1;
}
.widget {
  color: var(--text);
  background: transparent;
  font: var(--weight-regular) var(--text-body) var(--font-sans);
}
.arch-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-card);
  padding: var(--spacer-20);
}
.arch-title {
  font: var(--weight-medium) var(--text-title) var(--font-sans);
  color: var(--text);
  margin: 0 0 var(--spacer-4) 0;
}
.arch-sub {
  font: var(--weight-regular) var(--text-caption) var(--font-sans);
  color: var(--text-muted);
  margin: 0 0 var(--spacer-16) 0;
}
.t { fill: var(--text); font: var(--weight-regular) var(--text-body) var(--font-sans); }
.th { fill: var(--text); font: var(--weight-medium) var(--text-body) var(--font-sans); }
.ts { fill: var(--text-muted); font: var(--weight-regular) var(--text-caption) var(--font-sans); }
.tc { fill: var(--text); font: var(--weight-medium) var(--text-code) var(--font-mono); }
.arr { stroke: var(--text-muted); stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; fill: none; }
.arr-brand { stroke: var(--brand); stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; fill: none; }
.boundary { fill: var(--surface-muted); stroke: var(--border); stroke-dasharray: 4 4; }
.boundary-brand { fill: var(--brand-soft); stroke: var(--brand); stroke-dasharray: 4 4; }
.node { fill: var(--surface); stroke: var(--border); }
.node-brand { fill: var(--surface); stroke: var(--brand); }
.legend-row { display: flex; align-items: center; gap: var(--spacer-8); font: var(--weight-regular) var(--text-caption) var(--font-sans); color: var(--text-muted); }
.legend-swatch { width: 14px; height: 10px; border-radius: 3px; flex-shrink: 0; }
.legend-line { width: 18px; height: 2px; flex-shrink: 0; }
</style>
<div class="widget arch-card" data-dynamic-ui-widget data-template="node-flow">
  <h3 class="arch-title">mfish-nocode AI 能力架构</h3>
  <p class="arch-sub">三支柱编排：Planner 规划 · Capability Engine 能力聚合 · Memory+LLM 上下文路由</p>
  <svg viewBox="0 0 720 600" width="100%" height="auto" role="img" aria-label="AI能力架构图">
    <defs>
      <marker id="arrow" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="8" markerHeight="8" markerUnits="userSpaceOnUse" orient="auto">
        <path d="M1 1 L7 4 L1 7 Z" fill="#52525B"/>
      </marker>
      <marker id="arrow-brand" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="8" markerHeight="8" markerUnits="userSpaceOnUse" orient="auto">
        <path d="M1 1 L7 4 L1 7 Z" fill="#4B3FE3"/>
      </marker>
    </defs>
    <!-- Tier 1: Entry Layer -->
    <rect class="boundary" x="80" y="24" width="560" height="86" rx="12"/>
    <text class="ts" x="360" y="44" text-anchor="middle">入口层 · Entry Points</text>
    <rect class="node" x="100" y="54" width="160" height="46" rx="8"/>
    <text class="tc" x="180" y="74" text-anchor="middle">/router</text>
    <text class="ts" x="180" y="90" text-anchor="middle">路由聊天</text>
    <rect class="node" x="280" y="54" width="160" height="46" rx="8"/>
    <text class="tc" x="360" y="74" text-anchor="middle">/assist/chat</text>
    <text class="ts" x="360" y="90" text-anchor="middle">垂直助手</text>
    <rect class="node" x="460" y="54" width="160" height="46" rx="8"/>
    <text class="tc" x="540" y="74" text-anchor="middle">/agent/chat</text>
    <text class="ts" x="540" y="90" text-anchor="middle">自主规划</text>
    <!-- Connector: Entry → AgentRuntime -->
    <path class="arr-brand" d="M 360 110 L 360 140" marker-end="url(#arrow-brand)"/>
    <!-- Tier 2: AgentRuntime (brand focal) -->
    <rect class="node-brand" x="230" y="140" width="260" height="54" rx="8" fill="var(--brand-soft)"/>
    <text class="th" x="360" y="163" text-anchor="middle" fill="var(--brand-text)">AgentRuntime 编排器</text>
    <text class="ts" x="360" y="181" text-anchor="middle" fill="var(--brand)">Plan → Execute → Emit</text>
    <!-- Connectors: AgentRuntime → 3 Pillars (junction at y=210) -->
    <path class="arr" d="M 360 194 L 360 210 L 140 210 L 140 220" marker-end="url(#arrow)"/>
    <path class="arr-brand" d="M 360 194 L 360 220" marker-end="url(#arrow-brand)"/>
    <path class="arr" d="M 360 194 L 360 210 L 580 210 L 580 220" marker-end="url(#arrow)"/>
    <!-- Pillar 1: Planner (neutral) -->
    <rect class="boundary" x="40" y="220" width="200" height="170" rx="12"/>
    <text class="ts" x="140" y="240" text-anchor="middle">Planner 柱 · 规划</text>
    <rect class="node" x="60" y="250" width="160" height="32" rx="8"/>
    <text class="t" x="140" y="270" text-anchor="middle">Planner 任务规划</text>
    <rect class="node" x="60" y="294" width="160" height="32" rx="8"/>
    <text class="t" x="140" y="314" text-anchor="middle">Executor 步骤执行</text>
    <rect class="node" x="60" y="338" width="160" height="32" rx="8"/>
    <text class="t" x="140" y="358" text-anchor="middle">EventBus 事件流</text>
    <path class="arr" d="M 140 282 L 140 294" marker-end="url(#arrow)"/>
    <path class="arr" d="M 140 326 L 140 338" marker-end="url(#arrow)"/>
    <!-- Pillar 2: CapabilityEngine (brand focal) -->
    <rect class="boundary-brand" x="260" y="220" width="200" height="170" rx="12"/>
    <text class="ts" x="360" y="240" text-anchor="middle" fill="var(--brand)">Capability Engine · 能力聚合</text>
    <rect class="node-brand" x="280" y="250" width="160" height="26" rx="8"/>
    <text class="tc" x="360" y="267" text-anchor="middle">ToolCapability</text>
    <rect class="node-brand" x="280" y="284" width="160" height="26" rx="8"/>
    <text class="tc" x="360" y="301" text-anchor="middle">McpCapability</text>
    <rect class="node-brand" x="280" y="318" width="160" height="26" rx="8"/>
    <text class="tc" x="360" y="335" text-anchor="middle">SkillCapability</text>
    <rect class="node-brand" x="280" y="352" width="160" height="26" rx="8"/>
    <text class="tc" x="360" y="369" text-anchor="middle">WorkflowCapability</text>
    <!-- Pillar 3: Memory + LLM (neutral) -->
    <rect class="boundary" x="480" y="220" width="200" height="170" rx="12"/>
    <text class="ts" x="580" y="240" text-anchor="middle">Memory + LLM 柱 · 上下文</text>
    <rect class="node" x="500" y="258" width="160" height="44" rx="8"/>
    <text class="t" x="580" y="278" text-anchor="middle">ChatMemory</text>
    <text class="ts" x="580" y="294" text-anchor="middle">会话窗口策略</text>
    <rect class="node" x="500" y="316" width="160" height="44" rx="8"/>
    <text class="t" x="580" y="336" text-anchor="middle">LlmModelRouter</text>
    <text class="ts" x="580" y="352" text-anchor="middle">多租户路由</text>
    <!-- Connector: CapabilityEngine → External -->
    <path class="arr-brand" d="M 360 390 L 360 424" marker-end="url(#arrow-brand)"/>
    <!-- Tier 4: External Systems -->
    <rect class="boundary" x="40" y="424" width="640" height="76" rx="12"/>
    <text class="ts" x="360" y="444" text-anchor="middle">外部系统 · External Systems</text>
    <rect class="node" x="60" y="456" width="140" height="34" rx="8"/>
    <text class="t" x="130" y="478" text-anchor="middle">微服务 Feign+HTTP</text>
    <rect class="node" x="220" y="456" width="140" height="34" rx="8"/>
    <text class="t" x="290" y="478" text-anchor="middle">MCP 服务器</text>
    <rect class="node" x="380" y="456" width="140" height="34" rx="8"/>
    <text class="t" x="450" y="478" text-anchor="middle">skills/*.md</text>
    <rect class="node" x="540" y="456" width="140" height="34" rx="8"/>
    <text class="t" x="610" y="478" text-anchor="middle">Flowable 工作流</text>
    <!-- Legend -->
    <g transform="translate(40, 528)">
      <rect class="boundary-brand" x="0" y="0" width="18" height="12" rx="3"/>
      <text class="ts" x="24" y="10">核心引擎</text>
      <rect class="boundary" x="100" y="0" width="18" height="12" rx="3"/>
      <text class="ts" x="124" y="10">支撑层</text>
      <line class="arr-brand" x1="200" y1="6" x2="224" y2="6"/>
      <text class="ts" x="230" y="10">主调用链</text>
      <line class="arr" x1="310" y1="6" x2="334" y2="6"/>
      <text class="ts" x="340" y="10">编排流向</text>
    </g>
    <text class="ts" x="680" y="538" text-anchor="end">mf-common-ai · mf-ai</text>
  </svg>
</div>

### 智能网关路由

通过网关助手（GatewayAssistant）实现 LLM 智能路由决策：用户用自然语言提问，LLM 自动判断意图并路由到对应领域助手，全程无需手动选择入口。

### 多租户大模型管理

- 按租户隔离 AI 模型配置，不同租户可使用不同的 LLM 提供商（OpenAI / Ollama / 智谱等）
- ChatModel 按配置签名缓存共享，不随租户增长
- 提供 OpenAI 兼容的 `/v1/chat/completions` 统一代理接口，流式/非流式自动切换

### 垂直领域 AI 助手

平台为每个业务中心配备专属 AI 助手，通过自然语言直接操作业务系统：

| 助手 | 路由路径 | 覆盖能力 |
|------|---------|---------|
| 摸鱼小助手 | `/ai/agent/chat` | 通用问答，平台介绍 |
| 认证中心助手 | `/ai/oauth2/assist` | 菜单、组织、角色、帐号、租户管理 |
| 系统中心助手 | `/ai/sys/assist` | 字典、分类目录、日志、代码生成、数据库、数据源 |
| 低代码中心助手 | `/ai/nocode/assist` | 自助大屏、自助API、组件管理、公式信息 |
| 调度中心助手 | `/ai/scheduler/assist` | 定时任务调度、任务执行日志、任务回调状态 |
| 存储中心助手 | `/ai/storage/assist` | 文件存储信息、文件管理、文件资源获取 |
| 工作流助手 | `/ai/workflow/assist` | 流程部署、流程实例管理、任务审批、待办任务 |


## 🎯适用场景
企业内部系统搭建（ERP、CRM、OA等）

快速原型设计与验证

数据展示看板等轻应用开发快速集成

## 💡技术栈
* 后端基于SpringBoot4, Spring Cloud Alibaba，实现微服务、单体服务代码一体化架构
* 前端采用VUE3+AntDesign
* 注册中心、配置中心采用nacos(作为单体服务时无需使用注册中心)
* 支持oauth2统一认证接入，支持多种登录方式（账号密码登录、手机短信登录、微信扫码登录）
* 支持租户切换，租户可以自己管理自己的人员、组织、角色
* 支持可视化配置查询API接口，后端自动生成SQL执行
* 支持注解方式进行数据权限控制，与业务代码完全解耦
* AI能力基于 Spring AI 2.0，支持多租户大模型路由、OpenAI兼容代理

## 🧩功能模块

```

├─驾驶舱
│  ├─工作台
├─低代码
│  ├─自助大屏
│  ├─自助API
│  ├─数据源
│  └─代码生成
├─系统管理
│  ├─菜单管理
│  ├─组织管理
│  ├─角色管理
│  ├─帐号管理
│  ├─字典管理
│  ├─分类管理
│  ├─日志管理
│  ├─文件管理
│  ├─在线用户
│  ├─应用管理
│  └─数据库
├─审批管理
│  ├─审批列表
│  ├─我的申请
│  └─流程管理
├─租户管理
│  ├─租户配置
│  ├─个人信息
│  ├─租户信息
│  ├─租户组织
│  ├─租户角色
│  ├─租户人员
├─系统监控
│  ├─监控中心
├─任务调度
│  ├─任务管理
│  ├─任务日志
├─项目文档
│  ├─接口地址
│  ├─开发文档
│  ├─Gitee地址
│  ├─Github地址
│  └─AntDesign文档
├─使用样例
│  ├─上传下载
│  ├─导入导出
│  ├─多级目录
│  ├─主子表
│  └─数据权限
├─流程表单
│  ├─流程图
│  └─表单设计器
├─AI助手
│  ├─摸鱼小助手(通用问答)
│  ├─认证中心助手
│  ├─系统中心助手
│  ├─低代码中心助手
│  ├─调度中心助手
│  ├─存储中心助手
│  └─工作流助手
├─关于
└─其他模块 
   └─更多功能开发中...
```
## 🛡️安全报告

[![Security Status](https://www.murphysec.com/platform3/v31/badge/1796428877999906816.svg)](https://www.murphysec.com/console/report/1672256253122600960/1796428877999906816)

## 🌐前端源码地址

[![github](https://img.shields.io/badge/前端地址-github-black.svg)](https://github.com/mfish-qf/mfish-nocode-view)
[![gitee](https://img.shields.io/badge/前端地址-gitee-ad312d.svg)](https://gitee.com/qiufeng9862/mfish-nocode-view)
[![gitcode](https://img.shields.io/badge/前端地址-gitcode-be3642.svg)](https://gitcode.com/mfish-qf/mfish-nocode-view.git)

## 🌐后端源码地址

[![github](https://img.shields.io/badge/后端地址-github-black.svg)](https://github.com/mfish-qf/mfish-nocode)
[![gitee](https://img.shields.io/badge/后端地址-gitee-ad312d.svg)](https://gitee.com/qiufeng9862/mfish-nocode)
[![gitcode](https://img.shields.io/badge/后端地址-gitcode-be3642.svg)](https://gitcode.com/mfish-qf/mfish-nocode.git)

## 📖文档地址

+ [文档地址](http://www.mfish.com.cn)
+ [在线预览](http://app.mfish.com.cn:11119)

## 🎭平台交流

### 微信:

![微信](https://oscimg.oschina.net/oscnet/up-aaf63a91b96c092ad240b2e9755d926ba62.png)

### QQ群:
[![加入QQ2群](https://img.shields.io/badge/QQ2%E7%BE%A4-289877815-blue.svg)](https://qm.qq.com/q/zQdI2rMsj8)

点击链接加入群聊【摸鱼低代码2群】

[![加入QQ1群](https://img.shields.io/badge/QQ1%E7%BE%A4-522792773--已满-blue--已满)](https://jq.qq.com/?_wv=1027&k=0A2bxoZX)

点击链接加入群聊【摸鱼低代码1群】（已满）

### 大屏配置教学
[1.自助大屏配置系列-画布操作](https://www.bilibili.com/video/BV14YLbz7ESh/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)

[2.自助大屏配置系列-画布配置](https://www.bilibili.com/video/BV15CLnzBEWN/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)

[3.自助大屏配置系列-数据绑定](https://www.bilibili.com/video/BV1Mr5KzSE6V/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)

[4.自助大屏配置系列-动画绑定](https://www.bilibili.com/video/BV1K1JNzdEG1/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)

[5.自助大屏配置系列-多屏联动](https://www.bilibili.com/video/BV1kvjHzdEok/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)

[6.自助大屏配置系列-动态显隐](https://www.bilibili.com/video/BV1CkrNBWEsj/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)

[7.自助大屏配置系列-表单查询](https://www.bilibili.com/video/BV1efzsB4Ebq/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)

### 项目截图

<table>
    <tr>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-88ced73c1f0228af408c50349497540b.png" /></td>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-c77cdb0b34e61b837cbfc22e5ad5acb7.png" /></td>
    </tr>
    <tr>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-ee4f2a35788db09fcc6e281cf171f8c1.jpg" /></td>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-6e87424a0defbdb968a21f9bc42a610b.jpg" /></td>
    </tr>
    <tr>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-0d6f1ff813c700191c34536a1d84bf6d.jpg" /></td>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-5586c6c0e7600f96fe62eed1e2a558ad.jpg" /></td>
    </tr>
    <tr>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-51a14b3a3d460c40a5cad78404420d78.png" /></td>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-a8003812c6f8895b1f52a9005742f521.png" /></td>
    </tr>
    <tr>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-68939a2ada4ed26028beebaa6cabac04.png" /></td>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-1e29d5d79f2176d1b54c06778010ab1a.png" /></td>
    </tr>
    <tr>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-a01321e2df1ead79c96e552b108b5332.png" /></td>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-444b8574b9965a5f031febde2337f0e6.png" /></td>
    </tr>
    <tr>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-1c749d25a5a7cf3f6d881ac252ff4a45.png" /></td>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-e12d1cf33ec7bed102e42d60306c13bb.png" /></td>
    </tr>
    <tr>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-aae99916987fd56a5106571ad16499c6.png" /></td>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-e62a5748e91eaf1909cb7f27a7c3b4a5.png" /></td>
    </tr>
    <tr>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-3cf5951020da20b3e7ccf445fadfc0c3.png" /></td>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-7bb21a0253bd19d4172331ec2472b277.jpg" /></td>
    </tr>
    <tr>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-7fa88472a42caa99f6606697b1b0a250.png" /></td>
      <td><img src="http://oscimg.oschina.net/AiCreationDetail/up-6723cb1d005c5760fdc2c004c379a8b4.png" /></td>
    </tr>
</table>

### 数据库信息

| 文件                 | 描述                           |
|--------------------|------------------------------|
| `mf_config.sql`    | nacos数据库                     |
| `mf_oauth.sql`     | 认证数据库                        |
| `mf_system.sql`    | 系统管理数据库                      |
| `mf_scheduler.sql` | 调度中心数据库                      |
| `mf_nocode.sql`    | 低代码中心数据库                     |
| `mf_workflow.sql`  | 工作流数据库                       |
| `mf_demo.sql`      | 样例中心数据库(非必须)                 |
| `mf_ai.sql`         | AI中心数据库                 |
| `mfish_nocode.sql` | 单实例数据库<br />`单实例启动只需要执行这个脚本` |

```
如果单实例使用只需要导入mfish_nocode.sql库即可
如果使用微服务需要导入mf_config.sql、mf_oauth.sql
、mf_system.sql、mf_scheduler.sql、mf_nocode.sql、mf_workflow.sql、mf_demo.sql等数据库
```
### 单实例启动
* 初始化SQL脚本 mfish-nocode.sql
* 在目录mf-start/mf-start-boot中找到MfNoCodeStart启动

### 微服务启动
* 请查看 https://www.mfish.com.cn

### swagger访问地址

* http://localhost:8888/swagger-ui/index.html

### 账号密码
+ 账号：admin
+ 密码：!QAZ2wsx
