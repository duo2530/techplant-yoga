import request from '@/utils/request'

// 查询课程列表（§2.2.1）：返回若依标准 TableDataInfo（rows / total）
export function listCourse(query) {
  return request({
    url: '/admin/courses',
    method: 'get',
    params: query
  })
}

// 查询课程详情（§2.2.2）
export function getCourse(courseId) {
  return request({
    url: '/admin/courses/' + courseId,
    method: 'get'
  })
}

// 新增课程（§2.2.3）：请求体不含 status，新课程默认启用
export function addCourse(data) {
  return request({
    url: '/admin/courses',
    method: 'post',
    data: data
  })
}

// 修改课程（§2.2.4）：PUT 全量提交，路径带课程编号，请求体不含 status
export function updateCourse(courseId, data) {
  return request({
    url: '/admin/courses/' + courseId,
    method: 'put',
    data: data
  })
}

// 设置课程状态（§2.2.5）：status = 1 启用 / 0 停用；停用被引用时业务码 409，提示语由后端给出
export function changeCourseStatus(courseId, status) {
  return request({
    url: '/admin/courses/' + courseId + '/status',
    method: 'put',
    data: { status: status }
  })
}
