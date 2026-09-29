import request from '@/utils/request'

export function listMember(query) {
  return request({ url: '/admin/members', method: 'get', params: query })
}

export function getMember(memberId) {
  return request({ url: '/admin/members/' + memberId, method: 'get' })
}
