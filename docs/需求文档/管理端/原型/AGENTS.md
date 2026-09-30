# AGENTS.md — frontend/pc（管理端）

> 作用域：`frontend/pc/` 及其所有子目录。后端接口与业务口径见 `backend/AGENTS.md` 和 `docs/详细设计/` 下的各模块详细设计。

## 1. 这是什么

管理端（运营后台）Web 应用，基于 **RuoYi-Vue3 3.9.2 原样脚手架**：目前只有若依自带的系统管理 / 系统监控 / 系统工具页面，**没有任何瑜伽业务页面**（课程、门店、教练、排班、预约等都要新增）。

## 2. 技术栈（已核对 package.json）

| 项 | 版本 |
|---|---|
| Vue | 3.5.26（`<script setup>` 为主） |
| Vite | 6.4.1 |
| Element Plus | 2.13.1（含 dark css-vars） |
| 状态管理 | Pinia 3.0.4 |
| 路由 | vue-router 4.6.4（history 模式） |
| HTTP | axios 1.13.2（统一封装 `src/utils/request.js`） |
| 其他 | echarts 5.6、vue-quill(富文本)、vue-cropper、jsencrypt、file-saver、fuse.js |
| 本机环境 | Node 22.19 / npm 11.19，已验证 `npm install` + `npm run build:prod` 通过 |

## 3. 常用命令

```bash
cd frontend/pc
npm install
npm run dev          # vite，端口 80（Windows 上可能需管理员/端口占用冲突），自动开浏览器
npm run build:prod   # 生产构建 → dist/
npm run build:stage  # staging 构建（读 .env.staging）
npm run preview
```

- 开发代理写在 `vite.config.js`：`/dev-api` → `http://localhost:8080`（第 5 行 `baseUrl`，后端不在本机就改这里）。
- 环境变量：`.env.development`（`VITE_APP_BASE_API=/dev-api`）、`.env.staging`、`.env.production`（`/prod-api`，由部署时的 Nginx 反代决定）。
- 后端没启动时能打开页面，但登录会失败——需要 `backend` 的 MySQL/Redis + 服务在 8080。

## 4. 目录与职责

| 路径 | 职责 |
|---|---|
| `src/api/**` | 按后端的模块/实体一对一拆分的接口函数文件 |
| `src/views/**` | 页面，按业务模块分目录；`src/views/index.vue` 是首页工作台（若依演示页） |
| `src/components/**` | 全局注册的通用组件（见第 5 节） |
| `src/layout/**` | 框架布局（侧边栏/顶栏/tagsView/设置抽屉），业务不要改 |
| `src/router/index.js` | `constantRoutes`（免权限路由）+ `dynamicRoutes`（隐藏的详情/分配类路由） |
| `src/store/modules/**` | Pinia：`user`、`permission`（动态路由）、`dict`、`tagsView`、`settings`、`app`、`lock` |
| `src/directive/**` | `v-hasPermi`、`v-hasRole`、`v-copyText` |
| `src/plugins/**` | 挂到 `proxy` 上的 `$modal`/`$tab`/`$auth`/`$cache`/`$download` |
| `src/utils/request.js` | axios 实例：自动带 `Bearer` token、防重复提交、统一错误提示、`download()` |
| `src/assets/icons/svg/` | 侧边栏/菜单用的 svg 图标（文件名即 `icon` 值） |
| `vite/plugins/` | 自动导入、svg-icons、setup-extend、gzip 压缩 |

## 5. 全局能力（**不要重复 import / 重复注册**）

