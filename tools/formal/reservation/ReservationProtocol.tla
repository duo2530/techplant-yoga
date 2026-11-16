------------------------- MODULE ReservationProtocol -------------------------
(* 预约写事务的行锁与提交协议。每个请求是一个进程，一个动作对应一条 SQL 或  *)
(* 一次不可分的代码步骤。db 是已提交状态，cur 是含未提交写入的最新版本；    *)
(* 写入某行的事务一直持有该行行锁到提交或回滚，所以提交和回滚按 written 回放。*)
EXTENDS Integers, FiniteSets

CONSTANTS
    Scenario,   \* 并发请求组合，见 Sc
    Mutations   \* 破坏版本，取 MutationNames 的子集；空集表示当前代码

MutationNames == {"RR", "NoScheduleLock", "NoCapacityGuard", "NoMemberLock"}
ASSUME Mutations \subseteq MutationNames

(* 与当前代码一致的取值。代码有意改变这些机制时改这里，并同步 SKILL.md 的覆盖记录。 *)
(* 各事务入口的隔离级别："RC" 为 READ_COMMITTED，"RR" 为未声明时 MySQL 默认的 REPEATABLE_READ *)
CodeIsolation == [create |-> "RC",         \* ReservationServiceImpl#create
                  queue |-> "RC",          \* ReservationServiceImpl#joinQueue
                  cancel |-> "RC",         \* ReservationServiceImpl#cancel
                  understaffed |-> "RC",   \* ReservationServiceImpl#cancelUnderstaffedSchedule
                  stop |-> "RR",           \* MemberCardServiceImpl#stop
                  status |-> "RR"]         \* ScheduleServiceImpl#changeStatus
CodeScheduleLock == TRUE   \* ScheduleDaoImpl#selectByIdForUpdate 带 FOR UPDATE
CodeCapacityGuard == TRUE  \* ScheduleDaoImpl#updateBookedPersons 带 max_persons >= 新值
CodeMemberLock == TRUE     \* ReservationServiceImpl#submit 调用 memberService.lockForBooking

ReservationOps == {"create", "queue", "cancel", "understaffed"}
ScheduleLock == CodeScheduleLock /\ "NoScheduleLock" \notin Mutations
CapacityGuard == CodeCapacityGuard /\ "NoCapacityGuard" \notin Mutations
MemberLock == CodeMemberLock /\ "NoMemberLock" \notin Mutations

Rq(op, m, s, r, to) == [op |-> op, m |-> m, s |-> s, r |-> r, to |-> to]
Rs(m, s, st, ded) == [m |-> m, s |-> s, st |-> st, ded |-> ded]

(* 会员 i 只有一张次数卡，卡号也记为 i；每笔预约 persons = 1，每个请求的 requestId 互不相同。 *)
Sc ==
  CASE Scenario = "QueueRace" ->
        [max |-> <<1>>, min |-> <<1>>, qlimit |-> <<2>>, rem |-> <<1, 1, 1>>,
         res |-> <<Rs(1, 1, "RESERVED", TRUE), Rs(2, 1, "QUEUED", FALSE), Rs(3, 1, "NONE", FALSE)>>,
         reqs |-> <<Rq("cancel", 1, 1, 1, ""), Rq("cancel", 2, 1, 2, ""), Rq("queue", 3, 1, 3, ""),
                    Rq("understaffed", 0, 1, 0, ""), Rq("stop", 2, 0, 0, "")>>]
    [] Scenario = "RefundRace" ->
        [max |-> <<1>>, min |-> <<1>>, qlimit |-> <<1>>, rem |-> <<1, 1>>,
         res |-> <<Rs(1, 1, "RESERVED", TRUE), Rs(2, 1, "QUEUED", FALSE)>>,
         reqs |-> <<Rq("cancel", 1, 1, 1, ""), Rq("cancel", 2, 1, 2, "")>>]
    [] Scenario = "AutoCancel" ->
        [max |-> <<2>>, min |-> <<2>>, qlimit |-> <<1>>, rem |-> <<1, 1>>,
         res |-> <<Rs(1, 1, "RESERVED", TRUE), Rs(2, 1, "NONE", FALSE)>>,
         reqs |-> <<Rq("understaffed", 0, 1, 0, ""), Rq("cancel", 1, 1, 1, ""),
                    Rq("create", 2, 1, 2, "")>>]
    [] Scenario = "LastSeat" ->
        [max |-> <<1, 1>>, min |-> <<1, 1>>, qlimit |-> <<1, 1>>, rem |-> <<2, 1>>,
         res |-> <<Rs(1, 1, "NONE", FALSE), Rs(2, 1, "NONE", FALSE),
                   Rs(1, 1, "NONE", FALSE), Rs(1, 2, "NONE", FALSE)>>,
         reqs |-> <<Rq("create", 1, 1, 1, ""), Rq("create", 2, 1, 2, ""),
                    Rq("create", 1, 1, 3, ""), Rq("create", 1, 2, 4, "")>>]
    [] Scenario = "CrossQueue" ->
        [max |-> <<1, 1>>, min |-> <<1, 1>>, qlimit |-> <<1, 1>>, rem |-> <<1, 1>>,
         res |-> <<Rs(1, 1, "RESERVED", TRUE), Rs(2, 1, "QUEUED", FALSE),
                   Rs(2, 2, "RESERVED", TRUE), Rs(1, 2, "QUEUED", FALSE)>>,
         reqs |-> <<Rq("cancel", 1, 1, 1, ""), Rq("cancel", 2, 2, 3, "")>>]
    [] Scenario = "StatusRace" ->
        [max |-> <<1>>, min |-> <<1>>, qlimit |-> <<1>>, rem |-> <<1>>,
         res |-> <<Rs(1, 1, "NONE", FALSE)>>,
         reqs |-> <<Rq("status", 0, 1, 0, "PENDING"), Rq("status", 0, 1, 0, "CANCELLED"),
                    Rq("understaffed", 0, 1, 0, "")>>]

