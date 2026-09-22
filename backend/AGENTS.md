# AGENTS.md — backend（若依后端）

> 作用域：`backend/` 及其所有子目录。项目背景与业务口径见仓库根目录 `docs/`（需求分析 / 概要设计 / 详细设计 / 原型）。

## 1. 这是什么

一水·瑜伽普拉提项目的后端 Web 服务。底座是 **RuoYi-Vue 3.9.2 脚手架**（`com.ruoyi.*` 框架代码 + `sys_*` 系统表，尽量与上游保持一致），在此之上已有独立业务模块 **`ruoyi-yoga`**（包名 `com.techplant.yoga`，目前只有「课程管理」一个业务模块，详见第 3、6 节）。

**业务代码一律写进 `ruoyi-yoga`，不要写进 `ruoyi-*` 框架模块。** 统一响应、统一异常、ID 序列化、审计字段填充、业务日志、MyBatis-Plus 装配这些横切能力**已经有统一实现**，动手前先看 **第 8 节「全局体系」**，不要再造第二套。

## 2. 技术栈（已核对 pom）

| 项 | 版本 / 说明 |
|---|---|
| Spring Boot | **2.5.15** |
| JDK | **1.8**（`<java.version>1.8</java.version>`），不是 17 |
| 构建 | Maven 多模块，`maven-compiler-plugin:3.1`（本机 Maven 3.3.9 即可构建） |
| 安全 | Spring Security + JWT（jjwt 0.9.1，token 存 Redis，key 前缀 `login_tokens:`） |
| ORM | **MyBatis-Plus 3.5.5（业务模块）+ 原生 MyBatis XML Mapper + PageHelper 1.4.7（`sys_*`）共存**：两者共用 framework 里那一个 `SqlSessionFactory`，装配方式与坑见 §8.9 |
| JSON | Jackson（`ruoyi-yoga` 用 `JacksonConfig` 把业务 ID 序列化成字符串，见 §8.3）+ FastJson2（Redis 序列化器、框架内部 JSON） |
| 连接池 | Druid 1.2.28（自带 `/druid/*` 监控台） |
| 存储 | MySQL（`mysql-connector-java`，版本由 Boot 托管）+ Redis（Lettuce） |
| 定时任务 | Quartz，任务配置存 `sys_job` 表 |
| 接口文档 | springfox 3.0.0（OAS 3）。**方法必须标 `@ApiOperation` 才会被扫描进文档**（见 `ruoyi-admin/.../SwaggerConfig.java`） |
| 代码生成 | ruoyi-generator，Velocity 模板在 `ruoyi-generator/src/main/resources/vm`（含 vue3 / v3ts 模板） |

## 3. 模块职责

| 模块 | 职责 |
|---|---|
| `ruoyi-admin` | 启动类 `com.ruoyi.RuoYiApplication`、全部 Controller、`application*.yml`、`logback.xml` |
| `ruoyi-framework` | Security/JWT 过滤器、拦截器、全局异常、Druid/Redis/线程池等配置、`TokenService` |
| `ruoyi-system` | `sys_*` 业务：用户/角色/菜单/部门/岗位/字典/参数/公告/日志 |
| `ruoyi-common` | 工具类、注解、常量、`AjaxResult`/`R`/`TableDataInfo`/`BaseController`、`BaseEntity` |
| `ruoyi-quartz` | 定时任务（`SysJob*`，任务目标写法 `ryTask.ryParams('ry')`） |
| `ruoyi-generator` | 代码生成器（表结构 → 后端 CRUD + vue 页面） |
| **`ruoyi-yoga`** | **业务模块（本项目自己的代码都在这）**：包名 `com.techplant.yoga`（**不是 `com.ruoyi`**），ORM 用 MyBatis-Plus。当前内容：`course` 模块（controller / service / service.impl / dao / mapper / domain / dto / vo / query / convert）+ `common` 横切基础设施（`config` / `mybatis` / `log` / `response` / `util`）+ `schedule`、`booking` 的跨模块查询接口（接口先行，临时占位实现）。已在父 `pom.xml` 的 `<modules>` 与 `ruoyi-admin/pom.xml` 中注册 |

