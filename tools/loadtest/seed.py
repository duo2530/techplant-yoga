"""造数与重置。

full：清空会员、会员卡、排课、预约与流水表，写入压测门店、课程、教练、教室，
      MEMBER_COUNT 个已注册会员（各持一张本店团课次数卡）、SCHEDULE_COUNT 节场景 A/C 用的排课
      （名额等于会员数，同一会员每节课只约一次），以及一节场景 B 用的 20 人排课。
reset：只清空预约与流水，把卡余额和排课占位恢复到造数后的状态，会员与登录态不变。

两种模式都必须带 --confirm-target，值与 check_target.py 打印的目标库一致才会执行。
"""
import argparse
from datetime import datetime, timedelta

import bcrypt

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


def schedule_rows():
    """A/C 用排课分布在明天起 13 天内，每天 06:00 起每 30 分钟一节、每节 1 小时，全部处于可预约窗口。"""
    today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    rows = []
    for i in range(SCHEDULE_COUNT):
        start = today + timedelta(days=1 + i % 13, hours=6, minutes=30 * (i // 13))
        rows.append((SCHEDULE_BASE + i, STORE_ID, COURSE_ID, COACH_ID, CLASSROOM_ID, start.date(), start,
                     start + timedelta(hours=1), MEMBER_COUNT))
    b_start = today + timedelta(days=2, hours=22)
    rows.append((B_SCHEDULE_ID, STORE_ID, COURSE_ID, COACH_ID, CLASSROOM_ID, b_start.date(), b_start,
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["full", "reset"])
    parser.add_argument("--confirm-target", required=True)
    args = parser.parse_args()
    conn = connect(args.confirm_target)
    (full if args.mode == "full" else reset)(conn)
    for t in CORE_TABLES:
        print(f"{t}: {query(conn, f'SELECT COUNT(*) AS n FROM {t}')[0]['n']} 行")
    print("排课:", query(conn, "SELECT COUNT(*) AS n, SUM(booked_persons) AS booked, MIN(start_time) AS first,"
                              " MAX(start_time) AS last FROM t_schedule")[0])


if __name__ == "__main__":
    main()
