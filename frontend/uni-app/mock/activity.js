/**
 * Mock：活动
 *
 * 原型「活动列表」页整屏是空态（浅蓝渐变 + 灰色图标 + 「暂无数据」），
 * 按 _复刻规范.md §1.2 不塞假数据 —— 默认由 MOCK.empty.activities 控制显示空态。
 * 下面的数据是「有数据」模式下用的（把开关改成 false 即可看到列表）。
 */
export const activities = [
  {
    id: 3001,
    title: '新店开业｜双桥路店体验课 0 元约',
    summary: '开业首月，新客可免费预约一节体验课，含体测与课程规划。',
    coverUrl: '/static/images/home/banner-scene.jpg',
    startDate: '2026-09-01',
    endDate: '2026-09-30',
    status: 1,
    statusLabel: '进行中'
  },
  {
    id: 3002,
    title: '秋季会员招募｜办卡送 3 节私教',
    summary: '9 月 30 日前办理年卡，赠 3 节一对一私教课。',
    coverUrl: '/static/images/store/photo-1.jpg',
    startDate: '2026-09-10',
    endDate: '2026-09-30',
    status: 1,
    statusLabel: '进行中'
  },
  {
    id: 3003,
    title: '空中瑜伽公开课',
    summary: '已结束：9 月 12 日 19:00 空中瑜伽公开课，感谢参与。',
    coverUrl: '/static/images/store/photo-2.jpg',
    startDate: '2026-09-12',
    endDate: '2026-09-12',
    status: 0,
    statusLabel: '已结束'
  }
]
