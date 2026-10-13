import config from '@/config'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'
import { useUserStore } from '@/store/modules/user'
import { toast, showConfirm, tansParams } from '@/utils/common'
import { isSessionValid, LOGIN_PAGE } from '@/utils/session'

let timeout = 10000
const baseUrl = config.baseUrl

/** 「重新登录」弹窗是否已经弹出：并发多个 401 只提示一次 */
let reLoginShowing = false

/** 只接受普通对象作为业务响应体（数组、字符串、null 都算异常） */
const isPlainObject = (value) => Object.prototype.toString.call(value) === '[object Object]'

/**
 * 收到 401：立即完整清理本地登录态，弹窗只决定要不要跳登录页
 *
 * @param {string|null} requestToken 本次请求实际携带的 token（没带则为 null）
 */
function handleUnauthorized(requestToken) {
  const currentToken = getToken()
  if (requestToken && currentToken && requestToken !== currentToken) {
    return
  }
  useUserStore().clearSession()
  if (reLoginShowing) {
    return
  }
  reLoginShowing = true
  showConfirm('登录状态已过期，您可以继续留在该页面，或者重新登录?').then(
    (res) => {
      reLoginShowing = false
      if (res.confirm) {
        uni.reLaunch({ url: LOGIN_PAGE })
      }
    },
    () => {
      reLoginShowing = false
    }
  )
}

const request = config => {
  // 是否需要设置 token
  const isToken = (config.headers || {}).isToken === false
  config.header = config.header || {}
  if (!isToken && getToken() && !isSessionValid()) {
    useUserStore().ensureValidSession()
  }
  const requestToken = !isToken && isSessionValid() ? getToken() : null
  if (requestToken) {
    config.header['Authorization'] = 'Bearer ' + requestToken
  }
  // get请求映射params参数
  if (config.params) {
    let url = config.url + '?' + tansParams(config.params)
    url = url.slice(0, -1)
    config.url = url
  }
  return new Promise((resolve, reject) => {
    const requestUrl = config.baseUrl || baseUrl + config.url
    uni.request({
      method: config.method || 'get',
      timeout: config.timeout || timeout,
      url: requestUrl,
      data: config.data,
      header: config.header,
      dataType: 'json',
      success: (response) => {
        try {
          const res = response || {}
          const status = res.statusCode
          const body = res.data
          if (typeof status === 'number' && (status < 200 || status >= 300)) {
            console.error('[request] ' + requestUrl + ' HTTP ' + status + ' body=' + JSON.stringify(body).slice(0, 200))
            toast('接口异常（HTTP ' + status + '）')
            reject(status)
            return
          }
          if (!isPlainObject(body)) {
            console.error('[request] ' + requestUrl + ' 返回格式异常：' + JSON.stringify(body).slice(0, 200))
            toast('接口返回格式异常，请稍后重试')
            reject('bad-response')
            return
          }
          if (typeof body.code !== 'number') {
            console.error('[request] ' + requestUrl + ' 缺少业务码：' + JSON.stringify(body).slice(0, 200))
            toast('接口返回异常（缺少业务码）')
            reject('missing-code')
            return
          }
          const code = body.code
          // 后端业务提示语优先（如「手机号或密码错误」「手机号未注册」），本地错误码表只作兜底
          const msg = body.msg || errorCode[code] || errorCode['default']
          if (code === 401) {
            handleUnauthorized(requestToken)
            reject('无效的会话，或者会话已过期，请重新登录。')
            return
          }
          if (code !== 200) {
            toast(msg)
            reject(code)
            return
          }
          resolve(body)
        } catch (e) {
          console.error('[request] ' + requestUrl + ' 处理响应异常：' + (e && e.message))
          toast('接口处理异常，请稍后重试')
          reject(e)
        }
      },
      fail: (error) => {
        // 传输层失败：uni.request 给的是 { errMsg: 'request:fail ...' }，不是 axios 的 error.message
        const raw = (error && (error.errMsg || error.message)) || ''
        let message
        if (!raw) {
          message = '请求失败，请检查网络后重试'
        } else if (raw.indexOf('timeout') >= 0) {
          message = '系统接口请求超时'
        } else if (raw.indexOf('Request failed with status code') >= 0) {
          message = '系统接口' + raw.slice(-3) + '异常'
        } else if (raw.indexOf('Network Error') >= 0 || raw.indexOf('request:fail') >= 0) {
          // 后端没起、地址不对、或小程序没勾「不校验合法域名」都会走这里
          message = '接口连不上，请检查后端与「不校验合法域名」'
        } else {
          message = raw
        }
        // 完整信息留给控制台，Toast 只给一句能看懂的
        console.error('[request] ' + requestUrl + ' 失败：' + raw)
        toast(message)
        reject(error)
      }
    })
  })
}

export default request
