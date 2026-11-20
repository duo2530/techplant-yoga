# 用 GitHub Actions 发布到 SAE

工作流会从 `master` 当前提交构建后端、管理端和 H5 镜像，推送到现有 ACR 个人版仓库，更新 SAE 上的三个业务应用。发布使用仓库里已有的 [SAE 配置](../../.claude/skills/yoga-cloud-deploy/config/sae.config.json) 定位地域、命名空间和应用。MySQL 和 Redis 停机时会先启动，后续按后端、管理端、H5 的顺序发布。PR 检查只构建镜像和验证 Nginx 配置。

## 一次性配置 GitHub 与阿里云

1. 在 GitHub 仓库的 **Settings → Environments** 创建 `sae`，部署分支限制为 `master`。
2. 在阿里云 RAM 创建 GitHub OIDC 身份提供商和用于发布的角色。签发者为 `https://token.actions.githubusercontent.com`，受众为 `sts.aliyuncs.com`。角色信任策略用 `StringEquals` 匹配此仓库的 `sae` 环境 subject。使用不可变 subject 的仓库，格式是 `repo:<owner>@<owner_id>/<repo>@<repo_id>:environment:sae`。使用旧格式的仓库，格式是 `repo:<owner>/<repo>:environment:sae`。 [阿里云 RAM 角色配置](https://help.aliyun.com/zh/ram/user-guide/create-a-ram-role-for-a-trusted-idp) · [GitHub OIDC subject](https://docs.github.com/en/actions/reference/security/oidc)
3. OIDC 角色需要两条自定义策略。
   - SAE：`sae:ListApplications`、`sae:DescribeApplicationConfig`、`sae:DescribeApplicationStatus`、`sae:DeployApplication`、`sae:DescribeChangeOrder`、`sae:StartApplication`、`sae:StopApplication`、`sae:DescribeApplicationSlbs`、`sae:BindSlb` 和 `sae:UnbindSlb`，资源为 `*`。
   - 云解析：`alidns:DescribeDomainRecords`、`alidns:AddDomainRecord`、`alidns:UpdateDomainRecord` 和 `alidns:DeleteDomainRecord`，资源为 `acs:alidns:*:*:domain/liujie.tech`。RAM 只能限制到域名，不能限制到单条子域名。
4. 在 ACR 个人版的 **仓库管理 → 访问凭证** 获取有镜像推送权限的仓库登录名和固定密码。GitHub 托管运行器使用此凭证连接 ACR 的公网推送地址，SAE 通过现有 VPC 拉取地址和镜像拉取凭证获取镜像。 [ACR 个人版推拉镜像](https://help.aliyun.com/zh/acr/user-guide/use-a-container-registry-personal-edition-instance-to-push-and-pull-images)
5. 在 GitHub 的 `sae` 环境添加以下变量和密钥。

| 名称 | 类型 | 值 |
| --- | --- | --- |
| `ALIYUN_OIDC_PROVIDER_ARN` | Variable | GitHub OIDC 身份提供商 ARN |
| `ALIYUN_ROLE_ARN` | Variable | 发布角色 ARN |
| `ACR_USERNAME` | Secret | ACR 镜像仓库登录名 |
| `ACR_PASSWORD` | Secret | ACR 镜像仓库固定密码 |

## 域名与工作台

发布完成后，工作流从 `yoga-web-pc` 读取当前公网绑定。有可用绑定时复用。绑定的 CLB 已失效时先解除旧绑定。缺少绑定时由 SAE 代购按使用量计费的公网 CLB。管理端使用端口 `80`，H5 使用端口 `8081`。两个监听绑定到 `yoga-web-pc`，其 Nginx 将 H5 请求转发至 `yoga-web-h5` 的 SAE 内网服务。

拿到 CLB 公网 IP 后，工作流在云解析的 `liujie.tech` 下写入两条 A 记录，TTL 600 秒。子域名在 [SAE 配置](../../.claude/skills/yoga-cloud-deploy/config/sae.config.json) 的 `public_domain` 中配置。

| 入口 | 地址 |
| --- | --- |
| 管理端 | `http://yoga.liujie.tech` |
| H5 用户端 | `http://m.yoga.liujie.tech:8081` |

记录已存在时更新 IP。同名记录不是 A 类型或有多条时，工作流报错，需要先在云解析控制台处理。写入后，工作流向权威 DNS 确认解析生效，再按域名经 CLB 验证管理端登录页、验证码接口、H5 首页及其公开 API。

本次运行的 **Summary** 顶部是工作台，包含管理端和 H5 的入口，以及释放环境的链接。点击释放链接会打开回滚工作流页面，再点 **Run workflow** 执行。发布失败时，工作台只显示释放链接。发布明细折叠在工作台下方。本地 DNS 缓存未刷新时，可以先用工作台下方的 IP 入口。

点击链接后使用项目已有的管理端账号登录。先修改默认密码。域名使用 HTTP，适合短时验收；长期使用管理员账号时，配置带有效证书的 HTTPS。公开仓库的 Actions 运行摘要可以被其他人查看。

## 释放环境

在 GitHub 选择 **Actions → Tear down Alibaba Cloud SAE → Run workflow**，分支选 `master`，或者点发布摘要里的释放链接。工作流按以下顺序执行，与发布共用一个并发组，不会同时运行。

1. 删除 `yoga`、`m.yoga` 两条解析记录。CLB 释放后 IP 会回到地址池，保留记录可能让域名指向别人的资源。
2. 解除各应用的公网 CLB 绑定。SAE 代购的 CLB 随解绑释放。自行购买的实例解绑后仍保留，摘要会提示到 CLB 控制台另行释放。
3. 按 H5、管理端、后端、Redis、MySQL 的顺序停止 5 个 SAE 应用。

NAS 上的 MySQL、Redis 数据和 ACR 镜像保留。再次发布会重新启动应用、购买 CLB 并写入解析。

[SAE 绑定 CLB](https://help.aliyun.com/zh/sae/sae-2-bind-clb-for-application) · [CLB 配置约束与解绑](https://help.aliyun.com/zh/sae/clb-instructions-for-use-2-0)

## 发布与回退

在 GitHub 选择 **Actions → Deploy to Alibaba Cloud SAE → Run workflow**，分支选 `master`。三个镜像推送成功后才会启动和更新应用。工作流会等待各次 SAE 发布单和启动单完成。打开本次运行摘要工作台的管理端和 H5 链接，验证所需业务页面。

若中途失败，Actions 运行摘要的发布明细列出新旧镜像与已完成的发布单。确认各应用当前运行的版本后，可以在 SAE 控制台将受影响的应用部署回发布前的镜像。重跑工作流会使用独立镜像标签。
