import request from '@/utils/request'
import { mockResult } from '@/mock/config'
import { getMemberInfo } from '@/utils/auth'
import { isSessionValid } from '@/utils/session'
import { logoutMember } from '@/api/auth'
import {
  userInfo,
  guestInfo,
  memberBenefits,
  coupons,
  giftPacks,
  pointsRecords,
  contracts,
  bodyTests,
  profileMenus
} from '@/mock/user'

/** 我的会员卡分页上限 */
const MAX_PAGE_SIZE = 100

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

/** 雪花 ID 一律当字符串：19 位数字转 Number 会塌缩 */
function toId(value) {
  return value === null || value === undefined ? '' : String(value)
}

function toText(value) {
  return value === null || value === undefined ? '' : String(value)
}

/** 可空字段：保持 null 语义，不把 null 变成 0 或空串 */
function toNullableNumber(value) {
  return value === null || value === undefined || value === '' ? null : Number(value)
}

/** 归一化 MemberCardPublicItemVO（只做类型与空值兜底，不新增或推导后端没给的字段） */
function normalizeMemberCard(row) {
  const item = row || {}
  return {
    id: toId(item.id),
    cardNo: toText(item.cardNo),
    cardName: toText(item.cardName),
    cardType: toNullableNumber(item.cardType),
    cardTypeText: toText(item.cardTypeText),
    cardCategoryText: toText(item.cardCategoryText),
    status: toNullableNumber(item.status),
    statusText: toText(item.statusText),
    courseScope: toNullableNumber(item.courseScope),
    courseScopeName: toText(item.courseScopeName),
    storeName: toText(item.storeName),
    storeScopeText: toText(item.storeScopeText),
    purchaseTimes: toNullableNumber(item.purchaseTimes),
    giftTimes: toNullableNumber(item.giftTimes),
    totalTimes: toNullableNumber(item.totalTimes),
    remainingTimes: toNullableNumber(item.remainingTimes),
    remainingDays: toNullableNumber(item.remainingDays),
    activateTime: toText(item.activateTime),
    validStartTime: toText(item.validStartTime),
    validEndTime: toText(item.validEndTime)
  }
}

/**
 * 我的会员卡（分页）—— GET /api/member-cards
 *
 * 会员身份只取服务端登录态，客户端不传会员 ID，也不按当前门店过滤。
 *
 * @param {Object} params { pageNum, pageSize }
 * @returns {Promise<Array>} 会员卡数组，total 属性为后端返回的总条数。
 */
export function listMemberCards(params = {}) {
  const pageSize = Math.min(Number(params.pageSize) || 10, MAX_PAGE_SIZE)
  return request({
    url: '/api/member-cards',
    method: 'get',
    params: {
      pageNum: params.pageNum || 1,
      pageSize: pageSize
    }
  }).then((res) => {
    const rows = res && Array.isArray(res.rows) ? res.rows : []
    const list = rows.map(normalizeMemberCard)
    list.total = Number((res && res.total) || 0)
    return list
  })
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
