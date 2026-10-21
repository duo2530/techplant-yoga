const http = require('http')
const fs = require('fs')
const path = require('path')

/**
 * 托管管理端构建产物，并把 `/prod-api/**` 代理到隔离后端。
 *
 * 生产构建的接口前缀是 Nginx 反代用的 `/prod-api`，端到端测试用本服务代替 Nginx：
 * 静态文件缺失且路径无扩展名时回退 index.html，支撑 vue-router history 模式。
 */

const API_PREFIX = '/prod-api'
const INDEX_FILE = 'index.html'

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.eot': 'application/vnd.ms-fontobject'
}

function contentType(filePath)
{
  return MIME_TYPES[path.extname(filePath).toLowerCase()] || 'application/octet-stream'
}

async function sendFile(res, filePath)
{
  const content = await fs.promises.readFile(filePath)
  res.statusCode = 200
  res.setHeader('content-type', contentType(filePath))
  res.setHeader('content-length', content.length)
  res.setHeader('cache-control', 'no-store')
  res.end(content)
}

function resolveStaticPath(root, pathname)
{
  let decoded
  try
  {
    decoded = decodeURIComponent(pathname)
  }
  catch (error)
  {
    return null
  }
  const relative = decoded === '/' ? INDEX_FILE : decoded.replace(/^\/+/, '')
  const filePath = path.resolve(root, relative)
  const prefix = root.endsWith(path.sep) ? root : root + path.sep
  if (filePath !== root && !filePath.startsWith(prefix))
  {
    return null
  }
  return filePath
}

async function serveStatic(req, res, root, pathname)
{
  const filePath = resolveStaticPath(root, pathname)
  const stat = filePath ? await fs.promises.stat(filePath).catch(() => null) : null
  if (stat && stat.isFile())
  {
    return sendFile(res, filePath)
  }
  if (path.extname(pathname))
  {
    res.statusCode = 404
    res.setHeader('content-type', 'text/plain; charset=utf-8')
    res.end('Not Found')
    return
  }
  return sendFile(res, path.join(root, INDEX_FILE))
}

async function readRequestBody(req)
{
  const chunks = []
  for await (const chunk of req)
  {
    chunks.push(chunk)
  }
  return chunks.length ? Buffer.concat(chunks) : undefined
}

async function proxy(req, res, apiBase, url)
{
  const target = apiBase + url.pathname.slice(API_PREFIX.length) + url.search
  const headers = {}
  for (const name of ['authorization', 'content-type', 'accept', 'accept-language'])
  {
    if (req.headers[name])
    {
      headers[name] = req.headers[name]
    }
  }
  const body = req.method === 'GET' || req.method === 'HEAD' ? undefined : await readRequestBody(req)
  const upstream = await fetch(target, { method: req.method, headers, body, redirect: 'manual' })
  const content = Buffer.from(await upstream.arrayBuffer())
  res.statusCode = upstream.status
  const upstreamType = upstream.headers.get('content-type')
  if (upstreamType)
  {
    res.setHeader('content-type', upstreamType)
  }
  res.setHeader('content-length', content.length)
  res.end(content)
}

/**
 * 启动静态服务，返回 { url, close }。
 *
 * @param {{ root: string, apiBase: string, host: string, port: number }} options
 */
function startStaticServer({ root, apiBase, host, port })
{
  const staticRoot = path.resolve(root)
  const server = http.createServer(async (req, res) =>
  {
    try
    {
      const url = new URL(req.url, 'http://localhost')
      if (url.pathname === API_PREFIX || url.pathname.startsWith(API_PREFIX + '/'))
      {
        await proxy(req, res, apiBase, url)
      }
      else
      {
        await serveStatic(req, res, staticRoot, url.pathname)
      }
    }
    catch (error)
    {
      res.statusCode = 502
      res.setHeader('content-type', 'text/plain; charset=utf-8')
      res.end(`请求处理失败：${error && error.message ? error.message : error}`)
    }
  })

  return new Promise((resolve, reject) =>
  {
    server.once('error', reject)
    server.listen(port, host, () =>
    {
      const address = server.address()
      resolve({
        url: `http://${host}:${address.port}`,
        close: () => new Promise((done) => server.close(() => done()))
      })
    })
  })
}

module.exports = { startStaticServer }
