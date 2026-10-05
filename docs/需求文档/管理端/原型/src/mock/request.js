const STORAGE_KEY = 'techplant-yoga-management-prototype-v2'

// 演示数据里的时间一律按“今天”偏移生成，这样「已过期／已用完／剩余天数」等派生状态不随运行日期失效。
const pad = value => String(value).padStart(2, '0')

function shiftDays(days) {
  const date = new Date()
  date.setDate(date.getDate() + Number(days))
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function stamp(offsetDays, time = '10:00:00') {
  return `${shiftDays(offsetDays)} ${time}`
}

// 有效期：起 = 开卡日 00:00:00；止 = 起 + (总天数 - 1) 天 23:59:59（含首尾日）
function validity(startOffsetDays, totalDays) {
  return {
    validStartTime: `${shiftDays(startOffsetDays)} 00:00:00`,
    validEndTime: `${shiftDays(startOffsetDays + Number(totalDays) - 1)} 23:59:59`
  }
}

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
  // 会员档案：无状态位、无密码；name 由系统生成（忻会员 + 手机号后 4 位）；userId 为空表示尚未注册。
  members: [
    { id: 5001, userId: '9001', phone: '13800138000', name: '忻会员8000', gender: 1, level: 0, createTime: stamp(-40, '10:30:00'), updateTime: stamp(-2, '09:18:00') },
    { id: 5002, userId: null, phone: '13800138001', name: '忻会员8001', gender: 2, level: 0, createTime: stamp(-38, '14:10:00'), updateTime: stamp(-3, '20:05:00') },
    { id: 5003, userId: null, phone: '13800138002', name: '忻会员8002', gender: 0, level: 0, createTime: stamp(-30, '18:00:00'), updateTime: stamp(-7, '11:20:00') },
    { id: 5004, userId: '9002', phone: '13800138003', name: '忻会员8003', gender: 2, level: 0, createTime: stamp(-120, '09:40:00'), updateTime: stamp(-4, '16:35:00') },
    { id: 5005, userId: '9003', phone: '13800138004', name: '忻会员8004', gender: 1, level: 0, createTime: stamp(-25, '15:20:00'), updateTime: stamp(-1, '10:05:00') },
    { id: 5006, userId: null, phone: '13800138005', name: '忻会员8005', gender: 0, level: 0, createTime: stamp(-1, '19:30:00'), updateTime: stamp(-1, '19:30:00') }
  ],
  // 会员卡：落库只存生命周期 3 值（0未开卡/1使用中/2已停卡），「已过期」「已用完」由 service 推导。
  memberCards: [
    { id: 6001, memberId: 5001, openStoreId: 3001, cardNo: `${shiftDays(-40).replace(/-/g, '')}001000001`, cardName: '普拉提次卡', cardType: 1, courseScope: 1, storeId: 3001, purchaseTimes: 18, giftTimes: 2, remainingTimes: 18, validPeriodUnit: 1, validPeriodCount: 12, ...validity(-10, 360), lifecycleStatus: 1, activateTime: stamp(-10, '10:00:00'), stopTime: null, stopReason: null, createTime: stamp(-40, '10:50:00'), updateTime: stamp(-5, '09:20:00') },
    { id: 6002, memberId: 5001, openStoreId: 3001, cardNo: `${shiftDays(-38).replace(/-/g, '')}001000001`, cardName: '私教次卡', cardType: 1, courseScope: 2, storeId: 3001, purchaseTimes: 2, giftTimes: 0, remainingTimes: 0, validPeriodUnit: 1, validPeriodCount: 3, ...validity(-12, 90), lifecycleStatus: 1, activateTime: stamp(-12, '11:05:00'), stopTime: null, stopReason: null, createTime: stamp(-38, '14:20:00'), updateTime: stamp(-8, '12:05:00') },
    { id: 6003, memberId: 5002, openStoreId: 3002, cardNo: `${shiftDays(-20).replace(/-/g, '')}002000001`, cardName: '瑜伽年卡', cardType: 2, courseScope: 1, storeId: null, purchaseTimes: null, giftTimes: null, remainingTimes: null, validPeriodUnit: 2, validPeriodCount: 1, ...validity(-20, 365), lifecycleStatus: 1, activateTime: stamp(-20, '15:00:00'), stopTime: null, stopReason: null, createTime: stamp(-20, '14:50:00'), updateTime: stamp(-20, '15:00:00') },
    { id: 6004, memberId: 5003, openStoreId: 3001, cardNo: `${shiftDays(-30).replace(/-/g, '')}001000001`, cardName: '普拉提次卡', cardType: 1, courseScope: 1, storeId: 3001, purchaseTimes: 10, giftTimes: 0, remainingTimes: 10, validPeriodUnit: 1, validPeriodCount: 6, validStartTime: null, validEndTime: null, lifecycleStatus: 0, activateTime: null, stopTime: null, stopReason: null, createTime: stamp(-30, '18:10:00'), updateTime: stamp(-30, '18:10:00') },
    { id: 6005, memberId: 5004, openStoreId: 3002, cardNo: `${shiftDays(-200).replace(/-/g, '')}002000001`, cardName: '私教次卡', cardType: 1, courseScope: 2, storeId: 3002, purchaseTimes: 6, giftTimes: 2, remainingTimes: 3, validPeriodUnit: 1, validPeriodCount: 3, ...validity(-200, 90), lifecycleStatus: 1, activateTime: stamp(-200, '10:30:00'), stopTime: null, stopReason: null, createTime: stamp(-200, '10:20:00'), updateTime: stamp(-200, '10:30:00') },
    { id: 6006, memberId: 5004, openStoreId: 3001, cardNo: `${shiftDays(-30).replace(/-/g, '')}001000002`, cardName: '瑜伽年卡', cardType: 2, courseScope: 2, storeId: null, purchaseTimes: null, giftTimes: null, remainingTimes: null, validPeriodUnit: 2, validPeriodCount: 1, ...validity(-30, 365), lifecycleStatus: 2, activateTime: stamp(-30, '09:50:00'), stopTime: stamp(-5, '14:20:00'), stopReason: '客户申请停卡', createTime: stamp(-30, '09:40:00'), updateTime: stamp(-5, '14:20:00') }
  ],
  // 扣次／返次流水：只增不改不删；年卡不写流水；同一预约同类流水只有一条。
  memberCardFlows: [
    { id: 9102, cardId: 6002, memberId: 5001, bookingId: 7003, flowType: 1, changeTimes: 2, beforeTimes: 2, afterTimes: 0, createTime: stamp(-8, '12:05:00') },
    { id: 9101, cardId: 6001, memberId: 5001, bookingId: 7001, flowType: 1, changeTimes: 2, beforeTimes: 20, afterTimes: 18, createTime: stamp(-5, '09:20:00') }
  ],
  schedules: [
    { id: 4001, storeId: 3001, storeName: '一水瑜伽·徐汇店', courseId: 1001, courseType: 2, courseName: '普拉提核心塑形', coachId: 2001, coachName: '林晓瑜', classroomId: 8001, classroomName: '普拉提教室', startTime: '2026-10-05 10:00:00', endTime: '2026-10-05 11:00:00', capacity: 12, bookingCount: 2, location: '普拉提教室', status: 1, createTime: '2026-09-25 10:00:00', updateTime: '2026-09-29 09:00:00' },
    { id: 4002, storeId: 3002, storeName: '一水瑜伽·静安店', courseId: 1002, courseType: 1, courseName: '流瑜伽基础', coachId: 2002, coachName: '周然', classroomId: 8003, classroomName: '瑜伽教室', startTime: '2026-10-05 19:00:00', endTime: '2026-10-05 20:00:00', capacity: 16, bookingCount: 1, location: '瑜伽教室', status: 1, createTime: '2026-09-25 10:10:00', updateTime: '2026-09-29 09:10:00' },
    { id: 4003, storeId: 3001, storeName: '一水瑜伽·徐汇店', courseId: 1003, courseType: 3, courseName: '一对一体态评估', coachId: 2001, coachName: '林晓瑜', classroomId: 8002, classroomName: '私教室', startTime: '2026-09-28 14:00:00', endTime: '2026-09-28 15:15:00', capacity: 2, bookingCount: 2, location: '私教室', status: 3, createTime: '2026-09-20 12:00:00', updateTime: '2026-09-28 16:00:00' }
  ],
  reservations: [
    { id: 7001, memberId: 5001, memberName: '忻会员8000', phone: '13800138000', scheduleId: 4001, courseName: '普拉提核心塑形', storeName: '一水瑜伽·徐汇店', coachName: '林晓瑜', classroomName: '普拉提教室', startTime: '2026-10-05 10:00:00', endTime: '2026-10-05 11:00:00', cardId: 6001, cardName: '普拉提次卡', cardType: 1, deductionResult: '已扣 2 次', bookingCount: 2, status: 1, cancelReason: null, cancelSource: null, bookingTime: stamp(-5, '09:20:00'), checkInTime: null },
    { id: 7002, memberId: 5002, memberName: '忻会员8001', phone: '13800138001', scheduleId: 4002, courseName: '流瑜伽基础', storeName: '一水瑜伽·静安店', coachName: '周然', classroomName: '瑜伽教室', startTime: '2026-10-05 19:00:00', endTime: '2026-10-05 20:00:00', cardId: 6003, cardName: '瑜伽年卡', cardType: 2, deductionResult: '年卡不扣次', bookingCount: 1, status: 1, cancelReason: null, cancelSource: null, bookingTime: stamp(-6, '19:30:00'), checkInTime: null },
    { id: 7003, memberId: 5001, memberName: '忻会员8000', phone: '13800138000', scheduleId: 4003, courseName: '一对一体态评估', storeName: '一水瑜伽·徐汇店', coachName: '林晓瑜', classroomName: '私教室', startTime: '2026-09-28 14:00:00', endTime: '2026-09-28 15:15:00', cardId: 6002, cardName: '私教次卡', cardType: 1, deductionResult: '已扣 2 次', bookingCount: 2, status: 3, cancelReason: null, cancelSource: null, bookingTime: stamp(-8, '12:00:00'), checkInTime: stamp(-8, '13:52:00') },
    { id: 7004, memberId: 5005, memberName: '忻会员8004', phone: '13800138004', scheduleId: 4003, courseName: '一对一体态评估', storeName: '一水瑜伽·徐汇店', coachName: '林晓瑜', classroomName: '私教室', startTime: '2026-09-28 14:00:00', endTime: '2026-09-28 15:15:00', cardId: null, cardName: '体验课（不扣次）', cardType: null, deductionResult: '体验课不扣次', bookingCount: 1, status: 3, cancelReason: null, cancelSource: null, bookingTime: stamp(-9, '10:10:00'), checkInTime: stamp(-9, '13:50:00') }
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
    merged.members.forEach(member => {
      member.name = member.name || `忻会员${String(member.phone || '').slice(-4)}`
      member.userId = member.userId === undefined ? null : member.userId
      member.gender = Number(member.gender) || 0
      member.level = Number(member.level) || 0
    })
    merged.memberCards.forEach(card => {
      card.lifecycleStatus = Number(card.lifecycleStatus) || 0
      card.cardType = Number(card.cardType) || 1
      card.courseScope = Number(card.courseScope) || 1
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
  const pad2 = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

function today() {
  return now().slice(0, 10)
}

function addDays(dateText, days) {
  const date = new Date(`${dateText}T00:00:00`)
  date.setDate(date.getDate() + Number(days) - 1)
  const pad2 = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
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

// ---------------- 枚举翻译（真实后端由 Java 枚举成对返回，前端不做映射） ----------------

const MEMBER_GENDER_NAMES = { 0: '未知', 1: '男', 2: '女' }
const MEMBER_LEVEL_NAMES = { 0: 'V0 普通会员' }
const CARD_TYPE_NAMES = { 1: '次数卡', 2: '年卡' }
const COURSE_SCOPE_NAMES = { 1: '团课类', 2: '私教课' }
const CARD_STATUS_NAMES = { 0: '未开卡', 1: '使用中', 2: '已停卡', 3: '已过期', 4: '已用完' }

const findStore = id => database.stores.find(item => String(item.id) === String(id))
const findMember = id => database.members.find(item => String(item.id) === String(id))
const findCard = id => database.memberCards.find(item => String(item.id) === String(id))

// 状态推导（对齐设计 §1.2.3 第 4 条的优先级）：未开卡 → 已停卡 → 已过期 → 已用完 → 使用中
function resolveCardStatus(card) {
  if (Number(card.lifecycleStatus) === 0) return 0
  if (Number(card.lifecycleStatus) === 2 || card.stopTime) return 2
  const endTime = card.validEndTime ? new Date(String(card.validEndTime).replace(' ', 'T')).getTime() : null
  if (endTime && Date.now() > endTime) return 3
  if (Number(card.cardType) === 1 && Number(card.remainingTimes) === 0) return 4
  return 1
}

// 仅年卡且未过期时返回剩余天数（含今天）
function resolveRemainingDays(card, status) {
  if (Number(card.cardType) !== 2 || status === 3 || !card.validEndTime) return null
  const end = new Date(String(card.validEndTime).replace(' ', 'T'))
  end.setHours(0, 0, 0, 0)
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  return Math.max(0, Math.round((end.getTime() - start.getTime()) / 86400000) + 1)
}

function memberView(member) {
  return {
    id: member.id,
    phone: member.phone,
    name: member.name,
    gender: Number(member.gender) || 0,
    genderName: MEMBER_GENDER_NAMES[Number(member.gender) || 0] || '-',
    level: Number(member.level) || 0,
    levelName: MEMBER_LEVEL_NAMES[Number(member.level) || 0] || '-',
    userId: member.userId === undefined ? null : member.userId,
    createTime: member.createTime,
    updateTime: member.updateTime
  }
}

function cardView(card) {
  const openStore = findStore(card.openStoreId)
  const store = card.storeId ? findStore(card.storeId) : null
  const member = findMember(card.memberId)
  const status = resolveCardStatus(card)
  const isCountCard = Number(card.cardType) === 1
  return {
    id: card.id,
    cardNo: card.cardNo,
    openStoreId: card.openStoreId,
    openStoreNo: openStore?.storeNo || null,
    openStoreName: openStore?.name || null,
    cardName: card.cardName,
    cardType: Number(card.cardType),
    cardTypeName: CARD_TYPE_NAMES[Number(card.cardType)] || '-',
    courseScope: Number(card.courseScope),
    courseScopeName: COURSE_SCOPE_NAMES[Number(card.courseScope)] || '-',
    storeId: card.storeId || null,
    storeName: store?.name || null,
    memberId: card.memberId,
    memberName: member?.name || null,
    purchaseTimes: isCountCard ? Number(card.purchaseTimes) || 0 : null,
    giftTimes: isCountCard ? Number(card.giftTimes) || 0 : null,
    remainingTimes: isCountCard ? Number(card.remainingTimes) || 0 : null,
    totalTimes: isCountCard ? (Number(card.purchaseTimes) || 0) + (Number(card.giftTimes) || 0) : null,
    validStartTime: card.validStartTime || null,
    validEndTime: card.validEndTime || null,
    remainingDays: resolveRemainingDays(card, status),
    status,
    statusText: CARD_STATUS_NAMES[status] || '-',
    activateTime: card.activateTime || null,
    stopTime: card.stopTime || null,
    stopReason: card.stopReason || null,
    createTime: card.createTime
  }
}

// ---------------- 会员管理 ----------------

function listMembers(params = {}) {
  const rows = database.members
    .filter(member => contains(member.phone, params.phone) && contains(member.name, params.name) && equals(member.level, params.level))
    .sort((a, b) => Number(b.id) - Number(a.id))
    .map(memberView)
  return page(rows, params)
}

function detailMember(id) {
  const member = findMember(id)
  if (!member) throw new Error('会员不存在或已被删除')
  return { code: 200, data: memberView(member) }
}

function createMember(payload) {
  const phone = String(payload.phone || '').trim()
  if (!/^1[3-9]\d{9}$/.test(phone)) throw new Error('请输入正确的 11 位手机号')
  if (database.members.some(member => member.phone === phone)) throw new Error('该手机号已建立会员档案')
  const level = Number(payload.level ?? 0)
  if (level !== 0) throw new Error('会员等级暂不支持该取值')
  const gender = Number(payload.gender ?? 0)
  if (![0, 1, 2].includes(gender)) throw new Error('请选择有效的性别')
  const timestamp = now()
  // 显示名由服务端生成：忻会员 + 手机号后 4 位（BR-会员-010）；本接口不建账号、不设密码。
  const item = {
    id: nextId(database.members),
    userId: null,
    phone,
    name: `忻会员${phone.slice(-4)}`,
    gender,
    level,
    createTime: timestamp,
    updateTime: timestamp
  }
  database.members.unshift(item)
  saveData()
  return { code: 200, msg: '操作成功' }
}

function updateMember(id, payload) {
  const member = findMember(id)
  if (!member) throw new Error('会员不存在或已被删除')
  // phone / name / userId 一律忽略：手机号是账号绑定键，显示名由系统生成，绑定关系不由管理端维护。
  const level = Number(payload.level ?? member.level)
  if (level !== 0) throw new Error('会员等级暂不支持该取值')
  const gender = Number(payload.gender ?? member.gender)
  if (![0, 1, 2].includes(gender)) throw new Error('请选择有效的性别')
  member.gender = gender
  member.level = level
  member.updateTime = now()
  saveData()
  return { code: 200, data: memberView(member), msg: '操作成功' }
}

function deleteMember(id) {
  const index = database.members.findIndex(member => String(member.id) === String(id))
  if (index < 0) throw new Error('会员不存在或已被删除')
  // 引用门禁 fail-closed：名下有会员卡或预约一律拒绝，并把数量写进提示语。
  const cardCount = database.memberCards.filter(card => String(card.memberId) === String(id)).length
  if (cardCount > 0) throw new Error(`该会员名下有 ${cardCount} 张会员卡，无法删除`)
  const bookingCount = database.reservations.filter(item => String(item.memberId) === String(id)).length
  if (bookingCount > 0) throw new Error(`该会员名下有 ${bookingCount} 条预约，无法删除`)
  database.members.splice(index, 1)
  saveData()
  return { code: 200, msg: '操作成功' }
}

// ---------------- 会员卡管理 ----------------

function listMemberCards(params = {}) {
  const rows = database.memberCards
    .filter(card => {
      if (!equals(card.memberId, params.memberId)) return false
      if (!contains(card.cardNo, params.cardNo)) return false
      if (!equals(card.cardType, params.cardType)) return false
      if (!equals(card.storeId, params.storeId)) return false
      if (!equals(resolveCardStatus(card), params.status)) return false
      return true
    })
    .sort((a, b) => Number(b.id) - Number(a.id))
    .map(cardView)
  return page(rows, params)
}

function detailMemberCard(id) {
  const card = findCard(id)
  if (!card) throw new Error('会员卡不存在或已被删除')
  return { code: 200, data: cardView(card) }
}

// 卡号 = 发卡日期 yyyyMMdd + 开卡门店编号（3 位）+ 6 位当日序列
function nextCardNo(storeNo) {
  const prefix = `${today().replace(/-/g, '')}${storeNo}`
  const seq = database.memberCards.filter(card => String(card.cardNo).startsWith(prefix)).length + 1
  return `${prefix}${String(seq).padStart(6, '0')}`
}

function createMemberCard(payload) {
  const member = findMember(payload.memberId)
  if (!member) throw new Error('会员不存在或已被删除')
  const openStore = findStore(payload.openStoreId)
  if (!openStore) throw new Error('门店不存在或已被删除')
  const cardType = Number(payload.cardType)
  if (![1, 2].includes(cardType)) throw new Error('请选择有效的卡片类型')
  const courseScope = Number(payload.courseScope)
  if (![1, 2].includes(courseScope)) throw new Error('请选择有效的适用课种')
  const cardName = String(payload.cardName || '').trim()
  if (!cardName) throw new Error('请输入卡片名称')
  if (cardName.length > 64) throw new Error('卡片名称不能超过 64 个字符')

  let store = null
  let purchaseTimes = null
  let giftTimes = null
  let remainingTimes = null
  let validPeriodUnit = null
  let validPeriodCount = null

  if (cardType === 1) {
    store = findStore(payload.storeId)
    if (!store) throw new Error('次数卡必须指定适用门店')
    purchaseTimes = Number(payload.purchaseTimes ?? 0)
    giftTimes = Number(payload.giftTimes ?? 0)
    if (!Number.isInteger(purchaseTimes) || purchaseTimes < 0 || !Number.isInteger(giftTimes) || giftTimes < 0) {
      throw new Error('购买次数与赠送次数必须为不小于 0 的整数')
    }
    if (purchaseTimes + giftTimes <= 0) throw new Error('次数卡的总次数必须大于 0')
    remainingTimes = purchaseTimes + giftTimes
    validPeriodUnit = 1
    validPeriodCount = Number(payload.validPeriodCount)
    if (![3, 6, 12].includes(validPeriodCount)) throw new Error('次数卡有效期只能选 3／6／12 个月')
  } else {
    if (payload.storeId) throw new Error('年卡不绑定门店')
    if (payload.purchaseTimes || payload.giftTimes || payload.validPeriodCount) throw new Error('年卡不按次数计')
    validPeriodUnit = 2
    validPeriodCount = 1
  }

  const timestamp = now()
  const item = {
    id: nextId(database.memberCards),
    memberId: member.id,
    openStoreId: openStore.id,
    cardNo: nextCardNo(openStore.storeNo),
    cardName,
    cardType,
    courseScope,
    storeId: store?.id || null,
    purchaseTimes,
    giftTimes,
    remainingTimes,
    validPeriodUnit,
    validPeriodCount,
    validStartTime: null,
    validEndTime: null,
    lifecycleStatus: 0,
    activateTime: null,
    stopTime: null,
    stopReason: null,
    createTime: timestamp,
    updateTime: timestamp
  }
  database.memberCards.unshift(item)
  saveData()
  return { code: 200, msg: '操作成功' }
}

function activateMemberCard(id) {
  const card = findCard(id)
  if (!card) throw new Error('会员卡不存在或已被删除')
  if (Number(card.lifecycleStatus) === 2) throw new Error('已停卡的会员卡不能开卡')
  if (card.activateTime) return { code: 200, data: cardView(card), msg: '操作成功' } // 幂等：不延长有效期、不增次数
  // 有效期天数：月 → 数量 × 30，年 → 数量 × 365；开卡日取服务端自然日
  const totalDays = Number(card.validPeriodUnit) === 2 ? Number(card.validPeriodCount) * 365 : Number(card.validPeriodCount) * 30
  const startDate = today()
  card.validStartTime = `${startDate} 00:00:00`
  card.validEndTime = `${addDays(startDate, totalDays)} 23:59:59`
  card.activateTime = now()
  card.lifecycleStatus = 1
  card.updateTime = now()
  saveData()
  return { code: 200, data: cardView(card), msg: '操作成功' }
}

function stopMemberCard(id, payload) {
  const card = findCard(id)
  if (!card) throw new Error('会员卡不存在或已被删除')
  const reason = String(payload.stopReason || '').trim()
  if (!reason) throw new Error('请输入停卡原因')
  if (reason.length > 255) throw new Error('停卡原因不能超过 255 个字符')
  const status = resolveCardStatus(card)
  if (status === 3 || status === 4) throw new Error('已过期或已用完的会员卡不能停卡')
  if (status === 2) return { code: 200, data: cardView(card), msg: '操作成功' } // 幂等
  card.stopTime = now()
  card.stopReason = reason
  card.lifecycleStatus = 2 // 已停卡：保留剩余次数与有效期，有效期不顺延
  card.updateTime = now()
  saveData()
  return { code: 200, data: cardView(card), msg: '操作成功' }
}

function deleteMemberCard(id) {
  const index = database.memberCards.findIndex(card => String(card.id) === String(id))
  if (index < 0) throw new Error('会员卡不存在或已被删除')
  if (Number(database.memberCards[index].lifecycleStatus) !== 0) throw new Error('只有未开卡的会员卡可以删除')
  database.memberCards.splice(index, 1)
  saveData()
  return { code: 200, msg: '操作成功' }
}

function listMemberCardFlows(cardId, params = {}) {
  const card = findCard(cardId)
  if (!card) throw new Error('会员卡不存在或已被删除')
  const rows = database.memberCardFlows
    .filter(flow => String(flow.cardId) === String(cardId))
    .sort((a, b) => Number(b.id) - Number(a.id))
    .map(flow => ({ ...flow, flowTypeName: Number(flow.flowType) === 1 ? '扣次' : '返次' }))
  return page(rows, params)
}

// ---------------- 排课 / 预约 ----------------

function scheduleView(schedule) {
  const course = database.courses.find(item => String(item.id) === String(schedule.courseId))
  return { ...clone(schedule), courseName: course?.name || schedule.courseName, courseType: schedule.courseType ?? course?.type }
}

function list(resource, params = {}) {
  const items = database[resource].filter(item => {
    if (resource === 'courses') return contains(item.name, params.name) && equals(item.type, params.type) && equals(item.status, params.status)
    if (resource === 'coaches') return contains(item.name, params.name) && equals(item.status, params.status)
    if (resource === 'stores') return contains(item.name, params.name) && contains(item.region, params.region) && equals(item.businessType, params.businessType) && equals(item.storeType, params.storeType) && equals(item.status, params.status)
    if (resource === 'classrooms') return equals(item.storeId, params.storeId) && contains(item.name, params.name) && equals(item.status, params.status)
    if (resource === 'schedules') return (!params.date || item.startTime.startsWith(params.date)) && equals(item.courseType, params.courseType) && equals(item.storeId, params.storeId) && equals(item.courseId, params.courseId) && equals(item.coachId, params.coachId) && equals(item.status, params.status)
    if (resource === 'reservations') return contains(item.memberName, params.memberName) && contains(item.phone, params.phone) && contains(item.courseName, params.courseName) && contains(item.storeName, params.storeName) && (!params.date || item.startTime.startsWith(params.date)) && equals(item.status, params.status)
    return true
  })
  const rows = resource === 'schedules' ? items.map(scheduleView) : items
  return page(rows, params)
}

function detail(resource, id) {
  const item = database[resource].find(row => String(row.id) === String(id))
  if (!item) throw new Error('未找到对应数据')
  const data = clone(item)
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

// 取消预约／取消排课时的返次：次数卡按原扣次人数返还并写返次流水，年卡不处理次数
function refundCard(reservation) {
  if (Number(reservation.cardType) !== 1) return
  const card = findCard(reservation.cardId)
  if (!card) return
  const beforeTimes = Number(card.remainingTimes) || 0
  const changeTimes = Number(reservation.bookingCount) || 0
  card.remainingTimes = beforeTimes + changeTimes
  card.updateTime = now()
  const duplicated = database.memberCardFlows.some(flow => String(flow.bookingId) === String(reservation.id) && Number(flow.flowType) === 2)
  if (!duplicated) {
    database.memberCardFlows.unshift({
      id: nextId(database.memberCardFlows),
      cardId: card.id,
      memberId: reservation.memberId,
      bookingId: reservation.id,
      flowType: 2,
      changeTimes,
      beforeTimes,
      afterTimes: beforeTimes + changeTimes,
      createTime: now()
    })
  }
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
        const resource = route.resource

        if (resource === 'members') {
          if (method === 'get' && segments.length === 0) result = listMembers(config.params)
          else if (method === 'get' && segments.length === 1) result = detailMember(segments[0])
          else if (method === 'post' && segments.length === 0) result = createMember(config.data || {})
          else if (method === 'put' && segments.length === 1) result = updateMember(segments[0], config.data || {})
          else if (method === 'delete' && segments.length === 1) result = deleteMember(segments[0])
          else throw new Error(`原型接口尚未实现：${method.toUpperCase()} ${path}`)
        } else if (resource === 'memberCards') {
          if (method === 'get' && segments.length === 0) result = listMemberCards(config.params)
          else if (method === 'get' && segments.length === 2 && segments[1] === 'flows') result = listMemberCardFlows(segments[0], config.params)
          else if (method === 'get' && segments.length === 1) result = detailMemberCard(segments[0])
          else if (method === 'post' && segments.length === 0) result = createMemberCard(config.data || {})
          else if (method === 'put' && segments[1] === 'activate') result = activateMemberCard(segments[0])
          else if (method === 'put' && segments[1] === 'stop') result = stopMemberCard(segments[0], config.data || {})
          else if (method === 'delete' && segments.length === 1) result = deleteMemberCard(segments[0])
          else throw new Error(`原型接口尚未实现：${method.toUpperCase()} ${path}`)
        } else if (method === 'get' && segments.length === 0) result = list(resource, config.params)
        else if (method === 'get' && segments.length === 1) result = detail(resource, segments[0])
        else if (method === 'post' && segments.length === 0 && resource === 'schedules') result = createSchedule(config.data || {})
        else if (method === 'post' && segments.length === 0) result = create(resource, config.data || {})
        else if (method === 'put' && segments.length === 1 && resource === 'schedules') result = updateSchedule(segments[0], config.data || {})
        else if (method === 'put' && segments.length === 1) result = update(resource, segments[0], config.data || {})
        else if (method === 'put' && segments[1] === 'status') result = update(resource, segments[0], { status: Number(config.data?.status ?? config.params?.status) })
        else if (method === 'put' && segments[1] === 'cancel' && resource === 'schedules') result = cancelSchedule(segments[0])
        else if (method === 'put' && segments[1] === 'cancel' && resource === 'reservations') result = cancelReservation(segments[0], config.data || {})
        else if (method === 'put' && segments[1] === 'check-in' && resource === 'reservations') result = checkInReservation(segments[0])
        else throw new Error(`原型接口尚未实现：${method.toUpperCase()} ${path}`)
        resolve(result)
      } catch (error) {
        reject(error)
      }
    }, 120)
  })
}
