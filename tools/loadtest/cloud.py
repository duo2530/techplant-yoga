"""loadtest-capacity 工作流的云操作：启停已创建好的 SAE 应用，给压测机临时绑一个只放行指定 IP 的公网 CLB，结束后删除。

  python cloud.py start <AppId>...                    启动应用并等待实例 Running
  python cloud.py expose --app <AppId> --port 8000 --ip <IP> --name <名称> --state <文件>
  python cloud.py cleanup --state <文件> --stop <AppId>...   解绑并删除 CLB 与 ACL，停止应用

凭证与地域由 aliyun CLI 从 ALIBABA_CLOUD_ACCESS_KEY_ID / ALIBABA_CLOUD_ACCESS_KEY_SECRET / ALIBABA_CLOUD_REGION_ID 读取。
cleanup 只删除 state 文件里记录的、本次创建的 CLB 与 ACL。
"""
import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path


def aliyun(product, *args):
    cmd = ["aliyun", product, *args]
    if product == "sae":
        cmd += ["--header", "Content-Type=application/json"]
    p = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    out = (p.stdout or "").strip() or (p.stderr or "").strip()
    try:
        return json.loads(out)
    except json.JSONDecodeError:
        return {"_raw": out[:500]}


def ok(r):
    if r.get("_raw") or r.get("error_code") or r.get("Success") is False:
        return False
    return "Code" not in r or r["Code"] in (200, "200")


def need(r, what):
    if not ok(r):
        sys.exit(f"{what} 失败: {r.get('Message') or r.get('message') or r.get('_raw')}")
    return r.get("Data", r)


def region():
    return os.environ["ALIBABA_CLOUD_REGION_ID"]


def wait_order(order, label, on_tick=None, timeout=900):
    t0 = time.time()
    while time.time() - t0 < timeout:
        if on_tick:
            on_tick()
        status = need(aliyun("sae", "DescribeChangeOrder", "--ChangeOrderId", order), f"{label} 查发布单")["Status"]
        if status == 2:
            return
        if status in (3, 6, 10):
            sys.exit(f"{label}: 发布单失败或被终止 (status={status})")
        time.sleep(5)
    sys.exit(f"{label}: 等待发布单超过 {timeout} 秒")


def wait_running(app, timeout=900):
    t0, stable = time.time(), 0
    while time.time() - t0 < timeout:
        d = need(aliyun("sae", "DescribeApplicationStatus", "--AppId", app), "查应用状态")
        running = d.get("CurrentStatus") == "RUNNING" and (d.get("RunningInstances") or 0) >= 1
        stable = stable + 1 if running else 0
        if stable >= 2:
            return
        time.sleep(10)
    sys.exit(f"{app}: {timeout} 秒内没有稳定 Running")


def cmd_start(a):
    for app in a.apps:
        d = need(aliyun("sae", "DescribeApplicationStatus", "--AppId", app), "查应用状态")
        if (d.get("RunningInstances") or 0) > 0:
            print(f"{app}: 已在运行")
            continue
        data = need(aliyun("sae", "StartApplication", "--AppId", app), f"启动 {app}")
        if data and data.get("ChangeOrderId"):
            wait_order(data["ChangeOrderId"], f"启动 {app}")
        wait_running(app)
        print(f"{app}: 已启动")


def save(path, state):
    Path(path).write_text(json.dumps(state), encoding="utf-8")


def guard(lb, port, acl):
    """监听一出现就开启白名单，返回是否已经确认生效。"""
    r = aliyun("slb", "DescribeLoadBalancerTCPListenerAttribute", "--RegionId", region(), "--LoadBalancerId", lb,
               "--ListenerPort", str(port))
    if not ok(r) or "ListenerPort" not in r:
        return False
    if r.get("AclStatus") == "on" and r.get("AclId") == acl and r.get("AclType") == "white":
        return True
    aliyun("slb", "SetLoadBalancerTCPListenerAttribute", "--RegionId", region(), "--LoadBalancerId", lb,
           "--ListenerPort", str(port), "--AclStatus", "on", "--AclType", "white", "--AclId", acl, "--DryRun", "false")
    return False


