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


def connect(confirm_target=None):
    """连接压测库。写操作必须传入与实际目标一致的 confirm_target，防止误连开发库或生产库。"""
    target = db_target()
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
