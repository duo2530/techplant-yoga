import request from '@/utils/request'

export function listSchedule(query) {
  return request({ url: '/admin/schedules', method: 'get', params: query })
}

export function getSchedule(scheduleId) {
  return request({ url: '/admin/schedules/' + scheduleId, method: 'get' })
}

export function addSchedule(data) {
  return request({ url: '/admin/schedules', method: 'post', data })
}

export function updateSchedule(scheduleId, data) {
  return request({ url: '/admin/schedules/' + scheduleId, method: 'put', data })
}

export function cancelSchedule(scheduleId) {
  return request({ url: '/admin/schedules/' + scheduleId + '/cancel', method: 'put' })
}
