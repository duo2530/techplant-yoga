# 人数不足自动取消

已上架的排课进入开课前 120 分钟时，已预约人数仍小于最低开课人数的，由定时任务（每分钟一次）自动取消：排课变为已取消，已预约记录取消并按原扣次返次，排队中记录一并取消，取消原因都是“排课取消”。管理员手动取消排课不做这些批量处理。

## Sub-features

- `auto-cancel-job` 管理端“系统监控 → 定时任务”有“人数不足自动取消排课”，调用目标 `yogaScheduleTask.cancelUnderstaffed`，cron `0 * * * * ?`，可“执行一次”。
- `auto-cancel-before-window` 距开课超过 120 分钟时执行任务，不取消任何排课。
- `auto-cancel-understaffed` 进入 120 分钟后，人数不足的排课变为已取消，预约变为已取消（排课取消），卡次数返还，写一条返次流水。
- `auto-cancel-control` 同一时间、人数已达最低开课人数的排课保持已上架。
- `auto-cancel-member-view` 用户端我的预约里这条预约显示“已取消”，约课列表与详情页这节课显示“已取消”。
- `auto-cancel-admin-view` 管理端排课管理里这节课状态为“已取消”，预约管理里取消原因为“排课取消”。

## How to get to it (user POV)

- 会员无需操作：到点后系统自动取消，会员在我的预约“全部”或“已取消”Tab 看到结果。
- 约课列表（`http://localhost:9090/#/pages/booking/index`，徐汇店、团课，按开课日期）看这节课的状态。
- 管理端排课管理、预约管理看取消结果；“系统监控 → 定时任务”看任务和执行日志。

## Driving it with agent-browser

Preconditions:

- 开发库已执行 `backend/sql/migrations/20261109-schedule-auto-cancel-job.sql`，并在之后重启了后端（任务在启动时从 `sys_job` 加载）。`status` 第一行显示 `定时任务 100：yogaScheduleTask.cancelUnderstaffed  0 * * * * ?  正常`。
- 运行 `node .agents/skills/verify-yoga/scripts/auto-cancel.mjs setup`（21:00～22:00 之间会跨午夜，不能执行）。它在约 125 分钟后造两节团课，22:00 之后执行时课在明天凌晨：
  - 不足排课：最大 5 人、最低 3 人，验收账号 `19900000005` 和 C `19900000003` 已预约（2 < 3）。输出第一行给出 ID，记为 `$U`。
  - 对照排课：同一时间另一位教练、另一间教室，最低 2 人，A、B 已预约（2 = 2）。输出第二行给出 ID，记为 `$K`。
  - 第三行给出进入 120 分钟窗口的时刻，约为 setup 后 5 分钟。
- 已用 `19900000005` 登录，见 [login.md](login.md)。

- **取消前。** 运行 `$B open "http://localhost:9090/#/pages/detail/index?scheduleId=$U"`、`$B reload`、`$B wait 2000`、`$B get text ".card__status"`。输出可约状态，不是 `已取消`。截图 `01-取消前详情页.png`。
- **未进入窗口不取消。** 进入窗口前运行 `node .agents/skills/verify-yoga/scripts/auto-cancel.mjs run`。两节课都还是 `已上架`，卡剩余 9 次。
- **自动取消。** 等到第三行给出的时刻后再等 1 分钟，或直接运行 `run`。`status` 显示不足排课 `已取消  已预约 0`，验收体验和 C 都是 `已取消（排课取消）  卡剩余 10 次  返次流水 1 条`；对照排课仍为 `已上架  已预约 2`，A、B 仍是已预约、卡 9 次。
- **我的预约。** 运行 `$B open "http://localhost:9090/#/pages/my-bookings/index"`、`$B reload`、`$B wait 2000`、`$B find text "已取消" click`、`$B wait 2000`、`$B eval "[...document.querySelectorAll('.ys-booking')].map(e => e.innerText.replace(/\n/g,' '))"`。输出含这节课，状态 `已取消`。截图 `02-我的预约已取消.png`。
- **约课列表。** 运行 `$B open "http://localhost:9090/#/pages/booking/index"`、`$B reload`、`$B wait 2000`，开课日期是明天时先点日期条第 2 天，再 `$B eval "[...document.querySelectorAll('.ccard')].map(e => e.querySelector('.ccard__meta').innerText + ' | ' + e.querySelector('.ccard__status').innerText + ' | ' + e.querySelector('.ccard__btn').innerText)"`。按开始时间找到两节课：`$U` 是 `已取消`，`$K` 不是。截图 `03-约课列表已取消.png`。
- **详情页。** 运行 `$B open "http://localhost:9090/#/pages/detail/index?scheduleId=$U"`、`$B reload`、`$B wait 2000`、`$B get text ".card__status"`。输出 `已取消`。截图 `04-详情页已取消.png`。
- **管理端。** admin／admin123 登录 `http://localhost`。排课管理（`/operation/schedule`）按开课日期筛选，`$U` 状态“已取消”、`$K` 状态“已上架”；预约管理（`/operation/reservation`）里两条预约状态“已取消”、取消原因“排课取消”；“系统监控 → 定时任务”的调度日志有成功记录。截图 `05-管理端排课已取消.png`。

## Gotchas

- 后端在执行迁移之前启动的，任务不会加载，`run` 返回成功但不会执行。执行迁移后先重启后端。
- 任务每分钟都在跑。setup 到进入窗口只有约 5 分钟，“取消前”和“未进入窗口”两步要在这之前做完。来不及就重新 `setup`。
- 本脚本复用 `queue.mjs` 的验收会员 A、B、C 与验收体验，两个脚本的 `setup` 会互相清掉对方的数据。
- 开课后取消窗口已过，约课列表显示“已结束”优先于其它状态；隔太久再看要重新 `setup`。
- 21:00～22:00 之间 setup 造出的课会跨过午夜，脚本直接报错退出。
