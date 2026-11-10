"""对账：执行与 ReservationConcurrencyTest 相同的不变量 SQL，每条都应查不到行；另输出预约与流水汇总。"""
import json
import sys

from common import B_SCHEDULE_ID, connect, query

INVARIANTS = {
    "排课占位等于有效预约人数": "SELECT s.id, s.booked_persons, (SELECT COALESCE(SUM(r.persons), 0)"
    " FROM t_reservation r WHERE r.schedule_id = s.id AND r.status IN (1, 3)) AS seated FROM t_schedule s"
    " HAVING s.booked_persons <> seated",
    "已预约人数不超过最大人数":
    "SELECT id, booked_persons, max_persons FROM t_schedule WHERE booked_persons > max_persons",
    "同一会员同一排课至多一条有效记录": "SELECT member_id, schedule_id, COUNT(*) AS open_count"
    " FROM t_reservation WHERE status IN (1, 3, 4) GROUP BY member_id, schedule_id HAVING COUNT(*) > 1",
    "次数卡剩余次数非负": "SELECT id, remaining_times FROM t_member_card WHERE remaining_times < 0",
    "流水余额守恒": "SELECT * FROM t_member_card_transaction WHERE after_times <> before_times + change_times",
    "占位的次数卡预约恰有一条等额扣次流水": "SELECT r.id, r.member_id, r.schedule_id, r.status, r.persons"
    " FROM t_reservation r JOIN t_member_card c ON c.id = r.member_card_id"
    " WHERE c.card_type = 1 AND r.status IN (1, 3) AND (SELECT COUNT(*) FROM t_member_card_transaction t"
    " WHERE t.reservation_id = r.id AND t.change_type = 1 AND t.change_times = -r.persons) <> 1",
    "已取消且扣过次的预约已等额返次": "SELECT r.id, r.member_id, r.schedule_id, r.reservation_time,"
    " r.cancel_time, d.change_times AS deduct_times, d.create_time AS deduct_time FROM t_reservation r"
    " JOIN t_member_card_transaction d ON d.reservation_id = r.id AND d.change_type = 1"
    " WHERE r.status = 2 AND NOT EXISTS (SELECT 1 FROM t_member_card_transaction t"
    " WHERE t.reservation_id = r.id AND t.change_type = 2 AND t.change_times = -d.change_times)",
    "每条预约至多一条返次流水": "SELECT reservation_id, COUNT(*) AS refund_count FROM t_member_card_transaction"
    " WHERE change_type = 2 GROUP BY reservation_id HAVING COUNT(*) > 1",
    "排队中与排队失败没有次数流水": "SELECT r.id, r.status FROM t_reservation r WHERE r.status IN (4, 5)"
    " AND EXISTS (SELECT 1 FROM t_member_card_transaction t WHERE t.reservation_id = r.id)",
    "次数卡余额等于初始次数加流水变动": "SELECT c.id, c.purchase_times + c.gift_times AS initial_times,"
    " c.remaining_times, (SELECT COALESCE(SUM(t.change_times), 0) FROM t_member_card_transaction t"
    " WHERE t.member_card_id = c.id) AS ledger_change FROM t_member_card c WHERE c.card_type = 1"
    " HAVING c.remaining_times <> initial_times + ledger_change",
    "有空位时不应仍有人排队": "SELECT s.id, s.booked_persons, s.max_persons FROM t_schedule s"
    " WHERE s.booked_persons < s.max_persons"
    " AND EXISTS (SELECT 1 FROM t_reservation r WHERE r.schedule_id = s.id AND r.status = 4)",
    "排队人数不超过上限": "SELECT s.id, s.queue_limit FROM t_schedule s WHERE s.queue_limit <"
    " (SELECT COUNT(*) FROM t_reservation r WHERE r.schedule_id = s.id AND r.status = 4)",
}

TOTALS = {
    "预约按状态": "SELECT status, COUNT(*) AS n, SUM(persons) AS persons FROM t_reservation GROUP BY status",
    "扣次流水": "SELECT change_type, COUNT(*) AS n, SUM(change_times) AS times FROM t_member_card_transaction"
    " GROUP BY change_type",
    "排课占位合计": "SELECT SUM(booked_persons) AS booked FROM t_schedule",
    "场景B排课": f"SELECT id, max_persons, booked_persons, (SELECT COUNT(*) FROM t_reservation r"
    f" WHERE r.schedule_id = {B_SCHEDULE_ID} AND r.status = 1) AS reserved FROM t_schedule WHERE id = {B_SCHEDULE_ID}",
}


def reconcile(conn):
    violations = {}
    for name, sql in INVARIANTS.items():
        rows = query(conn, sql)
        violations[name] = {"count": len(rows), "sample": rows[:5]}
    totals = {name: query(conn, sql) for name, sql in TOTALS.items()}
    conn.commit()
    return {"violations": violations, "violation_total": sum(v["count"] for v in violations.values()),
            "totals": totals}


def main():
    result = reconcile(connect())
    for name, v in result["violations"].items():
        print(f"[{'OK' if v['count'] == 0 else 'FAIL'}] {name}: {v['count']} 行")
    print(json.dumps(result["totals"], ensure_ascii=False, default=str))
    sys.exit(1 if result["violation_total"] else 0)


if __name__ == "__main__":
    main()
