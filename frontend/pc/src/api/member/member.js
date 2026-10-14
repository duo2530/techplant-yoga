import request from '@/utils/request'

// 会员管理：管理端只建档、不建账号、不设密码。
// 手机号是账号绑定键（本期不可修改），显示名由服务端生成（忻会员 + 手机号后 4 位）。

// 查询会员列表（phone_last4 精确 / name 模糊 / level 精确）
export function listMember(query) {
  return request({
    url: '/admin/members',
    method: 'get',
    params: query
  })
}

// 查询会员详情
export function getMember(memberId) {
  return request({
    url: '/admin/members/' + memberId,
    method: 'get'
  })
}

// 新增会员建档（只提交 phone / gender / level，登录凭证在用户端注册时写入）
export function addMember(data) {
  return request({
    url: '/admin/members',
    method: 'post',
    data: data
  })
}

// 修改会员（只提交 gender / level，phone 与 name 由服务端忽略）
export function updateMember(memberId, data) {
  return request({
    url: '/admin/members/' + memberId,
    method: 'put',
    data: data
  })
}

// 删除会员（物理删除，登录凭证随行删除，既有登录态失效）
export function delMember(memberId) {
  return request({
    url: '/admin/members/' + memberId,
    method: 'delete'
  })
}
