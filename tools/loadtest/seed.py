"""造数与重置。

full：清空会员、会员卡、排课、预约与流水表，写入压测门店、课程、教练、教室，
      MEMBER_COUNT 个已注册会员（各持一张本店团课次数卡）、SCHEDULE_COUNT 节场景 A/C 用的排课
      （名额等于会员数，同一会员每节课只约一次），以及一节场景 B 用的 20 人排课。
reset：只清空预约与流水，把卡余额和排课占位恢复到造数后的状态，会员与登录态不变。

--history-members N（只用于 full --scoped，库内须有 bulk_seed 数据）：场景 A/C 改用有历史预约的会员。
      按 --history-seed 从 bulk_seed 门店 0 的历史会员中选 N 个，各补发一张本店次数卡，排课改挂在该门店下，
      名单写入 lt_seed_progress 的 a_history，export_tokens --set history 按名单登录。
      这些会员已有历史预约，预约时按会员与排课查有效预约的语句会扫描到这些行。

两种模式都必须带 --confirm-target，值与 check_target.py 打印的目标库一致才会执行。
"""
import argparse
import json
import math
import sys
from datetime import datetime, timedelta

import bcrypt

from bulk_layout import HISTORY_CARD_OFFSET, PROFILES, hid, history_sample
from common import (B_MAX_PERSONS, B_SCHEDULE_ID, CARD_BASE, CARD_TIMES, CLASSROOM_ID, COACH_ID, COURSE_ID,
                    MEMBER_BASE, MEMBER_COUNT, MEMBER_PASSWORD, SCHEDULE_BASE, SCHEDULE_COUNT, STORE_ID, connect, query)

CORE_TABLES = ["t_reservation", "t_member_card_transaction", "t_member_card", "t_schedule", "t_member"]

INSERT_MEMBER = ("INSERT INTO t_member (id, phone, password, phone_last4, name, gender, level, create_by, create_time,"
                 " update_by, update_time) VALUES (%s, %s, %s, %s, %s, 0, 0, 1, NOW(), 1, NOW())")

INSERT_CARD = ("INSERT INTO t_member_card (id, member_id, open_store_id, card_no, card_name, card_type, course_scope,"
               " store_id, purchase_times, gift_times, remaining_times, valid_period_unit, valid_period_count,"
               " valid_start_time, valid_end_time, activate_time, create_by, create_time, update_by, update_time)"
               " VALUES (%s, %s, %s, %s, '压测次数卡', 1, 1, %s, %s, 0, %s, 1, 12, %s, %s, %s, 1, NOW(), 1, NOW())")

INSERT_SCHEDULE = ("INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id, schedule_date,"
                   " start_time, end_time, max_persons, min_persons, queue_limit, queue_stop_minutes, booked_persons,"
                   " status, create_by, create_time, update_by, update_time)"
                   " VALUES (%s, %s, %s, 1, %s, %s, %s, %s, %s, %s, 1, 3, 110, 0, 2, 1, NOW(), 1, NOW())")


def seed_reference(cur):
    cur.execute("INSERT IGNORE INTO t_store (id, store_no, name, store_type, region_code, phone, business_hours,"
                " address, create_by, create_time, update_by, update_time) VALUES (%s, '9100', '压测店', 1, '310104',"
                " '021-00000000', '周一至周日 06:00-23:30', '压测路 1 号', 1, NOW(), 1, NOW())", (STORE_ID,))
    cur.execute("INSERT IGNORE INTO t_classroom (id, store_id, name, create_by, create_time, update_by, update_time)"
                " VALUES (%s, %s, '压测教室', 1, NOW(), 1, NOW())", (CLASSROOM_ID, STORE_ID))
    cur.execute("INSERT IGNORE INTO t_coach (id, name, create_by, create_time, update_by, update_time)"
                " VALUES (%s, '压测教练', 1, NOW(), 1, NOW())", (COACH_ID,))
    cur.execute("INSERT IGNORE INTO t_course (id, name, course_type, difficulty, create_by, create_time, update_by,"
                " update_time) VALUES (%s, '压测团课', 1, 2, 1, NOW(), 1, NOW())", (COURSE_ID,))


