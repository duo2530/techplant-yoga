# 用户端 H5 端到端测试（Playwright）

覆盖 `frontend/uni-app` 构建出的 H5 页面。正常流程**直连真实后端**（期望值来自同一个接口），
网络故障、时序（乱序响应、追加失败）这类异常场景用**响应拦截**构造；教练相关用例的教练、课程、
教室与排课数据通过隔离后端的**真实管理端接口**准备与清理，不使用 mock 列表代替业务流程。
未登录与页面恢复走真实交互路径。

独立目录与独立依赖：这里的 `playwright-core` 不进 uni-app 应用的依赖，用系统已安装的 Edge / Chrome
运行，不额外下载 Playwright 自带浏览器。

## 前置条件

| 项 | 要求 |
|---|---|
| Node.js | ≥ 20.19（与应用一致） |
| 后端 | `backend` 已启动且可访问（默认 `http://localhost:8080`），MySQL／Redis 可用，已导入 `backend/sql/yoga.sql` |
| 管理端账号 | 默认 `admin` / `admin123`，需在隔离环境关闭登录验证码（`sys.account.captchaEnabled=false`），教练与排课数据准备依赖管理端接口 |
| 种子会员 | `13800138000` / `yoga1234`；预约用例需要适用于 `E2E_STORE_ID` 门店的可用次数卡，缺少时跳过 |
| H5 产物 | `frontend/uni-app/dist/build/h5`（先执行 `npm run build:h5`，或用下面的 `test:build`） |
| 浏览器 | 本机已安装 Edge 或 Chrome（找不到时用 `E2E_BROWSER_PATH` 指定） |

> 测试跑的是**构建产物**，不是 `dev:h5`：`pages.json` 的页面注册表在 dev 服务启动时就固定了，
> 新增页面后必须重启 dev 服务，构建产物没有这个坑。
>
> H5 构建产物不包含 `static/` 图片资源，测试用静态服务对 `/static/**` 回退读取工程的
> `frontend/uni-app/static` 目录，保证页面里的头像、相册与课程封面按真实路径加载。
> `run.js` 会对比源文件与 `dist/build/h5/index.html` 的修改时间，产物过旧时打印警告；
> 若刚改过页面或接口，先执行 `npm run build:h5`。

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
node run.js --only=booking
node run.js --only=约教练页
node run.js --only=首页金牌教练
```

退出码：全部通过为 0，有失败为 1。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `E2E_API_URL` | `http://localhost:8080` | 后端地址；需与 `frontend/uni-app/config.js` 的 `baseUrl` 一致 |
| `E2E_H5_DIR` | `frontend/uni-app/dist/build/h5` | H5 构建产物目录 |
| `E2E_MEMBER_PHONE` | `13800138000` | 登录手机号 |
| `E2E_MEMBER_PASSWORD` | `yoga1234` | 登录密码 |
| `E2E_ADMIN_USERNAME` | `admin` | 管理端账号，教练与排课数据准备用 |
| `E2E_ADMIN_PASSWORD` | `admin123` | 管理端密码 |
| `E2E_BROWSER_PATH` | 自动探测 Edge / Chrome | 浏览器可执行文件路径 |
| `E2E_STORE_ID` | `1856739201475235901`（徐汇店） | 预约门店 ID，须在种子会员可用次数卡的适用范围内 |
| `E2E_HEADFUL` | 未设置（无头） | 设为 `1` 时显示浏览器窗口，便于排查 |

管理端登录验证码未关闭时，教练相关用例会以「跳过」结束并给出原因，不会用 mock 数据替代。

## 数据准备与清理

- `lib/admin.js` 通过隔离后端的 `/login`、`/admin/coaches`、`/admin/coaches/{id}/level`、
  `/admin/courses`、`/admin/classrooms`、`/admin/schedules` 准备数据；每个用例在 `finally` 中按
  「排课 → 教练 → 课程 → 教室」顺序回收自己新建的记录。
- 教练名、课程名带本次运行随机后缀，删除与断言都只针对本次新建记录，不依赖种子数据。
- 公开接口 `GET /api/coaches/featured`、`GET /api/coaches/{coachId}`、
  `GET /api/coaches/{coachId}/schedules` 同时作为端到端断言的期望值来源。

## 失败产物

失败时在该目录的 `artifacts/<时间戳>/` 下按用例名留三份材料（已 gitignore）：

- `<用例名>.png`：整页截图
- `<用例名>.trace.zip`：Playwright trace（`npx playwright show-trace <文件>` 打开）
- `<用例名>.log.txt`：断言错误、页面地址、会员卡接口请求、控制台错误与失败请求

## 用例

| 文件 | 用例 |
|---|---|
| `specs/member-cards.spec.js` | 接口字段与卡片展示、空态、失败重试、登录跳转、`onShow` 刷新与首屏请求去重、列表收尾文案 |
| `specs/booking.spec.js` | 预约提交与成功页跳转、取消预约、筛选过期响应隔离、签到窗口自动启用；后两项通过响应拦截构造时序 |
| `specs/home-coaches.spec.js` | 金牌教练全量展示与排序、资料占位、加载与异常状态、门店独立性、首页刷新及卡片导航 |
| `specs/coach-page.spec.js` | 教练资料与相册预览、参数与资源错误、失败重试、无门店场景及取消金牌后访问 |
| `specs/coach-schedules.spec.js` | 课种筛选、卡片字段、分页重试、过期响应隔离、页面返回刷新及排课卡不可点击 |

具体断言与夹具清理逻辑见对应测试文件。

## 不覆盖范围

- 触底加载（`onReachBottom`）与下拉刷新是页面生命周期：H5 端 `onReachBottom` 在本仓库的
  构建产物上不触发，这两项以微信开发者工具或真机验证为准；教练页分页在 H5 用显式「加载更多」按钮验收。
- 微信授权、地图等小程序原生能力。
- 管理端（`frontend/pc`）页面：已在其独立目录配置最小 Playwright 入口。

测试策略、运行方式与验证要求的统一口径见 [AI Agent测试手册](../../../../docs/系统测试/AI%20Agent测试手册.md)。
