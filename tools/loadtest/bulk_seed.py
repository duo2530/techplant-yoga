"""大数据量造数：按 profile 把压测区间内的预约补足到 --target-rows 行，其他表按比例推导，再刷新压测可消耗池。

  top-up（默认）：从库内进度表 lt_seed_progress 记录的位置继续，已有行不重写，不超出目标行数。
  rebuild：先删除压测 ID 区间（及压测会员通过接口产生的预约与流水）再从头生成，必须带 --confirm-rebuild。

所有写入都限定在 common.BULK_BASE 的区间内，不触碰种子数据与业务数据。每批单独提交，进度与数据同事务，
中断后重跑即从上次提交的批次继续。结束后输出各表行数与耗时，执行 ANALYZE TABLE 与 reconcile。
"""
import argparse
import json
import math
import sys
import time
from collections import defaultdict
from datetime import datetime, timedelta

import bcrypt

from bulk_layout import (COURSE_TYPES, POOL_CANCEL_SCHEDULE, POOL_CHECKIN_RESERVATION, POOL_CHECKIN_SCHEDULE,
                         POOL_HOLDER_RESERVATION, POOL_QUEUE_SCHEDULE, PROFILES, YEAR_CARD_OFFSET, hid,
                         member_rows, members_per_store, pid, plan_schedule, reservation_of, rolling_schedules,
                         store_row)
from common import (BULK_BASE, BULK_SPAN, CORE_TABLES, MEMBER_PASSWORD, POOL_OFFSET, connect, data_dir, db_target,
                    local_now, query, table_counts, write_json)
from reconcile import reconcile

MAX_TARGET_ROWS = 10_000_000
POOL_CARD_TIMES = 500

INSERT_STORE = ("INSERT IGNORE INTO t_store (id, store_no, name, store_type, region_code, phone, business_hours,"
                " address, create_by, create_time, update_by, update_time)"
                " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 1, %s, 1, %s)")
INSERT_CLASSROOM = ("INSERT IGNORE INTO t_classroom (id, store_id, name, create_by, create_time, update_by,"
                    " update_time) VALUES (%s, %s, %s, 1, %s, 1, %s)")
INSERT_COACH = ("INSERT IGNORE INTO t_coach (id, name, intro, level, create_by, create_time, update_by, update_time)"
                " VALUES (%s, %s, %s, %s, 1, %s, 1, %s)")
INSERT_COURSE = ("INSERT IGNORE INTO t_course (id, name, course_type, intro, difficulty, create_by, create_time,"
                 " update_by, update_time) VALUES (%s, %s, %s, %s, %s, 1, %s, 1, %s)")
INSERT_SCHEDULE = ("INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id,"
                   " schedule_date, start_time, end_time, max_persons, min_persons, queue_limit, queue_stop_minutes,"
                   " booked_persons, status, create_by, create_time, update_by, update_time)"
                   " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 1, %s, 110, %s, 2, 1, %s, 1, %s)")
INSERT_MEMBER = ("INSERT INTO t_member (id, phone, password, phone_last4, name, gender, level, create_by,"
                 " create_time, update_by, update_time) VALUES (%s, %s, %s, %s, %s, %s, %s, 1, %s, 1, %s)")
INSERT_CARD = ("INSERT INTO t_member_card (id, member_id, open_store_id, card_no, card_name, card_type,"
               " course_scope, store_id, purchase_times, gift_times, remaining_times, valid_period_unit,"
               " valid_period_count, valid_start_time, valid_end_time, activate_time, create_by, create_time,"
               " update_by, update_time)"
               " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 1, %s, 1, %s)")
INSERT_RESERVATION = ("INSERT INTO t_reservation (id, request_id, member_id, schedule_id, start_time, end_time,"
                      " persons, member_card_id, status, reservation_time, cancel_time, cancel_reason,"
                      " check_in_time, create_time, update_time)"
                      " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)")
