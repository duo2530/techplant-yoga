"""压测脚本共用的配置读取与数据库连接。配置全部来自 LT_* 环境变量，带 _FILE 后缀的变量指向存放值的文件。"""
import json
import os
import sys
from pathlib import Path

import pymysql

MEMBER_COUNT = 2500
SCHEDULE_COUNT = 400
CARD_TIMES = 500
B_MEMBER_COUNT = 500
B_MAX_PERSONS = 20

STORE_ID = 9100000001
COURSE_ID = 9200000001
COACH_ID = 9300000001
CLASSROOM_ID = 9400000001
MEMBER_BASE = 9500000000
CARD_BASE = 9600000000
SCHEDULE_BASE = 9700000000
B_SCHEDULE_ID = SCHEDULE_BASE + 9999
MEMBER_PASSWORD = "Loadtest123"

# bulk_seed.py 的 ID 区间：每张表占 [base, base + 1e11)，其中前 9e10 为历史数据，后 1e10 为压测可消耗池。
# 区间高于 seed.py 的 9.1e9 段、低于业务雪花 ID，并且小于 2^53，k6 可以直接按数字计算。
BULK_BASE = {
    "t_store": 8_100_000_000_000, "t_classroom": 8_200_000_000_000, "t_coach": 8_300_000_000_000,
    "t_course": 8_400_000_000_000, "t_schedule": 8_500_000_000_000, "t_member": 8_600_000_000_000,
    "t_member_card": 8_700_000_000_000, "t_reservation": 8_800_000_000_000,
    "t_member_card_transaction": 8_900_000_000_000,
}
BULK_SPAN = 100_000_000_000
POOL_OFFSET = 90_000_000_000
CORE_TABLES = ["t_member", "t_member_card", "t_schedule", "t_reservation", "t_member_card_transaction"]


def local_now():
    """与后端 JVM 时区一致的当前时间（默认东八区），不依赖容器是否装了 tzdata。"""
    from datetime import datetime, timedelta, timezone
    return datetime.now(timezone(timedelta(hours=float(env("LT_TZ_OFFSET", "8"))))).replace(tzinfo=None)


def env(name, default=None):
    value = os.environ.get(name)
    if value is None and os.environ.get(name + "_FILE"):
        value = Path(os.environ[name + "_FILE"]).read_text(encoding="utf-8").strip()
    if value is None:
        if default is None:
            sys.exit(f"缺少环境变量 {name}（或 {name}_FILE）")
        return default
    return value


def data_dir():
    path = Path(env("LT_DATA_DIR", "./data"))
    path.mkdir(parents=True, exist_ok=True)
    return path


def db_target():
    return f"{env('LT_MYSQL_HOST')}:{env('LT_MYSQL_PORT', '3306')}/{env('LT_MYSQL_DB', 'yoga')}"


def connect(confirm_target=None, quiet=False):
    """连接压测库。写操作必须传入与实际目标一致的 confirm_target，防止误连开发库或生产库。"""
    target = db_target()
    if not quiet:
        print(f"目标库: {target}", flush=True)
    if confirm_target is not None and confirm_target != target:
        sys.exit(f"--confirm-target 为 {confirm_target!r}，与实际目标 {target!r} 不一致，拒绝执行")
    return pymysql.connect(host=env("LT_MYSQL_HOST"), port=int(env("LT_MYSQL_PORT", "3306")),
                           user=env("LT_MYSQL_USER", "root"), password=env("LT_MYSQL_PASSWORD"),
                           database=env("LT_MYSQL_DB", "yoga"), charset="utf8mb4", autocommit=False,
                           cursorclass=pymysql.cursors.DictCursor)


def query(conn, sql, args=None):
    with conn.cursor() as cur:
        cur.execute(sql, args)
        return cur.fetchall()


def global_status(conn, *names):
    rows = query(conn, "SHOW GLOBAL STATUS WHERE Variable_name IN (" + ",".join(["%s"] * len(names)) + ")", names)
    return {r["Variable_name"]: int(r["Value"]) for r in rows}


def write_json(path, obj):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(obj, ensure_ascii=False, indent=1, default=str), encoding="utf-8")


def table_counts(conn):
    """各业务表总行数，以及落在 bulk_seed 压测区间内的行数。"""
    out = {}
    for t in CORE_TABLES + ["t_store", "t_classroom", "t_coach", "t_course"]:
        total = query(conn, f"SELECT COUNT(*) AS n FROM {t}")[0]["n"]
        base = BULK_BASE[t]
        bulk = query(conn, f"SELECT COUNT(*) AS n FROM {t} WHERE id BETWEEN %s AND %s",
                     (base, base + BULK_SPAN - 1))[0]["n"]
        out[t] = {"total": total, "bulk": bulk}
    conn.commit()
    return out
