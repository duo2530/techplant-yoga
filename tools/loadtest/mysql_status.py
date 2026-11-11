"""只读输出压测库的配置与运行状态：版本、binlog、隔离级别、行锁计数器与 InnoDB 最近一次死锁。"""
from common import connect, query

VARIABLES = ("version", "binlog_format", "log_bin", "transaction_isolation", "innodb_buffer_pool_size",
             "max_connections", "innodb_flush_log_at_trx_commit", "sync_binlog", "innodb_lock_wait_timeout")


def main():
    conn = connect()
    rows = query(conn, "SHOW GLOBAL VARIABLES WHERE Variable_name IN (" + ",".join(["%s"] * len(VARIABLES)) + ")",
                 VARIABLES)
    for r in rows:
        print(f"{r['Variable_name']} = {r['Value']}")
    for r in query(conn, "SHOW GLOBAL STATUS WHERE Variable_name LIKE 'Innodb_row_lock%%'"
                         " OR Variable_name IN ('Uptime', 'Threads_connected', 'Max_used_connections')"):
        print(f"{r['Variable_name']} = {r['Value']}")
    status = query(conn, "SHOW ENGINE INNODB STATUS")[0]["Status"]
    start = status.find("LATEST DETECTED DEADLOCK")
    print("最近一次死锁:", "无" if start < 0 else status[start:status.find("TRANSACTIONS", start)][:3000])


if __name__ == "__main__":
    main()
