---
name: verify-yoga
description: 在本地真实环境驱动瑜伽预约系统（uni-app H5 用户端为主，管理端为辅），按功能准备验收数据、像会员一样操作页面并截图留证。功能开发完成后要自证或为用户准备验收场景时使用，也用于“验收一下排队”“帮我准备验收数据”。
---

# verify-yoga

驱动本地开发环境里的真实应用：后端 `http://localhost:8080`、用户端 H5 `http://localhost:9090`、管理端 `http://localhost`。浏览器用 `agent-browser`（见 `.agents/skills/agent-browser`），数据准备走真实接口，只在接口不提供的地方（插入登录会员）直接写开发库。

本环境是用户自己的开发库，不是隔离环境。只增删本 skill 脚本名下的验收数据，不清库、不改种子数据。

## Launch

1. Docker Desktop 运行中，再启动本项目的数据库容器：

   ```bash
   docker start yoga-mysql yoga-redis
   until docker exec yoga-mysql mysqladmin ping -h127.0.0.1 -uroot -p123456 --silent; do sleep 2; done
   ```

   `exam-mysql`／`exam-redis` 等其他项目容器也绑定 3306／6379，不要启动它们。

2. 启动三个服务（会先 `mvn clean package` 打包当前分支，约 5～10 分钟）：

   ```bash
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w tools/dev.ps1)" -Action Start
   ```

   日志在 `.tmp/dev/<backend|pc|h5>.log`。就绪标志：`backend.log` 出现 `Started RuoYiApplication`，且三个地址都返回 200。命令输出 `Services are running` 后才会退出；以端口和日志为准。

3. 后端改动后要重新打包才生效：先 Teardown 再 Launch。

Teardown：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w tools/dev.ps1)" -Action Stop`（只停该脚本启动的进程）。数据库容器保持运行，用户也在用。

## Doctor

只读检查，任何异常先跑它：

```bash
for u in http://localhost:8080/ http://localhost/ http://localhost:9090/; do curl -s -o /dev/null -w "$u %{http_code}\n" "$u"; done
grep -E "Started RuoYiApplication" .tmp/dev/backend.log | tail -1
ls -l --time-style=+%F_%T backend/ruoyi-admin/target/ruoyi-admin.jar; git log -1 --format='HEAD %cd %h'
docker ps --format '{{.Names}} {{.Ports}}' | grep -E '3306|6379'
```

要求：三个 200；jar 时间晚于要验证的最后一次后端提交；3306／6379 由 `yoga-mysql`／`yoga-redis` 占用。jar 比提交旧时，页面跑的是旧后端，结论无效。

## Drive

uni-app H5 使用 hash 路由：`http://localhost:9090/#/pages/<page>/index?<query>`。固定一个会话名和手机视口：

```bash
B="agent-browser --session yoga-verify"
$B set viewport 390 844
```

稳定定位规则（本仓库实测）：
- `input` 的 placeholder 由 uni-app 渲染成独立元素，不能按 placeholder 选。用外层类名加类型，如 `.pa__input input[type=number]`。
- 页面主按钮多为 `view`，用类名点击：底部栏 `.bar__btn`、登录 `.pa__submit`、协议勾选 `.pa__agree-box`。
- 弹窗按钮、Tab 用文字：`$B find text "查看预约" click`、`$B find text "排队中" click`。
- 每次跳转后 `$B wait 2000`，再用 `$B get url` 确认真的到了目标页（未登录会被重定向到 `#/pages/auth/quick-login`）。

登录（会员页面的前置）：见 [features/login.md](features/login.md)。

## Evidence

- 截图放 `.verify/<功能>/`（已 gitignore，不提交），文件名用 `序号-看到的状态.png`。Cleanup 不删这个目录。
- 每一步同时留“动作 + 结果”：截图配一条 `get text`／`get url` 输出。
- 页面结果要用第二个视角核对副作用：功能脚本的 `status`（直接读库：名额、状态、卡剩余次数）或管理端列表。
- 走真实用户路径：页面点击或真实会员接口。不调测试专用接口，不拦截响应。e2e 里的响应拦截不算验收。

## Cleanup

```bash
node .agents/skills/verify-yoga/scripts/<功能>.mjs cleanup   # 删除该功能脚本造的数据
agent-browser --session yoga-verify close
```

服务和数据库容器是否停止由用户决定，默认保持运行。截图保留。

## Helpers

每个功能一个造数脚本，放 `scripts/<功能>.mjs`，统一子命令：

| 命令 | 作用 |
| --- | --- |
| `setup` | 先 cleanup 再造数，结尾打印 status，可重复执行 |
| `status` | 只读：打印该场景的名额、每个人的状态与卡余额 |
| `cleanup` | 只删本脚本固定 ID／手机号段的数据 |
| 功能专属命令 | 例如排队的 `cancel <手机号>`，用来扮演“其他会员”触发流程 |

新功能的脚本照 `scripts/queue.mjs` 写：会员用固定 ID 段（`1999…`）与手机号段（`1990000…`）直接插库，密码统一 `yoga1234`；会员卡、排课、预约都走管理端／会员端真实接口；新功能写进 `features/`。

## 给用户的验收交付

功能完成时，把验收场景交给用户：运行 `setup`，然后告诉用户验收账号、逐步操作和每步应看到的结果，以及触发流程的命令和 `cleanup` 命令。步骤从对应 `features/*.md` 抄。
