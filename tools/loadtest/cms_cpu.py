"""按 result.json 的起止时间查询云监控里 SAE 应用的 CPU 使用率（每分钟平均与最大值，单位 %）。

  python -X utf8 cms_cpu.py --app backend=<应用ID> --app mysql=<应用ID> results/A/result.json ...

应用 ID 用 sae.py status 查看。云监控 acs_serverless 命名空间的 cpu 指标最小粒度为 60 秒。
"""
import argparse
import json
import subprocess


def series(app_id, start_ms, end_ms):
    out = subprocess.run(["aliyun", "cms", "DescribeMetricList", "--Namespace", "acs_serverless",
                          "--MetricName", "cpu", "--Period", "60", "--StartTime", str(start_ms),
                          "--EndTime", str(end_ms), "--Dimensions", json.dumps([{"appId": app_id}])],
                         capture_output=True, text=True, encoding="utf-8", check=True).stdout
    points = json.loads(json.loads(out).get("Datapoints") or "[]")
    return [(round(p.get("Average", 0), 1), round(p.get("Maximum", 0), 1)) for p in points]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--app", action="append", required=True, help="名称=SAE 应用 ID，可重复")
    p.add_argument("results", nargs="+")
    a = p.parse_args()
    apps = dict(x.split("=", 1) for x in a.app)
    for path in a.results:
        r = json.load(open(path, encoding="utf-8"))
        print("==", r["name"])
        for name, app_id in apps.items():
            print(f"  {name} cpu% avg/max per min:", series(app_id, int(r["started"] * 1000), int(r["ended"] * 1000)))


if __name__ == "__main__":
    main()
