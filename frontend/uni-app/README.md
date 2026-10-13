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

## 脚手架来源

<p align="center">
	<img alt="logo" src="https://oscimg.oschina.net/oscnet/up-43e3941654fa3054c9684bf53d1b1d356a1.png">
</p>
<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">RuoYi v1.2.0</h1>
<h4 align="center">基于UniApp开发的轻量级移动端框架</h4>
<p align="center">
	<a href="https://gitee.com/y_project/RuoYi-App/stargazers"><img src="https://gitee.com/y_project/RuoYi-App/badge/star.svg?theme=dark"></a>
	<a href="https://gitee.com/y_project/RuoYi-App"><img src="https://img.shields.io/badge/RuoYi-v1.2.0-brightgreen.svg"></a>
	<a href="https://gitee.com/y_project/RuoYi-App/blob/master/LICENSE"><img src="https://img.shields.io/github/license/mashape/apistatus.svg"></a>
</p>

## 平台简介

RuoYi App 移动解决方案，采用uniapp框架，一份代码多终端适配，同时支持APP、小程序、H5！实现了与[RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)、[RuoYi-Cloud](https://gitee.com/y_project/RuoYi-Cloud)完美对接的移动解决方案！目前已经实现登录、我的、工作台、编辑资料、头像修改、密码修改、常见问题、关于我们等基础功能。

* 本仓库为前端技术栈 Vue3 + Pinia 版本。
* 配套后端代码仓库地址[RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) 或 [RuoYi-Cloud](https://github.com/yangzongzhuan/RuoYi-Cloud) 版本。
* 应用框架基于[uniapp](https://uniapp.dcloud.net.cn/)，支持小程序、H5、Android和IOS。
* 前端组件采用[uni-ui](https://github.com/dcloudio/uni-ui)，全端兼容的高性能UI框架。
* 阿里云折扣场：[点我进入](http://aly.ruoyi.vip)，腾讯云秒杀场：[点我进入](http://txy.ruoyi.vip)&nbsp;&nbsp;

## 技术文档

- 官网网站：[http://ruoyi.vip](http://ruoyi.vip)
- 文档地址：[http://doc.ruoyi.vip](http://doc.ruoyi.vip)
- H5页体验：[http://h5.ruoyi.vip](http://h5.ruoyi.vip)
- QQ交流群： ①133713780(满)、②146013835(满)、③189091635
- 小程序体验

<img src="https://oscimg.oschina.net/oscnet/up-26c76dc90b92acdbd9ac8cd5252f07c8ad9.jpg" alt="小程序演示"/>

## 演示图

<table>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-21f6f842fdc94540469b4eb43fdadbaf7f8.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-a6f23cf9a371a30165e135eff6d9ae89a9d.png"/></td>
		<td><img src="https://oscimg.oschina.net/oscnet/up-ff5f62016bf6624c1ff27eee57499dccd44.png"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-b9a582fdb26ec69d407fabd044d2c8494df.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-96427ee08fca29d77934cfc8d1b1a637cef.png"/></td>
		<td><img src="https://oscimg.oschina.net/oscnet/up-5fdadc582d24cccd7727030d397b63185a3.png"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-0a36797b6bcc50c36d40c3c782665b89efc.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-d77995cc00687cedd00d5ac7d68a07ea276.png"/></td>
		<td><img src="https://oscimg.oschina.net/oscnet/up-fa8f5ab20becf59b4b38c1b92a9989e7109.png"/></td>
    </tr>
</table>
