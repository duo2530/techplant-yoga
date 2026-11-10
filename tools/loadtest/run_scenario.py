"""运行一个压测场景：记录 MySQL 计数器 → 后台采样监控 → k6 → 再记录计数器 → 对账，结果写入 results/<名称>/。

  A / WARMUP：恒定到达率 --rate 持续 --duration
  B：--b-members 个会员在 --b-seconds 秒内抢同一节 20 人排课
  C：从 --rate 起每级加 --step，每级 --duration，某级 p95 超过 --p95-limit-ms、错误率达到 --error-limit
     或到达率不足目标的 99% 时停止，该级的前一级即拐点
"""
import argparse
import json
import subprocess
import sys
import threading
import time
from pathlib import Path

import requests

from common import (B_SCHEDULE_ID, SCHEDULE_BASE, SCHEDULE_COUNT, connect, data_dir, env, global_status, query,
                    write_json)
from reconcile import reconcile

LT_DIR = Path(__file__).resolve().parent
LOCK_COUNTERS = ("Innodb_row_lock_waits", "Innodb_row_lock_time", "Innodb_row_lock_time_max",
                 "Innodb_row_lock_current_waits", "Com_commit", "Com_rollback", "Innodb_rows_inserted",
                 "Innodb_rows_updated", "Innodb_data_fsyncs", "Innodb_os_log_fsyncs", "Threads_running",
                 "Threads_connected")
TOMCAT_MBEAN = 'Tomcat:type=ThreadPool,name="http-nio-8080"'


class Sampler(threading.Thread):
    """每 interval 秒采一次后端 Druid 连接池、Tomcat 线程池、JVM CPU 与 MySQL 运行状态，写入 jsonl。"""

    def __init__(self, path, interval=2.0):
        super().__init__(daemon=True)
        self.path, self.interval, self.stop = path, interval, threading.Event()
        self.backend = env("LT_BASE_URL").rstrip("/")
        self.jolokia = env("LT_JOLOKIA_URL").rstrip("/")
        self.http = requests.Session()
        self.druid_login()

    def druid_login(self):
        self.http.post(f"{self.backend}/druid/submitLogin", timeout=5,
                       data={"loginUsername": env("LT_DRUID_USER", "ruoyi"),
                             "loginPassword": env("LT_DRUID_PASSWORD", "123456")})

    def druid(self):
        content = self.http.get(f"{self.backend}/druid/datasource.json", timeout=5).json()["Content"][0]
        return {k: content.get(k) for k in ("ActiveCount", "ActivePeak", "PoolingCount", "WaitThreadCount",
                                             "NotEmptyWaitCount", "NotEmptyWaitMillis", "MaxActive",
                                             "ConnectCount", "ErrorCount")}

    def jmx(self, mbean, attrs):
        r = self.http.post(self.jolokia + "/", timeout=5,
                           json=[{"type": "read", "mbean": mbean, "attribute": a} for a in attrs]).json()
        return {a: x.get("value") for a, x in zip(attrs, r)}

    def sample(self, conn):
        row = {"t": time.time()}
        for name, fn in (("druid", self.druid),
                         ("tomcat", lambda: self.jmx(TOMCAT_MBEAN, ["currentThreadsBusy", "currentThreadCount",
                                                                    "maxThreads", "connectionCount"])),
                         ("jvm", lambda: self.jmx("java.lang:type=OperatingSystem",
                                                  ["ProcessCpuLoad", "SystemCpuLoad", "AvailableProcessors"])),
                         ("threads", lambda: self.jmx("java.lang:type=Threading", ["ThreadCount"])),
                         ("mysql", lambda: global_status(conn, "Threads_running", "Threads_connected",
                                                         "Innodb_row_lock_current_waits"))):
            try:
                row[name] = fn()
            except Exception as e:
                row[name] = {"error": str(e)[:200]}
        return row

    def run(self):
        conn = connect()
        conn.autocommit(True)
        with open(self.path, "a", encoding="utf-8") as f:
            while not self.stop.is_set():
                started = time.time()
                f.write(json.dumps(self.sample(conn), ensure_ascii=False) + "\n")
                f.flush()
                self.stop.wait(max(0.0, self.interval - (time.time() - started)))
        conn.close()


