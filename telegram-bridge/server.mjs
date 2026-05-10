import fs from 'node:fs'
import http from 'node:http'
import path from 'node:path'
import QRCode from 'qrcode'
import telegram from 'telegram'
import sessions from 'telegram/sessions/index.js'

const { TelegramClient } = telegram
const { StringSession } = sessions

const port = Number(process.env.TELEGRAM_BRIDGE_PORT || 18884)
const sessionFile = path.resolve(process.env.TELEGRAM_SESSION_FILE || 'data/telegram-session.txt')

let client = null
let clientPromise = null
let qrLoginPromise = null
let qrValue = ''
let status = 'starting'
let statusMessage = 'Telegram API 读取服务启动中'
const entityCache = new Map()

const sendJson = (response, statusCode, body) => {
  const payload = Buffer.from(JSON.stringify(body), 'utf8')
  response.writeHead(statusCode, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': payload.length,
    'access-control-allow-origin': 'http://127.0.0.1:18882',
    'access-control-allow-methods': 'GET, POST, OPTIONS',
    'access-control-allow-headers': 'content-type',
  })
  response.end(payload)
}

const readJsonBody = (request) =>
  new Promise((resolve, reject) => {
    const chunks = []
    request.on('data', (chunk) => chunks.push(chunk))
    request.on('end', () => {
      if (chunks.length === 0) {
        resolve({})
        return
      }
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')))
      } catch (error) {
        reject(error)
      }
    })
    request.on('error', reject)
  })

const toNumber = (value, fallback) => {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : fallback
}

const normaliseTimestampMs = (value) => {
  if (value instanceof Date) return value.getTime()
  const numeric = toNumber(value, 0)
  if (numeric <= 0) return 0
  return numeric < 10_000_000_000 ? numeric * 1000 : numeric
}

const readApiCredentials = () => {
  const apiId = Number(String(process.env.TELEGRAM_API_ID || '').trim())
  const apiHash = String(process.env.TELEGRAM_API_HASH || '').trim()
  if (!Number.isFinite(apiId) || apiId <= 0 || !apiHash) {
    status = 'missing_api'
    statusMessage = '缺少 TELEGRAM_API_ID 或 TELEGRAM_API_HASH'
    qrValue = ''
    return null
  }
  return { apiId, apiHash }
}

const readSession = () => {
  if (!fs.existsSync(sessionFile)) return ''
  return fs.readFileSync(sessionFile, 'utf8').trim()
}

const saveSession = () => {
  if (!client?.session?.save) return
  const session = client.session.save()
  if (!session) return
  fs.mkdirSync(path.dirname(sessionFile), { recursive: true })
  fs.writeFileSync(sessionFile, session, 'utf8')
}

const startQrLogin = ({ apiId, apiHash }) => {
  if (qrLoginPromise) return qrLoginPromise
  status = 'qr_required'
  statusMessage = '请用手机 Telegram 扫描 API 登录二维码，二维码会自动刷新'

  qrLoginPromise = client.signInUserWithQrCode(
    { apiId, apiHash },
    {
      qrCode: async ({ token }) => {
        const loginUrl = `tg://login?token=${token.toString('base64url')}`
        qrValue = await QRCode.toDataURL(loginUrl, {
          errorCorrectionLevel: 'M',
          margin: 4,
          width: 320,
          color: { dark: '#000000', light: '#FFFFFFFF' },
        })
        status = 'qr_required'
        statusMessage = '请用手机 Telegram 扫描 API 登录二维码，二维码会自动刷新'
      },
      password: async () => {
        throw new Error('当前 Telegram 账号开启了二次验证，需要先补充二次密码登录流程')
      },
      onError: async (error) => {
        status = 'failed'
        statusMessage = error?.message || 'Telegram API 登录失败'
        return true
      },
    },
  )
    .then(() => {
      saveSession()
      status = 'ready'
      statusMessage = 'Telegram API 已连接'
      qrValue = ''
    })
    .catch((error) => {
      status = 'failed'
      statusMessage = error?.message || 'Telegram API 登录失败'
    })
    .finally(() => {
      qrLoginPromise = null
    })

  return qrLoginPromise
}

const initialiseClient = async () => {
  const credentials = readApiCredentials()
  if (!credentials) return null

  status = 'starting'
  statusMessage = 'Telegram API 正在连接'
  const stringSession = new StringSession(readSession())
  client = new TelegramClient(stringSession, credentials.apiId, credentials.apiHash, {
    connectionRetries: 5,
  })
  await client.connect()

  if (await client.isUserAuthorized()) {
    saveSession()
    status = 'ready'
    statusMessage = 'Telegram API 已连接'
    qrValue = ''
    return client
  }

  startQrLogin(credentials)
  return client
}

const ensureClient = async () => {
  if (client && status !== 'failed' && status !== 'missing_api') return client
  if (!clientPromise) {
    clientPromise = initialiseClient().finally(() => {
      clientPromise = null
    })
  }
  return clientPromise
}

const isGroupEntity = (entity) => {
  const className = entity?.className || entity?.constructor?.name || ''
  if (className === 'Chat') return true
  if (className !== 'Channel') return false
  return Boolean(entity.megagroup || entity.gigagroup)
}

const entityId = (dialog, entity) => {
  const id = dialog?.id ?? entity?.id
  return id == null ? '' : String(id)
}

const entityTitle = (dialog, entity) => {
  return String(dialog?.title || entity?.title || entity?.username || entity?.firstName || '').trim()
}

