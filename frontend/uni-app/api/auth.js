import { mockResult } from '@/mock/config'
import { MOCK_SMS_CODE, agreements, termsContent, privacyContent } from '@/mock/auth'

/**
 * 微信快捷登录（mock）
 *
 * 真实实现要调 uni.login 拿 code 再换 token；mock 阶段直接返回成功，
 * 页面把它当登录成功处理（写本地 mock 登录态即可）。
 */
export function loginByWechat() {
  return mockResult({ success: true, token: 'mock-token-wechat', message: '登录成功' })
}

/**
 * 手机号 + 密码登录（mock）
 */
export function loginByPhone(params = {}) {
  if (!params.phone) {
    return mockResult({ success: false, message: '请输入手机号' })
  }
  if (!params.password) {
    return mockResult({ success: false, message: '请输入密码' })
  }
  return mockResult({ success: true, token: 'mock-token-phone', message: '登录成功' })
}

/**
 * 注册（mock）：验证码固定 8888
 */
export function register(params = {}) {
  if (!params.phone) {
    return mockResult({ success: false, message: '请输入手机号' })
  }
  if (params.code !== MOCK_SMS_CODE) {
    return mockResult({ success: false, message: '验证码错误（mock 验证码为 ' + MOCK_SMS_CODE + '）' })
  }
  if (!params.password) {
    return mockResult({ success: false, message: '请设置密码' })
  }
  return mockResult({ success: true, message: '注册成功' })
}

/**
 * 重置密码（mock）
 */
export function resetPassword(params = {}) {
  if (params.code !== MOCK_SMS_CODE) {
    return mockResult({ success: false, message: '验证码错误（mock 验证码为 ' + MOCK_SMS_CODE + '）' })
  }
  if (!params.password) {
    return mockResult({ success: false, message: '请设置新密码' })
  }
  return mockResult({ success: true, message: '密码已重置' })
}

/**
 * 发送短信验证码（mock）：不真发短信，固定回 8888
 */
export function sendSmsCode(phone) {
  if (!phone) {
    return mockResult({ success: false, message: '请输入手机号' })
  }
  return mockResult({ success: true, message: '验证码已发送（mock：' + MOCK_SMS_CODE + '）' })
}

/** 登录页要展示的协议 */
export function getAgreements() {
  return mockResult(agreements)
}

/** 协议正文（按类型取） */
export function getAgreementContent(type) {
  return mockResult(type === 'privacy' ? privacyContent : termsContent)
}
