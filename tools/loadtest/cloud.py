"""loadtest-capacity 工作流的云操作：启停已创建好的 SAE 应用，给压测机临时绑一个只放行指定 IP 的公网 CLB，结束后删除。

  python cloud.py start <AppId>...                    启动应用并等待实例 Running
  python cloud.py expose --app <AppId> --port 8000 --ip <IP> --name <名称> --state <文件>
  python cloud.py verify --state <文件>               复查白名单，不符合时立即删除 CLB 与 ACL 并失败退出
  python cloud.py cleanup --state <文件> --stop <AppId>...   解绑并删除 CLB 与 ACL，停止应用

凭证与地域由 aliyun CLI 从 ALIBABA_CLOUD_ACCESS_KEY_ID / ALIBABA_CLOUD_ACCESS_KEY_SECRET / ALIBABA_CLOUD_REGION_ID 读取。
cleanup 只删除 state 文件里记录的、本次创建的 CLB 与 ACL。

监听由 SAE 在 BindSlb 时创建，创建时不带访问控制，因此 expose 的顺序是：
先建 ACL 并确认条目只有 --ip，再建 CLB 并设为 inactive（实例下的监听都不转发），然后绑定；
监听一出现就停止它并开启白名单；确认 AclStatus=on 之后才启动监听、把 CLB 设回 active，再复查一次。
任何一步失败都立即删除本次创建的 CLB 与 ACL。
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path


class CloudError(RuntimeError):
    pass


def message(r):
    return r.get("Message") or r.get("message") or r.get("_raw") or f"退出码 {r.get('_exit')}"


def ok(r):
    if r.get("_exit") or r.get("_raw") or r.get("error_code") or r.get("Success") is False:
        return False
    return "Code" not in r or r["Code"] in (200, "200")


def aliyun(product, api, *args, check=True):
    """调用 aliyun CLI；check 为真时，退出码非 0 或响应表示失败都抛 CloudError。"""
    cmd = ["aliyun", product, api, *args]
    if product == "sae":
        cmd += ["--header", "Content-Type=application/json"]
    p = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    out = (p.stdout or "").strip() or (p.stderr or "").strip()
    try:
        r = json.loads(out)
    except json.JSONDecodeError:
        r = None
    if not isinstance(r, dict):
        r = {"_raw": out[:500] or "无输出"}
    if p.returncode:
        r["_exit"] = p.returncode
    if check and not ok(r):
        raise CloudError(f"{product} {api} 失败: {message(r)}")
    return r


def region():
    return os.environ["ALIBABA_CLOUD_REGION_ID"]


def wait_order(order, label, on_tick=None, timeout=900):
    t0 = time.time()
    while time.time() - t0 < timeout:
        if on_tick:
            on_tick()
        status = (aliyun("sae", "DescribeChangeOrder", "--ChangeOrderId", order).get("Data") or {}).get("Status")
        if status == 2:
            return
        if status in (3, 6, 10):
            raise CloudError(f"{label}: 发布单失败或被终止 (status={status})")
        time.sleep(5)
    raise CloudError(f"{label}: 等待发布单超过 {timeout} 秒")


def app_status(app):
    return aliyun("sae", "DescribeApplicationStatus", "--AppId", app).get("Data") or {}


def wait_running(app, timeout=900):
    t0, stable = time.time(), 0
    while time.time() - t0 < timeout:
        d = app_status(app)
        running = d.get("CurrentStatus") == "RUNNING" and (d.get("RunningInstances") or 0) >= 1
        stable = stable + 1 if running else 0
        if stable >= 2:
            return
        time.sleep(10)
    raise CloudError(f"{app}: {timeout} 秒内没有稳定 Running")


def cmd_start(a):
    for app in a.apps:
        if (app_status(app).get("RunningInstances") or 0) > 0:
            print(f"{app}: 已在运行")
            continue
        order = (aliyun("sae", "StartApplication", "--AppId", app).get("Data") or {}).get("ChangeOrderId")
        if order:
            wait_order(order, f"启动 {app}")
        wait_running(app)
        print(f"{app}: 已启动")


def save(path, state):
    Path(path).write_text(json.dumps(state), encoding="utf-8")


def slb(api, *args, check=True):
    return aliyun("slb", api, "--RegionId", region(), *args, check=check)


def listener(state, check=True):
    return slb("DescribeLoadBalancerTCPListenerAttribute", "--LoadBalancerId", state["lb"],
               "--ListenerPort", str(state["port"]), check=check)


def acl_on(r, state):
    return r.get("AclStatus") == "on" and r.get("AclType") == "white" and r.get("AclId") == state["acl"]


def acl_entries(acl):
    r = slb("DescribeAccessControlListAttribute", "--AclId", acl, "--PageSize", "50")
    return sorted(e.get("AclEntryIP") for e in (r.get("AclEntrys") or {}).get("AclEntry") or [])


def lb_status(lb):
    return slb("DescribeLoadBalancerAttribute", "--LoadBalancerId", lb).get("LoadBalancerStatus")


def set_lb_status(lb, status, timeout=60):
    slb("SetLoadBalancerStatus", "--LoadBalancerId", lb, "--LoadBalancerStatus", status)
    t0 = time.time()
    while lb_status(lb) != status:
        if time.time() - t0 > timeout:
            raise CloudError(f"CLB {lb} 在 {timeout} 秒内没有变为 {status}")
        time.sleep(2)


def lock_listener(state):
    """绑定期间轮询：监听一出现，先停止再开启白名单。监听还没创建时查询会失败，此时不做任何事。"""
    r = listener(state, check=False)
    if not ok(r) or "ListenerPort" not in r or acl_on(r, state):
        return
    port = ["--LoadBalancerId", state["lb"], "--ListenerPort", str(state["port"])]
    if r.get("Status") != "stopped":
        slb("StopLoadBalancerListener", *port)
        print(f"监听 {state['port']} 已出现，先停止", flush=True)
    slb("SetLoadBalancerTCPListenerAttribute", *port, "--AclStatus", "on", "--AclType", "white",
        "--AclId", state["acl"], "--DryRun", "false")


def check_exposure(state):
    """监听必须开启本次 ACL 的白名单，且 ACL 只放行 state["allow"]，否则抛 CloudError。"""
    r = listener(state)
    if not acl_on(r, state):
        raise CloudError(f"监听 {state['port']} 未启用本次 ACL 的白名单: AclStatus={r.get('AclStatus')}"
                         f" AclType={r.get('AclType')} AclId={r.get('AclId')}")
    entries = acl_entries(state["acl"])
    if entries != [state["allow"]]:
        raise CloudError(f"ACL {state['acl']} 的条目应只有 {state['allow']}，实际为 {entries}")
    return r


def expose(a, state):
    acl = slb("CreateAccessControlList", "--AclName", a.name, "--AddressIPVersion", "ipv4")
    if "AclId" not in acl:
        raise CloudError(f"创建 ACL 没有返回 AclId: {message(acl)}")
    state["acl"] = acl["AclId"]
    save(a.state, state)
    slb("AddAccessControlListEntry", "--AclId", state["acl"],
        "--AclEntrys", json.dumps([{"entry": state["allow"], "comment": "github-runner"}]))
    if acl_entries(state["acl"]) != [state["allow"]]:
        raise CloudError(f"ACL {state['acl']} 的条目与 {state['allow']} 不符")

    zone = ["--MasterZoneId", a.zone] if a.zone else []
    lb = slb("CreateLoadBalancer", "--LoadBalancerName", a.name, "--AddressType", "internet",
             "--PayType", "PayOnDemand", "--InstanceChargeType", "PayByCLCU", "--InternetChargeType", "paybytraffic",
             *zone)
    if "LoadBalancerId" not in lb:
        raise CloudError(f"创建 CLB 没有返回 LoadBalancerId: {message(lb)}")
    state.update(lb=lb["LoadBalancerId"], ip=lb["Address"])
    save(a.state, state)
    set_lb_status(state["lb"], "inactive")
    print(f"CLB {state['lb']} 已设为 inactive，开始绑定", flush=True)

    ports = json.dumps([{"port": a.port, "targetPort": a.port, "protocol": "TCP"}])
    order = (aliyun("sae", "BindSlb", "--AppId", a.app, "--Internet", ports, "--InternetSlbId", state["lb"])
             .get("Data") or {}).get("ChangeOrderId")
    state["bound"] = True
    save(a.state, state)
    if not order:
        raise CloudError("绑定 CLB 没有返回 ChangeOrderId")
    wait_order(order, "绑定 CLB", on_tick=lambda: lock_listener(state))
    for _ in range(30):
        lock_listener(state)
        r = listener(state, check=False)
        if ok(r) and acl_on(r, state):
            break
        time.sleep(2)
    else:
        raise CloudError(f"绑定完成后 60 秒内监听 {a.port} 没有启用白名单")
    check_exposure(state)

    status = lb_status(state["lb"])
    if status != "inactive":
        raise CloudError(f"绑定期间 CLB 状态被改为 {status}，白名单生效前监听可能已对公网开放")
    if listener(state).get("Status") != "running":
        slb("StartLoadBalancerListener", "--LoadBalancerId", state["lb"], "--ListenerPort", str(a.port))
    set_lb_status(state["lb"], "active")
    r = check_exposure(state)
    print(f"白名单复查通过: AclStatus={r['AclStatus']} AclType={r['AclType']} AclId={r['AclId']}"
          f" 监听状态={r.get('Status')}")


def release(state, path):
    """依次停止转发、解绑、删除 CLB 与 ACL，每完成一项就更新 state 文件，返回失败项。"""
    failed = []
    if state.get("lb"):
        slb("SetLoadBalancerStatus", "--LoadBalancerId", state["lb"], "--LoadBalancerStatus", "inactive", check=False)
    if state.get("bound"):
        try:
            order = (aliyun("sae", "UnbindSlb", "--AppId", state["app"], "--Internet", "true")
                     .get("Data") or {}).get("ChangeOrderId")
            if order:
                wait_order(order, "解绑 CLB")
            state.pop("bound")
            save(path, state)
        except CloudError as e:
            print(f"解绑 CLB: {e}")
            failed.append("解绑 CLB")
    for key, api, arg, label in (("lb", "DeleteLoadBalancer", "--LoadBalancerId", "CLB"),
                                 ("acl", "DeleteAccessControlList", "--AclId", "ACL")):
        if not state.get(key):
            continue
        try:
            slb(api, arg, state[key])
            print(f"删除 {label} {state[key]}: OK")
            state.pop(key)
            save(path, state)
        except CloudError as e:
            print(f"删除 {label} {state[key]}: {e}")
            failed.append(f"删除 {label} {state[key]}")
    return failed


def fail_and_release(state, path, err):
    print(f"::error::{err}", flush=True)
    failed = release(state, path)
    if failed:
        sys.exit(f"{err}；清理未完成，需要人工处理: " + "; ".join(failed))
    sys.exit(f"{err}；已删除本次创建的 CLB 与 ACL")


def cmd_expose(a):
    if not re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}", a.ip or ""):
        sys.exit(f"--ip 不是 IPv4 地址: {a.ip!r}")
    state = {"app": a.app, "port": a.port, "allow": f"{a.ip}/32"}
    save(a.state, state)
    try:
        expose(a, state)
    except CloudError as e:
        fail_and_release(state, a.state, e)
    print(f"CLB {state['lb']} 公网 IP {state['ip']}，端口 {a.port} 只放行 {a.ip}")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as f:
            f.write(f"agent=http://{state['ip']}:{a.port}\n")


def cmd_verify(a):
    state = json.loads(Path(a.state).read_text(encoding="utf-8"))
    try:
        check_exposure(state)
    except CloudError as e:
        fail_and_release(state, a.state, e)
    print(f"监听 {state['port']} 白名单生效，ACL 只有 {state['allow']}")


def cmd_cleanup(a):
    state = json.loads(Path(a.state).read_text(encoding="utf-8")) if a.state and Path(a.state).is_file() else {}
    failed = release(state, a.state) if state else []
    for app in a.stop or []:
        r = aliyun("sae", "StopApplication", "--AppId", app, check=False)
        print(f"停止 {app}: {'OK' if ok(r) else message(r)}")
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
    v = sub.add_parser("verify")
    v.add_argument("--state", required=True)
    c = sub.add_parser("cleanup")
    c.add_argument("--state")
    c.add_argument("--stop", nargs="*")
    a = p.parse_args()
    try:
        {"start": cmd_start, "expose": cmd_expose, "verify": cmd_verify, "cleanup": cmd_cleanup}[a.cmd](a)
    except CloudError as e:
        sys.exit(str(e))


if __name__ == "__main__":
    main()
