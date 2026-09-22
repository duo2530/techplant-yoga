import { mockResult, MOCK } from '@/mock/config'
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
 * 个人信息（mock）
 *
 * `MOCK.loggedIn = false` 时返回游客态（暂未登录 / V0 普通会员 / 未绑定手机号），
 * 也就是原型「我的」页截图的状态。
 */
export function getUserProfile() {
  const profile = MOCK.loggedIn ? userInfo : guestInfo
  return mockResult({
    profile,
    loggedIn: !!MOCK.loggedIn,
    menus: profileMenus
  })
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

/** 退出登录（mock）：仅返回成功，由页面清本地状态 */
export function logout() {
  return mockResult({ success: true })
}
