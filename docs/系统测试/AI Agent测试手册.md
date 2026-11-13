# AI Agent测试手册

本手册集中维护测试策略、运行方式和验证要求。各级 AGENTS.md 与开发手册只保留入口，不重复维护这些内容。

## 测试策略

- 后端不编写单元测试，使用 Testcontainers 集成测试；真实 MySQL、Redis 等依赖由隔离容器提供，不新增 H2 测试。
- 加载真实应用配置和相关业务调用链，不模拟内部 service、DAO 或存储。测试所需的连接信息可以覆盖，生产配置和 SQL 不维护测试副本。
- 测试数据自行准备和清理，不连接开发库或生产库，不依赖共享账号或人工预置数据。
- 时间相关用例按运行时日期准备夹具，覆盖日期窗口首尾和已结束、未来场次等边界。
- 按接口契约和业务流程覆盖正常、边界、异常、认证隔离、数据库约束、事务回滚和并发行为，不要求逐类逐方法编写测试。
- 前端使用 Playwright 端到端测试。管理端测试 Web 页面，uni-app 测试 H5 页面；正常流程连接隔离的真实后端，网络故障等异常场景可以拦截响应。
- 需要防止回归的行为通过测试约束，不依赖源码注释提醒。全量接口 mock、编译和静态检查不能替代集成测试或端到端测试。
- 微信授权、平台 API 等小程序能力仍需开发者工具或真机验证，H5 浏览器测试不能代替这些检查。

## 开发与推送时机

- 每个功能实现或缺陷修复都要新增或更新对应的后端集成测试；涉及公共配置、身份隔离、事务或跨模块调用链时，同时补充相关回归测试。
- 后端集成测试只在 GitHub Actions 上执行，本地不运行。推送分支、创建 PR 后，以 PR 上的检查结果为准，用 `gh pr checks <PR号>` 查看。
- 涉及前端行为时，按本手册的前端测试策略补充 Playwright 端到端测试，并在本地连接隔离后端运行。
- 对应测试通过后才可报告功能完成；CI 失败时根据 Actions 日志和 Surefire 报告修复后重新推送。环境不可用时报告阻塞原因和未验证范围，不得报告已完成验证。
- 本手册的“全量集成测试”指 `ruoyi-yoga` 中的全部后端集成测试及其 Maven 依赖模块测试，不包含 Playwright 端到端测试和真机验收。

### CI 检查

工作流定义在 [backend-integration-test.yml](../../.github/workflows/backend-integration-test.yml)，在推送到 `master` 和向 `master` 发起 PR 时触发，包含三个检查：

- `Detect backend changes`：判断本次改动是否影响后端测试，范围包括后端源码、POM、SQL、`docker-compose.yml`、工作流本身和 Java 格式配置。
- `Run backend integration tests`：受影响时由 Testcontainers 启动 MySQL 8 与 Redis 7，执行全量集成测试，Surefire 报告作为 `backend-test-reports` 产物保留 14 天。仅修改文档或前端时跳过。
- `CI Gate`：需要测试时要求测试通过，无需测试时要求测试被跳过，作为合并前的统一检查。

`ReservationConcurrencyTest` 默认不执行；需要时在 Actions 页面手动触发该工作流并勾选 `run_concurrency_tests`。
需要强制所有合并都通过检查时，在分支保护中把 `CI Gate` 设为必需检查。

## 后端集成测试

### 环境与运行

CI 使用 JDK 8、Maven 和 Docker。依赖版本以 [父 POM](../../backend/pom.xml) 为准，容器镜像与启动配置以 [YogaMySqlContainer](../../backend/ruoyi-yoga/src/test/java/com/techplant/yoga/common/testcontainers/YogaMySqlContainer.java) 为准。CI 在 `backend` 目录执行：

```bash
mvn -B -ntp -pl ruoyi-yoga -am -DfailIfNoTests=false test
```

失败时下载 `backend-test-reports` 产物，确认实际执行的用例、失败和跳过情况，不能仅凭检查状态判断哪些用例执行过。

### 预约并发协议模型检查

