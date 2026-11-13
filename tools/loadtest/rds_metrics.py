"""按 result.json 的起止时间查询 RDS 与 SAE 应用的云监控指标，写回 result.json 的 rds 与 sae_cpu 字段。

  python -X utf8 rds_metrics.py --instance <RDS 实例 ID> --app backend=<SAE 应用 ID> results/*/result.json

RDS：DescribeDBInstancePerformance 取 CPU、IOPS、会话数、QPS/TPS，DescribeSlowLogRecords 取慢 SQL 并按语句模板汇总。
接口失败或没有数据点时，对应字段写 {"available": false, "reason": ...}，不中断。
在本机或 GitHub runner 上执行，需要 aliyun CLI 的 RDS 与 CMS 只读权限；压测机上没有云凭证。
RDS 默认监控粒度为 300 秒，短于 10 分钟的运行可能只有一两个点；慢日志有数分钟延迟。
"""
import argparse
import json
import re
import subprocess
import time
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

PERF_KEYS = {
    "MySQL_MemCpuUsage": ("cpu_pct", "mem_pct"),
    "MySQL_IOPS": ("iops",),
    "MySQL_Sessions": ("active_sessions", "total_sessions"),
    "MySQL_QPSTPS": ("qps", "tps"),
}


def aliyun(product, action, **params):
    cmd = ["aliyun", product, action]
    for k, v in params.items():
        cmd += [f"--{k}", str(v)]
    p = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=60)
    if p.returncode:
        text = (p.stderr or p.stdout).strip()
        try:
            err = json.loads(text)
            text = f"{err.get('error_code') or err.get('Code')}: {err.get('message') or err.get('Message')}"
        except json.JSONDecodeError:
            pass
        raise RuntimeError(text[:300])
    return json.loads(p.stdout)


def utc_minute(ts, pad_s=0):
    return datetime.fromtimestamp(ts + pad_s, timezone.utc).strftime("%Y-%m-%dT%H:%MZ")


def unavailable(e):
    return {"available": False, "reason": str(e)[:300]}


def stats(values):
    if not values:
        return None
    return {"avg": round(sum(values) / len(values), 2), "max": round(max(values), 2), "points": len(values)}


def performance(instance, started, ended):
    # 窗口两端各放宽一个粒度，避免 300 秒粒度下取不到点
    r = aliyun("rds", "DescribeDBInstancePerformance", DBInstanceId=instance, Key=",".join(PERF_KEYS),
               StartTime=utc_minute(started, -300), EndTime=utc_minute(ended, 300))
    series = defaultdict(list)
    for item in (r.get("PerformanceKeys") or {}).get("PerformanceKey") or []:
        names = PERF_KEYS.get(item.get("Key"), ())
        for point in (item.get("Values") or {}).get("PerformanceValue") or []:
            for name, v in zip(names, str(point.get("Value", "")).split("&")):
                try:
                    series[name].append(float(v))
                except ValueError:
                    pass
    if not series:
        return unavailable("窗口内没有监控数据点")
    return {"available": True, **{name: stats(series.get(name)) for names in PERF_KEYS.values() for name in names}}


def sql_template(text):
    t = re.sub(r"'(?:[^'\\]|\\.)*'", "?", text or "")
    t = re.sub(r"\b\d+\b", "?", t)
    t = re.sub(r"\(\s*\?(?:\s*,\s*\?)*\s*\)", "(?)", t)
    return re.sub(r"\s+", " ", t).strip()[:500]


def slow_log(instance, started, ended, top=10):
    records, page = [], 1
    while True:
        r = aliyun("rds", "DescribeSlowLogRecords", DBInstanceId=instance, StartTime=utc_minute(started, -60),
                   EndTime=utc_minute(ended, 60), PageSize=100, PageNumber=page)
        items = (r.get("Items") or {}).get("SQLSlowRecord") or []
        records += items
        if len(items) < 100 or page >= 50:
            break
        page += 1
    groups = defaultdict(lambda: {"count": 0, "total_ms": 0, "max_ms": 0, "max_rows_examined": 0})
    for x in records:
        g = groups[sql_template(x.get("SQLText"))]
        ms = x.get("QueryTimeMS") or (x.get("QueryTimes") or 0) * 1000
        g["count"] += 1
        g["total_ms"] += ms
        g["max_ms"] = max(g["max_ms"], ms)
        g["max_rows_examined"] = max(g["max_rows_examined"], x.get("ParseRowCounts") or 0)
    ranked = sorted(groups.items(), key=lambda kv: kv[1]["total_ms"], reverse=True)[:top]
    return {"available": True, "records": len(records), "templates": len(groups),
            "top": [{"sql": sql, **g, "avg_ms": round(g["total_ms"] / g["count"], 1)} for sql, g in ranked]}


def sae_cpu(app_id, started, ended):
    r = aliyun("cms", "DescribeMetricList", Namespace="acs_serverless", MetricName="cpu", Period=60,
               StartTime=int(started * 1000), EndTime=int(ended * 1000), Dimensions=json.dumps([{"appId": app_id}]))
    points = json.loads(r.get("Datapoints") or "[]")
    if not points:
        return unavailable("窗口内没有监控数据点")
    return {"available": True, "avg": stats([p.get("Average", 0) for p in points])["avg"],
            "max": stats([p.get("Maximum", 0) for p in points])["max"], "points": len(points)}


def guarded(fn, *args):
    try:
        return fn(*args)
    except Exception as e:  # 云接口失败只记录原因
        return unavailable(e)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--instance", help="RDS 实例 ID；不给时 rds 记为不可用")
    p.add_argument("--app", action="append", default=[], help="名称=SAE 应用 ID，可重复")
    p.add_argument("--wait-slow-log", type=int, default=0, help="查询慢日志前等待的秒数，等待慢日志落盘")
    p.add_argument("results", nargs="+")
    a = p.parse_args()
    apps = dict(x.split("=", 1) for x in a.app)
    if a.wait_slow_log:
        time.sleep(a.wait_slow_log)
    for path in a.results:
        f = Path(path)
        r = json.loads(f.read_text(encoding="utf-8"))
        started, ended = r["started"], r["ended"]
        if a.instance:
            r["rds"] = {"instance": a.instance, "performance": guarded(performance, a.instance, started, ended),
                        "slow_log": guarded(slow_log, a.instance, started, ended)}
        else:
            r["rds"] = {"performance": unavailable("没有提供 RDS 实例 ID"),
                        "slow_log": unavailable("没有提供 RDS 实例 ID")}
        r["sae_cpu"] = {name: guarded(sae_cpu, app_id, started, ended) for name, app_id in apps.items()}
        f.write_text(json.dumps(r, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
        perf, slow = r["rds"]["performance"], r["rds"]["slow_log"]
        print(f"== {r.get('name')}: rds cpu={perf.get('cpu_pct') if perf['available'] else perf['reason']}"
              f" slow={slow.get('records') if slow['available'] else slow['reason']}"
              f" sae_cpu={ {k: v.get('avg') if v['available'] else '不可用' for k, v in r['sae_cpu'].items()} }")


if __name__ == "__main__":
    main()
