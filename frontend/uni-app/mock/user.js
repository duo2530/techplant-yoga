/**
 * Mock：用户与会员资产
 *
 * 「我的」页原型截图是**游客态**（暂未登录 / V0 普通会员 / 未绑定手机号），
 * 是否显示登录态由 `MOCK.loggedIn` 控制（默认 true，显示下面的 mock 用户）。
 *
 * 菜单项与会员资产字段来自用户端功能清单（会员卡 / 优惠券 / 礼包 / 积分 / 合同 / 体测记录）。
 * 注意：这些子页面**原型里没有**，因此本轮不建页面，只在「我的」页做入口占位。
 */
export const userInfo = {
  id: '1856739201475235841',
  nickname: '瑜伽小一',
  avatar: '/static/images/mine/avatar.jpg',
  phone: '13800008888',
  memberLevel: '黄金会员',
  memberLevelCode: 'V2',
  points: 1280,
  gender: '女',
  birthday: '1995-06-18',
  joinDate: '2026-03-12'
}

/** 游客态（MOCK.loggedIn = false 时使用，文案照原型） */
export const guestInfo = {
  nickname: '暂未登录',
  avatar: '',
  phone: '',
  memberLevel: '普通会员',
  memberLevelCode: 'V0',
  points: 0
}

/** 会员权益（各等级权益包） */
export const memberBenefits = [
  {
    level: '白银会员',
    levelCode: 'V1',
    threshold: '累计消费满 1000 元',
    benefits: ['课程预约 9.5 折', '每月 1 次免费体测']
  },
  {
    level: '黄金会员',
    levelCode: 'V2',
    threshold: '累计消费满 3000 元',
    benefits: ['课程预约 9 折', '每月 1 次免费体测', '生日月赠 1 节团课']
  },
  {
    level: '钻石会员',
    levelCode: 'V3',
    threshold: '累计消费满 8000 元',
    benefits: ['课程预约 8.5 折', '每月 1 次免费体测', '生日月赠 2 节团课', '优先约热门课']
  }
]

/** 我的会员卡 */
export const memberCards = [
  {
    id: 5001,
    name: '10 次卡',
    cardType: '次卡',
    totalTimes: 10,
    usedTimes: 4,
    expireDate: '2027-03-12',
    status: 1,
    statusLabel: '使用中'
  }
]

/** 我的优惠券 */
export const coupons = [
  { id: 6001, name: '新客体验券', amount: 99, threshold: 0, expireDate: '2026-10-31', status: 1, statusLabel: '未使用' },
  { id: 6002, name: '团课立减券', amount: 30, threshold: 100, expireDate: '2026-09-15', status: 2, statusLabel: '已过期' }
]

/** 我的礼包 */
export const giftPacks = [
  { id: 7001, name: '开业礼包', content: '1 节私教体验课 + 运动毛巾', receiveDate: '2026-09-01', status: 1, statusLabel: '已领取' }
]

/** 积分明细 */
export const pointsRecords = [
  { id: 8001, title: '完成课程签到', points: 50, time: '2026-09-18 19:55' },
  { id: 8002, title: '邀请好友注册', points: 200, time: '2026-09-10 12:03' }
]

/** 我的合同 */
export const contracts = [
  { id: 9001, name: '会员服务合同', signDate: '2026-03-12', status: 1, statusLabel: '生效中' }
]

/** 我的体测记录 */
export const bodyTests = [
  {
    id: 9501,
    date: '2026-09-12',
    weight: 52.4,
    height: 163,
    bodyFat: 24.1,
    muscle: 22.8,
    coachName: '橘子老师',
    remark: '建议加强核心与背部力量训练'
  }
]

/** 「我的」页菜单（照原型顺序；path 为 null 的表示原型没有对应页面） */
export const profileMenus = [
  { key: 'cards', label: '我的会员卡', path: null },
  { key: 'coupons', label: '我的优惠券', path: null },
  { key: 'gifts', label: '我的礼包', path: null },
  { key: 'points', label: '我的积分', path: null },
  { key: 'contracts', label: '我的合同', path: null },
  { key: 'bookings', label: '已约课程', path: '/pages/my-bookings/index' },
  { key: 'bodyTests', label: '我的体测', path: null },
  { key: 'trials', label: '我的体验课', path: '/pages/my-trials/index' },
  { key: 'terms', label: '用户协议', path: '/pages/terms/index' },
  { key: 'privacy', label: '隐私政策', path: '/pages/privacy/index' }
]
