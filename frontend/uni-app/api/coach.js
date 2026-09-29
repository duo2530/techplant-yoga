import request from '@/utils/request'

const PUBLIC_REQUEST = {
  headers: { isToken: false }
}

/** 首页金牌教练：后端只返回启用且头衔为「金牌教练」的数据。 */
export function getFeaturedCoaches() {
  return request(Object.assign({}, PUBLIC_REQUEST, {
    url: '/api/coaches/featured',
    method: 'get'
  })).then((res) => {
    return Array.isArray(res && res.data) ? res.data : []
  })
}

/** 用户端教练分页列表。 */
export function listCoaches(params = {}) {
  return request(Object.assign({}, PUBLIC_REQUEST, {
    url: '/api/coaches',
    method: 'get',
    params: {
      pageNum: params.pageNum || 1,
      pageSize: params.pageSize || 10
    }
  })).then((res) => {
    return {
      list: Array.isArray(res && res.rows) ? res.rows : [],
      total: Number(res && res.total) || 0
    }
  })
}
