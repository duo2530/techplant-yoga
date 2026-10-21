# Proposal

## Why

用户需要从首页直接发现金牌教练，并进入指定教练的“约教练”页面查看资料、相册及排课。目前首页仅展示门店卡，教练 API 返回空结果，后台没有金牌标记，无法提供真实的浏览入口。

## What Changes

- 管理端教练列表增加金牌标识及设置／取消金牌操作，已有教练默认为普通教练。
- 首页门店卡下增加“金牌教练”完整列表，一次全量展示平台全部金牌教练，不截取前三位、不增加“更多教练”入口；卡片显示头像、姓名和简介。
- 点击卡片进入该教练的“约教练”页面，以 `docs/产品分析/用户端小程序截图/P1-首页-06-约教练.png` 为视觉依据，展示头像、姓名、简介、相册和四类课程 Tab 下的排课列表。
- 新增匿名只读接口，返回金牌列表、教练公开详情和指定教练排课；联系电话、审计信息等管理字段不公开。
- 排课沿用当前门店及现有课程详情、预约流程。默认查看当前门店未来 14 天（含今天）的排课，按课种筛选；此日期口径是对截图未说明部分的规划假设。
- 实施时同步需求、接口与教练管理文档，明确替代“一期不设金牌、首页只有门店卡”的旧约束；不恢复已移除的头衔、技能或教练归属门店功能。

## Capabilities

### New Capabilities

- `coach-feature-management`: 管理员设置／取消金牌教练及标记持久化。
- `home-featured-coaches`: 首页全量展示金牌教练并进入对应教练页面。
- `coach-booking-page`: 教练公开资料、相册与当前门店按课种展示的排课浏览。

### Modified Capabilities

无。当前 `openspec/specs/` 没有现存能力规格；上述能力作为新增规格建立。

## Impact

- 后端：`backend/ruoyi-yoga` 的 coach、schedule 模块及公开接口鉴权；`backend/sql/yoga.sql` 和已有数据库的增量升级脚本。
- 管理端：`frontend/pc/src/views/coach/index.vue` 与 `src/api/coach/coach.js`。
- 用户端：`frontend/uni-app/pages/home/index.vue`、`pages/coaches/index.vue`、`api/coach.js`、`pages.json` 及相关样式和图片资源。
- 文档：UC-001、UC-007、用例总览、业务规则、教练管理与用户端接口详细设计及测试映射。
- 验证：真实 MySQL 的 Testcontainers 集成测试，连接隔离后端的 Playwright 端到端测试，以及使用 agent-browser 进行的 H5 移动视口视觉与交互验收。本次界面验收以 H5 为准，不要求微信开发者工具或真机验证。
- 不新增第三方运行依赖，不改造既有排课列表必填参数及预约规则，不包含排课管理或推荐排序系统。
