"""bulk_seed 的数据布局：ID 区间、profile 参数与每节历史排课的确定性预约计划。

历史数据以排课为单位：第 s 节排课固定 slots 个预约位，预约序号 i 属于排课 i // slots 的第 i % slots 位。
同一随机种子与基准日期下，任何规模、任何一次续跑生成的同一序号的行都相同。
"""
import random
import uuid
from datetime import date, datetime, time, timedelta

from common import BULK_BASE, POOL_OFFSET

PROFILES = {
    # 以 1000 万预约为基准：50 店、20 万节排课、约 25 万会员、30 万张卡、约 1090 万条流水
    "booking-heavy": {
        "slots": 50,
        "schedules_per_store": 4000,
        "days_past": 180,
        "days_future": 14,
        "classrooms_per_store": 3,
        "coaches_per_store": 8,
        "courses": 12,
        # 会员窗口：第 k 节排课从 [k*5/4, k*5/4 + window) 的会员中选人，每个会员只出现在约 100 节排课里
        "member_window": 125,
        "member_step": (5, 4),
        "second_card_ratio": 0.2,
        # 预约位类别：占位、已扣次后取消、排队中取消、排队
        "p_seat": 0.78, "p_cancel_refund": 0.15, "p_cancel_queue": 0.05,
        # 已开始排课的占位里签到的比例，其余保持已预约（未到课）
        "p_checkin_past": 0.967,
        "queue_limit": 3,
    },
}

STATUS_RESERVED, STATUS_CANCELLED, STATUS_CHECKED_IN, STATUS_QUEUED, STATUS_QUEUE_FAILED = 1, 2, 3, 4, 5
SEAT, CANCEL_REFUND, CANCEL_QUEUE, QUEUE = 0, 1, 2, 3
COURSE_TYPES = [1, 1, 2, 4, 1, 2, 1, 4, 1, 2, 1, 1]
REGIONS = ["310101", "310104", "310105", "310106", "310107", "310109", "310110", "310112", "310113", "310115"]
YEAR_CARD_OFFSET = 50_000_000_000
# seed.py --history-members 给选中的历史会员补发的次数卡
HISTORY_CARD_OFFSET = 80_000_000_000

POOL_QUEUE_SCHEDULE = 1_000_000
POOL_CANCEL_SCHEDULE = 2_000_000
POOL_CHECKIN_SCHEDULE = 3_000_000
POOL_CHECKIN_RESERVATION = 100_000_000
POOL_HOLDER_RESERVATION = 200_000_000


def hid(table, n):
    return BULK_BASE[table] + n


def pid(table, n):
    return BULK_BASE[table] + POOL_OFFSET + n


def members_per_store(p):
    num, den = p["member_step"]
    return p["schedules_per_store"] * num // den + p["member_window"]


def store_row(b, now):
    return (hid("t_store", b), f"L{b:03d}", f"压测门店{b:03d}", 1 if b % 3 else 2, REGIONS[b % len(REGIONS)],
            "021-55550000", "周一至周日 06:00-22:00", f"压测路 {b + 1} 号", now, now)


