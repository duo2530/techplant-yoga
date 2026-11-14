"""从零建一台压测用的按量 RDS MySQL 8.0，并把 SAE 上的后端与压测机改连到它。

  python rds_setup.py setup --app <后端 AppId> --vswitch <VSwitchId> --class mysql.x4.large.2c --cpu 4 --memory-gb 16 \
      --description yogalt-rebuild-test --namespace cn-shanghai:yogalt --secret yogalt-secrets --state <文件>
  python rds_setup.py point-apps --state <文件> --backend <AppId> --loadgen <AppId>

setup 的步骤（可重复执行，已完成的步骤跳过）：
  1. 用 --app 的应用配置核对 VPC、交换机与安全组：--vswitch 必须是该应用所在的交换机，安全组属于同一 VPC。
  2. DescribeClassDetails 核对规格的 CPU 与内存，DescribeAvailableClasses 核对交换机所在可用区有售。
  3. 按 --description 查重；没有才 CreateDBInstance（带 ClientToken），白名单只放 VPC 网段，不开公网。
     调用失败或超时后先按描述查询，查不到再用同一个 ClientToken 重试。
  4. 建高权限账号与库（utf8mb4）。密码取自 SAE Secret 的 mysql.root.password，只在本进程内使用。
  5. 一次 ModifyParameter 改完 PARAMS，只重启一次，再用 RunningParameters 逐项核对。
point-apps 把后端的 JDBC 地址和压测机的 LT_MYSQL_HOST 改成新实例，SAE 会按新配置重新部署。
"""
import argparse
import base64
import json
import os
import sys
import time
import uuid
from pathlib import Path

from cloud import CloudError, aliyun, message, ok, region

PARAMS = {
    "character_set_server": "utf8mb4",
    "transaction_isolation": "REPEATABLE-READ",
    "performance_schema": "1",
    "loose_performance_schema_max_digest_length": "1024",
    "loose_performance_schema_max_sql_text_length": "1024",
}
ENGINE = ["--Engine", "MySQL", "--EngineVersion", "8.0"]
DB_NAME = "yoga"


def rds(api, *args, check=True):
    return aliyun("rds", api, *args, check=check)


def sae_get(path, *args):
    return aliyun("sae", "GET", path, *args).get("Data") or {}


def load(path):
    p = Path(path)
    return json.loads(p.read_text(encoding="utf-8")) if p.is_file() else {}


def save(path, state):
    Path(path).write_text(json.dumps(state, ensure_ascii=False, indent=1), encoding="utf-8")


def log(text):
    print(f"[{time.strftime('%H:%M:%S')}] {text}", flush=True)


def check_network(a):
    cfg = sae_get("/pop/v1/sam/app/describeApplicationConfig", "--AppId", a.app)
    if cfg.get("VSwitchId") != a.vswitch:
        raise CloudError(f"应用 {a.app} 在交换机 {cfg.get('VSwitchId')}，与 --vswitch {a.vswitch} 不一致")
    vpc = cfg["VpcId"]
    vsw = aliyun("vpc", "DescribeVSwitchAttributes", "--RegionId", region(), "--VSwitchId", a.vswitch)
    if vsw.get("VpcId") != vpc:
        raise CloudError(f"交换机 {a.vswitch} 不属于 VPC {vpc}")
    sgs = aliyun("ecs", "DescribeSecurityGroups", "--RegionId", region(), "--VpcId", vpc,
                 "--SecurityGroupId", cfg["SecurityGroupId"])
    if not (sgs.get("SecurityGroups") or {}).get("SecurityGroup"):
        raise CloudError(f"安全组 {cfg['SecurityGroupId']} 不在 VPC {vpc} 内")
    cidr = aliyun("vpc", "DescribeVpcAttribute", "--RegionId", region(), "--VpcId", vpc)["CidrBlock"]
    log(f"网络核对通过: VPC {vpc}（{cidr}），交换机 {a.vswitch}（{vsw.get('VSwitchName')}，{vsw['ZoneId']}，"
        f"{vsw.get('CidrBlock')}），安全组 {cfg['SecurityGroupId']}")
    return {"vpc": vpc, "vswitch": a.vswitch, "zone": vsw["ZoneId"], "vpc_cidr": cidr,
            "security_group": cfg["SecurityGroupId"]}


def memory_gb(text):
    digits = "".join(ch for ch in str(text).split("GB")[0] if ch.isdigit())
    return int(digits) if digits else None


