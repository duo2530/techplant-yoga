# AGENTS.md — frontend/uni-app（用户端 / 小程序）

> 作用域：`frontend/uni-app/` 及其所有子目录。业务口径见 `docs/需求分析/需求分析.md`、`docs/详细设计/详细设计.md`。

## 1. 这是什么

面向 **微信小程序**（用户端；教练端是否并入本工程尚未定论）的 uni-app 工程，基于 **RuoYi-App 模板**二次开发。目前**已按 `docs/原型/` 的高保真原型实现用户端 15 个页面**（4 个 tabBar 一级页 + 11 个二级页 + 3 个登录相关页，见第 6 节），数据**全部来自本地 mock，不调用后端接口**。若依自带的登录/注册/首页/工作台/我的等演示页面**已删除**。

**这是 Vue 3 + uni-app，且是 HBuilderX 工程**——两个最容易踩的前提，见下节。

## 2. 运行方式（**没有 package.json，不能用 npm**）

- 工程里**没有 `package.json`**，`npm install` / `npm run dev:mp-weixin` 这类 CLI 流程**不适用**（工程里的 `unpackage/`、`dist/` 只是构建缓存/产物，已在 `.gitignore` 里忽略）。只能：
  1. 用 **HBuilderX** 打开 `frontend/uni-app` 目录（本机：`D:\Develop\HBuilderX`）；
  2. 运行 → 运行到小程序模拟器 → 微信开发者工具（本机：`D:\Develop\miniprogram_ide`）；或运行到浏览器（H5）。
- 因此**不要在本目录引入 npm 依赖**（HBuilderX 直接编译，不会装包）；需要新组件优先用 `uni_modules` 插件市场或 `components/` 自写组件。

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
| `config.js` | 全局配置：`baseUrl`（后端地址，mock 阶段用不到）、`appInfo`（名称、logo、协议链接） |
| `main.js` / `App.vue` | 应用入口；`App.vue` 里 `onLaunch` 只初始化配置，**不做登录检查**（mock 阶段无登录态，见第 6 节） |
| `permission.js` | 页面跳转拦截 + 免登录白名单；当前 `MOCK_MODE = true`，**不拦截** |
| `pages/**` | 页面：`home`、`booking`、`my-bookings`、`profile`（4 个一级页）+ `trial-apply`、`my-trials`、`coaches`、`activities`、`messages`、`share-poster`、`terms`、`privacy`、`auth/*` + `common/webview`、`common/textview` |
| `mock/**` | **唯一数据源**：`config.js`（`MOCK.delay` / `MOCK.empty.*` 空态开关 / `MOCK.loggedIn`）+ 各业务域的 mock 数组 |
| `api/**` | **假接口**（业务域同名文件，返回 Promise、内部读 `mock/`）。页面只依赖这一层，接真实后端时只改这里。另存的 `api/login.js`、`api/system/**` 是脚手架遗留、当前无人调用 |
| `components/ys-*/` | 自写业务组件（easycom 自动注册）：`ys-nav-brand`、`ys-section-head`、`ys-notice-bar`、`ys-empty`、`ys-course-card`、`ys-booking-card` |
| `store/modules/**` | `user`（token/角色/权限）、`config`、`dict`（mock 阶段只有 `config` 在用） |
| `utils/**` | `request`、`auth`（token 存取）、`dict`（`useDict`）、`storage`、`permission`、`upload`、`validate`、`common`（`toast`/`showConfirm`/`tansParams`）、`constant`、`errorCode` |
| `plugins/**` | `proxy.$tab`、`proxy.$auth`、`proxy.$modal` |
| `static/**` | 静态资源（`images/` 业务资产 + 清单 `images/README.md`、字体、`scss/index.scss`、`uni.scss` 设计 token、`static/index.html` H5 模板） |
| `uni_modules/**` | 第三方插件代码，**不要手改** |
| `scripts/check-static.js` | 本工程静态自检脚本（无 npm 依赖，复用 HBuilderX 自带编译器，见第 9 节第 3 步） |

## 5. 代码约定

