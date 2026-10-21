import request from '@/utils/request'
import { toId, toText } from '@/utils/public-api'

/** 用户端门店查询与当前门店缓存。 */

const PUBLIC_REQUEST = { headers: { isToken: false } }

/** 当前门店只存在小程序本地存储里，不落服务端、不登录 */
export const CURRENT_STORE_ID_KEY = 'ys_current_store_id'
export const CURRENT_STORE_KEY = 'ys_current_store'

/** 门店列表分页上限 */
const MAX_PAGE_SIZE = 100

/** 归一化 StorePublicItemVO（只做类型与空值兜底，不新增/推导任何后端没给的字段） */
function normalizeStore(row) {
  const item = row || {}
  return {
    id: toId(item.id),
    name: toText(item.name),
    storeType: item.storeType,
    storeTypeName: toText(item.storeTypeName),
    regionCode: toText(item.regionCode),
    regionName: toText(item.regionName),
    phone: toText(item.phone),
    businessHours: toText(item.businessHours),
    address: toText(item.address),
    imageUrl: toText(item.imageUrl)
  }
}

/** 当前门店 id（同步，字符串；没有则返回空串） */
export function getCurrentStoreId() {
  const id = uni.getStorageSync(CURRENT_STORE_ID_KEY)
  return id === null || id === undefined || id === '' ? '' : String(id)
}

/**
 * 当前门店对象（同步，取本地缓存）
 *
 * 缓存对象只用于首屏即时渲染与「当前门店」展示；门店数据仍以 GET /api/stores 为准。
 */
export function getCachedStore() {
  const cached = uni.getStorageSync(CURRENT_STORE_KEY)
  return cached && typeof cached === 'object' ? cached : null
}

/** 选中门店：把 id 与门店对象一起写本地存储 */
export function saveCurrentStore(store) {
  if (!store || store.id === null || store.id === undefined || store.id === '') {
    return null
  }
  const item = Object.assign({}, normalizeStore(store), { id: String(store.id) })
  uni.setStorageSync(CURRENT_STORE_ID_KEY, item.id)
  uni.setStorageSync(CURRENT_STORE_KEY, item)
  return item
}

/** 兼容旧调用点：只写当前门店 id（不更新展示缓存） */
export function setCurrentStoreId(storeId) {
  if (storeId === null || storeId === undefined || storeId === '') {
    return ''
  }
  const id = String(storeId)
  uni.setStorageSync(CURRENT_STORE_ID_KEY, id)
  return id
}

/**
 * 门店列表（分页）—— GET /api/stores
 *
 * @param {Object} params { regionCode, keyword, pageNum, pageSize }
 * @returns {Promise<Array>} 门店数组，total 属性为后端返回的总条数。
 */
export function listStores(params = {}) {
  const pageSize = Math.min(Number(params.pageSize) || 10, MAX_PAGE_SIZE)
  return request(Object.assign({}, PUBLIC_REQUEST, {
    url: '/api/stores',
    method: 'get',
    params: {
      regionCode: params.regionCode || '',
      keyword: params.keyword || '',
      pageNum: params.pageNum || 1,
      pageSize: pageSize
    }
  })).then((res) => {
    const rows = res && Array.isArray(res.rows) ? res.rows : []
    const list = rows.map(normalizeStore)
    list.total = Number((res && res.total) || 0)
    return list
  })
}

/** 当前门店对象（异步） */
export function getCurrentStore() {
  const id = getCurrentStoreId()
  const cached = getCachedStore()
  if (cached && (!id || String(cached.id) === id)) {
    return Promise.resolve(cached)
  }
  return listStores({ pageNum: 1, pageSize: MAX_PAGE_SIZE }).then((list) => {
    if (!list.length) {
      return null
    }
    let hit = null
    for (let i = 0; i < list.length; i++) {
      if (String(list[i].id) === id) {
        hit = list[i]
        break
      }
    }
    return saveCurrentStore(hit || list[0])
  })
}
