---
name: verify-reservation-concurrency
description: 用 TLA+ 模型和 TLC 穷举检查预约、排队、取消、补位、人数不足自动取消、停卡与排课状态修改在并发下的行锁与事务协议，确认不超卖、不重复有效预约、返次恰好一次。修改预约服务、排课或会员卡的加锁、事务隔离级别、条件更新，或排课状态流转之后使用，也用于“检查预约并发”“跑一下 TLA+ 模型”。
---

# verify-reservation-concurrency

模型在 `tools/formal/reservation/`。它把预约写事务里的每条 SQL 写成一个动作，再列举一组请求在 InnoDB 行锁下的全部交错顺序，检查每个已提交状态。它不运行 Java 代码，所以只有在模型与代码保持一致时，结论才成立。用法见下文“判断是否需要重跑”。

## 检查的规则

每条规则只针对已提交的数据库状态。

| 规则 | 含义 | 被破坏时的后果 |
|---|---|---|
| `NoOversell` | 每节课的 `booked_persons` 不超过 `max_persons`，且等于已预约加已签到的人数 | 课程超卖，或名额显示与实际占位不符 |
| `NoDuplicateOpen` | 同一会员在同一节课至多一条已预约、已签到或排队中的记录 | 会员重复占位或重复扣次 |
| `RefundExactlyOnce` | 扣过次的预约被取消后恰好有一条返次流水，没扣次的不返；每张卡的余额等于期初减扣次加返次，且不为负 | 会员少返或多返次数，账与流水对不上 |
| `CancelledIsTerminal` | 排课一旦提交为已取消，就不再变回其他状态 | 已取消的课重新开放，取消时没有处理的预约与次数无从追回 |
| `NoLockCycle` | 模型中的行锁等待图无环 | InnoDB 报 1213 死锁并回滚一方，请求失败需要重试 |

`NoLockCycle` 用等待图检查：进程 p 下一步需要的行被 q 持有时，记一条 p 等 q 的边，然后在每个状态检查是否有环。模型另有 `DeadlockVictim` 动作，模拟 InnoDB 回滚环上的一方。所以其他规则检查的是“死锁后受害者整体回滚”的结果，TLC 自身的卡死检查（`CHECK_DEADLOCK`）则确认所有请求最终都能结束。

## 运行

在仓库根目录，用 Git Bash 运行：

```bash
bash tools/formal/reservation/run.sh       # 默认检查与已知发现，约 1 分钟
bash tools/formal/reservation/run.sh all   # 另跑 3 个破坏版本，确认规则能失败
bash tools/formal/reservation/run.sh MutationRR   # 只跑一个配置，打印 TLC 完整输出和反例
```

需要可用的 Docker。脚本在 `eclipse-temurin:17-jre` 容器里运行 TLC v1.8.0；首次运行会把 jar 下载到 `tools/formal/.cache/`，并核对 SHA-256。v1.8.0 在 GitHub 上标为预发布版本。上游重新构建后哈希会变，`run.sh` 会报 SHA 不符并退出 2。这时不要直接改哈希，先在 GitHub release 页面核对新 jar 的 digest，再用新 jar 重跑 `run.sh all`，确认结果不变后，再更新 `run.sh` 中的哈希。每个配置打印一行结果，TLC 原始输出放在 `tools/formal/.cache/out/reservation/<配置>.out`。

当前代码的期望输出：

```text
PASS            QueueRace / RefundRace / AutoCancel / LastSeat / StatusRace / CrossQueue
KNOWN FINDING #36              FindingStatusTerminal
KNOWN FINDING card-lock-cycle  FindingCrossQueueLockCycle
CAUGHT          MutationRR / MutationNoScheduleLock / MutationDuplicate   （只在 all 时出现）
```

退出码 0 表示与期望一致。行首出现 `FAIL` 时退出码为 1，`FAIL no longer breaks` 也在其中，它表示已知发现或破坏版本不再违反规则。退出码 2 表示工具没有跑起来。

## 读结果

每个结果都用 /statechart 读：违反规则时看最短路径的业务描述，通过时看状态图。`views/refund.mjs`（RefundRace 与 MutationRR）和 `views/status.mjs`（StatusRace 与 FindingStatusTerminal）是现成的视图模块，它们把模型变量换成业务用语。其他场景按 /statechart 的说明另写视图。上次的读图结果写在 [README](../../../tools/formal/reservation/README.md)，可以对照。

本机记录（2026-11-10）：/statechart 需要用 Linux 容器里的 Node 22 运行。它读取 SANY XML 的代码与 TLC 1.8.0 的输出格式不兼容，要换用它自己固定的 TLC 版本。这只影响画图，`run.sh` 的检查结果以 TLC 1.8.0 为准。

## 判断真缺陷还是建模错误

违反规则时，先把最短路径逐步对回代码，每一步对应下表中的一个函数：

