"""运行一个压测场景：记录 MySQL 计数器 → 后台采样监控 → k6 → 再记录计数器 → 对账，结果写入 results/<名称>/。

  A / WARMUP：恒定到达率 --rate 持续 --duration；--members history 时用 seed.py --history-members 选出的历史会员
  B：--b-members 个会员在 --b-seconds 秒内抢同一节 20 人排课
  C：从 --rate 起每级加 --step，每级 --duration，某级 p95 超过 --p95-limit-ms、错误率达到 --error-limit
     或到达率不足目标的 99% 时停止，该级的前一级即拐点
  READ / MIXED：capacity.js 对 bulk_seed 数据压读接口或按 --mix 混合读写，按接口分别统计

--warmup-duration 不为 0 时，正式运行前先按 --warmup-rate 跑 capacity.js 的 MIXED（权重同 --mix），
结果不计入，参数与摘要写入 result.json 的 warmup；READ / MIXED 的 OFFSET 跳过预热用掉的可消耗池数据。

result.json 带上代码 commit、环境规格（LT_SPEC_* 环境变量）、各表行数、最近一次造数摘要与运行参数。
"""
import argparse
import json
import os
import re
import subprocess
import sys
import threading
import time
from pathlib import Path

import requests

from common import (B_SCHEDULE_ID, SCHEDULE_BASE, SCHEDULE_COUNT, connect, data_dir, env, global_status, query,
                    table_counts, write_json)
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
        self.jolokia = env("LT_JOLOKIA_URL", "").rstrip("/")
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
        if not self.jolokia:
            raise RuntimeError("未配置 LT_JOLOKIA_URL")
        r = self.http.post(self.jolokia + "/", timeout=5,
                           json=[{"type": "read", "mbean": mbean, "attribute": a} for a in attrs]).json()
        return {a: x.get("value") for a, x in zip(attrs, r)}

    def jmx_by_name(self, pattern, attrs):
        """读通配 MBean，返回 {name 属性: {属性: 值}}，如 GC 收集器和内存池。"""
        if not self.jolokia:
            raise RuntimeError("未配置 LT_JOLOKIA_URL")
        r = self.http.post(self.jolokia + "/", timeout=5,
                           json={"type": "read", "mbean": pattern, "attribute": attrs}).json()
        if r.get("status") != 200:
            raise RuntimeError(r.get("error") or f"status {r.get('status')}")
        return {re.search(r"name=([^,]+)", k).group(1): v for k, v in r["value"].items()}

    def gc(self):
        return self.jmx_by_name("java.lang:type=GarbageCollector,name=*", ["CollectionCount", "CollectionTime"])

    def heap(self):
        pools = self.jmx_by_name("java.lang:type=MemoryPool,name=*", ["Usage", "Type"])
        return {name: v["Usage"]["used"] for name, v in pools.items() if v.get("Type") == "HEAP"}

    def sample(self, conn):
        row = {"t": time.time()}
        for name, fn in (("druid", self.druid),
                         ("tomcat", lambda: self.jmx(TOMCAT_MBEAN, ["currentThreadsBusy", "currentThreadCount",
                                                                    "maxThreads", "connectionCount"])),
                         ("jvm", lambda: self.jmx("java.lang:type=OperatingSystem",
                                                  ["ProcessCpuLoad", "SystemCpuLoad", "AvailableProcessors"])),
                         ("threads", lambda: self.jmx("java.lang:type=Threading", ["ThreadCount"])),
                         ("gc", self.gc), ("heap", self.heap),
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


class ThreadDumper(threading.Thread):
    """每隔 interval 秒经 Jolokia 抓一次后端 Tomcat 请求线程的栈，写入 threads.jsonl。抓栈会触发安全点，只在诊断时开启。"""

    def __init__(self, path, interval):
        super().__init__(daemon=True)
        self.path, self.interval, self.stop = path, interval, threading.Event()
        self.jolokia = env("LT_JOLOKIA_URL", "").rstrip("/")
        self.http = requests.Session()

    def dump(self):
        r = self.http.post(self.jolokia + "/", timeout=30,
                           json={"type": "exec", "mbean": "java.lang:type=Threading", "operation": "dumpAllThreads(boolean,boolean)",
                                 "arguments": [False, False], "config": {"maxDepth": 8}}).json()
        if r.get("status") != 200:
            raise RuntimeError(r.get("error") or f"status {r.get('status')}")
        return [{"n": t["threadName"], "s": t["threadState"], "lock": t.get("lockName"),
                 "f": [f"{f['className']}.{f['methodName']}:{f.get('lineNumber')}" for f in t["stackTrace"][:THREAD_FRAMES]]}
                for t in r["value"] if t["threadName"].startswith("http-nio")]

    def run(self):
        with open(self.path, "a", encoding="utf-8") as f:
            while not self.stop.wait(self.interval):
                try:
                    row = {"t": time.time(), "threads": self.dump()}
                except Exception as e:
                    row = {"t": time.time(), "error": str(e)[:200]}
                f.write(json.dumps(row, ensure_ascii=False) + "\n")
                f.flush()


THREAD_FRAMES = 40
THREAD_DUMP_SECONDS = 0
APP_PACKAGES = ("com.techplant.", "com.ruoyi.")
JDK_PACKAGES = ("java.", "javax.", "sun.", "jdk.")


def thread_key(t):
    """按线程状态、最内层的非 JDK 帧和最内层的业务帧归类，例如 WAITING、Druid 取连接、ReservationServiceImpl.submit。"""
    lib = next((f for f in t["f"] if not f.startswith(JDK_PACKAGES)), "-")
    app = next((f for f in t["f"] if f.startswith(APP_PACKAGES)), "-")
    return f"{t['s']} | {lib} | {app}"


def summarize_threads(path):
    rows = [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]
    dumps = [r["threads"] for r in rows if "threads" in r]
    if not dumps:
        return {"dumps": 0, "errors": len(rows)}
    counts = {}
    for threads in dumps:
        for t in threads:
            counts[thread_key(t)] = counts.get(thread_key(t), 0) + 1
    top = sorted(counts.items(), key=lambda kv: -kv[1])[:20]
    return {"dumps": len(dumps), "errors": len(rows) - len(dumps),
            "threads_avg": round(sum(len(d) for d in dumps) / len(dumps), 1),
            "hotspots": [{"avg_threads": round(n / len(dumps), 1), "key": k} for k, n in top]}


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
    out.update(summarize_gc(rows))
    return out


def summarize_gc(rows):
    """各收集器在运行期间的次数与耗时增量，以及单个采样间隔内的最大 GC 耗时；各堆内存池的最大占用。"""
    out = {}
    gc = [r["gc"] for r in rows if isinstance(r.get("gc"), dict) and "error" not in r["gc"]]
    for name in (gc[0] if len(gc) >= 2 else {}):
        times = [g[name]["CollectionTime"] for g in gc if name in g]
        counts = [g[name]["CollectionCount"] for g in gc if name in g]
        out[f"gc.{name}"] = {"count": counts[-1] - counts[0], "time_ms": times[-1] - times[0],
                             "max_interval_ms": max((b - a for a, b in zip(times, times[1:])), default=0)}
    heap = [r["heap"] for r in rows if isinstance(r.get("heap"), dict) and "error" not in r["heap"]]
    for name in (heap[0] if heap else {}):
        used = [h[name] for h in heap if name in h]
        out[f"heap.{name}"] = {"max_mb": round(max(used) / 2**20, 1), "end_mb": round(used[-1] / 2**20, 1)}
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


def endpoint_metrics(summary, duration_s):
    """capacity.js 按 ep 标签拆分的子指标：每个接口的 QPS、分位数、HTTP 错误率、业务错误率与 409 分类。"""
    m = summary["metrics"]
    out = {}
    for key, value in m.items():
        hit = re.fullmatch(r"http_req_duration\{ep:(\w+)\}", key)
        if not hit:
            continue
        ep = hit.group(1)
        reqs = (m.get(f"http_reqs{{ep:{ep}}}") or {}).get("count", 0)
        if not reqs:
            continue
        out[ep] = {"requests": reqs, "qps": round(reqs / duration_s, 2),
                   "p50_ms": round(value["med"], 2), "p95_ms": round(value["p(95)"], 2),
                   "p99_ms": round(value["p(99)"], 2), "max_ms": round(value["max"], 2),
                   "avg_ms": round(value["avg"], 2),
                   "http_error_rate": round((m.get(f"http_errors{{ep:{ep}}}") or {}).get("value", 0), 6),
                   "biz_error_rate": round((m.get(f"biz_errors{{ep:{ep}}}") or {}).get("value", 0), 6)}
    for key, value in m.items():
        hit = re.fullmatch(r"(conflict_\w+)\{ep:(\w+)\}", key)
        if hit and hit.group(2) in out and value.get("count"):
            out[hit.group(2)].setdefault("conflicts", {})[hit.group(1)] = value["count"]
    return out


def capacity_metrics(summary, duration_s):
    m = summary["metrics"]
    d = m["http_req_duration"]

    def val(name, key="count"):
        return (m.get(name) or {}).get(key, 0)

    return {
        "http_reqs": val("http_reqs"), "qps": round(val("http_reqs", "rate"), 2),
        "iterations": val("iterations"), "dropped_iterations": val("dropped_iterations"),
        "p50_ms": round(d["med"], 2), "p95_ms": round(d["p(95)"], 2), "p99_ms": round(d["p(99)"], 2),
        "max_ms": round(d["max"], 2), "avg_ms": round(d["avg"], 2),
        "error_rate": val("http_errors", "value"), "biz_error_rate": val("biz_errors", "value"),
        "conflicts": {k: v.get("count", 0) for k, v in m.items() if k.startswith("conflict_") and "{" not in k},
        "vus_max": val("vus_max", "value"), "endpoints": endpoint_metrics(summary, duration_s),
    }


def run_k6(out_dir, env_vars, script="reservation.js", duration_s=None):
    out_dir.mkdir(parents=True, exist_ok=True)
    cmd = ["k6", "run", "--quiet", "--no-color", "--summary-export", str(out_dir / "k6-summary.json"),
           str(LT_DIR / script)]
    for k, v in env_vars.items():
        cmd[2:2] = ["-e", f"{k}={v}"]
    with open(out_dir / "k6-stdout.txt", "w", encoding="utf-8") as log:
        code = subprocess.run(cmd, stdout=log, stderr=subprocess.STDOUT).returncode
    summary = json.loads((out_dir / "k6-summary.json").read_text(encoding="utf-8"))
    if script == "capacity.js":
        return code, capacity_metrics(summary, duration_s)
    return code, k6_metrics(summary)


def git_commit(explicit=None):
    if explicit:
        return explicit
    if os.environ.get("LT_COMMIT"):
        return os.environ["LT_COMMIT"]
    try:
        return subprocess.run(["git", "rev-parse", "HEAD"], cwd=LT_DIR, capture_output=True, text=True,
                              timeout=10).stdout.strip() or None
    except OSError:
        return None


def run_meta(conn, args):
    """运行环境与数据规模，供 compare.py 横向对比不同规模的结果。"""
    seed = None
    if query(conn, "SHOW TABLES LIKE 'lt_seed_progress'"):
        rows = query(conn, "SELECT info FROM lt_seed_progress WHERE name = 'last_run'")
        if rows:
            info = rows[0]["info"]
            info = json.loads(info) if isinstance(info, str) else info
            seed = {k: info.get(k) for k in ("target_rows", "profile", "seed", "mode", "seconds", "phases",
                                             "base_date", "reconcile_violations")}
    mysql = query(conn, "SELECT @@version AS version, @@innodb_buffer_pool_size AS buffer_pool,"
                        " @@max_connections AS max_connections")[0]
    spec = {k[len("LT_SPEC_"):].lower(): v for k, v in os.environ.items() if k.startswith("LT_SPEC_")}
    return {"commit": git_commit(args.commit), "spec": {**spec, "mysql": mysql}, "table_counts": table_counts(conn),
            "seed": seed, "args": vars(args)}


def run_once(name, k6_env, script="reservation.js", meta=None):
    out = data_dir() / "results" / name
    out.mkdir(parents=True, exist_ok=True)
    conn = connect()
    before = global_status(conn, *LOCK_COUNTERS)
    sampler = Sampler(out / "samples.jsonl")
    sampler.start()
    dumper = ThreadDumper(out / "threads.jsonl", THREAD_DUMP_SECONDS) if THREAD_DUMP_SECONDS else None
    if dumper:
        dumper.start()
    started = time.time()
    duration_s = duration_seconds(k6_env["DURATION"]) if "DURATION" in k6_env else None
    code, metrics = run_k6(out, k6_env, script, duration_s)
    ended = time.time()
    sampler.stop.set()
    sampler.join(timeout=10)
    if dumper:
        dumper.stop.set()
        dumper.join(timeout=40)
    after = global_status(conn, *LOCK_COUNTERS)
    delta = {k: after[k] - before[k] for k in LOCK_COUNTERS if not k.startswith(("Threads_", "Innodb_row_lock_c"))}
    waits = delta["Innodb_row_lock_waits"]
    delta["row_lock_time_avg_ms_in_run"] = round(delta["Innodb_row_lock_time"] / waits, 2) if waits else 0
    delta["Innodb_row_lock_time_max_global"] = after["Innodb_row_lock_time_max"]
    status_avg = query(conn, "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_time_avg'")[0]["Value"]
    result = {"name": name, "k6_exit": code, "env": k6_env, "started": started, "ended": ended,
              "started_at": time.strftime("%Y-%m-%dT%H:%M:%S%z", time.localtime(started)),
              "ended_at": time.strftime("%Y-%m-%dT%H:%M:%S%z", time.localtime(ended)), **(meta or {}),
              "k6": metrics, "mysql_delta": delta, "Innodb_row_lock_time_avg_global": int(status_avg),
              "monitor": summarize_samples(out / "samples.jsonl")}
    if dumper:
        result["threads"] = summarize_threads(out / "threads.jsonl")
    write_json(out / "result.json", result)
    print(json.dumps({"name": name, "k6": metrics, "mysql_delta": delta}, ensure_ascii=False), flush=True)
    return result


def base_env(scenario, tokens):
    return {"BASE_URL": env("LT_BASE_URL").rstrip("/"), "SCENARIO": scenario, "TOKENS_FILE": str(tokens),
            "SCHEDULE_BASE": SCHEDULE_BASE, "SCHEDULE_COUNT": SCHEDULE_COUNT, "B_SCHEDULE_ID": B_SCHEDULE_ID}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("scenario", choices=["A", "B", "C", "WARMUP", "READ", "MIXED"])
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
    p.add_argument("--mix", default="read=80,reserve=10,cancel=5,queue=3,checkin=2", help="MIXED 的操作权重")
    p.add_argument("--read-mix", help="读请求在各读接口之间的权重，缺省用 capacity.js 的默认值")
    p.add_argument("--commit", help="被测代码的 commit；缺省取 LT_COMMIT 或本目录的 git HEAD")
    p.add_argument("--rate-limit", type=int, default=2000, help="--rate 的安全上限")
    p.add_argument("--members", choices=["seed", "history"], default="seed",
                   help="A / C / WARMUP 的预约会员：seed 为 seed.py 新建的会员，history 为历史会员")
    p.add_argument("--warmup-rate", type=int, default=50)
    p.add_argument("--warmup-duration", default="0", help="如 2m；0 不预热")
    p.add_argument("--thread-dump-seconds", type=float, default=0,
                   help="每隔多少秒抓一次后端请求线程栈并按栈顶归类，0 不抓；只用于诊断，会增加后端停顿")
    a = p.parse_args()
    global THREAD_DUMP_SECONDS
    THREAD_DUMP_SECONDS = a.thread_dump_seconds
    if max(a.rate, a.warmup_rate) > a.rate_limit:
        sys.exit(f"--rate / --warmup-rate 超过上限 {a.rate_limit}")
    warmup = run_warmup(a)
    if a.scenario in ("READ", "MIXED"):
        sys.exit(run_capacity(a, warmup))
    tokens = data_dir() / ("tokens-history.json" if a.members == "history" else "tokens.json")
    if not tokens.is_file():
        sys.exit(f"缺少 {tokens.name}，先运行 export_tokens.py" + (" --set history" if a.members == "history" else ""))

    if a.scenario in ("A", "WARMUP"):
        result = run_once(a.name, {**base_env(a.scenario, tokens), "RATE": a.rate, "DURATION": a.duration,
                                   "OFFSET": a.offset})
        if warmup:
            write_json(data_dir() / "results" / a.name / "result.json", {**result, "warmup": warmup})
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


def capacity_env(a, rate, duration, offset, mix):
    tokens = data_dir() / "tokens-bulk.json"
    if not tokens.is_file():
        sys.exit("缺少 tokens-bulk.json，先运行 export_tokens.py --set bulk")
    k6_env = {"BASE_URL": env("LT_BASE_URL").rstrip("/"), "TOKENS_FILE": str(tokens), "RATE": rate,
              "DURATION": duration, "OFFSET": offset, "MIX": mix}
    if a.read_mix:
        k6_env["READ_MIX"] = a.read_mix
    return k6_env


def run_warmup(a):
    """按低速率跑 MIXED 预热缓存与连接，返回参数与摘要。"""
    if a.warmup_duration in ("0", "0s", "0m"):
        return None
    k6_env = capacity_env(a, a.warmup_rate, a.warmup_duration, a.offset, a.mix)
    started = time.time()
    code, k = run_k6(data_dir() / "results" / a.name / "warmup", k6_env, "capacity.js",
                     duration_seconds(a.warmup_duration))
    used = k["iterations"] + k["dropped_iterations"]
    if a.scenario in ("READ", "MIXED"):
        a.offset += used
    warmup = {"scenario": "MIXED", "rate": a.warmup_rate, "duration": a.warmup_duration, "mix": a.mix,
              "offset": k6_env["OFFSET"], "iterations_used": used, "k6_exit": code,
              "started_at": time.strftime("%Y-%m-%dT%H:%M:%S%z", time.localtime(started)),
              **{x: k[x] for x in ("qps", "p95_ms", "p99_ms", "error_rate")}}
    print(json.dumps({"warmup": warmup}, ensure_ascii=False), flush=True)
    return warmup


def run_capacity(a, warmup=None):
    meta = run_meta(connect(), a)
    k6_env = capacity_env(a, a.rate, a.duration, a.offset, "read=100" if a.scenario == "READ" else a.mix)
    result = run_once(a.name, k6_env, "capacity.js", meta)
    result["reconcile"] = reconcile(connect())
    if warmup:
        result["warmup"] = warmup
    write_json(data_dir() / "results" / a.name / "result.json", result)
    for ep, x in sorted(result["k6"]["endpoints"].items()):
        print(f"{ep:18s} qps={x['qps']:8.2f} p95={x['p95_ms']:8.2f}ms p99={x['p99_ms']:8.2f}ms"
              f" http_err={x['http_error_rate']:.4f} biz_err={x['biz_error_rate']:.4f} {x.get('conflicts', '')}",
              flush=True)
    print(json.dumps({"reconcile_violations": result["reconcile"]["violation_total"]}, ensure_ascii=False))
    return 1 if result["reconcile"]["violation_total"] or result["k6"]["error_rate"] > 0 else 0


def duration_seconds(text):
    units = {"s": 1, "m": 60, "h": 3600}
    return int(text[:-1]) * units[text[-1]]


if __name__ == "__main__":
    main()
