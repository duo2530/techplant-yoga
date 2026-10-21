const fs = require('fs')
const path = require('path')
const { config } = require('./config')
const { assert, AssertionError } = require('./assert')
const { apiListCoaches, apiGetCoach, apiUpdateCoachLevel } = require('./admin-api')

/**
 * 用例执行器。
 *
 * 每个用例一个独立的浏览器上下文：失败时留截图、trace 与日志到 artifacts/<时间戳>/，其它用例继续执行。
 * 登录态由真实登录接口取得的 token 写入 Admin-Token Cookie；Cookie 名与 `src/utils/auth.js` 一致。
 */

const TOKEN_COOKIE = 'Admin-Token'
const COACH_PAGE_PATH = '/operation/coach'
const SEARCH_PLACEHOLDER = '请输入教练姓名'

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

async function poll(fn, { timeout = 15000, interval = 200, message = '等待超时' } = {})
{
  const deadline = Date.now() + timeout
  let lastError
  while (Date.now() < deadline)
  {
    try
    {
      const value = await fn()
      if (value)
      {
        return value
      }
    }
    catch (error)
    {
      lastError = error
    }
    await new Promise((resolve) => setTimeout(resolve, interval))
  }
  throw new AssertionError(message + (lastError ? `（最后错误：${lastError.message}）` : ''))
}

