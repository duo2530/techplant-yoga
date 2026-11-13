# 预约接口 HTTP 层压测与容量压测

本目录有两套压测，共用造数、对账、控制服务和结果格式：

- issue #38 的预约写接口压测：场景 A / B / C，见下文前半部分。
- issue #58 的大数据量容量压测：先用 `bulk_seed.py` 把业务数据补足到指定规模，再用 `capacity.js` 压读接口与混合场景，见「容量压测（issue #58）」一节。

对 `POST /api/reservations` 做 issue #38 定义的三个场景：

- A：稳态。
- B：抢名额。
- C：阶梯找拐点。

k6 和控制脚本运行在云上的压测机里，本机只经 CLB 下发任务、取回结果。2026-11-10 的结果和判定见 [results/2026-11-10/report.md](results/2026-11-10/report.md)。

## 文件

| 文件 | 作用 |
|---|---|
| `reservation.js` | k6 脚本，按 `SCENARIO` 选择 A / B / C，统计错误率和各类 409 |
| `capacity.js` | k6 容量脚本，按 `MIX` 权重混合读接口与预约、取消、排队、签到，按接口打 `ep` 标签 |
| `seed.py` | `full` 清空并造数，`reset` 只清预约与流水、恢复余额和占位 |
| `bulk_seed.py`、`bulk_layout.py` | 容量压测造数：按 profile 把压测 ID 区间的预约补足到目标行数，可续跑，结束后 ANALYZE 与对账 |
| `export_tokens.py` | 逐个登录造数会员；默认写 `data/tokens.json`，`--set bulk` 写 `data/tokens-bulk.json` |
| `run_scenario.py` | 跑一个场景：记录 MySQL 计数器差值，每 2 秒采样 Druid、Tomcat、JVM、MySQL，结束后对账；`READ`、`MIXED` 走 `capacity.js` |
| `compare.py` | 汇总多次运行的 `result.json`，输出规模对比 CSV 与 HTML，可设阈值使退出码非 0 |
| `cloud.py` | 工作流用的云操作：启动 SAE 应用，给压测机绑只放行 runner IP 的临时 CLB，清理 |
| `reconcile.py` | 对账 SQL，与 `ReservationConcurrencyTest` 的不变量一致，任一条查到行即退出码 1 |
| `check_target.py`、`mysql_status.py` | 只读，打印目标库地址、版本、binlog 与锁统计 |
| `init_schema.py` | 在空库上按顺序执行镜像内的 `ry_20260417.sql`、`quartz.sql`、`yoga.sql`；库里已有表时拒绝执行 |
| `sql_stats.py` | 清空或导出 `performance_schema` 语句摘要，对写路径查询执行 EXPLAIN |
| `agent.py`、`ctl.py` | 压测机上的白名单任务服务及本机客户端，请求头 `X-LT-Token` 鉴权 |
| `cms_cpu.py` | 按运行起止时间查询云监控里各 SAE 应用的 CPU |
| `rds_metrics.py` | 按运行起止时间查询 RDS 的 CPU、IOPS、会话数、QPS/TPS 与慢 SQL，以及后端 CPU，写回 `result.json` |
| `Dockerfile.*` | 后端（带 Jolokia）、MySQL（带建表脚本）、压测机（k6 与 Python）三个镜像 |
| `sae.config.example.json` | SAE 环境配置样例，由 `sae-microservice-deploy` 技能的 `sae.py` 读取 |

## 数据与口径

