<script setup lang="ts">
import {
  ref,
  computed,
  nextTick,
  onMounted,
  onUnmounted
} from 'vue'

import axios from 'axios'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

interface UserInfo {
  id: number
  username: string
  displayName?: string
}

interface Conversation {
  id: number
  name: string
  ownerId: number
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

/**
 * 登录相关
 */
const username = ref('demo')
const password = ref('123456')

const logged = ref(false)
const token = ref('')

const currentUser = ref<UserInfo | null>(null)

/**
 * 群聊相关
 */
const conversations = ref<Conversation[]>([])

const activeConversationId = ref<number | null>(null)

const conversationSearch = ref('')

const newConversationName = ref('')

const inviteUsername = ref('')

const showCreateConversation = ref(false)

const showInviteUser = ref(false)

const conversationLoading = ref(false)

/**
 * 消息相关
 */
const content = ref('')

const messages = ref<ChatMessage[]>([])

const loading = ref(false)

const connecting = ref(false)

/**
 * 消息滚动相关
 */
const messageListRef = ref<HTMLElement | null>(null)

const isNearBottom = ref(true)

const hasNewMessages = ref(false)

const SCROLL_BOTTOM_THRESHOLD = 80

/**
 * WebSocket
 */
let stompClient: Client | null = null

/**
 * 当前选中的群聊
 */
const activeConversation = computed(() => {
  return conversations.value.find(
    item => item.id === activeConversationId.value
  ) || null
})

/**
 * 根据搜索框筛选群聊
 */
const filteredConversations = computed(() => {
  const keyword = conversationSearch.value
    .trim()
    .toLowerCase()

  if (!keyword) {
    return conversations.value
  }

  return conversations.value.filter(conversation => {
    return conversation.name
      .toLowerCase()
      .includes(keyword)
  })
})

/**
 * 判断是否为当前用户发送的消息
 */
function isMine(message: ChatMessage) {
  return message.senderId === currentUser.value?.id
}

/**
 * 登录
 */
async function login() {
  try {
    const response = await api.post('/api/users/login', {
      username: username.value,
      password: password.value
    })

    console.log('登录返回数据：', response.data)

    token.value = response.data.token || ''

    currentUser.value = response.data.user || {
      id: response.data.userId || response.data.id,
      username: response.data.username || username.value,
      displayName:
        response.data.displayName || username.value
    }

    if (!currentUser.value?.id) {
      alert('登录接口没有返回用户ID，请检查后端登录接口')
      return
    }

    logged.value = true

    // 登录后加载群聊
    await loadConversations()
  } catch (error) {
    console.error('登录失败：', error)
    alert('登录失败，请先注册或检查后端服务')
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
  } catch (error) {
    console.error('注册失败：', error)
    alert('注册失败，用户名可能已经存在')
  }
}

/**
 * 加载当前用户的群聊列表
 */
async function loadConversations() {
  if (!currentUser.value?.id) {
    return
  }

  conversationLoading.value = true

  try {
    const response = await api.get(
      '/api/conversations/user/' +
        currentUser.value.id
    )

    console.log('群聊列表：', response.data)

    conversations.value = response.data

    /**
     * 如果当前没有选中群聊，
     * 默认打开第一个群聊。
     */
    if (
      conversations.value.length > 0 &&
      !activeConversationId.value
    ) {
      await selectConversation(conversations.value[0])
    }
  } catch (error) {
    console.error('加载群聊失败：', error)

    /**
     * 如果后端暂时还没有群聊接口，
     * 可以临时使用默认群聊，避免页面完全空白。
     */
    if (conversations.value.length === 0) {
      conversations.value = [
        {
          id: 1,
          name: '产品研发群',
          ownerId: currentUser.value.id
        }
      ]

      await selectConversation(conversations.value[0])
    }
  } finally {
    conversationLoading.value = false
  }
}

/**
 * 选择群聊
 */
async function selectConversation(
  conversation: Conversation
) {
  if (
    activeConversationId.value === conversation.id &&
    stompClient?.active
  ) {
    return
  }

  // 断开旧群聊的 WebSocket
  disconnectWebSocket()

  activeConversationId.value = conversation.id

  messages.value = []

  hasNewMessages.value = false

  isNearBottom.value = true

  // 加载当前群聊历史消息
  await load()

  // 连接当前群聊 WebSocket
  connectWebSocket()
}

/**
 * 创建群聊
 */
async function createConversation() {
  const name = newConversationName.value.trim()

  if (!name) {
    alert('请输入群聊名称')
    return
  }

  if (!currentUser.value?.id) {
    alert('当前用户信息不存在，请重新登录')
    return
  }

  try {
    const response = await api.post(
      '/api/conversations',
      {
        name,
        ownerId: currentUser.value.id
      }
    )

    console.log('创建群聊返回：', response.data)

    newConversationName.value = ''

    showCreateConversation.value = false

    await loadConversations()

    /**
     * 如果后端返回了新群聊，
     * 自动选中新群聊。
     */
    if (response.data?.id) {
      const newConversation: Conversation = {
        id: response.data.id,
        name: response.data.name || name,
        ownerId:
          response.data.ownerId ||
          currentUser.value.id
      }

      const exists = conversations.value.some(
        item => item.id === newConversation.id
      )

      if (!exists) {
        conversations.value.unshift(newConversation)
      }

      await selectConversation(newConversation)
    }

    alert('群聊创建成功')
  } catch (error) {
    console.error('创建群聊失败：', error)
    alert('创建群聊失败，请检查后端群聊接口')
  }
}

/**
 * 邀请用户加入群聊
 *
 * 这里先根据用户名搜索用户，
 * 再把用户ID提交给群聊成员接口。
 */
async function inviteUser() {
  const targetUsername = inviteUsername.value.trim()

  if (!targetUsername) {
    alert('请输入要邀请的用户名')
    return
  }

  if (!activeConversationId.value) {
    alert('请先选择一个群聊')
    return
  }

  try {
    /**
     * 这里假设后端提供：
     * GET /api/users/search?keyword=用户名
     */
    const userResponse = await api.get(
      '/api/users/search',
      {
        params: {
          keyword: targetUsername
        }
      }
    )

    let targetUser: UserInfo | null = null

    /**
     * 兼容后端返回单个用户或用户数组
     */
    if (Array.isArray(userResponse.data)) {
      targetUser = userResponse.data[0] || null
    } else {
      targetUser = userResponse.data
    }

    if (!targetUser?.id) {
      alert('没有找到该用户')
      return
    }

    if (targetUser.id === currentUser.value?.id) {
      alert('不能邀请自己')
      return
    }

    /**
     * 邀请接口：
     * POST /api/conversations/{conversationId}/members
     */
    await api.post(
      '/api/conversations/' +
        activeConversationId.value +
        '/members',
      {
        userId: targetUser.id
      }
    )

    inviteUsername.value = ''

    showInviteUser.value = false

    alert(
      '已邀请 ' +
        (targetUser.displayName ||
          targetUser.username) +
        ' 加入群聊'
    )
  } catch (error) {
    console.error('邀请用户失败：', error)
    alert('邀请用户失败，请检查用户名或后端接口')
  }
}

/**
 * 判断消息列表是否接近底部
 */
function checkIsNearBottom() {
  const element = messageListRef.value

  if (!element) {
    return true
  }

  const distanceFromBottom =
    element.scrollHeight -
    element.scrollTop -
    element.clientHeight

  return distanceFromBottom <= SCROLL_BOTTOM_THRESHOLD
}

/**
 * 用户手动滚动消息列表
 */
function handleMessageScroll() {
  isNearBottom.value = checkIsNearBottom()

  /**
   * 用户自己滚动到底部后，
   * 隐藏“有新消息”提示。
   */
  if (isNearBottom.value) {
    hasNewMessages.value = false
  }
}

/**
 * 滚动到底部
 *
 * force = true：
 * 无论用户当前在哪里，都滚到底部。
 *
 * force = false：
 * 只有用户原本就在底部附近时才滚动。
 */
async function scrollToBottom(
  force = false
) {
  await nextTick()

  const element = messageListRef.value

  if (!element) {
    return
  }

  if (force || isNearBottom.value) {
    element.scrollTo({
      top: element.scrollHeight,
      behavior: force ? 'smooth' : 'auto'
    })

    isNearBottom.value = true

    hasNewMessages.value = false
  }
}

/**
 * 加载当前群聊的历史消息
 */
async function load() {
  if (!activeConversationId.value) {
    return
  }

  if (loading.value) {
    return
  }

  loading.value = true

  try {
    const response = await api.get(
      '/api/messages/' +
        activeConversationId.value
    )

    console.log('消息接口返回：', response.data)

    /**
     * 后端返回倒序消息，
     * 前端反转为时间正序。
     */
    messages.value = [
      ...response.data
    ].reverse()

    /**
     * 切换群聊后默认显示最新消息。
     */
    await scrollToBottom(true)
  } catch (error) {
    console.error('加载消息失败：', error)
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

  if (!activeConversationId.value) {
    alert('请先选择一个群聊')
    return
  }

  try {
    await api.post('/api/messages', {
      conversationId: activeConversationId.value,
      senderId: currentUser.value.id,
      content: text
    })

    content.value = ''

    /**
     * 不在这里调用 load()。
     *
     * 后端保存成功后，
     * WebSocket 会把消息推送回来。
     */
  } catch (error) {
    console.error('发送消息失败：', error)
    alert('发送消息失败')
  }
}

/**
 * 连接当前群聊的 WebSocket
 */
function connectWebSocket() {
  if (!activeConversationId.value) {
    return
  }

  if (stompClient?.active || connecting.value) {
    return
  }

  connecting.value = true

  const currentConversationId =
    activeConversationId.value

  stompClient = new Client({
    webSocketFactory: () => {
      return new SockJS(
        'http://localhost:8080/ws'
      )
    },

    // 断线后5秒自动重连
    reconnectDelay: 5000,

    // 心跳
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    debug: message => {
      console.log('[STOMP]', message)
    },

    onConnect: () => {
      connecting.value = false

      console.log(
        'WebSocket连接成功，群聊ID：',
        currentConversationId
      )

      stompClient?.subscribe(
        '/topic/conversation/' +
          currentConversationId,
        async message => {
          try {
            const newMessage: ChatMessage =
              JSON.parse(message.body)

            console.log(
              '收到实时消息：',
              newMessage
            )

            /**
             * 如果当前已经切换到其他群聊，
             * 忽略旧群聊的消息。
             */
            if (
              newMessage.conversationId !==
              activeConversationId.value
            ) {
              return
            }

            /**
             * 防止重复添加消息。
             */
            const exists = messages.value.some(
              item => item.id === newMessage.id
            )

            if (exists) {
              return
            }

            /**
             * 在添加消息之前记录滚动状态。
             */
            const shouldScroll =
              isNearBottom.value

            messages.value.push(newMessage)

            await nextTick()

            if (shouldScroll) {
              /**
               * 用户在底部：
               * 自动显示最新消息。
               */
              await scrollToBottom()
            } else {
              /**
               * 用户正在查看历史消息：
               * 不改变当前位置，只显示提示。
               */
              hasNewMessages.value = true
            }
          } catch (error) {
            console.error(
              '解析WebSocket消息失败：',
              error
            )
          }
        }
      )
    },

    onDisconnect: () => {
      connecting.value = false

      console.log('WebSocket已断开')
    },

    onStompError: frame => {
      connecting.value = false

      console.error(
        'STOMP服务端错误：',
        frame.headers['message'],
        frame.body
      )
    },

    onWebSocketError: error => {
      connecting.value = false

      console.error(
        'WebSocket连接错误：',
        error
      )
    }
  })

  stompClient.activate()
}

/**
 * 断开WebSocket
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
    <!-- 登录页面 -->
    <div
      v-if="!logged"
      class="login"
    >
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

    <!-- 聊天页面 -->
    <div
      v-else
      class="chat"
    >
      <!-- 顶部 -->
      <header class="chat-header">
        <div>
          <b>Enterprise IM</b>
          <span>企业级即时通信平台</span>
        </div>

        <div class="current-user">
          当前用户：
          {{
            currentUser?.displayName ||
            currentUser?.username
          }}
        </div>
      </header>

      <!-- 聊天主体 -->
      <div class="chat-body">
        <!-- 左侧群聊列表 -->
        <aside class="conversation-sidebar">
          <div class="sidebar-title">
            <b>群聊列表</b>

            <el-button
              type="primary"
              size="small"
              @click="
                showCreateConversation = true
              "
            >
              新建群聊
            </el-button>
          </div>

          <el-input
            v-model="conversationSearch"
            placeholder="搜索群聊"
            clearable
            class="conversation-search"
          />

          <div
            v-if="conversationLoading"
            class="empty-tip"
          >
            正在加载群聊...
          </div>

          <div
            v-else-if="
              filteredConversations.length === 0
            "
            class="empty-tip"
          >
            暂无群聊
          </div>

          <div
            v-else
            class="conversation-list"
          >
            <div
              v-for="conversation in filteredConversations"
              :key="conversation.id"
              class="conversation-item"
              :class="{
                active:
                  conversation.id ===
                  activeConversationId
              }"
              @click="
                selectConversation(conversation)
              "
            >
              <div class="conversation-avatar">
                {{
                  conversation.name.slice(0, 1)
                }}
              </div>

              <div class="conversation-info">
                <b>
                  {{ conversation.name }}
                </b>

                <span>
                  群聊 ID：{{ conversation.id }}
                </span>
              </div>
            </div>
          </div>
        </aside>

        <!-- 右侧聊天内容 -->
        <section class="chat-content">
          <!-- 当前群聊顶部 -->
          <div class="conversation-header">
            <div>
              <b>
                {{
                  activeConversation?.name ||
                  '请选择群聊'
                }}
              </b>

              <span
                v-if="activeConversation"
              >
                群聊 ID：
                {{ activeConversation.id }}
              </span>
            </div>

            <div class="conversation-actions">
              <el-button
                size="small"
                :disabled="
                  !activeConversationId
                "
                @click="
                  showInviteUser = true
                "
              >
                邀请用户
              </el-button>
            </div>
          </div>

          <!-- 消息区域 -->
          <main class="chat-main">
            <div
              ref="messageListRef"
              class="messages"
              @scroll="handleMessageScroll"
            >
              <div
                v-if="loading"
                class="loading-message"
              >
                正在加载消息...
              </div>

              <div
                v-else-if="messages.length === 0"
                class="empty-message"
              >
                暂无消息，发送第一条消息吧
              </div>

              <div
                v-for="message in messages"
                :key="message.id"
                class="message-item"
                :class="{
                  mine: isMine(message)
                }"
              >
                <div class="message-name">
                  {{
                    message.senderName ||
                    ('用户' + message.senderId)
                  }}
                </div>

                <div class="message-content">
                  {{ message.content }}
                </div>
              </div>
            </div>

            <!-- 新消息提示 -->
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
              placeholder="输入消息，按 Enter 发送"
              clearable
              @keyup.enter="send"
            />

            <el-button
              type="primary"
              :disabled="
                !activeConversationId ||
                !content.trim()
              "
              @click="send"
            >
              发送
            </el-button>
          </footer>
        </section>
      </div>
    </div>

    <!-- 新建群聊弹窗 -->
    <el-dialog
      v-model="showCreateConversation"
      title="新建群聊"
      width="420px"
    >
      <el-input
        v-model="newConversationName"
        placeholder="请输入群聊名称"
        maxlength="100"
        show-word-limit
        clearable
        @keyup.enter="createConversation"
      />

      <template #footer>
        <el-button
          @click="
            showCreateConversation = false
          "
        >
          取消
        </el-button>

        <el-button
          type="primary"
          @click="createConversation"
        >
          创建群聊
        </el-button>
      </template>
    </el-dialog>

    <!-- 邀请用户弹窗 -->
    <el-dialog
      v-model="showInviteUser"
      title="邀请用户加入群聊"
      width="420px"
    >
      <el-input
        v-model="inviteUsername"
        placeholder="请输入用户名"
        clearable
        @keyup.enter="inviteUser"
      />

      <template #footer>
        <el-button
          @click="showInviteUser = false"
        >
          取消
        </el-button>

        <el-button
          type="primary"
          @click="inviteUser"
        >
          邀请用户
        </el-button>
      </template>
    </el-dialog>
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

/* 登录页面 */
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
  width: 1100px;
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

/* 聊天主体 */
.chat-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

/* 左侧群聊列表 */
.conversation-sidebar {
  width: 280px;
  min-width: 230px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  border-right: 1px solid #ebeef5;
  background: #ffffff;
}

.sidebar-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.sidebar-title b {
  font-size: 16px;
}

.conversation-search {
  width: 100%;
}

.conversation-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  margin-bottom: 6px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.conversation-item:hover {
  background: #f2f6fc;
}

.conversation-item.active {
  background: #ecf5ff;
}

.conversation-avatar {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  color: white;
  font-size: 18px;
  background: #409eff;
  border-radius: 8px;
}

.conversation-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.conversation-info b {
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-info span {
  color: #909399;
  font-size: 12px;
}

.empty-tip {
  padding: 20px 5px;
  color: #909399;
  font-size: 13px;
  text-align: center;
}

/* 右侧聊天区域 */
.chat-content {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* 当前群聊标题 */
.conversation-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
}

.conversation-header b {
  display: block;
  color: #303133;
  font-size: 16px;
}

.conversation-header span {
  display: block;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

/* 消息区域外层 */
.chat-main {
  position: relative;
  flex: 1;
  min-height: 0;
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

/* 空消息状态 */
.empty-message,
.loading-message {
  padding: 40px 10px;
  color: #909399;
  font-size: 14px;
  text-align: center;
}

/* 消息 */
.message-item {
  width: fit-content;
  max-width: 75%;
  margin-bottom: 16px;
}

.message-item.mine {
  margin-left: auto;
  text-align: right;
}

.message-name {
  margin-bottom: 5px;
  color: #909399;
  font-size: 13px;
}

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

/* 底部输入框 */
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
@media (max-width: 800px) {
  .page {
    padding: 10px;
  }

  .chat {
    height: calc(100vh - 20px);
    max-height: none;
  }

  .conversation-sidebar {
    width: 220px;
    min-width: 180px;
    padding: 12px;
  }

  .chat-header {
    align-items: flex-start;
    flex-direction: column;
    padding: 16px;
  }

  .current-user {
    font-size: 12px;
  }

  .message-item {
    max-width: 85%;
  }
}

@media (max-width: 600px) {
  .chat-body {
    flex-direction: column;
  }

  .conversation-sidebar {
    width: 100%;
    min-width: 0;
    max-height: 220px;
    border-right: none;
    border-bottom: 1px solid #ebeef5;
  }

  .conversation-list {
    display: flex;
    gap: 6px;
    overflow-x: auto;
    overflow-y: hidden;
  }

  .conversation-item {
    min-width: 180px;
    margin-bottom: 0;
  }

  .conversation-header {
    padding: 12px;
  }

  .messages {
    padding: 14px;
  }

  .chat-footer {
    padding: 10px;
  }
}
</style>