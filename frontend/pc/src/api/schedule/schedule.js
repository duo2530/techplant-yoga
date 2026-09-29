import request from '@/utils/request'

export function listSchedule(query) {
  return request({ url: '/admin/schedules', method: 'get', params: query })
}

export function getSchedule(scheduleId) {
  return request({ url: '/admin/schedules/' + scheduleId, method: 'get' })
}

export function addSchedule(data) {
  return request({ url: '/admin/schedules', method: 'post', data: data })
}

export function updateSchedule(scheduleId, data) {
  return request({ url: '/admin/schedules/' + scheduleId, method: 'put', data: data })
}

export function updateScheduleStatus(scheduleId, status) {
  return request({ url: '/admin/schedules/' + scheduleId + '/status', method: 'put', data: { status } })
}
