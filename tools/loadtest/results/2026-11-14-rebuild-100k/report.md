# 从零重建压测库并用 10 万行验证全流程（issue #58）

测试时间为 2026-11-14 09:03 到 11:14（北京时间）。目的是确认「建 RDS → 改参数 → 接入 → 造数 → 压测 → 清理」能从零复现，并第一次在云上验证 `d0d064f` 的 CLB 暴露流程。被测后端为 `yogalt:backend`（`origin/master` 的 `dfd2712`，不含 #61 的索引）。

## 结论

- RDS 由 `rds_setup.py` 一次建成，RunningParameters 5 项核对通过，全程 765 秒。其间 `CreateDBInstance` 超时一次，脚本按描述查到了实例，没有重复创建。
- CLB 新流程没有出现暴露窗口：白名单开启前，CLB 一直是 inactive，监听也已停止；读回 `AclStatus=on` 之后才启动监听。`verify` 通过。agent 访问日志里的 137 个请求都来自本机出口 IP。
- 首次造数用时 13.8 秒，再跑一次 top-up 用时 5.7 秒，两次各表行数相同，12 项对账都是 0。
- READ、MIXED、A、A-history 在 100/s×2m 下的错误率都是 0，对账都是 0。MIXED 阶梯 200 到 700/s 全部通过，800/s 失败：p95 为 3041 ms，达成率 0.987。
- 每轮都取到了 RDS 指标，SQL 摘要都非空，每轮 25 条。
- 发现的问题都已修复或写入文档：
  - CLB 不指定可用区时创建失败。
  - 新库建表前后端无法启动。
  - 压测机镜像过旧，不支持 `--members history`。

## 环境

资源 ID 都在 2026-11-14 从阿里云 API 现查。

| 项目 | 取值 |
|---|---|
| RDS | `rm-uf608ywe004r04px2`，描述 `yogalt-rebuild-test`，`mysql.x4.large.2c`（`DescribeClassDetails`：4 核，16GB 独享型），MySQL 8.0，高可用版，ESSD 100 GB，按量付费，cn-shanghai-l |
| 白名单与网络 | `default` 组只有 `172.16.0.0/12`（VPC 网段），没有公网地址 |
| RDS 参数（RunningParameters） | `character_set_server=utf8mb4`、`transaction_isolation=REPEATABLE-READ`、`performance_schema=1`、`loose_performance_schema_max_digest_length=1024`、`loose_performance_schema_max_sql_text_length=1024`；默认值：`innodb_buffer_pool_size={DBInstanceClassMemory*3/4}`、`sync_binlog=1`、`innodb_flush_log_at_trx_commit=1`、`long_query_time=1`、`max_connections=8520` |
| 网络 | VPC `vpc-uf6di68weekxptacmx4gf`，交换机 `vsw-uf680x8lci72dwju2vvj7`（yogalt-l），安全组 `sg-uf679eth1u7f32rvwvdk` |
| 后端 | SAE `yogalt-backend`，2 核 4 GB，1 个实例，`yogalt:backend`（`dfd2712`），Druid `maxActive=20` |
| 压测机 | SAE `yogalt-loadgen`，4 核 8 GB。A-history 之前的轮次用旧镜像（已备份为 `yogalt:loadgen-pre3bc3950`）；A-history 用本分支 `d0d064f` 重建的镜像 |
| 入口 | CLB `lb-uf6lt5iawju1ll93apddd`，ACL `acl-uf699yvxl5a41jt4zb5im`，只放行本机出口 IP |
| 预热 | 每个正式场景前跑 MIXED 50/s×2m，不计入结果 |

## 步骤耗时

| 步骤 | 耗时 | 来源 |
|---|---|---|
| 网络与规格核对 | 1 秒 | `logs/rds_setup.log` |
| 创建实例到 Running | 652 秒 | 同上 |
| 账号与库 | 23 秒 | 同上 |
| 改参数并重启一次 | 86 秒 | 同上 |
| `rds_setup.py setup` 合计 | 765 秒 | `rds.json` 的 `setup_seconds` |
| `init_schema`（41 张表） | 6 秒 | `logs/init_schema.log` |
| `point-apps` 后端发布单 | 09:16:53 提交，约 09:35 完成，耽误的原因见下文 | |
| `cloud.py expose` | 74.6 秒（第一次因可用区失败，用时 8.2 秒） | `logs/expose.log`、`logs/expose-attempt1.log` |
| `cloud.py verify` | 约 1 秒 | `logs/verify.log` |
| 首次造数（10 万预约） | 13.8 秒，墙钟 15 秒 | `seed/bulk_seed-1.json` |
| 第二次 top-up | 5.7 秒，墙钟 11 秒 | `seed/bulk_seed-2.json` |
| 压测机重新部署（换镜像） | 87 秒 | 发布单 `bf6557d9-2333-4efe-9de8-baacf69663a4` |

