const STORAGE_KEY = 'techplant-yoga-management-prototype-v1'

const initialData = {
  stores: [
    { id: 3001, storeNo: '001', name: '一水瑜伽·徐汇店', region: '上海市上海市徐汇区', provinceCode: '310000', cityCode: '310100', districtCode: '310104', address: '漕溪北路 88 号 3 层', phone: '021-55556666', businessType: 1, storeType: 1, businessHours: '周一至周日 09:00-22:00', status: 1, createTime: '2026-09-20 09:00:00', updateTime: '2026-09-29 08:30:00' },
    { id: 3002, storeNo: '002', name: '一水瑜伽·静安店', region: '上海市上海市静安区', provinceCode: '310000', cityCode: '310100', districtCode: '310106', address: '南京西路 1688 号 5 层', phone: '021-55557777', businessType: 1, storeType: 2, businessHours: '周一至周日 10:00-21:30', status: 1, createTime: '2026-09-22 09:00:00', updateTime: '2026-09-28 18:20:00' }
  ],
  classrooms: [
    { id: 8001, storeId: 3001, storeName: '一水瑜伽·徐汇店', name: '普拉提教室', capacity: 12, remark: '器械普拉提', status: 1 },
    { id: 8002, storeId: 3001, storeName: '一水瑜伽·徐汇店', name: '私教室', capacity: 2, remark: '一对一训练', status: 1 },
    { id: 8003, storeId: 3002, storeName: '一水瑜伽·静安店', name: '瑜伽教室', capacity: 16, remark: '团课教室', status: 1 }
  ],
  courses: [
    { id: 1001, name: '普拉提核心塑形', type: 2, difficulty: 2, coverUrl: null, intro: '通过器械和垫上训练强化核心，改善身体控制能力。', durationMin: 60, sortNo: 10, status: 1, createTime: '2026-09-20 10:00:00', updateTime: '2026-09-29 09:30:00' },
    { id: 1002, name: '流瑜伽基础', type: 1, difficulty: 2, coverUrl: null, intro: '以呼吸串联体式，适合具有基础运动经验的会员。', durationMin: 60, sortNo: 20, status: 1, createTime: '2026-09-21 11:00:00', updateTime: '2026-09-28 15:20:00' },
    { id: 1003, name: '一对一体态评估', type: 3, difficulty: 1, coverUrl: null, intro: '私教一对一体态评估与针对性训练。', durationMin: 75, sortNo: 30, status: 1, createTime: '2026-09-22 13:00:00', updateTime: '2026-09-27 17:10:00' }
  ],
  coaches: [
    { id: 2001, name: '林晓瑜', title: '普拉提主教练', avatarUrl: null, albumUrls: [], intro: '擅长普拉提核心训练、体态改善和产后恢复。', status: 1, createTime: '2026-09-20 10:00:00', updateTime: '2026-09-29 09:00:00' },
    { id: 2002, name: '周然', title: '瑜伽导师', avatarUrl: null, albumUrls: [], intro: '擅长流瑜伽、哈他瑜伽和呼吸练习。', status: 1, createTime: '2026-09-21 10:00:00', updateTime: '2026-09-28 16:00:00' }
  ],
  members: [
    { id: 5001, nickname: '陈小满', phone: '13800001001', level: 0, status: 1, joinTime: '2026-09-20 10:30:00', lastActiveTime: '2026-09-29 09:18:00' },
    { id: 5002, nickname: '李思雨', phone: '13800001002', level: 0, status: 1, joinTime: '2026-09-22 14:10:00', lastActiveTime: '2026-09-28 20:05:00' },
    { id: 5003, nickname: '王可', phone: '13800001003', level: 0, status: 0, joinTime: '2026-09-23 18:00:00', lastActiveTime: '2026-09-25 11:20:00' }
  ],
  memberCards: [
    { id: 6001, cardNo: '20260920001105000', storeId: 3001, storeNo: '001', memberId: 5001, memberName: '陈小满', phone: '13800001001', cardName: '普拉提 20 次卡', cardType: 1, courseScope: 1, initialCount: 20, remainingCount: 16, validDays: 365, status: 1, activateTime: '2026-09-20 11:00:00', startDate: '2026-09-20', endDate: '2027-09-19', createTime: '2026-09-20 10:50:00' },
    { id: 6002, cardNo: '20260920001105500', storeId: 3001, storeNo: '001', memberId: 5001, memberName: '陈小满', phone: '13800001001', cardName: '私教 10 次卡', cardType: 1, courseScope: 4, initialCount: 10, remainingCount: 8, validDays: 365, status: 1, activateTime: '2026-09-20 11:05:00', startDate: '2026-09-20', endDate: '2027-09-19', createTime: '2026-09-20 10:55:00' },
    { id: 6003, cardNo: '20260922002145000', storeId: 3002, storeNo: '002', memberId: 5002, memberName: '李思雨', phone: '13800001002', cardName: '普拉提月卡', cardType: 2, courseScope: 1, initialCount: null, remainingCount: null, validDays: 30, status: 1, activateTime: '2026-09-22 15:00:00', startDate: '2026-09-22', endDate: '2026-10-21', createTime: '2026-09-22 14:50:00' },
    { id: 6004, cardNo: '20260924001090000', storeId: 3001, storeNo: '001', memberId: 5003, memberName: '王可', phone: '13800001003', cardName: '普拉提 10 次卡', cardType: 1, courseScope: 1, initialCount: 10, remainingCount: 10, validDays: 365, status: 0, activateTime: null, startDate: null, endDate: null, createTime: '2026-09-24 09:00:00' }
  ],
  schedules: [
    { id: 4001, storeId: 3001, storeName: '一水瑜伽·徐汇店', courseId: 1001, courseType: 2, courseName: '普拉提核心塑形', coachId: 2001, coachName: '林晓瑜', classroomId: 8001, classroomName: '普拉提教室', startTime: '2026-10-05 10:00:00', endTime: '2026-10-05 11:00:00', capacity: 12, bookingCount: 2, location: '普拉提教室', status: 1, createTime: '2026-09-25 10:00:00', updateTime: '2026-09-29 09:00:00' },
    { id: 4002, storeId: 3002, storeName: '一水瑜伽·静安店', courseId: 1002, courseType: 1, courseName: '流瑜伽基础', coachId: 2002, coachName: '周然', classroomId: 8003, classroomName: '瑜伽教室', startTime: '2026-10-05 19:00:00', endTime: '2026-10-05 20:00:00', capacity: 16, bookingCount: 1, location: '瑜伽教室', status: 1, createTime: '2026-09-25 10:10:00', updateTime: '2026-09-29 09:10:00' },
    { id: 4003, storeId: 3001, storeName: '一水瑜伽·徐汇店', courseId: 1003, courseType: 3, courseName: '一对一体态评估', coachId: 2001, coachName: '林晓瑜', classroomId: 8002, classroomName: '私教室', startTime: '2026-09-28 14:00:00', endTime: '2026-09-28 15:15:00', capacity: 2, bookingCount: 2, location: '私教室', status: 3, createTime: '2026-09-20 12:00:00', updateTime: '2026-09-28 16:00:00' }
  ],
  reservations: [
    { id: 7001, memberId: 5001, memberName: '陈小满', phone: '13800001001', scheduleId: 4001, courseName: '普拉提核心塑形', storeName: '一水瑜伽·徐汇店', coachName: '林晓瑜', classroomName: '普拉提教室', startTime: '2026-10-05 10:00:00', endTime: '2026-10-05 11:00:00', cardId: 6001, cardName: '普拉提 20 次卡', cardType: 1, deductionResult: '已扣 2 次', bookingCount: 2, status: 1, cancelReason: null, cancelSource: null, bookingTime: '2026-09-29 09:20:00', checkInTime: null },
    { id: 7002, memberId: 5002, memberName: '李思雨', phone: '13800001002', scheduleId: 4002, courseName: '流瑜伽基础', storeName: '一水瑜伽·静安店', coachName: '周然', classroomName: '瑜伽教室', startTime: '2026-10-05 19:00:00', endTime: '2026-10-05 20:00:00', cardId: 6003, cardName: '普拉提月卡', cardType: 2, deductionResult: '期限内有效', bookingCount: 1, status: 1, cancelReason: null, cancelSource: null, bookingTime: '2026-09-28 19:30:00', checkInTime: null },
    { id: 7003, memberId: 5001, memberName: '陈小满', phone: '13800001001', scheduleId: 4003, courseName: '一对一体态评估', storeName: '一水瑜伽·徐汇店', coachName: '林晓瑜', classroomName: '私教室', startTime: '2026-09-28 14:00:00', endTime: '2026-09-28 15:15:00', cardId: 6002, cardName: '私教 10 次卡', cardType: 1, deductionResult: '已扣 2 次', bookingCount: 2, status: 3, cancelReason: null, cancelSource: null, bookingTime: '2026-09-25 12:00:00', checkInTime: '2026-09-28 13:52:00' }
  ]
}

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}