## 4. 常用命令

```bash
# 编译（本机 Maven 3.3.9 + JAVA_HOME=jdk1.8.0_202 已验证通过）
mvn -f backend/pom.xml -DskipTests clean compile
# 打包 → backend/ruoyi-admin/target/ruoyi-admin.jar
mvn -f backend/pom.xml -DskipTests clean package
# 运行
java -jar backend/ruoyi-admin/target/ruoyi-admin.jar
```

- Windows 下也有 `backend/bin/*.bat`（clean/package/run）和 `backend/ry.bat`；Linux 用 `backend/ry.sh {start|stop|restart|status}`。**这些 .bat 是 GBK 编码，别用 UTF-8 工具直接改写。**
- **JDK 必须是 8**：本机 `JAVA_HOME=D:\Develop\Java\jdk1.8.0_202`，命令行/IDEA 都用这个 SDK。
- 测试：`ruoyi-yoga/src/test` 已有用例（JUnit 5 + Mockito + MockMvc，另有 H2 装配自检）。`ruoyi-yoga/pom.xml` 显式锁定 `maven-surefire-plugin` 2.22.2 —— 本机 Maven 3.3.9 默认绑的 2.12.4 不认识 JUnit 5，去掉就会**静默跑不到用例**。`ruoyi-*` 框架模块仍没有 `src/test`。
- 启动前置条件：**MySQL 和 Redis 都必须可用**，且 `backend/sql/ry_20260417.sql`、`backend/sql/quartz.sql` 两个脚本都已导入目标库。这两个 SQL 里没有 `CREATE DATABASE`/`use`，必须先自己建库（当前工作区用的是默认库名 `ry-vue`，带横线，命令行/SQL 里记得转义）再导入。
- 业务模块的表/菜单另有三个脚本：`backend/sql/yoga_course.sql`（`t_course` 建表）、`yoga_course_menu.sql`（`sys_menu` 菜单与按钮权限串）、`yoga_course_dev_data.sql`（开发库演示数据，不要导到生产）。
- 默认端口 8080，默认账号 `admin / admin123`。

## 5. 配置文件（改配置前先看清这里）

| 文件 | 内容 |
|---|---|
| `ruoyi-admin/src/main/resources/application.yml` | 应用名、端口、`spring.profiles.active: druid`、**Redis 连接**（当前已填云实例 host/port/password，未提交）、token 密钥与有效期、MyBatis/PageHelper、swagger、XSS/防盗链 |
| `ruoyi-admin/src/main/resources/application-druid.yml` | **数据源在这里，不在 application.yml**：`master.url/username/password`（当前已填云实例，库名仍为默认 `ry-vue`）、Druid 池参数、监控台账号、`/druid/*` 白名单 |
| `ruoyi-admin/src/main/resources/logback.xml` | 日志级别与输出路径（上游默认 `/home/ruoyi/logs`） |
| `ruoyi-admin/src/main/resources/mybatis/mybatis-config.xml` | MyBatis 全局设置 |

## 6. 项目专属约束（**最重要**）

`docs/详细设计/详细设计.md` §1.1.1 已评审确认的口径，写代码时必须遵守：

- 业务包名 `com.techplant.yoga.<模块>`；表名 `t_` + 业务名（如 `t_course`）；主键 `bigint` 雪花 ID，不用数据库自增；逻辑删除字段 `deleted`（0 正常/1 删除）；审计字段 `create_by/create_time/update_by/update_time`。
- ORM 用 **MyBatis-Plus**（`IdType.ASSIGN_ID`、`@TableLogic`、`MetaObjectHandler`）—— **已经引入**（父 pom `mybatis-plus.version=3.5.5`），装配方式与易踩的坑见 §8.9。
- 主键返回前端时**必须序列化成字符串**（Long → String），因为小程序 `Number` 只有 53 位精度（已有统一实现，见 §8.3）。

因此：

