#!/usr/bin/env node
const fs = require('fs')
const path = require('path')
const { spawnSync } = require('child_process')
const { config } = require('./lib/config')
const { launchBrowser } = require('./lib/browser')
const { startStaticServer } = require('./lib/server')
const {
  apiCaptchaEnabled,
  apiAdminLogin,
  apiCreateCoach,
  apiListCoaches,
  apiDeleteCoach
} = require('./lib/admin-api')
const { runSuite, timestamp } = require('./lib/runner')

/**
 * 管理端端到端测试入口。
 *
 *   npm test              跑 tests/e2e/specs 下的全部用例（需先有 npm run build:prod 的产物）
 *   npm run test:build    先执行 npm run build:prod 再跑用例
 *   可选参数：--only=<spec 标识> 只跑标识匹配的用例文件，标识取文件名（如 coach-level）
 *
 * 用例依赖隔离后端：验证码关闭、管理员账号可用、教练菜单与 yoga:coach:level 权限已配置。
 * 套件会创建并删除教练、系统用户与角色，因此必须显式设置 E2E_API_URL 与 E2E_ALLOW_DESTRUCTIVE=1。
 */

const SPEC_DIR = path.join(__dirname, 'specs')

function listSpecIds()
{
  return fs.readdirSync(SPEC_DIR)
    .filter((file) => file.endsWith('.spec.js'))
    .sort()
    .map((file) => path.basename(file, '.spec.js'))
}

function assertIsolationOptIn()
{
  const missing = []
  if (!config.apiUrlExplicit)
  {
    missing.push('E2E_API_URL（显式指定隔离后端地址）')
  }
  if (!config.allowDestructive)
  {
    missing.push('E2E_ALLOW_DESTRUCTIVE=1（显式允许破坏性测试数据变更）')
  }
  if (!missing.length)
  {
    return
  }

  console.error('拒绝执行：缺少隔离运行所需的显式开关。')
  missing.forEach((item) => console.error(`  - ${item}`))
  console.error('')
  console.error('本套件会创建并删除教练、系统用户（sys_user）与角色（sys_role），只允许对一次性隔离的 e2e 后端运行：')
  console.error('  1) 必须显式指定 E2E_API_URL，不使用默认 localhost:8080 静默执行；')
  console.error('  2) 必须显式设置 E2E_ALLOW_DESTRUCTIVE=1 确认允许测试数据变更。')
  console.error('')
  console.error('PowerShell 示例：')
  console.error("  $env:E2E_API_URL = 'http://localhost:8080'")
  console.error("  $env:E2E_ALLOW_DESTRUCTIVE = '1'")
  console.error('  npm test')
  console.error('')
  console.error('禁止指向开发库、共享库或生产环境；用例失败时也会清理已创建的测试数据。')
  process.exit(3)
}

function loadSuites(only)
{
  return listSpecIds()
    .map((specId) =>
    {
      const suite = require(path.join(SPEC_DIR, specId + '.spec.js'))
      return Object.assign({}, suite, { id: suite.id || specId })
    })
    .filter((suite) => !only || suite.id.includes(only))
}

function buildPc()
{
  console.log('构建管理端产物（frontend/pc：npm run build:prod）…')
  const result = spawnSync('npm', ['run', 'build:prod'], {
    cwd: config.pcRoot,
    stdio: 'inherit',
    shell: true
  })
  if (result.status !== 0)
  {
    throw new Error('npm run build:prod 失败')
  }
}

async function findCoach(session, name)
{
  const body = await apiListCoaches(session.token, { pageNum: 1, pageSize: 100, name })
  return (body.rows || []).find((item) => item.name === name) || null
}

/** 每个成功用例都在真实后端留下操作记录，套件自建并清理一个专属教练，不依赖种子数据 */
async function createCoachFixture(session)
{
  const suffix = String(Date.now()).slice(-6)
  const name = `E2E金牌测试${suffix}`
  const initialIntro = 'E2E 初始简介'
  try
  {
    await apiCreateCoach(session.token, { name, intro: initialIntro, phone: '13900000000', gallery: [] })
    let row = null
    for (let attempt = 0; attempt < 3 && !row; attempt += 1)
    {
      row = await findCoach(session, name)
      if (!row)
      {
        await new Promise((resolve) => setTimeout(resolve, 300))
      }
    }
    if (!row)
    {
      throw new Error(`新增的测试教练未出现在列表中：${name}`)
    }
    console.log(`测试教练：${name}（id=${row.id}，初始等级 level=${row.level}）`)
    return { id: row.id, name, initialIntro }
  }
  catch (error)
  {
    // 新增可能已落库但取不到 ID：按唯一名再查一次并清理，避免残留
    try
    {
      const created = await findCoach(session, name)
      if (created)
      {
        await apiDeleteCoach(session.token, created.id)
        console.warn(`夹具创建失败后已清理测试教练：${name}（id=${created.id}）`)
      }
    }
    catch (cleanupError)
    {
      console.warn(`夹具创建失败后清理测试教练失败（需人工删除 ${name}）：${cleanupError.message}`)
    }
    throw error
  }
}

