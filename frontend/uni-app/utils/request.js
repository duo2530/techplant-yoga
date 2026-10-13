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
 * - **先清理再弹窗**：用户点取消也要回到游客态，不能留着失效 token
 *   （2026-10-13 复审：原来清理写在「确定」分支里，取消就什么都没清）
 * - **只清当前会话**：`requestToken` 与当前 token 不一致说明是旧请求迟到的 401，
 *   不能把用户刚登录拿到的新会话清掉
 * - **并发只弹一次**：多个请求同时 401 时共用一个弹窗
 *
 * @param {string|null} requestToken 本次请求实际携带的 token（没带则为 null）
 */
function handleUnauthorized(requestToken) {
  const currentToken = getToken()
  if (requestToken && currentToken && requestToken !== currentToken) {
    // 旧会话迟到的 401：忽略，别动新会话
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
  // 本地登录态已过期（含历史缓存缺过期时间）：先清干净，本次请求不再带它
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
    // 用 success / fail 回调而不是 Promise 链：两条路径都必定给出反馈
    // （Promise 链写法在失败时如果处理函数本身抛错，外层 Promise 会永远不 settle，
    //  页面里再 .catch 一吞，界面上就是「点了没反应」——2026-10-13 踩过）
    const requestUrl = config.baseUrl || baseUrl + config.url
    uni.request({
      method: config.method || 'get',
      timeout: config.timeout || timeout,
      url: requestUrl,
      data: config.data,
      header: config.header,
      dataType: 'json',
      success: (response) => {
        // 整个处理过程都包在 try 里：万一取字段抛错，也必须 reject，不能让 Promise 一直挂起
        try {
          const res = response || {}
          const status = res.statusCode
          const body = res.data
          // ① HTTP 层：502／404 的 HTML 错误页不能当成业务成功（2026-10-13 复审修复）
          if (typeof status === 'number' && (status < 200 || status >= 300)) {
            console.error('[request] ' + requestUrl + ' HTTP ' + status + ' body=' + JSON.stringify(body).slice(0, 200))
            toast('接口异常（HTTP ' + status + '）')
            reject(status)
            return
          }
          // ② 响应结构：必须是 {code,msg,data} 形状的普通对象
          if (!isPlainObject(body)) {
            console.error('[request] ' + requestUrl + ' 返回格式异常：' + JSON.stringify(body).slice(0, 200))
            toast('接口返回格式异常，请稍后重试')
            reject('bad-response')
            return
          }
          // ③ 业务码必须存在且是数字：不再用 `code || 200` 把缺失业务码当成成功
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
