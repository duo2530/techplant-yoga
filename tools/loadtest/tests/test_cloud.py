"""用假的 aliyun CLI 跑 cloud.py：记录调用顺序，模拟 SAE 绑定时创建不带 ACL 的监听。

  python -X utf8 -m unittest discover -s tests（在 tools/loadtest 下执行）
"""
import json, sys, tempfile, types, unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import cloud


class Fake:
    def __init__(self, fail=None, keep_acl_off=False, extra_entry=False, reactivate=False):
        self.calls, self.fail, self.keep_acl_off, self.extra_entry, self.reactivate = [], fail, keep_acl_off, extra_entry, reactivate
        self.lb_status, self.listener, self.entries, self.order_polls = None, None, [], 0

    def __call__(self, product, api, *args, check=True):
        kv = dict(zip(args[::2], args[1::2]))
        self.calls.append((api, kv.get("--LoadBalancerStatus") or kv.get("--AclStatus")))
        if api == self.fail:
            r = {"_raw": "boom", "_exit": 1}
        else:
            r = self.handle(api, kv)
        if check and not cloud.ok(r):
            raise cloud.CloudError(f"{product} {api} 失败: {cloud.message(r)}")
        return r

    def handle(self, api, kv):
        if api == "CreateAccessControlList": return {"AclId": "acl-x"}
        if api == "AddAccessControlListEntry":
            self.entries = [json.loads(kv["--AclEntrys"])[0]["entry"]] + (["0.0.0.0/0"] if self.extra_entry else []); return {}
        if api == "DescribeAccessControlListAttribute":
            return {"AclEntrys": {"AclEntry": [{"AclEntryIP": e} for e in self.entries]}}
        if api == "CreateLoadBalancer": self.lb_status = "active"; return {"LoadBalancerId": "lb-x", "Address": "1.1.1.1"}
        if api == "SetLoadBalancerStatus": self.lb_status = kv["--LoadBalancerStatus"]; return {}
        if api == "DescribeLoadBalancerAttribute": return {"LoadBalancerStatus": self.lb_status}
        if api == "BindSlb": return {"Data": {"ChangeOrderId": "co-1"}}
        if api == "DescribeChangeOrder":
            self.order_polls += 1
            if self.order_polls == 2:   # SAE 在绑定过程中创建监听，默认 running、不带 ACL
                self.listener = {"ListenerPort": 8000, "Status": "running", "AclStatus": "off"}
                if self.reactivate: self.lb_status = "active"
            return {"Data": {"Status": 2 if self.order_polls >= 3 else 1}}
        if api == "DescribeLoadBalancerTCPListenerAttribute":
            return dict(self.listener) if self.listener else {"_raw": "not found", "_exit": 2}
        if api == "StopLoadBalancerListener": self.listener["Status"] = "stopped"; return {}
        if api == "StartLoadBalancerListener": self.listener["Status"] = "running"; return {}
        if api == "SetLoadBalancerTCPListenerAttribute":
            assert kv.get("--DryRun") == "false"
            if not self.keep_acl_off:
                self.listener.update(AclStatus="on", AclType="white", AclId=kv["--AclId"])
            return {}
        if api in ("UnbindSlb",): return {"Data": {"ChangeOrderId": "co-2"}}
        if api in ("DeleteLoadBalancer", "DeleteAccessControlList", "StopApplication"): return {}
        raise AssertionError(api)


def run(fake):
    cloud.aliyun, cloud.time.sleep = fake, lambda s: None
    cloud.os.environ["ALIBABA_CLOUD_REGION_ID"] = "cn-shanghai"
    cloud.os.environ.pop("GITHUB_OUTPUT", None)
    st = Path(tempfile.mkdtemp()) / "s.json"
    a = types.SimpleNamespace(app="app", port=8000, ip="2.2.2.2", name="t", zone=None, state=str(st))
    code = None
    try:
        cloud.cmd_expose(a)
    except SystemExit as e:
        code = e.code
    return code, [c[0] for c in fake.calls], json.loads(st.read_text())


