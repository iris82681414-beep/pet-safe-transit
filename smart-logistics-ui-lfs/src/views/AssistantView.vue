<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import type { ChatMessage } from '@/types'
import { assistantApi } from '@/services/api'
import { isDemoMode } from '@/services/config'
import { useLogisticsStore } from '@/stores/logistics'

interface Conversation {
  id: string
  title: string
  updatedAt: string
  messages: ChatMessage[]
  remoteSessionId?: string
}

const store = useLogisticsStore()
const { avatar, user, alerts, onlineCount, transitCount, pendingAlertCount } = storeToRefs(store)
const storageKey = 'smart-logistics-chat-conversations'
const activeStorageKey = 'smart-logistics-chat-active'

const welcomeMessage = (): ChatMessage => ({
  id: Date.now(),
  role: 'assistant',
  content: '你好，我是物流智能助手。我可以结合当前车辆、货物和告警数据，协助你查询运输状态与处理建议。',
  time: '刚刚',
})

const defaultConversations = (): Conversation[] => [
  {
    id: 'deviation',
    title: '车辆偏航处理建议',
    updatedAt: '刚刚',
    messages: [welcomeMessage()],
  },
  {
    id: 'alerts',
    title: '今日告警汇总',
    updatedAt: '昨天',
    messages: [
      { id: 2, role: 'user', content: '汇总今天的严重告警', time: '昨天 16:20' },
      { id: 3, role: 'assistant', content: '昨天共有 2 项严重告警，均已关闭；平均处理时间为 17 分钟。', time: '昨天 16:20' },
    ],
  },
  {
    id: 'device',
    title: '设备离线排查',
    updatedAt: '6 月 28 日',
    messages: [
      { id: 4, role: 'user', content: '设备离线应该怎么排查？', time: '6 月 28 日' },
      { id: 5, role: 'assistant', content: '请依次检查终端供电、SIM 卡网络和心跳配置，必要时执行远程重启。', time: '6 月 28 日' },
    ],
  },
]

function restoreConversations() {
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) || 'null') as Conversation[] | null
    if (Array.isArray(saved) && saved.length) return saved
  } catch {
    localStorage.removeItem(storageKey)
  }
  return defaultConversations()
}

const question = ref('')
const sending = ref(false)
const streamingMessageId = ref<number | null>(null)
const conversations = ref<Conversation[]>(restoreConversations())
const savedActive = localStorage.getItem(activeStorageKey)
const activeConversation = ref(conversations.value.some((item) => item.id === savedActive) ? savedActive! : conversations.value[0].id)
const chatRef = ref<HTMLElement>()
const suggestions = ref(['当前有哪些车辆发生偏航？', '沪A·C0291 预计几点到达？', '设备离线应该如何处理？', '汇总今天的严重告警'])
const currentConversation = computed(() => conversations.value.find((item) => item.id === activeConversation.value) || conversations.value[0])
const messages = computed<ChatMessage[]>({
  get: () => currentConversation.value?.messages || [],
  set: (value) => {
    if (currentConversation.value) currentConversation.value.messages = value
  },
})
const sessionId = computed<string | undefined>({
  get: () => currentConversation.value?.remoteSessionId,
  set: (value) => {
    if (currentConversation.value) currentConversation.value.remoteSessionId = value
  },
})

watch([conversations, activeConversation], () => {
  localStorage.setItem(storageKey, JSON.stringify(conversations.value))
  localStorage.setItem(activeStorageKey, activeConversation.value)
}, { deep: true })

function newConversation() {
  const id = `chat-${Date.now()}`
  conversations.value.unshift({
    id,
    title: '新会话',
    updatedAt: '刚刚',
    messages: [{ id: Date.now(), role: 'assistant', content: '新会话已创建，请输入你想咨询的物流问题。', time: '刚刚' }],
  })
  activeConversation.value = id
}

function loadConversation(key: string) {
  activeConversation.value = key
  void nextTick(() => {
    if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
  })
}

function deleteConversation(id: string, event?: MouseEvent) {
  event?.stopPropagation()
  conversations.value = conversations.value.filter((item) => item.id !== id)
  if (!conversations.value.length) newConversation()
  if (!conversations.value.some((item) => item.id === activeConversation.value)) {
    activeConversation.value = conversations.value[0].id
  }
}

function deleteMessage(id: number) {
  if (!currentConversation.value) return
  currentConversation.value.messages = currentConversation.value.messages.filter((item) => item.id !== id)
}

function renderMarkdown(content: string) {
  const html = marked.parse(content || '', { breaks: true, async: false }) as string
  return DOMPurify.sanitize(html)
}