- 造数规模：2500 个会员，每人一张 500 次的团课次数卡。场景 A/C 用 400 节排课，名额 2500；场景 B 另用 1 节 20 人排课。所有 ID 都在 9.1e9 以上，不和业务数据重叠。
- 场景 A/C 的第 k 个请求由会员 `k % 2500` 预约排课 `(k + floor(k/2500)) % 400`。前 100 万个请求里的会员与排课组合互不重复。
- 同一份数据上再跑 A/C 时，要么先 `seed reset`，要么用 `--offset` 接着上一轮的请求序号。否则请求会全部返回 409「您已预约该课程」。场景 C 的阶梯会自动累加 offset。
- 历史会员模式：库内有 `bulk_seed` 数据时，`seed.py full --scoped --history-members N`（`--history-seed` 默认 58）从 `bulk_seed` 门店 0 的历史会员里按种子选 N 个，给每人补发一张 500 次的本店次数卡，并把 A/C 用的排课挂到该门店下。人数会调整到加 1 后与 400 互素，以保持上面的组合不重复。随后执行 `export_tokens --set history` 和 `run_scenario A --members history`。这些会员在 `t_reservation` 里已有历史预约，预约时「同人同课有效预约」查询（`selectOpenByMemberAndSchedule`）会扫描到这些行，用来覆盖 #42。本机 30 万行数据上冒烟：选中 2500 人，其中 2496 人有历史预约，人均 39 条；A 30/s×1m 成功 1800 笔，0 错误，对账 0。不带 `--history-members` 再执行一次 `seed full --scoped`，即恢复为新会员模式。
- 计为错误的响应：
  - HTTP 5xx。
  - 连接失败，即 k6 status 0。
  - 10 秒超时。
  - 响应体 code 500。
- 409 不计为错误，按 msg 分类计数。没有分类的 409 记入 `conflict_other`，并打印到 `k6-stdout.txt`。
- `Innodb_row_lock_*` 和 `Com_commit` 取运行前后 `SHOW GLOBAL STATUS` 的差值。Druid 活跃连接、Tomcat 忙线程和 JVM CPU 取运行期间每 2 秒采样的平均值与最大值。

## 运行

前提：

- 本机有 `aliyun` CLI、docker，以及 `~/.claude/skills/sae-microservice-deploy`。
- SAE 命名空间 `cn-shanghai:yogalt` 的 Secret `yogalt-secrets` 里有 `mysql.root.password`、`spring.datasource.druid.master.password` 和 `agent.token`，挂载到后端与压测机的 `/etc/yogalt-secrets/`。本机需要这些值时，从 Secret 读取后只在进程内使用，不写入文件。
- 下面的命令在 Git Bash 中执行。

```bash
cd tools/loadtest
sae() { python -X utf8 -I "$HOME/.claude/skills/sae-microservice-deploy/scripts/sae.py" --config .local/sae.config.json "$@"; }
cp sae.config.example.json .local/sae.config.json   # 填入 VPC、交换机、安全组、ACR

# 1. 构建镜像。后端用 JDK 8 打包；Jolokia 1.7.2 的 JVM agent jar 放在 .local/
(cd ../../backend && mvn -q -DskipTests clean package)
T=$(python -c "import tempfile;print(tempfile.gettempdir())")/yoga-lt-ctx; mkdir -p "$T/backend" "$T/mysql"
cp ../../backend/ruoyi-admin/target/ruoyi-admin.jar "$T/backend/app.jar"; cp .local/jolokia-jvm-1.7.2.jar "$T/backend/"
cp Dockerfile.backend "$T/backend/Dockerfile"; cp Dockerfile.mysql "$T/mysql/Dockerfile"
cp ../../backend/sql/ry_20260417.sql "$T/mysql/01-ry.sql"; cp ../../backend/sql/quartz.sql "$T/mysql/02-quartz.sql"; cp ../../backend/sql/yoga.sql "$T/mysql/03-yoga.sql"
H=<ACR 公网域名>/<命名空间>
docker build -t $H/yogalt:backend "$T/backend"
docker build --build-arg BASE=$H/default-images-house:mysql-8.0.29 -t $H/yogalt:mysql-8.0.29-init "$T/mysql"
docker build -f Dockerfile.loadgen --build-context sql=../../backend/sql -t $H/yogalt:loadgen .
for t in backend mysql-8.0.29-init loadgen; do docker push $H/yogalt:$t; done

# 2. 建环境并暴露压测机的 8000 端口，CLB 的 ACL 只放行本机出口 IP
sae sync-config && sae create && sae start && sae expose
A=http://<CLB IP>:8000; C="python -X utf8 ctl.py --agent $A run"

# 3. 先确认目标库，确认后把打印的地址作为 --confirm-target
$C check_target
$C seed full --confirm-target <host:port/db> && $C export_tokens

# 4. 三个场景
$C run_scenario A --name A --rate 100 --duration 10m
$C seed reset --confirm-target <host:port/db> && $C run_scenario B --name B --b-members 500 --b-seconds 10
$C seed reset --confirm-target <host:port/db> && $C run_scenario C --name C --rate 20 --step 20 --duration 2m
$C reconcile

# 5. 取回结果。每次运行的文件位于压测机 data/results/<名称>/
python -X utf8 ctl.py --agent $A fetch results/A/result.json .local/results/A/result.json
python -X utf8 cms_cpu.py --app backend=<应用ID> --app mysql=<应用ID> .local/results/A/result.json

# 6. 收尾：停应用、解绑并删除 CLB 与 ACL，确认没有残留
sae cleanup --delete-clb <lb-id> --delete-acl <acl-id>
```

