<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import type { ChatMessage } from '@/types'
import { agentApi, assistantApi } from '@/services/api'
import { isDemoMode } from '@/services/config'
import { useLogisticsStore } from '@/stores/logistics'
import { executeAgentAction } from '@/utils/actionExecutor'
import { getAgentApprovalMode, type AgentApprovalMode } from '@/utils/agentApproval'

const petImage = new URL('../assets/iot-sheep-pet.png', import.meta.url).href

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
const recording = ref(false)
const recognizingVoice = ref(false)
const speechEnabled = ref(localStorage.getItem('smart-logistics-assistant-speech') !== 'off')
const approvalMode = ref<AgentApprovalMode>(getAgentApprovalMode())
const speakingMessageId = ref<number | null>(null)
type PetMood = 'idle' | 'listening' | 'thinking' | 'speaking' | 'happy' | 'working' | 'alert' | 'received' | 'analysis'
const petMood = ref<PetMood>('idle')
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
const petCopy = computed(() => ({
  idle: '嗨！我是智能小羊', listening: '我在认真听…', thinking: '正在理解你的意图…', speaking: '正在为你播报结果',
  happy: '太好了，任务完成！', working: '正在执行物流任务…', alert: '发现告警，马上处理！', received: '收到！已经安排好了', analysis: '正在分析业务数据…',
}[petMood.value]))
const petStatusText = computed(() => ({
  idle: '待命中', listening: '倾听中', thinking: '思考中', speaking: '播报中', happy: '开心', working: '工作中', alert: '告警提醒', received: '已收到', analysis: '数据分析',
}[petMood.value]))
const sessionId = computed<string | undefined>({
  get: () => currentConversation.value?.remoteSessionId,
  set: (value) => {
    if (currentConversation.value) currentConversation.value.remoteSessionId = value
  },
})

let voiceStream: MediaStream | null = null
let voiceContext: AudioContext | null = null
let voiceSource: MediaStreamAudioSourceNode | null = null
let voiceProcessor: ScriptProcessorNode | null = null
let voiceSampleRate = 16000
let voiceLength = 0
let voiceChunks: Float32Array[] = []
let voiceMaxTimer: number | null = null
let speechAudio: HTMLAudioElement | null = null
let speechObjectUrl = ''
let speechGenerationId = 0
let petMoodTimer: number | null = null

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

function plainText(content: string) {
  const wrapper = document.createElement('div')
  wrapper.innerHTML = renderMarkdown(content)
  return (wrapper.textContent || '').trim()
}

function stopSpeech() {
  speechGenerationId += 1
  speechAudio?.pause()
  speechAudio = null
  if (speechObjectUrl) URL.revokeObjectURL(speechObjectUrl)
  speechObjectUrl = ''
  speakingMessageId.value = null
  if (petMood.value === 'speaking') setPetMood('idle')
}

function toggleSpeech() {
  speechEnabled.value = !speechEnabled.value
  localStorage.setItem('smart-logistics-assistant-speech', speechEnabled.value ? 'on' : 'off')
  if (!speechEnabled.value) stopSpeech()
  ElMessage.success(speechEnabled.value ? '智能问答语音播报已开启' : '智能问答语音播报已关闭')
}

function syncApprovalMode(event: Event) {
  approvalMode.value = ((event as CustomEvent<{ mode?: AgentApprovalMode }>).detail?.mode) || getAgentApprovalMode()
}

function setPetMood(mood: PetMood, resetAfter = 0) {
  if (petMoodTimer != null) window.clearTimeout(petMoodTimer)
  petMoodTimer = null
  petMood.value = mood
  window.dispatchEvent(new CustomEvent('smart-logistics:desk-pet-state', {
    detail: { mood, message: petCopy.value, priority: mood === 'alert' ? 'HIGH' : 'INFO' },
  }))
  if (resetAfter > 0) {
    petMoodTimer = window.setTimeout(() => {
      if (petMood.value === mood) setPetMood('idle')
      petMoodTimer = null
    }, resetAfter)
  }
}

function moodForAgentFunction(name: unknown): PetMood {
  const value = String(name || '')
  if (value.includes('alert')) return 'alert'
  if (value.startsWith('query_') || value.includes('rating')) return 'analysis'
  if (value === 'navigate_page' || value === 'locate_vehicle') return 'received'
  return 'working'
}

