/**
 * Mock：我的预约（课程预约）
 */
export const BOOKING_STATUS = [
  { value: 0, label: '全部' },
  { value: 1, label: '已预约' },
  { value: 2, label: '已取消' },
  { value: 3, label: '已签到' }
]

/** 课种下拉（原型「全部...」筛选项） */
export const BOOKING_COURSE_FILTERS = [
  { value: 0, label: '全部' },
  { value: 1, label: '团课' },
  { value: 2, label: '精品课' },
  { value: 3, label: '私教课' },
  { value: 4, label: '特色课' }
]

/** 卡种下拉（原型「全部...」筛选项） */
export const BOOKING_CARD_FILTERS = [
  { value: 0, label: '全部' },
  { value: 1, label: '次卡' },
  { value: 2, label: '期限卡' },
  { value: 3, label: '单次' }
]

export const bookings = [
  {
    id: 1001,
    courseId: '1856739201475235801',
    courseName: '哈他瑜伽',
    courseType: 1,
    coachName: '橘子老师',
    storeName: '一水·瑜伽普拉提(双桥路店)',
    room: 'A 教室',
    date: '2026-09-22',
    startTime: '10:00',
    endTime: '11:00',
    status: 1,
    cardName: '10次卡'
  },
  {
    id: 1002,
    courseId: '1856739201475235804',
    courseName: '普拉提垫上',
    courseType: 1,
    coachName: '胡林波',
    storeName: '一水·瑜伽普拉提(双桥路店)',
    room: 'A 教室',
    date: '2026-09-18',
    startTime: '19:00',
    endTime: '19:50',
    status: 3,
    cardName: '10次卡'
  },
  {
    id: 1003,
    courseId: '1856739201475235802',
    courseName: '流瑜伽',
    courseType: 2,
    coachName: '媛媛',
    storeName: '一水·瑜伽普拉提(双桥路店)',
    room: 'B 教室',
    date: '2026-09-15',
    startTime: '14:00',
    endTime: '15:15',
    status: 2,
    cardName: '次卡'
  }
]
