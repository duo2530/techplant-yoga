import request from '@/utils/request'

// 查询课程列表（§2.2.1）：参数 name / courseType / pageNum / pageSize；返回 TableDataInfo
// 列表项由后端成对返回 courseType + courseTypeName，前端不要自己维护码→名映射
export function listCourse(query) {
  return request({
    url: '/admin/courses',
    method: 'get',
    params: query
  })
}

// 查询课程详情（§2.2.2）：在列表项字段之上多一个 intro
export function getCourse(courseId) {
  return request({
    url: '/admin/courses/' + courseId,
    method: 'get'
  })
}

// 新增课程（§2.2.3）：请求体 { name, courseType, coverUrl, intro, difficulty }；成功不返回新 ID
export function addCourse(data) {
  return request({
    url: '/admin/courses',
    method: 'post',
    data: data
  })
}

// 修改课程（§2.2.4）：PUT 全量提交，返回更新后的详情；不做引用检查
export function updateCourse(courseId, data) {
  return request({
    url: '/admin/courses/' + courseId,
    method: 'put',
    data: data
  })
}

// 删除课程（§2.2.5）：物理删除，被未结束排课引用时业务码 409，提示语由后端给出。
// 课程没有状态接口（下架通过删除实现），changeCourseStatus 已删除。
export function delCourse(courseId) {
  return request({
    url: '/admin/courses/' + courseId,
    method: 'delete'
  })
}
