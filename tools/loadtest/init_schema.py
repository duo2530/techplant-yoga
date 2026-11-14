"""在空的压测库上按顺序执行镜像内的建表脚本（ry_20260417.sql、quartz.sql、yoga.sql）。

  python -X utf8 init_schema.py --confirm-target <host:port/db>

用于只能从 VPC 内访问的 RDS：由压测机执行。库里已有表时拒绝执行，不会覆盖已有数据。
SQL 目录默认取 LT_SQL_DIR（镜像内为 /loadtest/sql）。
"""
import argparse
import sys
from pathlib import Path

import pymysql
from pymysql.constants import CLIENT

from common import connect, db_target, env, query

FILES = ["ry_20260417.sql", "quartz.sql", "yoga.sql"]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--confirm-target", required=True)
    p.add_argument("--sql-dir", default=env("LT_SQL_DIR", "/loadtest/sql"))
    a = p.parse_args()
    paths = [Path(a.sql_dir) / f for f in FILES]
    missing = [str(x) for x in paths if not x.is_file()]
    if missing:
        sys.exit(f"缺少 SQL 文件: {missing}")
    conn = connect(a.confirm_target)
    tables = query(conn, "SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema = DATABASE()")[0]["n"]
    conn.close()
    if tables:
        sys.exit(f"{db_target()} 已有 {tables} 张表，拒绝执行")
    conn = pymysql.connect(host=env("LT_MYSQL_HOST"), port=int(env("LT_MYSQL_PORT", "3306")),
                           user=env("LT_MYSQL_USER", "root"), password=env("LT_MYSQL_PASSWORD"),
                           database=env("LT_MYSQL_DB", "yoga"), charset="utf8mb4", autocommit=True,
                           client_flag=CLIENT.MULTI_STATEMENTS)
    for path in paths:
        with conn.cursor() as cur:
            cur.execute(path.read_text(encoding="utf-8"))
            while cur.nextset():
                pass
        print(f"已执行 {path.name}", flush=True)
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")
        print(f"共 {cur.fetchone()[0]} 张表", flush=True)
        cur.execute("SELECT @@version, @@character_set_server, @@character_set_database, @@log_bin, @@binlog_format,"
                    " @@innodb_flush_log_at_trx_commit, @@sync_binlog, @@innodb_buffer_pool_size,"
                    " @@transaction_isolation")
        names = ["version", "character_set_server", "character_set_database", "log_bin", "binlog_format",
                 "innodb_flush_log_at_trx_commit", "sync_binlog", "innodb_buffer_pool_size", "transaction_isolation"]
        for k, v in zip(names, cur.fetchone()):
            print(f"{k} = {v}")
    conn.close()


if __name__ == "__main__":
    main()
