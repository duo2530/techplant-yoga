# TechPlant Yoga 管理端

瑜伽约课管理端 Web 应用，技术栈为 Vue 3 + Element Plus + Vite。脚手架来源：[RuoYi-Vue3](https://gitee.com/y_project/RuoYi-Vue3) v3.9.2。

## 命令行使用

在本目录执行以下命令

```powershell
npm install
npm run dev
```

| 命令 | 用途 |
| --- | --- |
| `npm run dev` | 启动开发服务，端口 80，`/dev-api` 代理到 `http://localhost:8080` |
| `npm run build:prod` | 生产构建，输出 `dist/` |
| `npm run build:stage` | staging 构建，读取 `.env.staging` |

代理目标在 `vite.config.js` 的 `baseUrl` 中配置。登录需要后端服务已在 8080 端口启动。

开发约定见 [AGENTS.md](AGENTS.md)，项目整体说明见[根目录 README](../../README.md)。
