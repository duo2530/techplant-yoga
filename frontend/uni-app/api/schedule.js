import request from '@/utils/request'
import { toId, toText, toImageUrl, toNum } from '@/utils/public-api'

/** 用户端排课查询接口。 */

const PUBLIC_REQUEST = { headers: { isToken: false } }

const MAX_PAGE_SIZE = 100

function normalizeCard(row) {
  const item = row || {}
  return {
    id: toId(item.id),
    courseName: toText(item.courseName),
    courseCoverUrl: toImageUrl(item.courseCoverUrl),
    courseType: item.courseType,
    courseTypeName: toText(item.courseTypeName),
    difficulty: item.difficulty,
    scheduleDate: toText(item.scheduleDate),
    startTime: toText(item.startTime),
    endTime: toText(item.endTime),
    coachName: toText(item.coachName),
    storeId: toId(item.storeId),
    storeName: toText(item.storeName),
    classroomName: toText(item.classroomName),
    maxPersons: toNum(item.maxPersons),
    bookedPersons: toNum(item.bookedPersons),
    remainingPlaces: toNum(item.remainingPlaces),
    status: item.status,
    statusText: toText(item.statusText),
    bookable: item.bookable === true
  }
}

/** 归一化 ScheduleDetailVO（比卡片多课程/教练/人数信息） */
function normalizeDetail(row) {
  const item = normalizeCard(row)
  const raw = row || {}
  return Object.assign({}, item, {
    courseId: toId(raw.courseId),
    courseIntro: toText(raw.courseIntro),
    coachId: toId(raw.coachId),
    coachAvatarUrl: toImageUrl(raw.coachAvatarUrl),
    coachIntro: toText(raw.coachIntro),
    storeAddress: toText(raw.storeAddress),
    minPersons: toNum(raw.minPersons),
    bookers: Array.isArray(raw.bookers) ? raw.bookers : []
  })
}

/**
 * 约课页排课列表（分页）—— GET /api/schedules
 *
 * @param {Object} params { storeId, courseType, date, pageNum, pageSize }
 * @returns {Promise<{list: Array, total: number}>}
 */
export function listSchedules(params = {}) {
  const pageSize = Math.min(Number(params.pageSize) || 10, MAX_PAGE_SIZE)
  return request(Object.assign({}, PUBLIC_REQUEST, {
    url: '/api/schedules',
    method: 'get',
    params: {
      storeId: params.storeId || '',
      courseType: params.courseType || '',
      date: params.date || '',
      pageNum: params.pageNum || 1,
      pageSize: pageSize
    }
  })).then((res) => {
    const rows = res && Array.isArray(res.rows) ? res.rows : []
    return {
      list: rows.map(normalizeCard),
      total: Number((res && res.total) || 0)
    }
  })
}

/**
 * 首页今日可约团课 —— GET /api/schedules/today-bookable
 *
 * 后端只返回当前门店今天未开始且有名额的团课，最多 2 条；无场次时 data 为空数组。
 * 响应格式异常不能伪装成空列表，交给首页显示失败并可重试。
 *
 * @param {string} storeId 当前门店 ID
 * @returns {Promise<Array>}
 */
export function listTodayBookable(storeId) {
  return request(Object.assign({}, PUBLIC_REQUEST, {
    url: '/api/schedules/today-bookable',
    method: 'get',
    params: { storeId: toId(storeId) }
  })).then((res) => {
    if (!res || !Array.isArray(res.data)) {
      return Promise.reject(new Error('今日可约团课接口返回格式异常'))
    }
    return res.data.map((row) => {
      const item = row || {}
      return {
        id: toId(item.id),
        courseName: toText(item.courseName),
        coachName: toText(item.coachName),
        startTime: toText(item.startTime),
        endTime: toText(item.endTime),
        classroomName: toText(item.classroomName),
        bookedPersons: toNum(item.bookedPersons),
        maxPersons: toNum(item.maxPersons)
      }
    })
  })
}

/**
 * 课程详情（排课详情）—— GET /api/schedules/{scheduleId}
 *
 * 排课不存在 / 已删除 / 处于「待上架」时后端一律返回 404（同一个响应），
 * 因此这里 reject 由页面兜底：给友好提示并返回上一页。
 */
export function getScheduleDetail(scheduleId) {
  return request(Object.assign({}, PUBLIC_REQUEST, {
    url: '/api/schedules/' + encodeURIComponent(toId(scheduleId)),
    method: 'get'
  })).then((res) => {
    return res && res.data ? normalizeDetail(res.data) : null
  })
}
