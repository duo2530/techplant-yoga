import request from '@/utils/request'

// 会员卡管理：卡号由服务端生成，本模块没有「修改卡」接口。
// 落库只存生命周期（0未开卡/1使用中/2已停卡），已过期/已用完由服务端推导后经 status/statusText 返回。

// 查询会员卡列表（memberId / cardNo / cardType / status / storeId）
export function listMemberCard(query) {
  return request({
    url: '/admin/member-cards',
    method: 'get',
    params: query
  })
}

// 查询会员卡详情
export function getMemberCard(cardId) {
  return request({
    url: '/admin/member-cards/' + cardId,
    method: 'get'
  })
}

// 发卡（创建待开卡：有效期留空，需再调开卡接口）
export function addMemberCard(data) {
  return request({
    url: '/admin/member-cards',
    method: 'post',
    data: data
  })
}

// 正式开卡（服务端取自然日算有效期；重复开卡幂等）
export function activateMemberCard(cardId) {
  return request({
    url: '/admin/member-cards/' + cardId + '/activate',
    method: 'put'
  })
}

// 停卡（原因必填；保留剩余次数与有效期，有效期不顺延）
export function stopMemberCard(cardId, data) {
  return request({
    url: '/admin/member-cards/' + cardId + '/stop',
    method: 'put',
    data: data
  })
}

// 删除会员卡（仅未开卡可删）
export function delMemberCard(cardId) {
  return request({
    url: '/admin/member-cards/' + cardId,
    method: 'delete'
  })
}

// 查询扣次／返次流水（年卡不产生流水）
export function listMemberCardFlow(cardId, query) {
  return request({
    url: '/admin/member-cards/' + cardId + '/flows',
    method: 'get',
    params: query
  })
}
