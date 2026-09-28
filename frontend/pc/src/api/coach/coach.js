import request from '@/utils/request'

// 查询教练列表（管理端）
export function listCoach(query) {
  return request({
    url: '/admin/coaches',
    method: 'get',
    params: query
  })
}

// 查询教练详情
export function getCoach(coachId) {
  return request({
    url: '/admin/coaches/' + coachId,
    method: 'get'
  })
}

// 新增教练
export function addCoach(data) {
  return request({
    url: '/admin/coaches',
    method: 'post',
    data: data
  })
}

// 修改教练
export function updateCoach(coachId, data) {
  return request({
    url: '/admin/coaches/' + coachId,
    method: 'put',
    data: data
  })
}

// 设置教练状态
export function changeCoachStatus(coachId, status) {
  return request({
    url: '/admin/coaches/' + coachId + '/status',
    method: 'put',
    data: { status: status }
  })
}
