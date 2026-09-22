import { mockResult } from '@/mock/config'
import { stores } from '@/mock/store'

/**
 * 当前门店（内存态）：原型首页有「换店」，mock 阶段只切本地状态，不发请求。
 * 将来接真实后端时，这里换成「查询用户所在门店 / 保存偏好门店」的接口。
 */
let currentStoreId = 1

export function getCurrentStoreId() {
  return currentStoreId
}

export function setCurrentStoreId(storeId) {
  currentStoreId = Number(storeId)
  return mockResult(getCurrentStoreId())
}

/** 当前门店对象 */
export function getCurrentStore() {
  return mockResult(stores.find((item) => item.id === currentStoreId) || stores[0])
}

/** 门店列表（「换店」选择用） */
export function listStores() {
  return mockResult(stores)
}

/** 门店详情（含实景照片） */
export function getStoreDetail(storeId) {
  return mockResult(stores.find((item) => String(item.id) === String(storeId)) || null)
}