function handleGlobalVoiceState(event: Event) {
  if (recording.value || recognizingVoice.value || sending.value || speakingMessageId.value != null) return
  const status = String((event as CustomEvent<{ status?: string }>).detail?.status || '')
  if (status === 'requesting' || status === 'recording') setPetMood('listening')
  else if (status === 'uploading') setPetMood('thinking')
  else if (status === 'executing') setPetMood('working')
  else if (status === 'idle' && ['listening', 'thinking', 'working'].includes(petMood.value)) setPetMood('idle')
}

function handleAgentComplete(event: Event) {
  if (sending.value || speakingMessageId.value != null) return
  const detail = (event as CustomEvent<Record<string, any>>).detail || {}
  const name = detail.functionName || detail.action?.functionName || detail.action?.type || detail.url
  const mood = moodForAgentFunction(name)
  setPetMood(mood === 'alert' ? 'alert' : 'received', 1800)
}

async function speak(content: string, messageId?: number) {
  if (!speechEnabled.value) return
  const text = plainText(content)
  if (!text) return
  stopSpeech()
  const generationId = ++speechGenerationId
  if (petMood.value === 'idle') setPetMood('thinking')
  try {
    const blob = await assistantApi.speech(text)
    if (generationId !== speechGenerationId) return
    speechObjectUrl = URL.createObjectURL(blob)
    speechAudio = new Audio(speechObjectUrl)
    speakingMessageId.value = messageId || null
    setPetMood('speaking')
    speechAudio.onended = () => stopSpeech()
    speechAudio.onerror = () => {
      stopSpeech()
      ElMessage.error('ElevenLabs 语音播放失败')
    }
    await speechAudio.play()
  } catch (error) {
    if (generationId === speechGenerationId) {
      speakingMessageId.value = null
      if (petMood.value === 'thinking' || petMood.value === 'speaking') setPetMood('idle')
      ElMessage.error(error instanceof Error ? error.message : 'ElevenLabs 语音生成失败')
    }
  }
}

function stopVoiceHardware() {
  if (voiceMaxTimer != null) window.clearTimeout(voiceMaxTimer)
  voiceMaxTimer = null
  voiceProcessor?.disconnect()
  voiceSource?.disconnect()
  voiceProcessor = null
  voiceSource = null
  voiceStream?.getTracks().forEach((track) => track.stop())
  voiceStream = null
  void voiceContext?.close()
  voiceContext = null
}

function mergeVoiceChunks() {
  const merged = new Float32Array(voiceLength)
  let offset = 0
  voiceChunks.forEach((chunk) => {
    merged.set(chunk, offset)
    offset += chunk.length
  })
  return merged
}

function resampleVoice(input: Float32Array, fromRate: number, toRate: number) {
  if (fromRate === toRate) return input
  const ratio = fromRate / toRate
  const output = new Float32Array(Math.round(input.length / ratio))
  for (let i = 0; i < output.length; i += 1) {
    const start = Math.floor(i * ratio)
    const end = Math.min(Math.floor((i + 1) * ratio), input.length)
    let sum = 0
    for (let j = start; j < end; j += 1) sum += input[j]
    output[i] = sum / Math.max(1, end - start)
  }
  return output
}

function voiceWavBlob(input: Float32Array, fromRate: number) {
  const samples = resampleVoice(input, fromRate, 16000)
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const ascii = (offset: number, value: string) => {
    for (let i = 0; i < value.length; i += 1) view.setUint8(offset + i, value.charCodeAt(i))
  }
  ascii(0, 'RIFF'); view.setUint32(4, 36 + samples.length * 2, true); ascii(8, 'WAVE'); ascii(12, 'fmt ')
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true)
  view.setUint32(24, 16000, true); view.setUint32(28, 32000, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true)
  ascii(36, 'data'); view.setUint32(40, samples.length * 2, true)
  samples.forEach((sample, index) => {
    const value = Math.max(-1, Math.min(1, sample))
    view.setInt16(44 + index * 2, value < 0 ? value * 0x8000 : value * 0x7fff, true)
  })
  return new Blob([view], { type: 'audio/wav' })
}

