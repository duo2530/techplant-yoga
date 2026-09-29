import request from '@/utils/request'

export function listMemberCard(query) {
  return request({ url: '/admin/member-cards', method: 'get', params: query })
}

export function getMemberCard(cardId) {
  return request({ url: '/admin/member-cards/' + cardId, method: 'get' })
}

export function addMemberCard(data) {
  return request({ url: '/admin/member-cards', method: 'post', data })
}

export function activateMemberCard(cardId) {
  return request({ url: '/admin/member-cards/' + cardId + '/activate', method: 'put' })
}
