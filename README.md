# WindFlow平台后端

[![文档地址](https://img.shields.io/badge/docs-%E6%96%87%E6%A1%A3%E5%9C%B0%E5%9D%80-green)](http://www.mfish.com.cn)
[![合作联系](https://img.shields.io/badge/contact-%E5%90%88%E4%BD%9C%E8%81%94%E7%B3%BB-green)](mailto:qiufeng9862@qq.com)
[![ Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-green)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![License](https://img.shields.io/badge/power%20by-SpringBoot-green)](https://spring.io/projects/spring-boot)
[![Spring Cloud Alibaba](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-green)](https://spring.io/projects/spring-cloud-alibaba)
[![MyBatis Plus](https://img.shields.io/badge/MyBatis%20Plus-green)](https://baomidou.com/)
[![前端](https://img.shields.io/badge/doc-WindFlow%E5%BC%80%E5%8F%91%E5%B9%B3%E5%8F%B0%E5%89%8D%E7%AB%AF-blue)](https://gitee.com/qiufeng9862/mfish-nocode-view)

## 🐟项目介绍

WindFlow平台，是一款致力于 **让开发更高效** 的开发平台。我们希望打破技术门槛，让程序员和非程序员都能快速构建业务系统，提升效率，释放创造力。

这不仅是程序员偷闲时的效率神器，更是职场小白的建站利器，甚至是领导画原型的秘密武器！

## 🚀核心特点
开发 + 无代码统一平台：灵活切换，按需使用

即可快速生成业务代码，也可以无代码生成API接口和可视化大屏

单体+微服务一体化架构：一套代码同时支持单体和微服务两种开发模式

权限解耦设计：企业级权限控制，安全可靠

## 📔系统功能菜单

```
├─驾驶舱
│  ├─工作台
├─开发
│  ├─自助大屏
│  ├─自助API
│  ├─数据源
│  └─代码生成
├─系统管理
│  ├─菜单管理
│  ├─用户管理
│  ├─角色管理
│  ├─部门管理
│  ├─岗位管理
│  ├─字典管理
│  ├─分类管理
│  ├─系统日志
│  ├─在线用户
│  └─操作日志
├─连接中心
│  ├─数据源管理
│  └─API连接管理
├─第三方
│  ├─对象存储
│  └─短信配置
├─调度中心
│  ├─调度任务
│  └─调度日志
├─流程管理
│  ├─流程设计
│  ├─流程实例
│  └─流程任务
└─监控管理
   ├─在线人数
   ├─服务器日志
   ├─系统日志
   └─接口文档
```

## 🛠️技术框架

### 后端技术

* 核心框架：SpringBoot4 + Spring Cloud Alibaba
* 微服务框架：Spring Cloud Alibaba + Nacos
* 持久层框架：MyBatis-Plus
* 数据库支持：MySQL、PostgreSQL、Oracle、SQLServer
* 关系型数据库：Redis
* 分布式文件系统：MinIO、阿里OSS、华为OBS、七牛云
* 服务保护：Sentinel
* 工作流引擎：Flowable
* 定时任务：Quartz
* 工具类：Hutool、Lombok
* AI：langchain4j、Ollama、OpenAI

## ⚡快速开始

### 环境准备

- JDK 17+
- Node.js 18+
- Redis 6+
- MySQL 8+ 或 PostgreSQL 15+
- Maven 3.8+

### 启动说明

#### 单体服务启动（推荐）

无需启动Nacos注册中心，只需启动以下服务：

1. 启动数据库（执行 `db/mfish_nocode.sql` 初始化数据库）
2. 配置 `db/mf_config.sql` 中的 Redis 和数据库连接信息
3. 修改 `mf-start/mf-start-boot/src/main/resources/application.yml` 中的数据库和Redis配置
4. 启动 `MfNoCodeStart` 启动类

单体服务启动后访问地址：http://localhost:9999

默认账号密码：`admin/admin123`

#### 微服务启动

需要先启动 Nacos 注册中心，然后依次启动以下服务：

1. 启动 Nacos 注册中心（需部署3个节点）
2. 启动 MySQL、Redis 等基础服务
3. 初始化数据库（执行 `db/*.sql` 脚本）
4. 启动顺序：mf-gateway → mf-oauth → mf-system → mf-scheduler → mf-nocode → mf-workflow → mf-monitor
5. 启动前端项目访问 http://localhost:5173

### 前端部署

```bash
# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev

# 构建生产环境
pnpm build
```

## 📦数据库文件

| 文件                 | 描述                           |
|--------------------|------------------------------|
| `mf_config.sql`    | nacos数据库                     |
| `mf_oauth.sql`     | 认证数据库                        |
| `mf_system.sql`    | 系统管理数据库                      |
| `mf_scheduler.sql` | 调度中心数据库                      |
| `mf_nocode.sql`    | 开发中心数据库                     |
| `mf_workflow.sql`  | 工作流数据库                       |
| `mf_demo.sql`      | 样例中心数据库(非必须)                 |
| `mfish_nocode.sql` | 单实例数据库<br />`单实例启动只需要执行这个脚本` |

## 📂目录结构

```
mf-nocode
├── db/                              # 数据库脚本
├── mf-api/                          # Feign接口模块
│   ├── mf-sys-api/                  # 系统服务Feign接口
│   ├── mf-oauth-api/                # 认证服务Feign接口
│   ├── mf-nocode-api/               # 开发服务Feign接口
│   └── mf-job-api/                  # 调度服务Feign接口
├── mf-common/                        # 公共模块
│   ├── mf-common-core/              # 核心包
│   ├── mf-common-log/               # 日志记录包
│   ├── mf-common-mybatis/           # MyBatis封装包
│   ├── mf-common-redis/             # Redis封装包
│   ├── mf-common-security/         # 安全认证包
│   ├── mf-common-swagger/           # Swagger封装包
│   └── mf-common-ai/                # AI封装包
├── mf-gateway/                       # 网关服务
├── mf-oauth/                         # 认证中心
├── mf-business/                      # 业务模块
│   ├── mf-sys/                      # 系统服务
│   ├── mf-scheduler/                # 调度中心
│   ├── mf-nocode/                   # 开发中心
│   ├── mf-workflow/                 # 工作流服务
│   ├── mf-demo/                     # 样例服务
│   └── mf-ai/                       # AI服务
├── mf-start/                         # 启动模块
│   ├── mf-start-boot/               # 单体启动
│   └── mf-start-oauth/              # 微服务认证中心启动
└── pom.xml                           # 父POM
```

## 📱大屏配置教学
[1.自助大屏配置系列-画布操作](https://www.bilibili.com/video/BV14YLbz7ESh/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)
[2.自助大屏配置系列-组件操作](https://www.bilibili.com/video/BV1FCABLE7d8/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)
[3.自助大屏配置系列-数据源配置](https://www.bilibili.com/video/BV1Fz4y1R7Yj/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)
[4.自助大屏配置系列-交互配置](https://www.bilibili.com/video/BV1Nz4y1R7Ku/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)

## 🙏鸣谢

| 贡献者 |
|-----|
| [qiufeng](https://gitee.com/qiufeng9862) |
| [MrQF](https://github.com/MrQF) |
| [sunkai](https://gitee.com/sunkai0820) |
| [lws1830671533](https://gitee.com/lws1830671533) |
| [lihailong](https://gitee.com/lihailong) |
| [culer](https://github.com/culer) |
| [zhangyafei](https://gitee.com/zyf520) |
| [chenling](https://gitee.com/cl406) |
| [zhengyouyin](https://gitee.com/zhengyouyin) |
| [simaq](https://gitee.com/simaq) |
| [willinghero](https://gitee.com/willinghero) |
| [xianduir](https://gitee.com/xianduir) |

## 💬技术交流

### 技术交流群

#### 大群

[![加入QQ群](https://img.shields.io/badge/QQ%E7%BE%A4-289877815-blue.svg)](https://qm.qq.com/q/zQdI2rMsj8)

点击链接加入群聊【WindFlow2群】

[![加入QQ1群](https://img.shields.io/badge/QQ1%E7%BE%A4-522792773--已满-blue--已满)](https://jq.qq.com/?_wv=1027&k=0A2bxoZX)

点击链接加入群聊【WindFlow1群】（已满）

### 大屏配置教学
[1.自助大屏配置系列-画布操作](https://www.bilibili.com/video/BV14YLbz7ESh/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)
[2.自助大屏配置系列-组件操作](https://www.bilibili.com/video/BV1FCABLE7d8/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)
[3.自助大屏配置系列-数据源配置](https://www.bilibili.com/video/BV1Fz4y1R7Yj/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)
[4.自助大屏配置系列-交互配置](https://www.bilibili.com/video/BV1Nz4y1R7Ku/?share_source=copy_web&vd_source=0cf425790dc7750eb5d8a4d1c0b028f4)

## 🧩相关项目

- [mf-nocode](https://gitee.com/qiufeng9862/mfish-nocode) - WindFlow平台后端
- [mf-nocode-view](https://gitee.com/qiufeng9862/mfish-nocode-view) - WindFlow平台前端
- [mf-wechat](https://gitee.com/qiufeng9862/mf-wechat) - 微信小程序