async function startVoiceMessage() {
  if (recording.value || recognizingVoice.value || sending.value) return
  if (!navigator.mediaDevices?.getUserMedia) return ElMessage.error('当前浏览器不支持录音')
  try {
    stopSpeech()
    voiceChunks = []
    voiceLength = 0
    voiceStream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true } })
    const AudioContextCtor = window.AudioContext || (window as any).webkitAudioContext
    voiceContext = new AudioContextCtor({ sampleRate: 16000 })
    if (voiceContext.state === 'suspended') await voiceContext.resume()
    voiceSampleRate = voiceContext.sampleRate
    voiceSource = voiceContext.createMediaStreamSource(voiceStream)
    voiceProcessor = voiceContext.createScriptProcessor(4096, 1, 1)
    voiceProcessor.onaudioprocess = (event) => {
      const chunk = new Float32Array(event.inputBuffer.getChannelData(0))
      voiceChunks.push(chunk)
      voiceLength += chunk.length
      event.outputBuffer.getChannelData(0).fill(0)
    }
    voiceSource.connect(voiceProcessor)
    voiceProcessor.connect(voiceContext.destination)
    recording.value = true
    setPetMood('listening')
    voiceMaxTimer = window.setTimeout(() => void stopVoiceMessage(), 20_000)
    ElMessage.info('正在录音，再次点击麦克风即可发送')
  } catch (error) {
    stopVoiceHardware()
    setPetMood('idle')
    ElMessage.error(error instanceof Error ? error.message : '无法打开麦克风')
  }
}

async function stopVoiceMessage() {
  if (!recording.value) return
  recording.value = false
  const samples = mergeVoiceChunks()
  stopVoiceHardware()
  if (!samples.length) return ElMessage.warning('没有录到声音')
  recognizingVoice.value = true
  setPetMood('thinking')
  try {
    const result = await agentApi.recognize(voiceWavBlob(samples, voiceSampleRate))
    const text = String(result.text || '').trim()
    if (!text) throw new Error(result.message || '没有识别到有效语音')
    question.value = text
    await send(text)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '语音识别失败')
  } finally {
    recognizingVoice.value = false
    if (!sending.value && petMood.value === 'thinking') setPetMood('idle')
  }
}

function toggleVoiceMessage() {
  if (recording.value) void stopVoiceMessage()
  else void startVoiceMessage()
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
  let speechMessage: ChatMessage | null = null
  const appendAssistantMessage = (message: ChatMessage) => {
    targetConversation.messages.push(message)
    speechMessage = message
    void scrollToBottom()
  }
  question.value = ''
  sending.value = true
  setPetMood('thinking')
  try {
    let handledByAgent = false
    try {
      const agent = await agentApi.command(content, 'assistant')
      if (agent.action) {
        setPetMood(moodForAgentFunction(agent.action.functionName || agent.action.type))
        try {
          const result = await executeAgentAction({
            ...agent.action,
            recognizedText: content,
            reply: agent.reply,
            approvalMode: getAgentApprovalMode(),
          }) as { message?: string } | undefined
          const message: ChatMessage = {
            id: Date.now() + 1,
            role: 'assistant',
            content: result?.message || agent.reply || 'Agent 操作已执行。',
            time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
          }
          appendAssistantMessage(message)
          setPetMood('received', 1600)
        } catch (error) {
          const cancelled = error === 'cancel' || error === 'close' || (error instanceof Error && /cancel|取消/i.test(error.message))
          if (!cancelled) throw error
          appendAssistantMessage({ id: Date.now() + 1, role: 'assistant', content: '已取消本次 Agent 操作，没有修改业务数据。', time: '刚刚' })
          setPetMood('idle')
        }
        handledByAgent = true
      } else if (agent.reply && agent.agentMode === 'LLM_CHAT') {
        appendAssistantMessage({ id: Date.now() + 1, role: 'assistant', content: agent.reply, time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) })
        setPetMood('happy', 1700)
        handledByAgent = true
      }
    } catch (error) {
      console.warn('智能问答 Agent 暂时不可用，使用问答接口兜底', error)
    }

    if (!handledByAgent && isDemoMode()) {
      setPetMood('analysis')
      await new Promise((resolve) => window.setTimeout(resolve, 450))
      appendAssistantMessage({ id: Date.now() + 1, role: 'assistant', content: answerFor(content), time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) })
    } else if (!handledByAgent) {
      setPetMood('analysis')
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
      speechMessage = assistantMessage
    }
  } catch (error) {
    setPetMood('alert', 2200)
    const failedMessage = targetConversation.messages.find((item) => item.id === streamingMessageId.value)
    if (failedMessage && !failedMessage.content) failedMessage.content = '抱歉，智能问答暂时失败，请稍后再试。'
    ElMessage.error(error instanceof Error ? error.message : '智能问答请求失败')
  } finally {
    sending.value = false
    streamingMessageId.value = null
    if (petMood.value === 'thinking' || petMood.value === 'analysis') setPetMood('idle')
  }
  await scrollToBottom()
  if (speechMessage?.content) speak(speechMessage.content, speechMessage.id)
}

