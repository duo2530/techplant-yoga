import config from '@/config'

/** 用户端公开接口响应的公共归一化工具：字符串 ID、文本、图片地址与数字。 */

/** 雪花 ID 一律当字符串：19 位数字转 Number 会塌缩 */
export function toId(value) {
  return value === null || value === undefined ? '' : String(value)
}

export function toText(value) {
  return value === null || value === undefined ? '' : String(value)
}

/** 后端可能返回相对路径，统一补成可访问地址；绝对地址与本地静态资源保持原样 */
export function toImageUrl(value) {
  const url = toText(value).trim()
  if (!url || /^(?:[a-z][a-z\d+.-]*:|\/\/|\/?static\/)/i.test(url)) {
    return url
  }
  return config.baseUrl.replace(/\/+$/, '') + '/' + url.replace(/^\/+/, '')
}

export function toNum(value) {
  const n = Number(value)
  return isNaN(n) ? 0 : n
}
