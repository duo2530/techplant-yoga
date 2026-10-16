# AI Agent 开发手册

## 1、版本记录

| 版本 | 日期 | 修改内容 |
|---|---|---|
| v0.1 | 2026-09-29 | 初始编写 |
| v0.2 | 2026-10-12 | 集中后端开发约定，清理过时规则 |

## 2、编码约定

- 使用 Lombok 的 `@Getter`、`@Setter` 替代直接读写字段的 getter/setter；包含默认值、校验或转换逻辑的访问器保留显式实现。
- 使用 `@RequiredArgsConstructor` 与 `final` 字段完成 Bean 注入。
- `ruoyi-yoga/lombok.config` 将字段上的 `@Lazy`、`@Value` 复制到生成的构造器参数，保留延迟依赖和配置值注入。
- 使用 MapStruct 完成 DTO、DO、VO 转换。
- 基础设施位置见[项目基础设施](项目基础设施.md)，技术版本见本手册 §5。
- 业务规则、接口和数据模型以[各模块详细设计](../详细设计/)为准。

### Java 格式与提交检查

Java 源码统一使用 UTF-8、LF、4 空格缩进、左大括号同行，续行缩进 8 空格，目标行宽 120。
检查规则见根目录 [checkStyle.xml](../../checkStyle.xml)，自动格式化规则见
[eclipse-formatter.xml](../../tools/java-style/eclipse-formatter.xml)。包名、import 和包含 URL 的行不检查行宽；
不可自动换行的长字符串或注释需要手动拆分。

在仓库根目录启用提交钩子（每个克隆执行一次）：

```powershell
git config core.hooksPath .githooks
```

macOS / Linux 还需确保钩子可执行：

```bash
chmod +x .githooks/pre-commit
```

提交时自动格式化暂存的后端 Java 源码（包含测试源码），检查通过后重新暂存格式化结果。
部分暂存的 Java 文件会阻止提交，请先完整暂存该文件或取消暂存。格式化和检查先在临时目录执行，
失败时不回写源码。仅修改前端或文档时不会启动 Maven。首次运行需要联网下载插件，环境需提供 Maven、JDK 8 或更高版本。
钩子通过 Bash 执行，兼容 macOS 自带的 Bash 3.2、Linux Bash 和 Windows 的 Git Bash，无需安装 PowerShell。

手动格式化整个后端，或只检查格式：

```powershell
mvn -f tools/java-style/pom.xml formatter:format checkstyle:check
mvn -f tools/java-style/pom.xml formatter:validate checkstyle:check
```

手动命令覆盖所有后端模块的 `src/main/java` 和 `src/test/java`，可能产生大范围格式差异。
提交钩子只处理本次暂存文件，因此未迁移的历史源码不会阻止无关提交。该独立工具 POM 不改变后端业务构建。

IntelliJ IDEA 可通过 Eclipse Code Formatter 插件导入格式化规则，通过 CheckStyle-IDEA 插件导入
`checkStyle.xml`（Checkstyle 9.3）。提交钩子使用相同的仓库配置，IDE 内提交也会执行。

## 3、后端分层

**业务代码**（`ruoyi-yoga` / `com.techplant.yoga.*`）—— 照 `course` 包抄，逐层职责与调用规则：

| 层 | 包 | 规则 |
|---|---|---|
| controller | `<模块>.controller` | **只调 service**，不碰 dao/mapper；`@Valid` 做参数校验；只负责装配响应（`AjaxResult` / `TableDataInfo`），**不写业务逻辑** |
| service | `<模块>.service` / `service.impl` | 业务规则、存在性校验、跨模块业务调用、事务边界；**出参一律 VO，DO 不出 service 层**；`IPage` 也不出 service 层 |
| dao | `<模块>.dao` | 数据访问，面向业务语义，屏蔽 mapper 细节；只被 service 调用 |
| mapper | `<模块>.mapper` | MyBatis-Plus `BaseMapper<XxxDO>`；复杂 SQL 写 XML，不用注解堆 SQL |
| domain / dto / vo / query / convert | 同名包 | 对象只用这五种：DO 对应表、DTO 是 service 入参、Query 承载查询条件、VO 是出参、Converter 使用 MapStruct 做转换。**不用 PO / BO / AO** |
| 公共设施 | `common.*` | `response` / `config` / `mybatis` / `log` / `util`，见第 4 节 |

- 中文提示语保持一致；源码注释遵循根目录 `AGENTS.md`，仅说明必要的业务含义、外部约束或调用边界，不引用设计章节号或其他易变定位信息。

## 4、公共能力与接口约定

