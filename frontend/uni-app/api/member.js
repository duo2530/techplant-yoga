import { mockResult } from '@/mock/config'
import { getMemberInfo } from '@/utils/auth'
import { isSessionValid } from '@/utils/session'
import { logoutMember } from '@/api/auth'
import {
  userInfo,
  guestInfo,
  memberBenefits,
  memberCards,
  coupons,
  giftPacks,
  pointsRecords,
  contracts,
  bodyTests,
  profileMenus
} from '@/mock/user'

/**
 * 个人信息
 *
 * **登录态是真的、会员资料仍是 mock**：`/api/auth/login` 已是真实后端，
 * 因此「已登录」一律以**本地有效会员会话**为准（`@/utils/session`：
 * 有 token、有服务端下发的 expireTime 且未过期），并用登录时保存的显示名与手机号
 * 覆盖 mock 用户资料（`@/utils/auth` 的 `App-Member`）。
 * 会员卡／优惠券／礼包／积分等域还没接后端，仍走 mock。
 *
 * **会话无效（无 token／缺过期时间／已过期）就是游客态**
 * （暂未登录 / V0 普通会员 / 未绑定手机号，即原型游客截图）：
 * token 过期后「我的」页必须回到游客态，不能一直显示已登录（2026-10-13 复审要求）。
 */
export function getUserProfile() {
  const member = isSessionValid() ? getMemberInfo() : null
  if (member) {
    // 用 JSON 深拷贝，避免改坏 mock 数据（工程里没有对象展开的既有写法，保持一致）
    const profile = JSON.parse(JSON.stringify(userInfo))
    if (member.memberName) {
      profile.nickname = member.memberName
    }
    if (member.phone) {
      profile.phone = member.phone
    }
    return mockResult({ profile, loggedIn: true, menus: profileMenus })
  }
  return mockResult({ profile: guestInfo, loggedIn: false, menus: profileMenus })
}

/** 会员权益（各等级权益包） */
export function listMemberBenefits() {
  return mockResult(memberBenefits)
}

/** 我的会员卡 */
export function listMemberCards() {
  return mockResult(memberCards)
}

/** 我的优惠券 */
export function listCoupons() {
  return mockResult(coupons)
}

/** 我的礼包 */
export function listGiftPacks() {
  return mockResult(giftPacks)
}

/** 积分明细 */
export function listPointsRecords() {
  return mockResult(pointsRecords)
}

/** 我的合同 */
export function listContracts() {
  return mockResult(contracts)
}

/** 我的体测记录 */
export function listBodyTests() {
  return mockResult(bodyTests)
}

/** 退出登录（真实后端，幂等）：撤销当前会员会话；本地登录态由调用方清理 */
export function logout() {
  return logoutMember()
}
