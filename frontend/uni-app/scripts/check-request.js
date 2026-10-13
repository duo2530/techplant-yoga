/**
 * `utils/request.js` + `utils/session.js` 的异常响应与登录态回归自检 —— 不装 npm 包、不起后端。
 *
 * 用法（PowerShell）：
 *   node frontend\uni-app\scripts\check-request.js
 *
 * 覆盖 2026-10-13 复审提出的第 2、3、4 项：
 *   · HTTP 502 / 空响应 / 非对象响应 / 缺业务码 → 必须 reject（不能当成功、不能挂起）
 *   · 会话过期（含历史缓存缺 expireTime）→ 不带 token、清本地登录态
 *   · 401 → 立即完整清理；点取消也回到游客态；并发只弹一次；旧请求迟到的 401 不动新会话
 *
 * 实现方式：把这两个 ESM 源文件就地转成 CommonJS 加载（不引入构建工具），
 * 只桩掉 `@/config`、`@/utils/auth`、`@/store/modules/user`、`@/utils/common`、`@/utils/errorCode`；
 * `@/utils/session` 用的是**真实源码**（过期判断要测真的那份实现）。
 */
const fs = require('fs')
const path = require('path')
const Module = require('module')

const ROOT = path.join(__dirname, '..')
const errors = []

function check(cond, name, extra) {
  if (cond) {
    console.log('  √ ' + name)
  } else {
    errors.push(name + (extra ? ' → ' + extra : ''))
    console.log('  × ' + name + (extra ? ' → ' + extra : ''))
  }
}

/** 把 ESM 源码转成 CommonJS 文本（只处理本工程用到的写法） */
function toCommonJs(src) {
  const exported = []
  let out = src
    .replace(/^import\s+\{([^}]+)\}\s+from\s+['"]([^'"]+)['"]\s*$/gm, "const {$1} = require('$2')")
    .replace(/^import\s+([A-Za-z0-9_$]+)\s+from\s+['"]([^'"]+)['"]\s*$/gm, "const $1 = require('$2')")
    .replace(/^export\s+default\s+([A-Za-z0-9_$]+)\s*$/gm, 'module.exports = $1')
  out = out.replace(/^export\s+(function|const|let|var)\s+([A-Za-z0-9_$]+)/gm, (m, kind, name) => {
    exported.push(name)
    return kind + ' ' + name
  })
  if (exported.length) {
    out += '\nmodule.exports = { ' + exported.join(', ') + ' }\n'
  }
  return out
}

/** 用给定的 require 解析器加载一个源文件（每次都是全新模块实例） */
function loadSource(relPath, resolver) {
  const filename = path.join(ROOT, relPath)
  const src = toCommonJs(fs.readFileSync(filename, 'utf8'))
  const mod = new Module(filename, null)
  mod.filename = filename
  mod.paths = Module._nodeModulePaths(path.dirname(filename))
  const previous = Module._load
  Module._load = function (request) {
    const stubbed = resolver(request)
    if (stubbed !== undefined) {
      return stubbed
    }
    return previous.apply(this, arguments)
  }
  try {
    mod._compile(src, filename)
  } finally {
    Module._load = previous
  }
  return mod.exports
}

/** 造一份「本地存储 + store」的可控环境 */
function newEnv(overrides) {
  const env = Object.assign(
    {
      token: 'token-current',
      member: {
        memberId: '1856739201475235902',
        memberName: '忻会员8000',
        phone: '13800138000',
        expireTime: Date.now() + 7 * 24 * 3600 * 1000
      },
      toasts: [],
      confirms: 0,
      confirmAnswer: true,
      clears: 0,
      ensures: 0,
      reLaunches: []
    },
    overrides || {}
  )
  env.auth = {
    getToken: () => env.token,
    setToken: (t) => {
      env.token = t
    },
    removeToken: () => {
      env.token = ''
    },
    getMemberInfo: () => env.member,
    setMemberInfo: (i) => {
      env.member = i
    },
    removeMemberInfo: () => {
      env.member = null
    }
  }
  const sessionValid = () => {
    if (!env.auth.getToken()) return false
    const info = env.auth.getMemberInfo() || {}
    const expire = Number(info.expireTime)
    if (!expire || !isFinite(expire)) return false
    return expire > Date.now()
  }
  env.isSessionValid = sessionValid
  env.store = {
    clearSession: () => {
      env.clears += 1
      env.auth.removeToken()
      env.auth.removeMemberInfo()
    },
    ensureValidSession: () => {
      env.ensures += 1
      if (sessionValid()) return true
      env.store.clearSession()
      return false
    }
  }
  return env
}

