import { mockResult, isEmpty } from '@/mock/config'
import { COURSE_TYPES, BOOKING_DATES, courses, courseSchedules } from '@/mock/course'
import { coaches } from '@/mock/coach'
import { stores } from '@/mock/store'
import { getCurrentStoreId } from './store'
import { toScheduleCard } from './home'

/** 课程类型（团课 / 精品课 / 私教课 / 特色课） */
export function getCourseTypes() {
  return mockResult(COURSE_TYPES)
}

/** 约课页的 7 天日期条 */
export function getBookingDates() {
  return mockResult(BOOKING_DATES)
}

/**
 * 约课页场次列表（mock）
 *
 * @param {Object} params { date, type, storeId } —— 不传表示不限
 */
export function listSchedules(params = {}) {
  const storeId = params.storeId || getCurrentStoreId()
  let list = courseSchedules.filter((item) => item.storeId === storeId)
  if (params.date) {
    list = list.filter((item) => item.date === params.date)
  }
  if (params.type) {
    list = list.filter((item) => {
      const course = courses.find((c) => String(c.id) === String(item.courseId))
      return course && course.type === Number(params.type)
    })
  }
  return mockResult(list.map(toScheduleCard))
}

/**
 * 课程详情（mock）：课程 + 教练 + 门店 + 该课程的场次
 */
export function getCourseDetail(courseId) {
  const course = courses.find((item) => String(item.id) === String(courseId)) || null
  if (!course) {
    return mockResult(null)
  }
  const coach = coaches.find((item) => String(item.id) === String(course.coachId)) || null
  const store = stores.find((item) => item.id === getCurrentStoreId()) || stores[0]
  const schedules = courseSchedules
    .filter((item) => item.courseId === course.id)
    .map(toScheduleCard)
  return mockResult({ course, coach, store, schedules })
}

/**
 * 提交课程预约（mock）：只做成功返回，不落库
 */
export function bookSchedule(scheduleId) {
  const schedule = courseSchedules.find((item) => String(item.id) === String(scheduleId))
  if (!schedule) {
    return mockResult({ success: false, message: '场次不存在' })
  }
  const left = schedule.capacity - schedule.booked
  if (left <= 0) {
    return mockResult({ success: false, message: '该场次已约满' })
  }
  return mockResult({ success: true, message: '预约成功', bookingId: 1000 + Number(scheduleId) })
}

/** 空态开关（页面可用它决定是否渲染空态；一般不需要，api 会直接返回空数组） */
export function isHomeEmpty() {
  return isEmpty('homeTodayCourses')
}