### CreateDBInstance 超时

09:03:51，`CreateDBInstance` 返回 `Client.Timeout exceeded while awaiting headers`，没有拿到实例 ID。脚本每 10 秒按描述查询一次，09:04:12 查到 `rm-uf608ywe004r04px2`，所以没有重试。`DescribeDBInstances` 确认只有这一台的描述是 `yogalt-rebuild-test`。

### CLB 暴露过程

时间相对于 `expose` 开始。

| 时间 | 事件 |
|---|---|
| 0.6 到 1.7 秒 | 建 ACL，加入本机 IP，读回条目只有这一条 |
| 8.3 秒 | 建 CLB |
| 8.9 到 9.3 秒 | CLB 设为 inactive，读回为 inactive |
| 11.9 秒 | SAE BindSlb |
| 18.2 秒 | SAE 自动创建的监听出现：`Status=running`、`AclStatus=off`。此时 CLB 是 inactive，不转发流量 |
| 19.7 秒 | 停止监听 |
| 20.9 秒 | 开白名单（`--DryRun false`） |
| 27.1 到 71.2 秒 | 每次读回都是 `Status=stopped AclStatus=on AclType=white`；69.0 秒绑定发布单完成 |
| 70.8 秒 | 复查 CLB 仍为 inactive |
| 72.9 秒 | 启动监听 |
| 73.4 秒 | CLB 设回 active |
| 74.2 到 74.6 秒 | 复查：`running`、`on`、`white`，ACL 只有本机 IP |

`AclStatus=off` 只出现在 18.2 秒那一次读回中，当时 CLB 处于 inactive。可接收公网流量的状态（CLB active 且监听 running）只在 73.4 秒之后出现，此时白名单已经生效。压测机换镜像重新部署后，CLB 绑定还在，`verify` 再次通过（`logs/verify-after-redeploy.log`），所以不需要重新 expose。

## 数据规模

| 表 | 总行数 | 压测区间 |
|---|---|---|
| `t_reservation` | 127,000 | 127,000（含可消耗池 27,000） |
| `t_member_card_transaction` | 135,459 | 135,459 |
| `t_member` | 5,624 | 5,623 |
| `t_member_card` | 6,127 | 6,124 |
| `t_schedule` | 4,054 | 4,049 |
| `t_store` | 4 | 1 |

第二次 top-up 的「已完成预约」为 100,000，没有新写入历史数据，只重建了可消耗池。两次各表行数完全相同。

## 场景结果

都在 100k 规模，持续 2 分钟，先预热 50/s×2m。p95 和 p99 单位为 ms。RDS CPU 和 IOPS 取 k6 窗口内完整时间桶的值。数据来自 `runs/<名称>.json`、`runs/<名称>.reconcile.json` 和 `sql/<名称>.json`。

| 场景 | 目标速率 | QPS | p95 | p99 | 错误率 | 丢弃迭代 | 对账 | RDS CPU 均值/峰值 % | RDS IOPS 峰值 | 慢日志 | SQL 摘要 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| READ | 100 | 100.0 | 8.6 | 11.8 | 0 | 0 | 0 | 4.7 / 4.7 | 18.7 | 0 | 25 |
| MIXED | 100 | 100.0 | 12.8 | 14.5 | 0 | 0 | 0 | 5.6 / 5.6 | 120.3 | 0 | 25 |
| A | 100 | 100.0 | 7.9 | 12.5 | 0 | 0 | 0 | 3.9 / 3.9 | 493.0 | 0 | 25 |
| A（`--members history`） | 100 | 100.0 | 10.1 | 13.1 | 0 | 0 | 0 | 4.0 / 4.0 | 512.8 | 0 | 25 |
| MIXED 阶梯 | 200 | 200.0 | 13.1 | 15.9 | 0 | 0 | 0 | 12.1 / 12.1 | 211.4 | 0 | 25 |
| | 300 | 300.0 | 13.9 | 18.2 | 0 | 0 | 0 | 19.3 / 19.3 | 299.3 | 0 | 25 |
| | 400 | 400.0 | 14.8 | 20.1 | 0 | 0 | 0 | 28.0 / 28.0 | 422.3 | 0 | 25 |
| | 500 | 500.0 | 16.3 | 23.1 | 0 | 0 | 0 | 37.1 / 37.1 | 522.5 | 0 | 25 |
| | 600 | 600.0 | 18.6 | 28.4 | 0 | 0 | 0 | 46.3 / 46.3 | 642.9 | 0 | 25 |
| | 700 | 699.9 | 21.9 | 36.7 | 0 | 0 | 0 | 49.4 / 55.7 | 744.4 | 0 | 25 |
| | **800** | **779.5** | **3041.5** | **4194.3** | 0 | 1249 | 0 | 60.4 / 63.0 | 785.9 | 0 | 25 |

