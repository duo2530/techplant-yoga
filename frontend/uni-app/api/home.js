import { mockResult, isEmpty } from '@/mock/config'
import { stores } from '@/mock/store'
import { notices } from '@/mock/message'
import { coaches } from '@/mock/coach'
import { courses, courseSchedules } from '@/mock/course'
import { getCurrentStoreId } from './store'

/** 首页「今日可约团课」的日期（原型首页展示的就是当天） */
const TODAY = '2026-09-20'

/**
 * 场次 → 展示用卡片数据（course + coach 在 api 层拼好，页面不用自己 join）
 */
export function toScheduleCard(schedule) {
  const course = courses.find((item) => String(item.id) === String(schedule.courseId)) || {}
  const coach = coaches.find((item) => String(item.id) === String(schedule.coachId)) || {}
  const left = Math.max(schedule.capacity - schedule.booked, 0)
  return {
    id: schedule.id,
    courseId: schedule.courseId,
    courseName: course.name,
    courseType: course.type,
    difficulty: course.difficulty,
    durationMin: course.durationMin,
    coverUrl: course.coverUrl,
    tag: (course.tags && course.tags[0]) || '',
    coachId: schedule.coachId,
    coachName: coach.name || '',
    coachAvatar: coach.avatar || '',
    storeId: schedule.storeId,
    date: schedule.date,
    startTime: schedule.startTime,
    endTime: schedule.endTime,
    room: schedule.room,
    capacity: schedule.capacity,
    booked: schedule.booked,
    left,
    soldOut: left <= 0
  }
}

/**
 * 首页聚合数据（mock）
 *
 * 返回：当前门店、公告、今日可约团课、热门课程、金牌教练
 * 其中「今日可约团课 / 热门课程 / 公告」默认按原型显示空态（见 mock/config.js 的 MOCK.empty）
 */
export function getHomeData(params = {}) {
  const storeId = params.storeId || getCurrentStoreId()
  const store = stores.find((item) => item.id === storeId) || stores[0]
  const todaySchedules = isEmpty('homeTodayCourses')
    ? []
    : courseSchedules.filter((item) => item.date === TODAY && item.storeId === store.id).map(toScheduleCard)
  const hotCourses = isEmpty('homeHotCourses') ? [] : courses.slice(0, 4)
  const noticeList = isEmpty('homeNotices') ? [] : notices
  return mockResult({
    store,
    notices: noticeList,
    todaySchedules,
    hotCourses,
    coaches: coaches.slice(0, 3)
  })
}

/** 公告栏文案（快捷方法：只要第一条） */
export function getNoticeText() {
  return mockResult(isEmpty('homeNotices') ? '' : (notices[0] ? notices[0].content : ''))
}
