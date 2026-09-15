<script setup lang="ts">
import {
  ref,
  onMounted,
  onUnmounted,
  nextTick
} from 'vue'

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

/**
 * 消息滚动相关状态
 */
const messageListRef = ref<HTMLElement | null>(null)

// 用户是否正在底部附近
const isNearBottom = ref(true)

// 用户查看历史消息时，收到新消息显示提示
const hasNewMessages = ref(false)

// 距离底部 80 像素以内，认为用户正在查看最新消息
const SCROLL_BOTTOM_THRESHOLD = 80

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

    // 先加载历史消息
    await load()

    // 再连接 WebSocket
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
 * 判断消息列表是否接近底部
 */
function checkIsNearBottom() {
  const el = messageListRef.value

  if (!el) {
    return true
  }

  const distanceFromBottom =
    el.scrollHeight - el.scrollTop - el.clientHeight

  return distanceFromBottom <= SCROLL_BOTTOM_THRESHOLD
}

/**
 * 用户手动滚动消息列表
 */
function handleMessageScroll() {
  isNearBottom.value = checkIsNearBottom()

  // 用户重新滚动到底部后，隐藏新消息提示
  if (isNearBottom.value) {
    hasNewMessages.value = false
  }
}

/**
 * 滚动到消息列表底部
 *
 * force = true：
 * 不管用户当前在哪里，强制滚动到底部
 *
 * force = false：
 * 只有用户本来就在底部附近时才滚动
 */
async function scrollToBottom(force = false) {
  await nextTick()

  const el = messageListRef.value

  if (!el) {
    return
  }

  if (force || isNearBottom.value) {
    el.scrollTo({
      top: el.scrollHeight,
      behavior: force ? 'smooth' : 'auto'
    })

    isNearBottom.value = true
    hasNewMessages.value = false
  }
}

/**
 * 加载历史消息
 */
async function load() {
  if (loading.value) {
    return
  }

  loading.value = true

  try {
    const r = await api.get('/api/messages/' + conversationId)

    console.log('消息接口返回：', r.data)

    // 后端返回倒序，这里反转成正序
    messages.value = [...r.data].reverse()

    // 第一次进入页面，自动滚动到最新消息
    await scrollToBottom(true)
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

  if (!text) {
    return
  }

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
     * 这里不调用 load()
     *
     * 后端保存成功后，会通过 WebSocket 推送消息。
     * WebSocket 收到后会自动把消息添加到页面。
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
        async (message) => {
          try {
            const newMessage: ChatMessage = JSON.parse(message.body)

            console.log('收到实时消息：', newMessage)

            // 防止同一条消息重复添加
            const exists = messages.value.some(
              item => item.id === newMessage.id
            )

            if (exists) {
              return
            }

            /**
             * 必须在添加消息之前判断滚动状态。
             *
             * 如果用户正在查看历史消息，
             * 添加消息后不能改变当前滚动位置。
             */
            const shouldScroll = isNearBottom.value

            // 添加新消息
            messages.value.push(newMessage)

            await nextTick()

            if (shouldScroll) {
              // 用户在底部，自动滚动到最新消息
              await scrollToBottom()
            } else {
              // 用户正在查看历史消息，保持当前位置
              hasNewMessages.value = true
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

/**
 * 页面加载
 */
onMounted(() => {
  console.log('聊天页面已加载')
})

/**
 * 页面销毁
 */
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

      <div class="login-buttons">
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
      <!-- 顶部栏 -->
      <header class="chat-header">
        <div>
          <b>Enterprise IM</b>
          <span>产品研发群</span>
        </div>

        <div class="current-user">
          当前用户：
          {{ currentUser?.displayName || currentUser?.username }}
        </div>
      </header>

      <!-- 消息区域 -->
      <main class="chat-main">
        <div
          ref="messageListRef"
          class="messages"
          @scroll="handleMessageScroll"
        >
          <div
            v-for="m in messages"
            :key="m.id"
            class="message-item"
            :class="{
              mine: m.senderId === currentUser?.id
            }"
          >
            <div class="message-name">
              {{ m.senderName || ('用户' + m.senderId) }}
            </div>

            <div class="message-content">
              {{ m.content }}
            </div>
          </div>
        </div>

        <!-- 用户查看历史消息时显示 -->
        <button
          v-if="hasNewMessages"
          class="new-message-tip"
          @click="scrollToBottom(true)"
        >
          ↓ 有新消息
        </button>
      </main>

      <!-- 输入区域 -->
      <footer class="chat-footer">
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
* {
  box-sizing: border-box;
}

.page {
  min-height: 100vh;
  padding: 30px;
  background: #f5f7fa;
}

/* 登录区域 */
.login {
  width: 360px;
  max-width: 100%;
  margin: 100px auto;
  padding: 30px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.login h1 {
  margin: 0 0 10px;
  text-align: center;
}

.login p {
  margin: 0 0 25px;
  color: #909399;
  text-align: center;
}

.login .el-input {
  margin-bottom: 16px;
}

.login-buttons {
  display: flex;
  gap: 10px;
}

.login-buttons .el-button {
  flex: 1;
  margin: 0;
}

/* 聊天整体 */
.chat {
  width: 900px;
  max-width: 100%;
  height: 700px;
  max-height: calc(100vh - 60px);
  min-height: 0;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

/* 顶部 */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  padding: 18px 24px;
  border-bottom: 1px solid #ebeef5;
}

.chat-header b {
  display: block;
  font-size: 18px;
}

.chat-header span {
  display: block;
  margin-top: 5px;
  color: #909399;
  font-size: 13px;
}

.current-user {
  color: #606266;
  font-size: 14px;
}

/* 消息区域外层 */
.chat-main {
  position: relative;
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  background: #f8fafc;
}

/* 真正拥有滚动条的元素 */
.messages {
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 20px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

/* 单条消息 */
.message-item {
  width: fit-content;
  max-width: 70%;
  margin-bottom: 16px;
}

/* 自己发送的消息靠右 */
.message-item.mine {
  margin-left: auto;
  text-align: right;
}

/* 发送者名称 */
.message-name {
  margin-bottom: 5px;
  color: #909399;
  font-size: 13px;
}

/* 消息气泡 */
.message-content {
  display: inline-block;
  padding: 10px 14px;
  color: #303133;
  line-height: 1.5;
  word-break: break-word;
  background: white;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

/* 自己的消息气泡 */
.message-item.mine .message-content {
  color: #303133;
  background: #e1f3d8;
}

/* 新消息提示 */
.new-message-tip {
  position: absolute;
  right: 24px;
  bottom: 20px;
  z-index: 10;
  padding: 9px 15px;
  border: none;
  border-radius: 20px;
  color: white;
  background: #409eff;
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.18);
  cursor: pointer;
}

.new-message-tip:hover {
  background: #337ecc;
}

/* 底部输入区域 */
.chat-footer {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
  padding: 16px;
  border-top: 1px solid #ebeef5;
}

.chat-footer .el-input {
  flex: 1;
}

/* 手机适配 */
@media (max-width: 600px) {
  .page {
    padding: 10px;
  }

  .chat {
    height: calc(100vh - 20px);
    max-height: none;
  }

  .chat-header {
    align-items: flex-start;
    flex-direction: column;
    padding: 16px;
  }

  .current-user {
    font-size: 12px;
  }

  .messages {
    padding: 16px;
  }

  .message-item {
    max-width: 85%;
  }

  .chat-footer {
    padding: 10px;
  }
}
</style>