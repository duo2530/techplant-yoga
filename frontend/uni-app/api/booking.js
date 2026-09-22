import { mockResult, isEmpty } from '@/mock/config'
import { bookings, BOOKING_STATUS, BOOKING_COURSE_FILTERS, BOOKING_CARD_FILTERS } from '@/mock/booking'

/** 状态筛选 Tab（全部 / 已预约 / 已取消 / 已签到） */
export function getBookingStatusTabs() {
  return mockResult(BOOKING_STATUS)
}

/** 状态码 → 文案 */
export function bookingStatusLabel(status) {
  const hit = BOOKING_STATUS.find((item) => item.value === Number(status))
  return hit ? hit.label : ''
}

/** 课种 / 卡种下拉选项 */
export function getBookingFilters() {
  return mockResult({
    courseTypes: BOOKING_COURSE_FILTERS,
    cardTypes: BOOKING_CARD_FILTERS
  })
}

/**
 * 我的预约列表（mock）
 *
 * 原型「我的预约」页是空态（暂无符合条件的预约），默认由 MOCK.empty.bookings 控制；
 * 把开关改成 false 就会返回 mock 数据。
 *
 * @param {Object} params { status, courseType, cardType }
 */
export function listBookings(params = {}) {
  if (isEmpty('bookings')) {
    return mockResult([])
  }
  let list = bookings.slice()
  if (params.status) {
    list = list.filter((item) => item.status === Number(params.status))
  }
  return mockResult(list.map((item) => Object.assign({}, item, { statusLabel: bookingStatusLabel(item.status) })))
}

/** 预约详情 */
export function getBookingDetail(bookingId) {
  const booking = bookings.find((item) => String(item.id) === String(bookingId)) || null
  if (!booking) {
    return mockResult(null)
  }
  return mockResult(Object.assign({}, booking, { statusLabel: bookingStatusLabel(booking.status) }))
}

/** 取消预约（mock）：只返回成功，不落库 */
export function cancelBooking(bookingId) {
  const booking = bookings.find((item) => String(item.id) === String(bookingId))
  if (!booking) {
    return mockResult({ success: false, message: '预约不存在' })
  }
  if (booking.status === 3) {
    return mockResult({ success: false, message: '已签到的课程不能取消' })
  }
  return mockResult({ success: true, message: '已取消预约' })
}
