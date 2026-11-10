# 预约并发协议模型

`ReservationProtocol.tla` 把预约写事务的行锁、隔离级别与提交顺序写成 TLA+ 模型，用 TLC 在小规模场景下穷举全部交错。运行方式、范围与维护流程见 [verify-reservation-concurrency](../../../.agents/skills/verify-reservation-concurrency/SKILL.md)。

| 文件 | 内容 |
|---|---|
| `ReservationProtocol.tla` | 模型：每个请求一个进程，一个动作对应一条 SQL 或一次不可分的代码步骤 |
| `*.cfg` | 每个配置选一个场景、一组破坏开关和要检查的规则 |
| `run.sh` | 下载并校验 TLC，逐个配置运行，每个配置打印一行结果 |
| `views/*.mjs` | 用 /statechart 画图时的视图模块，把模型状态换成业务用语 |

## 配置

| 配置 | 场景 | 期望 |
|---|---|---|
| `QueueRace` | 一节课 1 个名额：会员1取消已预约、会员2取消排队、会员3加入排队、人数不足自动取消、会员2停卡，五个请求并发 | 通过 |
| `RefundRace` | 会员1取消已预约（触发补位），会员2同时取消自己的排队 | 通过 |
| `AutoCancel` | 两人成班的课只有 1 人：自动取消、会员1取消、会员2预约并发 | 通过 |
| `LastSeat` | 两节课各 1 个名额：会员1和会员2抢课程1最后一席，会员1用新 requestId 再约课程1，并约课程2；会员1的卡只有 2 次 | 通过 |
| `CrossQueue` | 课程1由会员1占位、会员2排队，课程2相反；两人同时各自取消 | 名额、重复与返次规则通过 |
| `StatusRace` | 管理员把已上架改为待上架、另一管理员改为已取消、人数不足自动取消，三者并发 | 名额、重复与返次规则通过 |
| `FindingStatusTerminal` | 同 `StatusRace`，检查已取消不可逆 | 已知发现 #36，违反 |
| `FindingCrossQueueLockCycle` | 同 `CrossQueue`，检查行锁等待无环 | 已知发现，违反 |
| `MutationRR` | 同 `RefundRace`，预约写事务改回可重复读 | 必须违反 RefundExactlyOnce |
| `MutationNoScheduleLock` | 同 `LastSeat`，去掉排课 `FOR UPDATE` 与 `max_persons` 条件 | 必须违反 NoOversell |
| `MutationDuplicate` | 同 `LastSeat`，去掉排课 `FOR UPDATE` 与会员锁 | 必须违反 NoDuplicateOpen |

## /statechart 读图结果

以下三份结果由 /statechart 在 2026-11-10 生成，视图模块是 `views/refund.mjs` 和 `views/status.mjs`。图中的状态是业务含义，一个状态可能对应多个模型状态。

### RefundRace，当前代码：通过

TLC 共 64 个状态、86 步，图上 23 个状态、21 条边。规则 NoOversell、NoDuplicateOpen、RefundExactlyOnce、NoLockCycle 与无卡死全部成立。

用业务话说，会员B的排队记录在已提交状态里只有三种走法：

- B 先取得排课锁，取消排队，不扣次也不返次。
- A 先取得排课锁并提交，B 被补位且扣次。B 随后取得排课锁，读到已预约，按已预约取消。B 查到扣次流水并返还，最后状态是“已取消，扣次已返还”。
- 不存在“已取消，扣次未返还”这个状态。

### MutationRR，预约写事务改回可重复读：违反 RefundExactlyOnce

最短路径 25 步。A 的补位动作在下面合并为一步：

1. B 读出自己的预约。这一步是可重复读下的第一次普通读，建立快照，此时 B 还在排队。
2. A 读出自己的预约，取得排课锁，锁定预约，把预约改为已取消，释放名额。
3. A 查到扣次流水，锁卡，加回次数，写返次流水。
4. A 找到排队会员 B，锁 B 的卡，把排队转为已预约，占用名额，扣次，写扣次流水，然后提交。B 的预约已提交为“已补位，已扣次”。
5. B 取得排课锁，用当前读读到已预约，于是把预约改为已取消，释放名额。
6. B 查扣次流水时用的是第 1 步的旧快照，查不到，所以不返次。
7. B 提交，结果是“已取消，扣次未返还”，会员B少了一次课。

对应代码是 `ReservationServiceImpl#cancel` 先调用 `requireOwnedReservation` 做普通读，再由 `refundTimesIfNeeded` 调用 `deductedTimesOf` 做普通读。集成测试 `cancel_queuedWhilePromoted_shouldRefundDeduction` 在可重复读下复现过这一路径。

### FindingStatusTerminal，当前代码：违反 CancelledIsTerminal（#36）

最短路径 6 步：

1. 取消请求读到“已上架”，通过流转校验。
2. 下架请求也读到“已上架”，通过流转校验。
3. 取消请求写入“已取消”并提交。
4. 下架请求写入“待上架”并提交，排课从已取消回到待上架。

对应代码是 `ScheduleServiceImpl#changeStatus`：`requireSchedule` 做普通读，`ScheduleDaoImpl#updateStatus` 的 `WHERE` 只有 id。用 `FOR UPDATE` 读或在 `WHERE` 中带原状态都能消除这一路径。

## 其余反例（TLC 原始路径）

- **MutationNoScheduleLock**：会员1完成预约但尚未提交时，会员2读到人数 0。会员1提交后，会员2写入人数 1 并提交。结果有两条已预约，`booked_persons` 却是 1。这与 [03-并发反例与实现机制](../../../docs/研究/预约正确性/03-并发反例与实现机制.md) 的 H1 一致。
- **MutationDuplicate**：会员1用两个 requestId 约同一节课，两个请求都在对方插入前通过了重复预约检查。会员1因此在同一节课有两条已预约。这里卡只有 2 次，不受卡锁限制。
- **FindingCrossQueueLockCycle**：两个请求分别持有课程1和课程2的排课锁，各自把会员1和会员2的卡锁住并返次。之后两边都去补位，需要锁对方排队会员的卡，于是互相等待。在 InnoDB 中，一方会以 1213 回滚，可重试。名额、返次规则仍成立，因为受害者整体回滚。
