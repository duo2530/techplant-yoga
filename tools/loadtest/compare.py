"""把多次 READ / MIXED 运行的 result.json 汇总成规模对比：CSV 一行一个（运行, 接口），HTML 一页表格。

  python -X utf8 compare.py results/1m/result.json results/10m/result.json --out .local/compare
  python -X utf8 compare.py results/*/result.json --max-p95-ms 300 --max-http-error-rate 0.001

给了阈值时，任一运行的任一接口超限即退出码 1；--threshold-endpoint 可以只检查部分接口。
"""
import argparse
import csv
import glob
import html
import json
import sys
from pathlib import Path

COLUMNS = ["run", "started_at", "commit", "reservations", "members", "schedules", "transactions", "seed_seconds",
           "rate", "duration", "endpoint", "requests", "qps", "p50_ms", "p95_ms", "p99_ms", "max_ms",
           "http_error_rate", "biz_error_rate", "conflicts", "rds_cpu_avg", "rds_cpu_max", "rds_iops_max",
           "rds_qps_max", "rds_sessions_max", "slow_sql", "backend_cpu_avg", "backend_cpu_max"]


def cloud_columns(r):
    """rds_metrics.py 写入的 RDS 与后端 CPU；不可用时留空。"""
    perf = (r.get("rds") or {}).get("performance") or {}
    slow = (r.get("rds") or {}).get("slow_log") or {}
    backend = (r.get("sae_cpu") or {}).get("backend") or {}
    pick = lambda key, stat: (perf.get(key) or {}).get(stat) if perf.get("available") else None
    return {"rds_cpu_avg": pick("cpu_pct", "avg"), "rds_cpu_max": pick("cpu_pct", "max"),
            "rds_iops_max": pick("iops", "max"), "rds_qps_max": pick("qps", "max"),
            "rds_sessions_max": pick("active_sessions", "max"),
            "slow_sql": slow.get("records") if slow.get("available") else None,
            "backend_cpu_avg": backend.get("avg") if backend.get("available") else None,
            "backend_cpu_max": backend.get("max") if backend.get("available") else None}


def rows_of(path):
    r = json.loads(Path(path).read_text(encoding="utf-8"))
    counts = r.get("table_counts") or {}
    total = lambda t: (counts.get(t) or {}).get("total")
    base = {"run": r.get("name"), "started_at": r.get("started_at"), "commit": (r.get("commit") or "")[:12],
            "reservations": total("t_reservation"), "members": total("t_member"), "schedules": total("t_schedule"),
            "transactions": total("t_member_card_transaction"),
            "seed_seconds": (r.get("seed") or {}).get("seconds"),
            "rate": (r.get("env") or {}).get("RATE"), "duration": (r.get("env") or {}).get("DURATION"),
            **cloud_columns(r)}
    k6 = r.get("k6") or {}
    endpoints = dict(k6.get("endpoints") or {})
    endpoints["ALL"] = {"requests": k6.get("http_reqs"), "qps": k6.get("qps"), "p50_ms": k6.get("p50_ms"),
                        "p95_ms": k6.get("p95_ms"), "p99_ms": k6.get("p99_ms"), "max_ms": k6.get("max_ms"),
                        "http_error_rate": k6.get("error_rate"), "biz_error_rate": k6.get("biz_error_rate"),
                        "conflicts": k6.get("conflicts")}
    for ep, x in sorted(endpoints.items()):
        yield {**base, "endpoint": ep, **{k: x.get(k) for k in COLUMNS if k in x},
               "conflicts": json.dumps(x.get("conflicts") or {}, ensure_ascii=False)}


def check(rows, a):
    eps = set(a.threshold_endpoint or [])
    failures = []
    for row in rows:
        if eps and row["endpoint"] not in eps:
            continue
        for key, limit in (("p95_ms", a.max_p95_ms), ("p99_ms", a.max_p99_ms),
                           ("http_error_rate", a.max_http_error_rate), ("biz_error_rate", a.max_biz_error_rate)):
            if limit is not None and row.get(key) is not None and row[key] > limit:
                failures.append(f"{row['run']} {row['endpoint']}: {key}={row[key]} > {limit}")
    return failures


def write_html(path, rows, failures):
    def cell(v):
        return html.escape("" if v is None else str(v))

    head = "".join(f"<th>{c}</th>" for c in COLUMNS)
    body = []
    for row in rows:
        cls = ' class="all"' if row["endpoint"] == "ALL" else ""
        body.append(f"<tr{cls}>" + "".join(f"<td>{cell(row.get(c))}</td>" for c in COLUMNS) + "</tr>")
    verdict = ("<p class='fail'>阈值未通过：</p><ul>" + "".join(f"<li>{cell(f)}</li>" for f in failures) + "</ul>"
               if failures else "<p>阈值检查：通过或未设置</p>")
    Path(path).write_text(f"""<!doctype html><meta charset="utf-8"><title>容量压测规模对比</title>
<style>body{{font:13px sans-serif;margin:16px}}table{{border-collapse:collapse}}
td,th{{border:1px solid #ccc;padding:3px 6px;text-align:right}}th{{background:#f3f3f3}}
tr.all td{{font-weight:bold;background:#fafafa}}.fail{{color:#b00020;font-weight:bold}}</style>
<h1>容量压测规模对比</h1>{verdict}<table><tr>{head}</tr>{''.join(body)}</table>""", encoding="utf-8")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("results", nargs="+", help="result.json 路径，支持通配符")
    p.add_argument("--out", default="compare", help="输出目录，写入 compare.csv 与 compare.html")
    p.add_argument("--max-p95-ms", type=float)
    p.add_argument("--max-p99-ms", type=float)
    p.add_argument("--max-http-error-rate", type=float)
    p.add_argument("--max-biz-error-rate", type=float)
    p.add_argument("--threshold-endpoint", action="append", help="只对这些接口检查阈值，可重复；ALL 为整体")
    a = p.parse_args()
    files = sorted({f for pattern in a.results for f in (glob.glob(pattern) or [pattern])})
    rows = [row for f in files for row in rows_of(f)]
    rows.sort(key=lambda r: (r["endpoint"] != "ALL", r["endpoint"], r["reservations"] or 0, r["started_at"] or ""))
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    with open(out / "compare.csv", "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS, extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)
    failures = check(rows, a)
    write_html(out / "compare.html", rows, failures)
    print(f"{len(files)} 个运行、{len(rows)} 行，已写入 {out / 'compare.csv'} 与 {out / 'compare.html'}")
    for f in failures:
        print("超限:", f)
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