const criticalAlertCount = computed(() => alerts.value.filter((item) => item.severity === 'CRITICAL' && item.status === 'PENDING').length)

function normalizeSources(sources: ChatMessage['sources'] = []) {
  return sources.map((source) => ({
    documentId: source.documentId || 'unknown',
    title: source.title || source.documentId || '业务参考',
    chunkId: source.chunkId || '-',
    score: typeof source.score === 'number' ? source.score : 0,
  }))
}

async function scrollToBottom(smooth = true) {
  await nextTick()
  chatRef.value?.scrollTo({ top: chatRef.value.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
}

onMounted(async () => {
  window.addEventListener('smart-logistics:agent-approval-mode', syncApprovalMode)
  window.addEventListener('smart-logistics:voice-state', handleGlobalVoiceState)
  window.addEventListener('smart-logistics:voice-action-complete', handleAgentComplete)
  await nextTick()
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
  if (isDemoMode()) return
  try {
    suggestions.value = await assistantApi.suggestions()
  } catch {
    // 推荐问题失败不影响手动提问。
  }
})

onUnmounted(() => {
  window.removeEventListener('smart-logistics:agent-approval-mode', syncApprovalMode)
  window.removeEventListener('smart-logistics:voice-state', handleGlobalVoiceState)
  window.removeEventListener('smart-logistics:voice-action-complete', handleAgentComplete)
  if (petMoodTimer != null) window.clearTimeout(petMoodTimer)
  stopVoiceHardware()
  stopSpeech()
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
    </aside>

    <section class="panel chat-panel">
      <div class="chat-head">
        <div class="assistant-avatar"><el-icon><MagicStick /></el-icon></div>
        <div><h3>物流智能问答 · Agent</h3><span><i></i>LLM 在线 · {{ currentConversation?.title || '新会话' }}</span></div>
        <span class="agent-mode-chip">{{ approvalMode === 'request' ? '请求批准' : '直接执行' }}</span>
        <el-button size="small" round :type="speechEnabled ? 'primary' : 'default'" @click="toggleSpeech">
          {{ speechEnabled ? '播报开启' : '播报关闭' }}
        </el-button>
        <el-button circle icon="MoreFilled" @click="ElMessage.info('会话与消息已自动保存在当前浏览器')" />
      </div>
      <div ref="chatRef" class="chat-messages">
        <div v-for="message in messages" :key="message.id" class="message" :class="message.role">
          <div v-if="message.role === 'assistant'" class="assistant-avatar small"><el-icon><MagicStick /></el-icon></div>
          <div class="bubble">
            <button class="message-delete" type="button" title="删除消息" @click="deleteMessage(message.id)"><el-icon><Close /></el-icon></button>
            <button v-if="message.role === 'assistant'" class="message-speak" type="button" :title="speakingMessageId === message.id ? '停止播报' : '播报这条回复'" @click="speakingMessageId === message.id ? stopSpeech() : speak(message.content, message.id)">
              {{ speakingMessageId === message.id ? '■' : '🔊' }}
            </button>
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
        <el-button
          class="chat-mic-button"
          :class="{ recording }"
          circle
          :type="recording ? 'danger' : 'default'"
          icon="Microphone"
          :loading="recognizingVoice"
          :title="recording ? '结束录音并发送' : '发送语音消息'"
          @click="toggleVoiceMessage"
        />
        <el-button type="primary" circle icon="Promotion" :loading="sending" @click="send()" />
      </div>
      <p class="ai-note">{{ recording ? '正在录音，再次点击麦克风发送' : '支持文字、语音与 Agent 业务操作' }}</p>
    </section>

    <aside class="view-stack assistant-context">
      <article class="panel assistant-pet-card" :class="[`mood-${petMood}`, { speaking: petMood === 'speaking' }]">
        <div class="pet-speech-bubble">
          {{ petCopy }}
        </div>
        <div class="pet-stage">
          <span class="pet-glow"></span>
          <span class="pet-effect pet-effect-question">?</span>
          <span class="pet-effect pet-effect-alert">!</span>
          <span class="pet-effect pet-effect-received">✓</span>
          <span class="pet-effect pet-effect-sparkle">✦</span>
          <span class="pet-effect pet-effect-wave">)))</span>
          <span class="pet-effect pet-effect-panel"><i></i><i></i><i></i></span>
          <img :src="petImage" alt="智慧物流智能小羊桌面宠物" />
          <span class="pet-shadow"></span>
        </div>
        <div class="pet-status"><i></i><span>{{ petStatusText }}</span></div>
      </article>
      <article class="panel context-card"><span class="section-kicker">业务上下文</span><h3>当前运行状态</h3><dl class="info-list"><div><dt>严重告警</dt><dd class="danger-text">{{ criticalAlertCount }} 项</dd></div><div><dt>在途车辆</dt><dd>{{ transitCount }} 辆</dd></div><div><dt>在线设备</dt><dd>{{ onlineCount }} 台</dd></div><div><dt>待处理告警</dt><dd>{{ pendingAlertCount }} 项</dd></div></dl></article>
    </aside>
  </div>
</template>

<style scoped>
.agent-mode-chip {
  flex: 0 0 auto !important;
  display: inline-flex !important;
  align-items: center;
  margin: 0 !important;
  padding: 5px 9px;
  border: 1px solid rgba(28, 190, 155, .28);
  border-radius: 999px;
  color: #0f8f76 !important;
  background: rgba(28, 190, 155, .09);
  font-size: 11px !important;
  font-weight: 800;
}

.message-speak {
  position: absolute;
  right: 28px;
  top: 5px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 7px;
  color: #72859b;
  background: transparent;
  cursor: pointer;
  font-size: 11px;
  opacity: 0;
  transition: opacity .18s ease, background .18s ease;
}

.bubble { position: relative; }
.bubble:hover .message-speak { opacity: 1; }
.message-speak:hover { background: rgba(39, 111, 230, .1); }

.chat-mic-button.recording {
  animation: chat-mic-pulse 1.2s ease-in-out infinite;
}

@keyframes chat-mic-pulse {
  50% { transform: scale(1.08); box-shadow: 0 0 0 8px rgba(245, 108, 108, .12); }
}

.assistant-pet-card {
  position: relative;
  min-height: 300px;
  padding: 14px 12px 10px;
  overflow: hidden;
  border: 1px solid rgba(76, 184, 245, .24);
  background:
    radial-gradient(circle at 50% 70%, rgba(57, 195, 255, .18), transparent 42%),
    linear-gradient(160deg, rgba(245, 252, 255, .98), rgba(231, 246, 255, .94));
}

.assistant-pet-card::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(47, 151, 220, .055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(47, 151, 220, .055) 1px, transparent 1px);
  background-size: 22px 22px;
  mask-image: linear-gradient(to bottom, transparent, black 20%, black 80%, transparent);
  pointer-events: none;
}

