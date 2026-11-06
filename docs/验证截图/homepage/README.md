# 首页验证截图

对照 `docs/产品分析/用户端小程序截图/P1-首页(1).png`、`P1-首页(2).png`，用 agent-browser 在 390×844 视口下截取用户端 H5 首页。

| 文件 | 内容 |
| --- | --- |
| `01-首页首屏.png` | 门店卡（头图＋左下半透明信息浮层）、今日可约团课、热门课程 |
| `02-首页整页.png` | 整页滚动截图（底部 TabBar 为 fixed 元素，整页截图时出现在中间） |
| `03-金牌教练.png` | 金牌教练卡：方形头像、姓名、简介、「查看课程 >」、右侧装饰插画 |
| `04-换店弹层.png` | 点击「换店 >」打开门店选择弹层 |
| `05-换店后.png` | 选择「静安精品店」后门店卡即时更新 |
| `06-点击教练进入约教练页.png` | 点击金牌教练卡进入该教练的约教练页 |
| `07-Tailscale-HTTPS首页.png` | 通过 Tailscale HTTPS 入口访问的首页 |

## 演示数据

种子数据里没有今天的排课和金牌教练，首页各栏目会显示空态。`demo-data.sql` 只用于本地 `docker-compose.yml` 启动的开发库，按执行当天日期补充今日团课、热门课程和两位金牌教练，并把图片指向工程 `static` 目录：

```powershell
Get-Content docs/验证截图/homepage/demo-data.sql -Encoding UTF8 | docker exec -i yoga-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 yoga
```

## Tailscale HTTPS 访问

`tailscale serve` 只在 tailnet 内可见。页面与接口共用一个 HTTPS 域名，`/api` 转发到后端 8080，其余路径转发到 9091 上的 H5 构建产物：

```powershell
cd frontend/uni-app
$env:VITE_API_BASE_URL = 'https://desktop-cj4qj6d.tail2ac3e8.ts.net'
npx cross-env UNI_INPUT_DIR=. UNI_OUTPUT_DIR=../../.tmp/tailnet-build uni build -p h5
cd ../..
node tools/serve-h5-build.js .tmp/tailnet-build 9091

tailscale serve --bg --https=443 http://127.0.0.1:9091
tailscale serve --bg --https=443 --set-path=/api http://127.0.0.1:8080/api
tailscale serve --https=443 off   # 关闭
```

在 Git Bash 中执行带 `/api` 的命令时需加 `MSYS_NO_PATHCONV=1`，否则路径会被改写成 Windows 路径。
