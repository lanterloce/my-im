<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import axios from 'axios'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

interface UserInfo {
  id: number
  username: string
  displayName?: string
}

interface ChatMessage {
  id: number
  conversationId: number
  senderId: number
  senderName?: string
  content: string
  createdAt?: string
}

const api = axios.create({
  baseURL: 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json'
  }
})

const username = ref('demo')
const password = ref('123456')
const logged = ref(false)
const token = ref('')
const currentUser = ref<UserInfo | null>(null)
const content = ref('')
const messages = ref<ChatMessage[]>([])
const conversationId = 1
const loading = ref(false)
const connecting = ref(false)

let stompClient: Client | null = null

/**
 * 登录
 */
async function login() {
  try {
    const r = await api.post('/api/users/login', {
      username: username.value,
      password: password.value
    })

    console.log('登录返回数据：', r.data)

    token.value = r.data.token || ''

    currentUser.value = r.data.user || {
      id: r.data.userId || r.data.id,
      username: r.data.username || username.value,
      displayName: r.data.displayName || username.value
    }

    if (!currentUser.value?.id) {
      alert('登录接口没有返回用户ID，请检查后端登录接口')
      return
    }

    logged.value = true

    await load()

    connectWebSocket()
  } catch (e) {
    console.error('登录失败：', e)
    alert('登录失败：请先注册或检查后端')
  }
}

/**
 * 注册
 */
async function register() {
  try {
    await api.post('/api/users/register', {
      username: username.value,
      password: password.value
    })

    alert('注册成功，请登录')
  } catch (e) {
    console.error('注册失败：', e)
    alert('注册失败，用户名可能已经存在')
  }
}

/**
 * 加载历史消息
 */
async function load() {
  if (loading.value) return

  loading.value = true

  try {
    const r = await api.get('/api/messages/' + conversationId)

    console.log('消息接口返回：', r.data)

    // 后端返回的是倒序，所以这里复制后再 reverse
    messages.value = [...r.data].reverse()

    await nextTick()
  } catch (e) {
    console.error('加载消息失败：', e)
  } finally {
    loading.value = false
  }
}

/**
 * 发送消息
 */
async function send() {
  const text = content.value.trim()

  if (!text) return

  if (!currentUser.value?.id) {
    alert('当前用户信息不存在，请重新登录')
    return
  }

  try {
    await api.post('/api/messages', {
      conversationId,
      senderId: currentUser.value.id,
      content: text
    })

    content.value = ''

    /**
     * 发送成功后不调用 load()
     * 因为后端会通过 WebSocket 把消息推送回来
     */
  } catch (e) {
    console.error('发送消息失败：', e)
    alert('发送消息失败')
  }
}

/**
 * 连接 WebSocket
 */
function connectWebSocket() {
  if (stompClient?.active || connecting.value) {
    return
  }

  connecting.value = true

  stompClient = new Client({
    webSocketFactory: () => {
      return new SockJS('http://localhost:8080/ws')
    },

    // 连接断开后，5秒自动重连
    reconnectDelay: 5000,

    // 心跳配置
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    debug: (message) => {
      console.log('[STOMP]', message)
    },

    onConnect: () => {
      connecting.value = false

      console.log('WebSocket 连接成功')

      stompClient?.subscribe(
        '/topic/conversation/' + conversationId,
        (message) => {
          try {
            const newMessage: ChatMessage = JSON.parse(message.body)

            console.log('收到实时消息：', newMessage)

            // 防止同一条消息重复添加
            const exists = messages.value.some(
              item => item.id === newMessage.id
            )

            if (!exists) {
              messages.value.push(newMessage)
            }
          } catch (e) {
            console.error('解析 WebSocket 消息失败：', e)
          }
        }
      )
    },

    onDisconnect: () => {
      connecting.value = false
      console.log('WebSocket 已断开')
    },

    onStompError: (frame) => {
      connecting.value = false

      console.error(
        'STOMP 服务端错误：',
        frame.headers['message'],
        frame.body
      )
    },

    onWebSocketError: (error) => {
      connecting.value = false
      console.error('WebSocket 连接错误：', error)
    }
  })

  stompClient.activate()
}

/**
 * 断开 WebSocket
 */
function disconnectWebSocket() {
  if (stompClient) {
    stompClient.deactivate()
    stompClient = null
  }

  connecting.value = false
}

onMounted(() => {
  console.log('聊天页面已加载')
})

onUnmounted(() => {
  disconnectWebSocket()
})
</script>

<template>
  <div class="page">
    <!-- 登录区域 -->
    <div v-if="!logged" class="login">
      <h1>Enterprise IM</h1>
      <p>企业级即时通信平台</p>

      <el-input
        v-model="username"
        placeholder="用户名"
        clearable
      />

      <el-input
        v-model="password"
        type="password"
        placeholder="密码"
        show-password
        clearable
      />

      <div>
        <el-button
          type="primary"
          @click="login"
        >
          登录
        </el-button>

        <el-button
          @click="register"
        >
          注册
        </el-button>
      </div>
    </div>

    <!-- 聊天区域 -->
    <div v-else class="chat">
      <header>
        <div>
          <b>Enterprise IM</b>
          <span>产品研发群</span>
        </div>

        <div class="current-user">
          当前用户：
          {{ currentUser?.displayName || currentUser?.username }}
        </div>
      </header>

      <main>
        <div
          v-for="m in messages"
          :key="m.id"
          class="msg"
          :class="{
            'my-message': m.senderId === currentUser?.id
          }"
        >
          <b>
            {{ m.senderName || ('用户' + m.senderId) }}
          </b>

          <p>{{ m.content }}</p>
        </div>
      </main>

      <footer>
        <el-input
          v-model="content"
          placeholder="输入消息"
          @keyup.enter="send"
        />

        <el-button
          type="primary"
          @click="send"
        >
          发送
        </el-button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 30px;
  box-sizing: border-box;
  background: #f5f7fa;
}

.login {
  width: 360px;
  max-width: 100%;
  margin: 100px auto;
  padding: 30px;
  box-sizing: border-box;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.login h1 {
  margin-bottom: 10px;
  text-align: center;
}

.login p {
  margin-bottom: 25px;
  color: #909399;
  text-align: center;
}

.login .el-input {
  margin-bottom: 16px;
}

.login .el-button {
  margin-top: 8px;
}

.chat {
  width: 900px;
  max-width: 100%;
  height: 700px;
  max-height: calc(100vh - 60px);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.chat header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 24px;
  border-bottom: 1px solid #ebeef5;
}

.chat header b {
  display: block;
  font-size: 18px;
}

.chat header span {
  display: block;
  margin-top: 5px;
  color: #909399;
  font-size: 13px;
}

.current-user {
  color: #606266;
  font-size: 14px;
}

.chat main {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: #f8fafc;
}

.msg {
  width: fit-content;
  max-width: 70%;
  margin-bottom: 16px;
  padding: 10px 14px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.msg b {
  color: #409eff;
  font-size: 13px;
}

.msg p {
  margin: 6px 0 0;
  color: #303133;
  word-break: break-word;
}

.my-message {
  margin-left: auto;
  background: #e1f3d8;
}

.my-message b {
  color: #67c23a;
}

.chat footer {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid #ebeef5;
}

.chat footer .el-input {
  flex: 1;
}

@media (max-width: 600px) {
  .page {
    padding: 10px;
  }

  .chat {
    height: calc(100vh - 20px);
    max-height: none;
  }

  .chat header {
    align-items: flex-start;
    flex-direction: column;
  }

  .current-user {
    font-size: 12px;
  }

  .msg {
    max-width: 85%;
  }
}
</style>