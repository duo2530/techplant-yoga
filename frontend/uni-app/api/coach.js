/** 教练页面的空结果接口。 */

export function getFeaturedCoaches() {
  return Promise.resolve([])
}

export function listCoaches() {
  return Promise.resolve({ list: [], total: 0 })
}
