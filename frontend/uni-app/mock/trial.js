/**
 * Mock：体验课申请
 */
export const TRIAL_STATUS = [
  { value: 0, label: '全部' },
  { value: 1, label: '申请待审核' },
  { value: 2, label: '审核通过' },
  { value: 3, label: '审核拒绝' }
]

export const trialApplications = [
  {
    id: 2001,
    storeId: 1,
    storeName: '瑜伽普拉提(双桥路店)',
    contactName: '张小一',
    phone: '13800008888',
    expectDate: '2026-09-24',
    expectTime: '19:00',
    courseName: '哈他瑜伽',
    status: 1,
    applyTime: '2026-09-20 10:12'
  },
  {
    id: 2002,
    storeId: 1,
    storeName: '瑜伽普拉提(双桥路店)',
    contactName: '张小一',
    phone: '13800008888',
    expectDate: '2026-09-16',
    expectTime: '10:00',
    courseName: '普拉提垫上',
    status: 2,
    applyTime: '2026-09-12 09:30'
  }
]

/** 申请体验表单的可选项（提交页用） */
export const TRIAL_FORM_OPTIONS = {
  expectTimes: ['09:00', '10:00', '14:00', '19:00', '20:00'],
  gender: [
    { value: 1, label: '女' },
    { value: 2, label: '男' }
  ],
  sources: [
    { value: 1, label: '朋友推荐' },
    { value: 2, label: '门店路过' },
    { value: 3, label: '大众点评' },
    { value: 4, label: '小红书' },
    { value: 5, label: '其他' }
  ]
}