### 4.1 统一响应（对外契约）

| 场景 | 用什么类 | 结构 |
|---|---|---|
| 单条查询 / 新增 / 修改 / 状态变更 | `com.ruoyi.common.core.domain.AjaxResult` | `{code, msg, data}`，成功 `code = 200`；**`data` 为空时该字段不返回** |
| 列表 / 分页 | `com.ruoyi.common.core.page.TableDataInfo` | `{total, rows, code, msg}`（字段名是 `rows`，**不是 `data.list`**） |
| service 层内部 | `com.techplant.yoga.common.response.PageResult` | 只承载「总记录数 + 当前页数据」；MyBatis-Plus 的 `IPage` **不出 service 层** |

- **HTTP 状态码固定 200，业务结果一律看 `code`** —— 与 RuoYi 管理端既有约定一致（管理端 axios 拦截器只判断一个字段）。
- 业务模块**不要自定义对外响应体**：原先的 `ApiResponse` 已在 R11 中删除（同一个工程里出现两套响应体会直接打架）。
- 反例：controller 直接 `return page.getList()`、或把 `IPage` 返回出去、或为了「好看」包一层 `{success, result}`。
- 范例：`ruoyi-yoga/.../course/controller/CourseController.java`（列表装配 `TableDataInfo`，单条用 `AjaxResult.success`）。

### 4.2 统一异常处理

一句话口径：**业务失败就抛 `ServiceException("提示语", 业务码)`，剩下的交给框架唯一的全局异常处理器**；不要 `catch` 之后自己拼响应，也不要吞异常返回 null。

| 环节 | 落点 | 说明 |
|---|---|---|
| **唯一的处理器** | `ruoyi-framework/.../web/exception/GlobalExceptionHandler.java` | `@RestControllerAdvice`，把所有异常统一转成 `AjaxResult`； |
| 抛业务异常 | `com.ruoyi.common.exception.ServiceException` | `throw new ServiceException("课程不存在或已被删除", 404)`；不带码时默认 500 |
| 401 未登录 / 登录态失效 | `framework/security/handle/AuthenticationEntryPointImpl` | 用 `ServletUtils.renderString` 手工写 `AjaxResult.error(401, ...)`，**HTTP 仍是 200** |
| 403 无权限 | `GlobalExceptionHandler#handleAccessDeniedException` | 固定文案「没有权限，请联系管理员授权」；**管理端业务不做 RBAC**，暂不返回 |
| 参数校验失败 | 同一个处理器里的 `MethodArgumentNotValidException` / `BindException` 分支 | 业务码是 **500（不是 400）**，`msg` 是具体字段提示（如「课程名称不能为空」） |
| 兜底 | `RuntimeException` / `Exception` 分支 | 会把 `e.getMessage()` 直接放进 `msg` —— 所以**不要往异常提示语里塞内部细节**（数据库异常会带出表名与语句片段） |

业务码口径：

| code | 用在 | 本项目的实例 |
|---|---|---|
| 200 | 成功 | 所有接口 |
| 401 | 未登录 / 登录态失效 | 框架返回 |
| 403 | 无权限 | 管理端业务不做 RBAC，暂不返回 |
| 404 | 资源不存在 | 「课程不存在或已被删除」 |
| 409 | 业务冲突 | 名称重复、卡号重复或状态流转非法 |
| 500 | 参数校验失败 / 系统异常 | `@Valid` 校验失败也走这里 |

响应处理约定：

1. **409 的结构化明细放不进 `data`**：`AjaxResult` 只有 `code/msg/data`，而框架处理器不会附带 `data`，所以阻塞明细（几个排班、几条预约）现在是**拼进 `msg`** 的。这是 R11 的已知遗留，要结构化明细就得改框架处理器（会同时影响 `sys_*` 接口）。
2. **`@Valid` 校验失败返回 500**，与直觉里的 400 不一样；要改成 400 同样得动框架处理器 —— **不要顺手改**。

范例：`ruoyi-yoga/.../course/service/impl/CourseServiceImpl.java`；响应转换通过真实 HTTP 接口集成测试验证。

### 4.3 ID 序列化：64 位整数一律返回字符串

- 落点：`com.techplant.yoga.common.config.JacksonConfig`。
- 规则：**只对 `com.techplant.yoga` 包下的返回对象生效**，且只处理**字段名是 `id` 或以 `Id` 结尾**的 `Long`/`long` → 序列化成带引号的字符串（雪花 ID 超出小程序 `Number` 的 53 位安全范围，返回数字会丢精度）。
- 两个**故意**的副作用，别当成 bug：
  - `sys_*` 接口不动（否则 `sys_user.userId` 会变成 `"1"`，管理端存在 `userId === 1` 之类数值比较）；
  - **计数型 Long 不动**：叫 `scheduleCount` / `bookingCount` 这类字段仍返回数字 —— **字段命名直接决定序列化行为**，新写 VO 时留意。