INSERT_TX = ("INSERT INTO t_member_card_transaction (id, member_card_id, member_id, reservation_id, change_type,"
             " persons, change_times, before_times, after_times, create_time)"
             " VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)")

PROGRESS_DDL = ("CREATE TABLE IF NOT EXISTS lt_seed_progress (name varchar(64) NOT NULL PRIMARY KEY,"
                " done bigint NOT NULL DEFAULT 0, info json DEFAULT NULL,"
                " update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)"
                " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='bulk_seed 造数进度'")


def log(text):
    print(f"[{datetime.now():%H:%M:%S}] {text}", flush=True)


class Throttle:
    """按 rows_per_sec 限制写入速度，0 表示不限。"""

    def __init__(self, rows_per_sec):
        self.rate, self.started, self.rows = rows_per_sec, time.time(), 0

    def add(self, rows):
        self.rows += rows
        if self.rate > 0:
            wait = self.rows / self.rate - (time.time() - self.started)
            if wait > 0:
                time.sleep(wait)


def get_progress(conn, name):
    rows = query(conn, "SELECT done, info FROM lt_seed_progress WHERE name = %s", (name,))
    if not rows:
        return 0, None
    info = rows[0]["info"]
    return rows[0]["done"], json.loads(info) if isinstance(info, str) else info


def set_progress(cur, name, done, info=None):
    cur.execute("INSERT INTO lt_seed_progress (name, done, info) VALUES (%s, %s, %s)"
                " ON DUPLICATE KEY UPDATE done = VALUES(done), info = COALESCE(VALUES(info), info)",
                (name, done, None if info is None else json.dumps(info, ensure_ascii=False, default=str)))


def sizing(p, target_rows):
    """目标预约行数推导出的排课、门店与历史会员数量。"""
    schedules = math.ceil(target_rows / p["slots"])
    per_store = p["schedules_per_store"]
    stores = max(1, math.ceil(schedules / per_store))
    last_local = schedules - 1 - (stores - 1) * per_store
    num, den = p["member_step"]
    members = (stores - 1) * members_per_store(p) + last_local * num // den + p["member_window"]
    return {"reservations": target_rows, "schedules": schedules, "stores": stores, "members": members}


def delete_range(conn, table, column, low, high, chunk=20000):
    """分块删除 column 落在 [low, high] 的行，每块单独提交，避免大事务。"""
    total = 0
    while True:
        with conn.cursor() as cur:
            n = cur.execute(f"DELETE FROM {table} WHERE {column} BETWEEN %s AND %s LIMIT {chunk}", (low, high))
        conn.commit()
        total += n
        if n < chunk:
            return total


def wipe(conn, pool_only):
    """删除压测区间：pool_only 时只删可消耗池，否则删除全部压测数据并清空进度。"""
    lo = POOL_OFFSET if pool_only else 0
    hi = BULK_SPAN - 1
    plan = [("t_member_card_transaction", "member_card_id", "t_member_card"),
            ("t_reservation", "member_id", "t_member"),
            ("t_member_card_transaction", "id", "t_member_card_transaction"),
            ("t_reservation", "id", "t_reservation"), ("t_member_card", "id", "t_member_card"),
            ("t_member", "id", "t_member"), ("t_schedule", "id", "t_schedule")]
    if not pool_only:
        plan += [("t_classroom", "id", "t_classroom"), ("t_coach", "id", "t_coach"), ("t_course", "id", "t_course"),
                 ("t_store", "id", "t_store")]
    for table, column, base_table in plan:
        n = delete_range(conn, table, column, BULK_BASE[base_table] + lo, BULK_BASE[base_table] + hi)
        if n:
            log(f"删除 {table}（{column} 在压测{'池' if pool_only else ''}区间）{n} 行")
    if not pool_only:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM lt_seed_progress")
        conn.commit()


