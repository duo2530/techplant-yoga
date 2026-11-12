# 用 GitHub Actions 发布到 SAE

工作流会从 `master` 当前提交构建后端、管理端和 H5 镜像，推送到现有 ACR 企业版仓库，更新 SAE 上的三个业务应用。发布使用仓库里已有的 [SAE 配置](../../.claude/skills/yoga-cloud-deploy/config/sae.config.json) 定位地域、命名空间和应用。MySQL 和 Redis 停机时会先启动，后续按后端、管理端、H5 的顺序发布。PR 检查只构建镜像和验证 Nginx 配置。

## 一次性配置 GitHub 与阿里云

1. 在 GitHub 仓库的 **Settings → Environments** 创建 `sae`，部署分支限制为 `master`。
2. 在阿里云 RAM 创建 GitHub OIDC 身份提供商和用于发布的角色。签发者为 `https://token.actions.githubusercontent.com`，受众为 `sts.aliyuncs.com`。角色信任策略用 `StringEquals` 匹配此仓库的 `sae` 环境 subject。使用不可变 subject 的仓库，格式是 `repo:<owner>@<owner_id>/<repo>@<repo_id>:environment:sae`。使用旧格式的仓库，格式是 `repo:<owner>/<repo>:environment:sae`。 [阿里云 RAM 角色配置](https://help.aliyun.com/zh/ram/user-guide/create-a-ram-role-for-a-trusted-idp) · [GitHub OIDC subject](https://docs.github.com/en/actions/reference/security/oidc)
3. 角色需要 `cr:GetAuthorizationToken`，以及发布仓库的 `cr:PullRepository`、`cr:PushRepository`。SAE 权限需要 `sae:ListApplications`、`sae:DescribeApplicationConfig`、`sae:DescribeApplicationStatus`、`sae:DeployApplication`、`sae:DescribeChangeOrder` 和 `sae:StartApplication`。GitHub 托管运行器需能连接 ACR 的公网推送地址，SAE 通过现有 VPC 拉取地址获取镜像。 [ACR 登录凭证](https://help.aliyun.com/zh/acr/user-guide/configure-access-credentials)
4. 在 GitHub 的 `sae` 环境添加以下变量。

| 变量 | 值 |
| --- | --- |
| `ALIYUN_OIDC_PROVIDER_ARN` | GitHub OIDC 身份提供商 ARN |
| `ALIYUN_ROLE_ARN` | 发布角色 ARN |
| `ACR_INSTANCE_ID` | 配置中推送仓库所属的 ACR 企业版实例 ID，格式为 `cri-…` |
| `SAE_PC_URL`（可选） | 管理端访问地址，例如 `https://yoga.example.com`。配置后，发布会检查登录页和验证码接口 |

## 用固定公网 IP 访问管理端

1. 在 SAE 控制台打开 `yoga-web-pc`，进入 **基础信息 → 应用访问设置 → 公网访问地址**。选择同地域已有的公网 CLB，或者选择新建 CLB，由 SAE 一次性购买并绑定。也可以从本地使用阿里云 CLI 调用 SLB `CreateLoadBalancer`（`AddressType=internet`）创建，再回到 SAE 选择该实例。公网 CLB 自带固定公网 IP。
2. 验证时可将 CLB 的 HTTP 监听端口 `80` 转发到容器端口 `80`。在浏览器打开 `http://<CLB 公网 IP>/login`，用项目现有的管理端账号登录。实际输入管理员密码时使用 HTTPS 监听，证书须覆盖浏览器使用的域名或 IP。管理端通过同源 `/prod-api/` 访问 VPC 内的后端。
3. 需要同时开放 H5 时，可以为 `yoga-web-h5` 使用独立的 CLB 监听端口，例如 `8081 → 80`。CLB 的创建和绑定是一次性操作，费用按阿里云的 CLB 计费规则计算。

[SAE 绑定 CLB](https://help.aliyun.com/zh/sae/sae-2-bind-clb-for-application) · [创建公网 CLB](https://help.aliyun.com/zh/slb/classic-load-balancer/user-guide/create-and-manage-a-clb-instance)

## 发布与回退

在 GitHub 选择 **Actions → Deploy to Alibaba Cloud SAE → Run workflow**，分支选 `master`。三个镜像推送成功后才会启动和更新应用。工作流会等待各次 SAE 发布单和启动单完成。完成后打开上面的管理端地址，验证登录及所需业务页面。

若中途失败，Actions 的运行摘要列出新旧镜像与已完成的发布单。确认各应用当前运行的版本后，可以在 SAE 控制台将受影响的应用部署回发布前的镜像。重跑工作流会使用独立镜像标签。