Scheds == DOMAIN Sc.max
Members == DOMAIN Sc.rem
ResIds == DOMAIN Sc.res
Procs == DOMAIN Sc.reqs
Req(p) == Sc.reqs[p]
M(p) == Req(p).m
S(p) == Req(p).s
R(p) == Req(p).r
Max(s) == Sc.max[s]
ResMember(r) == Sc.res[r].m
ResSched(r) == Sc.res[r].s

ASSUME \A p \in Procs : Req(p).op \in {"create", "queue", "cancel"} =>
            S(p) = ResSched(R(p)) /\ M(p) = ResMember(R(p))
ASSUME \A p \in Procs : Req(p).op \in {"create", "queue"} => Sc.res[R(p)].st = "NONE"
ASSUME \A p, q \in Procs : p # q /\ {Req(p).op, Req(q).op} \subseteq {"create", "queue"} => R(p) # R(q)

Seated == {"RESERVED", "CHECKED_IN"}                 \* ReservationStatusEnum.SEAT_CODES
OpenStatus == {"RESERVED", "CHECKED_IN", "QUEUED"}   \* ReservationStatusEnum.OPEN_CODES

SRow(s) == <<"S", s>>   \* t_schedule：status 与 booked_persons
MRow(m) == <<"M", m>>   \* t_member，只用于加锁
RRow(r) == <<"R", r>>   \* t_reservation.status；"NONE" 表示尚未插入
CRow(m) == <<"C", m>>   \* t_member_card：remaining_times 与是否停卡
DRow(r) == <<"D", r>>   \* t_member_card_transaction 扣次行，uk_reservation_type
FRow(r) == <<"F", r>>   \* t_member_card_transaction 返次行，uk_reservation_type

Rows == {SRow(s) : s \in Scheds} \cup {MRow(m) : m \in Members} \cup {CRow(m) : m \in Members}
        \cup {RRow(r) : r \in ResIds} \cup {DRow(r) : r \in ResIds} \cup {FRow(r) : r \in ResIds}

CountRes(P(_)) == Cardinality({r \in ResIds : P(r)})
InitBooked(s) == CountRes(LAMBDA r : ResSched(r) = s /\ Sc.res[r].st \in Seated)
Purchased(m) == Sc.rem[m] + CountRes(LAMBDA r : ResMember(r) = m /\ Sc.res[r].ded)

InitDb == [x \in Rows |->
             CASE x[1] = "S" -> [st |-> "PUBLISHED", bk |-> InitBooked(x[2])]
               [] x[1] = "M" -> "row"
               [] x[1] = "R" -> Sc.res[x[2]].st
               [] x[1] = "C" -> [rem |-> Sc.rem[x[2]], stop |-> FALSE]
               [] x[1] = "D" -> Sc.res[x[2]].ded
               [] x[1] = "F" -> FALSE]

NoView == [open |-> FALSE, rows |-> InitDb]
Ctx0 == [sb |-> 0, ss |-> "NONE", tgt |-> 0, rel |-> 0, todo |-> {}, ret |-> "NONE"]

Iso(p) == IF "RR" \in Mutations /\ Req(p).op \in ReservationOps THEN "RR" ELSE CodeIsolation[Req(p).op]