- **必须显式 import**：没有自动导入，`ref`/`computed` 要从 `vue` 引入，页面生命周期（`onLoad`/`onShow`/`onLaunch`/`onPullDownRefresh`）要从 `@dcloudio/uni-app` 引入。参考 `pages/booking/index.vue`、`App.vue`。
- **组件 easycom**：`components/组件名/组件名.vue` 与 `uni_modules/**` 自动注册，模板里直接 `<uni-icons type="person-filled" />`、`<ys-empty />`，不需要 import。
- **页面必须登记**：新建页面要在 `pages.json` 的 `pages` 数组里加一项（否则跳转失败）；新增 tabBar 页要同时改 `pages.json` 的 `tabBar.list` 并提供 `static/images/tabbar/*.png` 图标（未选中/选中两张）。
- **数据只走 `@/api/*`（当前是 mock 假接口）**：页面里**不要** `uni.request`、不要 import `@/utils/request`、不要 import `@/api/login` 或 `@/api/system/**`。接真实后端时改 `api/*` 一层即可（那时才恢复走 `@/utils/request` 与 `config.js` 的 `baseUrl`）。
- **状态**：`const userStore = useUserStore()`（`@/store` 或 `@/store/modules/user`），token 通过 `utils/auth` 读写（mock 阶段未使用）。
- **样式**：尺寸一律用 `rpx`（原型逻辑画布 390pt → 750rpx，`rpx = pt × 1.9231`），只有 1px 边框写 `px`；颜色用 `uni.scss` 末尾的设计 token（`$ys-*`，实测值）；全局类在 `static/scss/yoga.scss`（`.ys-page`/`.ys-card`/`.ys-pad`/`.press` 等），在 `App.vue` 里随 `index.scss` 一起引入。
- **静态资源路径**：`/static/images/xxx`（页面里统一用绝对路径；资产清单见 `static/images/README.md`）。
- **外壳不要自己画**：状态栏、导航栏、微信胶囊、tabBar 都由系统绘制（唯一例外是首页 `navigationStyle:custom` + `<ys-nav-brand>`）。
- 中文文案与注释，和现有代码保持一致；照原型复刻时把实测值写进注释（既有代码都是这个风格）。

## 6. 当前状态（原型还原 + mock 数据）

**页面**（全部登记在 `pages.json`，路径即文件路径）：

| 类型 | 页面 |
|---|---|
| tabBar 一级页（4） | `pages/home/index` 首页（`navigationStyle:custom`，用 `<ys-nav-brand>`）、`pages/booking/index` 约课、`pages/my-bookings/index` 我的预约、`pages/profile/index` 我的 |
| 二级页（11） | `trial-apply` 申请体验、`my-trials` 我的体验课、`coaches` 约教练、`activities` 活动列表、`messages` 消息、`share-poster` 分享海报、`terms` 用户协议、`privacy` 隐私政策、`auth/quick-login` 快速登录、`auth/phone-auth` 手机号登录/注册、`auth/forgot-password` 忘记密码 |

**数据层（不碰后端）**：

- `mock/` 是**唯一数据源**：`config.js`（开关）、`store/course/coach/booking/trial/activity/message/user/auth.js`。
- `api/` 是同名域的**假接口**，全部返回 Promise、内部读 `mock/`。**页面只依赖 `@/api/*`**；将来接真实后端就改这一层，页面不用动。
- `mock/config.js` 里的开关：
  - `MOCK.delay` 模拟网络延迟；
  - `MOCK.empty.*` **空态开关**（`homeTodayCourses / homeHotCourses / homeNotices / bookings / trials / activities / messages`）—— 默认 `true`，即**按原型显示空态**（原型截图里这些位置就是空态）；改成 `false` 可看到 mock 数据的列表视图；
  - `MOCK.loggedIn` 控制「我的」页显示 mock 用户还是原型的游客态。
- **`permission.js` 目前不做登录拦截**（`MOCK_MODE = true`），任何页面都能直接打开，方便逐页对照原型验收；接真实后端时改回 `false`。

**外壳口径**：原型是「整机复刻」（含 iOS 状态栏、导航栏、微信胶囊、TabBar），小程序里这些**由系统绘制**，所以页面只实现内容区；首页因为要还原「书法一 Logo + 品牌字左对齐」，用了 `navigationStyle:custom` + `<ys-nav-brand>`。

**图片资产**：`static/images/`（45 个文件 / 约 0.65MB），全部从 `docs/需求文档/用户端/用户端小程序截图/` 裁出（照片转 JPEG、图标与插画留 PNG，空态插画由原型 SVG 栅格化）。清单与来源坐标见 `static/images/README.md`；重跑用 `docs/原型/_工具/资产抽取/` 下的脚本。

