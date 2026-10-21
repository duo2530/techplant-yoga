const { assert } = require('./assert')

/**
 * 教练相关页面的端到端操作与读取辅助。
 *
 * 页面终态用稳定的业务类名判断，避免依赖 uni-app 内部结构；
 * 所有等待都走 Playwright 的条件等待，不使用固定延时同步业务结果。
 */

/** 首页「金牌教练」栏目终态：有卡片，或空/失败文案（「加载中」不算终态） */
const HOME_COACHES_READY = () => {
  const section = document.querySelector('.home-coaches')
  if (!section) {
    return false
  }
  if (section.querySelector('.home-coach')) {
    return true
  }
  const stateText = section.querySelector('.home-coaches__state-text')
  if (!stateText) {
    return false
  }
  const text = stateText.innerText.trim()
  return !!text && text.indexOf('加载中') < 0
}

/** 约教练页终态：资料面板出现，或参数错误/不存在/加载失败提示出现（不含「加载中」） */
const COACH_PAGE_READY = () => !!document.querySelector('.profile')
  || !!document.querySelector('.coach-state .ys-empty')
  || !!document.querySelector('.coach-state__btn')

async function registerRoutes(ctx, routes) {
  for (const route of routes || []) {
    await ctx.page.route(route.match, route.handler)
  }
}

/** 打开首页，并等「金牌教练」栏目进入终态 */
async function openHome(ctx, options = {}) {
  await registerRoutes(ctx, options.routes)
  await ctx.page.goto(ctx.h5Base + '/#/pages/home/index', { waitUntil: 'load' })
  await ctx.page.waitForSelector('uni-page-body', { timeout: 20000 })
  await ctx.page.waitForFunction(HOME_COACHES_READY, null, { timeout: 20000 })
}

/**
 * 打开约教练页。
 *
 * @param {Object} ctx 用例 helper
 * @param {string|undefined} coachId 不传则打开无参数地址（用于参数错误用例）
 * @param {Object} options { routes }
 */
async function openCoach(ctx, coachId, options = {}) {
  await registerRoutes(ctx, options.routes)
  const query = coachId === undefined || coachId === null ? '' : '?coachId=' + encodeURIComponent(String(coachId))
  await ctx.page.goto(ctx.h5Base + '/#/pages/coaches/index' + query, { waitUntil: 'load' })
  await ctx.page.waitForSelector('uni-page-body', { timeout: 20000 })
  await ctx.page.waitForFunction(COACH_PAGE_READY, null, { timeout: 20000 })
}

/** 首页金牌卡片上的可见信息（顺序即 DOM 顺序） */
function homeCoaches(ctx) {
  return ctx.page.evaluate(() =>
    Array.from(document.querySelectorAll('.home-coach')).map((card) => {
      const pick = (selector) => {
        const node = card.querySelector(selector)
        return node ? node.innerText.trim() : ''
      }
      return {
        name: pick('.home-coach__name'),
        intro: pick('.home-coach__intro'),
        avatarEmpty: !!card.querySelector('.home-coach__avatar--empty'),
        hasAvatar: !!card.querySelector('.home-coach__avatar') && !card.querySelector('.home-coach__avatar--empty')
      }
    }))
}

/** 排课卡片上的可见信息 */
function schedCards(ctx) {
  return ctx.page.evaluate(() =>
    Array.from(document.querySelectorAll('.sched-card')).map((card) => {
      const pick = (selector) => {
        const node = card.querySelector(selector)
        return node ? node.innerText.trim() : ''
      }
      return {
        name: pick('.sched-card__name'),
        type: pick('.sched-card__type'),
        status: pick('.sched-card__status'),
        lines: Array.from(card.querySelectorAll('.sched-card__line')).map((node) => node.innerText.trim()),
        remaining: pick('.sched-card__remaining'),
        coverEmpty: !!card.querySelector('.sched-card__cover--empty')
      }
    }))
}