function answerFor(value: string) {
  if (value.includes('偏航')) return '当前共有 1 辆车发生偏航：沪A·C0291，位于 G320 国道海宁段，偏离推荐路线约 8 公里，已持续 12 分钟。建议先联系司机确认临时绕行原因。'
  if (value.includes('0291') || value.includes('到达')) return '沪A·C0291 承运运单 SH20260629001，当前预计今天 16:42 到达杭州余杭物流中心，受偏航影响可能晚到约 18 分钟。'
  if (value.includes('离线')) return '建议依次检查：① 联系司机确认终端供电；② 检查 SIM 卡与网络；③ 尝试远程重启；④ 超过 10 分钟仍未恢复则创建人工巡检任务。'
  return '根据当前运营数据：5 辆车中 2 辆在途、4 台设备在线，现有 2 项待处理告警，其中 1 项为严重偏航告警。'
}

async function send(value = question.value) {
  const content = value.trim()
  if (!content || sending.value) return
  const targetConversation = currentConversation.value
  if (!targetConversation) return

  targetConversation.messages.push({ id: Date.now(), role: 'user', content, time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) })
  if (targetConversation.title === '新会话') targetConversation.title = content.slice(0, 18)
  targetConversation.updatedAt = '刚刚'

  const targetSessionId = targetConversation.remoteSessionId
  const appendAssistantMessage = (message: ChatMessage) => {
    targetConversation.messages.push(message)
    void scrollToBottom()
  }
  question.value = ''
  sending.value = true
  try {
    if (isDemoMode()) {
      await new Promise((resolve) => window.setTimeout(resolve, 450))
      appendAssistantMessage({ id: Date.now() + 1, role: 'assistant', content: answerFor(content), time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }), sources: [
        { documentId: 'demo-1', title: '车辆偏航处置规范', chunkId: '3.2', score: 0.93 },
      ] })
    } else {
      const assistantMessage: ChatMessage = {
        id: Date.now() + 1,
        role: 'assistant',
        content: '',
        time: '生成中',
        sources: [],
      }
      appendAssistantMessage(assistantMessage)
      streamingMessageId.value = assistantMessage.id
      const updateAssistantMessage = (patch: Partial<ChatMessage>) => {
        const index = targetConversation.messages.findIndex((item) => item.id === assistantMessage.id)
        if (index === -1) return
        targetConversation.messages[index] = {
          ...targetConversation.messages[index],
          ...patch,
        }
      }

      await assistantApi.chatStream(
        { question: content, sessionId: targetSessionId },
        {
          onToken: (token) => {
            assistantMessage.content += token
            updateAssistantMessage({ content: assistantMessage.content })
            void scrollToBottom(false)
          },
          onMeta: (meta) => {
            targetConversation.remoteSessionId = meta.sessionId || targetConversation.remoteSessionId
            const time = meta.answeredAt ? new Date(meta.answeredAt).toLocaleString('zh-CN') : new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
            assistantMessage.sources = normalizeSources(meta.sources || [])
            assistantMessage.time = time
            updateAssistantMessage({ sources: assistantMessage.sources, time })
          },
        },
      )
    }
  } catch (error) {
    const failedMessage = targetConversation.messages.find((item) => item.id === streamingMessageId.value)
    if (failedMessage && !failedMessage.content) failedMessage.content = '抱歉，智能问答暂时失败，请稍后再试。'
    ElMessage.error(error instanceof Error ? error.message : '智能问答请求失败')
  } finally {
    sending.value = false
    streamingMessageId.value = null
  }
  await scrollToBottom()
}

const currentSources = computed(() => [...messages.value].reverse().find((item) => item.role === 'assistant' && item.sources?.length)?.sources || [])
const criticalAlertCount = computed(() => alerts.value.filter((item) => item.severity === 'CRITICAL' && item.status === 'PENDING').length)

function normalizeSources(sources: ChatMessage['sources'] = []) {
  return sources.map((source) => ({
    documentId: source.documentId || 'unknown',
    title: source.title || source.documentId || '知识库片段',
    chunkId: source.chunkId || '-',
    score: typeof source.score === 'number' ? source.score : 0,
  }))
}