/** 加载真实 request.js（@/utils/session 也用真实源码） */
function loadRequest(env) {
  return loadSource('utils/request.js', (request) => {
    if (request === '@/config') return { baseUrl: 'http://stub:8080' }
    if (request === '@/utils/errorCode') {
      return {
        401: '认证失败，无法访问系统资源',
        403: '当前操作没有权限',
        404: '访问资源不存在',
        default: '系统未知错误，请反馈给管理员'
      }
    }
    if (request === '@/utils/auth') return env.auth
    if (request === '@/store/modules/user') return { useUserStore: () => env.store }
    if (request === '@/utils/common') {
      return {
        toast: (msg) => env.toasts.push(String(msg)),
        showConfirm: () => {
          env.confirms += 1
          return Promise.resolve({ confirm: env.confirmAnswer })
        },
        tansParams: () => ''
      }
    }
    if (request === '@/utils/session') {
      return loadSource('utils/session.js', (inner) => (inner === '@/utils/auth' ? env.auth : undefined))
    }
    return undefined
  })
}

/** 一个「客户端」：同一个 request 模块实例 + 可手动触发的响应列表（并发用例要共用实例） */
function makeClient(env) {
  const request = loadRequest(env)
  const pending = []
  global.uni = {
    request: (cfg) => pending.push(cfg),
    reLaunch: (o) => env.reLaunches.push(o.url),
    showToast: () => {},
    showModal: () => {}
  }
  return { request, pending, send: (cfg, response) => cfg.success(response) }
}

/** 等 Promise settle；超时说明「一直挂起」（这正是要防的 bug） */
function settled(promise, ms) {
  return Promise.race([
    promise.then(() => ({ resolved: true }), (e) => ({ rejected: true, error: e })),
    new Promise((resolve) => setTimeout(() => resolve({ timedOut: true }), ms || 800))
  ])
}