def check_class(a, zone):
    d = rds("DescribeClassDetails", "--RegionId", region(), "--ClassCode", a.cls, "--CommodityCode", "bards", *ENGINE)
    cpu, mem = int(d.get("Cpu") or 0), memory_gb(d.get("MemoryClass"))
    if (cpu, mem) != (a.cpu, a.memory_gb):
        raise CloudError(f"规格 {a.cls} 实际为 {cpu}C{mem}G，期望 {a.cpu}C{a.memory_gb}G")
    avail = rds("DescribeAvailableClasses", "--RegionId", region(), "--ZoneId", zone, *ENGINE,
                "--Category", "HighAvailability", "--DBInstanceStorageType", "cloud_essd",
                "--InstanceChargeType", "Postpaid", "--CommodityCode", "bards")
    if a.cls not in [c["DBInstanceClass"] for c in avail.get("DBInstanceClasses") or []]:
        raise CloudError(f"可用区 {zone} 不售卖 {a.cls}")
    log(f"规格核对通过: {a.cls} = {cpu}C{mem}G（{d.get('MemoryClass', '').strip()}），{zone} 有售")
    return {"class": a.cls, "cpu": cpu, "memory_gb": mem}


def find_instance(description):
    items = rds("DescribeDBInstances", "--RegionId", region(), "--PageSize", "100")["Items"]["DBInstance"]
    found = [x for x in items if x.get("DBInstanceDescription") == description]
    if len(found) > 1:
        raise CloudError(f"描述为 {description} 的实例有 {len(found)} 台: {[x['DBInstanceId'] for x in found]}")
    return found[0]["DBInstanceId"] if found else None


def create_instance(a, net, state):
    token = state.setdefault("client_token", f"yogalt-{uuid.uuid4().hex[:24]}")
    save(a.state, state)
    args = ["--RegionId", region(), *ENGINE, "--DBInstanceClass", a.cls, "--DBInstanceStorage", str(a.storage),
            "--DBInstanceStorageType", "cloud_essd", "--Category", "HighAvailability", "--PayType", "Postpaid",
            "--DBInstanceNetType", "Intranet", "--InstanceNetworkType", "VPC", "--VPCId", net["vpc"],
            "--VSwitchId", a.vswitch, "--ZoneId", net["zone"], "--ZoneIdSlave1", net["zone"],
            "--SecurityIPList", net["vpc_cidr"], "--DBInstanceDescription", a.description, "--ClientToken", token]
    for attempt in range(2):
        r = rds("CreateDBInstance", *args, check=False)
        if ok(r) and r.get("DBInstanceId"):
            return r["DBInstanceId"]
        log(f"CreateDBInstance 未返回实例 ID（{message(r)}），先按描述查询")
        for _ in range(12):
            time.sleep(10)
            found = find_instance(a.description)
            if found:
                return found
        if r.get("error_code") and "Timeout" not in str(r.get("error_code")):
            raise CloudError(f"CreateDBInstance 失败: {message(r)}")
        log(f"2 分钟内没有查到实例，用同一个 ClientToken 重试（第 {attempt + 2} 次）")
    raise CloudError("CreateDBInstance 两次都没有拿到实例")


def attribute(instance):
    return rds("DescribeDBInstanceAttribute", "--DBInstanceId", instance)["Items"]["DBInstanceAttribute"][0]


def wait_running(instance, timeout=1800):
    t0 = time.time()
    while time.time() - t0 < timeout:
        status = attribute(instance)["DBInstanceStatus"]
        if status == "Running":
            return round(time.time() - t0)
        time.sleep(15)
    raise CloudError(f"{instance} 在 {timeout} 秒内没有变为 Running")


def check_instance(instance, a, net):
    x = attribute(instance)
    want = {"DBInstanceClass": a.cls, "DBInstanceCPU": str(a.cpu), "DBInstanceMemory": a.memory_gb * 1024,
            "VSwitchId": a.vswitch, "VpcId": net["vpc"], "PayType": "Postpaid", "Engine": "MySQL",
            "EngineVersion": "8.0", "DBInstanceNetType": "Intranet"}
    bad = {k: x.get(k) for k, v in want.items() if str(x.get(k)) != str(v)}
    if bad:
        raise CloudError(f"实例 {instance} 的属性与期望不符: {bad}")
    ips = rds("DescribeDBInstanceIPArrayList", "--DBInstanceId", instance)["Items"]["DBInstanceIPArray"]
    lists = {i["DBInstanceIPArrayName"]: i["SecurityIPList"] for i in ips if i.get("DBInstanceIPArrayAttribute") != "hidden"}
    extra = {k: v for k, v in lists.items() if v not in ("", net["vpc_cidr"])}
    if extra:
        raise CloudError(f"白名单除 VPC 网段外还有其他条目: {extra}")
    net_info = rds("DescribeDBInstanceNetInfo", "--DBInstanceId", instance)["DBInstanceNetInfos"]["DBInstanceNetInfo"]
    public = [n["ConnectionString"] for n in net_info if n.get("IPType") == "Public"]
    if public:
        raise CloudError(f"实例有公网地址: {public}")
    private = [n for n in net_info if n.get("IPType") == "Private"]
    return {"host": private[0]["ConnectionString"], "port": int(private[0]["Port"]), "whitelist": lists}


