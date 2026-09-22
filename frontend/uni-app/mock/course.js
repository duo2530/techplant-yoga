/**
 * Mock：课程
 *
 * 课程类型 4 类取自详细设计 §1.2.2（团课 / 精品课 / 私教课 / 特色课，实测值），
 * 课程名沿用详细设计与联调数据里的示例（哈他瑜伽、流瑜伽…）。
 * 排班日期取 2026-09-20 ~ 2026-09-26 —— 与原型约课页的 7 天日期条一致。
 */
export const COURSE_TYPES = [
  { value: 1, label: '团课' },
  { value: 2, label: '精品课' },
  { value: 3, label: '私教课' },
  { value: 4, label: '特色课' }
]

/** 约课页的 7 天日期条（原型：9.20 ~ 9.26） */
export const BOOKING_DATES = [
  { date: '2026-09-20', label: '09-20', week: '周日', day: '20' },
  { date: '2026-09-21', label: '09-21', week: '周一', day: '21' },
  { date: '2026-09-22', label: '09-22', week: '周二', day: '22' },
  { date: '2026-09-23', label: '09-23', week: '周三', day: '23' },
  { date: '2026-09-24', label: '09-24', week: '周四', day: '24' },
  { date: '2026-09-25', label: '09-25', week: '周五', day: '25' },
  { date: '2026-09-26', label: '09-26', week: '周六', day: '26' }
]

export const courses = [
  {
    id: '1856739201475235801',
    name: '哈他瑜伽',
    type: 1,
    difficulty: 2,
    coverUrl: '',
    intro: '以体式与呼吸配合为主的经典课程，适合初学者建立基础。',
    durationMin: 60,
    coachId: 1,
    coachName: '橘子老师',
    tags: ['新手友好']
  },
  {
    id: '1856739201475235802',
    name: '流瑜伽',
    type: 2,
    difficulty: 3,
    coverUrl: '',
    intro: '体式之间以呼吸串联，节奏连贯流畅。',
    durationMin: 75,
    coachId: 2,
    coachName: '媛媛',
    tags: ['节奏流畅']
  },
  {
    id: '1856739201475235803',
    name: '阴瑜伽',
    type: 2,
    difficulty: 1,
    coverUrl: '',
    intro: '长时间保持体式，作用于筋膜与关节。',
    durationMin: 90,
    coachId: 1,
    coachName: '橘子老师',
    tags: ['深度放松']
  },
  {
    id: '1856739201475235804',
    name: '普拉提垫上',
    type: 1,
    difficulty: 2,
    coverUrl: '',
    intro: '核心控制与呼吸配合的垫上训练。',
    durationMin: 50,
    coachId: 3,
    coachName: '胡林波',
    tags: ['核心训练']
  },
  {
    id: '1856739201475235805',
    name: '空中瑜伽',
    type: 4,
    difficulty: 4,
    coverUrl: '',
    intro: '借助吊床完成体式，需要一定基础。',
    durationMin: 60,
    coachId: 4,
    coachName: '林溪',
    tags: ['进阶']
  },
  {
    id: '1856739201475235806',
    name: '孕产瑜伽',
    type: 4,
    difficulty: 1,
    coverUrl: '',
    intro: '面向孕产期人群的温和课程，需教练评估后参加。',
    durationMin: 60,
    coachId: 5,
    coachName: '苏晴',
    tags: ['需评估']
  },
  {
    id: '1856739201475235807',
    name: '普拉提器械',
    type: 3,
    difficulty: 4,
    coverUrl: '',
    intro: '一对一器械课程，按学员情况定制。',
    durationMin: 55,
    coachId: 3,
    coachName: '胡林波',
    tags: ['一对一']
  },
  {
    id: '1856739201475235809',
    name: '私教一对一',
    type: 3,
    difficulty: 2,
    coverUrl: '',
    intro: '按学员目标定制的私教课程，含体态评估与课程规划。',
    durationMin: 60,
    coachId: 1,
    coachName: '橘子老师',
    tags: ['一对一']
  },
  {
    id: '1856739201475235808',
    name: '肩颈理疗',
    type: 4,
    difficulty: 1,
    coverUrl: '',
    intro: '针对久坐人群的肩颈放松课程。',
    durationMin: 60,
    coachId: 2,
    coachName: '媛媛',
    tags: ['办公室人群']
  }
]