async function scrollToBottom(smooth = true) {
  await nextTick()
  chatRef.value?.scrollTo({ top: chatRef.value.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
}

onMounted(async () => {
  await nextTick()
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
  if (isDemoMode()) return
  try {
    suggestions.value = await assistantApi.suggestions()
  } catch {
    // 推荐问题失败不影响手动提问。
  }
})
</script>

<template>
  <div class="assistant-layout">
    <aside class="panel conversation-side">
      <el-button type="primary" icon="Plus" size="large" @click="newConversation">新建会话</el-button>
      <p class="section-kicker">最近会话</p>
      <div class="conversation-list">
        <div v-for="item in conversations" :key="item.id" class="conversation" :class="{ active: activeConversation === item.id }" role="button" tabindex="0" @click="loadConversation(item.id)" @keydown.enter="loadConversation(item.id)">
          <el-icon><ChatLineRound /></el-icon><span><strong>{{ item.title }}</strong><small>{{ item.updatedAt }}</small></span>
          <button class="conversation-delete" type="button" title="删除会话" @click="deleteConversation(item.id, $event)">
            <el-icon><Close /></el-icon>
          </button>
        </div>
      </div>
      <div class="knowledge-status"><el-icon><Collection /></el-icon><div><strong>知识库已连接</strong><span>18 份运营文档 · 326 个切片</span></div></div>
    </aside>

    <section class="panel chat-panel">
      <div class="chat-head">
        <div class="assistant-avatar"><el-icon><MagicStick /></el-icon></div>
        <div><h3>物流智能问答</h3><span><i></i>在线 · {{ currentConversation?.title || '新会话' }}</span></div>
        <el-button circle icon="MoreFilled" @click="ElMessage.info('会话与消息已自动保存在当前浏览器')" />
      </div>
      <div ref="chatRef" class="chat-messages">
        <div v-for="message in messages" :key="message.id" class="message" :class="message.role">
          <div v-if="message.role === 'assistant'" class="assistant-avatar small"><el-icon><MagicStick /></el-icon></div>
          <div class="bubble">
            <button class="message-delete" type="button" title="删除消息" @click="deleteMessage(message.id)"><el-icon><Close /></el-icon></button>
            <div v-if="message.role === 'assistant' && streamingMessageId === message.id && !message.content" class="typing"><i></i><i></i><i></i></div>
            <div v-else class="markdown-body" v-html="renderMarkdown(message.content)"></div>
            <span>{{ message.time }}</span>
          </div>
          <div v-if="message.role === 'user'" class="avatar small-avatar" :class="{ 'has-image': avatar }">
            <img v-if="avatar" :src="avatar" alt="用户头像" />
            <span v-else>{{ user?.name.slice(0, 1) || '用' }}</span>
          </div>
        </div>
        <div v-if="sending && !streamingMessageId" class="message assistant"><div class="assistant-avatar small"><el-icon><MagicStick /></el-icon></div><div class="bubble typing"><i></i><i></i><i></i></div></div>
      </div>
      <div class="suggestions">
        <button v-for="item in suggestions" :key="item" type="button" @click="send(item)">
          {{ item }}
        </button>
      </div>
      <div class="chat-input ai-soft-input">
        <el-input
          v-model="question"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 3 }"
          resize="none"
          placeholder="输入物流问题，按 Enter 发送"
          @keydown.enter.exact.prevent="send()"
        />
        <el-button type="primary" circle icon="Promotion" :loading="sending" @click="send()" />
      </div>
      <p class="ai-note">智能问答会结合当前车辆、订单、告警与知识库信息生成建议</p>
    </section>

    <aside class="view-stack assistant-context">
      <article class="panel context-card"><span class="section-kicker">业务上下文</span><h3>当前运行状态</h3><dl class="info-list"><div><dt>严重告警</dt><dd class="danger-text">{{ criticalAlertCount }} 项</dd></div><div><dt>在途车辆</dt><dd>{{ transitCount }} 辆</dd></div><div><dt>在线设备</dt><dd>{{ onlineCount }} 台</dd></div><div><dt>待处理告警</dt><dd>{{ pendingAlertCount }} 项</dd></div></dl></article>
      <article class="panel source-card">
        <div class="source-card-tools" aria-hidden="true">
          <span class="source-dot red"></span>
          <span class="source-dot yellow"></span>
          <span class="source-dot green"></span>
        </div>
        <span class="section-kicker">参考来源</span>
        <h3>知识依据</h3>
        <div v-for="source in currentSources" :key="`${source.documentId}-${source.chunkId}`"><el-icon><Document /></el-icon><span><strong>{{ source.title }}</strong><small>切片 {{ source.chunkId }} · 相关度 {{ source.score ? `${Math.round(source.score * 100)}%` : '已引用' }}</small></span></div>
        <p v-if="!currentSources.length" class="muted">回答后将在这里展示真实知识来源。</p>
      </article>
    </aside>
  </div>
</template>