[verify-reservation-concurrency](../../.agents/skills/verify-reservation-concurrency/SKILL.md) 用 TLA+ 模型和 TLC 穷举小规模场景下的全部交错，检查不超卖、不重复有效预约与返次恰好一次。修改预约、排课或会员卡的加锁、隔离级别、条件更新或排课状态流转时，与 #24 引入的多线程并发正确性测试 `ReservationConcurrencyTest`（随 #37 合入）一起执行：

```bash
bash tools/formal/reservation/run.sh
```

需要 Docker，约 1 分钟。模型只覆盖其范围内的交错，间隙锁与真实数据库行为仍以该并发测试为准；范围、已知发现与维护方法见该 skill。
### 预约并发正确性测试

[ReservationConcurrencyTest](../../backend/ruoyi-yoga/src/test/java/com/techplant/yoga/reservation/service/ReservationConcurrencyTest.java) 用多线程同时发起预约、排队与取消，每轮结束后按对账 SQL 校验名额、次数与流水。该类默认跳过，全量测试只把它计为 1 个跳过；修改预约、排课锁、会员卡扣返次或事务配置后，在 Actions 页面手动触发工作流并勾选 `run_concurrency_tests`，CI 会追加 `-Dyoga.concurrency=true` 执行。

- 混合场景每轮打印 `随机种子`，复现失败轮次时加 `-Dyoga.concurrency.seed=<种子>` 固定。
- 六个场景各 10 轮，共 60 个用例，单次运行约 5 分钟。任一轮出现对账失败、死锁或锁等待超时等非业务异常即判失败，出现死锁时打印 InnoDB 最近一次死锁信息。
- 输出末尾的 `[累计]` 行是各场景跨轮次的结果分布，用于前后对比；报告时给出 Surefire 计数和该分布。

### 配置与数据

- 测试入口位于 `backend/ruoyi-yoga/src/test/`，公共装配位于 `common/testcontainers/`。
- Maven 从启动模块复用 `application.yml` 和 MyBatis XML，相关资源配置见 [业务模块 POM](../../backend/ruoyi-yoga/pom.xml)。
- 建表语句由 `YogaSchema` 从 [yoga.sql](../../backend/sql/yoga.sql) 读取，业务脚本里没有的若依基础表（如 `sys_dict_data`）回退到 [ry_20260417.sql](../../backend/sql/ry_20260417.sql)。夹具只准备当前用例需要的数据，避免依赖种子数据。
- 共享容器时确保表与数据互不干扰；启用并行执行前验证隔离和清理方式。

### 当前覆盖边界

- 数据与业务：MyBatis 装配、教练／会员／排课数据访问、会员／会员卡／预约 HTTP 接口及预约事务。
- 接口与身份：真实安全链下的管理端／会员身份隔离、公开 GET 白名单，以及参数绑定、请求体校验和服务端配置异常。教练等级维护覆盖普通管理员可操作、游客和会员不可操作。
- 图片上传：`/admin/images` 的认证隔离与大小、格式、文件头校验，见 [ImageUploadHttpTest](../../backend/ruoyi-yoga/src/test/java/com/techplant/yoga/image/controller/ImageUploadHttpTest.java)。设置 `YOGA_OSS_ACCESS_KEY_ID`／`YOGA_OSS_ACCESS_KEY_SECRET` 后真实上传到 OSS、匿名读回并删除对象；未设置时该用例跳过，改为验证「图片存储未配置」。CI 未配置这两个变量，只覆盖未配置分支。
- 数据库初始化：`yoga.sql` 首次导入与重复重建、教练等级默认值、种子排课关联及业务菜单绑定，见 [SqlInitializationTest](../../backend/ruoyi-yoga/src/test/java/com/techplant/yoga/common/sql/SqlInitializationTest.java)。

预约并发与故障回滚的用例映射及未覆盖项见[预约并发与事务核对](预约并发与事务核对.md)。相关改动应补充测试或报告验证缺口。

接口级验证应检查统一响应、参数校验、ID 字符串化、身份隔离和失败分支。菜单相关改动应验证路由返回及页面入口，接口文档相关改动应检查 Swagger 中的接口是否可见。

## 前端端到端测试

### Playwright

用户端 H5 套件覆盖会员卡、预约、首页今日可约团课、热门课程、金牌教练及约教练页。测试数据通过隔离后端的真实接口准备与清理；运行命令、环境变量、覆盖范围及失败产物见[用户端套件 README](../../frontend/uni-app/tests/e2e/README.md)。

