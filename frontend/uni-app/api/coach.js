import request from '@/utils/request'
import { toId, toText, toImageUrl, toNum } from '@/utils/public-api'

/** 教练公开接口：金牌列表、公开资料与教练场景排课。 */

const PUBLIC_REQUEST = { headers: { isToken: false } }

const MAX_PAGE_SIZE = 100

/** 归一化教练公开摘要（首页卡片与详情共用字段） */
function normalizeSummary(row) {
  const item = row || {}
  return {
    id: toId(item.id),
    name: toText(item.name),
    avatarUrl: toImageUrl(item.avatarUrl),
    intro: toText(item.intro)
  }
}

function normalizeDetail(row) {
  const item = normalizeSummary(row)
  const raw = row || {}
  const gallery = Array.isArray(raw.gallery) ? raw.gallery : []
  return Object.assign({}, item, {
    gallery: gallery.map(toImageUrl).filter((url) => !!url)
  })
}

/** 归一化 ScheduleCardVO 中教练页展示需要的字段 */
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
    classroomName: toText(item.classroomName),
    maxPersons: toNum(item.maxPersons),
    bookedPersons: toNum(item.bookedPersons),
    remainingPlaces: toNum(item.remainingPlaces),
    status: item.status,
    statusText: toText(item.statusText),
    bookable: item.bookable === true
  }
}

/**
 * 全部金牌教练 —— GET /api/coaches/featured
 *
 * @returns {Promise<Array>} 平台范围内全部金牌教练，按后端返回顺序（教练 ID 降序）
 */
export function getFeaturedCoaches() {
  return request(Object.assign({}, PUBLIC_REQUEST, {
    url: '/api/coaches/featured',
    method: 'get'
  })).then((res) => {
    if (!res || !Array.isArray(res.data)) {
      // 响应格式异常不能伪装成空列表，交给首页显示失败并可重试
      return Promise.reject(new Error('金牌教练接口返回格式异常'))
    }
    return res.data.map(normalizeSummary)
  })
}

/**
 * 教练公开资料 —— GET /api/coaches/{coachId}
 *
 * 教练不存在或已删除时后端返回业务码 404，由页面区分「不存在」与网络失败。
 *
 * @returns {Promise<Object|null>} 头像、姓名、简介与相册（相册保持后端顺序）
 */
export function getCoachDetail(coachId) {
  return request(Object.assign({}, PUBLIC_REQUEST, {
    url: '/api/coaches/' + encodeURIComponent(toId(coachId)),
    method: 'get'
  })).then((res) => {
    return res && res.data ? normalizeDetail(res.data) : null
  })
}

/**
 * 教练在当前门店、未来 14 天内的排课 —— GET /api/coaches/{coachId}/schedules
 *
 * @param {Object} params { coachId, storeId, courseType, pageNum, pageSize }
 * @returns {Promise<{list: Array, total: number}>}
 */
export function listCoachSchedules(params = {}) {
  const pageSize = Math.min(Number(params.pageSize) || 10, MAX_PAGE_SIZE)
  return request(Object.assign({}, PUBLIC_REQUEST, {
    url: '/api/coaches/' + encodeURIComponent(toId(params.coachId)) + '/schedules',
    method: 'get',
    params: {
      storeId: params.storeId || '',
      courseType: params.courseType || '',
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