- **自动导入**（`unplugin-auto-import`）：`vue`、`vue-router`、`pinia` 的 API，外加 `useDict`(`@/utils/dict`)、`selectDictLabel`(`@/utils/ruoyi`)。直接写 `ref`/`computed`/`onMounted`/`useDict(...)`，**不要手写 import**（写了会重复导入告警）。
- **全局组件**：`Pagination`、`RightToolbar`、`DictTag`、`FileUpload`、`ImageUpload`、`ImagePreview`、`Editor`、`svg-icon`（`src/main.js` 中注册）。
- **全局方法**（经 `const { proxy } = getCurrentInstance()` 取）：`proxy.useDict`、`proxy.parseTime`、`proxy.resetForm`、`proxy.addDateRange`、`proxy.handleTree`、`proxy.selectDictLabel(s)`、`proxy.getConfigKey`、`proxy.download`、`proxy.$modal`、`proxy.$tab`、`proxy.$auth`、`proxy.$cache`、`proxy.$download`。
- **图标**：Element Plus 图标用字符串，如 `icon="Search"`；自定义图标用 `<svg-icon icon-class="course" />`，文件放 `src/assets/icons/svg/course.svg`。
- **形态约定**：页面根节点 `<div class="app-container">`；`<script setup name="Xxx">` 的 `name` **必须写且全局唯一**（tagsView 缓存与路由跳转依赖它）。

## 6. 新增一个管理端页面的标准步骤

1. **接口**：`src/api/<模块>/<实体>.js`，用 `@/utils/request` 导出 `listXxx/getXxx/addXxx/updateXxx/delXxx`，风格照 `src/api/system/post.js`。
2. **页面**：`src/views/<模块>/<实体>/index.vue`，抄 `src/views/system/post/index.vue` 的骨架：查询表单 + `<el-row>` 操作按钮 + `<el-table>` + `<Pagination>` + `<RightToolbar>`。
3. **权限**：按钮加 `v-hasPermi="['模块:实体:动作']"`，权限串与后端 `@PreAuthorize("@ss.hasPermi('...')")` 严格一致。
4. **字典**：`const { sys_normal_disable } = proxy.useDict('sys_normal_disable')`，模板里 `v-for="dict in sys_normal_disable"`，表格用 `<dict-tag :options="..." :value="..."/>`。
5. **菜单**：管理端路由由后端 `sys_menu` 数据驱动，页面写完后必须补菜单 SQL（`component` 填 `views` 下的相对路径，如 `course/index`；按钮权限写进 `perms`），否则侧边栏看不到。
6. **隐藏页**（详情/分配类）：加到 `src/router/index.js` 的 `dynamicRoutes`，带 `permissions` + `meta.activeMenu`。
7. **反馈统一走** `proxy.$modal.msgSuccess('新增成功')` / `proxy.$modal.confirm(...)`，请求失败已由拦截器统一提示，页面里不要重复 toast。

## 7. 已知小问题 / 注意

- `vite.config.js` 第 55~59 行的 `^/v3/api-docs/(.*)` 代理是给 springdoc 留的，而后端用的是 **springfox**（`swagger.pathMapping=/dev-api`）。本地看接口文档直接开后端端口（`http://localhost:8080/swagger-ui/index.html`）。
- dev 端口是 80：需要管理员权限或存在占用时，临时改 `vite.config.js` 的 `server.port`。
- 首页 `src/views/index.vue`、登录页文案、`src/settings.js` 的 `footerContent`、`.env.*` 的 `VITE_APP_TITLE` 仍是"若依管理系统"，品牌化时统一替换。
- 根 `.gitignore` 只忽略 `docs/原型/_工具/{渲染,对比}`，`src/assets` 下的 png/svg 正常入库；新增静态资源直接提交即可。
- `frontend/pc/.gitignore` 忽略了 `package-lock.json`（依赖版本靠 package.json 精确锁定）。

## 8. 不要做的事

- 不要为了单个业务页面去改 `src/layout/**`、`src/components/**`、`src/store/modules/permission.js` 等框架代码；业务改动只落在 `src/api`、`src/views`、`src/router`（`dynamicRoutes`）、`src/directive`。
- 不要引入 UI 框架/状态库的替代品，也不要手写一套 axios 调用绕过 `@/utils/request`。
- 不要把密码之类的敏感值写进前端代码或 `.env`。

## 9. 改完怎么验证

1. `npm run build:prod` 必须通过（最低要求，本地已验证可跑）。
2. 新增页面：后端起来后用 `admin/admin123` 登录，确认侧边栏出现菜单、增删改查与导出可用、按钮权限对非管理员隐藏。
3. 涉及字典/权限：确认 `sys_dict_type`/`sys_dict_data`、`sys_menu` 的 SQL 已一并提供。