async function createHelper({ browser, baseUrl, session, fixture, caseName, runId })
{
  const context = await browser.newContext({ viewport: config.viewport, baseURL: baseUrl })
  await context.tracing.start({ screenshots: true, snapshots: true })
  const page = await context.newPage()

  const logs = { console: [], failed: [], requests: [] }
  page.on('console', (message) =>
  {
    if (message.type() === 'error')
    {
      logs.console.push(message.text().slice(0, 300))
    }
  })
  page.on('requestfailed', (request) =>
  {
    logs.failed.push(request.url())
  })
  page.on('request', (request) =>
  {
    const url = request.url()
    if (url.includes('/prod-api/'))
    {
      logs.requests.push({ method: request.method(), url, postData: request.postData() || '' })
    }
  })

  const helper = { page, context, logs, baseUrl, session, fixture, env: config }

  helper.requireFixture = () =>
  {
    assert.ok(fixture, '测试教练夹具未创建，用例无法执行')
  }

  helper.seedAdminCookie = async (activeSession = session) =>
  {
    await context.clearCookies()
    await context.addCookies([{ name: TOKEN_COOKIE, value: activeSession.token, url: baseUrl }])
  }

  /** getInfo 返回的初始密码提示会盖住页面，用例只关心页面内容，直接取消该提示 */
  helper.dismissPasswordPrompt = async () =>
  {
    const box = page.locator('.el-message-box')
    await box.waitFor({ state: 'visible', timeout: 2000 }).catch(() => {})
    if (!(await box.count()) || !(await box.first().isVisible().catch(() => false)))
    {
      return
    }
    const text = await box.first().innerText()
    if (text.includes('密码'))
    {
      await box.first().getByRole('button', { name: '取消' }).click().catch(() => {})
      await box.first().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
    }
  }

  helper.loginViaUi = async () =>
  {
    await context.clearCookies()
    await page.goto(baseUrl + '/login', { waitUntil: 'load' })
    await page.getByPlaceholder('账号').waitFor({ state: 'visible', timeout: 20000 })
    await page.getByPlaceholder('账号').fill(config.adminUsername)
    await page.getByPlaceholder('密码').fill(config.adminPassword)
    await page.locator('.login-form .el-button--primary').click()
    try
    {
      await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30000 })
    }
    catch (error)
    {
      const toast = page.locator('.el-message--error, .el-notification').first()
      const detail = (await toast.count()) ? `，提示：${(await toast.innerText()).trim()}` : ''
      throw new AssertionError(`登录失败或未跳转（当前地址：${page.url().replace(baseUrl, '')}${detail}）`)
    }
    await page.locator('.sidebar-container .el-menu--vertical').waitFor({ state: 'visible', timeout: 30000 })
  }

  helper.openCoachList = async ({ searchName, session: activeSession = session } = {}) =>
  {
    helper.requireFixture()
    await helper.seedAdminCookie(activeSession)
    await page.goto(baseUrl + COACH_PAGE_PATH, { waitUntil: 'load' })
    try
    {
      await page.waitForFunction(
        (selector) => window.location.pathname === '/operation/coach' && !!document.querySelector(selector),
        `input[placeholder="${SEARCH_PLACEHOLDER}"]`,
        { timeout: 30000 }
      )
    }
    catch (error)
    {
      throw new AssertionError(`教练管理页未就绪，当前地址：${page.url().replace(baseUrl, '')}`)
    }
    await helper.dismissPasswordPrompt()
    if (searchName)
    {
      await helper.searchCoach(searchName)
    }
  }

  helper.openCoachListAs = (activeSession, options = {}) =>
    helper.openCoachList(Object.assign({}, options, { session: activeSession }))

  helper.searchCoach = async (name) =>
  {
    const input = page.getByPlaceholder(SEARCH_PLACEHOLDER)
    await input.fill(name)
    await page.getByRole('button', { name: '搜索' }).click()
    await helper.coachRow(name).waitFor({ state: 'visible', timeout: 20000 })
    await helper.waitTableIdle()
  }

  helper.waitTableIdle = async () =>
  {
    const mask = page.locator('.el-loading-mask')
    await mask.waitFor({ state: 'visible', timeout: 2000 }).catch(() => {})
    await mask.waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
  }

  helper.coachRow = (name) => page.locator('.el-table__row', { hasText: name }).first()

  helper.levelTagText = async (name) =>
  {
    const row = helper.coachRow(name)
    if (!(await row.count()))
    {
      return ''
    }
    return (await row.locator('.el-tag').first().innerText()).trim()
  }

  helper.waitLevelDisplay = (name, expected) =>
    poll(async () => (await helper.levelTagText(name)) === expected, {
      message: `列表中教练「${name}」的等级未显示为「${expected}」`
    })

  helper.clickRowButton = async (name, buttonText) =>
  {
    const button = helper.coachRow(name).getByRole('button', { name: buttonText, exact: true })
    await button.waitFor({ state: 'visible', timeout: 10000 })
    await button.click()
  }

  helper.clickLevelAction = (name, actionText) => helper.clickRowButton(name, actionText)

  helper.confirmDialog = async () =>
  {
    const box = page.locator('.el-message-box')
    await box.waitFor({ state: 'visible', timeout: 10000 })
    const text = await box.innerText()
    await box.locator('.el-message-box__btns .el-button--primary').click()
    await box.waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {})
    return text
  }

  helper.expectSuccessToast = async (text) =>
  {
    await page.locator('.el-message--success', { hasText: text }).first().waitFor({ state: 'visible', timeout: 10000 })
  }

  helper.expectErrorToast = async () =>
  {
    const toast = page.locator('.el-message--error, .el-notification').first()
    await toast.waitFor({ state: 'visible', timeout: 10000 })
    return (await toast.innerText()).trim()
  }

  helper.assertNoSuccessToast = async (text) =>
  {
    await page.waitForTimeout(500)
    assert.equal(
      await page.locator('.el-message--success', { hasText: text }).count(),
      0,
      `保存失败后不应出现成功提示「${text}」`
    )
  }

  helper.levelRequests = () => logs.requests.filter((item) =>
    item.method === 'PUT' && /\/admin\/coaches\/[^/]+\/level$/.test(new URL(item.url).pathname))

  helper.coachUpdateRequests = () => logs.requests.filter((item) =>
    item.method === 'PUT' && /\/admin\/coaches\/[^/]+$/.test(new URL(item.url).pathname))

  helper.lastCoachUpdatePayload = () =>
  {
    const requests = helper.coachUpdateRequests()
    if (!requests.length)
    {
      throw new AssertionError('未捕获到资料修改请求')
    }
    const postData = requests[requests.length - 1].postData
    if (!postData)
    {
      throw new AssertionError('资料修改请求没有请求体')
    }
    return JSON.parse(postData)
  }

  helper.openEditDialog = async (name) =>
  {
    await helper.clickRowButton(name, '修改')
    const dialog = page.locator('.el-dialog').filter({ hasText: '修改教练' }).first()
    await dialog.waitFor({ state: 'visible', timeout: 10000 })
    await dialog.locator('textarea').waitFor({ state: 'visible', timeout: 10000 })
    return dialog
  }

  helper.submitDialog = async (dialog) =>
  {
    await dialog.locator('.el-dialog__footer .el-button--primary').click()
    await dialog.waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {})
  }

  helper.setLevelViaApi = (level) => apiUpdateCoachLevel(session.token, fixture.id, level)

  helper.fetchLevel = async () =>
  {
    const body = await apiListCoaches(session.token, { pageNum: 1, pageSize: 100, name: fixture.name })
    const row = (body.rows || []).find((item) => item.name === fixture.name)
    if (!row)
    {
      throw new AssertionError(`教练列表接口中找不到测试教练「${fixture.name}」`)
    }
    return Number(row.level)
  }

  helper.fetchDetail = () => apiGetCoach(session.token, fixture.id)

  helper.bodyText = async () => (await page.locator('body').innerText()).replace(/\n+/g, ' | ')

  helper.dispose = async () =>
  {
    await context.close()
  }

  helper.saveArtifacts = async (error) =>
  {
    const dir = path.join(config.artifactsDir, runId)
    fs.mkdirSync(dir, { recursive: true })
    const base = path.join(dir, slug(caseName))
    try
    {
      await page.screenshot({ path: base + '.png', fullPage: true })
    }
    catch (screenshotError)
    {
      // 截图失败不掩盖原始断言失败
    }
    try
    {
      await context.tracing.stop({ path: base + '.trace.zip' })
    }
    catch (traceError)
    {
      // trace 失败不掩盖原始断言失败
    }
    fs.writeFileSync(
      base + '.log.txt',
      [
        `用例：${caseName}`,
        `错误：${error && error.stack ? error.stack : String(error)}`,
        `页面地址：${page.url()}`,
        `等级接口请求：${JSON.stringify(helper.levelRequests(), null, 2)}`,
        `资料修改请求：${JSON.stringify(helper.coachUpdateRequests(), null, 2)}`,
        `控制台错误：${JSON.stringify(logs.console, null, 2)}`,
        `失败请求：${JSON.stringify(logs.failed, null, 2)}`
      ].join('\n'),
      'utf8'
    )
    return dir
  }

  return helper
}

/** 按顺序执行一组用例，返回 { passed, failed, skipped, results } */
async function runSuite({ suite, browser, baseUrl, session, fixture, runId })
{
  const results = []
  console.log(`\n${suite.name}`)
  for (const testCase of suite.cases)
  {
    const helper = await createHelper({ browser, baseUrl, session, fixture, caseName: testCase.name, runId })
    try
    {
      await testCase.run(helper)
      console.log(`  √ ${testCase.name}`)
      results.push({ name: testCase.name, status: 'passed' })
    }
    catch (error)
    {
      console.log(`  × ${testCase.name}`)
      console.log(`      ${error && error.message ? error.message : error}`)
      const dir = await helper.saveArtifacts(error)
      results.push({ name: testCase.name, status: 'failed', message: String(error && error.message), artifacts: dir })
    }
    finally
    {
      await helper.dispose()
    }
  }

  const passed = results.filter((item) => item.status === 'passed').length
  const failed = results.filter((item) => item.status === 'failed')
  return { name: suite.name, results, passed, failed, skipped: 0 }
}

module.exports = { runSuite, timestamp }
