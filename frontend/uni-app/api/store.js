import request from '@/utils/request'

/**
 * 门店接口（用户端 · 免登录只读）
 *
 * 契约唯一来源：`docs/详细设计/用户端接口详细设计.md` §2.1
 *   GET /api/stores 参数 regionCode / keyword / pageNum / pageSize
 *     → TableDataInfo{ total, rows: StorePublicItemVO[] }
 *
 * 免登录：`/api/**` 是 permitAll 白名单，接口不认身份、也没有写操作（BR-全局-002）。
 * 页面只依赖本文件，不要自己写 uni.request。
 *
 * ⚠️ `listStores` 为了兼容一期不接入的 `pages/trial-apply`（仍按数组使用本函数），
 *    保留了「返回数组 + 在数组上挂 total」的旧语义；**新页面请用 `total` 属性取总数**
 *    （`list.length` 只是当前页条数）。将来该页重新接入时再统一改成 `{ list, total }`。
 */

/** 匿名请求：显式告诉 utils/request 不要带 token */
const PUBLIC_REQUEST = { headers: { isToken: false } }

/** 当前门店只存在小程序本地存储里，不落服务端、不登录（BR-全局-010） */
export const CURRENT_STORE_ID_KEY = 'ys_current_store_id'
export const CURRENT_STORE_KEY = 'ys_current_store'

/** 门店列表分页上限（契约 §2.1：pageSize 上限 100） */
const MAX_PAGE_SIZE = 100

/** 雪花 ID 一律当字符串：19 位数字转 Number 会塌缩 */
function toId(value) {
  return value === null || value === undefined ? '' : String(value)
}

function toText(value) {
  return value === null || value === undefined ? '' : String(value)
}

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

/**
 * 选中门店：把 id 与门店对象一起写本地存储
 *
 * - `ys_current_store_id` 是当前门店的**唯一事实来源**（约课页只认它）
 * - `ys_current_store` 只是展示缓存，避免每次进页面都要等一次列表请求
 */
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
 * @returns {Promise<Array>} 归一化后的 StorePublicItemVO 数组；
 *   数组上额外挂了 `total`（后端 TableDataInfo 的总条数），供分页判断是否还有下一页。
 *   ⚠️ 返回数组（不是 {list,total}）是为了兼容 `pages/trial-apply`（一期不接入的旧页面，
 *      仍按数组使用本函数）；分页信息通过 `list.total` 取。
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

/**
 * 当前门店对象（异步）
 *
 * 顺序：本地缓存命中 → 直接用；否则拉一次列表，按本地 id 找，找不到就默认**第一家**并写回本地。
 * 没有门店时返回 null（页面显示空态）。约课页用它拿到 storeId 后再查排课。
 */
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
