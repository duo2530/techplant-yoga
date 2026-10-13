# 后端项目地图

作用域：`backend/` 及其子目录。后端基于 RuoYi-Vue，业务模块位于 `ruoyi-yoga`，业务包名为 `com.techplant.yoga`。

## 文档入口

| 文档 | 内容 |
|---|---|
| [AI Agent开发手册](../docs/编码开发/AI%20Agent开发手册.md) | 后端编码、分层、接口约定、技术栈、配置和验证 |
| [项目基础设施](../docs/编码开发/项目基础设施.md) | 公共工具、框架类与基础设施位置 |
| [详细设计](../docs/详细设计/) | 各模块数据、接口、开发架构和运行流程 |

## 模块目录

| 目录 | 内容 |
|---|---|
| `ruoyi-admin/` | 启动类 `RuoYiApplication`、运行配置与日志配置 |
| `ruoyi-framework/` | 安全认证、全局异常、Redis、数据源及 MyBatis 装配 |
| `ruoyi-common/` | 公共响应、工具、注解与常量 |
| `ruoyi-system/` | 若依用户、角色、菜单、字典等系统模块 |
| `ruoyi-yoga/` | 本项目业务模块与公共业务基础设施 |
| `ruoyi-quartz/` | 定时任务 |
| `ruoyi-generator/` | 代码生成器与模板 |
| `sql/` | 系统种子、任务和业务 SQL |
| `bin/`、`ry.bat`、`ry.sh` | 构建与启动脚本 |

## 业务与公共设施

业务源码入口：`ruoyi-yoga/src/main/java/com/techplant/yoga/`。

| 位置 | 内容 |
|---|---|
| `store/`、`classroom/`、`coach/`、`course/`、`schedule/` | 门店、教室、教练、课程、排课 |
| `member/` | 会员与用户端账号：`t_member`、会员 token 登录态（Redis 前缀 `member_tokens:`）、发码／注册／登录／忘记密码／退出（[用户端接口详细设计](../docs/详细设计/用户端接口详细设计.md) §2.2.4～§2.2.8） |
| `booking/` | 既有预约代码；当前交付范围见详细设计 |
| `common/config/` | ID 序列化、MyBatis-Plus 等配置 |
| `common/mybatis/` | 审计字段填充 |
| `common/log/` | 链路标识 |
| `common/response/` | `PageResult` |
| `common/util/` | 登录人等公共工具 |

## 文件入口

| 路径 | 内容 |
|---|---|
| `pom.xml` | Maven 聚合构建 |
| `ruoyi-admin/src/main/resources/application.yml` | 应用、安全、Redis 与 MyBatis 配置 |
| `ruoyi-admin/src/main/resources/application-druid.yml` | 数据源配置 |
| `ruoyi-admin/src/main/resources/logback.xml` | 日志配置 |
| `ruoyi-yoga/src/main/resources/mapper/` | 业务 Mapper XML |
| `ruoyi-yoga/src/test/` | 业务单元测试及 H2 装配测试 |
| `sql/yoga.sql` | 业务表、字典和菜单 SQL |
| `ruoyi-admin/target/ruoyi-admin.jar` | 构建产物 |
