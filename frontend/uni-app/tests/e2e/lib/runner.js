const fs = require('fs')
const path = require('path')
const { config } = require('./config')
const { SkipError } = require('./assert')
const { apiMemberCards, seedSession, clearSession } = require('./session')

/**
 * 用例执行器。
 *
 * 每个用例一个独立的浏览器上下文：失败时留截图、trace 与日志到 artifacts/<时间戳>/，其它用例继续执行。
 */

/** 会员卡页面可能出现的四种终态选择器（含未登录被送到登录页） */
const MEMBER_CARDS_READY = () => {
  if (location.hash.includes('quick-login') || location.hash.includes('phone-auth')) {
    return true
  }
  return !!document.querySelector('.card, .cards__error, .ys-empty')
}

function slug(name)
{
  return name.replace(/[^\u4e00-\u9fa5a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 40)
}

function timestamp()
{
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
}

async function createHelper({ browser, h5Base, memberSession, caseName, runId })
{
  const context = await browser.newContext({
    viewport: config.viewport,
    deviceScaleFactor: config.deviceScaleFactor,
    isMobile: true,
    hasTouch: true
  })
  await context.tracing.start({ screenshots: true, snapshots: true })
  const page = await context.newPage()

  const logs = { console: [], failed: [], requests: [] }
  page.on('console', (message) => {
    if (message.type() === 'error') {
      logs.console.push(message.text().slice(0, 300))
    }
  })
  page.on('requestfailed', (request) => {
    logs.failed.push(request.url())
  })
  page.on('request', (request) => {
    if (request.url().includes('/api/')) {
      logs.requests.push(request.url())
    }
  })

  const helper = {
    page,
    context,
    logs,
    h5Base,
    memberSession,
    env: config,

    /** 记录到的会员卡接口请求，用于断言「页面恢复时重新拉取」 */
    memberCardRequests()
    {
      return logs.requests.filter((url) => url.includes('/api/member-cards'))
    },

    /** 打开会员卡页：session 取 member／none，routes 为 page.route 的拦截规则 */
    async openMemberCards({ session = 'member', routes = [] } = {})
    {
      for (const route of routes) {
        await page.route(route.match, route.handler)
      }
      await page.goto(h5Base + '/#/pages/home/index', { waitUntil: 'load' })
      await page.waitForSelector('uni-page-body', { timeout: 20000 })
      if (session === 'member') {
        await seedSession(page, memberSession)
      } else {
        await clearSession(page)
      }
      await page.goto(h5Base + '/#/pages/member-cards/index', { waitUntil: 'load' })
      await page.waitForFunction(MEMBER_CARDS_READY, null, { timeout: 20000 })
      await page.waitForTimeout(300)
    },

    /** 离开会员卡页再返回，触发页面 onShow（用于「恢复后刷新」用例） */
    async leaveAndReturn()
    {
      await page.goto(h5Base + '/#/pages/messages/index', { waitUntil: 'load' })
      await page.waitForTimeout(1000)
      await page.goBack()
      await page.waitForFunction(MEMBER_CARDS_READY, null, { timeout: 20000 })
      await page.waitForTimeout(1200)
    },

    /** 当前登录会员的会员卡（期望值来源：同一后端的列表接口） */
    fetchCards()
    {
      return apiMemberCards(memberSession.token, { pageNum: 1, pageSize: 100 })
    },

    /** DOM 上每张卡的展示字段（按卡号可与接口逐条对应） */
    cardMetrics()
    {
      return page.evaluate(() =>
        Array.from(document.querySelectorAll('.card')).map((card) => {
          const bar = card.querySelector('.bar')
          const fill = card.querySelector('.bar__fill')
          const pick = (selector) => {
            const node = card.querySelector(selector)
            return node ? node.innerText.trim() : ''
          }
          return {
            cardNo: pick('.card__no'),
            name: pick('.card__name'),
            status: pick('.pill__text'),
            category: pick('.card__chip-text'),
            valid: pick('.card__valid'),
            store: pick('.card__store-text'),
            statLabels: Array.from(card.querySelectorAll('.stat__label')).map((node) => node.innerText.trim()),
            statValues: Array.from(card.querySelectorAll('.stat__value')).map((node) => node.innerText.trim()),
            barWidth: bar ? bar.offsetWidth : null,
            fillWidth: fill ? fill.offsetWidth : null
          }
        })
      )
    },

    /** 页面可见文本（换行折叠），用于断言提示语 */
    async bodyText()
    {
      return (await page.locator('body').innerText()).replace(/\n+/g, ' | ')
    },

    count(selector)
    {
      return page.locator(selector).count()
    }
  }

  helper.dispose = async () => {
    await context.close()
  }

  helper.saveArtifacts = async (error) => {
    const dir = path.join(config.artifactsDir, runId)
    fs.mkdirSync(dir, { recursive: true })
    const base = path.join(dir, slug(caseName))
    try {
      await page.screenshot({ path: base + '.png', fullPage: true })
    } catch (screenshotError) {
      // 截图失败不掩盖原始断言失败
    }
    try {
      await context.tracing.stop({ path: base + '.trace.zip' })
    } catch (traceError) {
      // trace 失败不掩盖原始断言失败
    }
    fs.writeFileSync(
      base + '.log.txt',
      [
        `用例：${caseName}`,
        `错误：${error && error.stack ? error.stack : String(error)}`,
        `页面地址：${page.url()}`,
        `会员卡接口请求：${JSON.stringify(helper.memberCardRequests(), null, 2)}`,
        `控制台错误：${JSON.stringify(logs.console, null, 2)}`,
        `失败请求：${JSON.stringify(logs.failed, null, 2)}`
      ].join('\n'),
      'utf8'
    )
    return dir
  }

  return helper
}

/** 按顺序执行一组用例，返回 { passed, failed, skipped, artifacts } */
async function runSuite({ suite, browser, h5Base, memberSession, runId })
{
  const results = []
  console.log(`\n${suite.name}`)
  for (const testCase of suite.cases) {
    const helper = await createHelper({ browser, h5Base, memberSession, caseName: testCase.name, runId })
    try {
      await testCase.run(helper)
      console.log(`  √ ${testCase.name}`)
      results.push({ name: testCase.name, status: 'passed' })
    } catch (error) {
      if (error instanceof SkipError) {
        console.log(`  - ${testCase.name}（跳过：${error.message}）`)
        results.push({ name: testCase.name, status: 'skipped', message: error.message })
      } else {
        console.log(`  × ${testCase.name}`)
        console.log(`      ${error && error.message ? error.message : error}`)
        const dir = await helper.saveArtifacts(error)
        results.push({ name: testCase.name, status: 'failed', message: String(error && error.message), artifacts: dir })
      }
    } finally {
      await helper.dispose()
    }
  }

  const passed = results.filter((item) => item.status === 'passed').length
  const failed = results.filter((item) => item.status === 'failed')
  const skipped = results.filter((item) => item.status === 'skipped').length
  return { name: suite.name, results, passed, failed, skipped }
}

/** 生成默认的拦截响应 */
function jsonRoute(body)
{
  return (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body)
  })
}

module.exports = { runSuite, jsonRoute, timestamp }