1. **业务代码不要写进 `ruoyi-*` 框架模块**，也别改 `sys_*` 表结构。业务代码一律放 `ruoyi-yoga` 模块（包名 `com.techplant.yoga`），`com.ruoyi.*` 保持与上游一致，方便后续比对升级。
2. MyBatis-Plus 与原生 MyBatis XML Mapper 共用 framework 里那一个 `SqlSessionFactory`，**配置是手工装配的、不是自动配置**；改动 MP 相关配置前先读 §8.9，别想当然加 `mybatis-plus.*` 的 yml 配置（那些不生效）。
3. 管理端菜单/路由由数据库驱动：新增管理端页面必须配套 `sys_menu` 的 SQL（目录 + 菜单 + 按钮权限串），现成范例见 `backend/sql/yoga_course_menu.sql`。

## 7. 代码约定

**框架内**新增代码（`com.ruoyi.*`，尽量少动）：

- 包结构：`com.ruoyi.<模块>.controller | service | service.impl | mapper | domain`；Mapper XML 放 `src/main/resources/mapper/<模块>/*Mapper.xml`（`mybatis.mapperLocations=classpath*:mapper/**/*Mapper.xml`）。
- Controller 继承 `BaseController`：列表用 `startPage()` + `getDataTable(list)`，单条用 `AjaxResult.success(data)`，增删改用 `toAjax(int rows)`。（**业务模块不用这套 `startPage()`**，见 §8.8。）
- 权限：`@PreAuthorize("@ss.hasPermi('模块:实体:动作')")`；放行接口用 `@Anonymous`（会被 `PermitAllUrlProperties` 收集进 permitAll）。
- 切面注解：`@Log(title=..., businessType=BusinessType.INSERT)`（操作日志）、`@RepeatSubmit`（防重复提交）、`@RateLimiter`（限流）、`@DataScope`（数据权限）、`@DataSource`（多数据源）。
- 导出用 `@Excel` + `ExcelUtil`；分页参数 `pageNum/pageSize`；时间格式 `yyyy-MM-dd HH:mm:ss`。

**业务代码**（`ruoyi-yoga` / `com.techplant.yoga.*`）—— 照 `course` 包抄，逐层职责与调用规则：

| 层 | 包 | 规则 |
|---|---|---|
| controller | `<模块>.controller` | **只调 service**，不碰 dao/mapper；`@Valid` 做参数校验；只负责装配响应（`AjaxResult` / `TableDataInfo`），**不写业务逻辑** |
| service | `<模块>.service` / `service.impl` | 业务规则、存在性校验、跨模块引用检查、事务边界；**出参一律 VO，DO 不出 service 层**；`IPage` 也不出 service 层 |
| dao | `<模块>.dao` | 数据访问，面向业务语义，屏蔽 mapper 细节；只被 service 调用 |
| mapper | `<模块>.mapper` | MyBatis-Plus `BaseMapper<XxxDO>`；复杂 SQL 写 XML，不用注解堆 SQL |
| domain / dto / vo / query / convert | 同名包 | 对象只用这五种：DO 对应表、DTO 是 service 入参、Query 承载查询条件、VO 是出参、Converter 做转换（手写，字段多了再考虑 MapStruct）。**不用 PO / BO / AO** |
| 公共设施 | `common.*` | `response` / `config` / `mybatis` / `log` / `util`，见第 8 节 |

- 中文注释、中文提示语，和现有代码风格保持一致（JavaDoc 里引用设计章节号，如 `（详细设计 §4.1.1）`，这是本项目的既有写法）。

## 8. 全局体系（横切能力）—— **写业务代码前先看这一节**

> 这一节回答「轮子在哪、口径是什么、不要做什么」。权威出处是 `docs/详细设计/详细设计.md`；响应体系与异常体系的拍板过程见 `docs/详细设计/详细设计记录.md` 的 **R11（2026-09-22）**。
> 判断标准：**凡是「所有接口都该这么做」的事，这里都已经有统一实现，不要另起一套。**

### 8.1 统一响应（对外契约）

