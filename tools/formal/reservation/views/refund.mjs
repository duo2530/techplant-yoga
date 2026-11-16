const row = (v, t, i) => v.db[JSON.stringify({ 1: t, 2: i })];

const PHASE = {
  cancel_peek: "未开始",
  cancel_lockSchedule: "已读预约/等排课锁",
  cancel_lockReservation: "持排课锁/复查预约",
  cancel_cancelQueued: "持排课锁/取消排队",
  cancel_cancelReserved: "持排课锁/按已预约取消",
  cancel_release: "持排课锁/按已预约取消",
  refund_readDeduct: "持排课锁/查扣次流水",
  refund_lockCard: "持排课锁/返次",
  refund_update: "持排课锁/返次",
  refund_ledger: "持排课锁/返次",
  promote_findQueued: "持排课锁/补位",
  promote_lockCard: "持排课锁/补位",
  promote_markFailed: "持排课锁/补位",
  promote_promote: "持排课锁/补位",
  promote_occupy: "持排课锁/补位",
  promote_deduct: "持排课锁/补位",
  promote_ledger: "持排课锁/补位",
  commit: "待提交",
  abort: "回滚中",
  committed: "已提交",
  rejected: "已回滚",
};

const queuedSeat = (v) => {
  const st = row(v, "R", 2);
  const deducted = row(v, "D", 2);
  const refunded = row(v, "F", 2);
  if (st === "QUEUED") return "排队中/未扣次";
  if (st === "RESERVED") return "已补位/已扣次";
  if (!deducted) return "已取消排队/未扣次";
  return refunded ? "已取消/扣次已返还" : "已取消, 扣次未返还";
};

export const title = "会员A取消已预约, 会员B同时取消自己的排队";

export const state = (v) => ({
  "会员A的请求": PHASE[v.pc[1]],
  "会员B的请求": PHASE[v.pc[2]],
  "会员B的预约 (已提交)": queuedSeat(v),
});

const SAY = {
  cancel_peek: "读出自己的预约",
  cancel_lockSchedule: "取得排课锁",
  cancel_lockReservation: "锁定预约并读到最新状态",
  cancel_cancelQueued: "把排队改为已取消",
  cancel_cancelReserved: "把预约改为已取消",
  cancel_release: "释放名额",
  refund_lockCard: "锁定会员卡",
  refund_update: "加回次数",
  refund_ledger: "写返次流水",
  promote_lockCard: "锁定排队会员的卡",
  promote_markFailed: "把排队标为失败",
  promote_promote: "把排队转为已预约",
  promote_occupy: "占用名额",
  promote_deduct: "扣次",
  promote_ledger: "写扣次流水",
  commit: "提交",
  abort: "回滚",
};

export const step = (action, from, to) => {
  const p = from.pc[1] !== to.pc[1] ? 1 : from.pc[2] !== to.pc[2] ? 2 : 0;
  if (p === 0) return null;
  const who = p === 1 ? "A " : "B ";
  const at = from.pc[p];
  if (action === "DeadlockVictim") return who + "被数据库选为死锁受害者";
  if (at === "refund_readDeduct")
    return who + (to.pc[p] === "refund_lockCard" ? "查到扣次流水" : "查不到扣次流水, 不返次");
  if (at === "promote_findQueued")
    return who + (to.pc[p] === "promote_lockCard" ? "找到排队会员" : "没有可补位的排队");
  if (at === "cancel_lockReservation" && to.pc[p] === "commit") return who + "读到已取消, 按重复请求返回";
  return who + (SAY[at] ?? at);
};
