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

/** 会员资料展示，身份信息来自本地有效会话，权益数据来自 mock。 */
export function getUserProfile() {
  const member = isSessionValid() ? getMemberInfo() : null
  if (member) {
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