| 场景 | 用什么类 | 结构 |
|---|---|---|
| 单条查询 / 新增 / 修改 / 状态变更 | `com.ruoyi.common.core.domain.AjaxResult` | `{code, msg, data}`，成功 `code = 200`；**`data` 为空时该字段不返回** |
| 列表 / 分页 | `com.ruoyi.common.core.page.TableDataInfo` | `{total, rows, code, msg}`（字段名是 `rows`，**不是 `data.list`**） |
| service 层内部 | `com.techplant.yoga.common.response.PageResult` | 只承载「总记录数 + 当前页数据」；MyBatis-Plus 的 `IPage` **不出 service 层** |

- **HTTP 状态码固定 200，业务结果一律看 `code`** —— 与 RuoYi 管理端既有约定一致（管理端 axios 拦截器只判断一个字段）。
- 业务模块**不要自定义对外响应体**：原先的 `ApiResponse` 已在 R11 中删除（同一个工程里出现两套响应体会直接打架）。
- 反例：controller 直接 `return page.getList()`、或把 `IPage` 返回出去、或为了「好看」包一层 `{success, result}`。
- 范例：`ruoyi-yoga/.../course/controller/CourseController.java`（列表装配 `TableDataInfo`，单条用 `AjaxResult.success`）。

### 8.2 统一异常处理

一句话口径：**业务失败就抛 `ServiceException("提示语", 业务码)`，剩下的交给框架唯一的全局异常处理器**；不要 `catch` 之后自己拼响应，也不要吞异常返回 null。

| 环节 | 落点 | 说明 |
|---|---|---|
| **唯一的处理器** | `ruoyi-framework/.../web/exception/GlobalExceptionHandler.java` | `@RestControllerAdvice`，把所有异常统一转成 `AjaxResult`； |
| 抛业务异常 | `com.ruoyi.common.exception.ServiceException` | `throw new ServiceException("课程不存在或已被删除", 404)`；不带码时默认 500 |
| 401 未登录 / 登录态失效 | `framework/security/handle/AuthenticationEntryPointImpl` | 用 `ServletUtils.renderString` 手工写 `AjaxResult.error(401, ...)`，**HTTP 仍是 200** |
| 403 无权限 | `GlobalExceptionHandler#handleAccessDeniedException` | 固定文案「没有权限，请联系管理员授权」；本版 RBAC 未启用，暂不返回 |
| 参数校验失败 | 同一个处理器里的 `MethodArgumentNotValidException` / `BindException` 分支 | 业务码是 **500（不是 400）**，`msg` 是具体字段提示（如「课程名称不能为空」） |
| 兜底 | `RuntimeException` / `Exception` 分支 | 会把 `e.getMessage()` 直接放进 `msg` —— 所以**不要往异常提示语里塞内部细节**（数据库异常会带出表名与语句片段） |

业务码口径（详细设计 §2.4）：

| code | 用在 | 本项目的实例 |
|---|---|---|
| 200 | 成功 | 所有接口 |
| 401 | 未登录 / 登录态失效 | 框架返回 |
| 403 | 无权限 | RBAC 未确认，暂不返回 |
| 404 | 资源不存在 | 「课程不存在或已被删除」 |
| 409 | 状态冲突 | 「该课程下仍有 N 个未完成排班、M 条未结束预约，无法停用」 |
| 500 | 参数校验失败 / 系统异常 | `@Valid` 校验失败也走这里 |

三条容易写错的：

1. **409 的结构化明细放不进 `data`**：`AjaxResult` 只有 `code/msg/data`，而框架处理器不会附带 `data`，所以阻塞明细（几个排班、几条预约）现在是**拼进 `msg`** 的。这是 R11 的已知遗留，要结构化明细就得改框架处理器（会同时影响 `sys_*` 接口）。
2. **`@Valid` 校验失败返回 500**，与直觉里的 400 不一样；要改成 400 同样得动框架处理器 —— **不要顺手改**。
3. **跨模块调用失败不许降级放行**：引用检查（排班/预约统计）抛异常时，停用操作必须跟着失败（fail-closed），否则统计服务一抖动课程就会被误停用。范例见 `CourseServiceImpl#countUnfinishedSchedules`。