/**
 * 排班场次：约课页列表的数据源（课程 × 日期 × 时段 × 教练 × 门店 × 剩余名额）
 */
export const courseSchedules = [
  { id: 1, courseId: '1856739201475235801', storeId: 1, coachId: 1, date: '2026-09-20', startTime: '10:00', endTime: '11:00', capacity: 12, booked: 9, room: 'A 教室' },
  // 约课页原型默认选中「私教课 + 9.20」且列表里有 3 张卡，因此这三个场次是照着这个默认视图补的
  { id: 13, courseId: '1856739201475235809', storeId: 1, coachId: 1, date: '2026-09-20', startTime: '09:15', endTime: '10:15', capacity: 3, booked: 1, room: '私教区' },
  { id: 14, courseId: '1856739201475235807', storeId: 1, coachId: 3, date: '2026-09-20', startTime: '16:00', endTime: '16:55', capacity: 1, booked: 0, room: '器械区' },
  { id: 15, courseId: '1856739201475235809', storeId: 1, coachId: 4, date: '2026-09-20', startTime: '20:00', endTime: '21:00', capacity: 3, booked: 2, room: '私教区' },
  { id: 2, courseId: '1856739201475235802', storeId: 1, coachId: 2, date: '2026-09-20', startTime: '14:00', endTime: '15:15', capacity: 10, booked: 10, room: 'B 教室' },
  { id: 3, courseId: '1856739201475235804', storeId: 1, coachId: 3, date: '2026-09-20', startTime: '19:00', endTime: '19:50', capacity: 12, booked: 5, room: 'A 教室' },
  { id: 4, courseId: '1856739201475235802', storeId: 1, coachId: 2, date: '2026-09-21', startTime: '09:30', endTime: '10:45', capacity: 10, booked: 4, room: 'B 教室' },
  { id: 5, courseId: '1856739201475235803', storeId: 1, coachId: 1, date: '2026-09-21', startTime: '20:00', endTime: '21:30', capacity: 12, booked: 6, room: 'A 教室' },
  { id: 6, courseId: '1856739201475235805', storeId: 1, coachId: 4, date: '2026-09-22', startTime: '11:00', endTime: '12:00', capacity: 8, booked: 3, room: '吊床区' },
  { id: 7, courseId: '1856739201475235807', storeId: 1, coachId: 3, date: '2026-09-22', startTime: '15:00', endTime: '15:55', capacity: 1, booked: 0, room: '器械区' },
  { id: 8, courseId: '1856739201475235808', storeId: 1, coachId: 2, date: '2026-09-23', startTime: '18:30', endTime: '19:30', capacity: 12, booked: 7, room: 'A 教室' },
  { id: 9, courseId: '1856739201475235801', storeId: 1, coachId: 1, date: '2026-09-24', startTime: '10:00', endTime: '11:00', capacity: 12, booked: 2, room: 'A 教室' },
  { id: 10, courseId: '1856739201475235806', storeId: 1, coachId: 5, date: '2026-09-25', startTime: '14:30', endTime: '15:30', capacity: 6, booked: 1, room: 'B 教室' },
  { id: 11, courseId: '1856739201475235802', storeId: 2, coachId: 2, date: '2026-09-26', startTime: '10:00', endTime: '11:15', capacity: 10, booked: 8, room: 'C 教室' },
  { id: 12, courseId: '1856739201475235803', storeId: 1, coachId: 1, date: '2026-09-26', startTime: '19:00', endTime: '20:30', capacity: 12, booked: 12, room: 'A 教室' }
]
