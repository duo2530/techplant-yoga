# 用 GitHub Actions 发布到 SAE

工作流会从 `master` 当前提交构建后端、管理端和 H5 镜像，推送到现有 ACR 企业版仓库，更新 SAE 上的三个业务应用。发布使用仓库里已有的 [SAE 配置](../../.claude/skills/yoga-cloud-deploy/config/sae.config.json) 定位地域、命名空间和应用。MySQL 和 Redis 停机时会先启动，后续按后端、管理端、H5 的顺序发布。PR 检查只构建镜像和验证 Nginx 配置。

## 一次性配置 GitHub 与阿里云

1. 在 GitHub 仓库的 **Settings → Environments** 创建 `sae`，部署分支限制为 `master`。
2. 在阿里云 RAM 创建 GitHub OIDC 身份提供商和用于发布的角色。签发者为 `https://token.actions.githubusercontent.com`，受众为 `sts.aliyuncs.com`。角色信任策略用 `StringEquals` 匹配此仓库的 `sae` 环境 subject。使用不可变 subject 的仓库，格式是 `repo:<owner>@<owner_id>/<repo>@<repo_id>:environment:sae`。使用旧格式的仓库，格式是 `repo:<owner>/<repo>:environment:sae`。 [阿里云 RAM 角色配置](https://help.aliyun.com/zh/ram/user-guide/create-a-ram-role-for-a-trusted-idp) · [GitHub OIDC subject](https://docs.github.com/en/actions/reference/security/oidc)
3. 角色需要 `cr:GetAuthorizationToken`，以及发布仓库的 `cr:PullRepository`、`cr:PushRepository`。SAE 权限需要 `sae:ListApplications`、`sae:DescribeApplicationConfig`、`sae:DescribeApplicationStatus`、`sae:DeployApplication`、`sae:DescribeChangeOrder`、`sae:StartApplication`、`sae:DescribeApplicationSlbs` 和 `sae:BindSlb`。GitHub 托管运行器需能连接 ACR 的公网推送地址，SAE 通过现有 VPC 拉取地址获取镜像。 [ACR 登录凭证](https://help.aliyun.com/zh/acr/user-guide/configure-access-credentials)
4. 在 GitHub 的 `sae` 环境添加以下变量。

| 变量 | 值 |
| --- | --- |
| `ALIYUN_OIDC_PROVIDER_ARN` | GitHub OIDC 身份提供商 ARN |
| `ALIYUN_ROLE_ARN` | 发布角色 ARN |
| `ACR_INSTANCE_ID` | 配置中推送仓库所属的 ACR 企业版实例 ID，格式为 `cri-…` |

## 获取管理端的公网 IP

发布完成后，工作流从 `yoga-web-pc` 读取当前公网绑定。有可用绑定时复用，缺少绑定时由 SAE 代购按使用量计费的公网 CLB，并将公网端口 `80` 转发到管理端容器端口 `80`。工作流从公网验证登录页和同源验证码接口，然后在本次运行的 **Summary → 公网访问** 列出登录链接、IP 和 CLB 实例 ID。

点击链接后使用项目已有的管理端账号登录。先修改默认密码。自动新建的 IP 地址使用 HTTP，适合短时验收；长期使用管理员账号时，配置带有效证书的 HTTPS。公开仓库的 Actions 运行摘要可以被其他人查看。

用完后，在 SAE 的 `yoga-web-pc` **应用访问设置 → 公网访问地址** 删除绑定。SAE 代购的 CLB 会随解绑释放。自行购买的实例解绑后，在 CLB 控制台另行释放。若之前直接释放了 CLB，先在 SAE 移除残留绑定。下次发布会读取新分配的 IP。

[SAE 绑定 CLB](https://help.aliyun.com/zh/sae/sae-2-bind-clb-for-application) · [CLB 配置约束与解绑](https://help.aliyun.com/zh/sae/clb-instructions-for-use-2-0)

## 发布与回退

在 GitHub 选择 **Actions → Deploy to Alibaba Cloud SAE → Run workflow**，分支选 `master`。三个镜像推送成功后才会启动和更新应用。工作流会等待各次 SAE 发布单和启动单完成。打开本次运行摘要的管理端登录链接，验证登录及所需业务页面。

若中途失败，Actions 的运行摘要列出新旧镜像与已完成的发布单。确认各应用当前运行的版本后，可以在 SAE 控制台将受影响的应用部署回发布前的镜像。重跑工作流会使用独立镜像标签。
