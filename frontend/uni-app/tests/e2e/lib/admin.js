const { config } = require('./config')
const { SkipError } = require('./assert')

/**
 * 隔离后端的真实管理端接口。
 *
 * 端到端用例的教练、课程、教室与排课数据都通过这些接口准备、清理，不使用 mock 列表。
 * 验证码需在隔离环境关闭（sys.account.captchaEnabled=false），否则抛出 SkipError。
 */

const ADMIN_USERNAME = process.env.E2E_ADMIN_USERNAME || 'admin'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'admin123'

/** 本次测试运行的唯一后缀：测试名与教练名不重复，便于按名反查新建记录 */
const RUN_SUFFIX = createRunSuffix()

function createRunSuffix() {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  const stamp = `${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  const random = Math.floor(Math.random() * 900 + 100)
  return `${stamp}${random}`
}

function buildQuery(params) {
  const query = new URLSearchParams()
  Object.keys(params || {}).forEach((key) => {
    const value = params[key]
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, value)
    }
  })
  const text = query.toString()
  return text ? '?' + text : ''
}

async function readJson(url, options) {
  const response = await fetch(url, options)
  const body = await response.json()
  return { response, body }
}

function pad2(value) {
  return value < 10 ? '0' + value : '' + value
}

function formatDate(date) {
  return date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate())
}

function formatDateTime(date) {
  return formatDate(date) + ' ' + pad2(date.getHours()) + ':' + pad2(date.getMinutes()) + ':' + pad2(date.getSeconds())
}

/** 未来第 dayOffset 天的指定时刻 */
function futureDateTime(dayOffset, hour, minute) {
  const date = new Date()
  date.setHours(0, 0, 0, 0)
  date.setDate(date.getDate() + dayOffset)
  date.setHours(hour, minute, 0, 0)
  return date
}

function assertOk(response, body, label) {
  if (!body || body.code !== 200) {
    throw new Error(`${label}失败：HTTP ${response.status} ${body ? body.code : ''} ${body ? body.msg : ''}`)
  }
}

async function apiAdminLogin() {
  const captcha = await readJson(config.apiBase + '/captchaImage')
  if (captcha.body && captcha.body.captchaEnabled) {
    throw new SkipError('隔离后端未关闭验证码（sys.account.captchaEnabled=true），无法通过管理端接口准备测试数据')
  }
  const login = await readJson(config.apiBase + '/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: ADMIN_USERNAME, password: ADMIN_PASSWORD, code: '', uuid: '' })
  })
  if (!login.body || login.body.code !== 200 || !login.body.token) {
    throw new SkipError(
      `管理端登录失败（${ADMIN_USERNAME}）：${login.body ? login.body.msg : login.response.status}；` +
      '请检查 E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD')
  }
  return login.body.token
}

/** 公开只读接口：期望值直接取自同一后端 */
async function publicFeatured() {
  const result = await readJson(config.apiBase + '/api/coaches/featured')
  assertOk(result.response, result.body, '查询金牌教练')
  return result.body.data || []
}

async function publicCoach(coachId) {
  return readJson(config.apiBase + '/api/coaches/' + encodeURIComponent(coachId))
}

async function publicCoachSchedules(coachId, params) {
  const result = await readJson(
    config.apiBase + '/api/coaches/' + encodeURIComponent(coachId) + '/schedules' + buildQuery(params))
  assertOk(result.response, result.body, '查询教练排课')
  return { rows: result.body.rows || [], total: Number(result.body.total || 0) }
}

async function publicStores() {
  const result = await readJson(config.apiBase + '/api/stores' + buildQuery({ pageNum: 1, pageSize: 100 }))
  assertOk(result.response, result.body, '查询门店')
  return result.body.rows || []
}

/**
 * 创建一个管理端夹具；所有新建记录都登记在 created 里，cleanup() 逐层回收。
 *
 * @returns {Promise<Object>} 管理端数据准备与公开查询方法
 */
async function createAdminFixture() {
  const token = await apiAdminLogin()
  const created = { schedules: [], coaches: [], courses: [], classrooms: [] }

  async function call(method, path, options = {}) {
    const headers = { 'Content-Type': 'application/json' }
    if (token) {
      headers.Authorization = 'Bearer ' + token
    }
    const result = await readJson(config.apiBase + path + buildQuery(options.params), {
      method,
      headers,
      body: options.data === undefined ? undefined : JSON.stringify(options.data)
    })
    assertOk(result.response, result.body, `管理端接口 ${method.toUpperCase()} ${path} `)
    return result.body
  }

  async function listAll(path, params) {
    const body = await call('get', path, { params: Object.assign({ pageNum: 1, pageSize: 100 }, params) })
    return { rows: body.rows || [], total: Number(body.total || 0) }
  }

  /** 反查失败、需要 cleanup 再尝试回收的记录 */
  const pendingCleanups = []

  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

  /** 按唯一条件重试反查：请求异常或未命中都重试，返回命中记录或 null */
  async function retryLookup(label, lookup, attempts = 3, delayMs = 150) {
    let lastError = null
    for (let attempt = 1; attempt <= attempts; attempt++)
    {
      try
      {
        const hit = await lookup()
        if (hit)
        {
          return hit
        }
      }
      catch (error)
      {
        lastError = error
      }
      if (attempt < attempts)
      {
        await sleep(delayMs)
      }
    }
    if (lastError)
    {
      console.warn(`[fixture] ${label} 反查请求失败：${lastError.message}`)
    }
    return null
  }

  /**
   * 创建后登记。
   *
   * 先按唯一条件反查；反查失败（请求异常或查询不到）时重试查询并直接删除已落库记录（兜底回收）；
   * 兜底仍失败则登记 pending 交给 cleanup 再试，并抛错让用例失败，避免静默残留。
   */
  async function trackCreated(options) {
    const { label, lookup, remove, register } = options
    const hit = await retryLookup(label, lookup)
    if (hit)
    {
      register(hit)
      return hit
    }

    const rollback = async (attempts, delayMs) => {
      const found = await retryLookup(label, lookup, attempts, delayMs)
      if (!found)
      {
        return false
      }
      await remove(found)
      return true
    }

    let recovered = false
    try
    {
      recovered = await rollback(5, 300)
    }
    catch (error)
    {
      console.warn(`[fixture] ${label} 兜底回收失败：${error.message}`)
    }
    if (!recovered)
    {
      pendingCleanups.push({ label, cleanup: () => rollback(5, 500) })
      throw new Error(`${label} 创建后反查失败，已登记待清理；cleanup 仍无法回收时用例失败`)
    }
    throw new Error(`${label} 创建后反查失败，已按唯一条件兜底删除；用例失败以避免使用未知 ID`)
  }

  async function adminStores() {
    return (await listAll('/admin/stores')).rows
  }

  /** 新建一门无封面课程：保证卡片走课程占位，且课名可与其他用例区分 */
  async function createCourse(courseType, options = {}) {
    const name = options.name || `E2E课程${courseType}-${RUN_SUFFIX}`
    await call('post', '/admin/courses', {
      data: {
        name,
        courseType,
        coverUrl: options.coverUrl || null,
        intro: '端到端测试课程',
        difficulty: options.difficulty || 2
      }
    })
    const hit = await trackCreated({
      label: `课程「${name}」`,
      lookup: async () => {
        const rows = (await listAll('/admin/courses', { name })).rows
        return rows.filter((row) => row.name === name)[0] || null
      },
      remove: (row) => call('delete', '/admin/courses/' + String(row.id)),
      register: (row) => created.courses.push(String(row.id))
    })
    return { id: String(hit.id), name }
  }

  async function ensureClassroom(storeId) {
    const rows = (await listAll('/admin/classrooms', { storeId })).rows
    if (rows.length) {
      // 使用该门店已有的教室，只登记、不删除
      return { id: String(rows[0].id), name: rows[0].name }
    }
    const name = `E2E教室-${RUN_SUFFIX}`
    await call('post', '/admin/classrooms', { data: { storeId: String(storeId), name } })
    const hit = await trackCreated({
      label: `教室「${name}」`,
      lookup: async () => {
        const createdRows = (await listAll('/admin/classrooms', { storeId, name })).rows
        return createdRows.filter((row) => row.name === name)[0] || null
      },
      remove: (row) => call('delete', '/admin/classrooms/' + String(row.id)),
      register: (row) => created.classrooms.push(String(row.id))
    })
    return { id: String(hit.id), name: hit.name }
  }

  async function createCoach(options = {}) {
    const name = options.name || `E2E教练-${RUN_SUFFIX}`
    await call('post', '/admin/coaches', {
      data: {
        name,
        avatarUrl: options.avatarUrl || null,
        intro: options.intro || '',
        phone: '13800000000',
        gallery: options.gallery || []
      }
    })
    const hit = await trackCreated({
      label: `教练「${name}」`,
      lookup: async () => {
        const rows = (await listAll('/admin/coaches', { name })).rows
        return rows.filter((row) => row.name === name)[0] || null
      },
      remove: (row) => deleteCoach(String(row.id)),
      register: (row) => created.coaches.push(String(row.id))
    })
    const coachId = String(hit.id)
    if (options.featured !== false) {
      await call('put', '/admin/coaches/' + coachId + '/level', { params: { level: 2 } })
    }
    return { id: coachId, name }
  }

  async function setCoachLevel(coachId, level) {
    await call('put', '/admin/coaches/' + String(coachId) + '/level', { params: { level: level } })
  }

  async function deleteCoach(coachId) {
    await call('delete', '/admin/coaches/' + String(coachId))
  }

  /**
   * 新建排课并可选直接上架。
   *
   * @param {Object} options coachId、storeId、courseId、classroomId、dayOffset、hour、minute、duration、publish
   */
  async function createSchedule(options) {
    const start = futureDateTime(options.dayOffset === undefined ? 1 : options.dayOffset,
      options.hour === undefined ? 9 : options.hour,
      options.minute === undefined ? 0 : options.minute)
    const end = new Date(start.getTime() + (options.duration || 60) * 60000)
    const startText = formatDateTime(start)
    await call('post', '/admin/schedules', {
      data: {
        storeId: String(options.storeId),
        courseId: String(options.courseId),
        coachId: String(options.coachId),
        classroomId: String(options.classroomId),
        startTime: startText,
        endTime: formatDateTime(end),
        maxPersons: options.maxPersons || 20,
        minPersons: options.minPersons || 2
      }
    })
    const hit = await trackCreated({
      label: `排课「${startText} / 课程 ${options.courseId}」`,
      lookup: async () => {
        const rows = (await listAll('/admin/schedules', {
          coachId: options.coachId,
          storeId: options.storeId
        })).rows
        return rows.filter((row) => String(row.courseId) === String(options.courseId)
          && String(row.startTime) === startText)[0] || null
      },
      remove: (row) => deleteSchedule(String(row.id)),
      register: (row) => created.schedules.push(String(row.id))
    })
    const scheduleId = String(hit.id)
    if (options.publish !== false) {
      await call('put', '/admin/schedules/' + scheduleId + '/status', { params: { status: 2 } })
    }
    return scheduleId
  }

  async function deleteSchedule(scheduleId) {
    // 只有「待上架」允许删除：先把已上架/已取消的场次退回待上架
    try {
      await call('put', '/admin/schedules/' + String(scheduleId) + '/status', { params: { status: 1 } })
    } catch (error) {
      // 状态回退失败不阻断清理，继续尝试删除
    }
    await call('delete', '/admin/schedules/' + String(scheduleId))
  }

  async function cleanup() {
    const failures = []
    // 先处理反查失败、ID 未知的记录：重试反查并删除，仍无法回收则用例失败
    for (const pending of pendingCleanups.splice(0)) {
      try {
        const cleaned = await pending.cleanup()
        if (!cleaned) {
          failures.push(`${pending.label}：反查不到已创建记录，无法回收`)
        }
      } catch (error) {
        failures.push(`${pending.label}：${error.message}`)
      }
    }
    for (const scheduleId of created.schedules.slice().reverse()) {
      try {
        await deleteSchedule(scheduleId)
      } catch (error) {
        failures.push(`排课 ${scheduleId}：${error.message}`)
      }
    }
    for (const coachId of created.coaches.slice().reverse()) {
      try {
        await deleteCoach(coachId)
      } catch (error) {
        failures.push(`教练 ${coachId}：${error.message}`)
      }
    }
    for (const courseId of created.courses.slice().reverse()) {
      try {
        await call('delete', '/admin/courses/' + String(courseId))
      } catch (error) {
        failures.push(`课程 ${courseId}：${error.message}`)
      }
    }
    for (const classroomId of created.classrooms.slice().reverse()) {
      try {
        await call('delete', '/admin/classrooms/' + String(classroomId))
      } catch (error) {
        failures.push(`教室 ${classroomId}：${error.message}`)
      }
    }
    if (failures.length) {
      // 清理失败必须让用例失败：残留数据会污染后续运行与隔离库
      throw new Error('测试数据清理失败：\n  ' + failures.join('\n  '))
    }
  }

  return {
    token,
    runSuffix: RUN_SUFFIX,
    created,
    adminStores,
    publicStores,
    publicFeatured,
    publicCoach,
    publicCoachSchedules,
    createCourse,
    ensureClassroom,
    createCoach,
    setCoachLevel,
    deleteCoach,
    createSchedule,
    deleteSchedule,
    cleanup,
    /** 当前首页会自动选中的门店：公开门店列表第一项 */
    async currentStore() {
      const stores = await publicStores()
      return stores.length ? stores[0] : null
    }
  }
}

module.exports = {
  RUN_SUFFIX,
  apiAdminLogin,
  createAdminFixture,
  publicFeatured,
  publicCoach,
  publicCoachSchedules,
  publicStores
}
