# TechPlant Yoga 后端

瑜伽普拉提预约与运营管理项目的 Java 后端，基于 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)（Spring Boot 2 版本）改造。

- Spring Boot 2.5.15，JDK 1.8，Maven 多模块构建
- 业务模块 `ruoyi-yoga`（包名 `com.techplant.yoga`）：门店、教室、教练、课程、排课、会员、会员卡、预约
- 若依模块：`ruoyi-admin`（启动与配置）、`ruoyi-framework`、`ruoyi-common`、`ruoyi-system`、`ruoyi-quartz`、`ruoyi-generator`
- 初始化脚本位于 `sql/`，增量脚本位于 `sql/migrations/`

## 运行

前置条件：MySQL 与 Redis 可用，且已按顺序导入 `sql/ry_20260417.sql`、`sql/quartz.sql`、`sql/yoga.sql`。

```powershell
cd backend
mvn -DskipTests clean package
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

默认端口 8080，管理端默认账号 `admin / admin123`。数据库与 Redis 配置位于 `ruoyi-admin/src/main/resources/application-druid.yml` 和 `application.yml`。

## 更多文档

- [项目 README](../README.md)：完整的本地启动步骤与常见问题
- [后端项目地图](AGENTS.md)：模块目录、业务包与文档入口