范例：`ruoyi-yoga/.../course/service/impl/CourseServiceImpl.java`（404 / 409 的抛法、哪些分支按 WARN 记日志）、以及 `src/test/.../CourseControllerTest.java`（挂真实 `GlobalExceptionHandler` 验证转换链路）。

### 8.3 ID 序列化：64 位整数一律返回字符串

- 落点：`com.techplant.yoga.common.config.JacksonConfig`。
- 规则：**只对 `com.techplant.yoga` 包下的返回对象生效**，且只处理**字段名是 `id` 或以 `Id` 结尾**的 `Long`/`long` → 序列化成带引号的字符串（雪花 ID 超出小程序 `Number` 的 53 位安全范围，返回数字会丢精度）。
- 两个**故意**的副作用，别当成 bug：
  - `sys_*` 接口不动（否则 `sys_user.userId` 会变成 `"1"`，管理端存在 `userId === 1` 之类数值比较）；
  - **计数型 Long 不动**：叫 `scheduleCount` / `bookingCount` 这类字段仍返回数字 —— **字段命名直接决定序列化行为**，新写 VO 时留意。
- 所以：新 VO 里的业务主键**命名必须**是 `id` 或 `xxxId`，否则会以数字返回、前端丢精度。

### 8.4 审计字段自动填充 + 逻辑删除

| 能力 | 落点 | 用法 |
|---|---|---|
| 审计字段 | `com.techplant.yoga.common.mybatis.AuditMetaObjectHandler`（MyBatis-Plus `MetaObjectHandler`） | DO 字段上标 `@TableField(fill = FieldFill.INSERT)`（`createBy/createTime`）/ `INSERT_UPDATE`（`updateBy/updateTime`），**不要手写 set**；拿不到登录人时只填时间不填人，不让写操作失败 |
| 取当前登录人 | `com.techplant.yoga.common.util.CurrentUserUtils` | `getUserIdOrNull()` / `getUserIdText()`；**直接用 `SecurityUtils.getUserId()` 未登录会抛异常**，所以才包了这一层 |
| 逻辑删除 | DO 上的 `@TableLogic` | 标注 `deleted` 后，MP 自动给查询追加 `deleted = 0`、删除变 `UPDATE`；**不要手写 `deleted = 0` 条件** |

### 8.5 链路标识与业务日志

- `com.techplant.yoga.common.log.TraceIdFilter`：生成/透传 `traceId` 写入 MDC，并回写响应头 **`X-Trace-Id`**（前端与运维按它排查）。
- `com.techplant.yoga.common.log.BusinessLog`：专用 logger `com.techplant.yoga.business`，输出固定字段行 `traceId= operator= source= target= action= result= detail= cost=`。
- 方法语义：`start()`（进入，结果记 START）/ `success()` / `blocked()`（被业务规则拦截，如停用被引用）/ `warn()`（可预期失败）/ `error()` / `debug()`（跨模块调用细节，默认关）。
- 级别口径：INFO = 写操作成功；WARN = 可预期的业务失败（参数校验失败、对象不存在、被引用拦截、跨模块调用失败）；ERROR = 非预期异常；DEBUG = 细节。
- **查询类接口不写业务日志**（高频只读会把审计日志淹掉），例外是慢查询（> 500ms）打 WARN。
- **不写进日志**：长文本（课程介绍）、完整封面图地址（只记有/无）、请求全量报文。
- 注意区分：框架的 `@Log(title=..., businessType=...)` + `LogAspect` 是**另一套**（写 `sys_oper_log` 操作日志，`sys_*` 在用），业务模块目前用的是 `BusinessLog`。

### 8.6 认证与鉴权

- `SecurityConfig` 里是 `anyRequest().authenticated()`：**除 `/login`、`/register`、`/captchaImage`、swagger/druid/静态资源外，一律需要登录态**；业务接口（如 `/admin/courses`）都在其中。
- Token：JWT（jjwt），登录态存 Redis（`TokenService`，key 前缀 `login_tokens:`），过期时间看 `token.expireTime`。
- 细粒度权限：`@PreAuthorize("@ss.hasPermi('模块:实体:动作')")`；放行接口用 `@Anonymous`（由 `PermitAllUrlProperties` 收集进 permitAll 列表）。
- **现状**：管理端 RBAC 尚未确认（需求分析 E09），`CourseController` 上**没有任何 `@PreAuthorize`**，即「登录即可调用」。等权限矩阵定了再补，别自己发明权限串。

