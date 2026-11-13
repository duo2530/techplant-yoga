const ST = { PENDING: "待上架", PUBLISHED: "已上架", CANCELLED: "已取消" };

const PHASE = {
  status_read: "未开始",
  status_update: "已读状态/准备写入",
  under_lockSchedule: "未开始",
  under_closeSchedule: "持排课锁/准备取消",
  under_scan: "持排课锁/取消预约",
  under_next: "持排课锁/取消预约",
  under_release: "持排课锁/取消预约",
  refund_readDeduct: "持排课锁/取消预约",
  commit: "待提交",
  abort: "回滚中",
  committed: "已结束",
  rejected: "已结束",
};

export const title = "管理员两次修改排课状态, 与人数不足自动取消同时发生";

export const state = (v) => ({
  "排课状态 (已提交)": ST[v.db[JSON.stringify({ 1: "S", 2: 1 })].st],
  "管理员改为待上架": PHASE[v.pc[1]],
  "管理员改为已取消": PHASE[v.pc[2]],
  "自动取消": PHASE[v.pc[3]],
});

export const step = (action, from, to) => {
  const p = [1, 2, 3].find((i) => from.pc[i] !== to.pc[i]);
  if (p === undefined) return null;
  const who = ["", "下架请求", "取消请求", "自动取消"][p];
  const at = from.pc[p];
  if (at === "status_read") {
    if (to.pc[p] === "status_update") return who + "读到状态并通过流转校验";
    if (to.pc[p] === "commit") return who + "读到状态已是目标, 直接返回";
    return who + "读到状态不允许流转, 拒绝";
  }
  if (at === "under_lockSchedule")
    return who + (to.pc[p] === "under_closeSchedule" ? "持锁确认人数不足" : "持锁确认无需取消");
  if (at === "status_update") return who + "写入新状态";
  if (at === "commit") return who + "提交";
  if (at === "abort") return who + "回滚";
  return who + "取消排课";
};