StartPc(op) == CASE op \in {"create", "queue"} -> "submit_peek"
                 [] op = "cancel" -> "cancel_peek"
                 [] op = "understaffed" -> "under_lockSchedule"
                 [] op = "stop" -> "stop_lockCard"
                 [] op = "status" -> "status_read"

SetMin(T) == CHOOSE x \in T : \A y \in T : x <= y

VARIABLES db, cur, owner, written, snap, pc, ctx, cancelledOnce
vars == <<db, cur, owner, written, snap, pc, ctx, cancelledOnce>>

C(p) == ctx[p]

(* 一致性读：读已提交看最新已提交版本；可重复读看首次一致性读建立的快照；都能看到本事务自己的写入。 *)
View(p) == IF snap[p].open THEN snap[p].rows ELSE db
PlainRead(p, x) == IF x \in written[p] THEN cur[x] ELSE View(p)[x]
OpenView(p) == IF Iso(p) = "RR" /\ ~snap[p].open
               THEN [snap EXCEPT ![p] = [open |-> TRUE, rows |-> db]]
               ELSE snap

(* 锁定读与 UPDATE 在所需行锁空闲或已归自己时执行，读到 cur；只锁满足 WHERE 的行，不建模间隙锁。 *)
SchedRows(s) == {RRow(r) : r \in {r \in ResIds : ResSched(r) = s /\ cur[RRow(r)] # "NONE"}}
QueuedIn(s) == {r \in ResIds : ResSched(r) = s /\ cur[RRow(r)] = "QUEUED"}
MemberSchedRows(p) == {RRow(r) : r \in {r \in ResIds : ResMember(r) = M(p) /\ ResSched(r) = S(p)
                                                        /\ cur[RRow(r)] # "NONE"}}
OpenOf(p) == {r \in ResIds : ResMember(r) = M(p) /\ ResSched(r) = S(p) /\ cur[RRow(r)] \in OpenStatus}
SchedRead(p) == IF ScheduleLock THEN cur[SRow(S(p))] ELSE PlainRead(p, SRow(S(p)))
Allowed(a, b) == <<a, b>> \in {<<"PENDING", "PUBLISHED">>, <<"PUBLISHED", "PENDING">>,
                               <<"PUBLISHED", "CANCELLED">>}

(* 每个程序位置下一步需要的行锁；被他人持有时该步不能执行，即在等锁。 *)
Needs(p) ==
  LET t == C(p).tgt IN
  CASE pc[p] \in {"submit_lockSchedule", "cancel_lockSchedule", "under_lockSchedule"} ->
          IF ScheduleLock THEN {SRow(S(p))} ELSE {}
    [] pc[p] = "submit_lockMember" -> {MRow(M(p))}
    [] pc[p] = "submit_checkOpen" -> IF C(p).ss = "PUBLISHED" THEN MemberSchedRows(p) ELSE {}
    [] pc[p] \in {"submit_countQueue", "under_scan"} -> SchedRows(S(p))
    [] pc[p] \in {"submit_lockCard", "submit_deduct", "stop_lockCard", "stop_update"} -> {CRow(M(p))}
    [] pc[p] \in {"submit_insert", "cancel_lockReservation", "cancel_cancelQueued",
                  "cancel_cancelReserved"} -> {RRow(R(p))}
    [] pc[p] \in {"submit_occupy", "promote_occupy", "cancel_release", "under_closeSchedule",
                  "under_release", "status_update"} -> {SRow(S(p))}
    [] pc[p] = "submit_ledger" -> {DRow(R(p))}
    [] pc[p] \in {"refund_lockCard", "refund_update", "promote_lockCard", "promote_deduct"} ->
          {CRow(ResMember(t))}
    [] pc[p] = "refund_ledger" -> {FRow(t)}
    [] pc[p] = "promote_findQueued" -> IF Max(S(p)) > C(p).sb THEN SchedRows(S(p)) ELSE {}
    [] pc[p] \in {"promote_markFailed", "promote_promote"} -> {RRow(t)}
    [] pc[p] = "promote_ledger" -> {DRow(t)}
    [] pc[p] = "under_next" -> IF C(p).todo = {} THEN {} ELSE {RRow(SetMin(C(p).todo))}
    [] OTHER -> {}

(* 等锁图：p 下一步需要的行被 q 持有时 p 等 q；p 在环上即行锁死锁 *)
WaitsFor == {e \in Procs \X Procs : e[1] # e[2] /\ \E x \in Needs(e[1]) : owner[x] = e[2]}
RECURSIVE Reach(_, _, _)
Reach(W, E, k) == IF k = 0 THEN E
                  ELSE Reach(W, E \cup {<<a, c>> \in Procs \X Procs : \E b \in Procs :
                                           <<a, b>> \in E /\ <<b, c>> \in W}, k - 1)
InDeadlock(p) == LET W == WaitsFor IN W # {} /\ <<p, p>> \in Reach(W, W, Cardinality(Procs))

At(p, l) == pc[p] = l /\ \A x \in Needs(p) : owner[x] \in {0, p}
Goto(p, l) == pc' = [pc EXCEPT ![p] = l]
SetCtx(p, c) == ctx' = [ctx EXCEPT ![p] = c]
Grab(p, X) == owner' = [x \in Rows |-> IF x \in X THEN p ELSE owner[x]]
Put(p, x, v) == /\ cur' = [cur EXCEPT ![x] = v]
                /\ written' = [written EXCEPT ![p] = @ \cup {x}]
                /\ Grab(p, {x})
Fail(p) == Goto(p, "abort") /\ UNCHANGED <<cur, written, owner>>
LockSchedule(p) ==
    /\ IF ScheduleLock THEN Grab(p, {SRow(S(p))}) /\ UNCHANGED snap
                       ELSE snap' = OpenView(p) /\ UNCHANGED owner
    /\ SetCtx(p, [C(p) EXCEPT !.sb = SchedRead(p).bk, !.ss = SchedRead(p).st])

Init ==
    /\ db = InitDb
    /\ cur = InitDb
    /\ owner = [x \in Rows |-> 0]
    /\ written = [p \in Procs |-> {}]
    /\ snap = [p \in Procs |-> NoView]
    /\ pc = [p \in Procs |-> StartPc(Req(p).op)]
    /\ ctx = [p \in Procs |-> Ctx0]
    /\ cancelledOnce = {}

---------------------------------------------------------------------------
(* ReservationServiceImpl#submit，create 与 joinQueue 共用 *)

SubmitPeek(p) ==   \* reservationDao.selectByMemberAndRequestId，普通读
    /\ At(p, "submit_peek")
    /\ snap' = OpenView(p)
    /\ Goto(p, IF PlainRead(p, RRow(R(p))) = "NONE" THEN "submit_lockSchedule" ELSE "commit")
    /\ UNCHANGED <<db, cur, owner, written, ctx, cancelledOnce>>

SubmitLockSchedule(p) ==   \* scheduleBookingService.lockForBooking
    /\ At(p, "submit_lockSchedule")
    /\ LockSchedule(p)
    /\ Goto(p, IF MemberLock THEN "submit_lockMember" ELSE "submit_checkOpen")
    /\ UNCHANGED <<db, cur, written, cancelledOnce>>

SubmitLockMember(p) ==   \* memberService.lockForBooking
    /\ At(p, "submit_lockMember")
    /\ Grab(p, {MRow(M(p))})
    /\ Goto(p, "submit_checkOpen")
    /\ UNCHANGED <<db, cur, written, snap, ctx, cancelledOnce>>

SubmitCheckOpen(p) ==   \* validateSchedule、selectOpenByMemberAndSchedule、remainingPlaces
    LET open == OpenOf(p)
        free == Max(S(p)) - C(p).sb
    IN
    /\ At(p, "submit_checkOpen")
    /\ IF C(p).ss = "PUBLISHED" THEN Grab(p, {RRow(r) : r \in open}) ELSE UNCHANGED owner
    /\ Goto(p, CASE C(p).ss # "PUBLISHED" \/ open # {} -> "abort"
                 [] Req(p).op = "queue" ->
                      IF free > 0 \/ Sc.qlimit[S(p)] <= 0 THEN "abort" ELSE "submit_countQueue"
                 [] OTHER -> IF free < 1 THEN "abort" ELSE "submit_lockCard")
    /\ UNCHANGED <<db, cur, written, snap, ctx, cancelledOnce>>

SubmitCountQueue(p) ==   \* validateQueueable：reservationDao.countQueuedForUpdate
    LET q == QueuedIn(S(p)) IN
    /\ At(p, "submit_countQueue")
    /\ Grab(p, {RRow(r) : r \in q})
    /\ Goto(p, IF Cardinality(q) >= Sc.qlimit[S(p)] THEN "abort" ELSE "submit_lockCard")
    /\ UNCHANGED <<db, cur, written, snap, ctx, cancelledOnce>>

SubmitLockCard(p) ==   \* lockBookableCard、validateCardLimit
    LET card == cur[CRow(M(p))] IN
    /\ At(p, "submit_lockCard")
    /\ Grab(p, {CRow(M(p))})
    /\ Goto(p, IF card.stop \/ card.rem < 1 THEN "abort" ELSE "submit_insert")
    /\ UNCHANGED <<db, cur, written, snap, ctx, cancelledOnce>>

SubmitInsert(p) ==   \* reservationDao.insert
    /\ At(p, "submit_insert")
    /\ Put(p, RRow(R(p)), IF Req(p).op = "create" THEN "RESERVED" ELSE "QUEUED")
    /\ Goto(p, IF Req(p).op = "create" THEN "submit_occupy" ELSE "commit")
    /\ UNCHANGED <<db, snap, ctx, cancelledOnce>>

(* occupy：scheduleBookingService.updateBookingCount 写入绝对值 读到的人数 + 1 *)
Occupy(p, from, to) ==
    LET v == C(p).sb + 1 IN
    /\ At(p, from)
    /\ IF CapacityGuard /\ Max(S(p)) < v
       THEN Fail(p) /\ UNCHANGED ctx
       ELSE /\ Put(p, SRow(S(p)), [cur[SRow(S(p))] EXCEPT !.bk = v])
            /\ SetCtx(p, [C(p) EXCEPT !.sb = v])
            /\ Goto(p, to)
    /\ UNCHANGED <<db, snap, cancelledOnce>>

(* deductTimesIfNeeded：MemberCardDaoImpl#deductTimes 条件为 remaining_times >= persons *)
Deduct(p, from, m, to) ==
    LET card == cur[CRow(m)] IN
    /\ At(p, from)
    /\ IF card.rem >= 1 THEN Put(p, CRow(m), [card EXCEPT !.rem = @ - 1]) /\ Goto(p, to)
                        ELSE Fail(p)
    /\ UNCHANGED <<db, snap, ctx, cancelledOnce>>

(* 流水插入；uk_reservation_type 冲突抛 DuplicateKeyException，整个事务回滚 *)
InsertLedger(p, from, x, to) ==
    /\ At(p, from)
    /\ IF cur[x] THEN Fail(p) ELSE Put(p, x, TRUE) /\ Goto(p, to)
    /\ UNCHANGED <<db, snap, ctx, cancelledOnce>>

(* 预约状态的条件更新：reservationDao.cancel / promote / markQueueFailed，影响 0 行时抛 409 *)
Transit(p, from, r, a, b, to) ==
    /\ At(p, from)
    /\ IF cur[RRow(r)] = a THEN Put(p, RRow(r), b) /\ Goto(p, to) ELSE Fail(p)
    /\ UNCHANGED <<db, snap, ctx, cancelledOnce>>

SubmitOccupy(p) == Occupy(p, "submit_occupy", "submit_deduct")
SubmitDeduct(p) == Deduct(p, "submit_deduct", M(p), "submit_ledger")
SubmitLedger(p) == InsertLedger(p, "submit_ledger", DRow(R(p)), "commit")

---------------------------------------------------------------------------
(* ReservationServiceImpl#cancel *)

CancelPeek(p) ==   \* requireOwnedReservation：reservationDao.selectById，普通读
    /\ At(p, "cancel_peek")
    /\ snap' = OpenView(p)
    /\ Goto(p, IF PlainRead(p, RRow(R(p))) = "NONE" THEN "abort" ELSE "cancel_lockSchedule")
    /\ UNCHANGED <<db, cur, owner, written, ctx, cancelledOnce>>

CancelLockSchedule(p) ==   \* scheduleBookingService.lockForBooking
    /\ At(p, "cancel_lockSchedule")
    /\ LockSchedule(p)
    /\ Goto(p, "cancel_lockReservation")
    /\ UNCHANGED <<db, cur, written, cancelledOnce>>

CancelLockReservation(p) ==   \* requireOwnedReservationForUpdate 及其后的状态判断
    LET st == cur[RRow(R(p))] IN
    /\ At(p, "cancel_lockReservation")
    /\ Grab(p, {RRow(R(p))})
    /\ Goto(p, CASE st = "CANCELLED" -> "commit"
                 [] st \notin {"RESERVED", "QUEUED"} \/ C(p).ss # "PUBLISHED" -> "abort"
                 [] st = "QUEUED" -> "cancel_cancelQueued"
                 [] OTHER -> "cancel_cancelReserved")
    /\ UNCHANGED <<db, cur, written, snap, ctx, cancelledOnce>>

CancelCancelQueued(p) == Transit(p, "cancel_cancelQueued", R(p), "QUEUED", "CANCELLED", "commit")
CancelCancelReserved(p) ==
    Transit(p, "cancel_cancelReserved", R(p), "RESERVED", "CANCELLED", "cancel_release")

CancelRelease(p) ==   \* updateBookingCount(Math.max(bookedPersons - persons, 0))
    LET v == IF C(p).sb > 1 THEN C(p).sb - 1 ELSE 0 IN
    /\ At(p, "cancel_release")
    /\ Put(p, SRow(S(p)), [cur[SRow(S(p))] EXCEPT !.bk = v])
    /\ SetCtx(p, [C(p) EXCEPT !.sb = v, !.tgt = R(p), !.ret = "cancel"])
    /\ Goto(p, "refund_readDeduct")
    /\ UNCHANGED <<db, snap, cancelledOnce>>

---------------------------------------------------------------------------
(* ReservationServiceImpl#refundTimesIfNeeded，返回调用方 ret *)

AfterRefund(p) == IF C(p).ret = "cancel" THEN "promote_findQueued" ELSE "under_next"

RefundReadDeduct(p) ==   \* MemberCardTransactionServiceImpl#deductedTimesOf，普通读
    /\ At(p, "refund_readDeduct")
    /\ snap' = OpenView(p)
    /\ Goto(p, IF PlainRead(p, DRow(C(p).tgt)) THEN "refund_lockCard" ELSE AfterRefund(p))
    /\ UNCHANGED <<db, cur, owner, written, ctx, cancelledOnce>>

RefundLockCard(p) ==   \* MemberCardServiceImpl#lockCard，不校验停卡
    /\ At(p, "refund_lockCard")
    /\ Grab(p, {CRow(ResMember(C(p).tgt))})
    /\ Goto(p, "refund_update")
    /\ UNCHANGED <<db, cur, written, snap, ctx, cancelledOnce>>

RefundUpdate(p) ==   \* MemberCardDaoImpl#refundTimes，无条件加回
    LET x == CRow(ResMember(C(p).tgt)) IN
    /\ At(p, "refund_update")
    /\ Put(p, x, [cur[x] EXCEPT !.rem = @ + 1])
    /\ Goto(p, "refund_ledger")
    /\ UNCHANGED <<db, snap, ctx, cancelledOnce>>

RefundLedger(p) == InsertLedger(p, "refund_ledger", FRow(C(p).tgt), AfterRefund(p))

---------------------------------------------------------------------------
(* ReservationServiceImpl#promoteQueued，持有排课锁循环补位 *)

PromoteFindQueued(p) ==   \* remainingPlaces > 0 时 selectFirstQueuedForUpdate
    LET q == QueuedIn(S(p)) IN
    /\ At(p, "promote_findQueued")
    /\ IF Max(S(p)) > C(p).sb /\ q # {}
       THEN /\ Grab(p, {RRow(SetMin(q))})
            /\ SetCtx(p, [C(p) EXCEPT !.tgt = SetMin(q)])
            /\ Goto(p, "promote_lockCard")
       ELSE Goto(p, "commit") /\ UNCHANGED <<owner, ctx>>
    /\ UNCHANGED <<db, cur, written, snap, cancelledOnce>>

PromoteLockCard(p) ==   \* lockCardForPromotion：卡不可用或次数不足返回 null
    LET card == cur[CRow(ResMember(C(p).tgt))] IN
    /\ At(p, "promote_lockCard")
    /\ Grab(p, {CRow(ResMember(C(p).tgt))})
    /\ Goto(p, IF card.stop \/ card.rem < 1 THEN "promote_markFailed" ELSE "promote_promote")
    /\ UNCHANGED <<db, cur, written, snap, ctx, cancelledOnce>>

PromoteMarkFailed(p) ==
    Transit(p, "promote_markFailed", C(p).tgt, "QUEUED", "QUEUE_FAILED", "promote_findQueued")
PromotePromote(p) == Transit(p, "promote_promote", C(p).tgt, "QUEUED", "RESERVED", "promote_occupy")
PromoteOccupy(p) == Occupy(p, "promote_occupy", "promote_deduct")
PromoteDeduct(p) == Deduct(p, "promote_deduct", ResMember(C(p).tgt), "promote_ledger")
PromoteLedger(p) == InsertLedger(p, "promote_ledger", DRow(C(p).tgt), "promote_findQueued")

---------------------------------------------------------------------------
(* ReservationServiceImpl#cancelUnderstaffedSchedule；开课时间窗口按已满足处理 *)

UnderLockSchedule(p) ==   \* lockForBooking、isUnderstaffedInCancelWindow
    LET row == SchedRead(p) IN
    /\ At(p, "under_lockSchedule")
    /\ LockSchedule(p)
    /\ Goto(p, IF row.st = "PUBLISHED" /\ row.bk < Sc.min[S(p)] THEN "under_closeSchedule" ELSE "commit")
    /\ UNCHANGED <<db, cur, written, cancelledOnce>>

UnderCloseSchedule(p) ==   \* ScheduleBookingServiceImpl#cancel：updateStatus 无条件写已取消
    /\ At(p, "under_closeSchedule")
    /\ Put(p, SRow(S(p)), [cur[SRow(S(p))] EXCEPT !.st = "CANCELLED"])
    /\ Goto(p, "under_scan")
    /\ UNCHANGED <<db, snap, ctx, cancelledOnce>>

UnderScan(p) ==   \* reservationDao.selectCancellableByScheduleForUpdate
    LET t == {r \in ResIds : ResSched(r) = S(p) /\ cur[RRow(r)] \in {"RESERVED", "QUEUED"}} IN
    /\ At(p, "under_scan")
    /\ Grab(p, {RRow(r) : r \in t})
    /\ SetCtx(p, [C(p) EXCEPT !.todo = t, !.ret = "under"])
    /\ Goto(p, "under_next")
    /\ UNCHANGED <<db, cur, written, snap, cancelledOnce>>

UnderNext(p) ==   \* 循环体：reservationDao.cancel，已预约的累计释放人数并返次
    LET r == SetMin(C(p).todo)
        st == cur[RRow(r)]
    IN
    /\ At(p, "under_next")
    /\ IF C(p).todo = {}
       THEN Goto(p, "under_release") /\ UNCHANGED <<cur, written, owner, ctx>>
       ELSE /\ Put(p, RRow(r), "CANCELLED")
            /\ SetCtx(p, [C(p) EXCEPT !.todo = @ \ {r}, !.tgt = r,
                                      !.rel = IF st = "RESERVED" THEN @ + 1 ELSE @])
            /\ Goto(p, IF st = "RESERVED" THEN "refund_readDeduct" ELSE "under_next")
    /\ UNCHANGED <<db, snap, cancelledOnce>>

UnderRelease(p) ==   \* updateBookingCount(Math.max(bookedPersons - releasedPersons, 0))
    LET v == IF C(p).sb > C(p).rel THEN C(p).sb - C(p).rel ELSE 0 IN
    /\ At(p, "under_release")
    /\ Put(p, SRow(S(p)), [cur[SRow(S(p))] EXCEPT !.bk = v])
    /\ Goto(p, "commit")
    /\ UNCHANGED <<db, snap, ctx, cancelledOnce>>

---------------------------------------------------------------------------
(* MemberCardServiceImpl#stop 与 ScheduleServiceImpl#changeStatus *)

StopLockCard(p) ==   \* requireCardForUpdate
    /\ At(p, "stop_lockCard")
    /\ Grab(p, {CRow(M(p))})
    /\ Goto(p, IF cur[CRow(M(p))].stop THEN "abort" ELSE "stop_update")
    /\ UNCHANGED <<db, cur, written, snap, ctx, cancelledOnce>>

StopUpdate(p) ==   \* MemberCardDaoImpl#stop，条件 stop_time IS NULL
    /\ At(p, "stop_update")
    /\ Put(p, CRow(M(p)), [cur[CRow(M(p))] EXCEPT !.stop = TRUE])
    /\ Goto(p, "commit")
    /\ UNCHANGED <<db, snap, ctx, cancelledOnce>>

StatusRead(p) ==   \* requireSchedule：scheduleDao.selectById，普通读，随后做流转判断
    LET st == PlainRead(p, SRow(S(p))).st
        to == Req(p).to
    IN
    /\ At(p, "status_read")
    /\ snap' = OpenView(p)
    /\ Goto(p, CASE st = "CANCELLED" -> "abort"
                 [] st = to -> "commit"
                 [] ~Allowed(st, to) -> "abort"
                 [] OTHER -> "status_update")
    /\ UNCHANGED <<db, cur, owner, written, ctx, cancelledOnce>>

StatusUpdate(p) ==   \* ScheduleDaoImpl#updateStatus，WHERE 只有 id
    /\ At(p, "status_update")
    /\ Put(p, SRow(S(p)), [cur[SRow(S(p))] EXCEPT !.st = Req(p).to])
    /\ Goto(p, "commit")
    /\ UNCHANGED <<db, snap, ctx, cancelledOnce>>

---------------------------------------------------------------------------
(* 事务结束：提交把本事务写入的行写进 db，回滚把 cur 恢复为 db；两者都释放全部行锁 *)

Finish(p, outcome) ==
    /\ owner' = [x \in Rows |-> IF owner[x] = p THEN 0 ELSE owner[x]]
    /\ written' = [written EXCEPT ![p] = {}]
    /\ snap' = [snap EXCEPT ![p] = NoView]
    /\ ctx' = [ctx EXCEPT ![p] = Ctx0]
    /\ Goto(p, outcome)

Commit(p) ==
    LET next == [x \in Rows |-> IF x \in written[p] THEN cur[x] ELSE db[x]] IN
    /\ pc[p] = "commit"
    /\ db' = next
    /\ cancelledOnce' = cancelledOnce \cup {s \in Scheds : next[SRow(s)].st = "CANCELLED"}
    /\ Finish(p, "committed")
    /\ UNCHANGED cur

Rollback(p) ==
    /\ pc[p] = "abort"
    /\ cur' = [x \in Rows |-> IF x \in written[p] THEN db[x] ELSE cur[x]]
    /\ Finish(p, "rejected")
    /\ UNCHANGED <<db, cancelledOnce>>

(* InnoDB 检测到死锁时回滚环上的一个事务（错误 1213），整个事务回滚 *)
DeadlockVictim(p) ==
    /\ InDeadlock(p)
    /\ Goto(p, "abort")
    /\ UNCHANGED <<db, cur, owner, written, snap, ctx, cancelledOnce>>

Done == /\ \A p \in Procs : pc[p] \in {"committed", "rejected"}
        /\ UNCHANGED vars

Next ==
    \/ \E p \in Procs :
        \/ SubmitPeek(p) \/ SubmitLockSchedule(p) \/ SubmitLockMember(p) \/ SubmitCheckOpen(p)
        \/ SubmitCountQueue(p) \/ SubmitLockCard(p) \/ SubmitInsert(p) \/ SubmitOccupy(p)
        \/ SubmitDeduct(p) \/ SubmitLedger(p)
        \/ CancelPeek(p) \/ CancelLockSchedule(p) \/ CancelLockReservation(p)
        \/ CancelCancelQueued(p) \/ CancelCancelReserved(p) \/ CancelRelease(p)
        \/ RefundReadDeduct(p) \/ RefundLockCard(p) \/ RefundUpdate(p) \/ RefundLedger(p)
        \/ PromoteFindQueued(p) \/ PromoteLockCard(p) \/ PromoteMarkFailed(p)
        \/ PromotePromote(p) \/ PromoteOccupy(p) \/ PromoteDeduct(p) \/ PromoteLedger(p)
        \/ UnderLockSchedule(p) \/ UnderCloseSchedule(p) \/ UnderScan(p) \/ UnderNext(p)
        \/ UnderRelease(p)
        \/ StopLockCard(p) \/ StopUpdate(p) \/ StatusRead(p) \/ StatusUpdate(p)
        \/ DeadlockVictim(p) \/ Commit(p) \/ Rollback(p)
    \/ Done

Spec == Init /\ [][Next]_vars

---------------------------------------------------------------------------
(* 不变量只检查已提交状态 db *)

SeatedIn(s) == CountRes(LAMBDA r : ResSched(r) = s /\ db[RRow(r)] \in Seated)
Deducted(m) == CountRes(LAMBDA r : ResMember(r) = m /\ db[DRow(r)])
Refunded(m) == CountRes(LAMBDA r : ResMember(r) = m /\ db[FRow(r)])

NoOversell ==
    \A s \in Scheds : db[SRow(s)].bk <= Max(s) /\ db[SRow(s)].bk = SeatedIn(s)

NoDuplicateOpen ==
    \A m \in Members, s \in Scheds :
        CountRes(LAMBDA r : ResMember(r) = m /\ ResSched(r) = s /\ db[RRow(r)] \in OpenStatus) <= 1

RefundExactlyOnce ==
    /\ \A r \in ResIds : db[RRow(r)] = "CANCELLED" /\ db[DRow(r)] => db[FRow(r)]
    /\ \A r \in ResIds : db[FRow(r)] => db[DRow(r)]
    /\ \A m \in Members : /\ db[CRow(m)].rem = Purchased(m) - Deducted(m) + Refunded(m)
                          /\ db[CRow(m)].rem >= 0

CancelledIsTerminal == \A s \in cancelledOnce : db[SRow(s)].st = "CANCELLED"

NoLockCycle == \A p \in Procs : ~InDeadlock(p)
=============================================================================
