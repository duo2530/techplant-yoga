"""run_scenario 采样汇总：GC 次数与耗时取运行期间的增量，线程栈按状态与栈帧归类，采样失败的行不参与计算。

  python -X utf8 -m unittest discover -s tests（在 tools/loadtest 下执行）
"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_scenario import summarize_gc, summarize_threads  # noqa: E402

MB = 2**20


def row(young, old, old_used):
    return {"gc": {"PS Scavenge": {"CollectionCount": young[0], "CollectionTime": young[1]},
                   "PS MarkSweep": {"CollectionCount": old[0], "CollectionTime": old[1]}},
            "heap": {"PS Old Gen": old_used * MB}}


class SummarizeGcTest(unittest.TestCase):
    def test_deltas_and_longest_interval(self):
        rows = [row((100, 1000), (5, 2000), 300),
                {"gc": {"error": "timeout"}, "heap": {"error": "timeout"}},
                row((110, 1040), (5, 2000), 700),
                row((115, 1050), (6, 3500), 200)]
        out = summarize_gc(rows)
        self.assertEqual(out["gc.PS Scavenge"], {"count": 15, "time_ms": 50, "max_interval_ms": 40})
        self.assertEqual(out["gc.PS MarkSweep"], {"count": 1, "time_ms": 1500, "max_interval_ms": 1500})
        self.assertEqual(out["heap.PS Old Gen"], {"max_mb": 700.0, "end_mb": 200.0})

    def test_no_samples(self):
        self.assertEqual(summarize_gc([{"gc": {"error": "未配置 LT_JOLOKIA_URL"}}]), {})


class SummarizeThreadsTest(unittest.TestCase):
    def test_groups_by_state_library_and_app_frame(self):
        take = ["sun.misc.Unsafe.park:-2", "com.alibaba.druid.pool.DruidDataSource.takeLast:2100",
                "com.techplant.yoga.reservation.service.impl.ReservationServiceImpl.submit:130"]
        read = ["java.net.SocketInputStream.socketRead0:-2", "com.mysql.cj.protocol.ReadAheadInputStream.fill:107"]
        lines = [{"t": 1, "threads": [{"n": "http-nio-8080-exec-1", "s": "WAITING", "f": take},
                                      {"n": "http-nio-8080-exec-2", "s": "WAITING", "f": take},
                                      {"n": "http-nio-8080-exec-3", "s": "RUNNABLE", "f": read}]},
                 {"t": 2, "error": "timeout"},
                 {"t": 3, "threads": [{"n": "http-nio-8080-exec-1", "s": "WAITING", "f": take}]}]
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "threads.jsonl"
            path.write_text("\n".join(json.dumps(x) for x in lines), encoding="utf-8")
            out = summarize_threads(path)
        self.assertEqual((out["dumps"], out["errors"], out["threads_avg"]), (2, 1, 2.0))
        self.assertEqual(out["hotspots"][0], {
            "avg_threads": 1.5,
            "key": "WAITING | com.alibaba.druid.pool.DruidDataSource.takeLast:2100 | "
                   "com.techplant.yoga.reservation.service.impl.ReservationServiceImpl.submit:130"})
        self.assertEqual(out["hotspots"][1]["key"], "RUNNABLE | com.mysql.cj.protocol.ReadAheadInputStream.fill:107 | -")


if __name__ == "__main__":
    unittest.main()
