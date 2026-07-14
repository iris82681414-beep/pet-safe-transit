<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { agentApi, assistantApi } from '@/services/api'

type VoiceStatus = 'idle' | 'listening' | 'recognizing' | 'thinking' | 'speaking' | 'error'
type ChatMessage = { id: number; role: 'user' | 'assistant'; content: string }

const props = withDefaults(defineProps<{ cargoId?: string; petName?: string }>(), {
  cargoId: '',
  petName: '我的宠物',
})

const visible = ref(false)
const status = ref<VoiceStatus>('idle')
const input = ref('')
const interimText = ref('')
const chatRef = ref<HTMLElement | null>(null)
const audio = ref<HTMLAudioElement | null>(null)
const audioUrl = ref('')
const sessionId = ref<string>()
const streamingMessageId = ref<number | null>(null)
const messages = ref<ChatMessage[]>([
  { id: 1, role: 'assistant', content: `你好呀，我是智慧小羊。你可以直接问我${props.petName}的位置、预计到达时间、舱内环境或最近照护记录。` },
])
let voiceStream: MediaStream | null = null
let voiceContext: AudioContext | null = null
let voiceSource: MediaStreamAudioSourceNode | null = null
let voiceProcessor: ScriptProcessorNode | null = null
let voiceSampleRate = 16000
let voiceLength = 0
let voiceChunks: Float32Array[] = []
let speechQueue: Promise<void> = Promise.resolve()
let speechBuffer = ''
let speechQueueId = 0
let playbackUnlocked = false

const suggestions = computed(() => [`${props.petName}现在到哪里了？`, '车厢温度安全吗？', '还有多久到达？', '今天有照护记录吗？'])
const statusText = computed(() => ({
  idle: '点击麦克风继续说',
  listening: '正在录音，再次点击即可发送…',
  recognizing: '正在识别语音…',
  thinking: `正在查询${props.petName}的旅程数据…`,
  speaking: '智慧小羊正在回答',
  error: '没有听清，可以再试一次',
}[status.value]))

async function scrollToLatest() {
  await nextTick()
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
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

function stopPlayback() {
  speechQueueId += 1
  speechBuffer = ''
  speechQueue = Promise.resolve()
  audio.value?.pause()
  audio.value = null
  if (audioUrl.value) URL.revokeObjectURL(audioUrl.value)
  audioUrl.value = ''
  window.speechSynthesis?.cancel()
}

function unlockPlayback() {
  if (playbackUnlocked) return
  // Run inside the click/submit gesture so later streamed MP3 playback is not
  // rejected when the first network chunk arrives asynchronously.
  const silent = new Audio('data:audio/wav;base64,UklGRiwAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQgAAACAgICAgICA')
  silent.volume = 0
  void silent.play().then(() => { playbackUnlocked = true }).catch(() => undefined)
}

function playAudioBlob(blob: Blob, queueId: number) {
  return new Promise<boolean>((resolve) => {
    if (queueId !== speechQueueId) return resolve(false)
    if (audioUrl.value) URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = URL.createObjectURL(blob)
    const player = new Audio(audioUrl.value)
    audio.value = player
    player.onended = () => resolve(true)
    player.onerror = () => resolve(false)
    player.play().catch(() => resolve(false))
  })
}

function appendSourceBuffer(sourceBuffer: SourceBuffer, chunk: Uint8Array) {
  return new Promise<void>((resolve, reject) => {
    const onComplete = () => { cleanup(); resolve() }
    const onError = () => { cleanup(); reject(new Error('追加流式音频失败')) }
    const cleanup = () => {
      sourceBuffer.removeEventListener('updateend', onComplete)
      sourceBuffer.removeEventListener('error', onError)
    }
    sourceBuffer.addEventListener('updateend', onComplete, { once: true })
    sourceBuffer.addEventListener('error', onError, { once: true })
    sourceBuffer.appendBuffer(chunk as BufferSource)
  })
}

async function playStreamingResponse(response: Response, queueId: number) {
  const reader = response.body!.getReader()
  if (!('MediaSource' in window) || !MediaSource.isTypeSupported('audio/mpeg')) {
    const chunks: BlobPart[] = []
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      chunks.push(value.slice().buffer)
    }
    return playAudioBlob(new Blob(chunks, { type: 'audio/mpeg' }), queueId)
  }

  const mediaSource = new MediaSource()
  if (audioUrl.value) URL.revokeObjectURL(audioUrl.value)
  audioUrl.value = URL.createObjectURL(mediaSource)
  const player = new Audio(audioUrl.value)
  audio.value = player
  const sourceOpen = new Promise<SourceBuffer>((resolve, reject) => {
    mediaSource.addEventListener('sourceopen', () => {
      try { resolve(mediaSource.addSourceBuffer('audio/mpeg')) }
      catch (error) { reject(error) }
    }, { once: true })
  })
  const sourceBuffer = await sourceOpen
  let playStarted: Promise<boolean> | null = null
  while (true) {
    if (queueId !== speechQueueId) {
      await reader.cancel()
      return false
    }
    const { done, value } = await reader.read()
    if (done) break
    await appendSourceBuffer(sourceBuffer, value)
    // Calling play() before the first MP3 bytes are appended is rejected by
    // Chromium on some systems and turns streaming playback into a full download.
    if (!playStarted) playStarted = player.play().then(() => true).catch(() => false)
  }
  if (mediaSource.readyState === 'open') mediaSource.endOfStream()
  if (!playStarted || !await playStarted) return false
  return new Promise<boolean>((resolve) => {
    player.onended = () => resolve(true)
    player.onerror = () => resolve(false)
  })
}

