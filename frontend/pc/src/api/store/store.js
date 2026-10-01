import request from '@/utils/request'

// 查询门店列表
export function listStore(query) {
  return request({
    url: '/admin/stores',
    method: 'get',
    params: query
  })
}

// 查询门店详情
export function getStore(storeId) {
  return request({
    url: '/admin/stores/' + storeId,
    method: 'get'
  })
}

// 新增门店（一期门店没有状态字段，没有 updateStoreStatus）
export function addStore(data) {
  return request({
    url: '/admin/stores',
    method: 'post',
    data: data
  })
}

// 修改门店（全量编辑）
export function updateStore(storeId, data) {
  return request({
    url: '/admin/stores/' + storeId,
    method: 'put',
    data: data
  })
}

// 删除门店（物理删除；名下有教室或未结束排课会被 409 拒绝）
export function delStore(storeId) {
  return request({
    url: '/admin/stores/' + storeId,
    method: 'delete'
  })
}
