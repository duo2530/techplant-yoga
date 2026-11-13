"""登录压测会员并把 token 写进 data/ 下的 JSON，压测时 k6 只读取该文件，登录不计入压测。

  --set seed（默认）：seed.py 的会员，写出 tokens.json，内容为 [会员ID, 会员卡ID, token]。
  --set bulk：bulk_seed.py 的可消耗池会员与部分历史会员，写出 tokens-bulk.json，附带 capacity.js 用的数据布局。
  --set history：seed.py --history-members 选出的历史会员，写出 tokens-history.json，格式同 tokens.json。
"""
import argparse
import json
import random
from concurrent.futures import ThreadPoolExecutor

import requests

from bulk_layout import PROFILES, hid, pid
from common import CARD_BASE, MEMBER_BASE, MEMBER_COUNT, MEMBER_PASSWORD, POOL_OFFSET, BULK_BASE, connect, data_dir,     env, query


def login(session, base, phone):
    resp = session.post(f"{base}/api/auth/login", json={"phone": phone, "password": MEMBER_PASSWORD}, timeout=30)
    body = resp.json()
    if resp.status_code != 200 or body.get("code") != 200:
        raise RuntimeError(f"会员 {phone} 登录失败: HTTP {resp.status_code} {body}")
    return body["data"]["token"]


def bulk_layout(conn):
    """从 lt_seed_progress 读出最近一次造数的规模与可消耗池布局。"""
    rows = {r["name"]: r for r in query(conn, "SELECT name, done, info FROM lt_seed_progress")}
    if "last_run" not in rows or "pool" not in rows:
        raise SystemExit("库内没有 bulk_seed 的造数记录，先运行 bulk_seed.py")
    last = json.loads(rows["last_run"]["info"])
    pool = json.loads(rows["pool"]["info"])
    p = PROFILES[last["profile"]]
    size = last["size"]
    return {
        "store_base": BULK_BASE["t_store"], "stores": size["stores"],
        "coach_base": BULK_BASE["t_coach"], "coaches_per_store": p["coaches_per_store"],
        "coaches": size["stores"] * p["coaches_per_store"],
        "schedule_base": BULK_BASE["t_schedule"], "schedules": size["schedules"], "history_members": size["members"],
        "pool_stores": pool["stores"], "pool_members_per_store": pool["members_per_store"],
        "pool_members": pool["members"], "reserve_schedules": pool["reserve_schedules"],
        "queue_schedules": pool["queue_schedules"], "queue_limit": pool["queue_limit"],
        "cancel": pool["cancel"], "checkin": pool["checkin"], "pool_refreshed": pool["refreshed"],
        "pool_schedule_base": pid("t_schedule", 0), "pool_card_base": pid("t_member_card", 0),
        "pool_reservation_base": pid("t_reservation", 0),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--set", choices=["seed", "bulk", "history"], default="seed")
    parser.add_argument("--history-sample", type=int, default=300, help="bulk：登录的历史会员数，用于“我的”读接口")
    parser.add_argument("--workers", type=int, default=16)
    a = parser.parse_args()
    base = env("LT_BASE_URL").rstrip("/")
    session = requests.Session()
    session.mount("http://", requests.adapters.HTTPAdapter(pool_maxsize=a.workers))
    with ThreadPoolExecutor(max_workers=a.workers) as pool:
        if a.set == "seed":
            tokens = list(pool.map(lambda i: [MEMBER_BASE + i, CARD_BASE + i, login(session, base, f"139{i:08d}")],
                                   range(MEMBER_COUNT)))
            path = data_dir() / "tokens.json"
            path.write_text(json.dumps(tokens), encoding="utf-8")
            print(f"已导出 {len(tokens)} 个 token 到 {path}")
            return
        if a.set == "history":
            rows = query(connect(), "SELECT info FROM lt_seed_progress WHERE name = 'a_history'")
            if not rows:
                raise SystemExit("库内没有场景 A 历史会员名单，先运行 seed.py full --scoped --history-members N")
            info = rows[0]["info"]
            plan = info if isinstance(info, dict) else json.loads(info)
            tokens = list(pool.map(lambda gm: [hid("t_member", gm), plan["card_base"] + gm,
                                               login(session, base, f"136{gm:08d}")], plan["members"]))
            path = data_dir() / "tokens-history.json"
            path.write_text(json.dumps(tokens), encoding="utf-8")
            print(f"已导出历史会员 {len(tokens)} 个 token 到 {path}")
            return
        layout = bulk_layout(connect())
        pool_tokens = list(pool.map(lambda m: login(session, base, f"137{m:08d}"), range(layout["pool_members"])))
        sample = sorted(random.Random(58).sample(range(layout["history_members"]),
                                                 min(a.history_sample, layout["history_members"])))
        history = list(pool.map(lambda gm: [hid("t_member", gm), login(session, base, f"136{gm:08d}")], sample))
    path = data_dir() / "tokens-bulk.json"
    path.write_text(json.dumps({"layout": layout, "pool": pool_tokens, "history": history}), encoding="utf-8")
    print(f"已导出可消耗池 {len(pool_tokens)} 个、历史会员 {len(history)} 个 token 到 {path}")


if __name__ == "__main__":
    main()
