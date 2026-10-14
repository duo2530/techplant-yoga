const fs = require('fs')
const path = require('path')
const { chromium } = require('playwright-core')
const { config } = require('./config')

/**
 * 浏览器解析与启动。
 *
 * 用系统已安装的 Edge / Chrome（playwright-core 不带浏览器），避免端到端测试额外下载浏览器；
 * 其它平台可用 E2E_BROWSER_PATH 指定路径。
 */

const WINDOWS_CANDIDATES = [
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe'
]

const POSIX_NAMES = ['msedge', 'google-chrome', 'google-chrome-stable', 'chromium', 'chromium-browser']

function resolveBrowserPath()
{
  if (config.browserPath)
  {
    if (!fs.existsSync(config.browserPath))
    {
      throw new Error(`E2E_BROWSER_PATH 指向的文件不存在：${config.browserPath}`)
    }
    return config.browserPath
  }

  if (process.platform === 'win32')
  {
    const hit = WINDOWS_CANDIDATES.find((candidate) => fs.existsSync(candidate))
    if (hit)
    {
      return hit
    }
  }
  else
  {
    const dirs = (process.env.PATH || '').split(path.delimiter).filter(Boolean)
    for (const name of POSIX_NAMES)
    {
      for (const dir of dirs)
      {
        const candidate = path.join(dir, name)
        if (fs.existsSync(candidate))
        {
          return candidate
        }
      }
    }
  }

  throw new Error('找不到可用的 Edge / Chrome，请用 E2E_BROWSER_PATH 指定浏览器路径')
}

async function launchBrowser()
{
  const executablePath = resolveBrowserPath()
  return chromium.launch({ executablePath, headless: config.headless })
}

module.exports = { launchBrowser, resolveBrowserPath }