function loadData() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (!saved) return clone(initialData)
    const parsed = JSON.parse(saved)
    const merged = clone(initialData)
    Object.keys(merged).forEach(key => {
      if (Array.isArray(parsed[key])) merged[key] = parsed[key]
    })
    merged.stores.forEach((store, index) => { if (!store.storeNo) store.storeNo = String(index + 1).padStart(3, '0') })
    merged.memberCards.forEach(card => {
      if (!card.storeId) card.storeId = 3001
      const courseScopeCodes = { '团课、精品课': 1, 团课: 1, 精品课: 2, 特色课: 3, 私教课: 4 }
      card.courseScope = courseScopeCodes[card.courseScope] || Number(card.courseScope)
      const store = merged.stores.find(item => String(item.id) === String(card.storeId)) || merged.stores[0]
      card.storeNo = card.storeNo || store?.storeNo || '001'
      if (!card.cardNo) card.cardNo = `${String(card.createTime || now()).replace(/\D/g, '').slice(0, 8)}${card.storeNo}${String(card.createTime || now()).replace(/\D/g, '').slice(8, 14)}`
    })
    merged.schedules.forEach(schedule => {
      if (schedule.courseType === undefined || schedule.courseType === null) {
        schedule.courseType = merged.courses.find(course => String(course.id) === String(schedule.courseId))?.type
      }
      if (!schedule.classroomId) {
        const classroom = merged.classrooms.find(item => String(item.storeId) === String(schedule.storeId))
        schedule.classroomId = classroom?.id
        schedule.classroomName = classroom?.name || schedule.location
        schedule.location = schedule.classroomName
      }
    })
    merged.reservations.forEach(reservation => {
      delete reservation.courseType
      if (!reservation.classroomName) reservation.classroomName = merged.schedules.find(item => String(item.id) === String(reservation.scheduleId))?.classroomName || ''
    })
    return merged
  } catch (error) {
    console.warn('读取原型数据失败，已使用初始数据。', error)
    return clone(initialData)
  }
}