def plan_schedule(p, seed, s, base_date):
    """第 s 节历史排课的属性与 slots 个预约位。类别与时间无关，保证续跑时占位人数与最大人数不变。"""
    rng = random.Random(f"{seed}:s:{s}")
    block, local = divmod(s, p["schedules_per_store"])
    days = p["days_past"] + p["days_future"]
    day, t = local % days, local // days
    rooms = p["classrooms_per_store"]
    start = datetime.combine(base_date + timedelta(days=day - p["days_past"]),
                             time(6 + 2 * (t // rooms), rng.choice((0, 15, 30))))
    end = start + timedelta(minutes=rng.choice((60, 75, 90)))
    course = (local * 5 + block) % p["courses"]

    cats = []
    for _ in range(p["slots"]):
        r = rng.random()
        if r < p["p_seat"]:
            cats.append(SEAT)
        elif r < p["p_seat"] + p["p_cancel_refund"]:
            cats.append(CANCEL_REFUND)
        elif r < p["p_seat"] + p["p_cancel_refund"] + p["p_cancel_queue"]:
            cats.append(CANCEL_QUEUE)
        else:
            cats.append(QUEUE)
    queued = 0
    for j, c in enumerate(cats):
        if c == QUEUE:
            queued += 1
            if queued > p["queue_limit"]:
                cats[j] = CANCEL_QUEUE
    n_seat = cats.count(SEAT)
    if n_seat == 0:
        cats = [CANCEL_QUEUE if c == QUEUE else c for c in cats]
    # 占位在前、排队在后：排课只写入一部分预约位时，有排队记录就一定已经满员
    cats.sort()
    has_queue = QUEUE in cats
    spare = rng.randint(0, 6)
    max_persons = max(1, n_seat if has_queue else n_seat + spare)

    num, den = p["member_step"]
    window_start = block * members_per_store(p) + local * num // den
    offset = rng.randrange(p["member_window"])
    slots = []
    for j, c in enumerate(cats):
        member = window_start + (j * 7 + offset) % p["member_window"]
        slots.append((c, member, rng.random(), rng.random(), rng.random(), rng.random(), rng.random()))
    return {
        "s": s, "block": block, "id": hid("t_schedule", s), "store_id": hid("t_store", block),
        "course_index": course, "course_id": hid("t_course", course), "course_type": COURSE_TYPES[course],
        "coach_id": hid("t_coach", block * p["coaches_per_store"] + t % p["coaches_per_store"]),
        "classroom_id": hid("t_classroom", block * rooms + t % rooms),
        "start": start, "end": end, "max_persons": max_persons, "slots": slots,
    }


def reservation_of(p, seed, plan, j, now):
    """预约位 j 在 now 时刻生成的预约行，以及需要写入的流水类别（deduct、refund）。"""
    cat, member, u_status, u_lead, u_cancel, u_checkin, _ = plan["slots"][j]
    start, end = plan["start"], plan["end"]
    started = start <= now
    reserved_at = start - timedelta(hours=1) - timedelta(days=7) * u_lead
    if reserved_at > now:
        reserved_at = now - timedelta(minutes=1) - timedelta(hours=1) * u_lead
    reserved_at = reserved_at.replace(microsecond=0)
    cancel_at = check_in_at = cancel_reason = None
    deduct = refund = False
    if cat == SEAT:
        status = STATUS_CHECKED_IN if started and u_status < p["p_checkin_past"] else STATUS_RESERVED
        deduct = True
        if status == STATUS_CHECKED_IN:
            check_in_at = (start - timedelta(minutes=25) * u_checkin).replace(microsecond=0)
    elif cat in (CANCEL_REFUND, CANCEL_QUEUE):
        status, cancel_reason = STATUS_CANCELLED, 1
        bound = min(start - timedelta(minutes=121), now)
        cancel_at = reserved_at + (bound - reserved_at) * u_cancel if bound > reserved_at \
            else reserved_at + timedelta(minutes=1)
        cancel_at = cancel_at.replace(microsecond=0)
        deduct = refund = cat == CANCEL_REFUND
    else:
        status = STATUS_QUEUE_FAILED if started else STATUS_QUEUED
    i = plan["s"] * p["slots"] + j
    request_id = str(uuid.UUID(int=(int(seed) % (1 << 32)) << 96 | i))
    row = (hid("t_reservation", i), request_id, hid("t_member", member), plan["id"], start, end, 1,
           hid("t_member_card", member), status, reserved_at, cancel_at, cancel_reason, check_in_at,
           reserved_at, reserved_at)
    return i, member, row, deduct, refund


def member_rows(seed, gm, store_id, base_date, password):
    """历史会员 gm 的会员行、主卡（本店次数卡）与可选的年卡。"""
    rng = random.Random(f"{seed}:m:{gm}")
    created = datetime.combine(base_date - timedelta(days=200 - rng.randrange(15)), time(9, rng.randrange(60)))
    phone = f"136{gm:08d}"
    member = (hid("t_member", gm), phone, password, phone[-4:], f"压测会员{gm}", rng.randrange(3), rng.randrange(5),
              created, created)
    valid_start = datetime.combine(created.date(), time())
    valid_end = datetime.combine((valid_start + timedelta(days=365)).date(), time(23, 59, 59))
    purchase, gift = rng.choice((100, 120, 150)), rng.randrange(11)
    card_id = hid("t_member_card", gm)
    cards = [(card_id, member[0], store_id, f"LTB{card_id}", f"团课次卡{purchase}次", 1, 1, store_id, purchase, gift,
              purchase + gift, 1, 12, valid_start, valid_end, created, created, created)]
    if rng.random() < 0.2:
        year_id = hid("t_member_card", YEAR_CARD_OFFSET + gm)
        cards.append((year_id, member[0], store_id, f"LTB{year_id}", "团课年卡", 2, 1, None, None, None, None, 2, 1,
                      valid_start, valid_end, created, created, created))
    return member, cards


def history_sample(p, members, n, seed, store=0):
    """从门店 store 的历史会员中按种子选出 n 个，返回升序的会员序号。"""
    mps = members_per_store(p)
    candidates = range(store * mps, min(members, (store + 1) * mps))
    return sorted(random.Random(f"{seed}:a-history:{store}").sample(candidates, min(n, len(candidates))))


ROLLING_OFFSET = 10_000_000_000
ROLLING_EPOCH = date(2026, 1, 1)


def rolling_schedules(p, seed, stores, day, created, per_store):
    """基准日期后、历史排课没有覆盖到的日期 day 上，每店一天的排课（不带预约）。

    编号按日期与门店确定，同一天重复生成得到相同的行，配合 INSERT IGNORE 幂等补齐。
    每店每天的节数与历史排课的日均节数一致（per_store 为每店历史排课数），保持读接口的数据密度。
    """
    rooms = p["classrooms_per_store"]
    per_day = max(1, -(-min(per_store, p["schedules_per_store"]) // (p["days_past"] + p["days_future"])))
    n = (day - ROLLING_EPOCH).days
    rows = []
    for b in range(stores):
        for t in range(per_day):
            rng = random.Random(f"{seed}:r:{n}:{b}:{t}")
            start = datetime.combine(day, time(6 + 2 * (t // rooms), rng.choice((0, 15, 30))))
            course = (n * 5 + b + t) % p["courses"]
            rows.append({"id": hid("t_schedule", ROLLING_OFFSET + n * 100_000 + b * 100 + t),
                         "store_id": hid("t_store", b), "course_id": hid("t_course", course),
                         "course_type": COURSE_TYPES[course],
                         "coach_id": hid("t_coach", b * p["coaches_per_store"] + t % p["coaches_per_store"]),
                         "classroom_id": hid("t_classroom", b * rooms + t % rooms), "start": start,
                         "end": start + timedelta(minutes=rng.choice((60, 75, 90))),
                         "max_persons": rng.randint(20, 40), "created": created})
    return rows
