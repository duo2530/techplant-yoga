/**
 * api 层小工具（mock 阶段专用）
 *
 * 命名为 helper 而不是 utils，避免和工程里既有的 `@/utils/*`（框架工具）混淆。
 */
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
