// 本地生产预览服务器：模拟 nginx 行为，验证 frontend/dist 构建产物 + 反代。
// 用法：node deploy/preview.mjs   （默认端口 4173，可用 PORT 环境变量覆盖）
//
// 行为与 deploy/nginx.conf 对齐：
//   1. 静态托管 frontend/dist
//   2. SPA history 回退：非文件路径回退到 index.html
//   3. /api/*     → http://127.0.0.1:8080/*        （去 /api 前缀）
//   4. /uploads/* → http://127.0.0.1:8080/uploads/* （保留路径）

import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const DIST = path.resolve(__dirname, '../frontend/dist')
const BACKEND = { host: '127.0.0.1', port: 8080 }
const PORT = Number(process.env.PORT) || 4173

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon'
}

// 静态文件服务：命中文件则返回 true，否则返回 false（由调用方回退 index.html）
function serveStatic(urlPath, res) {
  // URL 已由 new URL() 归一化掉 ../，此处再校验一次防路径穿越
  const filePath = path.join(DIST, urlPath)
  if (!filePath.startsWith(DIST)) return false
  if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) return false
  const ext = path.extname(filePath).toLowerCase()
  res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' })
  fs.createReadStream(filePath).pipe(res)
  return true
}

// 反向代理：method / headers / body 原样透传到后端，响应原样回传
function proxy(req, res, targetPath) {
  const upstream = http.request(
    {
      host: BACKEND.host,
      port: BACKEND.port,
      method: req.method,
      path: targetPath,
      headers: { ...req.headers, host: `${BACKEND.host}:${BACKEND.port}` }
    },
    (upRes) => {
      res.writeHead(upRes.statusCode, upRes.headers)
      upRes.pipe(res)
    }
  )
  upstream.on('error', () => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
    res.end(JSON.stringify({ success: false, errorMsg: '后端服务不可用（请先启动 8080）' }))
  })
  req.pipe(upstream)
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`)
  const p = url.pathname

  // 1. /api/* → 后端（去 /api 前缀）
  if (p.startsWith('/api/')) {
    return proxy(req, res, p.replace(/^\/api/, '') + (url.search || ''))
  }
  // 2. /uploads/* → 后端（保留完整路径）
  if (p.startsWith('/uploads/')) {
    return proxy(req, res, p + (url.search || ''))
  }
  // 3. 静态文件；4. SPA 回退（含 / 根路径、/job/8 等前端路由）
  if (!serveStatic(p, res)) {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
    fs.createReadStream(path.join(DIST, 'index.html')).pipe(res)
  }
})

server.listen(PORT, () => {
  console.log(`生产预览已启动：http://localhost:${PORT}`)
  console.log(`  静态目录：${DIST}`)
  console.log(`  反代：/api/* 与 /uploads/* → http://${BACKEND.host}:${BACKEND.port}`)
})
