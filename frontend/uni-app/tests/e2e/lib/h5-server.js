const fs = require('fs')
const http = require('http')
const path = require('path')
const { config } = require('./config')

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

/** 判断 child 是否严格位于 parent 目录内（含分隔符边界），不越界到同名前缀目录 */
function isInside(parent, child)
{
  const relative = path.relative(path.resolve(parent), path.resolve(child))
  return !!relative && !relative.startsWith('..') && !path.isAbsolute(relative)
}

function sendFile(res, file)
{
  res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' })
  res.end(fs.readFileSync(file))
}

function handleRequest(req, res, root)
{
  const urlPath = decodeURIComponent(String(req.url || '/').split('?')[0])
  let file = path.join(root, urlPath)
  if (!isInside(root, file) && path.resolve(file) !== path.resolve(root))
  {
    res.writeHead(403)
    res.end()
    return
  }
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory())
  {
    // 构建产物只带页面与打包资源，页面里以 /static/ 绝对路径引用的图片仍取工程 static 目录
    const staticRoot = path.join(config.uniAppRoot, 'static')
    const staticFile = path.join(config.uniAppRoot, urlPath)
    if (urlPath.startsWith('/static/') && isInside(staticRoot, staticFile) && fs.existsSync(staticFile))
    {
      file = staticFile
    }
    else
    {
      // 单页应用：未知路径一律回 index.html，由 hash 路由接管
      file = path.join(root, 'index.html')
    }
  }
  sendFile(res, file)
}

function startH5Server(root)
{
  if (!fs.existsSync(path.join(root, 'index.html')))
  {
    throw new Error(`H5 构建产物不存在：${root}\\index.html（先执行 npm run build:h5）`)
  }

  const server = http.createServer((req, res) =>
  {
    try
    {
      handleRequest(req, res, root)
    }
    catch (error)
    {
      console.error(`[h5-server] 处理请求失败 ${req.url}：${error && error.message}`)
      if (!res.headersSent)
      {
        res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' })
      }
      res.end('Internal Server Error')
    }
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
