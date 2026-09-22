/**
 * Mock 开关与工具
 *
 * 本阶段用户端还没有后端接口（详细设计目前只覆盖管理端「课程管理」），
 * 因此页面数据**全部来自本地 mock**，不发起任何网络请求。
 *
 * 页面只依赖 `@/api/*`（假接口），将来接真实后端时**只改 api 层**，页面不用动。
 *
 * `empty.*` 是「按原型显示空态」的开关：原型截图里这些位置就是空态
 * （首页今日可约团课 / 热门课程 / 暂无公告、我的预约、我的体验课），
 * 复刻规范要求「空态也要复刻，不要塞假数据」，所以默认为 true。
 * 想在有数据的状态下验收页面，把对应开关改成 false 即可（mock 数据是现成的）。
 */
export const MOCK = {
  /** 模拟网络延迟（毫秒），设为 0 即同步返回 */
  delay: 120,

  /**
   * 登录态：true = 「我的」页显示 mock 用户（已登录）；
   * false = 按原型截图显示「暂未登录 / V0 普通会员 / 未绑定手机号」的游客态
   */
  loggedIn: true,

  /** 空态开关：true = 按原型显示空态；false = 返回 mock 数据 */
  empty: {
    /** 首页「今日可约团课」 */
    homeTodayCourses: true,
    /** 首页「热门课程」 */
    homeHotCourses: true,
    /** 首页公告栏（原型显示「暂无公告」） */
    homeNotices: true,
    /** 我的预约（原型显示「暂无符合条件的预约」） */
    bookings: true,
    /** 我的体验课（原型显示「暂无数据」） */
    trials: true,
    /** 活动列表（原型整屏空态） */
    activities: true,
    /** 消息（原型整页空态） */
    messages: true
  }
}

/**
 * 深拷贝，避免页面改动污染 mock 数据
 */
function clone(data) {
  if (data === undefined || data === null) {
    return data
  }
  return JSON.parse(JSON.stringify(data))
}

/**
 * 模拟一次接口返回（延迟 + 深拷贝）
 */
export function mockResult(data) {
  const result = clone(data)
  if (!MOCK.delay) {
    return Promise.resolve(result)
  }
  return new Promise((resolve) => {
    setTimeout(() => resolve(result), MOCK.delay)
  })
}

/**
 * 按 key 取空态开关；未配置的 key 一律返回 false（即有数据）
 */
export function isEmpty(key) {
  return !!(MOCK.empty && MOCK.empty[key])
}