async function main() {
  console.log('request.js / session.js 异常与登录态自检\n')

  // ---------- 1. HTTP 状态异常 ----------
  console.log('HTTP 层')
  {
    const env = newEnv()
    const c = makeClient(env)
    const r = await settled(single(c, { statusCode: 502, data: '<html>502 Bad Gateway</html>' }))
    check(r.rejected === true, 'HTTP 502 + HTML 响应 → reject（不再当成功）')
    check(env.toasts.some((t) => t.indexOf('502') >= 0), 'HTTP 502 → toast 里带状态码', JSON.stringify(env.toasts))
  }
  {
    const env = newEnv()
    const c = makeClient(env)
    const r = await settled(single(c, { statusCode: 404, data: '<html>404 Not Found</html>' }))
    check(r.rejected === true, 'HTTP 404 错误页 → reject')
  }

  // ---------- 2. 响应结构异常 ----------
  console.log('响应结构')
  {
    const env = newEnv()
    const c = makeClient(env)
    const r = await settled(single(c, { statusCode: 200, data: null }))
    check(r.timedOut !== true, 'data 为 null → Promise 必须 settle（不能一直挂起）')
    check(r.rejected === true, 'data 为 null → reject')
    check(env.toasts.length > 0, 'data 为 null → 有错误提示')
  }
  {
    const env = newEnv()
    const c = makeClient(env)
    const r = await settled(single(c, { statusCode: 200, data: 'oops' }))
    check(r.rejected === true, '非对象响应（字符串）→ reject')
  }
  {
    const env = newEnv()
    const c = makeClient(env)
    const r = await settled(single(c, { statusCode: 200, data: { msg: 'ok' } }))
    check(r.rejected === true, '缺少业务码 → reject（不再默认 200 成功）')
    check(env.toasts.some((t) => t.indexOf('业务码') >= 0), '缺少业务码 → 提示语点明原因', JSON.stringify(env.toasts))
  }

  // ---------- 3. 正常与业务失败 ----------
  console.log('正常 / 业务失败')
  {
    const env = newEnv()
    const c = makeClient(env)
    const r = await settled(single(c, { statusCode: 200, data: { code: 200, msg: '登录成功', data: { token: 't' } } }))
    check(r.resolved === true, 'code=200 → resolve')
    check(env.toasts.length === 0, 'code=200 不 toast')
  }
  {
    const env = newEnv()
    const c = makeClient(env)
    const r = await settled(single(c, { statusCode: 200, data: { code: 409, msg: '手机号或密码错误' } }))
    check(r.rejected === true, 'code=409 → reject')
    check(env.toasts.indexOf('手机号或密码错误') >= 0, 'code=409 → 透出后端文案', JSON.stringify(env.toasts))
  }
  {
    const env = newEnv()
    const c = makeClient(env)
    await settled(single(c, { statusCode: 200, data: { code: 404, msg: '手机号未注册' } }))
    check(env.toasts.indexOf('手机号未注册') >= 0, 'code=404 → 后端文案优先于本地错误码表', JSON.stringify(env.toasts))
  }

  // ---------- 4. 传输层失败 ----------
  console.log('传输层失败')
  {
    const env = newEnv()
    const c = makeClient(env)
    const p = c.request({ url: '/api/auth/login', method: 'post' })
    c.pending[0].fail({ errMsg: 'request:fail url not in domain list' })
    const r = await settled(p)
    check(r.rejected === true, 'uni.request fail → reject')
    check(env.toasts.some((t) => t.indexOf('接口连不上') >= 0), '连接失败 → 可读提示', JSON.stringify(env.toasts))
  }

  // ---------- 5. 会话过期 ----------
  console.log('会话过期（第 3 项）')
  {
    const env = newEnv({ member: { memberId: '1', memberName: 'X', phone: 'p', expireTime: Date.now() - 1000 } })
    const c = makeClient(env)
    const p = c.request({ url: '/api/stores', method: 'get' })
    const header = c.pending[0].header || {}
    c.pending[0].success({ statusCode: 200, data: { code: 200, msg: 'ok', data: {} } })
    await settled(p)
    check(!header.Authorization, '过期会话：请求不再携带 Authorization')
    check(env.ensures >= 1 && env.clears >= 1, '过期会话：统一读取逻辑已清理本地登录态', 'ensures=' + env.ensures + ' clears=' + env.clears)
    check(!env.token && !env.member, '过期会话：token 与会员展示信息都被清掉')
  }
  {
    const env = newEnv({ member: { memberId: '1', memberName: 'X', phone: 'p' } }) // 历史缓存：没有 expireTime
    const session = loadSource('utils/session.js', (r) => (r === '@/utils/auth' ? env.auth : undefined))
    check(session.isSessionValid() === false, '历史缓存缺 expireTime → 视为无效，要求重新登录')
  }
  {
    const env = newEnv()
    const session = loadSource('utils/session.js', (r) => (r === '@/utils/auth' ? env.auth : undefined))
    check(session.isSessionValid() === true, '有效会话（token + 未到期的 expireTime）→ isValid=true')
    check(session.isValidLoginPayload({ token: 't', memberId: '1', expireTime: 1 }) === true, '登录返回体完整 → 校验通过')
    check(session.isValidLoginPayload({ token: 't', memberId: '1' }) === false, '缺 expireTime 的登录返回体 → 校验不通过')
    check(session.isValidLoginPayload(null) === false, '空登录返回体 → 校验不通过')
  }

  // ---------- 6. 401 处理 ----------
  console.log('401 处理（第 4 项）')
  {
    const env = newEnv()
    const c = makeClient(env)
    const r = await settled(single(c, { statusCode: 200, data: { code: 401, msg: '登录状态已失效' } }))
    check(r.rejected === true, '401 → reject')
    check(env.clears === 1, '401 → 立即完整清理本地登录态（不依赖弹窗选择）', 'clears=' + env.clears)
    check(env.confirms === 1, '401 → 弹一次重新登录确认框')
    check(env.reLaunches.length === 1, '点确定 → 跳登录页')
  }
  {
    const env = newEnv({ confirmAnswer: false })
    const c = makeClient(env)
    await settled(single(c, { statusCode: 200, data: { code: 401 } }))
    check(env.clears === 1, '点取消 → 登录态依然被清理（身份回到游客态）')
    check(env.reLaunches.length === 0, '点取消 → 不跳转，留在当前页')
  }
  {
    // 并发两个 401：同一个 request 模块实例，只应弹一次
    const env = newEnv()
    const c = makeClient(env)
    const p1 = c.request({ url: '/api/stores', method: 'get' })
    const p2 = c.request({ url: '/api/schedules', method: 'get' })
    c.pending.forEach((cfg) => cfg.success({ statusCode: 200, data: { code: 401 } }))
    await Promise.all([settled(p1), settled(p2)])
    check(env.confirms === 1, '并发两个 401 → 只弹一次重新登录框', 'confirms=' + env.confirms)
  }
  {
    // 旧请求迟到的 401：期间用户已经用新会话登录
    const env = newEnv()
    const c = makeClient(env)
    const p = c.request({ url: '/api/auth/logout', method: 'post' })
    env.token = 'token-newly-logged-in'
    c.pending[0].success({ statusCode: 200, data: { code: 401 } })
    await settled(p)
    check(env.clears === 0, '旧请求迟到的 401 → 不清除刚登录的新会话', 'clears=' + env.clears)
    check(env.token === 'token-newly-logged-in', '新 token 保持不变')
  }

  console.log('')
  if (errors.length) {
    console.log('未通过 ' + errors.length + ' 项：')
    errors.forEach((e) => console.log('  - ' + e))
    process.exit(1)
  }
  console.log('全部通过')
}

/** 发一个请求并立刻用给定响应回复它 */
function single(client, response) {
  const promise = client.request({ url: '/api/auth/login', method: 'post' })
  client.pending[0].success(response)
  return promise
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
