import request from '@/utils/request'

// 查询会员卡列表（memberId / cardNo 模糊 / cardType / storeId 按适用门店过滤）
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

// 发卡：创建待开卡记录，卡号由服务端生成，成功不返回新卡 ID
export function addMemberCard(data) {
  return request({
    url: '/admin/member-cards',
    method: 'post',
    data: data
  })
}

// 正式开卡（无请求体）：写入开卡时间与有效期，仅待开卡可调用一次
export function activateMemberCard(cardId) {
  return request({
    url: '/admin/member-cards/' + cardId + '/activate',
    method: 'put'
  })
}

// 停卡：保留剩余次数与有效期，待开卡与使用中均可停
export function stopMemberCard(cardId, data) {
  return request({
    url: '/admin/member-cards/' + cardId + '/stop',
    method: 'put',
    data: data
  })
}

// 删除会员卡
export function delMemberCard(cardId) {
  return request({
    url: '/admin/member-cards/' + cardId,
    method: 'delete'
  })
}
