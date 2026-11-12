# TechPlant Yoga 用户端

基于 Vue 3 的 uni-app 工程，支持 npm 命令行开发与构建，目标平台为 H5 和微信小程序。

## 命令行使用

需要 Node.js >= 20.19。在本目录执行以下命令

```powershell
npm ci
npm run dev:h5
```

| 命令 | 用途 | 地址或输出目录 |
| --- | --- | --- |
| `npm run dev:h5` | 启动 H5 开发服务 | `http://localhost:9090` |
| `npm run dev:mp-weixin` | 微信小程序开发，监听源码更新 | `dist/dev/mp-weixin` |
| `npm run build:h5` | H5 生产构建 | `dist/build/h5` |
| `npm run build:mp-weixin` | 微信小程序生产构建 | `dist/build/mp-weixin` |
| `npm run check:static` | 页面、组件与资源静态检查 | 终端输出 |
| `npm run check:request` | 请求与会话辅助自检 | 终端输出 |

H5 默认端口被占用时，先停止已有服务，或执行 `npm run dev:h5 -- --port 9091`。开发服务配置统一在 `vite.config.js`。微信开发者工具导入对应的输出目录进行预览、调试和上传；开发命令只负责持续编译，不会自动启动开发者工具。

后端地址配置在 `config.js`；真机和发布使用可访问的 HTTPS 地址，并在微信公众平台配置合法域名。微信 AppID 配置在 `manifest.json`。

源码保留在工程根目录，脚本通过 `UNI_INPUT_DIR=.` 指定入口。DCloud 编译依赖保持统一版本，依赖安装结果由 `package-lock.json` 锁定。修改依赖使用 `npm install`，复现安装使用 `npm ci`。HBuilderX 仍可打开本目录。

CLI 用法参考 [uni-app 官方文档](https://uniapp.dcloud.io/quickstart-cli)。编译和辅助自检不能替代端到端测试与微信真机验收，测试要求见 [AI Agent测试手册](../../docs/系统测试/AI%20Agent测试手册.md)。

脚手架来源：RuoYi-App v1.2.0（Vue3 + Pinia）。