function enqueueSpeech(text: string, queueId: number) {
  const speechText = plainText(text)
  if (!speechText) return
  const audioRequest = assistantApi.speechStream(speechText)
    .then((response) => ({ response, error: null as unknown }))
    .catch((error: unknown) => ({ response: null, error }))
  speechQueue = speechQueue.then(async () => {
    if (queueId !== speechQueueId) return
    status.value = 'speaking'
    const result = await audioRequest
    if (result.response) {
      const played = await playStreamingResponse(result.response, queueId)
      if (!played && queueId === speechQueueId) browserSpeak(speechText)
    }
    else if (queueId === speechQueueId) browserSpeak(speechText)
  })
}

function feedSpeechToken(token: string, queueId: number, flush = false) {
  speechBuffer += token
  const boundary = /[。！？!?；;\n]/g
  let match: RegExpExecArray | null
  let consumed = 0
  while ((match = boundary.exec(speechBuffer)) !== null) {
    const end = match.index + match[0].length
    enqueueSpeech(speechBuffer.slice(consumed, end), queueId)
    consumed = end
  }
  speechBuffer = speechBuffer.slice(consumed)
  // Do not wait for the model to finish a long sentence before speech starts.
  // Keep markdown constructs intact and synthesize a short readable phrase.
  if (!flush && plainText(speechBuffer).length >= 18) {
    const splitAt = Math.max(
      speechBuffer.lastIndexOf('，'),
      speechBuffer.lastIndexOf('、'),
      speechBuffer.lastIndexOf('：'),
      speechBuffer.lastIndexOf(' '),
    )
    const end = splitAt >= 8 ? splitAt + 1 : speechBuffer.length
    enqueueSpeech(speechBuffer.slice(0, end), queueId)
    speechBuffer = speechBuffer.slice(end)
  }
  if (flush && speechBuffer.trim()) {
    enqueueSpeech(speechBuffer, queueId)
    speechBuffer = ''
  }
}

function browserSpeak(text: string) {
  if (!('speechSynthesis' in window)) return
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'zh-CN'
  utterance.rate = .96
  utterance.pitch = 1.22
  const voices = window.speechSynthesis.getVoices()
  utterance.voice = voices.find((voice) => /Xiaoxiao|Tingting|Meijia|Female|女/i.test(voice.name) && /^zh/i.test(voice.lang))
    || voices.find((voice) => /^zh/i.test(voice.lang))
    || null
  utterance.onend = () => { if (status.value === 'speaking') status.value = 'idle' }
  utterance.onerror = () => { if (status.value === 'speaking') status.value = 'idle' }
  window.speechSynthesis.speak(utterance)
}

