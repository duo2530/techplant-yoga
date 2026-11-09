# 课程排队

课程满员时，会员可以加入排队并看到自己的名次；有人取消预约后，排在最前面的会员自动转为已预约并扣次。

## Sub-features

- `queue-entry` 满员课程详情页按钮显示“排队”，预约页人数固定 1、按钮显示“加入排队”并提示当前排队人数。
- `queue-join` 加入排队后弹窗“排队成功”，我的预约“排队中”Tab 显示名次，卡不扣次。
- `queue-promote` 有人取消预约后，排第 1 位的会员转为已预约，卡扣 1 次。
- `queue-cancel` 排队中的记录可以“取消排队”，不影响名额和次数。
- `queue-failed` 过了开课前 120 分钟仍在排队的记录显示为“排队失败”。

## How to get to it (user POV)

- 约课列表或首页点进满员课程的详情页，点底部“排队”。
- 我的预约 → “排队中”Tab 查看名次、取消排队。
- 管理端预约管理按“排队中”／“排队失败”筛选。

## Driving it with agent-browser

Preconditions:

- 运行 `node .agents/skills/verify-yoga/scripts/queue.mjs setup`。它造一节明天 15:00 的团课（最大 3 人），会员 A、B、C 已预约，D 排第 1 位，验收账号 `19900000005` 未排队，所有人各一张 10 次团课次卡。输出第一行给出排课 ID，记为 `$S`。
- 已用 `19900000005` 登录，见 [login.md](login.md)。

- **详情页入口。** 运行 `$B open "http://localhost:9090/#/pages/detail/index?scheduleId=$S"`、`$B wait 2000`、`$B get text ".bar__btn"`。输出 `排队`。截图 `01-详情页满员可排队.png`。
- **预约页。** 运行 `$B click ".bar__btn"`、`$B wait 2000`、`$B get text ".queue-tip"`、`$B get text ".bar__btn"`。URL 是 `#/pages/booking-confirm/index?scheduleId=$S`，提示含 `当前 1 人排队`，按钮 `加入排队`。截图 `02-预约页加入排队.png`。
- **加入排队。** 运行 `$B click ".bar__btn"` 和 `$B wait 1500`。出现“排队成功”弹窗和“查看预约”按钮。截图 `03-排队成功弹窗.png`。
- **查看名次。** 运行 `$B find text "查看预约" click`、`$B wait 2000`、`$B find text "排队中" click`、`$B wait 2000`、`$B get text ".ys-booking__queue"`。输出 `第 2 位`。截图 `04-我的预约排队第2位.png`。脚本 `status` 显示验收体验排队第 2 位、卡剩余 10 次。
- **第一次补位。** 运行 `node .agents/skills/verify-yoga/scripts/queue.mjs cancel 19900000001`。status 显示 D 已预约、卡 9 次，A 已取消、卡回到 10 次，验收体验排队第 1 位，名额仍是 3/3。
- **轮到验收账号。** 运行 `node .agents/skills/verify-yoga/scripts/queue.mjs cancel 19900000002`，再 `$B reload`、`$B wait 2000`、`$B find text "已预约" click`、`$B wait 2000`、`$B get text ".ys-booking__status"`。输出 `已预约`，status 显示验收体验已预约、卡剩余 9 次。截图 `05-补位后已预约.png`。
- **取消排队（可选）。** 重新 `setup` 并加入排队后，在“排队中”Tab 点 `取消排队` 并确认。status 显示验收体验已取消，名额 3/3 不变，卡仍 10 次。

## Gotchas

- 排课固定在明天 15:00，所以离开课超过 120 分钟。要验 `queue-failed` 需要一节 2 小时内开课的满员课，脚本目前不造，验收时报告未覆盖。
- 一个会员在同一节课上只能有一条预约或排队。重复跑旅程前先 `setup`。
- 发卡接口按“门店 + 当前秒”生成卡号，同一秒连发会 409，脚本已在每次发卡间等 1.1 秒。
- 后端没重新打包时，详情页不会返回 `queueable`，按钮显示“不可预约”。先跑 Doctor 核对 jar 时间。
