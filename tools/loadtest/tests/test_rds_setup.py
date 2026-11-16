"""用假的 aliyun CLI 跑 rds_setup.py setup：核对网络、规格、ClientToken、白名单，参数一次改完只重启一次。

  python -X utf8 -m unittest discover -s tests（在 tools/loadtest 下执行）
"""
import base64
import json
import sys
import tempfile
import time as real_time
import types
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import rds_setup  # noqa: E402
from cloud import CloudError, ok  # noqa: E402

PASSWORD = "fake-password-for-unit-test"


class FakeCloud:
    def __init__(self, cpu="4", memory=" 16GB（独享型）", app_vswitch="vsw-l", create_timeout=False,
                 public=False, extra_ip=False, param_drop=None):
        self.calls, self.instances, self.tokens = [], {}, []
        self.params = {"character_set_server": "utf8", "transaction_isolation": "READ-COMMITTED",
                       "performance_schema": "0", "loose_performance_schema_max_digest_length": "0",
                       "loose_performance_schema_max_sql_text_length": "0"}
        self.cpu, self.memory, self.app_vswitch = cpu, memory, app_vswitch
        self.create_timeout, self.public, self.extra_ip, self.param_drop = create_timeout, public, extra_ip, param_drop
        self.accounts, self.dbs, self.restarts, self.polls_after_restart = [], [], 0, None

    def __call__(self, product, api, *args, check=True):
        if product == "sae":
            name, kv = args[0], dict(zip(args[1::2], args[2::2]))
        else:
            name, kv = api, dict(zip(args[::2], args[1::2]))
        self.calls.append(name)
        r = self.handle(product, name, kv)
        if check and not ok(r):
            raise CloudError(f"{product} {name} 失败: {r}")
        return r

    def status(self):
        if self.polls_after_restart is None:
            return "Running"
        self.polls_after_restart += 1
        return "Restarting" if self.polls_after_restart <= 2 else "Running"

    def handle(self, product, name, kv):
        if product == "sae":
            if name.endswith("describeApplicationConfig"):
                return {"Data": {"AppName": "yogalt-backend", "VpcId": "vpc-1", "VSwitchId": self.app_vswitch,
                                 "SecurityGroupId": "sg-1", "Envs": "[]"}}
            if name.endswith("/secrets"):
                return {"Data": {"Secrets": [{"SecretName": "yogalt-secrets", "SecretId": 1}]}}
            if name.endswith("/secret"):
                return {"Data": {"SecretData": {"mysql.root.password": base64.b64encode(PASSWORD.encode()).decode()}}}
        if name == "DescribeVSwitchAttributes":
            return {"VpcId": "vpc-1", "ZoneId": "cn-shanghai-l", "VSwitchName": "yogalt-l",
                    "CidrBlock": "172.16.58.0/24"}
        if name == "DescribeSecurityGroups":
            return {"SecurityGroups": {"SecurityGroup": [{"SecurityGroupId": "sg-1"}]}}
        if name == "DescribeVpcAttribute":
            return {"CidrBlock": "172.16.0.0/12"}
        if name == "DescribeClassDetails":
            return {"Cpu": self.cpu, "MemoryClass": self.memory}
        if name == "DescribeAvailableClasses":
            return {"DBInstanceClasses": [{"DBInstanceClass": "mysql.x4.large.2c"}]}
        if name == "DescribeDBInstances":
            return {"Items": {"DBInstance": [{"DBInstanceId": i, "DBInstanceDescription": d}
                                             for i, d in self.instances.items()]}}
        if name == "CreateDBInstance":
            assert kv["--SecurityIPList"] == "172.16.0.0/12" and kv["--DBInstanceNetType"] == "Intranet"
            self.tokens.append(kv["--ClientToken"])
            self.instances.setdefault("rm-new", kv["--DBInstanceDescription"])
            if self.create_timeout and len(self.tokens) == 1:
                return {"_raw": "timeout", "_exit": 1, "error_code": "SDK.TimeoutError"}
            return {"DBInstanceId": "rm-new"}
        if name == "DescribeDBInstanceAttribute":
            return {"Items": {"DBInstanceAttribute": [{
                "DBInstanceStatus": self.status(), "DBInstanceClass": "mysql.x4.large.2c", "DBInstanceCPU": "4",
                "DBInstanceMemory": 16384, "VSwitchId": "vsw-l", "VpcId": "vpc-1", "PayType": "Postpaid",
                "Engine": "MySQL", "EngineVersion": "8.0", "DBInstanceNetType": "Intranet"}]}}
        if name == "DescribeDBInstanceIPArrayList":
            items = [{"DBInstanceIPArrayName": "default", "SecurityIPList": "172.16.0.0/12"}]
            if self.extra_ip:
                items.append({"DBInstanceIPArrayName": "x", "SecurityIPList": "0.0.0.0/0"})
            return {"Items": {"DBInstanceIPArray": items}}
        if name == "DescribeDBInstanceNetInfo":
            infos = [{"IPType": "Private", "ConnectionString": "rm-new.mysql.rds", "Port": "3306"}]
            if self.public:
                infos.append({"IPType": "Public", "ConnectionString": "rm-new.pub", "Port": "3306"})
            return {"DBInstanceNetInfos": {"DBInstanceNetInfo": infos}}
        if name == "DescribeAccounts":
            return {"Accounts": {"DBInstanceAccount": [{"AccountName": a, "AccountStatus": "Available"}
                                                       for a in self.accounts]}}
        if name == "CreateAccount":
            assert kv["--AccountPassword"] == PASSWORD and kv["--AccountType"] == "Super"
            self.accounts.append(kv["--AccountName"])
            return {}
        if name == "DescribeDatabases":
            return {"Databases": {"Database": [{"DBName": d, "CharacterSetName": "utf8mb4", "DBStatus": "Running"}
                                               for d in self.dbs]}}
        if name == "CreateDatabase":
            self.dbs.append(kv["--DBName"])
            return {}
        if name == "DescribeParameters":
            return {"RunningParameters": {"DBInstanceParameter": [{"ParameterName": k, "ParameterValue": v}
                                                                  for k, v in self.params.items()]}}
        if name == "ModifyParameter":
            assert kv["--Forcerestart"] == "true" and kv.get("--ClientToken")
            self.restarts += 1
            self.polls_after_restart = 0
            new = json.loads(kv["--Parameters"])
            if self.param_drop:
                new.pop(self.param_drop)
            self.params.update(new)
            return {}
        raise AssertionError(name)


