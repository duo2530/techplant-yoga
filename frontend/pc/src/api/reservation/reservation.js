import request from '@/utils/request'

// 查询预约列表（门店 / 课程 / 排课 / 会员 / 状态 / 卡类型 / 上课日期范围；管理端只查询）
export function listReservation(query) {
  return request({
    url: '/admin/reservations',
    method: 'get',
    params: query
  })
}
