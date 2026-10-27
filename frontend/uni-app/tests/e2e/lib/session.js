const { config } = require('./config')

/** 真实后端会话与 H5 本地存储口径。 */

/** 会员登录（联调种子会员），返回后端 LoginVO */
async function apiLogin(overrides = {})
{
  const phone = overrides.phone || config.memberPhone
  const password = overrides.password || config.memberPassword
  const response = await fetch(config.apiBase + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone, password })
  })
  const body = await response.json()
  if (body.code !== 200)
  {
    throw new Error(`登录失败（${phone}）：${JSON.stringify(body)}`)
  }
  return Object.assign({}, body.data, { phone })
}

/** 按 token 取「我的会员卡」，用作端到端断言的期望值来源 */
async function apiMemberCards(token, params = {})
{
  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 100
  const response = await fetch(`${config.apiBase}/api/member-cards?pageNum=${pageNum}&pageSize=${pageSize}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const body = await response.json()
  if (body.code !== 200)
  {
    throw new Error(`查询我的会员卡失败：${JSON.stringify(body)}`)
  }
  return body
}

/** 按 token 取「我的预约」，用作端到端断言的期望值来源 */
async function apiReservations(token, params = {})
{
  const query = new URLSearchParams({
    pageNum: params.pageNum || 1,
    pageSize: params.pageSize || 100
  })
  if (params.status) {
    query.set('status', params.status)
  }
  const response = await fetch(`${config.apiBase}/api/reservations?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const body = await response.json()
  if (body.code !== 200)
  {
    throw new Error(`查询我的预约失败：${JSON.stringify(body)}`)
  }
  return body
}

/** 取消预约（用例准备数据用：同一排课不允许重复预约） */
async function apiCancelReservation(token, reservationId)
{
  const response = await fetch(`${config.apiBase}/api/reservations/${reservationId}/cancel`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` }
  })
  const body = await response.json()
  if (body.code !== 200)
  {
    throw new Error(`取消预约失败：${JSON.stringify(body)}`)
  }
  return body
}

/** 会员用指定会员卡预约一节排课（用例准备热门课程的预约量） */
async function apiCreateReservation(token, params)
{
  const response = await fetch(`${config.apiBase}/api/reservations`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      requestId: crypto.randomUUID(),
      scheduleId: String(params.scheduleId),
      memberCardId: String(params.memberCardId),
      persons: params.persons || 1
    })
  })
  const body = await response.json()
  if (body.code !== 200)
  {
    throw new Error(`预约失败：${JSON.stringify(body)}`)
  }
  return body
}

/** 免登录查询排课列表，用作预约流程的期望值来源 */
async function apiSchedules(params = {})
{
  const query = new URLSearchParams({
    storeId: params.storeId,
    courseType: params.courseType,
    date: params.date,
    pageNum: 1,
    pageSize: 100
  })
  const response = await fetch(`${config.apiBase}/api/schedules?${query.toString()}`)
  const body = await response.json()
  if (body.code !== 200)
  {
    throw new Error(`查询排课失败：${JSON.stringify(body)}`)
  }
  return body
}

/**
 * 写入 H5 本地会话
 *
 * uni-app H5 的本地存储口径：字符串直接存，对象存 {type:'object', data}；
 * 页面用 @/utils/session 判断登录态（token + 未过期的 expireTime）。
 */
async function seedSession(page, session)
{
  await page.evaluate((data) =>
  {
    const member = {
      memberId: data.memberId,
      memberName: data.memberName,
      phone: data.phone,
      expireTime: Number(data.expireTime)
    }
    localStorage.setItem('App-Token', data.token)
    localStorage.setItem('App-Member', JSON.stringify({ type: 'object', data: member }))
  }, session)
}

async function clearSession(page)
{
  await page.evaluate(() =>
  {
    localStorage.removeItem('App-Token')
    localStorage.removeItem('App-Member')
  })
}

module.exports = {
  apiLogin,
  apiMemberCards,
  apiReservations,
  apiCancelReservation,
  apiCreateReservation,
  apiSchedules,
  seedSession,
  clearSession
}