.pet-speech-bubble {
  position: relative;
  z-index: 3;
  margin: 0 auto 4px;
  width: fit-content;
  max-width: 95%;
  padding: 8px 11px;
  border: 1px solid rgba(38, 156, 225, .25);
  border-radius: 12px 12px 12px 3px;
  color: #24506f;
  background: rgba(255, 255, 255, .9);
  box-shadow: 0 8px 20px rgba(44, 119, 167, .1);
  font-size: 11px;
  font-weight: 750;
}

.pet-stage {
  position: relative;
  z-index: 2;
  display: grid;
  place-items: end center;
  height: 225px;
}

.pet-stage img {
  position: relative;
  z-index: 2;
  width: 198px;
  max-height: 220px;
  object-fit: contain;
  transform-origin: 50% 92%;
  filter: drop-shadow(0 12px 14px rgba(21, 99, 150, .2));
  animation: pet-idle 3.6s ease-in-out infinite;
  user-select: none;
  pointer-events: none;
}

.pet-glow {
  position: absolute;
  z-index: 0;
  left: 50%;
  bottom: 34px;
  width: 176px;
  height: 176px;
  border: 1px solid rgba(53, 199, 255, .2);
  border-radius: 50%;
  transform: translateX(-50%);
  box-shadow: 0 0 42px rgba(61, 196, 255, .2), inset 0 0 35px rgba(58, 199, 255, .12);
  animation: pet-glow 2.8s ease-in-out infinite;
}

