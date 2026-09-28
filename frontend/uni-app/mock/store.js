/**
 * Mock：门店
 *
 * 门店文案取自原型首页门店卡（docs/原型/home.html）与截图实测：
 * 「瑜伽普拉提(双桥路店)」「上海市浦东新区双桥路937号1_2层」。
 * 其余门店是为了「换店」交互能演示而补充的（原型只给了 1 家的文案）。
 */
export const stores = [
  {
    id: 1,
    name: '瑜伽普拉提(双桥路店)',
    shortName: '双桥路店',
    address: '上海市浦东新区双桥路937号1_2层',
    phone: '021-5888 0001',
    distance: '1.2km',
    businessHours: '09:00-21:30',
    photos: [
      '/static/images/store/photo-1.jpg',
      '/static/images/store/photo-2.jpg',
      '/static/images/store/photo-3.jpg'
    ]
  },
  {
    id: 2,
    name: '瑜伽普拉提(世纪公园店)',
    shortName: '世纪公园店',
    address: '上海市浦东新区锦绣路1001号2层',
    phone: '021-5888 0002',
    distance: '3.6km',
    businessHours: '09:00-21:30',
    photos: []
  },
  {
    id: 3,
    name: '瑜伽普拉提(张江店)',
    shortName: '张江店',
    address: '上海市浦东新区祖冲之路2288号1层',
    phone: '021-5888 0003',
    distance: '5.1km',
    businessHours: '10:00-22:00',
    photos: []
  }
]

/** 当前门店（「换店」就是改这个值） */
export const currentStoreId = 1
