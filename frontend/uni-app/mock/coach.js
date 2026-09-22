/**
 * Mock：教练
 *
 * 教练姓名与照片取自原型首页「金牌教练」三张卡（橘子老师 / 媛媛 / 胡林波），
 * 头像与照片走从截图裁出的资产（static/images/home/coachN-*.jpg）。
 * 其余两位是为了「约教练」页能演示更多卡片而补充的（原型只给了三位）。
 *
 * `intro` 的取值照「约教练」页截图的原文「暂无教练简介」（首页教练卡里的「暂无介绍」是写死在页面上的）。
 */
export const coaches = [
  {
    id: 1,
    name: '橘子老师',
    avatar: '/static/images/home/coach1-avatar.jpg',
    photo: '/static/images/home/coach1-photo.jpg',
    intro: '暂无教练简介',
    tags: ['哈他瑜伽', '阴瑜伽'],
    years: 6,
    photos: []
  },
  {
    id: 2,
    name: '媛媛',
    avatar: '/static/images/home/coach2-avatar.jpg',
    photo: '/static/images/home/coach2-photo.jpg',
    intro: '暂无教练简介',
    tags: ['流瑜伽', '肩颈理疗'],
    years: 4,
    photos: []
  },
  {
    id: 3,
    name: '胡林波',
    avatar: '/static/images/home/coach3-avatar.jpg',
    photo: '/static/images/home/coach3-photo.jpg',
    intro: '暂无教练简介',
    tags: ['普拉提', '器械'],
    years: 8,
    photos: []
  },
  {
    id: 4,
    name: '林溪',
    avatar: '',
    photo: '',
    intro: '暂无教练简介',
    tags: ['空中瑜伽'],
    years: 3,
    photos: []
  },
  {
    id: 5,
    name: '苏晴',
    avatar: '',
    photo: '',
    intro: '暂无教练简介',
    tags: ['孕产瑜伽'],
    years: 5,
    photos: []
  }
]
