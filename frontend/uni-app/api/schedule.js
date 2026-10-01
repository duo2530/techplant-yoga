import request from '@/utils/request'

/**
 * 排课接口（用户端 · 免登录只读）
 *
 * 契约唯一来源：`docs/详细设计/用户端接口详细设计.md` §2.2、§2.3
 *   1. GET /api/schedules                  参数 storeId / courseType / date / pageNum / pageSize
 *      → TableDataInfo{ total, rows: ScheduleCardVO[] }
 *   2. GET /api/schedules/{scheduleId}     → AjaxResult{ data: ScheduleDetailVO }
 *
 * 两条硬口径（后端已经施加，前端**不要重复过滤、也不要自己推导状态**）：
 *   - 可见口径 `status <> 1`：已上架 / 已结束 / 已取消都会返回，只有「待上架」不返回；
 *   - `statusText`（可约 / 已结束 / 已取消）与 `bookable` 由后端 `ScheduleStatusEnum` 推导。
 *
 * 雪花 ID 一律当字符串：19 位数字转 Number 会塌缩，比较时用 String(...)。
 */

/** 匿名请求：显式告诉 utils/request 不要带 token */
const PUBLIC_REQUEST = { headers: { isToken: false } }

const MAX_PAGE_SIZE = 100

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

/** 归一化 ScheduleCardVO（原样透传后端字段，只做空值与类型兜底） */
function normalizeCard(row) {
  const item = row || {}
  return {
    id: toId(item.id),
    courseName: toText(item.courseName),
    courseCoverUrl: toText(item.courseCoverUrl),
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
    coachAvatarUrl: toText(raw.coachAvatarUrl),
    coachIntro: toText(raw.coachIntro),
    minPersons: toNum(raw.minPersons),
    // 一期没有预约：后端固定返回 []（不是 null）；这里保证页面拿到的一定是数组
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
