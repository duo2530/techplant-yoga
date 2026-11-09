# 课程排队

课程满员时，会员可以加入排队并看到自己的名次；有人取消预约后，排在最前面的会员自动转为已预约并扣次。每节课的排队上限与停止排队时间由管理端排课配置（默认 3 人、开课前 110 分钟）。

## Sub-features

- `queue-entry` 满员课程详情页按钮显示“排队”，预约页人数固定 1、按钮显示“加入排队”并提示“当前 X/Y 人排队”。
- `queue-join` 加入排队后弹窗“排队成功”，“查看预约”进入我的预约“全部”Tab，状态显示“排队中 · 第 N 位”，卡不扣次。
- `queue-full` 排队人数达到上限后详情页按钮显示“排队已满”且不可点，接口返回“排队人数已满”。
- `queue-stop` 过了停止排队时间的满员课程不可排队，详情页状态与按钮都显示“已约满”。
- `queue-promote` 有人取消预约后，排第 1 位的会员转为已预约，卡扣 1 次。
- `queue-cancel` 排队中的记录可以“取消排队”，不影响名额和次数。
- `queue-failed` 过了停止排队时间仍在排队的记录在我的预约“全部”Tab 显示为“排队失败”，没有操作按钮。
- `list-state` 约课列表按场次展示状态：可约卡片按钮“立即预约”，满员可排队的卡片状态“排队”、按钮“去排队”，排队已满或已过停止排队时间的卡片状态与按钮“已约满”，已开课的卡片状态与按钮“已结束”；详情页状态标签同口径。
- `queue-config` 管理端新增／修改排课有“排队上限”“停止排队时间”两项，默认 3 与 110，列表显示两列。

## How to get to it (user POV)

- 约课列表或首页点进满员课程的详情页，点底部“排队”。
- 约课列表（`http://localhost:9090/#/pages/booking/index`，当前门店、团课 Tab、按日期筛选）看各场次状态，点“去排队”进入预约页。
- 我的预约 → “全部”Tab 查看排队名次、排队失败，取消排队。状态 Tab 按原型只有全部／已预约／已取消／已签到。
- 管理端预约管理按“排队中”／“排队失败”筛选；排课管理新增排课时填写排队上限与停止排队时间。

## Driving it with agent-browser

Preconditions:

- 运行 `node .agents/skills/verify-yoga/scripts/queue.mjs setup`。它造四节团课，所有验收会员各一张 10 次团课次卡：
  - 主排课：明天 15:00，最大 3 人，排队上限 3，开课前 110 分钟停止排队。A、B、C 已预约，D、E 依次排队，验收账号 `19900000005` 和 F `19900000007` 未排队。输出第一行给出排课 ID，记为 `$S`。
  - 停止排队排课：今天约 3 小时后开课（取整到 5 分钟；跨过午夜时改为明天 01:00），最大 1 人，A 已预约，停止排队时间设为 200 分钟以上，一造好就已过停止排队时间。输出第二行给出排课 ID，记为 `$T`。
  - 排队失败排课：紧接停止排队排课下课后开课（跨过午夜时改为明天 02:00），最大 1 人，排队上限 2。A 已预约，验收账号先按开课前 10 分钟停止排队加入排队，脚本再经管理端下架、把停止排队时间改到已过、重新上架。输出第三行给出排课 ID，记为 `$F`。
  - 已结束排课：先按明天 18:00 新建上架，再直接改库把时间挪到今天 30 分钟前开课（向下取整到 5 分钟，不早于今天 00:00）。这是验收专用数据，管理端不允许排已开始的课。输出第四行给出排课 ID，记为 `$E`。
- 已用 `19900000005` 登录，见 [login.md](login.md)。

