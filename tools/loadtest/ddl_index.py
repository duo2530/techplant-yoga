"""在压测库上增删一个二级索引，用于索引改动的前后交替对比。

  ddl_index.py add  --table t_reservation --index idx_x --columns member_id,schedule_id --confirm-target <host:port/db>
  ddl_index.py drop --table t_reservation --index idx_x --confirm-target <host:port/db>

已是目标状态时跳过。完成后执行 ANALYZE TABLE，并打印该表现有的索引。
"""
import argparse
import re
import sys
import time

from common import connect, query

IDENT = re.compile(r"^[a-z][a-z0-9_]{0,63}$")


def ident(text):
    if not IDENT.match(text):
        raise argparse.ArgumentTypeError(f"非法标识符: {text!r}")
    return text


def main():
    p = argparse.ArgumentParser()
    p.add_argument("action", choices=["add", "drop"])
    p.add_argument("--table", type=ident, required=True)
    p.add_argument("--index", type=ident, required=True)
    p.add_argument("--columns", help="add 时必填，逗号分隔")
    p.add_argument("--confirm-target", required=True)
    a = p.parse_args()
    if not a.table.startswith("t_"):
        sys.exit("只允许操作业务表 t_*")
    columns = [ident(c) for c in (a.columns or "").split(",") if c]
    if a.action == "add" and not columns:
        sys.exit("add 需要 --columns")

    conn = connect(a.confirm_target)
    conn.autocommit(True)
    exists = query(conn, "SELECT COUNT(*) AS n FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                         " AND TABLE_NAME = %s AND INDEX_NAME = %s", (a.table, a.index))[0]["n"] > 0
    t0 = time.time()
    if a.action == "add" and not exists:
        cols = ", ".join(f"`{c}`" for c in columns)
        query(conn, f"ALTER TABLE `{a.table}` ADD INDEX `{a.index}` ({cols}), ALGORITHM=INPLACE, LOCK=NONE")
    elif a.action == "drop" and exists:
        query(conn, f"ALTER TABLE `{a.table}` DROP INDEX `{a.index}`, ALGORITHM=INPLACE, LOCK=NONE")
    else:
        print(f"{a.index} 已是目标状态（{'存在' if exists else '不存在'}），跳过")
    query(conn, f"ANALYZE TABLE `{a.table}`")
    print(f"{a.action} {a.table}.{a.index} 用时 {time.time() - t0:.1f} 秒")
    for r in query(conn, "SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols"
                         " FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s"
                         " GROUP BY INDEX_NAME", (a.table,)):
        print(f"  {r['INDEX_NAME']}: {r['cols']}")


if __name__ == "__main__":
    main()
