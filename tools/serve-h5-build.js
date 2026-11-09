// 在固定端口托管 H5 构建产物，供 tailscale serve 转发；/static/** 回退读取工程 static 目录。
// 用法：node tools/serve-h5-build.js <构建产物目录> [端口，默认 9091]
const http = require('http')
const path = require('path')
const { startH5Server } = require('../frontend/uni-app/tests/e2e/lib/h5-server.js')

const root = path.resolve(process.argv[2] || 'frontend/uni-app/dist/build/h5')
const port = Number(process.argv[3]) || 9091

startH5Server(root).then(({ url }) => {
  const target = new URL(url)
  http.createServer((req, res) => {
    const upstream = http.request(
      { host: target.hostname, port: target.port, path: req.url, method: req.method, headers: req.headers },
      (r) => { res.writeHead(r.statusCode, r.headers); r.pipe(res) })
    req.pipe(upstream)
  }).listen(port, '127.0.0.1', () => console.log(`H5 build ${root} on http://127.0.0.1:${port}`))
})
