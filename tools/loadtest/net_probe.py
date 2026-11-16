"""只读：测压测机到压测库的往返时延，用来判断连接持有时间里有多少花在网络上。

  net_probe.py [--n 500] [--interval-ms 0]   在一条连接上执行 n 次 SELECT 1，输出客户端往返耗时的分位数。
可以和 run_scenario 同时运行，对比空闲与压测期间的时延。
"""
import argparse
import json
import time

from common import connect, query


def percentiles(values):
    values = sorted(values)
    pick = lambda q: round(values[min(len(values) - 1, int(len(values) * q))], 3)  # noqa: E731
    return {"n": len(values), "p50_ms": pick(0.5), "p90_ms": pick(0.9), "p99_ms": pick(0.99),
            "max_ms": round(values[-1], 3), "avg_ms": round(sum(values) / len(values), 3)}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--n", type=int, default=500)
    p.add_argument("--interval-ms", type=float, default=0)
    a = p.parse_args()
    conn = connect()
    conn.autocommit(True)
    rtts = []
    for _ in range(a.n):
        t0 = time.perf_counter()
        query(conn, "SELECT 1")
        rtts.append((time.perf_counter() - t0) * 1000)
        time.sleep(a.interval_ms / 1000)
    print(json.dumps({"select1_rtt": percentiles(rtts)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
