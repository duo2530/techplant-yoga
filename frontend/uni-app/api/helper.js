/** 本地数据查询工具。 */
export function firstName(list) {
  return (list && list.length && list[0]) || {}
}

/** 按 id 找一条 */
export function findById(list, id) {
  return (list || []).find((item) => String(item.id) === String(id)) || null
}

/** 过滤掉 undefined / 空字符串的查询条件，便于 mock 里做「不传即不限」 */
export function pickFilters(params) {
  const result = {}
  Object.keys(params || {}).forEach((key) => {
    const value = params[key]
    if (value !== undefined && value !== null && value !== '' && value !== 0) {
      result[key] = value
    }
  })
  return result
}
