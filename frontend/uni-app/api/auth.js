import request from '@/utils/request'
import { agreements, termsContent, privacyContent } from '@/mock/auth'

/** 用户端账号接口。 */

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
