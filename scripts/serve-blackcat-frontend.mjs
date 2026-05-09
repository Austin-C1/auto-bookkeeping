import http from 'node:http'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const args = process.argv.slice(2)

function readArg(name, defaultValue) {
  const index = args.indexOf(name)
  if (index === -1 || index === args.length - 1) {
    return defaultValue
  }
  return args[index + 1]
}

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(readArg('--root', path.resolve(scriptDir, '..', 'frontend', 'dist')))
const host = readArg('--host', '127.0.0.1')
const port = Number.parseInt(readArg('--port', '18880'), 10)

const contentTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.gif', 'image/gif'],
  ['.html', 'text/html; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.map', 'application/json; charset=utf-8'],
  ['.png', 'image/png'],
  ['.svg', 'image/svg+xml'],
  ['.txt', 'text/plain; charset=utf-8'],
  ['.webp', 'image/webp'],
  ['.woff', 'font/woff'],
  ['.woff2', 'font/woff2']
])

function getContentType(filePath) {
  return contentTypes.get(path.extname(filePath).toLowerCase()) ?? 'application/octet-stream'
}

function isHtmlRequest(requestPath) {
  return !path.extname(requestPath) && !requestPath.startsWith('/api') && !requestPath.startsWith('/ws')
}

async function resolveRequestPath(requestPath) {
  const normalizedPath = decodeURIComponent(requestPath.split('?')[0] || '/')
  const relativePath = normalizedPath === '/' ? 'index.html' : normalizedPath.replace(/^\/+/, '')
  const candidatePath = path.resolve(root, relativePath)

  if (!candidatePath.startsWith(root)) {
    return null
  }

  try {
    const stats = await fs.stat(candidatePath)
    if (stats.isDirectory()) {
      return path.join(candidatePath, 'index.html')
    }
    return candidatePath
  } catch {
    if (isHtmlRequest(normalizedPath)) {
      return path.join(root, 'index.html')
    }
    return null
  }
}

const server = http.createServer(async (req, res) => {
  const method = req.method ?? 'GET'
  if (method !== 'GET' && method !== 'HEAD') {
    res.writeHead(405, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('Method Not Allowed')
    return
  }

  const requestPath = req.url ?? '/'
  const filePath = await resolveRequestPath(requestPath)

  if (!filePath) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('Not Found')
    return
  }

  try {
    const file = await fs.readFile(filePath)
    const headers = {
      'Content-Type': getContentType(filePath)
    }

    if (filePath.endsWith('.html')) {
      headers['Cache-Control'] = 'no-cache'
    } else {
      headers['Cache-Control'] = 'public, max-age=31536000, immutable'
    }

    res.writeHead(200, headers)
    if (method === 'HEAD') {
      res.end()
      return
    }
    res.end(file)
  } catch (error) {
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('Internal Server Error')
    console.error(error)
  }
})

server.listen(port, host, () => {
  console.log(`BlackCat frontend server listening at http://${host}:${port}`)
})

function shutdown() {
  server.close(() => process.exit(0))
}

process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
