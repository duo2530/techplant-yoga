const path = require('path')

/**
 * 管理端端到端测试的运行配置。
 *
 * 测试跑的是 `npm run build:prod` 的产物，由本套件启动的静态服务托管并把 `/prod-api` 代理到
 * E2E_API_URL（生产构建的 VITE_APP_BASE_API 是 Nginx 反代前缀）。全部项可用环境变量覆盖：
 * E2E_PC_DIST / E2E_API_URL / E2E_ALLOW_DESTRUCTIVE / E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD /
 * E2E_BROWSER_PATH / E2E_HEADFUL / E2E_PORT。
 *
 * 套件会创建并删除教练、系统用户与角色，因此 E2E_API_URL 必须显式指定且 E2E_ALLOW_DESTRUCTIVE=1
 * 才执行（见 run.js 的隔离开关），避免误连开发库或生产库。
 */

/** 本目录（frontend/pc/tests/e2e） */
const e2eRoot = path.join(__dirname, '..')

/** frontend/pc */
const pcRoot = path.join(e2eRoot, '..', '..')

const config = {
  e2eRoot,
  pcRoot,

  /** 管理端构建产物目录 */
  distDir: process.env.E2E_PC_DIST || path.join(pcRoot, 'dist'),

  /** 后端地址：静态服务把浏览器发出的 /prod-api/** 转发到这里 */
  apiBase: (process.env.E2E_API_URL || 'http://localhost:8080').replace(/\/+$/, ''),

  /** 是否显式指定了后端地址；未显式指定时拒绝执行，避免误连默认地址 */
  apiUrlExplicit: Boolean(process.env.E2E_API_URL),

  /** 破坏性用例开关：套件会创建/删除教练、系统用户与角色，只有显式置 1 才执行 */
  allowDestructive: process.env.E2E_ALLOW_DESTRUCTIVE === '1',

  /** 隔离环境管理员账号 */
  adminUsername: process.env.E2E_ADMIN_USERNAME || 'admin',
  adminPassword: process.env.E2E_ADMIN_PASSWORD || 'admin123',

  /** 失败产物目录：截图、trace 与日志 */
  artifactsDir: path.join(e2eRoot, 'artifacts'),

  /** 浏览器：默认用系统已安装的 Edge / Chrome，不下载 Playwright 自带浏览器 */
  browserPath: process.env.E2E_BROWSER_PATH || '',
  headless: process.env.E2E_HEADFUL !== '1',

  /** 管理端桌面视口 */
  viewport: { width: 1440, height: 900 },

  /** 静态服务监听地址；端口 0 表示随机可用端口 */
  serverHost: '127.0.0.1',
  serverPort: Number(process.env.E2E_PORT || 0)
}

module.exports = { config }