管理端已接入最小 Playwright 套件，位于 `frontend/pc/tests/e2e`：独立 `package.json` ＋ `playwright-core`（使用系统已安装的 Edge／Chrome），托管 `npm run build:prod` 的 `frontend/pc/dist` 产物，并把 `/prod-api/**` 代理到隔离后端。运行方式：

```powershell
cd frontend/pc/tests/e2e
npm ci
$env:E2E_API_URL = 'http://<隔离后端>:8080'
$env:E2E_ALLOW_DESTRUCTIVE = '1'
npm test
```

前置条件：必须先**显式设置 `E2E_API_URL`**（不依赖默认值）并设置 **`E2E_ALLOW_DESTRUCTIVE=1`**，任一缺失时套件以退出码 3 拒绝执行，禁止指向开发库或共享库；隔离库 `sys.account.captchaEnabled=false`、MySQL／Redis 可用、管理员 `admin`／`admin123` （账号可用环境变量覆盖）。用例与不覆盖范围见[管理端套件 README](../../frontend/pc/tests/e2e/README.md)。

- 管理端覆盖登录、退出、表单校验、增删改查和异常反馈；涉及权限或字典时验证相应页面行为。
- 教练用例覆盖首页金牌栏目、约教练资料、排课分页与筛选，以及页面返回刷新。
- 使用稳定的类名或结构定位元素，通过可见结果断言和自动等待同步操作，避免固定延时。
- 触底加载、下拉刷新等小程序页面生命周期在 H5 端不可靠（构建产物上 `onReachBottom` 不触发），
  这两类行为以微信开发者工具或真机验证为准。
- uni-app 的测试工具使用独立目录与依赖配置。
- 已知限制：`npm run build:h5`／`npm run build:mp-weixin` 当前产物不包含 `static/` 目录（既有 `UNI_INPUT_DIR=.` 与 uni copy 插件的相对路径判断冲突，非本次变更引入）。H5 端到端测试的静态服务对 `/static/**` 回退读取工程 `frontend/uni-app/static` 目录；正式发布需要另行配置静态资源复制，或后续单独修复构建脚本。

### 管理端辅助检查

在 `frontend/pc` 目录执行：

```powershell
npm run build:prod
```

构建通过后，按改动范围检查页面入口、增删改查、导出及相关 SQL。人工联调使用测试环境准备的账号，不连接生产环境。

### uni-app 辅助检查

在 `frontend/uni-app` 执行以下命令，使用工程内依赖，不需要本机 HBuilderX 插件路径：

```powershell
npm ci
npm run build:h5
npm run build:mp-weixin
npm run check:static
npm run check:request
```

开发时执行 `npm run dev:h5` 或 `npm run dev:mp-weixin`。H5 服务默认使用 9090 端口；微信开发者工具导入 `dist/dev/mp-weixin`，发行时导入 `dist/build/mp-weixin`。H5 生产产物为 `dist/build/h5`。

也可用 HBuilderX 打开工程并编译；修改 `pages.json` 或 `manifest.json` 后重新编译。微信平台能力仍需开发者工具或真机验证。

- `check-static.js` 检查模板与脚本、样式、资源引用、页面注册、跳转及 mock 数据一致性；具体检查以脚本为准。
- `check-request.js` 是现有请求与会话逻辑的本地自检，不启动真实后端，仅作为辅助检查。
- 原型对照以 [复刻规范](../需求文档/用户端/高保真原型/_复刻规范.md) 和截图为准，测量与差异记录维护在设计文档中。
- 验收 mock 列表时，可将 `mock/config.js` 对应的 `MOCK.empty.*` 改为 `false`；登录态用测试环境账号验证。
- 微信开发者工具和真机检查平台能力、导航及渲染；小程序编译产物还应检查页面注册、资源和包体积。

## 验证结果报告

说明执行了什么、验证了哪些行为、是否通过，以及尚未覆盖的部分。区分编译、静态检查、集成测试、端到端测试和人工验收；环境不可用或测试尚未接入时明确报告，不能以跳过或未发现测试代替成功。

具体执行结果记录在对应变更的验收记录中。