async function cleanupCoachFixture(session, fixture)
{
  try
  {
    await apiDeleteCoach(session.token, fixture.id)
    console.log(`已清理测试教练：${fixture.name}`)
    return null
  }
  catch (error)
  {
    console.warn(`清理测试教练失败（需人工删除 ${fixture.name}，id=${fixture.id}）：${error.message}`)
    return error
  }
}

async function main()
{
  const args = process.argv.slice(2)
  const only = (args.find((arg) => arg.startsWith('--only=')) || '').slice('--only='.length)

  assertIsolationOptIn()

  const suites = loadSuites(only)
  if (!suites.length)
  {
    throw new Error(
      `没有匹配的用例文件：--only=${only}\n可用的 spec 标识：${listSpecIds().join('、')}`
    )
  }

  if (args.includes('--build'))
  {
    buildPc()
  }

  const indexFile = path.join(config.distDir, 'index.html')
  if (!fs.existsSync(indexFile))
  {
    throw new Error(
      `管理端构建产物不存在：${indexFile}\n` +
      '先在 frontend/pc 执行 npm run build:prod，或改用 npm run test:build 自动构建'
    )
  }

  console.log(`管理端产物：${config.distDir}`)
  console.log(`后端地址：${config.apiBase}`)
  console.log(`允许破坏性测试数据变更：是（E2E_ALLOW_DESTRUCTIVE=1）`)

  let captchaEnabled
  try
  {
    captchaEnabled = await apiCaptchaEnabled()
  }
  catch (error)
  {
    throw new Error(
      `无法连接后端 ${config.apiBase}：${error.message}\n` +
      '请确认隔离后端已启动，或用 E2E_API_URL 指定地址'
    )
  }
  if (captchaEnabled)
  {
    console.error('后端 captchaEnabled=true：登录需要图形验证码，脚本无法自动登录，未执行用例。')
    console.error('请在隔离库关闭验证码后重跑：')
    console.error("  UPDATE sys_config SET config_value = 'false' WHERE config_key = 'sys.account.captchaEnabled';")
    console.error('修改后刷新后端配置缓存（重启应用或清理 Redis 配置缓存），确认 GET /captchaImage 返回 captchaEnabled=false。')
    process.exit(2)
  }

  const session = await apiAdminLogin()
  console.log(`管理员登录成功：${session.username}`)

  const server = await startStaticServer({
    root: config.distDir,
    apiBase: config.apiBase,
    host: config.serverHost,
    port: config.serverPort
  })

  const runId = timestamp()
  let browser
  let fixture = null
  let cleanupError = null
  const summaries = []
  try
  {
    fixture = await createCoachFixture(session)
    browser = await launchBrowser()
    console.log(`测试入口：${server.url}/operation/coach`)
    for (const suite of suites)
    {
      summaries.push(await runSuite({ suite, browser, baseUrl: server.url, session, fixture, runId }))
    }
  }
  finally
  {
    if (browser)
    {
      await browser.close().catch(() => {})
    }
    await server.close()
    if (fixture)
    {
      cleanupError = await cleanupCoachFixture(session, fixture)
    }
  }

  const passed = summaries.reduce((sum, item) => sum + item.passed, 0)
  const failed = summaries.flatMap((item) => item.failed)

  console.log('')
  if (failed.length)
  {
    console.log(`未通过 ${failed.length} 项（通过 ${passed} 项）：`)
    failed.forEach((item) => console.log(`  - ${item.name}：${item.message}`))
    console.log(`失败产物：${path.join(config.artifactsDir, runId)}`)
    process.exit(1)
  }
  if (cleanupError)
  {
    console.log(`用例全部通过（通过 ${passed} 项），但测试教练清理失败，请人工处理。`)
    process.exit(1)
  }
  console.log(`全部通过：通过 ${passed} 项`)
}

main().catch((error) =>
{
  console.error(error && error.message ? error.message : error)
  process.exit(1)
})
