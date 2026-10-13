import request from '@/utils/request'
import { agreements, termsContent, privacyContent } from '@/mock/auth'

/**
 * 用户端账号接口（**走真实后端**）
 *
 * 契约见《用户端接口详细设计》§2.2.4～§2.2.8，后端在 ruoyi-yoga 的 member 模块：
 *   POST /api/auth/sms-code      发送验证码（scene: register／reset）
 *   POST /api/auth/register      注册（成功不签发 token）
 *   POST /api/auth/login         登录（返回 7 天会员 token）
 *   POST /api/auth/reset-password 忘记密码
 *   POST /api/auth/logout        退出登录（幂等）
 *
 * 【返回与错误口径】
 *   直接返回后端的 `{code, msg, data}`：`code === 200` 进 `.then`；
 *   其余业务码由 `@/utils/request` 统一 toast 并 reject，页面 `catch` 里什么都不用做
 *   （这就是脚手架的既有约定，页面只负责成功分支）。
 *
 * 【字段映射】页面表单用的是 `code`，后端 DTO 字段名是 `smsCode`（§2.6.5），在本层转换。
 *
 * 【免登录接口】一律传 `headers.isToken = false`：登录前不该带旧 token（避免带着过期会员 token 请求）。
 *
 * 协议正文仍是本地内容（原型逐字稿），见 `@/mock/auth`；这两个函数不属于后端接口。
 */

/** 手机号 + 密码登录 → data: {token, expireTime, memberId, memberName} */
export function loginByPhone(params = {}) {
  return request({
    url: '/api/auth/login',
    method: 'post',
    headers: { isToken: false },
    data: {
      phone: params.phone,
      password: params.password
    }
  })
}

/** 发送短信验证码：scene 取 register（注册）／reset（忘记密码） */
export function sendSmsCode(phone, scene = 'register') {
  return request({
    url: '/api/auth/sms-code',
    method: 'post',
    headers: { isToken: false },
    data: { phone, scene }
  })
}

/** 注册：验证码字段由页面表单的 `code` 映射为后端的 `smsCode` */
export function register(params = {}) {
  return request({
    url: '/api/auth/register',
    method: 'post',
    headers: { isToken: false },
    data: {
      phone: params.phone,
      smsCode: params.code,
      password: params.password
    }
  })
}

/** 忘记密码：仅已注册手机号可重置，覆盖 BCrypt 哈希 */
export function resetPassword(params = {}) {
  return request({
    url: '/api/auth/reset-password',
    method: 'post',
    headers: { isToken: false },
    data: {
      phone: params.phone,
      smsCode: params.code,
      password: params.password
    }
  })
}

/** 退出登录：撤销当前会员会话（token 缺失／失效也返回成功） */
export function logoutMember() {
  return request({
    url: '/api/auth/logout',
    method: 'post'
  })
}

/**
 * 微信快捷登录：**三期交付**，二期只做手机号路径。
 *
 * 这里不再返回「假的登录成功」：真机上没有后端 code2Session 就换不来 token，
 * 假装成功只会让页面进了首页却仍是未登录态。页面按 `success: false` 提示即可。
 */
export function loginByWechat() {
  return Promise.resolve({ success: false, message: '微信快捷登录暂未开放，请使用手机号登录' })
}

/** 登录页要展示的协议 */
export function getAgreements() {
  return Promise.resolve(agreements)
}

/** 协议正文（按类型取） */
export function getAgreementContent(type) {
  return Promise.resolve(type === 'privacy' ? privacyContent : termsContent)
}
