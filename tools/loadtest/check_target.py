"""只读检查压测库：打印目标地址、实例身份、版本与 binlog_format，以及业务表行数。造数前先运行它确认目标。"""
from common import connect, query

TABLES = ["t_member", "t_member_card", "t_schedule", "t_reservation", "t_member_card_transaction"]


def main():
    conn = connect()
    info = query(conn, "SELECT @@hostname AS hostname, @@server_uuid AS server_uuid, @@version AS version,"
                       " @@binlog_format AS binlog_format, @@transaction_isolation AS isolation,"
                       " @@innodb_buffer_pool_size AS buffer_pool, @@max_connections AS max_connections,"
                       " @@innodb_flush_log_at_trx_commit AS flush_log, @@sync_binlog AS sync_binlog")[0]
    for k, v in info.items():
        print(f"{k}: {v}")
    for t in TABLES:
        print(f"{t}: {query(conn, f'SELECT COUNT(*) AS n FROM {t}')[0]['n']} 行")


if __name__ == "__main__":
    main()
