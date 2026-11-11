"""按 SQL 摘要统计 MySQL 语句耗时，定位随数据量变慢的语句。

  sql_stats.py reset            清空 performance_schema 的语句摘要统计（只清统计，不动业务数据）
  sql_stats.py dump --name X    输出业务表相关语句的次数、平均与最大耗时、扫描行数，写入 results/sql/X.json
  sql_stats.py explain          对预约写路径上的查询执行 EXPLAIN，并输出各表行数与索引
"""
import argparse
import json

from common import MEMBER_BASE, SCHEDULE_BASE, connect, data_dir, query, write_json

DIGESTS = ("SELECT DIGEST_TEXT AS sql_text, COUNT_STAR AS calls, ROUND(AVG_TIMER_WAIT / 1e9, 3) AS avg_ms,"
           " ROUND(MAX_TIMER_WAIT / 1e9, 3) AS max_ms, ROUND(SUM_TIMER_WAIT / 1e12, 3) AS total_s,"
           " ROUND(SUM_LOCK_TIME / 1e12, 3) AS lock_s, SUM_ROWS_EXAMINED AS rows_examined,"
           " ROUND(SUM_ROWS_EXAMINED / COUNT_STAR, 1) AS rows_examined_avg, SUM_NO_INDEX_USED AS no_index"
           " FROM performance_schema.events_statements_summary_by_digest"
           " WHERE SCHEMA_NAME = 'yoga' AND DIGEST_TEXT REGEXP 't_(reservation|member|schedule|course)'"
           " ORDER BY SUM_TIMER_WAIT DESC LIMIT 25")

PROBE_MEMBER = MEMBER_BASE + 7
PROBE_SCHEDULE = SCHEDULE_BASE + 7
EXPLAINS = {
    "幂等键查询": f"SELECT * FROM t_reservation WHERE member_id = {PROBE_MEMBER} AND request_id = 'x' LIMIT 1",
    "同人同课有效预约（加锁）": f"SELECT * FROM t_reservation WHERE member_id = {PROBE_MEMBER}"
    f" AND schedule_id = {PROBE_SCHEDULE} AND status IN (1, 3, 4) LIMIT 1 FOR UPDATE",
    "排课加锁": f"SELECT * FROM t_schedule WHERE id = {PROBE_SCHEDULE} FOR UPDATE",
}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("mode", choices=["reset", "dump", "explain"])
    p.add_argument("--name", default="latest")
    a = p.parse_args()
    conn = connect()
    if a.mode == "reset":
        query(conn, "TRUNCATE TABLE performance_schema.events_statements_summary_by_digest")
        print("已清空语句摘要统计")
        return
    if a.mode == "dump":
        rows = query(conn, DIGESTS)
        counts = {t: query(conn, f"SELECT COUNT(*) AS n FROM {t}")[0]["n"]
                  for t in ("t_reservation", "t_member_card_transaction")}
        write_json(data_dir() / "results" / "sql" / f"{a.name}.json", {"table_rows": counts, "digests": rows})
        print(json.dumps(counts, ensure_ascii=False))
        for r in rows:
            print(f"{r['calls']:>7} 次  avg {r['avg_ms']:>8} ms  max {r['max_ms']:>9} ms  total {r['total_s']:>7} s"
                  f"  lock {r['lock_s']:>6} s  扫描/次 {r['rows_examined_avg']:>8}  {r['sql_text'][:160]}")
        return
    for t in ("t_reservation", "t_member_card_transaction"):
        print(f"{t}: {query(conn, f'SELECT COUNT(*) AS n FROM {t}')[0]['n']} 行")
        for r in query(conn, f"SHOW INDEX FROM {t}"):
            print(f"  索引 {r['Key_name']} 列 {r['Column_name']} 序号 {r['Seq_in_index']} 基数 {r['Cardinality']}")
    for name, sql in EXPLAINS.items():
        print(f"== {name}")
        for r in query(conn, "EXPLAIN " + sql):
            print("  ", {k: r[k] for k in ("table", "type", "possible_keys", "key", "key_len", "rows", "filtered", "Extra")})
    conn.rollback()


if __name__ == "__main__":
    main()
