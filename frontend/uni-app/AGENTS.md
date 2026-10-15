# AGENTS.md — frontend/uni-app（用户端 / 小程序）

> 作用域：`frontend/uni-app/` 及其所有子目录。

## 1. 这是什么

微信小程序用户端，基于 Vue 3 + uni-app 和 RuoYi-App 模板，支持 npm CLI 与 HBuilderX 编译为 H5、微信小程序。账号与会员卡使用真实后端，其余业务域使用 mock；不开发教练端。

## 2. 运行方式

- Node.js >= 20.19，在本目录执行 `npm ci` 安装锁定依赖。
- `npm run dev:h5` 启动 H5，默认端口 9090；`npm run dev:mp-weixin` 持续编译微信小程序。
- `npm run build:h5`、`npm run build:mp-weixin` 分别生成 `dist/build/h5`、`dist/build/mp-weixin`。
- 微信开发者工具导入 `dist/dev/mp-weixin` 调试，发布时导入 `dist/build/mp-weixin`。
- `package.json` 中的 DCloud 包保持同一版本，依赖变更同步更新 `package-lock.json`。
- HBuilderX 仍可打开工程；命令行编译不依赖本机 HBuilderX 插件路径。操作说明见 [README.md](README.md)。

## 3. 技术栈（已核对文件）

| 项 | 说明 |
|---|---|
| 框架 | uni-app，**`vueVersion: "3"`**（`manifest.json`） |
| 入口 | `main.js` 用 `createSSRApp(App)` 导出 `createApp()`；H5 入口 `index.html` |
| 状态 | **Pinia**（`store/index.js` 导出 `useUserStore`/`useConfigStore`，另有 `store/modules/dict.js`） |
| 请求 | `uni.request` 封装在 `utils/request.js`，地址取自 `config.js` |
| 路由守卫 | `permission.js`（拦截 `navigateTo/redirectTo/reLaunch/switchTab`） |
| UI 组件 | `uni_modules/**`（插件市场引入，含 `uni-icons`/`uni-grid`/`uni-section`/`uni-popup` 等） |
| 目标端 | `mp-weixin`（appid `touristappid`，`urlCheck: false`）；H5 devServer 端口 9090 |

## 4. 目录与职责

| 路径 | 职责 |
|---|---|
| `pages.json` | **页面注册表**：`pages` 数组（第一项是启动页 `pages/home/index` 首页）、`tabBar`（首页/约课/已约/我的，文字色两态都固定 `#717579`，选中只换图标）、`globalStyle` |
| `manifest.json` | 应用名/appid/微信 appid/`vueVersion`/各端配置（改动这里后要重新编译） |
| `config.js` | 全局配置：`baseUrl`（真实接口地址）、`appInfo`（名称、logo、协议链接） |
| `main.js` / `App.vue` | 应用入口；`onLaunch` 初始化配置，不做登录检查 |
| `permission.js` | 页面导航拦截器，当前放行所有跳转 |
| `pages/**` | 用户端页面，登记在 `pages.json`；业务页面清单见第 6 节 |
| `mock/**` | 尚未接入后端的业务数据与 `MOCK.delay`、`MOCK.empty.*` 配置 |
| `api/**` | 页面数据访问层，按业务域封装真实请求或 mock；`api/login.js`、`api/system/**` 为未使用的脚手架接口 |
| `components/ys-*/` | 自写业务组件（easycom 自动注册）：`ys-nav-brand`、`ys-section-head`、`ys-notice-bar`、`ys-empty`、`ys-course-card`、`ys-booking-card`、`ys-member-card` |
| `store/modules/**` | `user`（会员会话）、`config`（应用配置）、`dict`（字典缓存） |
| `utils/**` | `request`、`session`（**本地登录态唯一来源**：`isSessionValid` / `saveSession` / `clearSessionStorage` / `LOGIN_PAGE`）、`auth`（token 与会员展示信息存取）、`dict`（`useDict`）、`storage`、`permission`、`upload`、`validate`、`common`（`toast`/`showConfirm`/`tansParams`）、`constant`、`errorCode` |
| `plugins/**` | `proxy.$tab`、`proxy.$auth`、`proxy.$modal` |
| `static/**` | 静态资源（`images/` 业务资产 + 清单 `images/README.md`、字体、`scss/index.scss`、`uni.scss` 设计 token、`static/index.html` H5 模板） |
| `uni_modules/**` | 第三方插件代码，**不要手改** |
| `scripts/check-static.js` | 本工程静态自检脚本（使用本地 Vue 编译器和 Sass，见测试手册） |
| `scripts/check-request.js` | 请求与会话逻辑的本地自检，运行方式见测试手册 |
| `tests/e2e/**` | 用户端 H5 端到端测试；运行说明见测试手册 |

