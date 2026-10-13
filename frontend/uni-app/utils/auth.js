const TokenKey = 'App-Token'

/**
 * 会员登录信息（会员ID／显示名／登录手机号）
 *
 * 登录成功后由登录页写入，用来在「我的」等页面显示当前登录会员；
 * 与 token 分开存：token 用于请求鉴权，本对象只用于展示（服务端不认客户端传入的会员 ID）。
 */
const MemberKey = 'App-Member'

export function getToken() {
  return uni.getStorageSync(TokenKey)
}

export function setToken(token) {
  return uni.setStorageSync(TokenKey, token)
}

export function removeToken() {
  return uni.removeStorageSync(TokenKey)
}

export function getMemberInfo() {
  return uni.getStorageSync(MemberKey) || null
}

export function setMemberInfo(info) {
  return uni.setStorageSync(MemberKey, info)
}

export function removeMemberInfo() {
  return uni.removeStorageSync(MemberKey)
}