- **详情页入口。** 运行 `$B open "http://localhost:9090/#/pages/detail/index?scheduleId=$S"`、`$B wait 2000`、`$B get text ".bar__btn"`。输出 `排队`。截图 `01-详情页满员可排队.png`。
- **预约页。** 运行 `$B click ".bar__btn"`、`$B wait 2000`、`$B get text ".queue-tip"`、`$B get text ".bar__btn"`。URL 是 `#/pages/booking-confirm/index?scheduleId=$S`，提示含 `当前 2/3 人排队`，按钮 `加入排队`。截图 `02-预约页加入排队.png`。
- **加入排队。** 运行 `$B click ".bar__btn"` 和 `$B wait 1500`。出现“排队成功”弹窗和“查看预约”按钮。截图 `03-排队成功弹窗.png`。
- **查看名次。** 运行 `$B find text "查看预约" click`、`$B wait 2000`、`$B get url`、`$B find text "全部" click`、`$B wait 2000`、`$B eval "[...document.querySelectorAll('.ys-booking')].filter(e => e.innerText.includes('哈他瑜伽')).map(e => e.querySelector('.ys-booking__status').innerText)"`。URL 是 `#/pages/my-bookings/index`，输出含 `排队中 · 第 3 位`（另有一条 `排队失败` 来自 `$F`）。截图 `04-我的预约排队第3位.png`。脚本 `status` 显示验收体验排队第 3 位、`排队 3/3`、卡剩余 10 次。
- **排队已满。** 运行 `$B open "http://localhost:9090/#/pages/detail/index?scheduleId=$S"`、`$B wait 2000`、`$B get text ".bar__btn"`。输出 `排队已满`，按钮置灰点了不跳转。截图 `05-详情页排队已满.png`。再运行 `node .agents/skills/verify-yoga/scripts/queue.mjs join 19900000007`，输出 `验收会员F 排队失败：POST /api/reservations/queue -> 409 排队人数已满`。
- **停止排队。** 运行 `$B open "http://localhost:9090/#/pages/detail/index?scheduleId=$T"`、`$B reload`、`$B wait 2000`、`$B get text ".card__status"`、`$B get text ".bar__btn"`。两者都输出 `已约满`，按钮置灰。截图 `06-已过停止排队时间.png`。
- **排队失败。** 运行 `$B open "http://localhost:9090/#/pages/my-bookings/index"`、`$B reload`、`$B wait 2000`、`$B find text "全部" click`、`$B wait 2000`、`$B eval "[...document.querySelectorAll('.ys-booking')].map(e => e.querySelector('.ys-booking__status').innerText + ' / ' + e.querySelectorAll('.ys-booking__cancel').length)"`。输出含 `排队失败 / 0`，即排队失败的记录没有操作按钮。截图 `06b-我的预约排队失败.png`。脚本 `status` 的“排队失败排课”显示验收体验排队第 1 位（库里仍是排队中，按展示口径显示为排队失败），卡剩余 10 次。
- **约课列表状态。** 运行 `$B open "http://localhost:9090/#/pages/booking/index"`、`$B reload`、`$B wait 2000`。列表默认当前门店、团课 Tab、今天；门店不是 `1856739201475235901`（徐汇店）时先在首页换店。运行 `$B eval "[...document.querySelectorAll('.ccard')].map(e => e.querySelector('.ccard__meta').innerText + ' | ' + e.querySelector('.ccard__status').innerText + ' | ' + e.querySelector('.ccard__btn').innerText)"`。按开始时间对照 setup 输出：`$E` 的卡片 `已结束 | 已结束`，`$T` 与 `$F` 的卡片 `已约满 | 已约满`，整卡置灰。截图 `06c-约课列表今天.png`。再点日期条第 2 天（明天），重复 `eval`：主排课 `$S`（15:00）是 `已约满 | 已约满`（此时排队 3/3）。截图 `06d-约课列表明天.png`。
- **去排队。** 运行 `node .agents/skills/verify-yoga/scripts/queue.mjs cancel 19900000004`（D 取消排队，排队变 2/3），然后 `$B reload`、`$B wait 2000`，点日期条第 2 天，再跑同一条 `eval`。主排课卡片是 `排队 | 去排队`，状态标签橙色。点这张卡片的 `.ccard__btn`，`$B wait 2000`、`$B get url`。URL 是 `#/pages/booking-confirm/index?scheduleId=$S`，按钮 `加入排队`。截图 `06e-约课列表去排队.png`。这一步改变了排队人数，后面的补位步骤先重新 `setup` 并让验收账号重新加入排队。
- **第一次补位。** 先 `setup` 并按“加入排队”让验收账号排第 3 位，再运行 `node .agents/skills/verify-yoga/scripts/queue.mjs cancel 19900000001`。status 显示 D 已预约、卡 9 次，A 已取消、卡回到 10 次，E 排队第 1 位，验收体验排队第 2 位，名额仍是 3/3。
- **轮到验收账号。** 依次运行 `cancel 19900000002`、`cancel 19900000003`，再 `$B open "http://localhost:9090/#/pages/my-bookings/index"`、`$B reload`、`$B wait 2000`、`$B find text "全部" click`、`$B wait 2000`，跑“查看名次”的 `eval`。主排课那条输出 `已预约`，status 显示 E 与验收体验已预约、各剩 9 次。截图 `07-补位后已预约.png`。
- **管理端配置。** 管理端排课管理点“新增排课”，表单有“排队上限”默认 3、“停止排队时间”默认 110；列表“排队上限”“停止排队(开课前分钟)”两列显示主排课的 3 与 110。截图 `08-管理端排队配置.png`。
- **取消排队（可选）。** 重新 `setup` 并加入排队后，在“全部”Tab 找到状态为 `排队中 · 第 3 位` 的卡片，点它的 `取消排队` 并确认。status 显示验收体验已取消，名额 3/3 不变，卡仍 10 次。

