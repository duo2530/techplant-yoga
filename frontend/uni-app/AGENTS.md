# AGENTS.md — frontend/uni-app（用户端 / 小程序）

> 作用域：`frontend/uni-app/` 及其所有子目录。业务口径见 `docs/需求分析/需求分析.md`、`docs/详细设计/详细设计.md`。

## 1. 这是什么

面向 **微信小程序**（用户端；教练端是否并入本工程尚未定论）的 uni-app 工程，基于 **RuoYi-App 模板**。目前只有若依自带的登录/注册/首页/工作台/我的页面，**没有任何瑜伽业务页面**。

**这是 Vue 3 + uni-app，且是 HBuilderX 工程**——两个最容易踩的前提，见下节。

## 2. 运行方式（**没有 package.json，不能用 npm**）

- 工程里**没有 `package.json`**，`npm install` / `npm run dev:mp-weixin` 这类 CLI 流程**不适用**，也没有 `unpackage/` 构建缓存。只能：
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
| `pages.json` | **页面注册表**：`pages` 数组（第一项是启动页 `pages/login`）、`tabBar`、`globalStyle` |
| `manifest.json` | 应用名/appid/微信 appid/`vueVersion`/各端配置（改动这里后要重新编译） |
| `config.js` | 全局配置：`baseUrl`（后端地址）、`appInfo`（名称、logo、协议链接） |
| `main.js` / `App.vue` | 应用入口；`App.vue` 里 `onLaunch` 初始化配置，且**只在 H5 分支**做登录检查（`#ifdef H5`） |
| `permission.js` | 页面跳转拦截 + 免登录白名单 |
| `pages/**` | 页面：`login`、`register`、`index`（首页）、`work/index`（工作台）、`mine/**`、`common/webview`、`common/textview` |
| `api/**` | 接口函数（`api/login.js`、`api/system/**`），内部用 `@/utils/request` |
| `store/modules/**` | `user`（token/角色/权限）、`config`、`dict` |
| `utils/**` | `request`、`auth`（token 存取）、`dict`（`useDict`）、`storage`、`permission`、`upload`、`validate`、`common`（`toast`/`showConfirm`/`tansParams`）、`constant`、`errorCode` |
| `plugins/**` | `proxy.$tab`、`proxy.$auth`、`proxy.$modal` |
| `components/**` | 自定义组件（easycom 规则见第 5 节） |
| `static/**` | 静态资源（图片、字体、`scss/index.scss`、`static/index.html` H5 模板） |
| `uni_modules/**` | 第三方插件代码，**不要手改** |

## 5. 代码约定

- **必须显式 import**：没有自动导入，`ref`/`computed` 要从 `vue` 引入，页面生命周期（`onLoad`/`onShow`/`onLaunch`/`onPullDownRefresh`）要从 `@dcloudio/uni-app` 引入。参考 `pages/login.vue`、`App.vue`。
- **组件 easycom**：`components/组件名/组件名.vue` 与 `uni_modules/**` 自动注册，模板里直接 `<uni-icons type="person-filled" />`，不需要 import。
- **页面必须登记**：新建页面要在 `pages.json` 的 `pages` 数组里加一项（否则跳转失败）；新增 tabBar 页要同时改 `pages.json` 的 `tabBar.list` 并提供 `static/images/tabbar/*.png` 图标（未选中/选中两张）。
- **请求统一走** `@/utils/request`：自动带 `Bearer` token，401 弹"重新登录"并 `reLaunch('/pages/login')`，500/其他 code 已 toast；页面里不要再包一层错误提示。风格照 `api/system/user.js`。
- **接口地址**来自 `config.js` 的 `baseUrl`，不要在页面里硬编码域名。
- **状态**：`const userStore = useUserStore()`（`@/store` 或 `@/store/modules/user`），token 通过 `utils/auth` 读写。
- **字典**：`useDict('sys_normal_disable')`（`@/utils/dict`，返回 `toRefs`）。
- **样式**：尺寸用 `rpx`；全局样式 `static/scss/index.scss` 在 `App.vue` 里 `@import`；`uni.scss` 放公共变量。
- **静态资源路径**：`/static/xxx.png` 或 `@/static/xxx.png`（两者工程里都在用，保持与所在文件一致即可）。
- **免登录页**：新增不需要登录的页面时，要同时加进 `permission.js` 的 `whiteList`，否则会被重定向到登录页。
- 中文文案与注释，和现有代码保持一致。

## 6. 当前状态与待品牌化的地方

`config.js` 的后端地址**已切到本机后端**（若依演示地址保留为注释）：

```js
// baseUrl: 'https://vue.ruoyi.vip/prod-api',
baseUrl: 'http://localhost:8080',
appInfo: { name: "ruoyi-app", version: "1.2.0", logo: "/static/logo.png",
           site_url: "http://ruoyi.vip",
           agreements: [{ title: "隐私政策", url: "https://ruoyi.vip/protocol.html" }, ...] }
```

仍需替换的若依痕迹：`appInfo` 的名称/版本/官网/协议链接（`config.js`）、`manifest.json` 的 `name`（"若依移动端"）与 H5 `title`（"RuoYi-App"）、`pages.json` 的 `globalStyle.navigationBarTitleText`（"RuoYi"）与首页标题（"若依移动端框架"）、`pages/index.vue`（Hello RuoYi）、`pages/work/index.vue`（若依演示工作台）。

> 注意：`baseUrl` 改成 `localhost` 只对本机 H5/开发者工具有效；真机预览和发布都要换成可访问的 https 域名（见第 7 节）。

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

1. HBuilderX 运行到微信开发者工具，**编译无报错**（最低要求；本工程没有自动化构建/测试）。
2. 新页面能正常跳转、出现在预期的 tabBar/入口中。
3. 需要后端联调时：`backend` 服务在 8080 且 `config.js` 已指向它，登录 → 首页 → 我的 链路能拿到真实数据。
