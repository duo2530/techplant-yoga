# TechPlant Yoga

[简体中文](./README.md) | [English](./README.en.md)

瑜伽普拉提预约与运营管理项目，包含管理端、用户端小程序、Java 后端和项目设计文档。

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

用户端页面只依赖 `api` 层：门店与排课已经接真实后端（`frontend/uni-app/api/store.js`、`api/schedule.js` → `GET /api/stores`、`GET /api/schedules`），其余业务域仍是 mock 数据。接入真实后端时，按同样方式替换 `frontend/uni-app/api` 下对应业务接口即可。

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
├─ README.md
└─ README.en.md
```

## 快速开始

按下面步骤依次执行，即可在本地跑通「数据库 → 后端 → 管理端 → 用户端小程序」完整链路（第 0 步是环境准备，第 1~6 步是操作步骤）。

| 约定项 | 默认值 |
| --- | --- |
| 数据库名 | `yoga` |
| 后端地址 | `http://localhost:8080` |
| 管理端地址 | `http://localhost`（Vite 开发端口 80） |
| 管理端登录账号 | `admin / admin123` |
| 用户端 H5 端口 | `9090`（仅运行到浏览器时使用） |

### 0. 环境准备

| 依赖 | 版本 / 说明 |
| --- | --- |
| JDK | 8 |
| Maven | 3.3.9 及以上 |
| MySQL | 5.7 / 8.0，字符集建议 `utf8mb4` |
| Redis | 5.0 及以上，默认 `localhost:6379`、无密码 |
| Node.js | 18 及以上（管理端使用 Vite 6） |
| HBuilderX | 最新稳定版，用于编译用户端小程序 |
| 微信开发者工具 | 最新稳定版，并在「设置 → 安全设置」中开启服务端口 |

### 1. 把三个 SQL 脚本导入数据库

后端依赖三个初始化脚本，全部位于 `backend/sql`，**必须按顺序执行**：

| 顺序 | 脚本 | 作用 |
| --- | --- | --- |
| 1 | `backend/sql/ry_20260417.sql` | 若依基础表与种子数据（`sys_user` 含 `admin` 账号、`sys_menu`、字典等） |
| 2 | `backend/sql/quartz.sql` | Quartz 定时任务表（`QRTZ_*`） |
| 3 | `backend/sql/yoga.sql` | 瑜伽业务表（`t_store`、`t_classroom`、`t_coach`、`t_course`、`t_schedule`）+ 业务菜单（2000~2005）+ `store_region` 区域字典 + 示例数据 |

`ry_20260417.sql` 和 `quartz.sql` 里**没有 `CREATE DATABASE` 和 `use` 语句**，所以要自己先建库再导入。库名默认用 `yoga`，与 `application-druid.yml` 保持一致：

```sql
CREATE DATABASE IF NOT EXISTS `yoga` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

**方式 A：图形化工具（推荐）**

用 Navicat / DBeaver / MySQL Workbench 连接 MySQL → 新建数据库 `yoga` → 依次打开并执行上表中的三个 `.sql` 文件（顺序不要颠倒；`yoga.sql` 需要在已导入若依基础表的库上执行）。

**方式 B：mysql 命令行**

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS yoga DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p --default-character-set=utf8mb4 yoga
```

先 `cd` 到仓库根目录，进入 `mysql>` 交互后用 `source` 执行三个脚本：

```sql
source backend/sql/ry_20260417.sql;
source backend/sql/quartz.sql;
source backend/sql/yoga.sql;
```

> PowerShell 不支持 `<` 输入重定向，因此这里用 mysql 客户端内部的 `source` 命令，避免中文乱码和语法问题。

**导入后自检**：

```sql
USE yoga;
SELECT COUNT(*) FROM sys_user;                                  -- 应至少有 admin 账号
SELECT menu_name FROM sys_menu WHERE menu_id BETWEEN 2000 AND 2005;
SELECT COUNT(*) FROM t_store;                                   -- 5 张业务表已建好
```