let database = loadData()

function saveData() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(database))
}

function now() {
  const date = new Date()
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function today() {
  return now().slice(0, 10)
}

function addDays(dateText, days) {
  const date = new Date(`${dateText}T00:00:00`)
  date.setDate(date.getDate() + Number(days) - 1)
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function nextId(items) {
  return items.reduce((max, item) => Math.max(max, Number(item.id) || 0), 0) + 1
}

function contains(source, keyword) {
  return !keyword || String(source || '').toLowerCase().includes(String(keyword).trim().toLowerCase())
}

function equals(source, expected) {
  return expected === undefined || expected === null || expected === '' || Number(source) === Number(expected)
}

function page(items, params = {}) {
  const pageNum = Math.max(Number(params.pageNum) || 1, 1)
  const pageSize = Math.max(Number(params.pageSize) || 10, 1)
  const start = (pageNum - 1) * pageSize
  return { code: 200, rows: clone(items.slice(start, start + pageSize)), total: items.length }
}

function list(resource, params = {}) {
  const items = database[resource].filter(item => {
    if (resource === 'courses') return contains(item.name, params.name) && equals(item.type, params.type) && equals(item.status, params.status)
    if (resource === 'coaches') return contains(item.name, params.name) && equals(item.status, params.status)
    if (resource === 'stores') return contains(item.name, params.name) && contains(item.region, params.region) && equals(item.businessType, params.businessType) && equals(item.storeType, params.storeType) && equals(item.status, params.status)
    if (resource === 'classrooms') return equals(item.storeId, params.storeId) && contains(item.name, params.name) && equals(item.status, params.status)
    if (resource === 'members') return contains(item.nickname, params.nickname) && contains(item.phone, params.phone) && equals(item.status, params.status)
    if (resource === 'memberCards') return contains(item.memberName, params.memberName) && contains(item.phone, params.phone) && contains(item.cardName, params.cardName) && contains(item.cardNo, params.cardNo) && equals(item.cardType, params.cardType) && equals(item.status, params.status)
    if (resource === 'schedules') return (!params.date || item.startTime.startsWith(params.date)) && equals(item.courseType, params.courseType) && equals(item.storeId, params.storeId) && equals(item.courseId, params.courseId) && equals(item.coachId, params.coachId) && equals(item.status, params.status)
    if (resource === 'reservations') return contains(item.memberName, params.memberName) && contains(item.phone, params.phone) && contains(item.courseName, params.courseName) && contains(item.storeName, params.storeName) && (!params.date || item.startTime.startsWith(params.date)) && equals(item.status, params.status)
    return true
  })
  const rows = resource === 'schedules' ? items.map(scheduleView) : items
  return page(rows, params)
}

function scheduleView(schedule) {
  const course = database.courses.find(item => String(item.id) === String(schedule.courseId))
  return { ...clone(schedule), courseName: course?.name || schedule.courseName, courseType: schedule.courseType ?? course?.type }
}

function detail(resource, id) {
  const item = database[resource].find(row => String(row.id) === String(id))
  if (!item) throw new Error('未找到对应数据')
  const data = clone(item)
  if (resource === 'members') data.cards = clone(database.memberCards.filter(card => String(card.memberId) === String(id)))
  if (resource === 'schedules') return { code: 200, data: scheduleView(item) }
  return { code: 200, data }
}

function create(resource, payload) {
  const timestamp = now()
  const item = { ...clone(payload), id: nextId(database[resource]), status: 1, createTime: timestamp, updateTime: timestamp }
  if (resource === 'stores') item.storeNo = String(nextId(database.stores) - 3000).padStart(3, '0')
  if (resource === 'classrooms') {
    const store = database.stores.find(row => String(row.id) === String(payload.storeId))
    if (!store) throw new Error('请选择有效门店')
    item.storeName = store.name
  }
  database[resource].unshift(item)
  saveData()
  return { code: 200, data: clone(item), msg: '操作成功' }
}

function update(resource, id, payload) {
  const index = database[resource].findIndex(row => String(row.id) === String(id))
  if (index < 0) throw new Error('未找到对应数据')
  database[resource][index] = { ...database[resource][index], ...clone(payload), id: database[resource][index].id, updateTime: now() }
  if (resource === 'classrooms') {
    const store = database.stores.find(row => String(row.id) === String(database[resource][index].storeId))
    database[resource][index].storeName = store?.name || ''
  }
  saveData()
  return { code: 200, data: clone(database[resource][index]), msg: '操作成功' }
}

function createMemberCard(payload) {
  const courseScope = Number(payload.courseScope)
  if (!Number.isInteger(courseScope) || courseScope < 1 || courseScope > 4) throw new Error('请选择有效适用课种')
  const member = database.members.find(item => String(item.id) === String(payload.memberId))
  if (!member) throw new Error('请选择有效会员')
  const store = database.stores.find(item => String(item.id) === String(payload.storeId))
  if (!store) throw new Error('请选择开卡门店')
  const stamp = now().replace(/\D/g, '')
  const item = {
    ...clone(payload), courseScope, id: nextId(database.memberCards), cardNo: `${stamp.slice(0, 8)}${store.storeNo}${stamp.slice(8, 14)}`, storeNo: store.storeNo, storeId: store.id, storeName: store.name, memberId: member.id, memberName: member.nickname, phone: member.phone,
    initialCount: Number(payload.cardType) === 1 ? Number(payload.initialCount) : null,
    remainingCount: Number(payload.cardType) === 1 ? Number(payload.initialCount) : null,
    status: 0, activateTime: null, startDate: null, endDate: null, createTime: now()
  }
  database.memberCards.unshift(item)
  saveData()
  return { code: 200, data: clone(item), msg: '开卡成功' }
}

function activateMemberCard(id) {
  const card = database.memberCards.find(item => String(item.id) === String(id))
  if (!card) throw new Error('未找到会员卡')
  if (card.status !== 0) throw new Error('只有未激活会员卡可以激活')
  card.status = 1
  card.activateTime = now()
  card.startDate = today()
  card.endDate = addDays(card.startDate, card.validDays)
  saveData()
  return { code: 200, data: clone(card), msg: '激活成功' }
}

function schedulePayload(payload) {
  const store = database.stores.find(item => String(item.id) === String(payload.storeId))
  const course = database.courses.find(item => String(item.id) === String(payload.courseId))
  const coach = database.coaches.find(item => String(item.id) === String(payload.coachId))
  const classroom = database.classrooms.find(item => String(item.id) === String(payload.classroomId))
  if (!store || !course || !coach || !classroom || String(classroom.storeId) !== String(store.id)) throw new Error('请选择同一门店下有效的教室、课程和教练')
  if (new Date(payload.endTime) <= new Date(payload.startTime)) throw new Error('结束时间必须晚于开始时间')
  return { ...clone(payload), storeId: store.id, storeName: store.name, courseId: course.id, courseName: course.name, courseType: course.type, coachId: coach.id, coachName: coach.name, classroomId: classroom.id, classroomName: classroom.name, location: classroom.name }
}

function createSchedule(payload) {
  return create('schedules', { ...schedulePayload(payload), bookingCount: 0 })
}

function updateSchedule(id, payload) {
  const schedule = database.schedules.find(item => String(item.id) === String(id))
  if (!schedule) throw new Error('未找到排班')
  if (Number(payload.capacity) < Number(schedule.bookingCount)) throw new Error('总容量不能小于已预约人数')
  return update('schedules', id, schedulePayload(payload))
}

function refundCard(reservation) {
  if (Number(reservation.cardType) !== 1) return
  const card = database.memberCards.find(item => String(item.id) === String(reservation.cardId))
  if (card) card.remainingCount = Number(card.remainingCount || 0) + Number(reservation.bookingCount || 0)
}

function cancelSchedule(id) {
  const schedule = database.schedules.find(item => String(item.id) === String(id))
  if (!schedule) throw new Error('未找到排班')
  if (schedule.status !== 1) throw new Error('只有待上课排班可以取消')
  schedule.status = 2
  schedule.updateTime = now()
  database.reservations.filter(item => String(item.scheduleId) === String(id) && item.status === 1).forEach(item => {
    item.status = 2
    item.cancelReason = '排班取消'
    item.cancelSource = '排班取消'
    item.cancelTime = now()
    refundCard(item)
  })
  schedule.bookingCount = 0
  saveData()
  return { code: 200, msg: '排班已取消，关联预约已同步取消' }
}

function cancelReservation(id, payload) {
  const reservation = database.reservations.find(item => String(item.id) === String(id))
  if (!reservation) throw new Error('未找到预约')
  if (reservation.status !== 1) throw new Error('只有已预约记录可以取消')
  const diff = new Date(reservation.startTime).getTime() - Date.now()
  if (diff < 2 * 60 * 60 * 1000) throw new Error('开课前两小时内不能取消预约')
  reservation.status = 2
  reservation.cancelReason = payload.reason
  reservation.cancelSource = '门店代取消'
  reservation.cancelTime = now()
  refundCard(reservation)
  const schedule = database.schedules.find(item => String(item.id) === String(reservation.scheduleId))
  if (schedule) schedule.bookingCount = Math.max(0, Number(schedule.bookingCount) - Number(reservation.bookingCount))
  saveData()
  return { code: 200, msg: '取消成功' }
}

function checkInReservation(id) {
  const reservation = database.reservations.find(item => String(item.id) === String(id))
  if (!reservation) throw new Error('未找到预约')
  if (reservation.status !== 1) throw new Error('只有已预约记录可以签到')
  reservation.status = 3
  reservation.checkInTime = now()
  saveData()
  return { code: 200, msg: '签到成功' }
}

const routeDefinitions = [
  { basePath: '/admin/classrooms', resource: 'classrooms' },
  { basePath: '/admin/member-cards', resource: 'memberCards' },
  { basePath: '/admin/reservations', resource: 'reservations' },
  { basePath: '/admin/schedules', resource: 'schedules' },
  { basePath: '/admin/members', resource: 'members' },
  { basePath: '/admin/courses', resource: 'courses' },
  { basePath: '/admin/coaches', resource: 'coaches' },
  { basePath: '/admin/stores', resource: 'stores' }
]

export function requestPrototype(config) {
  const method = String(config.method || 'get').toLowerCase()
  const path = String(config.url || '').split('?')[0]
  const route = routeDefinitions.find(item => path === item.basePath || path.startsWith(item.basePath + '/'))

  return new Promise((resolve, reject) => {
    window.setTimeout(() => {
      try {
        if (!route) throw new Error(`原型接口尚未实现：${method.toUpperCase()} ${path}`)
        const suffix = path.slice(route.basePath.length).replace(/^\//, '')
        const segments = suffix ? suffix.split('/') : []
        let result

        if (method === 'get' && segments.length === 0) result = list(route.resource, config.params)
        else if (method === 'get' && segments.length === 1) result = detail(route.resource, segments[0])
        else if (method === 'post' && segments.length === 0 && route.resource === 'memberCards') result = createMemberCard(config.data || {})
        else if (method === 'post' && segments.length === 0 && route.resource === 'schedules') result = createSchedule(config.data || {})
        else if (method === 'post' && segments.length === 0) result = create(route.resource, config.data || {})
        else if (method === 'put' && segments.length === 1 && route.resource === 'schedules') result = updateSchedule(segments[0], config.data || {})
        else if (method === 'put' && segments.length === 1) result = update(route.resource, segments[0], config.data || {})
        else if (method === 'put' && segments[1] === 'status') result = update(route.resource, segments[0], { status: Number(config.data?.status ?? config.params?.status) })
        else if (method === 'put' && segments[1] === 'activate' && route.resource === 'memberCards') result = activateMemberCard(segments[0])
        else if (method === 'put' && segments[1] === 'cancel' && route.resource === 'schedules') result = cancelSchedule(segments[0])
        else if (method === 'put' && segments[1] === 'cancel' && route.resource === 'reservations') result = cancelReservation(segments[0], config.data || {})
        else if (method === 'put' && segments[1] === 'check-in' && route.resource === 'reservations') result = checkInReservation(segments[0])
        else throw new Error(`原型接口尚未实现：${method.toUpperCase()} ${path}`)
        resolve(result)
      } catch (error) {
        reject(error)
      }
    }, 120)
  })
}
