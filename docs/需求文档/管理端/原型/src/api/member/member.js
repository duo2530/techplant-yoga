import request from '@/utils/request'

// 会员管理：管理端只建档、不建账号、不设密码。
// 手机号是账号绑定键（二期不可修改），显示名由服务端生成（忻会员 + 手机号后 4 位）。

// 查询会员列表（phone 模糊 / name 模糊 / level 精确）
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

// 新增会员（只提交 phone / gender / level，档案的 user_id 由用户端注册时回填）
export function addMember(data) {
  return request({
    url: '/admin/members',
    method: 'post',
    data: data
  })
}

// 修改会员（只提交 gender / level，phone 与 name 一律由服务端忽略）
export function updateMember(memberId, data) {
  return request({
    url: '/admin/members/' + memberId,
    method: 'put',
    data: data
  })
}

// 删除会员（物理删除，名下有会员卡或预约时服务端返回 409 及数量）
export function delMember(memberId) {
  return request({
    url: '/admin/members/' + memberId,
    method: 'delete'
  })
}