async function speak(text: string) {
  stopPlayback()
  const speechText = plainText(text)
  if (!speechText) {
    status.value = 'idle'
    return
  }
  status.value = 'speaking'
  try {
    const blob = await assistantApi.speech(speechText)
    audioUrl.value = URL.createObjectURL(blob)
    audio.value = new Audio(audioUrl.value)
    audio.value.onended = () => { status.value = 'idle'; stopPlayback() }
    audio.value.onerror = () => { browserSpeak(speechText) }
    await audio.value.play()
  } catch {
    browserSpeak(speechText)
  }
}

async function streamFallback(messageId: number, content: string) {
  const chunks = content.match(/.{1,8}/gu) || [content]
  for (const chunk of chunks) {
    const message = messages.value.find((item) => item.id === messageId)
    if (!message) return
    message.content += chunk
    await scrollToLatest()
    await new Promise((resolve) => window.setTimeout(resolve, 35))
  }
}

async function ask(text = input.value) {
  const normalized = text.trim()
  if (!normalized || status.value === 'thinking') return
  unlockPlayback()
  stopVoiceHardware()
  input.value = ''
  interimText.value = ''
  messages.value.push({ id: Date.now(), role: 'user', content: normalized })
  status.value = 'thinking'
  stopPlayback()
  const currentSpeechQueueId = speechQueueId
  await scrollToLatest()

  const assistantMessage: ChatMessage = { id: Date.now() + 1, role: 'assistant', content: '' }
  messages.value.push(assistantMessage)
  streamingMessageId.value = assistantMessage.id
  const streamedMessage = () => messages.value.find((item) => item.id === assistantMessage.id)
  let streamFailed = false
  try {
    await assistantApi.chatStream(
      { question: normalized, sessionId: sessionId.value, cargoId: props.cargoId || undefined },
      {
        onToken: (token) => {
          const message = streamedMessage()
          if (message) message.content += token
          feedSpeechToken(token, currentSpeechQueueId)
          void scrollToLatest()
        },
        onMeta: (meta) => {
          sessionId.value = meta.sessionId || sessionId.value
        },
      },
    )
    if (!streamedMessage()?.content.trim()) throw new Error('流式回答内容为空')
  } catch (error) {
    streamFailed = true
    console.warn('货主流式问答暂不可用', error)
  }
  if (streamFailed) {
    const message = streamedMessage()
    if (message) message.content = ''
    await streamFallback(assistantMessage.id, '智慧小羊暂时无法连接服务，请稍后重试。')
  } else {
    feedSpeechToken('', currentSpeechQueueId, true)
  }

  streamingMessageId.value = null
  await scrollToLatest()
  await speechQueue
  if (currentSpeechQueueId === speechQueueId) status.value = 'idle'
}