def run(fake, state=None):
    rds_setup.aliyun = fake
    path = Path(tempfile.mkdtemp()) / "rds.json"
    if state:
        path.write_text(json.dumps(state), encoding="utf-8")
    a = types.SimpleNamespace(app="app-1", vswitch="vsw-l", cls="mysql.x4.large.2c", cpu=4, memory_gb=16,
                              storage=100, description="yogalt-rebuild-test", account="yogalt",
                              namespace="cn-shanghai:yogalt", secret="yogalt-secrets", state=str(path))
    rds_setup.cmd_setup(a)
    return json.loads(path.read_text(encoding="utf-8")), path


class FakeClock:
    def __init__(self):
        self.now = 0.0

    def time(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class SetupTest(unittest.TestCase):
    def setUp(self):
        rds_setup.os.environ["ALIBABA_CLOUD_REGION_ID"] = "cn-shanghai"
        clock = FakeClock()
        rds_setup.time = types.SimpleNamespace(time=clock.time, sleep=clock.sleep, strftime=real_time.strftime)

    def tearDown(self):
        rds_setup.time = real_time

    def test_full_setup(self):
        f = FakeCloud()
        st, path = run(f)
        self.assertEqual(st["instance"], "rm-new")
        self.assertEqual(st["target"], "rm-new.mysql.rds:3306/yoga")
        self.assertEqual(f.restarts, 1)
        self.assertEqual(f.calls.count("CreateDBInstance"), 1)
        self.assertLess(f.calls.index("DescribeVSwitchAttributes"), f.calls.index("CreateDBInstance"))
        self.assertLess(f.calls.index("DescribeClassDetails"), f.calls.index("CreateDBInstance"))
        self.assertLess(f.calls.index("CreateAccount"), f.calls.index("ModifyParameter"))
        self.assertEqual(st["parameters"]["transaction_isolation"], "REPEATABLE-READ")
        self.assertEqual(st["parameters"]["loose_performance_schema_max_digest_length"], "1024")
        self.assertNotIn(PASSWORD, path.read_text(encoding="utf-8"))

    def test_rerun_is_idempotent(self):
        f = FakeCloud()
        st, _ = run(f)
        f.calls.clear()
        run(f, st)
        for api in ("CreateDBInstance", "CreateAccount", "CreateDatabase", "ModifyParameter"):
            self.assertNotIn(api, f.calls)
        self.assertEqual(f.restarts, 1)

    def test_timeout_queries_before_retry(self):
        f = FakeCloud(create_timeout=True)
        st, _ = run(f)
        self.assertEqual(st["instance"], "rm-new")
        self.assertEqual(len(f.tokens), 1)
        self.assertEqual(len(f.instances), 1)
        self.assertLess(f.calls.index("CreateDBInstance"), len(f.calls) - 1 - f.calls[::-1].index("DescribeDBInstances"))

    def test_wrong_spec_stops_before_create(self):
        f = FakeCloud(cpu="2")
        with self.assertRaises(CloudError):
            run(f)
        self.assertNotIn("CreateDBInstance", f.calls)

    def test_wrong_vswitch_stops_before_create(self):
        f = FakeCloud(app_vswitch="vsw-other")
        with self.assertRaises(CloudError):
            run(f)
        self.assertNotIn("CreateDBInstance", f.calls)

    def test_public_address_rejected(self):
        with self.assertRaises(CloudError):
            run(FakeCloud(public=True))

    def test_extra_whitelist_rejected(self):
        with self.assertRaises(CloudError):
            run(FakeCloud(extra_ip=True))

    def test_param_not_applied_fails_after_single_restart(self):
        f = FakeCloud(param_drop="performance_schema")
        with self.assertRaises(CloudError) as e:
            run(f)
        self.assertIn("performance_schema", str(e.exception))
        self.assertEqual(f.restarts, 1)

    def test_jdbc_url_keeps_query(self):
        self.assertEqual(rds_setup.jdbc_url("jdbc:mysql://old:3306/yoga?a=1&b=2", "new", 3306),
                         "jdbc:mysql://new:3306/yoga?a=1&b=2")


if __name__ == "__main__":
    unittest.main()