### 4.4 审计字段自动填充

| 能力 | 落点 | 用法 |
|---|---|---|
| 审计字段 | `com.techplant.yoga.common.mybatis.AuditMetaObjectHandler`（MyBatis-Plus `MetaObjectHandler`） | DO 字段上标 `@TableField(fill = FieldFill.INSERT)`（`createBy/createTime`）/ `INSERT_UPDATE`（`updateBy/updateTime`），**不要手写 set**；拿不到登录人时只填时间不填人，不让写操作失败 |
| 取当前登录人 | `com.techplant.yoga.common.util.CurrentUserUtils` | `getUserIdOrNull()` / `getUserIdText()`；**直接用 `SecurityUtils.getUserId()` 未登录会抛异常**，所以才包了这一层 |

### 4.5 链路标识

- `com.techplant.yoga.common.log.TraceIdFilter`：生成/透传 `traceId` 写入 MDC，并回写响应头 **`X-Trace-Id`**。

### 4.6 认证与鉴权

- 管理端复用若依 `sys_user`、JWT 与 `TokenService`，Redis 前缀 `login_tokens:`；业务接口要求登录，不加业务权限串。
- 用户端使用 `t_member` 与 `MemberTokenService`，Redis 前缀 `member_tokens:`；具体放行清单、会话和鉴权流程见[用户端接口详细设计](../详细设计/用户端接口详细设计.md)。
- 若依 `sys_*` 接口继续使用框架权限体系，公开接口按安全配置放行。

### 4.7 事务与跨模块调用

- 事务边界放在 service 层，使用 `@Transactional(rollbackFor = Exception.class)`。
- 跨模块调用对方 Service，不直接读对方的表，不另立 QueryService。
- 使用构造器注入；依赖关系按各模块详细设计定义。

### 4.8 分页

- 业务模块的路径：`Query` 对象（`pageNum`、`pageSize` 必填，页码最小 1，每页 1～100）→ service 用 MP 的 `IPage` 查询 → 转成 `PageResult` → controller 装配 `TableDataInfo`。
- **不要用框架那套 `BaseController.startPage()` + PageHelper** —— 那是 `sys_*` 的写法，两套分页混用会互相干扰。
- 分页必须有**稳定排序**，否则翻页会重复/漏项。
- 兜底：MP 分页插件设了 `maxLimit = 100`。

### 4.9 MyBatis-Plus 的装配方式

工程在 `ruoyi-framework` 里自己声明了 `SqlSessionFactory`，**MyBatis-Plus 的自动配置因此整体退出**，于是：

1. MP 的分页插件与 `MetaObjectHandler` 是 `MyBatisConfig.sqlSessionFactory(...)` 通过 `ObjectProvider` **手工装配**进去的 —— 新加 MP 的 `InnerInterceptor`、或新加 `MetaObjectHandler`，要改的是这里，**不是 `MybatisPlusConfig` 一个类**。
2. 启动类 `RuoYiApplication` 的 `scanBasePackages` **必须显式包含 `com.techplant.yoga`**（业务包不在 `com.ruoyi` 之下），否则业务 controller / service / 配置类**全都不会被注册**，接口 404 且没有任何报错。
3. `application.yml` 的 `mybatis.typeAliasesPackage` 必须同时含 `com.ruoyi.**.domain` 与 `com.techplant.yoga.**.domain`；`mapperLocations` 是 `classpath*:mapper/**/*Mapper.xml`（业务 XML 放 `ruoyi-yoga/src/main/resources/mapper/<模块>/`）。
4. 父 `pom.xml` 把 **`jsqlparser` 锁在 4.6**：PageHelper 5.3.3 需要 `SelectBody`（4.7 起被删），MP 3.5.5 是最后一个用 4.6 的版本。**不要顺手升级 MyBatis-Plus 或 jsqlparser**，升级会让 PageHelper 直接启动报错。
5. `com.techplant.yoga.common.config.MybatisPlusConfig` 只负责 `@MapperScan("com.techplant.yoga.**.mapper")` 与提供分页插件 Bean。

持久化装配的测试要求与运行方式见 [AI Agent测试手册](../系统测试/AI%20Agent测试手册.md)。

### 4.10 其他框架能力（按需取用，别重复实现）

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