function stopVoiceHardware() {
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

function microphoneError(error: unknown) {
  if (!(error instanceof Error)) return '无法访问麦克风'
  if (error.name === 'NotAllowedError' || error.name === 'SecurityError') return '请在浏览器地址栏允许麦克风权限'
  if (error.name === 'NotFoundError') return '没有检测到可用麦克风'
  if (error.name === 'NotReadableError') return '麦克风正被其他程序占用'
  return error.message || '无法访问麦克风'
}

async function finishListening() {
  if (status.value !== 'listening') return
  const samples = mergeVoiceChunks()
  stopVoiceHardware()
  if (!samples.length) {
    status.value = 'error'
    interimText.value = '没有录到声音，请检查麦克风后重试'
    return
  }

  status.value = 'recognizing'
  interimText.value = ''
  try {
    const result = await agentApi.recognize(voiceWavBlob(samples, voiceSampleRate))
    const text = result.text?.trim()
    if (!text) throw new Error('没有识别到有效语音')
    interimText.value = text
    await ask(text)
  } catch (error) {
    status.value = 'error'
    interimText.value = error instanceof Error ? error.message : '语音识别失败，请稍后重试'
    ElMessage.error(interimText.value)
  }
}

async function startListening() {
  if (status.value === 'thinking' || status.value === 'recognizing') return
  if (status.value === 'listening') {
    await finishListening()
    return
  }
  stopPlayback()
  if (!navigator.mediaDevices?.getUserMedia) {
    status.value = 'error'
    interimText.value = '当前浏览器不支持录音，请使用新版 Chrome 或 Edge'
    return
  }
  try {
    stopVoiceHardware()
    voiceChunks = []
    voiceLength = 0
    interimText.value = ''
    voiceStream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    })
    const AudioContextCtor = window.AudioContext || (window as any).webkitAudioContext
    if (!AudioContextCtor) throw new Error('当前浏览器不支持 AudioContext')
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
    status.value = 'listening'
  } catch (error) {
    stopVoiceHardware()
    status.value = 'error'
    interimText.value = microphoneError(error)
    ElMessage.error(interimText.value)
  }
}

async function openAndListen() {
  visible.value = true
  await nextTick()
  startListening()
}

function close() {
  visible.value = false
  stopVoiceHardware()
  if (status.value === 'listening') status.value = 'idle'
  stopPlayback()
  streamingMessageId.value = null
}

defineExpose({ openAndListen })
onBeforeUnmount(() => { stopVoiceHardware(); stopPlayback() })
</script>

<template>
  <Teleport to="body">
    <transition name="voice-dialog">
      <div v-if="visible" class="owner-voice-overlay" @click.self="close">
        <section class="owner-voice-dialog" role="dialog" aria-modal="true" aria-label="智慧小羊语音对话">
          <header>
            <div class="dialog-assistant">
              <span class="assistant-avatar"><img src="@/assets/iot-sheep-pet.png" alt="智慧小羊" /></span>
              <span><strong>智慧小羊</strong><small>{{ props.petName }}的专属运输助手 · 只读权限</small></span>
            </div>
            <button type="button" title="关闭对话" @click="close"><el-icon><Close /></el-icon></button>
          </header>

          <div ref="chatRef" class="owner-chat-stream">
            <article v-for="message in messages" :key="message.id" :class="message.role">
              <span v-if="message.role === 'assistant'" class="mini-avatar">羊</span>
              <p v-if="message.role === 'user'">{{ message.content }}</p>
              <div v-else class="message-markdown" :class="{ streaming: streamingMessageId === message.id }" v-html="renderMarkdown(message.content)"></div>
            </article>
          </div>

          <div v-if="messages.length <= 2" class="voice-suggestions">
            <button v-for="item in suggestions" :key="item" type="button" @click="ask(item)">{{ item }}</button>
          </div>

          <div v-if="status === 'listening' || status === 'recognizing' || status === 'speaking'" class="voice-live-state" :class="status">
            <span class="live-dot"></span>
            <strong>{{ interimText || statusText }}</strong>
            <span v-if="status === 'listening' || status === 'recognizing'" class="voice-bars"><i></i><i></i><i></i><i></i><i></i></span>
          </div>

          <form class="owner-voice-composer" @submit.prevent="ask()">
            <button class="mic-button" :class="{ active: status === 'listening' }" type="button" :title="status === 'listening' ? '结束录音并发送' : '开始语音对话'" :disabled="status === 'thinking' || status === 'recognizing'" @click="startListening">
              <el-icon><Microphone /></el-icon>
            </button>
            <input v-model="input" type="text" :placeholder="`也可以输入关于${props.petName}运输的问题`" :disabled="status === 'thinking' || status === 'recognizing'" />
            <button class="send-button" type="submit" title="发送" :disabled="!input.trim() || status === 'thinking' || status === 'recognizing'"><el-icon><Promotion /></el-icon></button>
          </form>
          <footer><el-icon><Lock /></el-icon> 仅可查询当前账户名下宠物，不执行调度、改道或告警处置</footer>
        </section>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.owner-voice-overlay { position: fixed; inset: 0; z-index: 12000; display: grid; place-items: center; padding: 20px; background: rgba(15, 35, 70, .32); backdrop-filter: blur(12px); }
