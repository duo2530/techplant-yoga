# 预约接口 HTTP 层压测

对 `POST /api/reservations` 做 issue #38 定义的三个场景：

- A：稳态。
- B：抢名额。
- C：阶梯找拐点。

k6 和控制脚本运行在云上的压测机里，本机只经 CLB 下发任务、取回结果。2026-11-10 的结果和判定见 [results/2026-11-10/report.md](results/2026-11-10/report.md)。

## 文件

| 文件 | 作用 |
|---|---|
| `reservation.js` | k6 脚本，按 `SCENARIO` 选择 A / B / C，统计错误率和各类 409 |
| `seed.py` | `full` 清空并造数，`reset` 只清预约与流水、恢复余额和占位 |
| `export_tokens.py` | 逐个登录造数会员，写出 `data/tokens.json` |
| `run_scenario.py` | 跑一个场景：记录 MySQL 计数器差值，每 2 秒采样 Druid、Tomcat、JVM、MySQL，结束后对账 |
| `reconcile.py` | 对账 SQL，与 `ReservationConcurrencyTest` 的不变量一致，任一条查到行即退出码 1 |
| `check_target.py`、`mysql_status.py` | 只读，打印目标库地址、版本、binlog 与锁统计 |
| `sql_stats.py` | 清空或导出 `performance_schema` 语句摘要，对写路径查询执行 EXPLAIN |
| `agent.py`、`ctl.py` | 压测机上的白名单任务服务及本机客户端，请求头 `X-LT-Token` 鉴权 |
| `cms_cpu.py` | 按运行起止时间查询云监控里各 SAE 应用的 CPU |
| `Dockerfile.*` | 后端（带 Jolokia）、MySQL（带建表脚本）、压测机（k6 与 Python）三个镜像 |
| `sae.config.example.json` | SAE 环境配置样例，由 `sae-microservice-deploy` 技能的 `sae.py` 读取 |

## 数据与口径

- 造数规模：2500 个会员，每人一张 500 次的团课次数卡。场景 A/C 用 400 节排课，名额 2500；场景 B 另用 1 节 20 人排课。所有 ID 都在 9.1e9 以上，不和业务数据重叠。
- 场景 A/C 的第 k 个请求由会员 `k % 2500` 预约排课 `(k + floor(k/2500)) % 400`。前 100 万个请求里的会员与排课组合互不重复。
- 同一份数据上再跑 A/C 时，要么先 `seed reset`，要么用 `--offset` 接着上一轮的请求序号。否则请求会全部返回 409「您已预约该课程」。场景 C 的阶梯会自动累加 offset。
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
- 项目根 `.env` 里有 `YOGALT_MYSQL_PASSWORD` 和 `YOGALT_AGENT_TOKEN`。
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
docker build -f Dockerfile.loadgen -t $H/yogalt:loadgen .
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
