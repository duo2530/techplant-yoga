# 课程排队

课程满员时，会员可以加入排队并看到自己的名次；有人取消预约后，排在最前面的会员自动转为已预约并扣次。每节课的排队上限与停止排队时间由管理端排课配置（默认 3 人、开课前 110 分钟）。

## Sub-features

- `queue-entry` 满员课程详情页按钮显示“排队”，预约页人数固定 1、按钮显示“加入排队”并提示“当前 X/Y 人排队”。
- `queue-join` 加入排队后弹窗“排队成功”，我的预约“排队中”Tab 显示名次，卡不扣次。
- `queue-full` 排队人数达到上限后详情页按钮显示“排队已满”且不可点，接口返回“排队人数已满”。
- `queue-stop` 过了停止排队时间的满员课程不可排队，详情页按钮显示“不可预约”。
- `queue-promote` 有人取消预约后，排第 1 位的会员转为已预约，卡扣 1 次。
- `queue-cancel` 排队中的记录可以“取消排队”，不影响名额和次数。
- `queue-failed` 过了停止排队时间仍在排队的记录显示为“排队失败”。
- `queue-config` 管理端新增／修改排课有“排队上限”“停止排队时间”两项，默认 3 与 110，列表显示两列。

## How to get to it (user POV)

- 约课列表或首页点进满员课程的详情页，点底部“排队”。
- 我的预约 → “排队中”Tab 查看名次、取消排队。
- 管理端预约管理按“排队中”／“排队失败”筛选；排课管理新增排课时填写排队上限与停止排队时间。

## Driving it with agent-browser

Preconditions:

- 运行 `node .agents/skills/verify-yoga/scripts/queue.mjs setup`。它造两节团课，所有验收会员各一张 10 次团课次卡：
  - 主排课：明天 15:00，最大 3 人，排队上限 3，开课前 110 分钟停止排队。A、B、C 已预约，D、E 依次排队，验收账号 `19900000005` 和 F `19900000007` 未排队。输出第一行给出排课 ID，记为 `$S`。
  - 停止排队排课：今天约 3 小时后开课（取整到 5 分钟；跨过午夜时改为明天 01:00），最大 1 人，A 已预约，停止排队时间设为 200 分钟以上，一造好就已过停止排队时间。输出第二行给出排课 ID，记为 `$T`。
- 已用 `19900000005` 登录，见 [login.md](login.md)。

- **详情页入口。** 运行 `$B open "http://localhost:9090/#/pages/detail/index?scheduleId=$S"`、`$B wait 2000`、`$B get text ".bar__btn"`。输出 `排队`。截图 `01-详情页满员可排队.png`。
- **预约页。** 运行 `$B click ".bar__btn"`、`$B wait 2000`、`$B get text ".queue-tip"`、`$B get text ".bar__btn"`。URL 是 `#/pages/booking-confirm/index?scheduleId=$S`，提示含 `当前 2/3 人排队`，按钮 `加入排队`。截图 `02-预约页加入排队.png`。
- **加入排队。** 运行 `$B click ".bar__btn"` 和 `$B wait 1500`。出现“排队成功”弹窗和“查看预约”按钮。截图 `03-排队成功弹窗.png`。
- **查看名次。** 运行 `$B find text "查看预约" click`、`$B wait 2000`、`$B find text "排队中" click`、`$B wait 2000`、`$B get text ".ys-booking__queue"`。输出 `第 3 位`。截图 `04-我的预约排队第3位.png`。脚本 `status` 显示验收体验排队第 3 位、`排队 3/3`、卡剩余 10 次。
- **排队已满。** 运行 `$B open "http://localhost:9090/#/pages/detail/index?scheduleId=$S"`、`$B wait 2000`、`$B get text ".bar__btn"`。输出 `排队已满`，按钮置灰点了不跳转。截图 `05-详情页排队已满.png`。再运行 `node .agents/skills/verify-yoga/scripts/queue.mjs join 19900000007`，输出 `验收会员F 排队失败：POST /api/reservations/queue -> 409 排队人数已满`。
- **停止排队。** 运行 `$B open "http://localhost:9090/#/pages/detail/index?scheduleId=$T"`、`$B reload`、`$B wait 2000`、`$B get text ".bar__btn"`。输出 `不可预约`，按钮置灰。截图 `06-已过停止排队时间.png`。
- **第一次补位。** 运行 `node .agents/skills/verify-yoga/scripts/queue.mjs cancel 19900000001`。status 显示 D 已预约、卡 9 次，A 已取消、卡回到 10 次，E 排队第 1 位，验收体验排队第 2 位，名额仍是 3/3。
- **轮到验收账号。** 依次运行 `cancel 19900000002`、`cancel 19900000003`，再 `$B open "http://localhost:9090/#/pages/my-bookings/index"`、`$B wait 2000`、`$B find text "已预约" click`、`$B wait 2000`、`$B get text ".ys-booking__status"`。输出含 `已预约`，status 显示 E 与验收体验已预约、各剩 9 次。截图 `07-补位后已预约.png`。
- **管理端配置。** 管理端排课管理点“新增排课”，表单有“排队上限”默认 3、“停止排队时间”默认 110；列表“排队上限”“停止排队(开课前分钟)”两列显示主排课的 3 与 110。截图 `08-管理端排队配置.png`。
- **取消排队（可选）。** 重新 `setup` 并加入排队后，在“排队中”Tab 点 `取消排队` 并确认。status 显示验收体验已取消，名额 3/3 不变，卡仍 10 次。

## Gotchas

- 开发库需要先执行 `backend/sql/migrations/20261109-schedule-queue-config.sql` 补 `queue_limit`、`queue_stop_minutes` 两列，否则新增排课与预约查询都会报 SQL 列不存在。
- 停止排队排课在 setup 时按当前时间计算，隔几个小时再验收也仍处于“已过停止排队时间”，但开课后它会变成“已结束”。隔天验收先重新 `setup`。
- `queue-failed` 需要排队中的记录跨过停止排队时间。主排课在明天，脚本没有造这种记录，验收时报告未覆盖，由后端集成测试 `cancel_afterQueueStop_shouldSkipPromotion` 覆盖。
- 一个会员在同一节课上只能有一条预约或排队。重复跑旅程前先 `setup`。
- 发卡接口按“门店 + 当前秒”生成卡号，同一秒连发会 409，脚本已在每次发卡间等 1.1 秒。
- 后端没重新打包时，详情页不会返回 `queueLimit`，预约页会显示 `当前 2/0 人排队`，按钮也不会显示“排队已满”。先跑 Doctor 核对 jar 时间。
- 在同一会话里用 `$B open` 换到另一个排课的详情页时，前一个页面仍留在 DOM 里，`get text ".bar__btn"` 可能读到旧页面的按钮。换排课后先 `$B reload`。