**已知缺口 / 偏差**（详见 `static/images/README.md` 与各页面注释）：

- 课程详情页、会员卡/优惠券/礼包/积分/合同/体测等子页面**原型里没有**，本轮未建；「我的」页对应菜单项点击只 toast「该功能页面暂未提供」。
- 门店定位不跳地图（mock 没有经纬度）、约课页品类 Tab 与日期条的选中态、部分图标用 `uni-icons` 近似原型线稿（原型是内联 SVG，小程序不支持）。
- 几个页面在原型里是**空态**，有数据时的卡片样式是按设计 token 推导的（已在组件与页面注释里标明）。

**品牌化已完成**：`config.js` 的 `appInfo`、`manifest.json` 的 `name`/`description`/H5 `title`、`index.html` 的 `<title>`、`pages.json` 的 `globalStyle` 与各页标题都已是「一水·瑜伽普拉提」；`baseUrl` 仍留在 `config.js`（当前 mock 阶段用不到，接后端时用）。

> 注意：`baseUrl` 将来改成 `localhost` 只对本机 H5/开发者工具有效；真机预览和发布都要换成可访问的 https 域名（见第 7 节）。

## 7. 小程序联调 / 发布注意

- 开发阶段在微信开发者工具里勾选"**不校验合法域名**"（`manifest.json` 里 `setting.urlCheck` 已是 `false`）。
- 正式发布必须是**已备案的 https 域名**，并在微信公众平台配置 `request` / `uploadFile` / `downloadFile` 合法域名；`localhost` 只能本机调试。
- 改 `pages.json` / `manifest.json` 后需要重新编译，个别情况要重启开发者工具。
- 登录态失效由 `utils/request.js` 统一处理，别在页面里各自实现。

## 8. 不要做的事

- 不要引入 npm 依赖或试图把工程改成 CLI 工程（除非明确要求，那属于脚手架改造）。
- 不要手改 `uni_modules/**`（升级插件会覆盖）。
- 不要把业务逻辑写进 `App.vue` / `permission.js`；它们只做初始化和登录守卫。
- 不要绕过 `utils/request.js` 直接用 `uni.request`。
- 不要用 PC 端的写法（`@/utils/request` 返回结构、Element Plus 组件、`npm run dev`）套到本工程。

## 9. 改完怎么验证

**当前阶段（mock、无后端）没有自动化构建**，按下面三层做：

1. **HBuilderX 编译（最低要求）**：HBuilderX 打开 `frontend/uni-app` → 运行到微信开发者工具（或运行到浏览器 H5），**编译无报错**。`pages.json` / `manifest.json` 改过要重新编译。
2. **命令行真实编译（不用开 HBuilderX GUI，已验证可用）**：HBuilderX 自带 uni-app（Vue3/vite）编译器，可直接调用：
   ```powershell
   # ① 临时把编译器缺的 sass / pinia 放进工程的 node_modules（HBuilderX 运行时自己会注入，命令行需要手动提供）
   #    来源：D:\Develop\HBuilderX\plugins\compile-dart-sass\node_modules（sass 及其依赖）
   #         D:\Develop\HBuilderX\plugins\uniapp-cli-vite\node_modules（pinia、vue-demi）
   #    这一步只是本机验证用，跑完删掉 node_modules 即可（已在 .gitignore 里忽略）
   # ② 跑编译（platform 换成 h5 / mp-weixin）
   $env:UNI_INPUT_DIR='<工程绝对路径>'; $env:UNI_PLATFORM='mp-weixin'; $env:NODE_ENV='production'
   $env:UNI_HBUILDERX_PLUGINS='D:\Develop\HBuilderX\plugins'
   cd 'D:\Develop\HBuilderX\plugins\uniapp-cli-vite'
   node node_modules\vite\bin\vite.js build --config vite.config.js
   ```
   产物默认落在工程的 `dist/`（已在 .gitignore 忽略）；mp-weixin 产物里可以直接核对 `app.json` 的页面数、`pages/**/*.wxml` 数量与总包大小（主包上限 2MB）。**本次实现 15 页时两端编译均通过（exit 0，小程序总包 1.54MB）。**