1. 每一步在代码中都能按这个顺序发生吗？重点看三点：锁定读是否真的带 `FOR UPDATE`，普通读在当前隔离级别下读到的是哪个版本，以及条件更新的 `WHERE` 条件。
2. 有一步做不到时，就是模型错了。常见原因有三类：把两条 SQL 合成了一个动作，漏了一把锁，场景违反了 `ASSUME`。修正模型，再从头跑一遍。
3. 每一步都能发生时，就是真缺陷。最好用集成测试复现，参照 `ReservationServiceImplTest#cancel_queuedWhilePromoted_shouldRefundDeduction` 控制提交顺序的写法。报告时用代码的话描述最短路径，不要放宽规则或场景去让检查通过，也不要擅自修改产品代码。

`run.sh` 报 `error` 时说明 TLC 没有跑完，通常是语法错误、`ASSUME` 不成立或配置写错，原因见对应的 `.out` 文件。

## 判断是否需要重跑

下面任一项发生变化时，都要重跑并对照覆盖表：

- 覆盖表里任一函数的加锁、SQL 顺序、`WHERE` 条件、隔离级别或 `@Transactional` 声明。
- 预约状态、排课状态的取值或允许的流转。
- 新增写 `t_schedule.booked_persons`、`t_schedule.status`、`t_reservation.status`、`t_member_card.remaining_times` 或次数流水的入口。新入口不在模型中，要先补进模型再下结论。

查看自同步提交以来覆盖代码的改动：

```bash
git diff 6df4364 -- backend/ruoyi-yoga/src/main/java/com/techplant/yoga/reservation backend/ruoyi-yoga/src/main/java/com/techplant/yoga/schedule backend/ruoyi-yoga/src/main/java/com/techplant/yoga/membercard backend/ruoyi-yoga/src/main/java/com/techplant/yoga/member/dao
```

## 有意改动时更新模型

1. 找到改动对应的动作（见覆盖表），按新代码修改。`ReservationProtocol.tla` 开头的 `CodeIsolation`、`CodeScheduleLock`、`CodeCapacityGuard` 和 `CodeMemberLock` 记录当前代码的隔离级别与关键锁。只改这些机制时，改这几个常量即可。例如去掉 `ReservationServiceImpl#cancel` 的 `isolation = Isolation.READ_COMMITTED`，对应把 `CodeIsolation` 中的 `cancel` 改为 `"RR"`。这时 `run.sh` 会让 `QueueRace` 和 `RefundRace` 以 `RefundExactlyOnce` 失败。
2. 改动修复了已知发现时，从 `run.sh` 的 `findings` 中移除该项，把规则加进对应场景的默认配置。例如 #36 修复后，把 `CancelledIsTerminal` 加入 `StatusRace.cfg`，并删除 `FindingStatusTerminal.cfg`。
3. 新增写入口时，在模型中新增一组动作和一个场景配置。再加一个破坏版本，证明新规则能失败。
4. 运行 `run.sh all`，确认结果符合预期，再用 /statechart 读一遍变化的场景，更新 README。
5. 确认模型与新提交的代码一致后，更新本文件的同步提交。

## 范围与边界

- **规模**：每个场景 2 到 5 个并发请求，1 到 2 节课，每个会员一张次数卡，每笔 `persons = 1`，每个请求的 requestId 互不相同。超出这些边界的情况没有检查。
- **假设**：开课时间、取消截止时间、停止排队时间等时间窗口都视为满足，卡的门店、课程类型、有效期也都视为匹配。年卡不扣次，不在模型中。
- **不建模间隙锁**：模型只锁 `WHERE` 命中的行。读已提交下的锁定读不加间隙锁，这一点由 `ReservationConcurrencyTest` 的跨课跨人场景（场景 5）实测覆盖。改回可重复读时，模型只能说明返次问题，不能说明间隙锁死锁。
- **未建模的入口**：签到（`ReservationServiceImpl#checkIn`）、会员卡启用、删除排课、会员或卡、HTTP 鉴权、事务提交结果未知时的客户端重试。
- **按行模型化**：`t_member` 只作为锁使用。流水表按 `uk_reservation_type` 简化为“每笔预约一条扣次、一条返次”。

## 已知发现

- **#36 排课状态被覆盖**：取消请求和下架请求都用普通读读到“已上架”。取消请求先提交，下架请求随后无条件写回“待上架”。代码在 `ScheduleServiceImpl#changeStatus` 与 `ScheduleDaoImpl#updateStatus`。
- **跨课补位的卡锁环**：课程1由会员1占位、会员2排队，课程2相反。两人同时取消时，各自先锁自己的卡返次，再补位时去锁对方的卡，形成环。InnoDB 会回滚一方（1213），名额与返次规则不受影响，但请求失败。代码在 `ReservationServiceImpl#refundTimesIfNeeded` 与 `ReservationServiceImpl#promoteQueued`。

## 覆盖表

路径相对 `backend/ruoyi-yoga/src/main/java/com/techplant/yoga/`。

