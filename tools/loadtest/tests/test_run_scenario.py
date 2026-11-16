"""run_scenario 采样汇总：GC 次数与耗时取运行期间的增量，采样失败的行不参与计算。

  python -X utf8 -m unittest discover -s tests（在 tools/loadtest 下执行）
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_scenario import summarize_gc  # noqa: E402

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


if __name__ == "__main__":
    unittest.main()