- `--b-seconds 0` 让 500 个请求同时发出，用于补充观察。
- `run_scenario C` 在某一级出现以下任一情况时停止，并把这一级的上一级记为拐点：
  - p95 超过 `--p95-limit-ms`，默认 300。
  - 错误率达到 `--error-limit`，默认 0.001。
  - 实际到达率低于目标的 99%。

## 注意

- `seed`、`reset` 和所有写操作都必须带 `--confirm-target`。参数与实际连接地址不一致时直接退出。
- 压测机的 `data/` 在容器里，重新部署压测机会清空结果和 `tokens.json`。重新部署前先取回结果，部署后重新导出 token。
- Spring Boot 2.5 默认不注册 Tomcat 线程池的 MBean，后端需要 `SERVER_TOMCAT_MBEANREGISTRY_ENABLED=true`。
- VPC 没有 NAT 时，所有镜像都要放进 ACR，并通过 `-vpc` 域名拉取。
- 后端入口会清除 `JAVA_TOOL_OPTIONS`，避免 SAE 注入的 ARMS 探针影响测量。
- 云监控 `acs_serverless` 的 `cpu` 指标只支持 60 秒粒度。

## 容量压测（issue #58）

目标是在不同数据规模下压同一组接口，比较性能怎样随数据量退化。流程是：`bulk_seed` 造数（含对账与 `ANALYZE TABLE`）→ `export_tokens --set bulk` 预热登录 → `run_scenario READ|MIXED` → `compare.py` 汇总。当前测的是不加 #42 索引的现状基线。

`run_scenario` 的 `--warmup-duration`（如 `2m`）让正式运行前先按 `--warmup-rate` 跑一段 MIXED，权重同 `--mix`，用来预热 buffer pool 和连接池，结果不计入。预热参数与 QPS、p95、p99、错误率写入 `result.json` 的 `warmup`。READ / MIXED 的 OFFSET 会跳过预热用掉的可消耗池数据。RDS 重启后不预热直接压，曾出现 p95 超过 8 秒。workflow 的 `warmup` 输入写作 `速率/时长`，默认 `50/2m`，填 `0` 不预热。

### 数据规模与 profile

`--target-rows` 是压测 ID 区间内 `t_reservation` 的目标行数，取值 1～10000000，其他表按 profile 推导。`booking-heavy` 的比例如下：

| 表 | 1000 万预约时 | 规则 |
|---|---|---|
| `t_store` | 50 | 每店 4000 节排课 |
| `t_schedule` | 20 万 | 每节 50 个预约位；过去 180 天 + 未来 14 天，每店 3 间教室、8 名教练、12 门课程，06:00 起每 2 小时一档 |
| `t_member` | 约 25 万 | 每个会员约出现在 100 节排课里，只约本店课程 |
| `t_member_card` | 约 30 万 | 每人一张本店团课次数卡（100～150 次加赠送 0～10 次），20% 另有一张年卡 |
| `t_reservation` | 1000 万 | 已签到约 70%，已取消约 20%，已预约约 7%，排队中 / 排队失败约 2% |
| `t_member_card_transaction` | 约 1090 万 | 占位的预约一条扣次，扣次后取消的再加一条返次 |

所有规模都按同一比例缩放。1 万行时实测状态分布：已签到 70.5%、已取消 20.5%、已预约 7.1%、排队失败 1.8%、排队中 0.1%。

每次运行还会重建一份**可消耗池**，与历史数据分开，避免写场景大量返回 409：

