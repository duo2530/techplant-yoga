import { getMemberInfo, getToken, removeMemberInfo, removeToken, setMemberInfo, setToken } from '@/utils/auth'

/**
 * 会员本地登录态（2026-10-13 复审新增）
 *
 * 【为什么要单独一层】
 * 后端签发的是 **7 天有效、不自动续期** 的会员 token（用户端接口详细设计 §2.2.6），
 * 但前端原来只判断「缓存里有没有 token」，既没存服务端给的 `expireTime`，
 * 也没有任何地方做过期检查 —— 结果 token 早就失效了，「我的」页仍显示已登录、
 * 还能进设置页（本地资料不请求后端，也就不会触发 401）。
 *
 * 这里把「读登录态 / 写登录态 / 清登录态」收成一处，供页面、请求层、user store 复用。
 *
 * ⚠️ 本地过期判断**只服务于展示与页面守卫**：最终鉴权始终在服务端
 * （请求鉴权见用户端接口详细设计 §4.7），本机时间不准也不影响安全。
 */

/** 登录页路径（401、会话过期、退出登录统一回这里） */
export const LOGIN_PAGE = '/pages/auth/quick-login'

/**
 * 本地登录态是否有效
 *
 * - 没有 token → 无效
 * - 会员信息里没有可用 `expireTime`（历史缓存 / 老版本写入的）→ 无效，要求重新登录
 * - `expireTime` 已过 → 无效
 */
export function isSessionValid() {
  if (!getToken()) {
    return false
  }
  const info = getMemberInfo() || {}
  const expireTime = Number(info.expireTime)
  if (!expireTime || !isFinite(expireTime)) {
    return false
  }
  return expireTime > Date.now()
}

/** 登录返回体是否完整：缺 token / memberId / expireTime 一律不能写缓存、不能提示成功 */
export function isValidLoginPayload(data) {
  return !!(data && data.token && data.memberId && data.expireTime)
}

/** 写入登录态：token + 会员展示信息 + 服务端下发的过期时间 */
export function saveSession(data, phone) {
  setToken(data.token)
  setMemberInfo({
    memberId: data.memberId,
    memberName: data.memberName,
    phone,
    expireTime: Number(data.expireTime)
  })
}

/** 只清存储层（token + 会员展示信息）；要同时复位 user store 请用 store 的 clearSession */
export function clearSessionStorage() {
  removeToken()
  removeMemberInfo()
}
