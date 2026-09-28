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

// 新增门店
export function addStore(data) {
  return request({
    url: '/admin/stores',
    method: 'post',
    data: data
  })
}

// 修改门店
export function updateStore(storeId, data) {
  return request({
    url: '/admin/stores/' + storeId,
    method: 'put',
    data: data
  })
}

// 设置门店状态
export function updateStoreStatus(storeId, status) {
  return request({
    url: '/admin/stores/' + storeId + '/status',
    method: 'put',
    params: { status: status }
  })
}
