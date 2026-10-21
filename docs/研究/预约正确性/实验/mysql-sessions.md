# MySQL 8.0 双会话实验

**E-SQL-1至4已实际执行，E-SQL-5仍未执行。** Docker恢复后，使用固定digest的MySQL8.0.46隔离容器和三个持久客户端会话完成实验。下面分步骤部分保留期望说明，实际SQL、输出、锁观测及判定见 [mysql-results.json](mysql-results.json)。实验解释数据库机制；项目Service链路另由34个通过的真实集成测试支持，各自边界不可混同。

自动重现命令：`python 'docs/研究/预约正确性/实验/run_mysql_sessions.py'`。脚本不需要额外Python库，通过Docker内mysql CLI维持独立连接；查询performance_schema确认锁等待，再放行持锁者，不以固定sleep充当等待证据。使用随机专用容器名、不映射主机端口、不挂载已有数据，并在finally回收容器。

实测环境：Docker29.4.2、MySQL8.0.46、REPEATABLE-READ、autocommit=1（事务步骤显式START TRANSACTION）、时区+08:00、innodb_rollback_on_timeout=0；完整sql_mode、固定镜像digest及脚本哈希见结果文件。

## 环境和准备

在仓库根目录运行以下命令，创建独立临时实例，不挂载任何已有数据库目录：

```powershell
docker run --name yoga-research-mysql --rm -d -e MYSQL_ROOT_PASSWORD=research-only -e MYSQL_DATABASE=booking_research mysql:8.0 --default-time-zone=+08:00
docker logs yoga-research-mysql
docker image inspect mysql:8.0 --format '{{json .RepoDigests}}'
docker exec -it yoga-research-mysql mysql -uroot -presearch-only booking_research
```

等待日志显示可接受连接后，在两个PowerShell窗口分别执行最后一条命令，得到A/B两个独立会话；另开C用于锁观测。密码仅是新建隔离实验实例的临时值，不是项目凭据。复验时固定记录下来的镜像digest。准备操作仅在A执行一次：

```sql
SELECT VERSION(),@@transaction_isolation,@@autocommit,@@sql_mode,
       @@innodb_rollback_on_timeout,@@session.time_zone;
CREATE TABLE resource (
 id INT PRIMARY KEY, capacity INT NOT NULL, used INT NOT NULL,
 CHECK(capacity>0 AND used>=0 AND used<=capacity)
) ENGINE=InnoDB;
CREATE TABLE commitment (
 id INT PRIMARY KEY, resource_id INT NOT NULL, persons INT NOT NULL
) ENGINE=InnoDB;
CREATE TABLE intent (
 member_id INT NOT NULL, request_id VARCHAR(36) NOT NULL,
 payload VARCHAR(100) NOT NULL, PRIMARY KEY(member_id,request_id)
) ENGINE=InnoDB;
INSERT INTO resource VALUES(1,1,0),(2,1,0);
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
```

B同样设置RR；每次实验前结束A/B未完成事务。`ROW_COUNT()`必须紧接目标UPDATE执行。实验表是刻意最小化的机制模型，不是生产schema或业务SQL的测试副本。

## E-SQL-1：绝对值UPDATE不能单独防超卖

| 步骤 | A会话 | B会话 |
|---|---|---|
| 1 | `START TRANSACTION; SELECT used FROM resource WHERE id=1;` 预期0 | — |
| 2 | — | `START TRANSACTION; SELECT used FROM resource WHERE id=1;` 预期0 |
| 3 | `INSERT INTO commitment VALUES(11,1,1); UPDATE resource SET used=1 WHERE id=1 AND capacity>=1; COMMIT;` | — |
| 4 | — | `INSERT INTO commitment VALUES(12,1,1); UPDATE resource SET used=1 WHERE id=1 AND capacity>=1; COMMIT;` |

核对（新事务或autocommit下）：

```sql
SELECT used,capacity,(SELECT SUM(persons) FROM commitment WHERE resource_id=1) AS promised
FROM resource WHERE id=1;
```

期望 used=1、capacity=1、promised=2；CHECK没有报错，但真实承诺超卖。测试判定：应捕获这个不一致；若只断言used<=capacity，说明检测器太弱。

## E-SQL-2：条件增量与等待后重新判定

A先重置 `DELETE FROM commitment; UPDATE resource SET used=0 WHERE id=1;`。

| 步骤 | A会话 | B会话 |
|---|---|---|
| 1 | `START TRANSACTION; UPDATE resource SET used=used+1 WHERE id=1 AND used+1<=capacity; SELECT ROW_COUNT();` 预期1，不提交 | — |
| 2 | — | `START TRANSACTION; UPDATE resource SET used=used+1 WHERE id=1 AND used+1<=capacity;` 预期等待 |
| 3 | `INSERT INTO commitment VALUES(21,1,1); COMMIT;` | — |
| 4 | — | UPDATE返回后立即 `SELECT ROW_COUNT();` 预期0；`ROLLBACK;` |

核对预期used=promised=1。只有更新成功者才插commitment；如果后续插入失败，必须把占位一起回滚。反向让A回滚，预期B可获1行成功：说明等待者结果取决于最终可修改状态，不能提前用快照推定失败。

