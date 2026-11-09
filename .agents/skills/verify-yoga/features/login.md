# 会员登录

会员用手机号和密码登录用户端 H5，登录后才能预约、排队和查看我的预约。

## Sub-features

- `login-password` 手机号密码登录。
- `login-redirect` 未登录访问会员页面时跳到登录页。

## How to get to it (user POV)

- 打开“我的”或任何需要登录的操作，跳到 `#/pages/auth/quick-login`，再选手机号登录。
- 直接打开 `http://localhost:9090/#/pages/auth/phone-auth`。

## Driving it with agent-browser

Preconditions:

- 账号已存在且有密码，例如排队脚本 `setup` 后的 `19900000005`／`yoga1234`。

- **打开登录页。** 运行 `$B open "http://localhost:9090/#/pages/auth/phone-auth"` 和 `$B wait 1500`。页面有数字输入框和密码框。
- **填写。** 运行 `$B fill ".pa__input input[type=number]" 19900000005` 和 `$B fill ".pa__input input[type=password]" yoga1234`。
- **勾选协议。** 运行 `$B click ".pa__agree-box"`。
- **登录。** 运行 `$B click ".pa__submit"` 和 `$B wait 2500`。`$B get url` 返回 `http://localhost:9090/#/`。

## Gotchas

- 不勾协议点登录只会弹“请先阅读并同意协议”，URL 不变，看起来像登录失败。
- 按 placeholder 选输入框会报 Element not found，placeholder 是独立渲染的元素。
- 登录态存在浏览器会话里。换账号前先 `$B close` 或用新的 `--session`。
