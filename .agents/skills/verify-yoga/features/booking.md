# 预约课程

有空位的课程，会员在详情页进入预约页，选会员卡和人数后提交，次数卡按人数扣次；开课前 120 分钟以前可以取消并返次。

## Sub-features

- `booking-create` 有空位时提交预约，跳到预约成功页，名额和卡次数变化。
- `booking-cancel` 我的预约“已预约”Tab 点“取消预约”，名额释放、次数返还。

## How to get to it (user POV)

- 首页今日可约、约课列表或热门课程点进详情页，点底部按钮进入预约页。
- 我的预约 → “已预约”Tab → “取消预约”。

## Driving it with agent-browser

Preconditions:

- 运行 `node .agents/skills/verify-yoga/scripts/queue.mjs setup` 后，再用 A 取消一次：`node .agents/skills/verify-yoga/scripts/queue.mjs cancel 19900000001` 会让 D 补位、名额仍满。要有空位，先让 D 也取消：`node .agents/skills/verify-yoga/scripts/queue.mjs cancel 19900000004`。status 显示 2/3。
- 已用 `19900000005` 登录。

- **详情页入口。** 运行 `$B open "http://localhost:9090/#/pages/detail/index?scheduleId=$S"`、`$B wait 2000`、`$B get text ".bar__btn"`。按钮是可预约文案，不是 `排队`。
- **提交预约。** 运行 `$B click ".bar__btn"`、`$B wait 2000`、`$B click ".bar__btn"`、`$B wait 2000`、`$B get url`。URL 进入 `#/pages/booking-success/index`。status 显示 3/3、验收体验已预约、卡 9 次。
- **取消预约。** 我的预约 → `$B find text "已预约" click` → `$B find text "取消预约" click` → 确认。status 显示 2/3、卡回到 10 次。

## Gotchas

- 这条旅程还没有按本文件实际跑过，第一次使用时把真实按钮文案和确认弹窗按钮补进来。
- 本功能目前借用排队脚本造数，排队以外的预约场景多了以后再单独写 `scripts/booking.mjs`。
