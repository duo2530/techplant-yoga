# AGENTS.md — backend（若依后端）

> 作用域：`backend/` 及其所有子目录。项目背景与业务口径见仓库根目录 `docs/`（需求分析 / 概要设计 / 详细设计 / 原型）。

## 1. 这是什么

一水·瑜伽普拉提项目的后端 Web 服务，当前是 **RuoYi-Vue 3.9.2 原样脚手架**：只有框架代码和 `sys_*` 系统表，**没有任何瑜伽业务代码**（详见第 6 节）。

## 2. 技术栈（已核对 pom）

| 项 | 版本 / 说明 |
|---|---|
| Spring Boot | **2.5.15** |
| JDK | **1.8**（`<java.version>1.8</java.version>`），不是 17 |
| 构建 | Maven 多模块，`maven-compiler-plugin:3.1`（本机 Maven 3.3.9 即可构建） |
| 安全 | Spring Security + JWT（jjwt 0.9.1，token 存 Redis，key 前缀 `login_tokens:`） |
| ORM | **MyBatis（XML Mapper）+ PageHelper 1.4.7**，注意不是 MyBatis-Plus |
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
- `**/src/test` 目录目前不存在，`mvn test` 跑不到任何用例；要加测试得自己建 `src/test/java`（JUnit 5）。
- 启动前置条件：**MySQL 和 Redis 都必须可用**，且 `backend/sql/ry_20260417.sql`、`backend/sql/quartz.sql` 两个脚本都已导入目标库。这两个 SQL 里没有 `CREATE DATABASE`/`use`，必须先自己建库（当前工作区用的是默认库名 `ry-vue`，带横线，命令行/SQL 里记得转义）再导入。
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
- ORM 要用 **MyBatis-Plus**（`IdType.ASSIGN_ID`、`@TableLogic`、`MetaObjectHandler`），当前脚手架还没引入。
- 主键返回前端时**必须序列化成字符串**（Long → String），因为小程序 `Number` 只有 53 位精度。

因此：

1. **业务代码不要写进 `ruoyi-*` 框架模块**，也别改 `sys_*` 表结构。新业务应放进新建的业务模块（如 `ruoyi-yoga` / `techplant-yoga`），`com.ruoyi.*` 保持与上游一致，方便后续比对升级。
2. 引入 MyBatis-Plus 时注意与现有原生 MyBatis XML Mapper 共存（同一 `SqlSessionFactory` 下配置冲突是常见坑），SB2 用 `mybatis-plus-boot-starter`。
3. 管理端菜单/路由由数据库驱动：新增管理端页面必须配套 `sys_menu` 的 SQL（目录 + 菜单 + 按钮权限串）。

## 7. 代码约定（框架内新增代码照这个来）

- 包结构：`com.ruoyi.<模块>.controller | service | service.impl | mapper | domain`；Mapper XML 放 `src/main/resources/mapper/<模块>/*Mapper.xml`（`mybatis.mapperLocations=classpath*:mapper/**/*Mapper.xml`；`typeAliasesPackage=com.ruoyi.**.domain`）。
- Controller 继承 `BaseController`：列表用 `startPage()` + `getDataTable(list)`，单条用 `AjaxResult.success(data)`，增删改用 `toAjax(int rows)`。
- 权限：`@PreAuthorize("@ss.hasPermi('模块:实体:动作')")`；放行接口用 `@Anonymous`（会被 `PermitAllUrlProperties` 收集进 permitAll）。
- 切面注解：`@Log(title=..., businessType=BusinessType.INSERT)`（操作日志）、`@RepeatSubmit`（防重复提交）、`@RateLimiter`（限流）、`@DataScope`（数据权限）、`@DataSource`（多数据源）。
- 业务失败抛 `ServiceException("提示语")`，由全局异常处理器转成统一响应；不要吞异常返回 null。
- 导出用 `@Excel` + `ExcelUtil`；分页参数 `pageNum/pageSize`；时间格式 `yyyy-MM-dd HH:mm:ss`（Jackson 已配 GMT+8）。
- 中文注释、中文提示语，和现有代码风格保持一致。

## 8. 不要做的事

- 不要为了"顺手"改 `sys_*` 表结构或历史 SQL 内容。
- **不要把真实密码提交进仓库**：当前工作区的 `application-druid.yml`（MySQL）和 `application.yml`（Redis）已填入真实云实例地址与密码，只是还没提交。提交前请改成占位符，例如 `password: ${MYSQL_PASSWORD:}` / `${REDIS_PASSWORD:}`，运行和部署时用环境变量或 `--spring.datasource.druid.master.password=xxx` 注入。
- `token.secret` 仍是默认串，`/druid/*` 监控台仍是 `ruoyi/123456` 且白名单为空（任何人可访问）；`ruoyi.profile` 上传目录仍是 `D:/ruoyi/uploadPath`（Windows 硬编码）。上线前必须改/关。
- 云 MySQL 常见连接坑：`useSSL=true` 且没有配证书时可能连不上（自建或普通云库建议 `useSSL=false`）；MySQL 8 默认认证插件是 `caching_sha2_password`，关掉 SSL 后需要追加 `allowPublicKeyRetrieval=true`。
- 不要擅自升级 Spring Boot / JDK 大版本，也不要引入与 SB 2.5.15 不兼容的依赖。

## 9. 改完怎么验证

1. `mvn -f backend/pom.xml -DskipTests clean compile` 通过（最低要求）。
2. 需要实跑时：确认 MySQL/Redis 可连 → `java -jar ruoyi-admin/target/ruoyi-admin.jar` → 日志出现"若依启动成功" → 用 `admin/admin123` 调 `/login` 拿 token。
3. 涉及菜单/页面：登录后 `GET /getRouters` 能看到新菜单。
4. 涉及接口：确认方法上有 `@ApiOperation`，然后看 swagger UI（`http://localhost:8080/swagger-ui/index.html`，被 Security 拦截时参考 `SecurityConfig` 的 permitAll 列表）。
