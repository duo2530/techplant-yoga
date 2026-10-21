#!/usr/bin/env node
const fs = require('fs')
const path = require('path')
const { spawnSync } = require('child_process')
const { config } = require('./lib/config')
const { launchBrowser } = require('./lib/browser')
const { startH5Server } = require('./lib/h5-server')
const { apiLogin } = require('./lib/session')
const { runSuite, timestamp } = require('./lib/runner')

/**
 * 用户端 H5 端到端测试入口。
 *
 *   npm test              跑 tests/e2e/specs 下的全部用例（需先有 H5 构建产物）
 *   npm run test:build    先执行 npm run build:h5 再跑用例
 *   可选参数：--only=<关键字> 只跑名称匹配的用例文件
 */

const SPEC_DIR = path.join(__dirname, 'specs')

/** 参与 H5 构建的源文件与目录；用于提示构建产物是否已过期 */
const SOURCE_ENTRIES = [
  'pages', 'components', 'api', 'utils', 'store', 'static', 'uni_modules',
  'main.js', 'App.vue', 'permission.js', 'pages.json', 'manifest.json', 'uni.scss', 'config.js'
]

function newestMtime(target, newest = 0)
{
  if (!fs.existsSync(target))
  {
    return newest
  }
  const stat = fs.statSync(target)
  if (!stat.isDirectory())
  {
    return Math.max(newest, stat.mtimeMs)
  }
  for (const name of fs.readdirSync(target))
  {
    if (name === 'node_modules' || name === 'dist' || name === 'unpackage')
    {
      continue
    }
    newest = newestMtime(path.join(target, name), newest)
  }
  return newest
}

/** 产物早于源文件时打印警告，避免拿旧 H5 构建跑端到端用例 */
function warnIfStaleBuild()
{
  const builtAt = fs.statSync(path.join(config.h5Dir, 'index.html')).mtimeMs
  let sourceAt = 0
  for (const entry of SOURCE_ENTRIES)
  {
    sourceAt = Math.max(sourceAt, newestMtime(path.join(config.uniAppRoot, entry)))
  }
  if (sourceAt - builtAt > 2000)
  {
    console.warn('\n⚠ H5 构建产物可能已过期：源文件修改时间晚于 dist/build/h5/index.html')
    console.warn(`  产物：${new Date(builtAt).toLocaleString()}；源文件：${new Date(sourceAt).toLocaleString()}`)
    console.warn('  如刚改过页面/接口，先执行 npm run build:h5，或改用 npm run test:build\n')
  }
}

function loadSuites(only)
{
  return fs.readdirSync(SPEC_DIR)
    .filter((file) => file.endsWith('.spec.js'))
    .sort()
    .map((file) => require(path.join(SPEC_DIR, file)))
    .filter((suite) => !only || suite.name.includes(only))
}

function buildH5()
{
  console.log('构建 H5 产物（frontend/uni-app：npm run build:h5）…')
  const result = spawnSync('npm', ['run', 'build:h5'], {
    cwd: config.uniAppRoot,
    stdio: 'inherit',
    shell: true
  })
  if (result.status !== 0)
  {
    throw new Error('npm run build:h5 失败')
  }
}

async function main()
{
  const args = process.argv.slice(2)
  const only = (args.find((arg) => arg.startsWith('--only=')) || '').slice('--only='.length)

  if (args.includes('--build'))
  {
    buildH5()
  }
  if (!fs.existsSync(path.join(config.h5Dir, 'index.html')))
  {
    throw new Error(
      `H5 构建产物不存在：${config.h5Dir}\n` +
      '先在 frontend/uni-app 执行 npm run build:h5，或改用 npm run test:build 自动构建'
    )
  }

  warnIfStaleBuild()

  console.log(`H5 构建产物：${config.h5Dir}`)
  console.log(`后端地址：${config.apiBase}`)

  const memberSession = await apiLogin()
  console.log(`登录成功：${memberSession.memberName}（${memberSession.phone}）`)

  const server = await startH5Server(config.h5Dir)
  const browser = await launchBrowser()
  const runId = timestamp()
  const suites = loadSuites(only)
  if (!suites.length)
  {
    throw new Error(`没有匹配的用例文件：--only=${only}`)
  }

  const summaries = []
  try
  {
    for (const suite of suites)
    {
      summaries.push(await runSuite({ suite, browser, h5Base: server.url, memberSession, runId }))
    }
  }
  finally
  {
    await browser.close()
    await server.close()
  }

  const passed = summaries.reduce((sum, item) => sum + item.passed, 0)
  const skipped = summaries.reduce((sum, item) => sum + item.skipped, 0)
  const failed = summaries.flatMap((item) => item.failed)

  console.log('')
  if (failed.length)
  {
    console.log(`未通过 ${failed.length} 项（通过 ${passed} 项，跳过 ${skipped} 项）：`)
    failed.forEach((item) => console.log(`  - ${item.name}：${item.message}`))
    console.log(`失败产物：${path.join(config.artifactsDir, runId)}`)
    process.exit(1)
  }
  console.log(`全部通过：通过 ${passed} 项，跳过 ${skipped} 项`)
}

main().catch((error) =>
{
  console.error(error && error.message ? error.message : error)
  process.exit(1)
})