| 内容 | 默认规模 | 供哪个操作使用 |
|---|---|---|
| 新会员与 500 次本店次数卡 | 3000（5 店 × 600） | 所有写操作的发起人 |
| 未来可预约排课 | 每店 40 节，名额等于每店会员数 | `reserve`，会员与排课组合不重复 |
| 已预约、可取消的记录 | 20000 | `cancel`，开课时间都在明天以后 |
| 已预约、可签到的记录 | 5000 | `checkin`，开课时间为造数后 15 分钟、课长 4 小时，造数后约 4 小时内可签到 |
| 满员、可排队的排课 | 2000 节，名额 1、排队上限 3 | `queue` |

ID 区间见 `common.BULK_BASE`：每张表在 8.1e12～8.9e12 之间各占 1e11，前 9e10 是历史数据，后 1e10 是可消耗池。这些区间高于 `seed.py` 的 9.1e9 段，低于业务雪花 ID。脚本只增删这些区间，不使用 `TRUNCATE` 或 `DROP`。会员密码与 `seed.py` 相同；历史会员手机号前缀为 `136`，池会员为 `137`。

### 模式、续跑与幂等

- `top-up`（默认）从库内表 `lt_seed_progress` 记录的位置继续。每批默认 1 万行，单独提交，进度与数据在同一事务内。进程被杀后重跑，会从最后提交的批次继续。目标行数不超过已有行数时，跳过历史数据，不删除任何行。
- `rebuild` 先分块删除压测区间（包括池会员通过接口产生的预约与流水），再从头生成。必须同时传 `--confirm-target` 和 `--confirm-rebuild`，两者都要等于实际连接的 `host:port/db`。如果删除阶段中断，`top-up` 会拒绝执行，需要重跑 `rebuild`。
- 随机种子（`--seed`，默认 58）、profile 和基准日期在首轮写入 `lt_seed_progress`。之后的 `top-up` 必须使用相同的种子和 profile，否则直接退出。同一序号的预约在任何规模下内容都相同，所以从 100 万扩到 1000 万时，已有的 100 万行保持不变。
- 历史排课以首轮造数那天为基准日期。每次运行（包括 `--skip-history`）都会补齐“今天起 14 天”里历史排课没有覆盖的日期：每店每天按历史日均节数生成不带预约的排课，编号由日期和门店决定，用 `INSERT IGNORE` 写入，重复执行不会产生重复行。补齐的排课不计入 `--target-rows`。
- `--rows-per-sec` 限制写入速度（单位为行/秒），避免把 RDS 的 IOPS 打满。
- `agent.py` 上，写库与施压的任务（`seed`、`bulk_seed`、`run_scenario`）互斥，同一时间只能跑一个。

### 本机运行

```bash
cd tools/loadtest
export LT_MYSQL_HOST=127.0.0.1 LT_MYSQL_PORT=3306 LT_MYSQL_PASSWORD=<密码> LT_BASE_URL=http://127.0.0.1:8080
python -X utf8 check_target.py
python -X utf8 bulk_seed.py --target-rows 10000 --confirm-target 127.0.0.1:3306/yoga
python -X utf8 export_tokens.py --set bulk
python -X utf8 run_scenario.py READ  --name read-10k  --rate 30 --duration 1m
python -X utf8 run_scenario.py MIXED --name mixed-10k --rate 30 --duration 1m --mix read=80,reserve=10,cancel=5,queue=3,checkin=2
python -X utf8 compare.py "data/results/*/result.json" --out .local/compare --max-p95-ms 300
```

- 需要本机安装 `k6`，或者在 `Dockerfile.loadgen` 构建的镜像里运行。没有配置 `LT_JOLOKIA_URL` 时，JVM 与 Tomcat 的采样记为错误，不影响压测。
- `export_tokens --set bulk` 登录全部池会员和 300 个历史会员（历史会员用于“我的预约”“我的会员卡”）。登录不计入压测。每次 `bulk_seed` 之后都要重新导出。
- 在同一份池数据上再跑 `MIXED`，要么先执行 `bulk_seed --skip-history` 重建池，要么用 `--offset` 接着上一轮的迭代序号。否则取消和签到会落到已处理过的记录上，而这两个接口是幂等的，会直接返回成功。
- `--read-mix` 调整各读接口的权重。接口名：`schedules`、`schedule_detail`、`today_bookable`、`hot`、`stores`、`coaches_featured`、`coach_detail`、`coach_schedules`、`my_reservations`、`my_cards`。
- `--rate` 超过 `--rate-limit`（默认 2000）时直接退出。