## 5. 代码约定

- **必须显式 import**：没有自动导入，`ref`/`computed` 要从 `vue` 引入，页面生命周期（`onLoad`/`onShow`/`onLaunch`/`onPullDownRefresh`）要从 `@dcloudio/uni-app` 引入。参考 `pages/booking/index.vue`、`App.vue`。
- **组件 easycom**：`components/组件名/组件名.vue` 与 `uni_modules/**` 自动注册，模板里直接 `<uni-icons type="person-filled" />`、`<ys-empty />`，不需要 import。
- **页面必须登记**：新建页面要在 `pages.json` 的 `pages` 数组里加一项（否则跳转失败）；新增 tabBar 页要同时改 `pages.json` 的 `tabBar.list` 并提供 `static/images/tabbar/*.png` 图标（未选中/选中两张）。
- **页面数据只走 `@/api/*`**：页面不直接调用 `uni.request` 或导入 `@/utils/request`、`@/api/login`、`@/api/system/**`。真实请求由业务 API 通过 `utils/request.js` 发起。
- **状态**：`const userStore = useUserStore()`（`@/store` 或 `@/store/modules/user`）；**会员登录态只用 `@/utils/session` 判断**（`isSessionValid()` / `LOGIN_PAGE`），不要直接 `getToken()` 判空 —— 后端 7 天到期不自动续期，只有带 `expireTime` 的会话才算已登录；登录写入用 `userStore.applySession()`，清理统一用 `userStore.clearSession()`（401、会话过期、退出登录共用）。
- **样式**：尺寸一律用 `rpx`（原型逻辑画布 390pt → 750rpx，`rpx = pt × 1.9231`），只有 1px 边框写 `px`；颜色用 `uni.scss` 末尾的设计 token（`$ys-*`，实测值）；全局类在 `static/scss/yoga.scss`（`.ys-page`/`.ys-card`/`.ys-pad`/`.press` 等），在 `App.vue` 里随 `index.scss` 一起引入。
- **静态资源路径**：`/static/images/xxx`（页面里统一用绝对路径；资产清单见 `static/images/README.md`）。
- **外壳不要自己画**：状态栏、导航栏、微信胶囊、tabBar 都由系统绘制（唯一例外是首页 `navigationStyle:custom` + `<ys-nav-brand>`）。
- 中文文案与注释遵循根目录 [AGENTS.md](../../AGENTS.md) 的源码注释约定；原型测量、还原过程及差异说明集中维护在设计文档中。

## 6. 页面与数据状态

原型依据为 `docs/需求文档/用户端/高保真原型/*.html`；设置页另见 `docs/产品分析/用户端小程序截图/P4-我的-02-设置.png`，会员卡页另见同目录的 `P4-我的-04-会员卡-*.jpg`。

**页面**（全部登记在 `pages.json`，路径即文件路径）：

| 类型 | 页面 |
|---|---|
| tabBar 一级页（4） | `pages/home/index` 首页（`navigationStyle:custom`，用 `<ys-nav-brand>`）、`pages/booking/index` 约课、`pages/my-bookings/index` 我的预约、`pages/profile/index` 我的 |
| 二级页（15） | `settings` 设置（入口：「我的」页点头像——**已登录**进设置页、**未登录**进登录页；设置页含退出登录）、`trial-apply` 申请体验、`my-trials` 我的体验课、`coaches` 约教练、`activities` 活动列表、`messages` 消息、`member-cards` 我的会员卡（入口：「我的」页四宫格第一项；未登录先去登录页）、`booking-confirm` 立即预约（入口：约课卡与课程详情页的「立即预约」；未登录先去登录页）、`booking-success` 预约成功（由立即预约页 `redirectTo` 进入，两个出口：查看预约 → 我的预约、返回首页）、`share-poster` 分享海报、`terms` 用户协议、`privacy` 隐私政策、`auth/quick-login` 快速登录、`auth/phone-auth` 手机号登录/注册、`auth/forgot-password` 忘记密码 |

