/** Mock 空态开关与响应工具。 */
export const MOCK = {
  /** 模拟网络延迟（毫秒），设为 0 即同步返回 */
  delay: 120,

  /** 空态开关：true = 按原型显示空态；false = 返回 mock 数据 */
  empty: {
    /** 首页「今日可约团课」 */
    homeTodayCourses: true,
    /** 首页「热门课程」 */
    homeHotCourses: true,
    /** 首页公告栏（原型显示「暂无公告」） */
    homeNotices: true,
    /** 我的体验课（原型显示「暂无数据」） */
    trials: true,
    /** 活动列表（原型整屏空态） */
    activities: true,
    /** 消息（原型整页空态） */
    messages: true
  }
}

/** 深拷贝，避免页面改动污染 mock 数据 */
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