.owner-voice-dialog { width: min(650px, calc(100vw - 32px)); max-height: min(760px, calc(100vh - 32px)); display: flex; flex-direction: column; overflow: hidden; border: 1px solid rgba(167, 207, 246, .95); border-radius: 20px; color: #183965; background: linear-gradient(160deg, rgba(255,255,255,.98), rgba(237,247,255,.98)); box-shadow: 0 30px 90px rgba(22, 64, 116, .28), inset 0 1px 0 #fff; }
.owner-voice-dialog > header { height: 78px; flex: 0 0 78px; display: flex; align-items: center; justify-content: space-between; padding: 0 22px; border-bottom: 1px solid #d9e8f7; }
.dialog-assistant { display: flex; align-items: center; gap: 12px; }.assistant-avatar { width: 52px; height: 52px; overflow: hidden; border: 1px solid #b9d9f6; border-radius: 15px; background: radial-gradient(circle,#fff,#dff2ff); }.assistant-avatar img { width: 100%; height: 100%; object-fit: contain; }.dialog-assistant > span:last-child { display: flex; flex-direction: column; }.dialog-assistant strong { color: #15396d; font-size: 17px; }.dialog-assistant small { margin-top: 4px; color: #7287a4; font-size: 11px; }.owner-voice-dialog > header > button { width: 36px; height: 36px; display: grid; place-items: center; border: 1px solid #cee1f4; border-radius: 50%; color: #5f7898; background: #f7fbff; }
.owner-chat-stream { min-height: 260px; flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 14px; padding: 22px; }.owner-chat-stream article { display: flex; align-items: flex-end; gap: 8px; }.owner-chat-stream p { max-width: 78%; margin: 0; padding: 12px 15px; border-radius: 14px 14px 14px 4px; color: #29476e; background: #fff; box-shadow: 0 5px 16px rgba(51,93,140,.08); font-size: 13px; line-height: 1.7; }.owner-chat-stream .user { justify-content: flex-end; }.owner-chat-stream .user p { border-radius: 14px 14px 4px 14px; color: #fff; background: linear-gradient(135deg,#46aef8,#3376ee); }.mini-avatar { width: 26px; height: 26px; flex: 0 0 26px; display: grid; place-items: center; border-radius: 9px; color: #287bd8; background: #dff2ff; font-size: 10px; font-weight: 800; }
.message-markdown { max-width: 78%; margin: 0; padding: 12px 15px; border-radius: 14px 14px 14px 4px; color: #29476e; background: #fff; box-shadow: 0 5px 16px rgba(51,93,140,.08); font-size: 13px; line-height: 1.7; }
.message-markdown.streaming::after { content: ''; display: inline-block; width: 2px; height: 1em; margin-left: 3px; vertical-align: -2px; background: #2b8df2; animation: stream-caret .75s steps(1) infinite; }
.message-markdown.streaming:empty::before { content: '正在连接智能问答'; color: #7890ac; }
.message-markdown :deep(p) { max-width: none; margin: 0 0 8px; padding: 0; border-radius: 0; background: transparent; box-shadow: none; }.message-markdown :deep(p:last-child) { margin-bottom: 0; }.message-markdown :deep(ul),.message-markdown :deep(ol) { margin: 7px 0; padding-left: 20px; }.message-markdown :deep(li) { margin: 3px 0; }.message-markdown :deep(h1),.message-markdown :deep(h2),.message-markdown :deep(h3) { margin: 8px 0 5px; color: #173f70; font-size: 14px; }.message-markdown :deep(code) { padding: 1px 4px; border-radius: 4px; color: #145eaa; background: #edf6ff; }.message-markdown :deep(strong) { color: #173f70; }
.voice-suggestions { display: flex; flex-wrap: wrap; gap: 7px; padding: 0 22px 14px; }.voice-suggestions button { padding: 7px 10px; border: 1px solid #cae0f7; border-radius: 14px; color: #47709e; background: rgba(255,255,255,.72); font-size: 11px; }.voice-suggestions button:hover { color: #176edc; border-color: #79b7ef; }
.voice-live-state { min-height: 42px; display: flex; align-items: center; justify-content: center; gap: 9px; margin: 0 22px 13px; border: 1px solid #d4e5f6; border-radius: 12px; color: #68809f; background: rgba(255,255,255,.62); }.voice-live-state strong { max-width: 70%; overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.live-dot { width: 7px; height: 7px; border-radius: 50%; background: #87a1be; }.voice-live-state.listening { color: #176dd8; border-color: #9bc9f5; background: #edf7ff; }.voice-live-state.listening .live-dot { background: #2b8df2; box-shadow: 0 0 0 5px rgba(43,141,242,.12); }.voice-live-state.speaking .live-dot { background: #22b76b; }.voice-bars { display: flex; align-items: center; gap: 2px; height: 18px; }.voice-bars i { width: 2px; height: 7px; border-radius: 2px; background: #2f8cf0; animation: voice-bar .7s ease-in-out infinite alternate; }.voice-bars i:nth-child(2),.voice-bars i:nth-child(4){animation-delay:.2s}.voice-bars i:nth-child(3){animation-delay:.35s}
.owner-voice-composer { height: 62px; flex: 0 0 62px; display: grid; grid-template-columns: 42px 1fr 42px; align-items: center; gap: 8px; margin: 0 22px; padding: 7px; border: 1px solid #bad7f2; border-radius: 16px; background: #fff; box-shadow: 0 8px 24px rgba(44,96,153,.09); }.owner-voice-composer input { min-width: 0; border: 0; outline: 0; color: #244467; background: transparent; font-size: 13px; }.mic-button,.send-button { width: 42px; height: 42px; display: grid; place-items: center; border-radius: 12px; font-size: 18px; }.mic-button { color: #2276dd; background: #eaf5ff; }.mic-button.active { color: #fff; background: #ef5c67; animation: mic-pulse 1.1s ease-in-out infinite; }.send-button { color: #fff; background: linear-gradient(135deg,#51b5ff,#3174ed); }.send-button:disabled { opacity: .42; }
.owner-voice-dialog > footer { padding: 13px 22px 17px; color: #8798ad; font-size: 10px; text-align: center; }.owner-voice-dialog > footer .el-icon { margin-right: 3px; vertical-align: -2px; }
.voice-dialog-enter-active,.voice-dialog-leave-active { transition: opacity .18s ease; }.voice-dialog-enter-active .owner-voice-dialog,.voice-dialog-leave-active .owner-voice-dialog { transition: transform .22s ease, opacity .18s ease; }.voice-dialog-enter-from,.voice-dialog-leave-to { opacity: 0; }.voice-dialog-enter-from .owner-voice-dialog,.voice-dialog-leave-to .owner-voice-dialog { opacity: 0; transform: translateY(12px) scale(.98); }
@keyframes voice-bar { to { height: 17px; } } @keyframes mic-pulse { 50% { box-shadow: 0 0 0 6px rgba(239,92,103,.12); } }
@keyframes stream-caret { 50% { opacity: 0; } }
@media (max-width: 620px) { .owner-voice-overlay { padding: 8px; align-items: end; }.owner-voice-dialog { width: 100%; max-height: 88vh; border-radius: 18px 18px 0 0; }.owner-chat-stream { padding: 16px; }.owner-chat-stream p { max-width: 86%; }.owner-voice-composer,.voice-live-state { margin-left: 14px; margin-right: 14px; }.voice-suggestions { padding-left: 14px; padding-right: 14px; } }
@media (prefers-reduced-motion: reduce) { .voice-bars i,.mic-button.active { animation: none; } }
</style>
