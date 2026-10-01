import request from '@/utils/request'

// 查询教室列表（§2.2.1）：返回若依标准 TableDataInfo（rows / total），storeName 由后端批量补齐
export function listClassroom(query) {
  return request({
    url: '/admin/classrooms',
    method: 'get',
    params: query
  })
}

// 新增教室（§2.2.2）：请求体 { storeId, name }；成功不返回新 ID
export function addClassroom(data) {
  return request({
    url: '/admin/classrooms',
    method: 'post',
    data: data
  })
}

// 修改教室（§2.2.3）：请求体只有 { name }，不可换门店
export function updateClassroom(classroomId, data) {
  return request({
    url: '/admin/classrooms/' + classroomId,
    method: 'put',
    data: data
  })
}

// 删除教室（§2.2.4）：物理删除，被未结束排课引用时业务码 409，提示语由后端给出
export function delClassroom(classroomId) {
  return request({
    url: '/admin/classrooms/' + classroomId,
    method: 'delete'
  })
}

// 说明：教室没有详情接口（列表项已含编辑回填所需的全部字段）