class T(unittest.TestCase):
    def test_order(self):
        f = Fake(); code, calls, st = run(f)
        self.assertIsNone(code)
        i = calls.index
        self.assertLess(i("AddAccessControlListEntry"), i("CreateLoadBalancer"))
        self.assertLess(i("CreateLoadBalancer"), i("SetLoadBalancerStatus"))
        self.assertLess(i("SetLoadBalancerStatus"), i("BindSlb"))
        self.assertLess(i("StopLoadBalancerListener"), i("SetLoadBalancerTCPListenerAttribute"))
        self.assertLess(i("SetLoadBalancerTCPListenerAttribute"), i("StartLoadBalancerListener"))
        self.assertEqual([c for c in f.calls if c[0] == "SetLoadBalancerStatus"][-1][1], "active")
        self.assertEqual(calls[-2:], ["DescribeLoadBalancerTCPListenerAttribute", "DescribeAccessControlListAttribute"])
        # 设回 active 之前监听已经开着白名单
        k = len(calls) - 1 - calls[::-1].index("SetLoadBalancerStatus")
        self.assertIn("SetLoadBalancerTCPListenerAttribute", calls[:k])
        self.assertEqual(st["lb"], "lb-x")

    def assert_released(self, code, calls, st):
        self.assertTrue(code and "已删除" in str(code), code)
        if "CreateLoadBalancer" in calls:
            self.assertIn("DeleteLoadBalancer", calls)
        self.assertIn("DeleteAccessControlList", calls)
        self.assertNotIn("lb", st); self.assertNotIn("acl", st)

    def test_acl_set_fails(self):
        f = Fake(fail="SetLoadBalancerTCPListenerAttribute"); code, calls, st = run(f)
        self.assert_released(code, calls, st)
        self.assertNotIn(("SetLoadBalancerStatus", "active"), f.calls)

    def test_acl_silently_off(self):
        f = Fake(keep_acl_off=True); code, calls, st = run(f)
        self.assert_released(code, calls, st)
        self.assertNotIn(("SetLoadBalancerStatus", "active"), f.calls)

    def test_extra_acl_entry(self):
        f = Fake(extra_entry=True); code, calls, st = run(f)
        self.assert_released(code, calls, st)
        self.assertNotIn("CreateLoadBalancer", calls)

    def test_lb_reactivated_during_bind(self):
        f = Fake(reactivate=True); code, calls, st = run(f)
        self.assert_released(code, calls, st)
        self.assertIn("被改为 active", str(code))

    def test_bind_fails(self):
        f = Fake(fail="BindSlb"); code, calls, st = run(f)
        self.assert_released(code, calls, st)

    def test_verify_fails_releases(self):
        f = Fake(); run(f)
        f.listener["AclStatus"] = "off"
        st = Path(tempfile.mkdtemp()) / "s.json"
        st.write_text(json.dumps({"app": "app", "port": 8000, "allow": "2.2.2.2/32", "acl": "acl-x", "lb": "lb-x", "bound": True}))
        with self.assertRaises(SystemExit) as e:
            cloud.cmd_verify(types.SimpleNamespace(state=str(st)))
        self.assertIn("已删除", str(e.exception.code))
        self.assertEqual(json.loads(st.read_text()), {"app": "app", "port": 8000, "allow": "2.2.2.2/32"})

    def test_cli_error_raises(self):
        import importlib, subprocess
        importlib.reload(cloud)
        real = subprocess.run
        cases = [(2, "", '{"message":"x","error_code":"E"}'), (0, "not json", ""), (0, '{"Code":"500","Message":"m"}', ""),
                 (1, '{}', "")]
        try:
            for rc, out, err in cases:
                cloud.subprocess.run = lambda *a, **k: subprocess.CompletedProcess(a, rc, out, err)
                with self.assertRaises(cloud.CloudError, msg=(rc, out, err)):
                    cloud.aliyun("slb", "X")
                self.assertFalse(cloud.ok(cloud.aliyun("slb", "X", check=False)))
            cloud.subprocess.run = lambda *a, **k: subprocess.CompletedProcess(a, 0, '{"RequestId":"r"}', "")
            self.assertTrue(cloud.ok(cloud.aliyun("slb", "X")))
        finally:
            cloud.subprocess.run = real


if __name__ == "__main__":
    unittest.main(verbosity=1)
