import { mockResult, isEmpty } from '@/mock/config'
import { activities } from '@/mock/activity'

/**
 * 活动列表（mock）
 *
 * 原型「活动列表」页整屏是空态（浅蓝渐变 + 灰色图标 + 「暂无数据」），
 * 默认由 MOCK.empty.activities 控制；改成 false 会返回下面的 mock 数据。
 */
export function listActivities() {
  if (isEmpty('activities')) {
    return mockResult([])
  }
  return mockResult(activities)
}

/** 活动详情 */
export function getActivityDetail(activityId) {
  return mockResult(activities.find((item) => String(item.id) === String(activityId)) || null)
}
