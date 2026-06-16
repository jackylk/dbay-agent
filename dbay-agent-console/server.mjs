import { createServer } from 'node:http'
import { createReadStream, existsSync } from 'node:fs'
import { extname, join, normalize } from 'node:path'

const port = Number(process.env.PORT || 4173)
const apiBaseUrl = (process.env.DBAY_AGENT_API_BASE_URL || '').replace(/\/$/, '')
const distDir = new URL('./dist/', import.meta.url)

const contentTypes = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
}

function serveFile(res, pathname) {
  const relativePath = pathname === '/' ? '/index.html' : pathname
  const filePath = normalize(join(distDir.pathname, relativePath))
  if (!filePath.startsWith(distDir.pathname) || !existsSync(filePath)) {
    return serveFile(res, '/index.html')
  }
  res.writeHead(200, { 'content-type': contentTypes[extname(filePath)] || 'application/octet-stream' })
  createReadStream(filePath).pipe(res)
}

async function proxyApi(req, res, url) {
  if (!apiBaseUrl) {
    res.writeHead(503, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ status: 'DOWN', error: 'DBAY_AGENT_API_BASE_URL is not configured' }))
    return
  }
  const target = `${apiBaseUrl}${url.pathname.replace(/^\/agent-api/, '')}${url.search}`
  try {
    const upstream = await fetch(target, {
      method: req.method,
      headers: {
        accept: req.headers.accept || 'application/json',
        'content-type': req.headers['content-type'] || 'application/json',
      },
      body: req.method === 'GET' || req.method === 'HEAD' ? undefined : req,
      duplex: 'half',
    })
    res.writeHead(upstream.status, {
      'content-type': upstream.headers.get('content-type') || 'application/json',
    })
    res.end(Buffer.from(await upstream.arrayBuffer()))
  } catch (error) {
    res.writeHead(502, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ status: 'DOWN', error: error.name }))
  }
}

createServer((req, res) => {
  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)
  if (url.pathname.startsWith('/agent-api/')) {
    proxyApi(req, res, url)
    return
  }
  serveFile(res, url.pathname)
}).listen(port, '0.0.0.0')
