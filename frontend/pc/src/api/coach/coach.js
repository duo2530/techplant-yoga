import request from '@/utils/request'

// 查询教练列表（管理端，支持按姓名模糊查询）
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

// 删除教练（物理删除，服务端带排课删除门禁）
export function delCoach(coachId) {
  return request({
    url: '/admin/coaches/' + coachId,
    method: 'delete'
  })
}
