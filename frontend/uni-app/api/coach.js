/**
 * 教练接口（用户端）—— **一期不存在**
 *
 * 一期用户端不提供教练接口（BR-用户端-010），此处仅为兼容保留页面的编译而保留同名导出，
 * 教练信息一律随 `/api/schedules` 带出（卡片 `coachName`，详情 `coachName` / `coachAvatarUrl` / `coachIntro`）。
 *
 * 后端 W3 已删除 `CoachPublicController`，`GET /api/coaches*` 不再存在，
 * 所以本文件**不再发任何请求**：保留同名导出只为让「一期不接入但保留」的旧页面
 * （`pages/coaches` 约教练）照常编译，统一返回空结果；
 * 该页将来重新接入时，再按新契约重写这一层。
 */

/** 旧「首页金牌教练」：一期首页已无该区块，恒返回空数组 */
export function getFeaturedCoaches() {
  return Promise.resolve([])
}

/** 旧「用户端教练分页列表」：一期接口不存在，恒返回空分页 */
export function listCoaches() {
  return Promise.resolve({ list: [], total: 0 })
}
