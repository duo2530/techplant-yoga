import request from '@/utils/request'

export function listClassroom(query) {
  return request({ url: '/admin/classrooms', method: 'get', params: query })
}

export function getClassroom(classroomId) {
  return request({ url: '/admin/classrooms/' + classroomId, method: 'get' })
}

export function addClassroom(data) {
  return request({ url: '/admin/classrooms', method: 'post', data })
}

export function updateClassroom(classroomId, data) {
  return request({ url: '/admin/classrooms/' + classroomId, method: 'put', data })
}
