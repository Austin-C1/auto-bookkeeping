import http from 'node:http'
import whatsappWeb from 'whatsapp-web.js'
import qrcode from 'qrcode-terminal'

const { Client, LocalAuth } = whatsappWeb

const port = Number(process.env.WHATSAPP_BRIDGE_PORT || 18883)
let status = 'starting'
let statusMessage = 'WhatsApp 读取服务启动中'
let lastQr = ''

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
  const numeric = toNumber(value, 0)
  if (numeric <= 0) return 0
  return numeric < 10_000_000_000 ? numeric * 1000 : numeric
}

const findChatById = async (chatId) => {
  try {
    return await client.getChatById(chatId)
  } catch {
    const chats = await client.getChats()
    return chats.find((chat) => {
      const id = chat.id?._serialized || chat.id?.user || chat.name
      return id === chatId || chat.name === chatId
    })
  }
}

const fetchMessagesForChats = async ({ chatIds, startTime, endTime, limit }) => {
  const startMs = normaliseTimestampMs(startTime)
  const endMs = normaliseTimestampMs(endTime)
  const fetchLimit = Math.max(1, Math.min(toNumber(limit, 300), 1000))
  const messages = []

  for (const chatId of chatIds) {
    const chat = await findChatById(chatId)
    if (!chat) continue

    const chatMessages = await chat.fetchMessages({ limit: fetchLimit })
    for (const message of chatMessages) {
      const timestampMs = normaliseTimestampMs(message.timestamp)
      if (startMs && timestampMs < startMs) continue
      if (endMs && timestampMs > endMs) continue
      const body = (message.body || '').trim()
      if (!body) continue
      messages.push({
        chatId: chat.id?._serialized || chat.id?.user || chatId,
        chatName: chat.name || chat.formattedTitle || chatId,
        messageId: message.id?._serialized || message.id?.id || `${chatId}-${message.timestamp}`,
        timestamp: timestampMs,
        from: message.from || null,
        author: message.author || message.from || null,
        body,
      })
    }
  }

  return messages.sort((a, b) => a.timestamp - b.timestamp)
}

const client = new Client({
  authStrategy: new LocalAuth({ clientId: 'blackcat-bookkeeping' }),
  puppeteer: {
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  },
})

client.on('qr', (qr) => {
  status = 'qr_required'
  statusMessage = '需要扫码登录 WhatsApp'
  lastQr = qr
  qrcode.generate(qr, { small: true })
})

client.on('authenticated', () => {
  status = 'authenticated'
  statusMessage = 'WhatsApp 已授权，正在加载群聊'
})

client.on('ready', () => {
  status = 'ready'
  statusMessage = 'WhatsApp 已连接'
})

client.on('disconnected', (reason) => {
  status = 'disconnected'
  statusMessage = `WhatsApp 已断开：${reason}`
})

client.initialize().catch((error) => {
  status = 'failed'
  statusMessage = error?.message || 'WhatsApp 读取服务启动失败'
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
    sendJson(response, 200, {
      connected: status === 'ready',
      status,
      message: statusMessage,
      qr: lastQr,
    })
    return
  }

  if (request.url === '/groups') {
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
      const chats = await client.getChats()
      const groups = chats
        .filter((chat) => chat.isGroup)
        .map((chat) => ({
          id: chat.id?._serialized || chat.id?.user || chat.name,
          name: chat.name || chat.formattedTitle || chat.id?._serialized,
        }))
        .filter((chat) => chat.id && chat.name)

      sendJson(response, 200, {
        connected: true,
        status,
        message: `已读取 ${groups.length} 个 WhatsApp 群聊`,
        groups,
      })
    } catch (error) {
      sendJson(response, 500, {
        connected: false,
        status: 'failed',
        message: error?.message || '读取 WhatsApp 群聊失败',
        groups: [],
      })
    }
    return
  }

  if (request.url === '/messages' && request.method === 'POST') {
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

      const messages = await fetchMessagesForChats({
        chatIds,
        startTime: body.startTime,
        endTime: body.endTime,
        limit: body.limit,
      })

      sendJson(response, 200, {
        connected: true,
        status,
        message: `已读取 ${messages.length} 条 WhatsApp 消息`,
        messages,
      })
    } catch (error) {
      sendJson(response, 500, {
        connected: false,
        status: 'failed',
        message: error?.message || '读取 WhatsApp 消息失败',
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
  console.log(`WhatsApp bridge listening at http://127.0.0.1:${port}`)
})
