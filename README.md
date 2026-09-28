# TechPlant Yoga

一水·瑜伽普拉提预约与运营管理项目，包含管理端、用户端小程序、Java 后端和项目设计文档。

项目当前以 MVP 和可验收的业务垂直切片为主，已落地课程、教练、门店等基础业务模块，并持续补充排班、预约等业务能力。

## 项目组成

| 目录 | 说明 |
| --- | --- |
| `backend` | 基于 RuoYi 的 Java 后端，包含系统管理和瑜伽业务模块 |
| `frontend/pc` | 运营管理端 Web，负责门店、课程、教练等后台管理 |
| `frontend/uni-app` | 用户端 uni-app 小程序工程，面向微信小程序和 H5 |
| `docs` | 需求分析、概要设计、详细设计、原型和业务对象资料 |

## 技术栈

### 后端

- Java 8
- Spring Boot 2.5.15
- RuoYi 3.9.2
- Spring MVC、Spring Security
- MyBatis-Plus 3.5.5
- MySQL、Druid、Redis（按 RuoYi 运行配置提供）
- Springfox Swagger
- Maven 多模块构建

瑜伽业务代码位于 `com.techplant.yoga` 包下，业务模块使用 DO、DTO、VO、DAO、Service、Controller 分层。接口不直接返回持久化实体，分页接口沿用 RuoYi 的 `TableDataInfo` 响应结构。

### 运营管理端

- Vue 3.5
- Vite 6
- Element Plus 2.13
- Pinia 3
- Axios
- Vue Router 4

管理端基于 RuoYi Vue3 脚手架，业务页面位于 `frontend/pc/src/views`，接口封装位于 `frontend/pc/src/api`。

### 用户端小程序

- uni-app
- Vue 3
- Pinia
- 微信小程序
- HBuilderX

当前用户端页面保持 mock 数据架构，页面只依赖 `api` 层；接入真实后端时，优先替换 `frontend/uni-app/api` 下对应业务接口。

## 项目结构

```text
techplant-yoga/
├─ backend/
│  ├─ ruoyi-admin/       # 后端启动模块
│  ├─ ruoyi-common/      # 公共工具和基础能力
│  ├─ ruoyi-framework/   # RuoYi 框架能力
│  ├─ ruoyi-system/      # 系统管理模块
│  ├─ ruoyi-yoga/        # 瑜伽业务模块
│  ├─ ruoyi-quartz/      # 定时任务模块
│  ├─ ruoyi-generator/   # 代码生成模块
│  └─ sql/               # 基础表和瑜伽业务初始化脚本
├─ frontend/
│  ├─ pc/
│  │  └─ src/
│  │     ├─ api/         # 管理端接口
│  │     ├─ views/       # 管理端页面
│  │     ├─ router/      # 路由和动态菜单
│  │     └─ store/       # Pinia 状态
│  └─ uni-app/
│     ├─ api/            # 用户端业务接口层
│     ├─ mock/           # 用户端 mock 数据
│     ├─ pages/          # 小程序页面
│     ├─ components/     # 业务组件
│     └─ static/         # 图片、字体和样式资源
├─ docs/
│  ├─ 需求分析/
│  ├─ 概要设计/
│  ├─ 详细设计/
│  ├─ 原型/
│  └─ 需求文档/
├─ .gitignore
├─ LICENSE
└─ README.md
```

## 当前门店模块

门店管理已包含：

- 管理端门店分页查询
- 新增、修改门店
- 启用和停用门店
- 游客免登录查询启用门店列表
- 省、市、区三级地址选择器
- 自动保存省、市、区行政区划 code
- 经营类型：直营连锁、加盟
- 门店类型：主力店、精品店
- 固定营业时间文本

管理端入口：

```text
经营基础 -> 门店管理
```

主要接口：

```text
GET  /admin/stores
GET  /admin/stores/{storeId}
POST /admin/stores
PUT  /admin/stores/{storeId}
PUT  /admin/stores/{storeId}/status?status=0
GET  /api/stores
```

## 本地运行

### 1. 准备基础服务

准备 JDK 8、Maven、MySQL、Redis。先执行 RuoYi 基础初始化脚本，再执行瑜伽业务脚本：

```text
backend/sql/ry_20260417.sql
backend/sql/yoga.sql
```

数据库、Redis 和端口配置位于：

```text
backend/ruoyi-admin/src/main/resources/application.yml
backend/ruoyi-admin/src/main/resources/application-druid.yml
```

### 2. 构建并启动后端

```powershell
cd backend
mvn -pl ruoyi-admin -am clean package
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

后端默认端口为 `8080`，具体以本地配置为准。

### 3. 启动管理端

```powershell
cd frontend/pc
npm install
npm run dev
```

生产构建：

```powershell
npm run build:prod
```

开发环境默认通过 `/dev-api` 代理访问 `http://localhost:8080`。

### 4. 运行用户端

`frontend/uni-app` 是 HBuilderX 工程，不包含 `package.json`，不能使用 npm 启动。请使用 HBuilderX 打开该目录，然后运行到微信开发者工具或浏览器。

## 验证命令

后端模块构建和门店定向测试：

```powershell
cd backend
mvn -pl ruoyi-yoga -am -DskipTests clean compile
mvn -pl ruoyi-yoga -am -Dtest=com.techplant.yoga.store.convert.StoreConverterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

管理端构建：

```powershell
cd frontend/pc
npm run build:prod
```

## 文档约定

- 需求、概要设计和详细设计文档使用简体中文。
- 未确认的业务规则保留为 `TODO-待确认`，不提前扩展领域能力。
- API 使用明确的 DTO 和 VO，避免直接暴露持久化实体。
- 数据库变更统一维护在 `backend/sql` 下的业务脚本中。

## 许可证

本项目使用 [MIT License](./LICENSE)。