def schedule_rows(store_id=STORE_ID):
    """A/C 用排课分布在明天起 13 天内，每天 06:00 起每 30 分钟一节、每节 1 小时，全部处于可预约窗口。"""
    today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    rows = []
    for i in range(SCHEDULE_COUNT):
        start = today + timedelta(days=1 + i % 13, hours=6, minutes=30 * (i // 13))
        rows.append((SCHEDULE_BASE + i, store_id, COURSE_ID, COACH_ID, CLASSROOM_ID, start.date(), start,
                     start + timedelta(hours=1), MEMBER_COUNT))
    b_start = today + timedelta(days=2, hours=22)
    rows.append((B_SCHEDULE_ID, store_id, COURSE_ID, COACH_ID, CLASSROOM_ID, b_start.date(), b_start,
                 b_start + timedelta(hours=1), B_MAX_PERSONS))
    return rows


def full(conn):
    password = bcrypt.hashpw(MEMBER_PASSWORD.encode(), bcrypt.gensalt(rounds=10, prefix=b"2a")).decode()
    now = datetime.now()
    members, cards = [], []
    for i in range(MEMBER_COUNT):
        phone = f"139{i:08d}"
        members.append((MEMBER_BASE + i, phone, password, phone[-4:], f"压测会员{i}"))
        cards.append((CARD_BASE + i, MEMBER_BASE + i, STORE_ID, f"LT{CARD_BASE + i}", STORE_ID, CARD_TIMES,
                      CARD_TIMES, now - timedelta(days=1), now + timedelta(days=300), now - timedelta(days=1)))
    with conn.cursor() as cur:
        for t in CORE_TABLES:
            cur.execute(f"TRUNCATE TABLE {t}")
        seed_reference(cur)
        cur.executemany(INSERT_MEMBER, members)
        cur.executemany(INSERT_CARD, cards)
        cur.executemany(INSERT_SCHEDULE, schedule_rows())
    conn.commit()


def reset(conn):
    with conn.cursor() as cur:
        cur.execute("TRUNCATE TABLE t_reservation")
        cur.execute("TRUNCATE TABLE t_member_card_transaction")
        cur.execute("UPDATE t_member_card SET remaining_times = purchase_times + gift_times")
        cur.execute("UPDATE t_schedule SET booked_persons = 0")
    conn.commit()


MEMBER_RANGE = (MEMBER_BASE, MEMBER_BASE + MEMBER_COUNT - 1)
CARD_RANGE = (CARD_BASE, CARD_BASE + MEMBER_COUNT - 1)
SCHEDULE_RANGE = (SCHEDULE_BASE, B_SCHEDULE_ID)
HISTORY_CARD_RANGE = (hid("t_member_card", HISTORY_CARD_OFFSET),
                      hid("t_member_card", HISTORY_CARD_OFFSET + 10_000_000_000 - 1))


def scoped_reset(conn):
    """只清本脚本排课上的预约与本脚本卡的流水、恢复卡余额与排课占位，不触碰其他数据。"""
    with conn.cursor() as cur:
        for card_range in (CARD_RANGE, HISTORY_CARD_RANGE):
            cur.execute("DELETE FROM t_member_card_transaction WHERE member_card_id BETWEEN %s AND %s", card_range)
            cur.execute("UPDATE t_member_card SET remaining_times = purchase_times + gift_times"
                        " WHERE id BETWEEN %s AND %s", card_range)
        cur.execute("DELETE FROM t_reservation WHERE member_id BETWEEN %s AND %s", MEMBER_RANGE)
        cur.execute("DELETE FROM t_reservation WHERE schedule_id BETWEEN %s AND %s", SCHEDULE_RANGE)
        cur.execute("UPDATE t_schedule SET booked_persons = 0 WHERE id BETWEEN %s AND %s", SCHEDULE_RANGE)
    conn.commit()


def history_plan(conn, n, seed):
    """按 bulk_seed 的规模选出历史会员；人数调到加 1 后与排课数互素，场景 A 前 M*S 个请求的组合才不重复。"""
    rows = query(conn, "SELECT info FROM lt_seed_progress WHERE name = 'last_run'")
    if not rows:
        sys.exit("库内没有 bulk_seed 的造数记录，--history-members 需要先运行 bulk_seed.py")
    info = rows[0]["info"]
    last = info if isinstance(info, dict) else json.loads(info)
    members = history_sample(PROFILES[last["profile"]], last["size"]["members"], n, seed)
    while len(members) > 1 and math.gcd(len(members) + 1, SCHEDULE_COUNT) != 1:
        members.pop()
    return {"seed": seed, "store_id": hid("t_store", 0), "members": members,
            "card_base": hid("t_member_card", HISTORY_CARD_OFFSET)}


def history_cards(plan, now):
    return [(plan["card_base"] + gm, hid("t_member", gm), plan["store_id"], f"LTH{plan['card_base'] + gm}",
             plan["store_id"], CARD_TIMES, CARD_TIMES, now - timedelta(days=1), now + timedelta(days=300),
             now - timedelta(days=1)) for gm in plan["members"]]


def history_coverage(conn, plan):
    """选中会员在造数里已有的预约条数。"""
    ids = [hid("t_member", gm) for gm in plan["members"]]
    counts = []
    for k in range(0, len(ids), 500):
        part = ids[k:k + 500]
        rows = query(conn, f"SELECT member_id, COUNT(*) AS n FROM t_reservation"
                           f" WHERE member_id IN ({','.join(['%s'] * len(part))}) GROUP BY member_id", part)
        got = {r["member_id"]: r["n"] for r in rows}
        counts += [got.get(m, 0) for m in part]
    return {"members": len(counts), "with_history": sum(1 for c in counts if c), "min": min(counts),
            "avg": round(sum(counts) / len(counts), 1), "max": max(counts)}


def scoped_full(conn, plan=None):
    """重建本脚本的会员、卡与排课，其他数据保持不变；plan 不为空时场景 A/C 改用其中的历史会员。"""
    scoped_reset(conn)
    with conn.cursor() as cur:
        for card_range in (CARD_RANGE, HISTORY_CARD_RANGE):
            cur.execute("DELETE FROM t_member_card WHERE id BETWEEN %s AND %s", card_range)
        cur.execute("DELETE FROM t_member WHERE id BETWEEN %s AND %s", MEMBER_RANGE)
        cur.execute("DELETE FROM t_schedule WHERE id BETWEEN %s AND %s", SCHEDULE_RANGE)
        if query(conn, "SHOW TABLES LIKE 'lt_seed_progress'"):
            cur.execute("DELETE FROM lt_seed_progress WHERE name = 'a_history'")
    conn.commit()
    password = bcrypt.hashpw(MEMBER_PASSWORD.encode(), bcrypt.gensalt(rounds=10, prefix=b"2a")).decode()
    now = datetime.now()
    members = [(MEMBER_BASE + i, f"139{i:08d}", password, f"{i:08d}"[-4:], f"压测会员{i}") for i in range(MEMBER_COUNT)]
    cards = [(CARD_BASE + i, MEMBER_BASE + i, STORE_ID, f"LT{CARD_BASE + i}", STORE_ID, CARD_TIMES, CARD_TIMES,
              now - timedelta(days=1), now + timedelta(days=300), now - timedelta(days=1)) for i in range(MEMBER_COUNT)]
    with conn.cursor() as cur:
        seed_reference(cur)
        cur.executemany(INSERT_MEMBER, members)
        cur.executemany(INSERT_CARD, cards)
        cur.executemany(INSERT_SCHEDULE, schedule_rows(plan["store_id"] if plan else STORE_ID))
        if plan:
            cur.executemany(INSERT_CARD, history_cards(plan, now))
            cur.execute("INSERT INTO lt_seed_progress (name, done, info) VALUES ('a_history', %s, %s)",
                        (len(plan["members"]), json.dumps(plan)))
    conn.commit()
    if plan:
        print("场景 A 历史会员:", json.dumps({"store_id": plan["store_id"], "seed": plan["seed"],
                                               **history_coverage(conn, plan)}, ensure_ascii=False))


def has_bulk_data(conn):
    return bool(query(conn, "SHOW TABLES LIKE 'lt_seed_progress'")) and         bool(query(conn, "SELECT 1 FROM lt_seed_progress LIMIT 1"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["full", "reset"])
    parser.add_argument("--confirm-target", required=True)
    parser.add_argument("--scoped", action="store_true",
                        help="只增删本脚本的 ID 区间，不清空整表；库里有 bulk_seed 数据时必须使用")
    parser.add_argument("--history-members", type=int, default=0,
                        help="full --scoped：场景 A/C 改用 bulk_seed 门店 0 的这么多个历史会员；0 用本脚本新建的会员")
    parser.add_argument("--history-seed", type=int, default=58)
    args = parser.parse_args()
    if args.history_members and not (args.scoped and args.mode == "full"):
        sys.exit("--history-members 只能与 full --scoped 一起使用")
    conn = connect(args.confirm_target)
    if not args.scoped and has_bulk_data(conn):
        sys.exit("库里有 bulk_seed 的数据，TRUNCATE 会清掉它们；改用 --scoped")
    if args.history_members:
        scoped_full(conn, history_plan(conn, args.history_members, args.history_seed))
    elif args.scoped:
        (scoped_full if args.mode == "full" else scoped_reset)(conn)
    else:
        (full if args.mode == "full" else reset)(conn)
    for t in CORE_TABLES:
        print(f"{t}: {query(conn, f'SELECT COUNT(*) AS n FROM {t}')[0]['n']} 行")
    print("排课:", query(conn, "SELECT COUNT(*) AS n, SUM(booked_persons) AS booked, MIN(start_time) AS first,"
                              " MAX(start_time) AS last FROM t_schedule")[0])


if __name__ == "__main__":
    main()