.pet-shadow {
  position: absolute;
  z-index: 1;
  left: 50%;
  bottom: 5px;
  width: 132px;
  height: 17px;
  border-radius: 50%;
  background: rgba(43, 102, 141, .17);
  filter: blur(5px);
  transform: translateX(-50%);
  animation: pet-shadow 3.6s ease-in-out infinite;
}

.pet-status {
  position: relative;
  z-index: 3;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  color: #52758d;
  font-size: 11px;
  font-weight: 750;
}

.pet-status i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #20c99a;
  box-shadow: 0 0 0 4px rgba(32, 201, 154, .12);
}

.mood-listening .pet-stage img { animation: pet-listen 1.05s ease-in-out infinite; }
.mood-thinking .pet-stage img { animation: pet-think 1.8s ease-in-out infinite; }
.mood-speaking .pet-stage img { animation: pet-speak .68s ease-in-out infinite; }
.mood-happy .pet-stage img { animation: pet-happy .72s ease-in-out 2; }
.mood-working .pet-stage img { animation: pet-work 1.25s ease-in-out infinite; }
.mood-alert .pet-stage img { animation: pet-alert .48s ease-in-out 4; filter: drop-shadow(0 12px 14px rgba(222, 62, 88, .25)); }
.mood-received .pet-stage img { animation: pet-received .62s ease-out 2; }
.mood-analysis .pet-stage img { animation: pet-analysis 1.7s ease-in-out infinite; }
.mood-listening .pet-status i { background: #ff6f91; box-shadow: 0 0 0 4px rgba(255, 111, 145, .15); }
.mood-thinking .pet-status i { background: #725cff; box-shadow: 0 0 0 4px rgba(114, 92, 255, .15); }
.mood-speaking .pet-status i { background: #25aeea; box-shadow: 0 0 0 4px rgba(37, 174, 234, .15); }
.mood-happy .pet-status i, .mood-received .pet-status i { background: #16c98d; }
.mood-working .pet-status i, .mood-analysis .pet-status i { background: #6a5cff; }
.mood-alert .pet-status i { background: #ff4f68; box-shadow: 0 0 0 4px rgba(255, 79, 104, .16); }

.pet-effect {
  position: absolute;
  z-index: 4;
  display: none;
  color: #318de8;
  font-weight: 950;
  pointer-events: none;
  text-shadow: 0 3px 12px rgba(27, 128, 226, .25);
}

.pet-effect-question { top: 28px; right: 23px; font-size: 32px; }
.pet-effect-alert { top: 20px; right: 27px; color: #ff4f5f; font-size: 40px; }
.pet-effect-received { top: 28px; right: 20px; color: #16b985; font-size: 31px; }
.pet-effect-sparkle { top: 24px; left: 25px; color: #ffc23d; font-size: 30px; }
.pet-effect-wave { top: 70px; right: 2px; color: #23aee8; font: 900 18px/1 monospace; transform: rotate(-8deg); }
.pet-effect-panel {
  right: 3px;
  bottom: 42px;
  align-items: end;
  gap: 3px;
  width: 55px;
  height: 42px;
  padding: 8px;
  border: 1px solid rgba(42, 170, 238, .45);
  border-radius: 7px;
  background: rgba(218, 247, 255, .7);
  box-shadow: 0 0 16px rgba(42, 170, 238, .18);
}
.pet-effect-panel i { width: 7px; border-radius: 2px 2px 0 0; background: #35a9ee; animation: pet-bars .8s ease-in-out infinite alternate; }
.pet-effect-panel i:nth-child(1) { height: 12px; }
.pet-effect-panel i:nth-child(2) { height: 24px; animation-delay: .18s; }
.pet-effect-panel i:nth-child(3) { height: 18px; animation-delay: .34s; }

.mood-thinking .pet-effect-question { display: block; animation: pet-effect-float 1.5s ease-in-out infinite; }
.mood-alert .pet-effect-alert { display: block; animation: pet-effect-pop .52s ease-in-out infinite alternate; }
.mood-received .pet-effect-received { display: block; animation: pet-effect-pop .7s ease-in-out 2; }
.mood-happy .pet-effect-sparkle { display: block; animation: pet-effect-spin 1.1s ease-in-out infinite; }
.mood-speaking .pet-effect-wave, .mood-listening .pet-effect-wave { display: block; animation: pet-wave .65s ease-in-out infinite alternate; }
.mood-working .pet-effect-panel, .mood-analysis .pet-effect-panel { display: flex; }

@keyframes pet-idle {
  0%, 100% { transform: translateY(0) rotate(-1deg); }
  48% { transform: translateY(-7px) rotate(1.2deg); }
  55% { transform: translateY(-7px) rotate(.2deg) scaleY(.985); }
}

@keyframes pet-listen {
  0%, 100% { transform: translateY(0) rotate(-2deg); }
  50% { transform: translateY(-5px) rotate(2deg) scale(1.015); }
}

@keyframes pet-think {
  0%, 100% { transform: translateX(0) translateY(0) rotate(0); }
  30% { transform: translateX(-4px) translateY(-5px) rotate(-2deg); }
  65% { transform: translateX(4px) translateY(-3px) rotate(2deg); }
}

@keyframes pet-speak {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-4px) scale(1.018, .985); }
}

@keyframes pet-happy {
  0%, 100% { transform: translateY(0) rotate(0) scale(1); }
  45% { transform: translateY(-13px) rotate(-3deg) scale(1.025); }
  70% { transform: translateY(-4px) rotate(3deg); }
}

@keyframes pet-work {
  0%, 100% { transform: translateX(0) rotate(-1deg); }
  50% { transform: translateX(4px) translateY(-4px) rotate(1.5deg); }
}

@keyframes pet-alert {
  0%, 100% { transform: translateX(0) rotate(0); }
  30% { transform: translateX(-4px) rotate(-2deg); }
  70% { transform: translateX(4px) rotate(2deg); }
}

@keyframes pet-received {
  0%, 100% { transform: translateY(0) scale(1); }
  55% { transform: translateY(-8px) scale(1.03); }
}

@keyframes pet-analysis {
  0%, 100% { transform: translateY(0) rotate(-1deg); }
  50% { transform: translateY(-5px) rotate(1deg) scale(.99, 1.015); }
}

@keyframes pet-effect-float { 50% { transform: translateY(-8px) rotate(8deg); opacity: .62; } }
@keyframes pet-effect-pop { 50% { transform: scale(1.18) rotate(4deg); } }
@keyframes pet-effect-spin { 50% { transform: scale(1.2) rotate(25deg); filter: brightness(1.15); } }
@keyframes pet-wave { to { transform: translateX(5px) rotate(-8deg) scaleX(1.15); opacity: .55; } }
@keyframes pet-bars { to { height: 29px; } }

@keyframes pet-glow {
  50% { opacity: .64; transform: translateX(-50%) scale(1.07); }
}

@keyframes pet-shadow {
  50% { opacity: .7; transform: translateX(-50%) scale(.88); }
}

@media (max-width: 1180px) {
  .assistant-pet-card { min-height: 245px; }
  .pet-stage { height: 178px; }
  .pet-stage img { width: 155px; max-height: 176px; }
}

@media (prefers-reduced-motion: reduce) {
  .pet-stage img, .pet-glow, .pet-shadow, .chat-mic-button.recording { animation: none !important; }
}
</style>