`yoga.sql` 可以重复执行：业务表都是 `DROP TABLE IF EXISTS` 后重建，菜单只清理并重写 2000~2006，不影响若依自带的「系统管理」菜单；但**重建业务表会清空业务数据**。

### 2. 修改后端配置文件

| 配置文件 | 需要修改的内容 |
| --- | --- |
| `backend/ruoyi-admin/src/main/resources/application-druid.yml` | `spring.datasource.druid.master.url`（默认 `jdbc:mysql://localhost:3306/yoga`）、`username`（默认 `root`）、`password`（默认 `123456`） |
| `backend/ruoyi-admin/src/main/resources/application.yml` | `spring.redis.host / port / password`（默认 `localhost:6379`、空密码）、`server.port`（默认 `8080`）、`ruoyi.profile` 上传目录（默认 `D:/ruoyi/uploadPath`，Windows 下请确认该目录已存在） |

几点提示：

- 数据库名如果没建成 `yoga`，记得同步改 `master.url` 里的库名。
- MySQL 8.0 且未配置 SSL 证书时，建议把 URL 里的 `useSSL=true` 改成 `useSSL=false&allowPublicKeyRetrieval=true`，否则可能出现连接失败。
- `token.secret`、`/druid/*` 监控台口令（默认 `ruoyi/123456`）、`ruoyi.profile` 都是脚手架默认值，仅用于本地开发，上线前必须修改。

### 3. 构建并启动后端

**方式 A：IDE 启动**

用 IDEA / Eclipse 打开 `backend`，直接运行启动类 `backend/ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java`。

**方式 B：命令行启动**

```powershell
cd backend
mvn -DskipTests clean package
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

Windows 下也可以用现成脚本 `backend/bin/package.bat`（打包）和 `backend/bin/run.bat`（启动）；Linux / macOS 用 `backend/ry.sh start`。

启动成功的标志：

- 控制台日志出现「若依启动成功」（端口 `8080` 无冲突）；
- 浏览器访问 `http://localhost:8080` 有响应；
- 可选：接口文档 `http://localhost:8080/swagger-ui/index.html`，Druid 监控台 `http://localhost:8080/druid`。

启动失败的常见原因：MySQL 或 Redis 未启动、配置里的库名/账号/密码不对、第 1 步的三个 SQL 没有导入完整。

### 4. 启动管理端（PC）

```powershell
cd frontend/pc
npm install
npm run dev
```

- 先 `npm install` 安装依赖，再 `npm run dev` 启动；Vite 会构建并自动打开浏览器。
- 开发端口是 **80**，访问地址为 `http://localhost`；若提示端口占用或需要权限，临时改 `frontend/pc/vite.config.js` 的 `server.port`。
- 开发环境接口通过 `/dev-api` 代理到 `http://localhost:8080`，后端换个地址就改 `frontend/pc/vite.config.js` 顶部的 `baseUrl`。
- 打开管理端后，输入用户名 **`admin`**、密码 **`admin123`** 登录（验证码是数学计算题，按提示填写结果）。
- 登录成功后，左侧菜单「经营基础」下应能看到门店管理、教室管理、教练管理、课程管理、排课管理；如果菜单为空，说明 `yoga.sql` 的菜单数据没导入成功或未重新登录。
- 只改前端不启后端也能打开登录页，但登录会失败，所以请先完成第 3 步。

### 5. 运行用户端小程序

`frontend/uni-app` 是 HBuilderX 工程，**没有 `package.json`，不能用也不需要用 npm 启动**。

1. **用 HBuilderX 打开工程**：HBuilderX → 文件 → 打开目录 → 选择 `frontend/uni-app` 目录。
2. **修改 AppID**：双击工程中的 `manifest.json`，在可视化界面左侧选择「微信小程序配置」，把 **AppID** 填成自己的微信小程序 AppID；也可以直接修改 `manifest.json` 里 `mp-weixin.appid`（当前是占位值 `touristappid`，**必须替换**，否则微信开发者工具会报 AppID 无效）。
   - AppID 在微信公众平台「开发 → 开发管理 → 开发设置」中获取。
   - 仓库根目录的 `.env`（已被 `.gitignore` 忽略，不入库）里记录了本地使用的 `APP_ID`，可以作参考；该文件不会被工程代码读取。