## Gotchas

- 开发库需要先执行 `backend/sql/migrations/20261109-schedule-queue-config.sql` 补 `queue_limit`、`queue_stop_minutes` 两列，否则新增排课与预约查询都会报 SQL 列不存在。
- 停止排队排课、排队失败排课在 setup 时按当前时间计算，隔几个小时再验收也仍处于“已过停止排队时间”，但开课后会变成“已结束”。已结束排课的时间是 setup 时刻的今天，隔天会从今天的列表消失。隔天验收先重新 `setup`。
- 用户端“已结束”按开始时间判定：开课即显示已结束；管理端仍按结束时间判定，开课后未下课的排课在管理端是“已上架”。
- 排队失败排课靠管理端“下架 → 改停止排队时间 → 上架”跨过停止排队时间。下架不检查已有预约与排队，这是管理端现有行为，不是本功能引入的。
- 已结束排课是直接改库造的：管理端不允许排已开始的课。不要用它验证管理端编辑或删除。
- 我的预约的状态 Tab 按原型只有全部／已预约／已取消／已签到，排队中与排队失败只在“全部”里。卡类型下拉的文字“全部卡类型”也含“全部”，`find text "全部" click` 没切到“全部”Tab 时改用 `$B click ".status__item"`（第一个 Tab 就是“全部”）。
- 一个会员在同一节课上只能有一条预约或排队。重复跑旅程前先 `setup`。
- 发卡接口按“门店 + 当前秒”生成卡号，同一秒连发会 409，脚本已在每次发卡间等 1.1 秒。
- 后端没重新打包时，详情页不会返回 `queueLimit`，预约页会显示 `当前 2/0 人排队`，按钮也不会显示“排队已满”。先跑 Doctor 核对 jar 时间。
- 在同一会话里用 `$B open` 换到另一个排课的详情页时，前一个页面仍留在 DOM 里，`get text ".bar__btn"` 可能读到旧页面的按钮。换排课后先 `$B reload`。约课列表与我的预约是 Tab 页，同样先 `$B reload` 再读。