def secret_password(namespace, name):
    secrets = sae_get("/pop/v1/sam/secret/secrets", "--NamespaceId", namespace)["Secrets"]
    ids = [s["SecretId"] for s in secrets if s["SecretName"] == name]
    if len(ids) != 1:
        raise CloudError(f"命名空间 {namespace} 里名为 {name} 的 Secret 有 {len(ids)} 个")
    data = sae_get("/pop/v1/sam/secret/secret", "--NamespaceId", namespace, "--SecretId", str(ids[0]))["SecretData"]
    return base64.b64decode(data["mysql.root.password"]).decode("utf-8").strip()


def ensure_account(instance, account, password_source):
    accounts = rds("DescribeAccounts", "--DBInstanceId", instance)["Accounts"]["DBInstanceAccount"]
    if any(x["AccountName"] == account for x in accounts):
        log(f"账号 {account} 已存在")
        return
    rds("CreateAccount", "--DBInstanceId", instance, "--AccountName", account, "--AccountType", "Super",
        "--AccountPassword", password_source(), "--AccountDescription", "loadtest")
    for _ in range(40):
        accounts = rds("DescribeAccounts", "--DBInstanceId", instance)["Accounts"]["DBInstanceAccount"]
        if any(x["AccountName"] == account and x["AccountStatus"] == "Available" for x in accounts):
            log(f"账号 {account} 已创建（高权限）")
            return
        time.sleep(5)
    raise CloudError(f"账号 {account} 没有变为 Available")


def ensure_database(instance):
    dbs = rds("DescribeDatabases", "--DBInstanceId", instance)["Databases"]["Database"]
    found = [d for d in dbs if d["DBName"] == DB_NAME]
    if not found:
        rds("CreateDatabase", "--DBInstanceId", instance, "--DBName", DB_NAME, "--CharacterSetName", "utf8mb4",
            "--DBDescription", "loadtest")
        for _ in range(40):
            found = [d for d in rds("DescribeDatabases", "--DBInstanceId", instance)["Databases"]["Database"]
                     if d["DBName"] == DB_NAME and d.get("DBStatus") == "Running"]
            if found:
                break
            time.sleep(5)
        else:
            raise CloudError(f"库 {DB_NAME} 没有变为 Running")
    if found[0]["CharacterSetName"] != "utf8mb4":
        raise CloudError(f"库 {DB_NAME} 的字符集是 {found[0]['CharacterSetName']}")
    log(f"库 {DB_NAME} 就绪（utf8mb4）")


def running_params(instance):
    d = rds("DescribeParameters", "--DBInstanceId", instance)
    return {p["ParameterName"]: p["ParameterValue"] for p in d["RunningParameters"]["DBInstanceParameter"]}


def ensure_params(instance, state, path):
    current = running_params(instance)
    pending = {k: v for k, v in PARAMS.items() if current.get(k) != v}
    if pending:
        log(f"修改参数并重启（一次）: {pending}")
        token = state.setdefault("param_token", f"yogalt-param-{uuid.uuid4().hex[:16]}")
        save(path, state)
        t0 = time.time()
        rds("ModifyParameter", "--DBInstanceId", instance, "--Parameters", json.dumps(PARAMS),
            "--Forcerestart", "true", "--ClientToken", token)
        deadline, restarted = time.time() + 1800, False
        while time.time() < deadline:
            time.sleep(20)
            status = attribute(instance)["DBInstanceStatus"]
            restarted = restarted or status != "Running"
            if status != "Running" or not (restarted or time.time() - t0 > 300):
                continue
            current = running_params(instance)
            if all(current.get(k) == v for k, v in PARAMS.items()):
                break
        state["param_seconds"] = round(time.time() - t0)
        log(f"参数生效，用时 {state['param_seconds']} 秒")
    bad = {k: (current.get(k), v) for k, v in PARAMS.items() if current.get(k) != v}
    if bad:
        raise CloudError(f"RunningParameters 核对不通过（实际, 期望）: {bad}")
    keys = ["innodb_buffer_pool_size", "sync_binlog", "innodb_flush_log_at_trx_commit", "long_query_time",
            "max_connections"]
    state["parameters"] = {k: current.get(k) for k in [*PARAMS, *keys]}
    log("RunningParameters 核对通过: " + json.dumps({k: current[k] for k in PARAMS}, ensure_ascii=False))