def seed_reference(conn, p, size, now):
    stores, rooms, coaches = size["stores"], p["classrooms_per_store"], p["coaches_per_store"]
    with conn.cursor() as cur:
        cur.executemany(INSERT_STORE, [store_row(b, now) for b in range(stores)])
        cur.executemany(INSERT_CLASSROOM, [(hid("t_classroom", b * rooms + r), hid("t_store", b),
                                            f"压测教室{r + 1}", now, now) for b in range(stores) for r in range(rooms)])
        cur.executemany(INSERT_COACH, [(hid("t_coach", n), f"压测教练{n}", "压测数据", 2 if n % coaches == 0 else 1,
                                        now, now) for n in range(stores * coaches)])
        cur.executemany(INSERT_COURSE, [(hid("t_course", c), f"压测课程{c:02d}", COURSE_TYPES[c], "压测数据",
                                         1 + c % 5, now, now) for c in range(p["courses"])])
    conn.commit()


def seed_schedules(conn, p, meta, size, a, throttle):
    done, _ = get_progress(conn, "schedules")
    need, base_date, created = size["schedules"], meta["base_date"], meta["created"]
    while done < need:
        end = min(done + a.batch_size, need)
        rows = []
        for s in range(done, end):
            x = plan_schedule(p, meta["seed"], s, base_date)
            rows.append((x["id"], x["store_id"], x["course_id"], x["course_type"], x["coach_id"], x["classroom_id"],
                         x["start"].date(), x["start"], x["end"], x["max_persons"], p["queue_limit"], 0, created,
                         created))
        with conn.cursor() as cur:
            cur.executemany(INSERT_SCHEDULE, rows)
            set_progress(cur, "schedules", end)
        conn.commit()
        throttle.add(len(rows))
        done = end
        log(f"排课 {done}/{need}")


