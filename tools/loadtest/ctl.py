"""本机控制端：经 CLB 向压测机的 agent.py 下发任务并等待结果。

  python -X utf8 ctl.py --agent http://<CLB IP>:8000 run <脚本> [参数...]
  python -X utf8 ctl.py --agent http://<CLB IP>:8000 fetch <LT_DATA_DIR 下的相对路径> <本机保存路径>

口令从环境变量 LT_AGENT_TOKEN 读取，或从 --env-file 指向的文件里的 YOGALT_AGENT_TOKEN 读取。
"""
import argparse
import os
import sys
import time
from pathlib import Path

import requests


def token(env_file):
    if os.environ.get("LT_AGENT_TOKEN"):
        return os.environ["LT_AGENT_TOKEN"]
    for line in Path(env_file).read_text(encoding="utf-8").splitlines():
        if line.startswith("YOGALT_AGENT_TOKEN="):
            return line.split("=", 1)[1].strip()
    sys.exit("缺少 LT_AGENT_TOKEN")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--agent", required=True)
    p.add_argument("--env-file", default=str(Path(__file__).resolve().parents[2] / ".env"))
    sub = p.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("script")
    r.add_argument("args", nargs=argparse.REMAINDER)
    f = sub.add_parser("fetch")
    f.add_argument("remote")
    f.add_argument("local")
    a = p.parse_args()
    base, headers = a.agent.rstrip("/"), {"X-LT-Token": token(a.env_file)}

    if a.cmd == "fetch":
        resp = requests.get(f"{base}/files/{a.remote}", headers=headers, timeout=60)
        resp.raise_for_status()
        Path(a.local).parent.mkdir(parents=True, exist_ok=True)
        Path(a.local).write_text(resp.json()["content"], encoding="utf-8")
        print(f"已保存 {a.local}")
        return

    resp = requests.post(f"{base}/jobs", json={"script": a.script, "args": a.args}, headers=headers, timeout=30)
    resp.raise_for_status()
    job = resp.json()["id"]
    printed = 0
    while True:
        state = requests.get(f"{base}/jobs/{job}", headers=headers, timeout=30).json()
        out = state["output"]
        size = state.get("size", len(out))
        if size > printed:
            sys.stdout.write(out[-(size - printed):] if size - printed <= len(out) else out)
            sys.stdout.flush()
            printed = size
        if not state["running"]:
            sys.exit(state["exit"])
        time.sleep(5)


if __name__ == "__main__":
    main()
