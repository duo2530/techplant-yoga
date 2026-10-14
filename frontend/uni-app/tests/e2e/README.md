# 用户端 H5 端到端测试（Playwright）

覆盖 `frontend/uni-app` 构建出的 H5 页面。正常流程**直连真实后端**（期望值来自同一个接口），
空列表、查询失败、失败后重试这类异常分支用**响应拦截**构造；未登录与页面恢复走真实交互路径。

独立目录与独立依赖：这里的 `playwright-core` 不进 uni-app 应用的依赖，用系统已安装的 Edge / Chrome
运行，不额外下载 Playwright 自带浏览器。

## 前置条件

| 项 | 要求 |
|---|---|
| Node.js | ≥ 20.19（与应用一致） |
| 后端 | `backend` 已启动且可访问（默认 `http://localhost:8080`），MySQL／Redis 可用，已导入 `backend/sql/yoga.sql` |
| 种子会员 | 手机号 `13800138000` / 密码 `yoga1234`（与 `frontend/uni-app/AGENTS.md` 联调账号一致），名下最好已有会员卡 |
| H5 产物 | `frontend/uni-app/dist/build/h5`（先执行 `npm run build:h5`，或用下面的 `test:build`） |
| 浏览器 | 本机已安装 Edge 或 Chrome（找不到时用 `E2E_BROWSER_PATH` 指定） |

> 测试跑的是**构建产物**，不是 `dev:h5`：`pages.json` 的页面注册表在 dev 服务启动时就固定了，
> 新增页面后必须重启 dev 服务，构建产物没有这个坑。

## 运行

```powershell
# 1) 先构建 H5 产物（在 frontend/uni-app）
cd frontend/uni-app
npm run build:h5

# 2) 安装测试依赖并运行（在本目录）
cd tests/e2e
npm ci
npm test

# 一步到位的写法：先构建再跑
npm run test:build

# 只跑名称匹配的用例文件
node run.js --only=member-cards
```

退出码：全部通过为 0，有失败为 1。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `E2E_API_URL` | `http://localhost:8080` | 后端地址；需与 `frontend/uni-app/config.js` 的 `baseUrl` 一致 |
| `E2E_H5_DIR` | `frontend/uni-app/dist/build/h5` | H5 构建产物目录 |
| `E2E_MEMBER_PHONE` | `13800138000` | 登录手机号 |
| `E2E_MEMBER_PASSWORD` | `yoga1234` | 登录密码 |
| `E2E_BROWSER_PATH` | 自动探测 Edge / Chrome | 浏览器可执行文件路径 |
| `E2E_HEADFUL` | 未设置（无头） | 设为 `1` 时显示浏览器窗口，便于排查 |

## 失败产物

失败时在该目录的 `artifacts/<时间戳>/` 下按用例名留三份材料（已 gitignore）：

- `<用例名>.png`：整页截图
- `<用例名>.trace.zip`：Playwright trace（`npx playwright show-trace <文件>` 打开）
- `<用例名>.log.txt`：断言错误、页面地址、会员卡接口请求、控制台错误与失败请求

## 用例

| 文件 | 用例 |
|---|---|
| `specs/member-cards.spec.js` | 列表字段与接口逐条一致；次数卡进度条比例与初始次数括注；期限卡只给剩余天数且无进度条；成功空列表显示「暂无会员卡」；查询失败显示失败提示与重试入口（不误报无卡）；失败后重试恢复列表；未登录跳登录页且返回后是登录提示；页面恢复（`onShow`）重新拉取且首屏只拉一次；底部「已经到底了」收尾文案 |

## 不覆盖范围

- 触底加载（`onReachBottom`）与下拉刷新是页面生命周期：H5 端 `onReachBottom` 在本仓库的
  构建产物上不触发，这两项以微信开发者工具或真机验证为准。
- 微信授权、地图等小程序原生能力。
- 管理端（`frontend/pc`）页面：需要时在 `frontend/pc/tests/e2e` 另建同构套件。

测试策略、运行方式与验证要求的统一口径见 [AI Agent测试手册](../../../../docs/系统测试/AI%20Agent测试手册.md)。