### 指标口径

`result.json` 的 `k6.endpoints.<接口>` 包含：请求数、QPS（请求数 ÷ 时长）、p50 / p95 / p99 / max、`http_error_rate`、`biz_error_rate`，以及按 msg 分类的 409（`conflicts`）。

- `http_error_rate` 统计 HTTP 5xx、连接失败、10 秒超时和响应 code 500。
- `biz_error_rate` 统计响应 code 不是 200 的请求，包括 409。

顶层另外记录以下字段：

- `commit`
- `spec`：`LT_SPEC_*` 环境变量，以及 MySQL 版本、buffer pool 与最大连接数
- `table_counts`：各表总行数与压测区间行数
- `seed`：最近一次造数的参数、各阶段耗时与对账结果
- `args`：运行参数
- `started_at` / `ended_at`
- `reconcile`：压测后的对账结果

后端 JVM、连接池沿用 `run_scenario` 的采样。`rds_metrics.py` 在有云凭证的机器上执行（本机或 runner，压测机上没有凭证），写入：

- `rds.performance`：`DescribeDBInstancePerformance` 的 `cpu_pct`、`mem_pct`、`iops`、`active_sessions`、`total_sessions`、`qps`、`tps`，各含 avg / max / 点数。只计入时间桶整段落在 k6 起止时间内的监控点，压测前后的造数与对账不计入；窗口内没有完整时间桶时记为不可用。
- `rds.slow_log`：`DescribeSlowLogRecords` 的慢 SQL，按语句模板（常量替换为 `?`）汇总次数、总耗时、最大耗时与最大扫描行数，取总耗时前 10。慢日志有数分钟延迟，`--wait-slow-log` 可以先等待。
- `sae_cpu.<名称>`：云监控 `acs_serverless` 的 CPU。

接口失败或没有数据点时，字段为 `{"available": false, "reason": ...}`。`compare.py` 会带上 RDS CPU / IOPS / QPS / 会话数、慢 SQL 条数和后端 CPU 列。

### GitHub Actions：`loadtest-capacity.yml`

只能手动触发：Actions → Loadtest Capacity → Run workflow。绑定 environment `loadtest`，同一时间只允许一个运行（`concurrency: loadtest-capacity`），总超时 6 小时。

| 输入 | 说明 |
|---|---|
| `target_rows` | 1～10000000 |
| `seed_profile` | 目前只有 `booking-heavy` |
| `seed_mode`、`confirm_rebuild` | 选 `rebuild` 时，`confirm_rebuild` 必须等于 `vars.LT_DB_TARGET` |
| `scenario` | `MIXED` 或 `READ` |
| `rate` | 恒定到达率，上限为 `vars.LT_MAX_RATE`（默认 1000） |
| `duration` | 10s～60m |
| `mix` | `MIXED` 的权重 |
| `run_seed`、`run_load_test` | 是否造数、是否压测；只压测时会先重建可消耗池 |

阶段：

1. 校验输入。
2. 启动 SAE 上的 Redis、后端和压测机。RDS 不由工作流启停。
3. 新建公网 CLB 与 ACL，ACL 只放行 runner 出口 IP。压测机 8000 端口在绑定过程中就开启白名单。
4. 经 `ctl.py` 运行 `check_target`，打印的目标必须等于 `LT_DB_TARGET`。
5. 运行 `bulk_seed`，然后 `export_tokens --set bulk`，最后 `run_scenario`。
6. 取回 `bulk_seed.json`、`result.json` 等文件，执行 `rds_metrics.py` 和 `compare.py`，作为 artifact 上传并保留 30 天。
7. `always()` 清理：解绑并删除本次创建的 CLB 与 ACL，停止三个 SAE 应用。

需要在 environment `loadtest` 中配置：