A-history 选了 2,500 个历史会员，其中 2,499 个有历史预约，每人平均 38.2 条，最多 55 条（`logs/ahistory.log`）。所有轮次结束后，最终对账 12 项都是 0（`logs/final-reconcile-2.log`）。

### 800/s 失败时的瓶颈

数据来自 `runs/mixed-100k-ladder-800.json` 的 `monitor`，括号里是 700/s 时的值。

- Druid 的 20 个连接全部用满。等待连接的线程峰值 779（0），`NotEmptyWaitCount` 增加 182,846 次（972 次）。
- Tomcat 忙线程峰值 800（14），到了线程上限。
- 后端 JVM 进程 CPU 峰值 0.925（0.665），SAE 云监控 CPU 峰值 87.4%（66.85%）。
- RDS CPU 峰值 63.0%，活跃会话峰值 10.4，没有慢日志。

瓶颈在后端（2 核 4 GB，Druid 20 个连接），不在 RDS。这和 #61 加索引后复测的结论一致。

800/s 时总耗时最高的语句是排课列表查询：20,150 次，平均 25.1 ms，平均扫描 1 行。它慢在等待，不在扫描，因为连接池排队把它的耗时也拉长了。另有一条排课查询没有用到索引（`no_index`，8,061 次，平均扫描 4,459 行）。100k 规模下这条查询的影响不大，规模扩大后需要关注，#61 的索引已经覆盖相关的表。

## 发现的问题与处理

| 现象 | 原因 | 处理 |
|---|---|---|
| `CreateLoadBalancer` 报 `The Instance zone id doesn't support.` | 没指定主可用区 | 脚本当场删除了 ACL `acl-uf6tor1zeumox3cre43ru`，没有残留。带 `--zone cn-shanghai-l` 重试后成功。workflow 改为 `LT_CLB_ZONE` 必填，GitHub 变量设为 `cn-shanghai-l` |
| `point-apps` 后后端一直重启，报 `Table 'yoga.sys_config' doesn't exist` | 新库还没建表，同时 Redis 处于停止状态 | 启动 Redis、`init_schema` 建表后，后端发布单完成。README 和 skill 写明顺序：Redis → `init_schema` → `point-apps` |
| `export_tokens.py: error: argument --set: invalid choice: 'history'`，A-history 没有执行 | 压测机镜像构建于 `3bc3950` 之前 | 旧镜像备份为 `yogalt:loadgen-pre3bc3950`（digest `e6f14b4b…`）；用 `git archive d0d064f` 的干净副本重建 `yogalt:loadgen`（digest `3211e762…`），在镜像里确认支持 `history` 后重新部署，补跑了 A-history |
| 本机取公网 IP 失败 | `api.ipify.org` 连接被重置，结果为空，`cloud.py` 拒绝执行 | 改用 `ipinfo.io/ip` 和 `checkip.amazonaws.com` 两个来源互相核对 |

作废的轮次：第一次 A-history 没有执行 k6，没有结果文件。补跑的结果在上表中。

## 清理

清理结果见 PR #63 的说明和本次汇报。全部用 API 核对。

- 停止 SAE 应用 `yogalt-redis`、`yogalt-backend`、`yogalt-loadgen`。
- 删除 CLB 和 ACL。
- 释放 `rm-uf6457mg6ukpee2if`（`yogalt-issue58`）和 `rm-uf608ywe004r04px2`（`yogalt-rebuild-test`）。
- 从 RAM 策略中删除这两台实例。
- 删除 GitHub 变量 `LT_DB_TARGET` 和 `LT_RDS_INSTANCE_ID`。
- 保留交换机 yogalt-l 和安全组，它们不收费。

## 局限

- 结论只适用于这套规格：RDS 4C16G，后端 2C4G 单实例，Druid 20 个连接。
- 数据只有 10 万行，用来验证流程，不用于判断大数据量下的性能。数据量的影响见 [2026-11-13 的报告](../2026-11-13-capacity/report.md)。
- RDS 监控粒度为 1 分钟，2 分钟的窗口内只有 1 到 2 个完整时间桶。

## 文件

- `rds.json`：`rds_setup.py` 的 state 文件，已去掉 ClientToken。
- `seed/`：两次 top-up 的日志和 `bulk_seed.json`。
- `runs/`：每轮的 `result.json`（含 `rds` 和 `sae_cpu`）、k6 摘要与输出、对账。
- `sql/`：每轮的 `performance_schema` 语句摘要。
- `logs/`：`rds_setup`、`expose`、`verify`、`init_schema`、压测主脚本、A-history 补跑、最终对账和 `rds_metrics` 的输出。本机出口 IP 已替换为 `<本机IP>`。
