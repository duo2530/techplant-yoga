/**
 * Mock：消息与公告
 *
 * 原型「消息」页整页是空态（插画 + 「暂无数据」#999999），
 * 默认由 MOCK.empty.messages 控制显示空态。
 *
 * 公告（notices）供首页公告栏使用：原型首页显示「暂无公告」，
 * 默认由 MOCK.empty.homeNotices 控制。
 */
export const MESSAGE_TYPES = [
  { value: 0, label: '全部' },
  { value: 1, label: '系统通知' },
  { value: 2, label: '课程提醒' },
  { value: 3, label: '活动通知' }
]

export const messages = [
  {
    id: 4001,
    type: 2,
    title: '课程提醒',
    content: '您预约的「哈他瑜伽」将于 9 月 22 日 10:00 开始，请提前 10 分钟到店。',
    time: '2026-09-21 18:00',
    read: false
  },
  {
    id: 4002,
    type: 1,
    title: '系统通知',
    content: '您的体验课申请已通过审核，请在预约时间到店。',
    time: '2026-09-20 10:30',
    read: false
  },
  {
    id: 4003,
    type: 3,
    title: '活动通知',
    content: '双桥路店开业活动：新客 0 元体验课，活动截止 9 月 30 日。',
    time: '2026-09-18 09:00',
    read: true
  }
]

export const notices = [
  { id: 1, content: '双桥路店开业活动：新客 0 元体验课，截止 9 月 30 日' },
  { id: 2, content: '国庆假期课程安排已更新，请查看约课页' }
]
