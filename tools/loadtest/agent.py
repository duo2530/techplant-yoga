"""压测机上的控制服务：只运行本目录下白名单内的脚本，供本机经 CLB 下发任务、取回结果。

请求头 X-LT-Token 必须等于 LT_AGENT_TOKEN。
  POST /jobs            {"script": "seed", "args": ["reset", "--confirm-target", "..."]} → {"id": ...}
  GET  /jobs/<id>       → 状态、退出码、输出末尾
  GET  /files/<相对路径>  → LT_DATA_DIR 下的文件内容
"""
import hmac
import json
import subprocess
import sys
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from common import data_dir, env

LT_DIR = Path(__file__).resolve().parent
SCRIPTS = {"check_target", "seed", "bulk_seed", "export_tokens", "run_scenario", "reconcile", "mysql_status",
           "sql_stats", "init_schema"}
# 写库或施压的脚本同一时间只允许一个在跑
EXCLUSIVE = {"seed", "bulk_seed", "run_scenario", "init_schema"}
JOBS = {}
LOCK = threading.Lock()


def start_job(script, args):
    job_id = uuid.uuid4().hex[:12]
    log = data_dir() / "jobs" / f"{job_id}.log"
    log.parent.mkdir(parents=True, exist_ok=True)
    handle = open(log, "w", encoding="utf-8")
    proc = subprocess.Popen([sys.executable, "-X", "utf8", "-u", str(LT_DIR / f"{script}.py"), *args],
                            cwd=LT_DIR, stdout=handle, stderr=subprocess.STDOUT)
    with LOCK:
        JOBS[job_id] = {"script": script, "args": args, "proc": proc, "log": log}
    return job_id


class Handler(BaseHTTPRequestHandler):
    def reply(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def authorized(self):
        if hmac.compare_digest(self.headers.get("X-LT-Token", ""), env("LT_AGENT_TOKEN")):
            return True
        self.reply(401, {"error": "unauthorized"})
        return False

    def do_POST(self):
        if not self.authorized():
            return
        if self.path != "/jobs":
            return self.reply(404, {"error": "not found"})
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        script, args = body.get("script"), body.get("args", [])
        if script not in SCRIPTS or not all(isinstance(x, str) for x in args):
            return self.reply(400, {"error": f"script 只能是 {sorted(SCRIPTS)}，args 为字符串数组"})
        if script in EXCLUSIVE:
            with LOCK:
                busy = [k for k, j in JOBS.items() if j["script"] in EXCLUSIVE and j["proc"].poll() is None]
            if busy:
                return self.reply(409, {"error": f"任务 {busy} 仍在运行，等它结束后再提交"})
        self.reply(200, {"id": start_job(script, args)})

    def do_GET(self):
        if self.path == "/health":
            return self.reply(200, {"ok": True})
        if not self.authorized():
            return
        if self.path.startswith("/jobs/"):
            job = JOBS.get(self.path[len("/jobs/"):])
            if not job:
                return self.reply(404, {"error": "no such job"})
            code = job["proc"].poll()
            text = job["log"].read_text(encoding="utf-8", errors="replace")
            return self.reply(200, {"script": job["script"], "args": job["args"],
                                    "running": code is None, "exit": code, "output": text[-20000:],
                                    "size": len(text)})
        if self.path.startswith("/files/"):
            root = data_dir().resolve()
            target = (root / self.path[len("/files/"):]).resolve()
            if root not in target.parents or not target.is_file():
                return self.reply(404, {"error": "no such file"})
            return self.reply(200, {"path": str(target.relative_to(root)),
                                    "content": target.read_text(encoding="utf-8", errors="replace")})
        self.reply(404, {"error": "not found"})

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    env("LT_AGENT_TOKEN")
    ThreadingHTTPServer(("0.0.0.0", int(env("LT_AGENT_PORT", "8000"))), Handler).serve_forever()