3. **静态自检（抓语法/资产/跳转/模板绑定类问题）**：直接用工程内的 `scripts/check-static.js`，**不需要 npm install**——它复用 HBuilderX 自带的编译器依赖（`@vue/compiler-sfc` 在 `uniapp-cli-vite`、`sass` 在 `compile-dart-sass`），用 `NODE_PATH` 指过去就行：
   ```powershell
   $env:NODE_PATH='D:\Develop\HBuilderX\plugins\uniapp-cli-vite\node_modules;D:\Develop\HBuilderX\plugins\compile-dart-sass\node_modules'
   node frontend\uni-app\scripts\check-static.js <工程绝对路径>
   ```
   它遍历 `frontend/uni-app/{pages,components}/**/*.vue` 做这些检查（**扫之前先去掉注释**，否则注释里的示例代码会误报）：
   - `@vue/compiler-sfc` 的 `parse` + `compileTemplate` + `compileScript` → 抓模板/脚本语法错误；
   - **模板引用了 script setup 未声明的标识符**：本工程没有自动导入，模板里写 `store.name` / `todaySchedules` 就必须先有顶层绑定（`const store = computed(...)`）。漏了**不会编译报错**，而是渲染时对 undefined 取属性抛错 → **整页动态绑定全部变空、只剩静态文字**（首页踩过：门店名/地址/课程/教练列表空白，只有「换店」这类写死的字还在）；
   - **`<text>` 节点里的 `>` / `<` / HTML 实体必须走绑定或加 `decode`**：uni-app 编译时会把模板里的字面 `>` 转义成 `&gt;` 写进 WXML，而微信 `<text>` **默认不解码实体**、会原样显示成「&gt;」。所以源码写 `>` 和写 `&gt;` 都会中招，正确写法是 `<text decode>{{ '>' }}</text>`（`decode` 是微信 `<text>` 的原生属性；绑定值在 WXML 里不过实体转义，H5 端同样正常）；
   - `sass` 编译 `uni.scss + style内容` → **抓 scss 变量写错**（`$ys-*` 拼错就会在这里报 undefined variable），并检查产物 CSS 里有没有**通配选择器 `*`**：WXSS 不支持（`& > * + *` 这类写法在 HBuilderX/开发者工具里报 `error at token '*'`），改用 `display:flex + gap`（本项目踩过）；
   - 正则收集所有 `/static/images/**` 引用并检查文件是否真实存在（**要同时扫 .vue、pages.json、config.js、mock/、api/、store/ —— 只扫 .vue 会漏掉 `store/modules/user.js` 里的默认头像引用，本项目已踩过**）；
   - 校验 `pages.json` 里登记的每个页面都有 `.vue`、tabBar 四个图标都在；
   - 校验页面里的跳转目标都已登记，且 tabBar 页必须用 `switchTab`（`navigateTo` 跳 tabBar 页会静默失败）；**扫之前先去掉注释**，否则 TODO 里的示例代码会误报；
   - 校验 `uni-icons` 的 `type` 名在 `uni_modules/uni-icons/.../uniicons_file_vue.js` 里真实存在（拼错会静默不显示）；
   - grep `uni.request` / `@/utils/request` / `@/mock`（页面只应依赖 `@/api/*`）；
   - **mock 数据一致性**：所有 `courseId` / `coachId` / `storeId` 能否解析到实体、默认视图（约课页「私教课 + 9.20」、教练页「团课」）是否有数据。
     ⚠️ **雪花 ID 必须写成字符串**：19 位数字字面量会超出 JS 安全整数范围而**塌缩成同一个数**，导致所有课程名/类型筛选错乱（本项目已踩过）。
4. **逐页对照原型**：`docs/原型/_复刻规范.md` 是唯一视觉权威（色值、换算、坑），页面注释里写的实测值要能对上；**原型注释与截图不一致时以截图为准**（约教练/我的体验课/申请体验三页都出现过，实现按截图并已在注释里记录）。

**想在「有数据」的状态下验收**：把 `mock/config.js` 的 `MOCK.empty.*` 改成 `false`（列表页会显示 mock 卡片），`MOCK.loggedIn` 改成 `false` 可看「我的」页的游客态。

**将来接真实后端**：确认 `backend` 在 8080、`config.js` 的 `baseUrl` 指向它、把 `api/*` 换成真实请求、`permission.js` 的 `MOCK_MODE` 改回 `false`，然后按第 7 节的域名要求准备发布。