| 类型 | 名称 | 说明 |
|---|---|---|
| secret | `ALIBABA_CLOUD_ACCESS_KEY_ID`、`ALIBABA_CLOUD_ACCESS_KEY_SECRET` | RAM 子账号。权限只给：SAE 启停与绑定 CLB，SLB 的创建、删除与 ACL，CMS 与 RDS 只读 |
| secret | `LT_AGENT_TOKEN` | 压测机 `agent.py` 的口令，与 SAE 密钥中的 `agent.token` 一致 |
| variable | `ALIBABA_CLOUD_REGION_ID` | 如 `cn-shanghai` |
| variable | `LT_DB_TARGET` | 压测机 `check_target` 打印的 `host:port/db` |
| variable | `SAE_REDIS_APP_ID`、`SAE_BACKEND_APP_ID`、`SAE_LOADGEN_APP_ID` | 已创建好的三个 SAE 应用 |
| variable | `LT_RDS_INSTANCE_ID` | 压测 RDS 实例 ID，`rds_metrics.py` 用；不设时 RDS 指标记为不可用 |
| variable（可选） | `LT_MAX_RATE`、`LT_CLB_ZONE`、`LT_MAX_P95_MS`、`LT_MAX_ERROR_RATE` | 速率上限、CLB 主可用区、compare 阈值 |

以下资源需要事先手工准备，工作流不会创建：

- 按量付费的 RDS MySQL 8.0：导入 `ry_20260417.sql`、`quartz.sql` 和 `yoga.sql`，白名单只放 VPC 网段。
- 在 RDS 上建高权限账号和 `yoga` 库（utf8mb4），账号密码使用 SAE Secret 里的 `mysql.root.password`。RDS 默认 `character_set_server=utf8`，在空库时改为 `utf8mb4`（需要重启）。
- 后端与压测机改为连接 RDS 内网地址：`SPRING_DATASOURCE_DRUID_MASTER_URL`、`SPRING_DATASOURCE_DRUID_MASTER_USERNAME` 和 `LT_MYSQL_HOST`、`LT_MYSQL_USER`。
- RDS 只能从 VPC 内访问，建表由压测机执行：`ctl.py run init_schema --confirm-target <host:port/db>`。
- 用本分支代码重新构建 `yogalt:loadgen` 镜像，并带上建表脚本：`docker build -f Dockerfile.loadgen --build-context sql=../../backend/sql -t <ACR>/yogalt:loadgen .`。
- 在 environment `loadtest` 上配置 required reviewers，作为人工确认。

### 成本与容量

- 存储：本机实测每行约占（含二级索引）预约 327 B、流水 184 B、会员 399 B、会员卡 371 B、排课 320 B。按 1000 万预约折算，数据加索引约 5.6 GB，再加上页分裂与 binlog，RDS 磁盘建议不少于 50 GB。
- 造数耗时：本机 docker MySQL 写入 1 万行预约（含流水和余额更新）约 1.5 秒。RDS 受 IOPS 与 `sync_binlog=1` 影响，1000 万行要以首次 100 万行的实测耗时线性推算。工作流超时为 6 小时，超时中断后重跑会续接。
- 费用：按量 RDS、SAE 应用和 CLB 都按时长计费。工作流结束会停止 SAE 应用、删除 CLB，但不会释放 RDS。不用时请手工释放 RDS，数据保留在 RDS 上，下次可以直接 top-up 扩容。
- GitHub-hosted runner 只发控制请求，造数和压测都在 VPC 内的压测机上执行，不占用 runner 资源。

### 故障排查

- `--confirm-target ... 不一致`：压测机连接的库和参数不同。先运行 `check_target` 核对，不要通过改参数绕过。
- `库内已有 seed=... 的数据，与本次参数不一致`：沿用原来的种子和 profile，或者执行 `rebuild`。
- `生成的扣次超过会员卡余额`：profile 参数不自洽。本批已回滚，没有写入任何数据。
- `MIXED` 中 `conflict_checkin_ended` 或 `conflict_checkin_too_early` 很多：可签到的记录已经过期。重新执行 `bulk_seed --skip-history` 和 `export_tokens --set bulk`。
- `conflict_other` 不为 0：`k6-stdout.txt` 中打印了对应的 msg，把它补进 `capacity.js` 的分类表。
- 清理步骤失败：按工作流日志中的 CLB / ACL ID 在控制台手工删除，并确认 SAE 应用已经停止。

### 清理

- 压测数据：执行 `bulk_seed --mode rebuild --target-rows 1 ...`，压测区间只保留 1 行规模的数据；或者直接释放压测 RDS。
- 本机冒烟：`docker compose -p <项目名> down`，并停止本机后端。
