import { mockResult } from '@/mock/config'
import { coaches } from '@/mock/coach'
import { courses, courseSchedules } from '@/mock/course'
import { toScheduleCard } from './home'

/**
 * 教练课程卡片的兜底封面
 *
 * mock 的课程都没有封面图（`course.coverUrl` 是空串），而原型「约教练」页的课程卡是有图的，
 * 用的就是约课页那张 YOGA 封面 → 这里统一兜底，页面不用自己判断。
 */
const COACH_COURSE_COVER = '/static/images/booking/cover-1.jpg'

/**
 * 教练列表（mock），支持按关键字过滤
 */
export function listCoaches(params = {}) {
  let list = coaches.slice()
  if (params.keyword) {
    const keyword = String(params.keyword)
    list = list.filter((item) => item.name.indexOf(keyword) > -1)
  }
  return mockResult(list)
}

/**
 * 教练详情（mock）：教练资料 + 相册 + 该教练的课程 + 该教练的场次
 *
 * `schedules` 是本次为「约教练」页**新增**的字段（原来只有 `coach` / `courses`）：
 * 原型课程卡上要显示「时间 09:15 - 10:15 / 团课教室 / 剩余 9」，
 * 这些来自排班（mock/course.js 的 courseSchedules），用 api/home.js 的 toScheduleCard 拼好。
 */
export function getCoachDetail(coachId) {
  const coach = coaches.find((item) => String(item.id) === String(coachId)) || null
  if (!coach) {
    return mockResult(null)
  }
  const coachCourses = courses.filter((item) => String(item.coachId) === String(coach.id))
  const coachSchedules = courseSchedules
    .filter((item) => item.coachId === coach.id)
    .map((item) => {
      const card = toScheduleCard(item)
      return Object.assign({}, card, { coverUrl: card.coverUrl || COACH_COURSE_COVER })
    })
  return mockResult({ coach, courses: coachCourses, schedules: coachSchedules })
}