### 8.7 事务与跨模块调用

- 写操作在 **service 层**加 `@Transactional(rollbackFor = Exception.class)`（三个写接口都是这么写的）；controller 不加事务。
- **不跨模块读表**：课程模块不直接查 `t_schedule` / `t_booking`，只依赖对方模块暴露的 service 接口（`ScheduleQueryService` / `BookingQueryService`，本版为「接口先行」+ 临时占位实现）。直接读表会把两个模块的库结构绑死。
- **fail-closed**：跨模块引用检查失败 → 当前操作失败，不降级放行（见 §8.2 第 3 条）。

### 8.8 分页

- 业务模块的路径：`Query` 对象（`pageNum`/`pageSize` + 默认值与上限校验，如 `CourseQuery.MAX_PAGE_SIZE = 100`）→ service 用 MP 的 `IPage` 查询 → 转成 `PageResult` → controller 装配 `TableDataInfo`。
- **不要用框架那套 `BaseController.startPage()` + PageHelper** —— 那是 `sys_*` 的写法，两套分页混用会互相干扰。
- 分页必须有**稳定排序**，否则翻页会重复/漏项（课程列表是 `sort_no ASC, id DESC`）。
- 兜底：MP 分页插件设了 `maxLimit = 100`。

### 8.9 MyBatis-Plus 的装配方式

工程在 `ruoyi-framework` 里自己声明了 `SqlSessionFactory`，**MyBatis-Plus 的自动配置因此整体退出**，于是：

1. MP 的分页插件与 `MetaObjectHandler` 是 `MyBatisConfig.sqlSessionFactory(...)` 通过 `ObjectProvider` **手工装配**进去的 —— 新加 MP 的 `InnerInterceptor`、或新加 `MetaObjectHandler`，要改的是这里，**不是 `MybatisPlusConfig` 一个类**。
2. 启动类 `RuoYiApplication` 的 `scanBasePackages` **必须显式包含 `com.techplant.yoga`**（业务包不在 `com.ruoyi` 之下），否则业务 controller / service / 配置类**全都不会被注册**，接口 404 且没有任何报错。
3. `application.yml` 的 `mybatis.typeAliasesPackage` 必须同时含 `com.ruoyi.**.domain` 与 `com.techplant.yoga.**.domain`；`mapperLocations` 是 `classpath*:mapper/**/*Mapper.xml`（业务 XML 放 `ruoyi-yoga/src/main/resources/mapper/<模块>/`）。
4. 父 `pom.xml` 把 **`jsqlparser` 锁在 4.6**：PageHelper 5.3.3 需要 `SelectBody`（4.7 起被删），MP 3.5.5 是最后一个用 4.6 的版本。**不要顺手升级 MyBatis-Plus 或 jsqlparser**，升级会让 PageHelper 直接启动报错。
5. `com.techplant.yoga.common.config.MybatisPlusConfig` 只负责 `@MapperScan("com.techplant.yoga.**.mapper")` 与提供分页插件 Bean。

装配类问题**读代码看不出来**，验证方式是跑 `MyBatisWiringTest`（H2 内存库，验证雪花 ID / 逻辑删除 / 审计填充真的生效）。

### 8.10 其他框架能力（按需取用，别重复实现）