## E-SQL-3：RR旧快照与当前读

先清理intent并重置resource.used=0，两会话均结束旧事务。

| 步骤 | A会话 | B会话 |
|---|---|---|
| 1 | `START TRANSACTION; SELECT * FROM resource WHERE id=1 FOR UPDATE; INSERT INTO intent VALUES(1,'k','p=1'); UPDATE resource SET used=1 WHERE id=1;` 不提交 | — |
| 2 | — | `START TRANSACTION; SELECT * FROM intent WHERE member_id=1 AND request_id='k';` 预期空，建立快照 |
| 3 | — | `SELECT * FROM resource WHERE id=1 FOR UPDATE;` 预期等待 |
| 4 | `COMMIT;` | — |
| 5 | — | resource锁读返回used=1；再普通 `SELECT * FROM intent WHERE member_id=1 AND request_id='k';` 预期仍空 |
| 6 | — | `SELECT * FROM intent WHERE member_id=1 AND request_id='k' FOR UPDATE;` 预期一行；`ROLLBACK;` |

若把容量满判定放在当前读幂等复查前，重试会返回错误结果。需同时记录SELECT输出与隔离级别，不能将RC结果冒充RR实验。

## E-SQL-4：死锁与超时回滚范围

死锁：A `START TRANSACTION; SELECT * FROM resource WHERE id=1 FOR UPDATE;`；B同样锁id=2。A随后锁id=2，等待；B再锁id=1，形成环。期望一个会话收到1213，另一会话继续；不得预设哪个是受害者。获胜者提交、受害者显式确认回滚并在新事务重试完整业务。

超时：A开启事务锁id=1；B执行：

```sql
SET SESSION innodb_lock_wait_timeout=2;
START TRANSACTION;
INSERT INTO commitment VALUES(41,2,1);
UPDATE resource SET used=used+1 WHERE id=1 AND used+1<=capacity;
-- 若等待报1205，先不要提交
SELECT * FROM commitment WHERE id=41;
ROLLBACK;
```

在 `innodb_rollback_on_timeout=OFF` 下，预期B自己的INSERT仍可见，只有失败语句被撤回。显式ROLLBACK后新连接查41应不存在。此实验验证不能把语句超时理解成自动撤销前面全部业务。若实例参数ON，预期结果不同，必须记录参数。

锁面观测C执行：

```sql
SELECT ENGINE_TRANSACTION_ID,THREAD_ID,OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_STATUS,LOCK_DATA
FROM performance_schema.data_locks WHERE OBJECT_SCHEMA='booking_research';
SELECT * FROM performance_schema.data_lock_waits;
SHOW ENGINE INNODB STATUS;
```

采集锁图和错误码；不能用固定sleep声称证明进入了锁等待。

## E-SQL-5：提交结果未知

方案一只测重放：Java服务调用真实create成功，但测试调用包装层故意不交付返回值，记录为UNKNOWN；以同request_id重试，检查四表只变动一次。这可确定覆盖“已提交但响应丢失”，不能覆盖“COMMIT包到达与否未知”。

方案二测真实断链：Testcontainers网络中增加Toxiproxy，JDBC与MySQL走代理；在服务写完、准备提交的可观测阶段断连接。记录服务返回、数据库最终状态、相同意图重放结果。结果允许原事务全提交或全回滚；不允许半状态，重试后只允许一次效果。断链时刻要可追踪；未准确定位COMMIT之前／之后就只能称未知窗口实验。

本次两方案均未执行。最终审计走直连主库新连接，不能走被切断代理或延迟副本。

## 清理与结果模板

完成后关闭A/B/C，`docker stop yoga-research-mysql` 回收本次 `--rm` 容器。只操作本次专用容器，不操作现有实例。

每项实验记录：镜像digest／MySQL版本、隔离级别、sql_mode、回滚参数、会话connection_id、逐步SQL和结果、锁图、最终核对输出、是否符合期望、与项目Service测试的对应关系。已执行结果如下：

| 实验 | 实际结果 | 判定与边界 |
|---|---|---|
| E-SQL-1 | used=1、capacity=1、promised=2，CHECK未阻止 | 成功检测到故意构造的超卖反例；不是生产实现超卖证据 |
| E-SQL-2 | 观测到等待；首事务提交后等待者影响0行，回滚后影响1行；最终used=promised=1 | 两分支符合预期；仅说明条件增量机制 |
| E-SQL-3 | B先读空并等待A；A提交后B普通读仍空，FOR UPDATE读到p=1 | RR快照和当前读差异被复现 |
| E-SQL-4死锁 | 一个会话报1213；受害者先前INSERT消失，获胜者保留并提交 | 符合整事务回滚预期；不证明业务锁协议无死锁 |
| E-SQL-4超时 | 报1205后B自己的INSERT仍存在；ROLLBACK后新连接看不到 | 在rollback_on_timeout=0下符合语句回滚预期 |
| E-SQL-5 | 未执行 | 不宣称已覆盖提交断网或真实Service未知结果 |

运行器保存完整转录和SHOW ENGINE INNODB STATUS中的死锁记录。机制实验未调用生产Service，不替代预约专项；专项通过也不替代提交断链与系统任务恢复实验。
