import { getMemberInfo, getToken, removeMemberInfo, removeToken, setMemberInfo, setToken } from '@/utils/auth'

/** 管理会员本地会话的有效性、写入与清理。 */

/** 登录页路径（401、会话过期、退出登录统一回这里） */
export const LOGIN_PAGE = '/pages/auth/quick-login'

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

export function isValidLoginPayload(data) {
  return !!(data && data.token && data.memberId && data.expireTime)
}

export function saveSession(data, phone) {
  setToken(data.token)
  setMemberInfo({
    memberId: data.memberId,
    memberName: data.memberName,
    phone,
    expireTime: Number(data.expireTime)
  })
}

/** 仅清理持久化会话缓存。 */
export function clearSessionStorage() {
  removeToken()
  removeMemberInfo()
}