def summarize_samples(path):
    rows = [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]
    out = {"samples": len(rows)}
    fields = {"druid": ["ActiveCount", "WaitThreadCount", "PoolingCount"],
              "tomcat": ["currentThreadsBusy", "currentThreadCount", "connectionCount"],
              "jvm": ["ProcessCpuLoad", "SystemCpuLoad"], "threads": ["ThreadCount"],
              "mysql": ["Threads_running", "Threads_connected", "Innodb_row_lock_current_waits"]}
    for group, keys in fields.items():
        for key in keys:
            values = [r[group][key] for r in rows if isinstance(r.get(group), dict)
                      and isinstance(r[group].get(key), (int, float))]
            if values:
                values.sort()
                out[f"{group}.{key}"] = {"max": values[-1], "avg": round(sum(values) / len(values), 3),
                                         "p50": values[len(values) // 2], "n": len(values)}
    errors = [r for r in rows if any(isinstance(v, dict) and "error" in v for v in r.values())]
    out["sample_errors"] = len(errors)
    if errors:
        out["first_sample_error"] = errors[0]
    druid = [r["druid"] for r in rows if isinstance(r.get("druid"), dict) and "NotEmptyWaitCount" in r["druid"]]
    if len(druid) >= 2:
        out["druid.NotEmptyWaitCount.delta"] = druid[-1]["NotEmptyWaitCount"] - druid[0]["NotEmptyWaitCount"]
        out["druid.NotEmptyWaitMillis.delta"] = druid[-1]["NotEmptyWaitMillis"] - druid[0]["NotEmptyWaitMillis"]
        out["druid.ActivePeak.end"] = druid[-1]["ActivePeak"]
    return out


def k6_metrics(summary):
    m = summary["metrics"]

    def val(name, key="count"):
        return (m.get(name) or {}).get(key, 0)

    d = m["http_req_duration"]
    return {
        "http_reqs": val("http_reqs"), "qps": round(val("http_reqs", "rate"), 2),
        "iterations": val("iterations"), "dropped_iterations": val("dropped_iterations"),
        "p50_ms": round(d["med"], 2), "p90_ms": round(d["p(90)"], 2), "p95_ms": round(d["p(95)"], 2),
        "p99_ms": round(d["p(99)"], 2), "max_ms": round(d["max"], 2), "avg_ms": round(d["avg"], 2),
        "error_rate": (m.get("errors") or {}).get("value", 0), "errors": (m.get("errors") or {}).get("passes", 0),
        "reserved": val("reserved"), "unexpected": val("unexpected"), "conflict_total": val("conflict_total"),
        "conflicts": {k: val(k) for k in m if k.startswith("conflict_") and k != "conflict_total"},
        "reserved_p95_ms": round((m.get("reserved_duration") or {}).get("p(95)", 0), 2),
        "vus_max": val("vus_max", "value"),
    }


def run_k6(out_dir, env_vars):
    out_dir.mkdir(parents=True, exist_ok=True)
    cmd = ["k6", "run", "--quiet", "--no-color", "--summary-export", str(out_dir / "k6-summary.json"),
           str(LT_DIR / "reservation.js")]
    for k, v in env_vars.items():
        cmd[2:2] = ["-e", f"{k}={v}"]
    with open(out_dir / "k6-stdout.txt", "w", encoding="utf-8") as log:
        code = subprocess.run(cmd, stdout=log, stderr=subprocess.STDOUT).returncode
    summary = json.loads((out_dir / "k6-summary.json").read_text(encoding="utf-8"))
    return code, k6_metrics(summary)


def run_once(name, k6_env):
    out = data_dir() / "results" / name
    out.mkdir(parents=True, exist_ok=True)
    conn = connect()
    before = global_status(conn, *LOCK_COUNTERS)
    sampler = Sampler(out / "samples.jsonl")
    sampler.start()
    started = time.time()
    code, metrics = run_k6(out, k6_env)
    ended = time.time()
    sampler.stop.set()
    sampler.join(timeout=10)
    after = global_status(conn, *LOCK_COUNTERS)
    delta = {k: after[k] - before[k] for k in LOCK_COUNTERS if not k.startswith(("Threads_", "Innodb_row_lock_c"))}
    waits = delta["Innodb_row_lock_waits"]
    delta["row_lock_time_avg_ms_in_run"] = round(delta["Innodb_row_lock_time"] / waits, 2) if waits else 0
    delta["Innodb_row_lock_time_max_global"] = after["Innodb_row_lock_time_max"]
    status_avg = query(conn, "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_time_avg'")[0]["Value"]
    result = {"name": name, "k6_exit": code, "env": k6_env, "started": started, "ended": ended,
              "k6": metrics, "mysql_delta": delta, "Innodb_row_lock_time_avg_global": int(status_avg),
              "monitor": summarize_samples(out / "samples.jsonl")}
    write_json(out / "result.json", result)
    print(json.dumps({"name": name, "k6": metrics, "mysql_delta": delta}, ensure_ascii=False), flush=True)
    return result


def base_env(scenario, tokens):
    return {"BASE_URL": env("LT_BASE_URL").rstrip("/"), "SCENARIO": scenario, "TOKENS_FILE": str(tokens),
            "SCHEDULE_BASE": SCHEDULE_BASE, "SCHEDULE_COUNT": SCHEDULE_COUNT, "B_SCHEDULE_ID": B_SCHEDULE_ID}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("scenario", choices=["A", "B", "C", "WARMUP"])
    p.add_argument("--name", required=True)
    p.add_argument("--rate", type=int, default=100)
    p.add_argument("--duration", default="10m")
    p.add_argument("--offset", type=int, default=0)
    p.add_argument("--b-members", type=int, default=500)
    p.add_argument("--b-seconds", type=int, default=10)
    p.add_argument("--step", type=int, default=20)
    p.add_argument("--max-rate", type=int, default=2000)
    p.add_argument("--p95-limit-ms", type=float, default=300)
    p.add_argument("--error-limit", type=float, default=0.001)
    a = p.parse_args()
    tokens = data_dir() / "tokens.json"
    if not tokens.is_file():
        sys.exit("缺少 tokens.json，先运行 export_tokens.py")

    if a.scenario in ("A", "WARMUP"):
        run_once(a.name, {**base_env(a.scenario, tokens), "RATE": a.rate, "DURATION": a.duration,
                          "OFFSET": a.offset})
    elif a.scenario == "B":
        result = run_once(a.name, {**base_env("B", tokens), "B_MEMBERS": a.b_members, "B_SECONDS": a.b_seconds})
        result["reconcile"] = reconcile(connect())
        write_json(data_dir() / "results" / a.name / "result.json", result)
        print(json.dumps({"reconcile_violations": result["reconcile"]["violation_total"],
                          "b_schedule": result["reconcile"]["totals"]["场景B排课"]}, ensure_ascii=False, default=str))
        return
    else:
        steps, offset, rate = [], a.offset, a.rate
        while rate <= a.max_rate:
            r = run_once(f"{a.name}/rate-{rate:04d}", {**base_env("C", tokens), "RATE": rate,
                                                       "DURATION": a.duration, "OFFSET": offset})
            k = r["k6"]
            achieved_ratio = k["http_reqs"] / (rate * duration_seconds(a.duration))
            reasons = [x for x, bad in (("p95", k["p95_ms"] > a.p95_limit_ms),
                                        ("error_rate", k["error_rate"] >= a.error_limit),
                                        ("rate_not_reached", achieved_ratio < 0.99)) if bad]
            steps.append({"rate": rate, "achieved_ratio": round(achieved_ratio, 4), "failed": reasons,
                          **{x: k[x] for x in ("qps", "http_reqs", "dropped_iterations", "p50_ms", "p95_ms",
                                               "p99_ms", "error_rate", "reserved", "conflict_total")},
                          "druid_active_max": r["monitor"].get("druid.ActiveCount", {}).get("max"),
                          "druid_wait_max": r["monitor"].get("druid.WaitThreadCount", {}).get("max"),
                          "tomcat_busy_max": r["monitor"].get("tomcat.currentThreadsBusy", {}).get("max"),
                          "jvm_cpu_avg": r["monitor"].get("jvm.ProcessCpuLoad", {}).get("avg"),
                          "row_lock_waits": r["mysql_delta"]["Innodb_row_lock_waits"]})
            print(json.dumps(steps[-1], ensure_ascii=False), flush=True)
            offset += k["iterations"] + k["dropped_iterations"]
            if reasons:
                break
            rate += a.step
        passed = [s["rate"] for s in steps if not s["failed"]]
        write_json(data_dir() / "results" / a.name / "ladder.json",
                   {"steps": steps, "knee_rate": passed[-1] if passed else None,
                    "first_failed": steps[-1] if steps and steps[-1]["failed"] else None})
    rec = reconcile(connect())
    write_json(data_dir() / "results" / a.name / "reconcile.json", rec)
    print(json.dumps({"reconcile_violations": rec["violation_total"]}, ensure_ascii=False))


def duration_seconds(text):
    units = {"s": 1, "m": 60, "h": 3600}
    return int(text[:-1]) * units[text[-1]]


if __name__ == "__main__":
    main()
