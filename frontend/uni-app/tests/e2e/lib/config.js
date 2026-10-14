const path = require('path')

/**
 * 端到端测试的运行配置。
 *
 * 默认值对应本机联调环境（H5 构建产物 + localhost:8080 的真实后端），全部可用环境变量覆盖：
 * E2E_H5_DIR / E2E_API_URL / E2E_MEMBER_PHONE / E2E_MEMBER_PASSWORD / E2E_BROWSER_PATH / E2E_HEADFUL。
 */

/** 本目录（tests/e2e） */
const e2eRoot = path.join(__dirname, '..')

/** frontend/uni-app */
const uniAppRoot = path.join(e2eRoot, '..', '..')

const config = {
  e2eRoot,
  uniAppRoot,

  /** H5 构建产物目录：端到端测试跑的是 `npm run build:h5` 的产物，不是 dev 服务 */
  h5Dir: process.env.E2E_H5_DIR || path.join(uniAppRoot, 'dist', 'build', 'h5'),

  /** 后端地址：需与 uni-app/config.js 的 baseUrl 一致（H5 请求按该地址发出） */
  apiBase: process.env.E2E_API_URL || 'http://localhost:8080',

  /** 联调种子会员（backend/sql/yoga.sql） */
  memberPhone: process.env.E2E_MEMBER_PHONE || '13800138000',
  memberPassword: process.env.E2E_MEMBER_PASSWORD || 'yoga1234',

  /** 失败产物目录：截图、trace 与日志 */
  artifactsDir: path.join(e2eRoot, 'artifacts'),

  /** 浏览器：默认用系统已安装的 Edge / Chrome，不下载 Playwright 自带浏览器 */
  browserPath: process.env.E2E_BROWSER_PATH || '',
  headless: process.env.E2E_HEADFUL !== '1',

  /** H5 页面视口：与真机截图一致的 390×844 逻辑画布 */
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 2
}

module.exports = { config }
