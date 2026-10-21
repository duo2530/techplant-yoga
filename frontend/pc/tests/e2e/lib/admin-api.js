const { config } = require('./config')

/** 真实后端接口调用：登录、验证码开关、教练资料与等级读写。 */

async function apiRequest(pathname, { token, method = 'GET', body } = {})
{
  const headers = { accept: 'application/json' }
  if (body !== undefined)
  {
    headers['content-type'] = 'application/json;charset=utf-8'
  }
  if (token)
  {
    headers.authorization = `Bearer ${token}`
  }
  const response = await fetch(config.apiBase + pathname, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000)
  })
  const text = await response.text()
  let data
  try
  {
    data = text ? JSON.parse(text) : {}
  }
  catch (error)
  {
    throw new Error(`${method} ${pathname} 返回了非 JSON 响应（HTTP ${response.status}）：${text.slice(0, 200)}`)
  }
  return { status: response.status, body: data }
}

/** 后端是否开启登录图形验证码 */
async function apiCaptchaEnabled()
{
  const { body } = await apiRequest('/captchaImage')
  return body.captchaEnabled === undefined ? true : body.captchaEnabled
}

/** 管理员账号登录，返回后端 token */
async function apiAdminLogin(overrides = {})
{
  const username = overrides.username || config.adminUsername
  const password = overrides.password || config.adminPassword
  const { body } = await apiRequest('/login', {
    method: 'POST',
    body: { username, password, code: '', uuid: '' }
  })
  if (body.code !== 200 || !body.token)
  {
    throw new Error(`管理员登录失败（${username}）：${JSON.stringify(body)}`)
  }
  return { username, token: body.token }
}

async function apiListCoaches(token, params = {})
{
  const query = new URLSearchParams({
    pageNum: String(params.pageNum || 1),
    pageSize: String(params.pageSize || 100)
  })
  if (params.name)
  {
    query.set('name', params.name)
  }
  const { body } = await apiRequest(`/admin/coaches?${query.toString()}`, { token })
  if (body.code !== 200)
  {
    throw new Error(`查询教练列表失败：${JSON.stringify(body)}`)
  }
  return body
}

async function apiGetCoach(token, coachId)
{
  const { body } = await apiRequest(`/admin/coaches/${coachId}`, { token })
  if (body.code !== 200)
  {
    throw new Error(`查询教练详情失败：${JSON.stringify(body)}`)
  }
  return body.data
}

async function apiCreateCoach(token, data)
{
  const { body } = await apiRequest('/admin/coaches', { token, method: 'POST', body: data })
  if (body.code !== 200)
  {
    throw new Error(`新增教练失败：${JSON.stringify(body)}`)
  }
}

async function apiUpdateCoach(token, coachId, data)
{
  const { body } = await apiRequest(`/admin/coaches/${coachId}`, { token, method: 'PUT', body: data })
  if (body.code !== 200)
  {
    throw new Error(`修改教练失败：${JSON.stringify(body)}`)
  }
}

async function apiUpdateCoachLevel(token, coachId, level)
{
  const { body } = await apiRequest(`/admin/coaches/${coachId}/level?level=${level}`, {
    token,
    method: 'PUT'
  })
  if (body.code !== 200)
  {
    throw new Error(`设置教练等级失败（level=${level}）：${JSON.stringify(body)}`)
  }
}

/** 直接返回后端统一响应体，用于断言非法等级、无权限等失败分支的业务码 */
async function apiUpdateCoachLevelRaw(token, coachId, level)
{
  const { body } = await apiRequest(`/admin/coaches/${coachId}/level?level=${level}`, {
    token,
    method: 'PUT'
  })
  return body
}

async function apiCreateRole(token, data)
{
  const { body } = await apiRequest('/system/role', { token, method: 'POST', body: data })
  if (body.code !== 200)
  {
    throw new Error(`新增角色失败：${JSON.stringify(body)}`)
  }
}

async function apiListRoles(token, params = {})
{
  const query = new URLSearchParams({ pageNum: '1', pageSize: '100' })
  if (params.roleKey)
  {
    query.set('roleKey', params.roleKey)
  }
  const { body } = await apiRequest(`/system/role/list?${query.toString()}`, { token })
  if (body.code !== 200)
  {
    throw new Error(`查询角色列表失败：${JSON.stringify(body)}`)
  }
  return body
}

async function apiDeleteRole(token, roleId)
{
  const { body } = await apiRequest(`/system/role/${roleId}`, { token, method: 'DELETE' })
  if (body.code !== 200)
  {
    throw new Error(`删除角色失败：${JSON.stringify(body)}`)
  }
}

async function apiCreateUser(token, data)
{
  const { body } = await apiRequest('/system/user', { token, method: 'POST', body: data })
  if (body.code !== 200)
  {
    throw new Error(`新增用户失败：${JSON.stringify(body)}`)
  }
}

async function apiListUsers(token, params = {})
{
  const query = new URLSearchParams({ pageNum: '1', pageSize: '100' })
  if (params.userName)
  {
    query.set('userName', params.userName)
  }
  const { body } = await apiRequest(`/system/user/list?${query.toString()}`, { token })
  if (body.code !== 200)
  {
    throw new Error(`查询用户列表失败：${JSON.stringify(body)}`)
  }
  return body
}

async function apiDeleteUser(token, userId)
{
  const { body } = await apiRequest(`/system/user/${userId}`, { token, method: 'DELETE' })
  if (body.code !== 200)
  {
    throw new Error(`删除用户失败：${JSON.stringify(body)}`)
  }
}

async function apiDeleteCoach(token, coachId)
{
  const { body } = await apiRequest(`/admin/coaches/${coachId}`, { token, method: 'DELETE' })
  if (body.code !== 200)
  {
    throw new Error(`删除教练失败：${JSON.stringify(body)}`)
  }
}

module.exports = {
  apiCaptchaEnabled,
  apiAdminLogin,
  apiListCoaches,
  apiGetCoach,
  apiCreateCoach,
  apiUpdateCoach,
  apiUpdateCoachLevel,
  apiUpdateCoachLevelRaw,
  apiDeleteCoach,
  apiCreateRole,
  apiListRoles,
  apiDeleteRole,
  apiCreateUser,
  apiListUsers,
  apiDeleteUser
}
