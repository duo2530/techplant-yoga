# AI Agent测试手册

本手册集中维护测试策略、运行方式和验证要求。各级 AGENTS.md 与开发手册只保留入口，不重复维护这些内容。

## 测试策略

- 后端不编写单元测试，使用 Testcontainers 集成测试；真实 MySQL、Redis 等依赖由隔离容器提供，不新增 H2 测试。
- 加载真实应用配置和相关业务调用链，不模拟内部 service、DAO 或存储。测试所需的连接信息可以覆盖，生产配置和 SQL 不维护测试副本。
- 测试数据自行准备和清理，不连接开发库或生产库，不依赖共享账号或人工预置数据。
- 按接口契约和业务流程覆盖正常、边界、异常、认证隔离、数据库约束、事务回滚和并发行为，不要求逐类逐方法编写测试。
- 前端使用 Playwright 端到端测试。管理端测试 Web 页面，uni-app 测试 H5 页面；正常流程连接隔离的真实后端，网络故障等异常场景可以拦截响应。
- 需要防止回归的行为通过测试约束，不依赖源码注释提醒。全量接口 mock、编译和静态检查不能替代集成测试或端到端测试。
- 微信授权、平台 API 等小程序能力仍需开发者工具或真机验证，H5 浏览器测试不能代替这些检查。

## 后端集成测试

### 环境与运行

需要 JDK 8、Maven 和可用的 Docker。依赖版本以 [父 POM](../../backend/pom.xml) 为准，容器镜像与启动配置以 [YogaMySqlContainer](../../backend/ruoyi-yoga/src/test/java/com/techplant/yoga/common/testcontainers/YogaMySqlContainer.java) 为准。

在仓库根目录执行：

```powershell
mvn -f backend/pom.xml -pl ruoyi-yoga -am test
```

删除或重命名测试、调整测试资源后，使用 `clean test` 排除旧产物影响。测试报告位于 `backend/ruoyi-yoga/target/surefire-reports/`，确认实际执行的用例、失败和跳过情况。

### 配置与数据

- 测试入口位于 `backend/ruoyi-yoga/src/test/`，公共装配位于 `common/testcontainers/`。
- Maven 从启动模块复用 `application.yml` 和 MyBatis XML，相关资源配置见 [业务模块 POM](../../backend/ruoyi-yoga/pom.xml)。
- 建表语句由 `YogaSchema` 从 [yoga.sql](../../backend/sql/yoga.sql) 读取，业务脚本里没有的若依基础表（如 `sys_dict_data`）回退到 [ry_20260417.sql](../../backend/sql/ry_20260417.sql)。夹具只准备当前用例需要的数据，避免依赖种子数据。
- 共享容器时确保表与数据互不干扰；启用并行执行前验证隔离和清理方式。

### 当前覆盖边界

已覆盖 MyBatis 装配、教练／会员／排课数据访问、会员／会员卡／预约 HTTP 接口及预约事务。本次新增真实安全链 HTTP 测试（真实 MySQL＋Redis Testcontainers，请求经真实 `FilterChainProxy` 与 `@PreAuthorize`，令牌由真实 TokenService 生成）：覆盖游客／会员／无权限管理员的权限隔离、公开接口仅 GET 白名单、新增接口 400／404 错误契约。该上下文使用**固定测试时钟**（Asia/Shanghai）消除跨日不稳定；生产运行时的 `Clock` 由 `BusinessTimeConfig` 固定为 `Asia/Shanghai`，并有测试断言锁定该业务时区常量。另新增 SQL 升级脚本测试（一次性隔离 MySQL）：验证增量脚本可重复执行、旧数据与排课关联保留、旧教练默认普通等级，且**重复执行保留运营角色对 2010 的授权**、内置管理员绑定不重复插入、菜单内容不变、既有 2000~2008 菜单与绑定逐 ID 不变。新增测试类位于 `backend/ruoyi-yoga/src/test/java/com/techplant/yoga/` 下的 `coach/controller`、`schedule/controller`、`member/controller`、`common/sql` 与 `common/testcontainers`。预约并发、故障回滚的规则与用例映射见[预约并发与事务核对](预约并发与事务核对.md)。该核对文档列出的部分并发组合仍有验证缺口，相关改动应补充测试或报告验证缺口。

接口级验证应检查统一响应、参数校验、ID 字符串化、身份隔离和失败分支。菜单相关改动应验证路由返回及页面入口，接口文档相关改动应检查 Swagger 中的接口是否可见。

## 前端端到端测试

### Playwright

用户端 H5 套件位于 `frontend/uni-app/tests/e2e`，覆盖会员卡、预约页面，以及本次新增的教练相关用例：`home-coaches`（8 条）、`coach-page`（9 条）、`coach-schedules`（9 条），共 26 条。教练、课程、教室与排课数据通过隔离后端的真实管理端接口准备与清理。运行命令、环境变量、用例及失败产物见[用户端套件 README](../../frontend/uni-app/tests/e2e/README.md)。

管理端已接入最小 Playwright 套件，位于 `frontend/pc/tests/e2e`：独立 `package.json` ＋ `playwright-core`（使用系统已安装的 Edge／Chrome），托管 `npm run build:prod` 的 `frontend/pc/dist` 产物，并把 `/prod-api/**` 代理到隔离后端。运行方式：

```powershell
cd frontend/pc/tests/e2e
npm ci
$env:E2E_API_URL = 'http://<隔离后端>:8080'
$env:E2E_ALLOW_DESTRUCTIVE = '1'
npm test
```

前置条件：必须先**显式设置 `E2E_API_URL`**（不依赖默认值）并设置 **`E2E_ALLOW_DESTRUCTIVE=1`**，任一缺失时套件以退出码 3 拒绝执行，禁止指向开发库或共享库；隔离库 `sys.account.captchaEnabled=false`、MySQL／Redis 可用、管理员 `admin`／`admin123` 且角色具备 `yoga:coach:level`（账号可用环境变量覆盖）。用例与不覆盖范围见[管理端套件 README](../../frontend/pc/tests/e2e/README.md)。

- 管理端覆盖登录、退出、表单校验、增删改查和异常反馈；涉及权限或字典时验证相应页面行为。
- 教练用例覆盖首页金牌栏目、约教练资料与排课分页/筛选，以及**约教练页返回刷新**：从课程详情返回后重新拉取当前课种第一页、课种 Tab 与门店保持不变（回归用例）。
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

本次交付（add-home-gold-coaches）的验证结果：后端全量 20 个测试类、159 个集成测试通过（失败 0／错误 0／跳过 0，BUILD SUCCESS；Java 风格检查 0 violations）；用户端 H5 全量 38 通过 / 2 跳过 / 0 失败（跳过为既有 booking 用例，因隔离库种子会员无可用会员卡/无预约记录，与本次无关），其中新增教练相关用例 26/26 通过（含「返回刷新」回归）；管理端最小套件 6/6 通过（0 失败 0 跳过）。构建与静态检查 `build:h5`、`build:mp-weixin`、`check:static`、`check:request`、`build:prod` 全部通过。完整链路（后台设置金牌 → 首页展示 → 约教练 → 课程详情 → 预约；后台取消金牌 → 首页移除；取消后已打开的教练资料仍可访问）复跑通过。
