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

- 每个功能实现或缺陷修复完成后，必须新增或更新对应的集成测试，并实际执行受影响的测试；涉及公共配置、身份隔离、事务或跨模块调用链时，同时运行相关回归测试。不得把测试编写和首次执行推迟到 push 前。
- 涉及前端行为时，按本手册的前端测试策略补充并运行对应的 Playwright 端到端测试。
- 对应测试通过后才可报告功能完成；失败时修复并重新执行。环境不可用时报告阻塞原因和未验证范围，不得报告已完成验证。
- 每次 `git push` 前，由仓库的 [pre-push](../../.githooks/pre-push) 钩子重新执行全量后端集成测试，不按本次改动文件筛选，也不复用此前测试通过的结果；仅修改文档或前端时同样执行。
- 本手册的“全量集成测试”指 `ruoyi-yoga` 中的全部后端集成测试及其 Maven 依赖模块测试，不包含 Playwright 端到端测试和真机验收。前端测试仍按改动范围执行。

### push 前自动检查

使用 Git `pre-push` 作为统一入口，终端、IDE 和 Agent 发起的正常 Git 推送共用同一检查。钩子启用方式见[开发手册](../编码开发/AI%20Agent开发手册.md)；新克隆需要单独启用。

钩子在仓库根目录执行以下命令，使用 `clean` 清除旧测试产物，并显式关闭跳过测试和忽略失败的选项：

```powershell
mvn -B -f backend/pom.xml -pl ruoyi-yoga -am clean test -DskipTests=false -Dmaven.test.skip=false -Dmaven.test.failure.ignore=false
```

Maven、JDK 或 Docker 不可用、测试失败或构建失败时，钩子以非零退出码阻止推送；修复后重新 push 会重新执行全量测试。测试命令不接收 Git 传给钩子的标准输入。

钩子验证的是当前工作区。推送前应提交或清理相关改动，并推送当前检出的提交；推送其他提交时，应先检出该提交再执行验证，避免把工作区测试结果当成其他提交的验证结果。

本地 Git hook 可被 `--no-verify` 或修改 Git 配置绕过，不能充当服务端强制门禁。项目约定不绕过检查；若需强制所有推送或合并均通过验证，应在 CI 和分支保护中增加相同检查。

## 后端集成测试

### 环境与运行

需要 JDK 8、Maven 和可用的 Docker。依赖版本以 [父 POM](../../backend/pom.xml) 为准，容器镜像与启动配置以 [YogaMySqlContainer](../../backend/ruoyi-yoga/src/test/java/com/techplant/yoga/common/testcontainers/YogaMySqlContainer.java) 为准。

在仓库根目录执行：

```powershell
mvn -f backend/pom.xml -pl ruoyi-yoga -am test
```

删除或重命名测试、调整测试资源后，使用 `clean test` 排除旧产物影响。测试报告位于 `backend/ruoyi-yoga/target/surefire-reports/`，确认实际执行的用例、失败和跳过情况。

功能完成后可指定对应的测试类或方法，例如：

```powershell
mvn -f backend/pom.xml -pl ruoyi-yoga -am test '-Dtest=ReservationServiceImplTest,ReservationControllerTest' -Dsurefire.failIfNoSpecifiedTests=false
mvn -f backend/pom.xml -pl ruoyi-yoga -am test '-Dtest=ReservationServiceImplTest#方法名' -Dsurefire.failIfNoSpecifiedTests=false
```

将示例替换为实际受影响的测试类或方法。`surefire.failIfNoSpecifiedTests=false` 用于允许依赖模块没有匹配的测试；必须检查业务模块报告，确认指定用例确实执行，不能仅凭 Maven 成功退出判断通过。push 前的全量命令不使用 `-Dtest` 筛选。

### 配置与数据

- 测试入口位于 `backend/ruoyi-yoga/src/test/`，公共装配位于 `common/testcontainers/`。
- Maven 从启动模块复用 `application.yml` 和 MyBatis XML，相关资源配置见 [业务模块 POM](../../backend/ruoyi-yoga/pom.xml)。
- 建表语句由 `YogaSchema` 从 [yoga.sql](../../backend/sql/yoga.sql) 读取，业务脚本里没有的若依基础表（如 `sys_dict_data`）回退到 [ry_20260417.sql](../../backend/sql/ry_20260417.sql)。夹具只准备当前用例需要的数据，避免依赖种子数据。
- 共享容器时确保表与数据互不干扰；启用并行执行前验证隔离和清理方式。

### 当前覆盖边界

- 数据与业务：MyBatis 装配、教练／会员／排课数据访问、会员／会员卡／预约 HTTP 接口及预约事务。
- 接口与身份：真实安全链下的管理端／会员身份隔离、公开 GET 白名单，以及参数绑定、请求体校验和服务端配置异常。教练等级维护覆盖普通管理员可操作、游客和会员不可操作。
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