3. **准备微信开发者工具**：先在 HBuilderX「工具 → 设置 → 运行配置」里把「微信开发者工具路径」指向本机安装目录，并确认微信开发者工具「设置 → 安全设置 → 服务端口」处于打开状态，否则 HBuilderX 无法把编译结果推给它。
4. **运行到小程序**：回到 HBuilderX，点击**左上角的「运行」→「运行到小程序模拟器」→「微信开发者工具」**，等待首次编译完成（第一次会慢一些），微信开发者工具会自动打开并加载小程序。
5. 开发阶段建议在微信开发者工具「详情 → 本地设置」里勾选「不校验合法域名」。
6. 用户端目前是「部分接后端、部分 mock」的状态：门店与排课走真实接口（`/api/stores`、`/api/schedules`，地址取 `frontend/uni-app/config.js` 里的 `baseUrl`，默认 `http://localhost:8080`），其余业务域仍是 mock 数据。因此想让首页门店、约课页有真实数据，请先完成第 3 步把后端启动起来；后端未启动时这些页面只会显示空态。
7. 只想快速看页面时，也可以「运行 → 运行到浏览器」，H5 端口默认 `9090`。

> 改过 `manifest.json` / `pages.json` 后需要重新编译，个别情况要重启微信开发者工具。

### 6. 验证清单

| 检查项 | 期望结果 |
| --- | --- |
| 数据库 | `yoga` 库中存在 `sys_*`、`QRTZ_*` 与 `t_store` 等 5 张业务表 |
| 后端 | 日志出现「若依启动成功」，`http://localhost:8080` 可访问 |
| 管理端 | `npm install` 与 `npm run dev` 无报错，`http://localhost` 能打开登录页 |
| 登录 | `admin / admin123` 登录成功，能看到「经营基础」下的 5 个业务菜单 |
| 业务页面 | 门店/教室/教练/课程/排课页面能正常分页查询、新增和修改 |
| 用户端 | HBuilderX 编译无报错，微信开发者工具中能看到 4 个 tabBar 页面 |

### 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 后端启动报 Redis 连接失败 | Redis 未启动，或 `application.yml` 的 `spring.redis` 地址/密码不对 |
| 后端启动报数据库连接失败 | MySQL 未启动、库名/账号/密码不对，或 MySQL 8 的 SSL 参数问题（见第 2 步） |
| 管理端登录报接口错误 / 404 | 后端未启动，或后端端口不是 `8080`（端口改了要同步改 `vite.config.js` 的 `baseUrl`） |
| 登录后左侧没有业务菜单 | `backend/sql/yoga.sql` 未执行成功，或执行后未重新登录 |
| `npm run dev` 启动失败 | Node.js 版本过低（需 18+），或 80 端口被占用（改 `server.port`） |
| 微信开发者工具提示 AppID 无效 | 第 5 步的 AppID 还是占位值 `touristappid`，需替换成自己的 AppID |
| 小程序接口请求失败 | 开发阶段勾选「不校验合法域名」，并确认后端已启动 |

## 当前门店模块

门店管理已包含：

- 管理端门店分页查询与详情查询
- 新增门店
- 修改门店（全量编辑，返回最新详情）
- 删除门店（物理删除，删除前校验该门店没有未结束的排课）
- 用户端免登录分页查询门店列表，支持按区域筛选与关键字搜索
- 门店类型：主力店、精品店
- 所在区域：存行政区划 code，区名由字典 `store_region` 翻译
- 联系电话、地址、门店图片（单图）
- 固定营业时间文本

门店一期**不设启用/停用状态**，也没有逻辑删除：删除就是对数据行执行 `DELETE`。

管理端入口：

```text
经营基础 -> 门店管理
```

主要接口：

```text
GET    /admin/stores
GET    /admin/stores/{storeId}
POST   /admin/stores
PUT    /admin/stores/{storeId}
DELETE /admin/stores/{storeId}
GET    /api/stores
```

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
