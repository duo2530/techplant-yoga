"""用造数的会员逐个调用 /api/auth/login，把 [会员ID, 会员卡ID, token] 写进 tokens.json，压测时 k6 只读取该文件。"""
import json
from concurrent.futures import ThreadPoolExecutor

import requests

from common import CARD_BASE, MEMBER_BASE, MEMBER_COUNT, MEMBER_PASSWORD, data_dir, env


def login(session, base, i):
    resp = session.post(f"{base}/api/auth/login", json={"phone": f"139{i:08d}", "password": MEMBER_PASSWORD},
                        timeout=30)
    body = resp.json()
    if resp.status_code != 200 or body.get("code") != 200:
        raise RuntimeError(f"会员 {i} 登录失败: HTTP {resp.status_code} {body}")
    return [MEMBER_BASE + i, CARD_BASE + i, body["data"]["token"]]


def main():
    base = env("LT_BASE_URL").rstrip("/")
    session = requests.Session()
    session.mount("http://", requests.adapters.HTTPAdapter(pool_maxsize=16))
    with ThreadPoolExecutor(max_workers=16) as pool:
        tokens = list(pool.map(lambda i: login(session, base, i), range(MEMBER_COUNT)))
    path = data_dir() / "tokens.json"
    path.write_text(json.dumps(tokens), encoding="utf-8")
    print(f"已导出 {len(tokens)} 个 token 到 {path}")


if __name__ == "__main__":
    main()