def seed_members(conn, p, meta, size, a, throttle):
    done, _ = get_progress(conn, "members")
    need, mps = size["members"], members_per_store(p)
    password = meta["password_hash"]
    while done < need:
        end = min(done + max(1, a.batch_size // 2), need)
        members, cards = [], []
        for gm in range(done, end):
            m, c = member_rows(meta["seed"], gm, hid("t_store", gm // mps), meta["base_date"], password)
            members.append(m)
            cards.extend(c)
        with conn.cursor() as cur:
            cur.executemany(INSERT_MEMBER, members)
            cur.executemany(INSERT_CARD, cards)
            set_progress(cur, "members", end)
        conn.commit()
        throttle.add(len(members) + len(cards))
        done = end
        log(f"会员 {done}/{need}")


def card_balances(conn, card_ids):
    rows = query(conn, "SELECT id, remaining_times FROM t_member_card WHERE id IN (" + ",".join(["%s"] * len(card_ids))
                 + ") FOR UPDATE", list(card_ids))
    return {r["id"]: r["remaining_times"] for r in rows}


def set_by_id(cur, table, column, values, absolute, chunk=1000):
    """按 id 批量改一列：absolute 为真时直接赋值，否则累加。每条 UPDATE 用 CASE 合并 chunk 行。"""
    items = list(values.items())
    for k in range(0, len(items), chunk):
        part = items[k:k + chunk]
        cases = " ".join(["WHEN %s THEN %s"] * len(part))
        expr = f"CASE id {cases} END" if absolute else f"{column} + CASE id {cases} END"
        args = [x for pair in part for x in pair] + [pair[0] for pair in part]
        cur.execute(f"UPDATE {table} SET {column} = {expr} WHERE id IN ({','.join(['%s'] * len(part))})", args)


def seed_reservations(conn, p, meta, size, a, throttle):
    done, _ = get_progress(conn, "reservations")
    need, slots = size["reservations"], p["slots"]
    while done < need:
        end = min(done + a.batch_size, need)
        now = local_now().replace(microsecond=0)
        reservations, ledger, seated = [], [], defaultdict(int)
        plans = {}
        for i in range(done, end):
            s, j = divmod(i, slots)
            if s not in plans:
                plans[s] = plan_schedule(p, meta["seed"], s, meta["base_date"])
            _, _, row, deduct, refund = reservation_of(p, meta["seed"], plans[s], j, now)
            reservations.append(row)
            if row[8] in (1, 3):
                seated[row[3]] += 1
            if deduct:
                ledger.append((i, row, refund))
        balances = card_balances(conn, {row[7] for _, row, _ in ledger}) if ledger else {}
        txs = []
        for i, row, refund in ledger:
            card, member = row[7], row[2]
            before = balances[card]
            txs.append((hid("t_member_card_transaction", 2 * i), card, member, row[0], 1, 1, -1, before, before - 1,
                        row[9]))
            balances[card] = before - 1
            if refund:
                txs.append((hid("t_member_card_transaction", 2 * i + 1), card, member, row[0], 2, 1, 1, before - 1,
                            before, row[10]))
                balances[card] = before
        if any(v < 0 for v in balances.values()):
            sys.exit("生成的扣次超过会员卡余额，profile 参数不一致，已回滚本批")
        with conn.cursor() as cur:
            cur.executemany(INSERT_RESERVATION, reservations)
            if txs:
                cur.executemany(INSERT_TX, txs)
            set_by_id(cur, "t_member_card", "remaining_times", balances, absolute=True)
            set_by_id(cur, "t_schedule", "booked_persons", seated, absolute=False)
            set_progress(cur, "reservations", end)
        conn.commit()
        throttle.add(len(reservations) + len(txs))
        done = end
        log(f"预约 {done}/{need}，本批流水 {len(txs)}")


def seed_rolling(conn, p, meta, today):
    """补齐今天起 days_future 天里历史排课没有覆盖的日期，每次运行都执行，已有的行保持不变。"""
    lo, hi = hid("t_store", 0), hid("t_store", POOL_OFFSET - 1)
    stores = query(conn, "SELECT COUNT(*) AS n FROM t_store WHERE id BETWEEN %s AND %s", (lo, hi))[0]["n"]
    per_store = get_progress(conn, "schedules")[0] // max(1, stores)
    covered = meta["base_date"] + timedelta(days=p["days_future"] - 1)
    days = [today + timedelta(days=d) for d in range(p["days_future"])]
    days = [d for d in days if d > covered]
    added = 0
    for day in days:
        rows = [(x["id"], x["store_id"], x["course_id"], x["course_type"], x["coach_id"], x["classroom_id"],
                 day, x["start"], x["end"], x["max_persons"], p["queue_limit"], 0, x["created"], x["created"])
                for x in rolling_schedules(p, meta["seed"], stores, day, meta["created"], per_store)]
        with conn.cursor() as cur:
            cur.executemany(INSERT_SCHEDULE.replace("INSERT INTO", "INSERT IGNORE INTO", 1), rows)
            added += cur.rowcount
        conn.commit()
    log(f"滚动排课：历史排课覆盖到 {covered}，补齐 {len(days)} 天 × {stores} 店，新增 {added} 节")
    return {"covered_until": covered.isoformat(), "days": len(days), "added": added}


def pool_layout(a, size):
    stores = min(a.pool_stores, size["stores"])
    per_store = a.pool_members // stores
    if per_store < 5:
        sys.exit("--pool-members 太少：每个门店至少 5 个可消耗会员")
    layout = {"stores": stores, "members_per_store": per_store, "members": per_store * stores,
              "reserve_schedules": a.pool_reserve_schedules,
              "cancel": a.pool_cancel, "cancel_schedules": math.ceil(a.pool_cancel / (per_store * stores)),
              "checkin": a.pool_checkin, "checkin_schedules": math.ceil(a.pool_checkin / (per_store * stores)),
              "queue_schedules": math.ceil(a.pool_queue_schedules / stores), "queue_limit": 3}
    for key in ("reserve_schedules", "cancel_schedules", "checkin_schedules", "queue_schedules"):
        if layout[key] >= 10000:
            sys.exit(f"可消耗池 {key}={layout[key]} 超过每店 10000 的编号空间，调小池参数")
    return layout


def seed_pool(conn, a, size, password):
    """可消耗池：新会员与本店次数卡、未来可预约排课、可取消与可签到的已预约记录、可排队的满员排课。

    映射规则与 capacity.js 一致：序号 k 落在门店 k % S，门店内会员 (k // S) % Mb，排课 (k // S) // Mb。
    """
    L = pool_layout(a, size)
    S, Mb = L["stores"], L["members_per_store"]
    now = local_now().replace(second=0, microsecond=0)
    tomorrow = datetime.combine(now.date() + timedelta(days=1), datetime.min.time())
    course, course_type = hid("t_course", 0), COURSE_TYPES[0]

    def member_of(b, u):
        return u * S + b

    def sched(kind, b, r, start, minutes, max_persons, booked):
        return (pid("t_schedule", kind + b * 10000 + r), hid("t_store", b), course, course_type,
                hid("t_coach", b * 8), hid("t_classroom", b * 3), start.date(), start,
                start + timedelta(minutes=minutes), max_persons, L["queue_limit"], booked, now, now)

    def day_slot(r, minute):
        return tomorrow + timedelta(days=r % 13, hours=7 + (r // 13) % 14, minutes=minute)

    members, cards = [], []
    for m in range(L["members"]):
        phone = f"137{m:08d}"
        member_id, card_id, store_id = pid("t_member", m), pid("t_member_card", m), hid("t_store", m % S)
        members.append((member_id, phone, password, phone[-4:], f"压测池会员{m}", 0, 0, now, now))
        cards.append([card_id, member_id, store_id, f"LTP{card_id}", "压测池次卡", 1, 1, store_id, POOL_CARD_TIMES, 0,
                      POOL_CARD_TIMES, 1, 12, now - timedelta(days=1), now + timedelta(days=300),
                      now - timedelta(days=1), now, now])
    balance = [POOL_CARD_TIMES] * L["members"]
    schedules, reservations, txs = [], [], []
    booked = defaultdict(int)

    def seat(n, m, schedule, start, end):
        rid = pid("t_reservation", n)
        reserved = now - timedelta(hours=1)
        reservations.append((rid, f"00000000-0000-4000-8000-{n:012d}", pid("t_member", m), schedule, start, end, 1,
                             pid("t_member_card", m), 1, reserved, None, None, None, reserved, reserved))
        txs.append((pid("t_member_card_transaction", 2 * n), pid("t_member_card", m), pid("t_member", m), rid, 1, 1,
                    -1, balance[m], balance[m] - 1, reserved))
        balance[m] -= 1
        booked[schedule] += 1

    for b in range(S):
        for r in range(L["reserve_schedules"]):
            schedules.append(sched(0, b, r, day_slot(r, 0), 60, Mb, 0))
    for kind, count, rows_key, n_base, start_of in (
            (POOL_CANCEL_SCHEDULE, L["cancel"], "cancel_schedules", 0, lambda r: day_slot(r, 30)),
            (POOL_CHECKIN_SCHEDULE, L["checkin"], "checkin_schedules", POOL_CHECKIN_RESERVATION,
             lambda r: now + timedelta(minutes=15))):
        minutes = 240 if kind == POOL_CHECKIN_SCHEDULE else 60
        for q in range(count):
            b, rest = q % S, q // S
            u, r = rest % Mb, rest // Mb
            start = start_of(r)
            seat(n_base + q, member_of(b, u), pid("t_schedule", kind + b * 10000 + r), start,
                 start + timedelta(minutes=minutes))
        for b in range(S):
            for r in range(L[rows_key]):
                sid = pid("t_schedule", kind + b * 10000 + r)
                schedules.append(sched(kind, b, r, start_of(r), minutes, Mb, booked[sid]))
    for b in range(S):
        for r in range(L["queue_schedules"]):
            start = day_slot(r, 45)
            sid = pid("t_schedule", POOL_QUEUE_SCHEDULE + b * 10000 + r)
            seat(POOL_HOLDER_RESERVATION + b * 10000 + r, member_of(b, r % Mb), sid, start, start + timedelta(hours=1))
            schedules.append(sched(POOL_QUEUE_SCHEDULE, b, r, start, 60, 1, booked[sid]))
    for m, card in enumerate(cards):
        card[10] = balance[m]

    with conn.cursor() as cur:
        for sql, rows in ((INSERT_MEMBER, members), (INSERT_CARD, cards), (INSERT_SCHEDULE, schedules),
                          (INSERT_RESERVATION, reservations), (INSERT_TX, txs)):
            for k in range(0, len(rows), a.batch_size):
                cur.executemany(sql, rows[k:k + a.batch_size])
        set_progress(cur, "pool", len(reservations), {**L, "refreshed": now})
    conn.commit()
    log(f"可消耗池：会员 {len(members)}，排课 {len(schedules)}，已预约 {len(reservations)}（可取消 {L['cancel']}、"
        f"可签到 {L['checkin']}、排队占位 {S * L['queue_schedules']}）")
    return {**L, "refreshed": now.isoformat()}


def load_meta(conn, a, now):
    """首轮写入随机种子、profile 与基准日期；之后的 top-up 必须与之一致，保证同一序号生成相同的行。"""
    _, meta = get_progress(conn, "meta")
    if meta:
        if meta["seed"] != a.seed or meta["profile"] != a.profile:
            sys.exit(f"库内已有 seed={meta['seed']} profile={meta['profile']} 的数据，与本次参数不一致；"
                     "沿用原参数，或用 rebuild 重建")
        meta["base_date"] = datetime.strptime(meta["base_date"], "%Y-%m-%d").date()
        meta["created"] = datetime.fromisoformat(meta["created"])
        return meta
    password = bcrypt.hashpw(MEMBER_PASSWORD.encode(), bcrypt.gensalt(rounds=10, prefix=b"2a")).decode()
    meta = {"seed": a.seed, "profile": a.profile, "base_date": now.date().isoformat(),
            "created": (now - timedelta(days=220)).replace(microsecond=0).isoformat(), "password_hash": password}
    with conn.cursor() as cur:
        set_progress(cur, "meta", 0, meta)
    conn.commit()
    return load_meta(conn, a, now)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--target-rows", type=int, required=True, help="压测区间内 t_reservation 的目标行数")
    p.add_argument("--profile", default="booking-heavy", choices=sorted(PROFILES))
    p.add_argument("--mode", default="top-up", choices=["top-up", "rebuild"])
    p.add_argument("--confirm-target", required=True)
    p.add_argument("--confirm-rebuild")
    p.add_argument("--seed", type=int, default=58)
    p.add_argument("--batch-size", type=int, default=10000)
    p.add_argument("--rows-per-sec", type=int, default=0, help="写入限速（行/秒），0 不限")
    p.add_argument("--skip-history", action="store_true", help="只刷新可消耗池")
    p.add_argument("--skip-pool", action="store_true")
    p.add_argument("--skip-reconcile", action="store_true")
    p.add_argument("--skip-analyze", action="store_true")
    p.add_argument("--pool-stores", type=int, default=5)
    p.add_argument("--pool-members", type=int, default=3000)
    p.add_argument("--pool-reserve-schedules", type=int, default=40)
    p.add_argument("--pool-cancel", type=int, default=20000)
    p.add_argument("--pool-checkin", type=int, default=5000)
    p.add_argument("--pool-queue-schedules", type=int, default=2000)
    a = p.parse_args()
    if not 1 <= a.target_rows <= MAX_TARGET_ROWS:
        sys.exit(f"--target-rows 取值 1～{MAX_TARGET_ROWS}")
    if not 100 <= a.batch_size <= 50000:
        sys.exit("--batch-size 取值 100～50000")
    if a.mode == "rebuild" and a.confirm_rebuild != db_target():
        sys.exit(f"rebuild 需要 --confirm-rebuild={db_target()}（与实际连接一致），拒绝执行")

    started = time.time()
    conn = connect(a.confirm_target)
    with conn.cursor() as cur:
        cur.execute(PROGRESS_DDL)
    conn.commit()
    if a.mode == "rebuild":
        log("rebuild：删除压测区间数据")
        with conn.cursor() as cur:
            cur.execute("DELETE FROM lt_seed_progress")
            set_progress(cur, "rebuild_pending", 1)
        conn.commit()
        wipe(conn, pool_only=False)
    elif get_progress(conn, "rebuild_pending")[0]:
        sys.exit("上一次 rebuild 在删除阶段中断，压测区间数据不完整；重新执行 rebuild")
    profile = PROFILES[a.profile]
    meta = load_meta(conn, a, local_now())
    size = sizing(profile, a.target_rows)
    done, _ = get_progress(conn, "reservations")
    log(f"profile={a.profile} seed={a.seed} 基准日期 {meta['base_date']}，目标 {size}，已完成预约 {done}")
    if done > a.target_rows:
        log(f"库内已有 {done} 行压测预约，超过目标 {a.target_rows}；top-up 不删除数据，只跳过历史数据生成")
    throttle = Throttle(a.rows_per_sec)
    phases = {}
    if not a.skip_history and done < a.target_rows:
        for name, fn in (("reference", lambda: seed_reference(conn, profile, size, meta["created"])),
                         ("schedules", lambda: seed_schedules(conn, profile, meta, size, a, throttle)),
                         ("members", lambda: seed_members(conn, profile, meta, size, a, throttle)),
                         ("reservations", lambda: seed_reservations(conn, profile, meta, size, a, throttle))):
            t0 = time.time()
            fn()
            phases[name] = round(time.time() - t0, 1)
    rolling = None
    if get_progress(conn, "schedules")[0]:
        t0 = time.time()
        rolling = seed_rolling(conn, profile, meta, local_now().date())
        phases["rolling"] = round(time.time() - t0, 1)
    pool = None
    if not a.skip_pool:
        t0 = time.time()
        wipe(conn, pool_only=True)
        pool = seed_pool(conn, a, size, meta["password_hash"])
        phases["pool"] = round(time.time() - t0, 1)
    if not a.skip_analyze:
        t0 = time.time()
        for t in CORE_TABLES:
            query(conn, f"ANALYZE TABLE {t}")
        phases["analyze"] = round(time.time() - t0, 1)
    counts = table_counts(conn)
    rec = None
    if not a.skip_reconcile:
        t0 = time.time()
        rec = reconcile(conn)
        phases["reconcile"] = round(time.time() - t0, 1)
    summary = {"target": db_target(), "mode": a.mode, "profile": a.profile, "seed": a.seed,
               "target_rows": a.target_rows, "size": size, "base_date": meta["base_date"].isoformat(),
               "batch_size": a.batch_size, "rows_per_sec": a.rows_per_sec, "started": started,
               "ended": time.time(), "seconds": round(time.time() - started, 1), "phases": phases,
               "counts": counts, "pool": pool, "rolling": rolling,
               "reconcile_violations": None if rec is None else rec["violation_total"]}
    with conn.cursor() as cur:
        set_progress(cur, "last_run", a.target_rows, summary)
    conn.commit()
    write_json(data_dir() / "bulk_seed.json", summary)
    if rec is not None:
        write_json(data_dir() / "bulk_seed_reconcile.json", rec)
    for t, c in counts.items():
        log(f"{t}: 共 {c['total']} 行，其中压测区间 {c['bulk']} 行")
    log(f"耗时 {summary['seconds']} 秒，各阶段 {phases}")
    if rec is not None:
        for name, v in rec["violations"].items():
            print(f"[{'OK' if v['count'] == 0 else 'FAIL'}] {name}: {v['count']} 行")
        sys.exit(1 if rec["violation_total"] else 0)


if __name__ == "__main__":
    main()
