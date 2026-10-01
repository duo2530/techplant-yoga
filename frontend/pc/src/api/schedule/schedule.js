import request from '@/utils/request'

// 查询排课列表
export function listSchedule(query) {
  return request({
    url: '/admin/schedules',
    method: 'get',
    params: query
  })
}

// 查询排课详情
export function getSchedule(scheduleId) {
  return request({
    url: '/admin/schedules/' + scheduleId,
    method: 'get'
  })
}

// 新增排课（成功不返回新 ID）
export function addSchedule(data) {
  return request({
    url: '/admin/schedules',
    method: 'post',
    data: data
  })
}

// 修改排课（仅「待上架」可改）
export function updateSchedule(scheduleId, data) {
  return request({
    url: '/admin/schedules/' + scheduleId,
    method: 'put',
    data: data
  })
}

// 排课状态流转：status 1 下架 / 2 上架 / 3 取消（查询参数，不是请求体）
export function changeScheduleStatus(scheduleId, status) {
  return request({
    url: '/admin/schedules/' + scheduleId + '/status',
    method: 'put',
    params: { status: status }
  })
}

// 删除排课（物理删除，仅「待上架」可删）
export function delSchedule(scheduleId) {
  return request({
    url: '/admin/schedules/' + scheduleId,
    method: 'delete'
  })
}