**数据来源**：

- `api/auth.js` 调用 `/api/auth/**`；`api/member.js` 的 `listMemberCards` 调用 `GET /api/member-cards`；`api/schedule.js` 调用 `/api/schedules**`；`api/booking.js` 调用 `/api/reservations**`（我的预约、取消、签到与提交预约）。接口契约见[用户端接口详细设计](../../docs/详细设计/用户端接口详细设计.md)。
- 会员显示名与手机号来自本地有效会话，预约与排课走真实接口，其余会员资料及门店、课程等业务数据使用 `mock/`；`mock/auth.js` 保存协议正文。
- `mock/config.js` 里的开关：
  - `MOCK.delay` 模拟网络延迟；
  - `MOCK.empty.*` **空态开关**（`homeTodayCourses / homeHotCourses / homeNotices / trials / activities / messages`）—— 默认 `true`，即**按原型显示空态**（原型截图里这些位置就是空态）；改成 `false` 可看到 mock 数据的列表视图。
- 会员卡等需要身份的页面自行校验会员会话，真实接口由后端鉴权。

**外壳口径**：原型是「整机复刻」（含 iOS 状态栏、导航栏、微信胶囊、TabBar），小程序里这些**由系统绘制**，所以页面只实现内容区；首页因为要还原「书法一 Logo + 品牌字左对齐」，用了 `navigationStyle:custom` + `<ys-nav-brand>`。

**图片资产**：`static/images/`（45 个文件 / 约 0.65MB），全部从 `docs/产品分析/用户端小程序截图/` 裁出（照片转 JPEG、图标与插画留 PNG，空态插画由原型 SVG 栅格化）。清单与来源坐标见 `static/images/README.md`；重跑用 `docs/需求文档/用户端/高保真原型/_工具/资产抽取/` 下的脚本。

**已知缺口 / 偏差**（详见 `static/images/README.md` 与各页面注释）：

- 课程详情页、优惠券、礼包、积分、合同、体测等子页面暂未实现；「我的」页对应菜单项提示“该功能页面暂未提供”。
- 会员卡截图中的次数卡有两种配色，页面统一使用蓝色渐变，期限卡使用米金渐变。
- 门店定位不跳地图（mock 没有经纬度）、约课页品类 Tab 与日期条的选中态、部分图标用 `uni-icons` 近似原型线稿（原型是内联 SVG，小程序不支持）。
- 几个页面在原型里是**空态**，有数据时的卡片样式是按设计 token 推导的（已在组件与页面注释里标明）。

## 7. 小程序联调 / 发布注意

- 开发阶段在微信开发者工具里勾选"**不校验合法域名**"（`manifest.json` 里 `setting.urlCheck` 已是 `false`）。
- 正式发布必须是**已备案的 https 域名**，并在微信公众平台配置 `request` / `uploadFile` / `downloadFile` 合法域名；`localhost` 只能本机调试。
- 改 `pages.json` / `manifest.json` 后需要重新编译，个别情况要重启开发者工具。
- 登录态失效由 `utils/request.js` 统一处理，别在页面里各自实现。
- **联调账号（backend/sql/yoga.sql 的种子会员）**：手机号 `13800138000` / 密码 `yoga1234`；
  验证码在开发期由后端配置 `sms.code.fixedCode` 固定为 `888888`（上线前置空走随机码）。
  后端需先导入 `backend/sql/yoga.sql`（含 `t_member` 建表与这条种子会员）。

## 8. 不要做的事

- npm 依赖需兼容 H5 和微信小程序，不引入只支持浏览器 DOM 的业务组件。
- 不要手改 `uni_modules/**`（升级插件会覆盖）。
- 不要把业务逻辑写进 `App.vue` / `permission.js`；它们只做初始化和登录守卫。
- 不要绕过 `utils/request.js` 直接用 `uni.request`。
- 不要用 PC 端的写法（`@/utils/request` 返回结构、Element Plus 组件、`npm run dev`）套到本工程。

## 9. 测试入口

编写和验证测试前，阅读并遵循 [AI Agent测试手册](../../docs/系统测试/AI%20Agent测试手册.md)。
