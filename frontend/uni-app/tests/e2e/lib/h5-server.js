const fs = require('fs')
const http = require('http')
const path = require('path')

/**
 * 进程内静态服务：把 H5 构建产物挂到随机端口，测试结束即关闭。
 *
 * 端到端测试跑构建产物而不是 dev 服务：pages.json 的页面注册表在 dev 服务启动时就固定了，
 * 新增页面后必须重启 dev 服务，构建产物没有这个坑。
 */

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ttf': 'font/ttf',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ico': 'image/x-icon'
}

function startH5Server(root)
{
  if (!fs.existsSync(path.join(root, 'index.html')))
  {
    throw new Error(`H5 构建产物不存在：${root}\\index.html（先执行 npm run build:h5）`)
  }

  const server = http.createServer((req, res) =>
  {
    const urlPath = decodeURIComponent(String(req.url || '/').split('?')[0])
    let file = path.join(root, urlPath)
    if (!file.startsWith(root))
    {
      res.writeHead(403)
      res.end()
      return
    }
    if (!fs.existsSync(file) || fs.statSync(file).isDirectory())
    {
      // 单页应用：未知路径一律回 index.html，由 hash 路由接管
      file = path.join(root, 'index.html')
    }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' })
    res.end(fs.readFileSync(file))
  })

  return new Promise((resolve) =>
  {
    server.listen(0, '127.0.0.1', () =>
    {
      const { port } = server.address()
      resolve({
        url: `http://127.0.0.1:${port}`,
        close: () => new Promise((done) => server.close(done))
      })
    })
  })
}

module.exports = { startH5Server }