/** 当前选中的课种 Tab 文案 */
async function activeTabText(ctx) {
  return (await ctx.page.locator('.tabs__text--on').innerText()).trim()
}

/** 切换到指定课种 Tab */
async function clickTab(ctx, label) {
  await ctx.page.locator('.tabs__item', { hasText: label }).click()
}

/** 等待排课卡片出现（数量可选） */
async function waitSchedCards(ctx, expected, timeout = 20000) {
  await ctx.page.waitForFunction((count) =>
    document.querySelectorAll('.sched-card').length === count, expected, { timeout })
}

/** 请求记录过滤 */
function requestsMatching(ctx, fragment) {
  return ctx.logs.requests.filter((url) => url.includes(fragment))
}

/** 打开约教练页后断言资料区字段 */
async function profileText(ctx) {
  return {
    name: (await ctx.page.locator('.profile__name').innerText()).trim(),
    intro: (await ctx.page.locator('.profile__intro').innerText()).trim(),
    galleryCount: await ctx.page.locator('.gallery__item').count()
  }
}

/** 相册缩略图的实际图片地址（按 DOM 顺序） */
function gallerySources(ctx) {
  return ctx.page.evaluate(() =>
    Array.from(document.querySelectorAll('.gallery__img')).map((node) =>
    {
      const img = node.tagName.toLowerCase() === 'img' ? node : node.querySelector('img')
      return img ? img.src : ''
    }))
}

/** 点击第 index 张相册图片，等 H5 图片预览打开 */
async function openGalleryPreview(ctx, index) {
  await ctx.page.locator('.gallery__item').nth(index).click()
  await ctx.page.waitForFunction(() => !!document.querySelector('uni-swiper'),
    null, { timeout: 10000 })
}

/** 读取 H5 图片预览的当前索引与图片地址（Swiper 位移换算） */
function previewState(ctx) {
  return ctx.page.evaluate(() =>
  {
    const swiper = document.querySelector('uni-swiper')
    if (!swiper)
    {
      return null
    }
    const frame = swiper.querySelector('.uni-swiper-slide-frame')
    const transform = frame ? frame.style.transform : ''
    const match = /translate\((-?[\d.]+)%/.exec(transform)
    return {
      current: match ? Math.round(-Number(match[1]) / 100) : -1,
      images: Array.from(swiper.querySelectorAll('img')).map((img) => img.src)
    }
  })
}

/** 等待图片预览停在指定索引 */
async function waitPreviewCurrent(ctx, index, timeout = 10000) {
  await ctx.page.waitForFunction((expected) =>
  {
    const swiper = document.querySelector('uni-swiper')
    if (!swiper)
    {
      return false
    }
    const frame = swiper.querySelector('.uni-swiper-slide-frame')
    const match = /translate\((-?[\d.]+)%/.exec(frame ? frame.style.transform : '')
    return !!match && Math.round(-Number(match[1]) / 100) === expected
  }, index, { timeout })
}

/** Node 侧轮询等待：用于同步拦截器捕获的请求/响应，不依赖固定时长 */
async function waitUntil(check, message = '等待条件超时', timeout = 10000) {
  const start = Date.now()
  while (!check())
  {
    if (Date.now() - start > timeout)
    {
      throw new Error(message)
    }
    await new Promise((resolve) => setTimeout(resolve, 25))
  }
}

/** 等两次渲染帧，让已到达的响应完成 DOM 更新 */
async function nextFrames(ctx) {
  await ctx.page.evaluate(() => new Promise((resolve) =>
  {
    requestAnimationFrame(() => requestAnimationFrame(resolve))
  }))
}

module.exports = {
  assert,
  openHome,
  openCoach,
  homeCoaches,
  schedCards,
  activeTabText,
  clickTab,
  waitSchedCards,
  requestsMatching,
  profileText,
  gallerySources,
  openGalleryPreview,
  previewState,
  waitPreviewCurrent,
  waitUntil,
  nextFrames,
  registerRoutes
}