def cmd_setup(a):
    state = load(a.state)
    t0 = time.time()
    state["network"] = check_network(a)
    state["spec"] = check_class(a, state["network"]["zone"])
    save(a.state, state)
    instance = state.get("instance") or find_instance(a.description)
    if instance:
        log(f"实例 {instance} 已存在，跳过创建")
    else:
        t1 = time.time()
        instance = create_instance(a, state["network"], state)
        log(f"已提交创建 {instance}")
        state["instance"] = instance
        save(a.state, state)
        state["create_seconds"] = round(time.time() - t1) + wait_running(instance)
        log(f"{instance} Running，创建用时 {state['create_seconds']} 秒")
    state["instance"] = instance
    wait_running(instance)
    state.update(check_instance(instance, a, state["network"]))
    save(a.state, state)
    ensure_account(instance, a.account, lambda: secret_password(a.namespace, a.secret))
    ensure_database(instance)
    ensure_params(instance, state, a.state)
    state["target"] = f"{state['host']}:{state['port']}/{DB_NAME}"
    state["setup_seconds"] = round(time.time() - t0)
    save(a.state, state)
    log(f"完成: 实例 {instance}，目标库 {state['target']}，本次用时 {state['setup_seconds']} 秒")


def deploy_with_envs(app, change):
    cfg = sae_get("/pop/v1/sam/app/describeApplicationConfig", "--AppId", app)
    envs = json.loads(cfg.get("Envs") or "[]")
    names = {e["name"] for e in envs}
    missing = set(change) - names
    if missing:
        raise CloudError(f"应用 {app} 没有环境变量 {sorted(missing)}")
    if all(e.get("value") == change[e["name"]] for e in envs if e["name"] in change):
        log(f"{cfg['AppName']}: 配置已指向新实例，跳过")
        return None
    for e in envs:
        if e["name"] in change:
            e["value"] = change[e["name"]]
    smd = cfg.get("SecretMountDesc")
    args = ["--AppId", app, "--PackageType", "Image", "--ImageUrl", cfg["ImageUrl"], "--Envs", json.dumps(envs),
            "--MinReadyInstances", "0"]
    if smd:
        args += ["--SecretMountDesc", smd if isinstance(smd, str) else json.dumps(smd)]
    order = (aliyun("sae", "POST", "/pop/v1/sam/app/deployApplication", *args).get("Data") or {}).get("ChangeOrderId")
    log(f"{cfg['AppName']}: 已提交部署，发布单 {order}")
    return order


def wait_order(order, label, timeout=1200):
    t0 = time.time()
    while time.time() - t0 < timeout:
        st = sae_get("/pop/v1/sam/changeorder/DescribeChangeOrder", "--ChangeOrderId", order).get("Status")
        if st == 2:
            return
        if st in (3, 6, 10):
            raise CloudError(f"{label}: 发布单失败或被终止 (status={st})")
        time.sleep(10)
    raise CloudError(f"{label}: 等待发布单超过 {timeout} 秒")


def jdbc_url(url, host, port):
    """只替换 JDBC URL 里的 host:port/db，保留查询参数。"""
    head, rest = url.split("//", 1)
    query = rest.split("?", 1)[1] if "?" in rest else ""
    return f"{head}//{host}:{port}/{DB_NAME}" + (f"?{query}" if query else "")


def cmd_point_apps(a):
    state = load(a.state)
    backend = sae_get("/pop/v1/sam/app/describeApplicationConfig", "--AppId", a.backend)
    url = next(e.get("value", "") for e in json.loads(backend["Envs"]) if e["name"] == "SPRING_DATASOURCE_DRUID_MASTER_URL")
    orders = []
    for app, change, label in ((a.backend, {"SPRING_DATASOURCE_DRUID_MASTER_URL": jdbc_url(url, state["host"], state["port"])}, "后端"),
                               (a.loadgen, {"LT_MYSQL_HOST": state["host"]}, "压测机")):
        order = deploy_with_envs(app, change)
        if order:
            orders.append((order, label))
    for order, label in orders:
        wait_order(order, label)
        log(f"{label}: 发布单完成")


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("setup")
    s.add_argument("--app", required=True, help="用来核对网络的 SAE 应用（后端）")
    s.add_argument("--vswitch", required=True)
    s.add_argument("--class", dest="cls", required=True)
    s.add_argument("--cpu", type=int, required=True)
    s.add_argument("--memory-gb", type=int, required=True)
    s.add_argument("--storage", type=int, default=100)
    s.add_argument("--description", required=True)
    s.add_argument("--account", default="yogalt")
    s.add_argument("--namespace", required=True)
    s.add_argument("--secret", required=True)
    s.add_argument("--state", required=True)
    pa = sub.add_parser("point-apps")
    pa.add_argument("--state", required=True)
    pa.add_argument("--backend", required=True)
    pa.add_argument("--loadgen", required=True)
    a = p.parse_args()
    os.environ.setdefault("ALIBABA_CLOUD_REGION_ID", "cn-shanghai")
    try:
        {"setup": cmd_setup, "point-apps": cmd_point_apps}[a.cmd](a)
    except CloudError as e:
        sys.exit(str(e))


if __name__ == "__main__":
    main()