const readGroups = async () => {
  await ensureClient()
  if (status !== 'ready' || !client) return []

  const dialogs = await client.getDialogs({ limit: 500 })
  entityCache.clear()
  const groups = []
  for (const dialog of dialogs) {
    const entity = dialog.entity
    if (!isGroupEntity(entity)) continue
    const id = entityId(dialog, entity)
    const name = entityTitle(dialog, entity)
    if (!id || !name) continue
    entityCache.set(id, entity)
    groups.push({ id, name })
  }
  return groups.sort((a, b) => a.name.localeCompare(b.name))
}

const resolveEntity = async (chatId) => {
  if (entityCache.has(chatId)) return entityCache.get(chatId)
  await readGroups()
  if (entityCache.has(chatId)) return entityCache.get(chatId)
  try {
    return await client.getEntity(chatId)
  } catch {
    return null
  }
}

const messageBody = (message) => {
  return String(message?.message || message?.text || message?.rawText || '').trim()
}

const readMessagesForChats = async ({ chatIds, startTime, endTime, limit }) => {
  await ensureClient()
  if (status !== 'ready' || !client) return []

  const startMs = normaliseTimestampMs(startTime)
  const endMs = normaliseTimestampMs(endTime)
  const fetchLimit = Math.max(1, Math.min(toNumber(limit, 300), 1000))
  const groups = await readGroups()
  const groupNames = new Map(groups.map((group) => [group.id, group.name]))
  const messages = []

  for (const chatId of chatIds) {
    const entity = await resolveEntity(chatId)
    if (!entity) continue
    const chatMessages = await client.getMessages(entity, { limit: fetchLimit })
    for (const message of chatMessages) {
      const body = messageBody(message)
      if (!body) continue
      const timestamp = normaliseTimestampMs(message.date)
      if (startMs && timestamp < startMs) continue
      if (endMs && timestamp > endMs) continue
      const messageId = `${chatId}-${message.id ?? timestamp}`
      messages.push({
        chatId,
        chatName: groupNames.get(chatId) || entityTitle(null, entity) || chatId,
        messageId,
        timestamp,
        from: message.senderId == null ? null : String(message.senderId),
        author: message.senderId == null ? null : String(message.senderId),
        body,
      })
    }
  }

  return messages
    .sort((a, b) => a.timestamp - b.timestamp)
    .slice(-fetchLimit)
}

initialiseClient().catch((error) => {
  status = 'failed'
  statusMessage = error?.message || 'Telegram API 读取服务启动失败'
})

const server = http.createServer(async (request, response) => {
  if (request.method === 'OPTIONS') {
    response.writeHead(204, {
      'access-control-allow-origin': 'http://127.0.0.1:18882',
      'access-control-allow-methods': 'GET, POST, OPTIONS',
      'access-control-allow-headers': 'content-type',
    })
    response.end()
    return
  }

  if (request.url === '/status') {
    await ensureClient()
    sendJson(response, 200, {
      connected: status === 'ready',
      status,
      message: statusMessage,
      qr: qrValue,
    })
    return
  }

  if (request.url === '/groups') {
    await ensureClient()
    if (status !== 'ready') {
      sendJson(response, 200, {
        connected: false,
        status,
        message: statusMessage,
        groups: [],
      })
      return
    }

    try {
      const groups = await readGroups()
      sendJson(response, 200, {
        connected: true,
        status,
        message: `已读取 ${groups.length} 个 Telegram 群聊`,
        groups,
      })
    } catch (error) {
      sendJson(response, 500, {
        connected: false,
        status: 'failed',
        message: error?.message || '读取 Telegram 群聊失败',
        groups: [],
      })
    }
    return
  }

  if (request.url === '/messages' && request.method === 'POST') {
    await ensureClient()
    if (status !== 'ready') {
      sendJson(response, 200, {
        connected: false,
        status,
        message: statusMessage,
        messages: [],
      })
      return
    }

    try {
      const body = await readJsonBody(request)
      const chatIds = Array.isArray(body.chatIds)
        ? body.chatIds.map((item) => String(item || '').trim()).filter(Boolean)
        : []
      if (chatIds.length === 0) {
        sendJson(response, 400, {
          connected: true,
          status: 'bad_request',
          message: 'chatIds 不能为空',
          messages: [],
        })
        return
      }

      const messages = await readMessagesForChats({
        chatIds,
        startTime: body.startTime,
        endTime: body.endTime,
        limit: body.limit,
      })

      sendJson(response, 200, {
        connected: true,
        status,
        message: `已读取 ${messages.length} 条 Telegram 消息`,
        messages,
      })
    } catch (error) {
      sendJson(response, 500, {
        connected: false,
        status: 'failed',
        message: error?.message || '读取 Telegram 消息失败',
        messages: [],
      })
    }
    return
  }

  sendJson(response, 404, {
    connected: false,
    status: 'not_found',
    message: 'Not Found',
    groups: [],
  })
})

server.listen(port, '127.0.0.1', () => {
  console.log(`Telegram bridge listening at http://127.0.0.1:${port}`)
})

const closeClient = () => {
  try {
    saveSession()
    client?.disconnect?.()
  } catch {
    // ignore shutdown errors
  }
}

process.on('SIGINT', () => {
  closeClient()
  process.exit(0)
})

process.on('SIGTERM', () => {
  closeClient()
  process.exit(0)
})