### 4.11 已知缺口 / 沿用上游未收敛的行为（**别当成 bug 去"修"**）

- **XSS 过滤器没覆盖业务接口**：`application.yml` 的 `xss.urlPatterns` 是 `/system/*,/monitor/*,/tool/*`，`/admin/**` **不在其中**。是否补齐待定，**不要擅自扩大**（会改变现有请求体行为）。
- **参数校验失败业务码是 500 而不是 400**，`RuntimeException` 分支会把异常 message 原样给前端 —— 都是框架既有行为，改动会波及 `sys_*` 接口。
- **409 没有结构化明细**（明细拼在 `msg` 里），R11 遗留。
- **管理端不做 RBAC**，业务接口只要求登录。
- **`exception` 包不存在**：R11 删掉了自定义异常类，业务侧统一用框架的 `ServiceException`。横切能力的口径以本手册**第 4 节**为准。

## 5、技术栈


| 项 | 版本 / 说明 |
|---|---|
| Spring Boot | **2.5.15** |
| JDK | **1.8**（`<java.version>1.8</java.version>`），不是 17 |
| 构建 | Maven 多模块，`maven-compiler-plugin:3.1`（本机 Maven 3.3.9 即可构建） |
| 安全 | Spring Security + JWT（jjwt 0.9.1，token 存 Redis，key 前缀 `login_tokens:`） |
| ORM | **MyBatis-Plus 3.5.5（业务模块）+ 原生 MyBatis XML Mapper + PageHelper 1.4.7（`sys_*`）共存**：两者共用 framework 里那一个 `SqlSessionFactory`，装配方式与坑见 §4.9 |
| JSON | Jackson（`ruoyi-yoga` 用 `JacksonConfig` 把业务 ID 序列化成字符串，见 §4.3）+ FastJson2（Redis 序列化器、框架内部 JSON） |
| 连接池 | Druid 1.2.28（自带 `/druid/*` 监控台） |
| 存储 | MySQL（`mysql-connector-java`，版本由 Boot 托管）+ Redis（Lettuce） |
| 定时任务 | Quartz，任务配置存 `sys_job` 表 |
| 接口文档 | springfox 3.0.0（OAS 3）。**方法必须标 `@ApiOperation` 才会被扫描进文档**（见 `ruoyi-admin/.../SwaggerConfig.java`） |
| 代码生成 | ruoyi-generator，Velocity 模板在 `ruoyi-generator/src/main/resources/vm`（含 vue3 / v3ts 模板） |

## 6、运行配置


| 文件 | 内容 |
|---|---|
| `ruoyi-admin/src/main/resources/application.yml` | 应用名、端口、`spring.profiles.active: druid`、**Redis 连接**、token 密钥与有效期、MyBatis/PageHelper、swagger、XSS/防盗链 |
| `ruoyi-admin/src/main/resources/application-druid.yml` | **数据源在这里，不在 application.yml**：`master.url/username/password`（连接信息与时区按运行环境配置）、Druid 池参数、监控台账号、`/druid/*` 白名单 |
| `ruoyi-admin/src/main/resources/logback.xml` | 日志级别与输出路径（上游默认 `/home/ruoyi/logs`） |
| `ruoyi-admin/src/main/resources/mybatis/mybatis-config.xml` | MyBatis 全局设置 |

## 7、构建与启动


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
- 启动前置条件：**MySQL 和 Redis 都必须可用**，且 `backend/sql/ry_20260417.sql`、`backend/sql/quartz.sql`、`backend/sql/yoga.sql` 三个脚本都已导入目标库。前两个 SQL 里没有 `CREATE DATABASE`/`use`，必须先自己建库（当前工作区的库名是 `yoga`，见 `application-druid.yml`）再导入；`yoga.sql` 在已有基础库上执行，建 5 张业务表 + `store_region` 字典 16 条 + 业务菜单 2000~2005。
- 业务模块的表的脚本：`backend/sql/yoga.sql`
- 默认端口 8080，默认账号 `admin / admin123`。

测试策略与验证要求统一维护在 [AI Agent测试手册](../系统测试/AI%20Agent测试手册.md)。

## 8、SQL 与菜单

- 业务 SQL 放在 `backend/sql/yoga.sql`；业务菜单使用 `sys_menu`，管理端页面与 API 分别放在 `frontend/pc/src/views/<模块>/`、`frontend/pc/src/api/<模块>/`。
- 门店区域使用字典 `store_region`，对应 `sys_dict_type` 与 `sys_dict_data`；区域取值见门店管理详细设计。
- 若依系统表与种子脚本按框架约定维护。