def cmd_expose(a):
    state = {"app": a.app}
    acl = aliyun("slb", "CreateAccessControlList", "--RegionId", region(), "--AclName", a.name,
                 "--AddressIPVersion", "ipv4")
    if "AclId" not in acl:
        sys.exit(f"创建 ACL 失败: {acl.get('Message') or acl.get('_raw')}")
    state["acl"] = acl["AclId"]
    save(a.state, state)
    entries = json.dumps([{"entry": f"{a.ip}/32", "comment": "github-runner"}])
    need(aliyun("slb", "AddAccessControlListEntry", "--RegionId", region(), "--AclId", state["acl"],
                "--AclEntrys", entries), "添加 ACL 条目")
    zone = ["--MasterZoneId", a.zone] if a.zone else []
    lb = aliyun("slb", "CreateLoadBalancer", "--RegionId", region(), "--LoadBalancerName", a.name,
                "--AddressType", "internet", "--PayType", "PayOnDemand", "--InstanceChargeType", "PayByCLCU",
                "--InternetChargeType", "paybytraffic", *zone)
    if "LoadBalancerId" not in lb:
        sys.exit(f"创建 CLB 失败: {lb.get('Message') or lb.get('_raw')}")
    state.update(lb=lb["LoadBalancerId"], ip=lb["Address"])
    save(a.state, state)
    ports = json.dumps([{"port": a.port, "targetPort": a.port, "protocol": "TCP"}])
    data = need(aliyun("sae", "BindSlb", "--AppId", a.app, "--Internet", ports, "--InternetSlbId", state["lb"]),
                "绑定 CLB")
    state["bound"] = True
    save(a.state, state)
    wait_order(data["ChangeOrderId"], "绑定 CLB", on_tick=lambda: guard(state["lb"], a.port, state["acl"]))
    for _ in range(30):
        if guard(state["lb"], a.port, state["acl"]):
            break
        time.sleep(2)
    else:
        sys.exit(f"监听 {a.port} 没能启用白名单，cleanup 会删除这个 CLB")
    print(f"CLB {state['lb']} 公网 IP {state['ip']}，端口 {a.port} 只放行 {a.ip}")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as f:
            f.write(f"agent=http://{state['ip']}:{a.port}\n")


def cmd_cleanup(a):
    failed = []
    state = json.loads(Path(a.state).read_text(encoding="utf-8")) if a.state and Path(a.state).is_file() else {}
    if state.get("bound"):
        r = aliyun("sae", "UnbindSlb", "--AppId", state["app"], "--Internet", "true")
        if ok(r) and (r.get("Data") or {}).get("ChangeOrderId"):
            wait_order(r["Data"]["ChangeOrderId"], "解绑 CLB")
        elif not ok(r):
            failed.append(f"解绑 CLB: {r.get('Message') or r.get('_raw')}")
    if state.get("lb"):
        r = aliyun("slb", "DeleteLoadBalancer", "--RegionId", region(), "--LoadBalancerId", state["lb"])
        print(f"删除 CLB {state['lb']}: {'OK' if ok(r) else r.get('Message') or r.get('_raw')}")
        if not ok(r):
            failed.append(f"删除 CLB {state['lb']}")
    if state.get("acl"):
        r = aliyun("slb", "DeleteAccessControlList", "--RegionId", region(), "--AclId", state["acl"])
        print(f"删除 ACL {state['acl']}: {'OK' if ok(r) else r.get('Message') or r.get('_raw')}")
        if not ok(r):
            failed.append(f"删除 ACL {state['acl']}")
    for app in a.stop or []:
        r = aliyun("sae", "StopApplication", "--AppId", app)
        print(f"停止 {app}: {'OK' if ok(r) else r.get('Message') or r.get('_raw')}")
        if not ok(r):
            failed.append(f"停止 {app}")
    if failed:
        sys.exit("清理未完成，需要人工处理: " + "; ".join(failed))


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("start")
    s.add_argument("apps", nargs="+")
    e = sub.add_parser("expose")
    e.add_argument("--app", required=True)
    e.add_argument("--port", type=int, default=8000)
    e.add_argument("--ip", required=True)
    e.add_argument("--name", required=True)
    e.add_argument("--zone")
    e.add_argument("--state", required=True)
    c = sub.add_parser("cleanup")
    c.add_argument("--state")
    c.add_argument("--stop", nargs="*")
    a = p.parse_args()
    {"start": cmd_start, "expose": cmd_expose, "cleanup": cmd_cleanup}[a.cmd](a)


if __name__ == "__main__":
    main()