| 能力 | 用法 | 现状 |
|---|---|---|
| 防重复提交 | `@RepeatSubmit`（`SameUrlDataInterceptor`，Redis 存 key） | 业务模块还没用 |
| 限流 | `@RateLimiter` | 业务模块还没用 |
| 数据权限 / 多数据源 | `@DataScope`（`DataScopeAspect`）、`@DataSource`（`DynamicDataSource`） | 仅 `sys_*` 使用 |
| 缓存 | Redis + `RedisCache` 工具；序列化用 `FastJson2JsonRedisSerializer` | `TokenService` 在用 |
| 国际化 | `MessageUtils` + `i18n/messages` | 业务提示语目前是硬编码中文 |
| 异步/线程池 | `ThreadPoolConfig` + `AsyncManager`（框架日志异步落库） | — |
| 接口文档 | springfox 3.0.0，**方法必须标 `@ApiOperation` + 类上 `@Api`** 才会进文档；`swagger.pathMapping=/dev-api` | `CourseController` 已按此标注 |
| 时间格式 | 框架只设了时区（`ApplicationConfig` 用 JVM 默认时区）；**pattern 要在 VO 字段上自己标** `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` | 见 `CourseListItemVO` / `CourseDetailVO` |
| CORS | `ResourcesConfig` 的 `CorsFilter`（`allowedOriginPattern("*")`） | 全开，上线前评估 |

### 8.11 已知缺口 / 沿用上游未收敛的行为（**别当成 bug 去"修"**）

- **XSS 过滤器没覆盖业务接口**：`application.yml` 的 `xss.urlPatterns` 是 `/system/*,/monitor/*,/tool/*`，`/admin/**` **不在其中**。是否补齐待定，**不要擅自扩大**（会改变现有请求体行为）。
- **参数校验失败业务码是 500 而不是 400**，`RuntimeException` 分支会把异常 message 原样给前端 —— 都是框架既有行为，改动会波及 `sys_*` 接口。
- **409 没有结构化明细**（明细拼在 `msg` 里），R11 遗留。
- **管理端 RBAC 未启用**（E09 未确认），业务接口目前只要求登录。
- **详细设计 §3.2.1 提到的 `exception` 包实际不存在**：R11 删掉了自定义异常类，业务侧统一用框架的 `ServiceException`，文档那句是遗留。

## 9. 不要做的事

- 不要为了"顺手"改 `sys_*` 表结构或历史 SQL 内容。
- **不要为了「让参数校验返回 400」或「隐藏异常 message」去改框架的 `GlobalExceptionHandler`**：它同时服务 `sys_*` 接口，属于全局改动，要先提出来单独评估（§8.11）。
- `token.secret` 仍是默认串，`/druid/*` 监控台仍是 `ruoyi/123456` 且白名单为空（任何人可访问）；`ruoyi.profile` 上传目录仍是 `D:/ruoyi/uploadPath`（Windows 硬编码）。上线前必须改/关。
- 云 MySQL 常见连接坑：`useSSL=true` 且没有配证书时可能连不上（自建或普通云库建议 `useSSL=false`）；MySQL 8 默认认证插件是 `caching_sha2_password`，关掉 SSL 后需要追加 `allowPublicKeyRetrieval=true`。

## 10. 改完怎么验证

1. `mvn -f backend/pom.xml -DskipTests clean compile` 通过（最低要求）。
2. 改了 `ruoyi-yoga`（业务代码）：`mvn -f backend/pom.xml -pl ruoyi-yoga -am test` 通过（JUnit 5 单元测试 + H2 装配自检，**不连开发库**）。新增接口请补两类用例：service 层用 Mockito 隔离，controller 层用 MockMvc（参考 `CourseControllerTest`，记得挂框架的 `GlobalExceptionHandler` 与 `JacksonConfig`，否则测不到统一异常转换和 ID 字符串化）。
3. 需要实跑时：确认 MySQL/Redis 可连 → `java -jar ruoyi-admin/target/ruoyi-admin.jar` → 日志出现"若依启动成功" → 用 `admin/admin123` 调 `/login` 拿 token。
4. 涉及菜单/页面：登录后 `GET /getRouters` 能看到新菜单。
5. 涉及接口：确认方法上有 `@ApiOperation`，然后看 swagger UI（`http://localhost:8080/swagger-ui/index.html`，被 Security 拦截时参考 `SecurityConfig` 的 permitAll 列表）。
6. 涉及响应/异常口径：确认返回体仍是 `{code,msg,data}` / `{total,rows,code,msg}`，业务失败走的是 `ServiceException` 而不是自拼响应。