| 模型动作 | 代码 |
|---|---|
| `SubmitPeek` | `reservation/service/impl/ReservationServiceImpl.java#submit` → `reservation/dao/ReservationDaoImpl.java#selectByMemberAndRequestId` |
| `SubmitLockSchedule`、`CancelLockSchedule`、`UnderLockSchedule` | `schedule/service/impl/ScheduleBookingServiceImpl.java#lockForBooking` → `schedule/dao/ScheduleDaoImpl.java#selectByIdForUpdate` |
| `SubmitLockMember` | `member/service/impl/MemberServiceImpl.java#lockForBooking` → `member/dao/MemberDaoImpl.java#selectByIdForUpdate` |
| `SubmitCheckOpen` | `reservation/service/impl/ReservationServiceImpl.java#submit`：`selectByMemberAndRequestIdForUpdate`、`validateSchedule`、`reservation/dao/ReservationDaoImpl.java#selectOpenByMemberAndSchedule`、`remainingPlaces` |
| `SubmitCountQueue` | `reservation/service/impl/ReservationServiceImpl.java#validateQueueable` → `reservation/dao/ReservationDaoImpl.java#countQueuedForUpdate` |
| `SubmitLockCard`、`PromoteLockCard` | `membercard/service/impl/MemberCardServiceImpl.java#lockBookableCard`、`reservation/service/impl/ReservationServiceImpl.java#validateCardLimit`、`reservation/service/impl/ReservationServiceImpl.java#lockCardForPromotion` |
| `SubmitInsert` | `reservation/dao/ReservationDaoImpl.java#insert` |
| `Occupy`（`SubmitOccupy`、`PromoteOccupy`） | `reservation/service/impl/ReservationServiceImpl.java#occupy` → `schedule/dao/ScheduleDaoImpl.java#updateBookedPersons`（`max_persons >= 新值`） |
| `Deduct`（`SubmitDeduct`、`PromoteDeduct`） | `reservation/service/impl/ReservationServiceImpl.java#deductTimesIfNeeded` → `membercard/dao/MemberCardDaoImpl.java#deductTimes`（`remaining_times >= persons`） |
| `InsertLedger`（扣次、返次） | `membercard/service/impl/MemberCardTransactionServiceImpl.java#recordDeduct`、`#recordRefund`，唯一键 `uk_reservation_type`（`backend/sql/yoga.sql`） |
| `CancelPeek` | `reservation/service/impl/ReservationServiceImpl.java#cancel` → `requireOwnedReservation` |
| `CancelLockReservation` | `reservation/service/impl/ReservationServiceImpl.java#cancel` → `requireOwnedReservationForUpdate` 及状态分支 |
| `CancelCancelQueued`、`CancelCancelReserved` | `reservation/dao/ReservationDaoImpl.java#cancel`（按原状态条件更新） |
| `CancelRelease` | `reservation/service/impl/ReservationServiceImpl.java#cancel` → `updateBookingCount(Math.max(b - p, 0))` |
| `RefundReadDeduct` | `reservation/service/impl/ReservationServiceImpl.java#refundTimesIfNeeded` → `membercard/service/impl/MemberCardTransactionServiceImpl.java#deductedTimesOf` |
| `RefundLockCard`、`RefundUpdate` | `membercard/service/impl/MemberCardServiceImpl.java#lockCard`、`membercard/dao/MemberCardDaoImpl.java#refundTimes` |
| `PromoteFindQueued` | `reservation/service/impl/ReservationServiceImpl.java#promoteQueued` → `reservation/dao/ReservationDaoImpl.java#selectFirstQueuedForUpdate` |
| `PromoteMarkFailed`、`PromotePromote` | `reservation/dao/ReservationDaoImpl.java#markQueueFailed`、`reservation/dao/ReservationDaoImpl.java#promote`（条件为排队中） |
| `UnderCloseSchedule` | `schedule/service/impl/ScheduleBookingServiceImpl.java#cancel` → `schedule/dao/ScheduleDaoImpl.java#updateStatus` |
| `UnderScan`、`UnderNext`、`UnderRelease` | `reservation/service/impl/ReservationServiceImpl.java#cancelUnderstaffedSchedule` → `reservation/dao/ReservationDaoImpl.java#selectCancellableByScheduleForUpdate`、`reservation/dao/ReservationDaoImpl.java#cancel`、`updateBookingCount` |
| `StopLockCard`、`StopUpdate` | `membercard/service/impl/MemberCardServiceImpl.java#stop` → `requireCardForUpdate`、`membercard/dao/MemberCardDaoImpl.java#stop`（`stop_time IS NULL`） |
| `StatusRead`、`StatusUpdate` | `schedule/service/impl/ScheduleServiceImpl.java#changeStatus` → `requireSchedule`、`schedule/dao/ScheduleDaoImpl.java#updateStatus`（`WHERE id`） |
| `CodeIsolation` | 各入口的 `@Transactional`：预约服务 5 个写方法为 `READ_COMMITTED`，`membercard/service/impl/MemberCardServiceImpl.java#stop` 与 `schedule/service/impl/ScheduleServiceImpl.java#changeStatus` 使用默认的可重复读 |

## 同步提交

`6df4364`：已确认在这个提交上模型与上表代码一致。确认新提交上的模型仍描述代码后，才把这里改成新提交。
