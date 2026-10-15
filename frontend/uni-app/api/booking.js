import request from '@/utils/request'
import config from '@/config'

/** 用户端预约接口：我的预约列表、取消、签到与提交预约。 */

const MAX_PAGE_SIZE = 100

/** 状态筛选：与后端的 status 取值一致 */
const STATUS_TABS = [
  { value: 0, label: '全部' },
  { value: 1, label: '已预约' },
  { value: 2, label: '已取消' },
  { value: 3, label: '已签到' }
]

/** 卡类型筛选：与后端的 cardType 取值一致 */
const CARD_TYPE_FILTERS = [
  { value: 0, label: '全部' },
  { value: 1, label: '次数卡' },
  { value: 2, label: '年卡' }
]

function toId(value) {
  return value === null || value === undefined ? '' : String(value)
}

function toText(value) {
  return value === null || value === undefined ? '' : String(value)
}

function toNum(value) {
  const n = Number(value)
  return isNaN(n) ? 0 : n
}

/** 封面可能是相对路径，按 baseUrl 拼成可直接渲染的地址 */
function toImageUrl(value) {
  const url = toText(value).trim()
  if (!url || /^(?:[a-z][a-z\d+.-]*:|\/\/|\/?static\/)/i.test(url)) {
    return url
  }
  return config.baseUrl.replace(/\/+$/, '') + '/' + url.replace(/^\/+/, '')
}

/** 完整时间（yyyy-MM-dd HH:mm:ss）取 HH:mm；长度不足时原样返回 */
function toHm(value) {
  const text = toText(value)
  return text.length >= 16 ? text.slice(11, 16) : text
}

/** 归一化 ReservationPublicItemVO，字段与后端契约一一对应 */
function normalizeBooking(row) {
  const item = row || {}
  return {
    id: toId(item.id),
    scheduleId: toId(item.scheduleId),
    courseId: toId(item.courseId),
    courseName: toText(item.courseName),
    courseCoverUrl: toImageUrl(item.courseCoverUrl),
    courseType: item.courseType,
    courseTypeName: toText(item.courseTypeName),
    coachName: toText(item.coachName),
    storeAddress: toText(item.storeAddress),
    scheduleDate: toText(item.scheduleDate),
    startTime: toText(item.startTime),
    endTime: toText(item.endTime),
    startHm: toHm(item.startTime),
    endHm: toHm(item.endTime),
    status: item.status,
    statusText: toText(item.statusText)
  }
}

/** 状态筛选 Tab（全部 / 已预约 / 已取消 / 已签到） */
export function getBookingStatusTabs() {
  return STATUS_TABS.slice()
}

/** 卡类型筛选选项（全部 / 次数卡 / 年卡） */
export function getCardTypeFilters() {
  return CARD_TYPE_FILTERS.slice()
}

/** 状态码 → 文案 */
export function bookingStatusLabel(status) {
  const hit = STATUS_TABS.find((item) => item.value === Number(status))
  return hit ? hit.label : ''
}

/**
 * 我的预约列表 —— GET /api/reservations
 *
 * @param {Object} params { status, cardType, pageNum, pageSize }
 * @returns {Promise<{list: Array, total: number}>}
 */
export function listBookings(params = {}) {
  const pageSize = Math.min(Number(params.pageSize) || 20, MAX_PAGE_SIZE)
  return request({
    url: '/api/reservations',
    method: 'get',
    params: {
      status: params.status || '',
      cardType: params.cardType || '',
      pageNum: params.pageNum || 1,
      pageSize: pageSize
    }
  }).then((res) => {
    const rows = res && Array.isArray(res.rows) ? res.rows : []
    return {
      list: rows.map(normalizeBooking),
      total: Number((res && res.total) || 0)
    }
  })
}

/** 取消预约 —— PUT /api/reservations/{reservationId}/cancel */
export function cancelBooking(reservationId) {
  return request({
    url: '/api/reservations/' + encodeURIComponent(toId(reservationId)) + '/cancel',
    method: 'put'
  }).then((res) => toText(res && res.msg))
}

/** 签到 —— PUT /api/reservations/{reservationId}/check-in */
export function checkInBooking(reservationId) {
  return request({
    url: '/api/reservations/' + encodeURIComponent(toId(reservationId)) + '/check-in',
    method: 'put'
  }).then((res) => toText(res && res.msg))
}

/**
 * 提交预约 —— POST /api/reservations
 *
 * requestId 由调用方生成并在同一笔预约的重试间复用，后端按它做幂等。
 */
export function createReservation(params = {}) {
  return request({
    url: '/api/reservations',
    method: 'post',
    data: {
      requestId: toText(params.requestId),
      scheduleId: toId(params.scheduleId),
      memberCardId: toId(params.memberCardId),
      persons: toNum(params.persons)
    }
  }).then((res) => toText(res && res.msg))
}

/** 生成 RFC4122 v4 请求号 */
export function generateRequestId() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}
