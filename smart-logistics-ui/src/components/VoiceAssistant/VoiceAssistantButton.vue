<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { getAgentApprovalMode, setAgentApprovalMode, type AgentApprovalMode } from '@/utils/agentApproval'
import { runVoiceAgent } from '@/services/voiceAgent'

type VoiceStatus = 'idle' | 'requesting' | 'recording' | 'uploading' | 'executing'

const props = defineProps<{ context?: Record<string, any> }>()

const status = ref<VoiceStatus>('idle')
const buttonRef = ref<HTMLButtonElement | null>(null)
const position = ref<{ x: number; y: number } | null>(null)
const approvalMode = ref<AgentApprovalMode>(getAgentApprovalMode())
const dragging = ref(false)
const elapsed = ref(0)
const lastText = ref('')
const detailMessage = ref('')
const chunks: Float32Array[] = []
let totalLength = 0
let inputSampleRate = 16000
let stream: MediaStream | null = null
let audioContext: AudioContext | null = null
let sourceNode: MediaStreamAudioSourceNode | null = null
let processorNode: ScriptProcessorNode | null = null
let timer: number | null = null
let dragPointerId: number | null = null
let dragStartX = 0
let dragStartY = 0
let dragOriginX = 0
let dragOriginY = 0
let dragMoved = false
let suppressNextClick = false

const positionStorageKey = 'smart-logistics-voice-assistant-position'
const dragStyle = computed(() => position.value
  ? { left: `${position.value.x}px`, top: `${position.value.y}px`, right: 'auto', bottom: 'auto' }
  : undefined)
const permissionStyle = computed(() => position.value
  ? { left: `${position.value.x}px`, top: `${Math.max(8, position.value.y - 42)}px`, right: 'auto', bottom: 'auto' }
  : undefined)

const recording = computed(() => status.value === 'recording')
const busy = computed(() => status.value === 'requesting' || status.value === 'uploading' || status.value === 'executing')
const portalMode = computed(() => String(props.context?.sourcePage || '') === 'portal')
const capturing = computed(() => ['requesting', 'recording', 'uploading'].includes(status.value))
const elapsedText = computed(() => `${String(Math.floor(elapsed.value / 60)).padStart(2, '0')}:${String(elapsed.value % 60).padStart(2, '0')}`)
const title = computed(() => {
  if (status.value === 'requesting') return '正在请求麦克风权限'
  if (status.value === 'recording') return '正在录音，点击结束'
  if (status.value === 'uploading') return '正在识别语音'
  if (status.value === 'executing') return '正在执行语音操作'
  return '点击开始语音指令'
})
const statusLabel = computed(() => {
  if (status.value === 'requesting') return 'PERMIT'
  if (status.value === 'recording') return elapsedText.value
  if (status.value === 'uploading') return 'LISTEN'
  if (status.value === 'executing') return 'ACTION'
  return 'READY'
})

function describeMicError(error: unknown) {
  if (!(error instanceof Error)) return '无法访问麦克风'
  if (error.name === 'NotAllowedError' || error.name === 'SecurityError') {
    return '麦克风权限被浏览器拒绝，请点击地址栏左侧图标允许麦克风'
  }
  if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
    return '没有检测到可用麦克风，请检查设备连接'
  }
  if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
    return '麦克风被其他程序占用，请关闭占用录音的软件后重试'
  }
  return error.message || '无法访问麦克风'
}

function canRequestMicrophone() {
  const host = window.location.hostname
  const isLocalHost = host === 'localhost' || host === '127.0.0.1' || host === '[::1]'
  if (!window.isSecureContext && !isLocalHost) {
    detailMessage.value = '浏览器只允许 HTTPS 或 localhost 页面调用麦克风'
    ElMessage.error(detailMessage.value)
    return false
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    detailMessage.value = '当前浏览器不支持网页录音，请使用新版 Chrome 或 Edge'
    ElMessage.error(detailMessage.value)
    return false
  }
  return true
}

function showInfo(message: string) {
  if (portalMode.value) return
  ElMessage.info(message)
}

function friendlyVoiceError(error: unknown) {
  const raw = error instanceof Error ? error.message : String(error || '')
  if (/502|network error|failed to fetch|econnrefused/i.test(raw)) {
    return '语音服务暂时未连接，请确认伴生云途后端已启动后重试'
  }
  return raw || '语音指令执行失败'
}

watch(capturing, (active) => {
  document.body.classList.toggle('voice-capturing', active && portalMode.value)
}, { immediate: true })

watch(status, (value) => {
  window.dispatchEvent(new CustomEvent('smart-logistics:voice-state', { detail: { status: value } }))
})

function clearTimers() {
  if (timer != null) window.clearInterval(timer)
  timer = null
}

function stopStream() {
  processorNode?.disconnect()
  sourceNode?.disconnect()
  processorNode = null
  sourceNode = null
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
  void audioContext?.close()
  audioContext = null
}

function resetBuffer() {
  chunks.length = 0
  totalLength = 0
  elapsed.value = 0
}

async function startRecording() {
  if (status.value !== 'idle') return
  if (!canRequestMicrophone()) return

  try {
    resetBuffer()
    detailMessage.value = '正在请求麦克风权限'
    status.value = 'requesting'
    stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    })
    const AudioContextCtor = window.AudioContext || (window as any).webkitAudioContext
    if (!AudioContextCtor) throw new Error('当前浏览器不支持 AudioContext')
    // 使用声卡原生采样率，上传前再统一转换为 16k PCM，避免浏览器实时重采样失真。
    audioContext = new AudioContextCtor()
    if (audioContext.state === 'suspended') await audioContext.resume()
    inputSampleRate = audioContext.sampleRate
    sourceNode = audioContext.createMediaStreamSource(stream)
    processorNode = audioContext.createScriptProcessor(4096, 1, 1)
    processorNode.onaudioprocess = (event) => {
      const input = event.inputBuffer.getChannelData(0)
      chunks.push(new Float32Array(input))
      totalLength += input.length
      event.outputBuffer.getChannelData(0).fill(0)
    }
    sourceNode.connect(processorNode)
    processorNode.connect(audioContext.destination)
    status.value = 'recording'
    detailMessage.value = '录音中，再次点击结束'
    showInfo('开始录音，再次点击麦克风结束')
    timer = window.setInterval(() => { elapsed.value += 1 }, 1000)
  } catch (error) {
    clearTimers()
    stopStream()
    resetBuffer()
    status.value = 'idle'
    detailMessage.value = describeMicError(error)
    ElMessage.error(detailMessage.value)
  }
}

async function stopAndSend() {
  if (status.value !== 'recording') return
  clearTimers()
  stopStream()
  if (!totalLength) {
    resetBuffer()
    status.value = 'idle'
    detailMessage.value = '没有录到声音，请靠近麦克风后重试'
    ElMessage.warning(detailMessage.value)
    return
  }

  const audioBlob = encodeWavBlob(mergeChunks(), inputSampleRate, 16000)
  resetBuffer()
  status.value = 'uploading'
  detailMessage.value = '正在识别语音'
  showInfo('正在识别语音')

  try {
    const payload = await runVoiceAgent(audioBlob, props.context || {}, {
      onExecuting: () => { status.value = 'executing' },
    })
    const { recognizedText, action, reply } = payload
    lastText.value = recognizedText

    if (recognizedText || reply) {
      ElNotification({
        title: recognizedText ? `识别：${recognizedText}` : '语音指令',
        message: reply || '已收到语音指令',
        type: action ? 'success' : 'info',
        duration: 3600,
      })
    }

    if (action) {
      detailMessage.value = reply || 'Agent 操作已执行'
    } else if (reply) {
      ElMessage.info(reply)
    } else {
      ElMessage.warning('没有识别到可执行操作')
    }
    window.dispatchEvent(new CustomEvent('smart-logistics:voice-result', {
      detail: payload,
    }))
  } catch (error) {
    detailMessage.value = friendlyVoiceError(error)
    ElMessage.error(detailMessage.value)
  } finally {
    status.value = 'idle'
  }
}

function mergeChunks() {
  const merged = new Float32Array(totalLength)
  let offset = 0
  chunks.forEach((chunk) => {
    merged.set(chunk, offset)
    offset += chunk.length
  })
  return merged
}

function resample(input: Float32Array, fromRate: number, toRate: number) {
  if (fromRate === toRate) return input
  const ratio = fromRate / toRate
  const length = Math.round(input.length / ratio)
  const output = new Float32Array(length)
  for (let i = 0; i < length; i += 1) {
    const start = Math.floor(i * ratio)
    const end = Math.min(Math.floor((i + 1) * ratio), input.length)
    let sum = 0
    for (let j = start; j < end; j += 1) sum += input[j]
    output[i] = sum / Math.max(1, end - start)
  }
  return output
}

function encodeWavBlob(samples: Float32Array, fromRate: number, toRate: number) {
  const pcm = prepareVoiceSamples(resample(samples, fromRate, toRate), toRate)
  const buffer = new ArrayBuffer(44 + pcm.length * 2)
  const view = new DataView(buffer)
  writeAscii(view, 0, 'RIFF')
  view.setUint32(4, 36 + pcm.length * 2, true)
  writeAscii(view, 8, 'WAVE')
  writeAscii(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, toRate, true)
  view.setUint32(28, toRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeAscii(view, 36, 'data')
  view.setUint32(40, pcm.length * 2, true)
  let offset = 44
  for (let i = 0; i < pcm.length; i += 1, offset += 2) {
    const sample = Math.max(-1, Math.min(1, pcm[i]))
    view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
  }
  return new Blob([view], { type: 'audio/wav' })
}

function prepareVoiceSamples(input: Float32Array, sampleRate: number) {
  if (!input.length) return input

  let mean = 0
  for (let i = 0; i < input.length; i += 1) mean += input[i]
  mean /= input.length
  const centered = new Float32Array(input.length)
  for (let i = 0; i < input.length; i += 1) centered[i] = input[i] - mean

  const frameSize = Math.max(1, Math.round(sampleRate * 0.02))
  const frameRms: number[] = []
  for (let offset = 0; offset < centered.length; offset += frameSize) {
    const end = Math.min(centered.length, offset + frameSize)
    let energy = 0
    for (let i = offset; i < end; i += 1) energy += centered[i] * centered[i]
    frameRms.push(Math.sqrt(energy / Math.max(1, end - offset)))
  }

  const sortedRms = [...frameRms].sort((a, b) => a - b)
  const noiseFloor = sortedRms[Math.floor(sortedRms.length * 0.2)] || 0
  const maxRms = Math.max(...frameRms, 0)
  const threshold = Math.max(0.004, Math.min(0.03, noiseFloor * 2.8), maxRms * 0.08)
  let firstFrame = frameRms.findIndex((value) => value >= threshold)
  let lastFrame = -1
  for (let i = frameRms.length - 1; i >= 0; i -= 1) {
    if (frameRms[i] >= threshold) {
      lastFrame = i
      break
    }
  }
  if (firstFrame < 0 || lastFrame < firstFrame) {
    firstFrame = 0
    lastFrame = frameRms.length - 1
  }

  const padding = Math.round(sampleRate * 0.18)
  const start = Math.max(0, firstFrame * frameSize - padding)
  const end = Math.min(centered.length, (lastFrame + 1) * frameSize + padding)
  const trimmed = centered.slice(start, end)
  let energy = 0
  let peak = 0
  for (let i = 0; i < trimmed.length; i += 1) {
    energy += trimmed[i] * trimmed[i]
    peak = Math.max(peak, Math.abs(trimmed[i]))
  }
  const rms = Math.sqrt(energy / Math.max(1, trimmed.length))
  let gain = rms > 0.0001 ? Math.min(8, Math.max(0.75, 0.12 / rms)) : 1
  if (peak > 0 && peak * gain > 0.96) gain = 0.96 / peak
  const normalized = new Float32Array(trimmed.length)
  for (let i = 0; i < trimmed.length; i += 1) {
    normalized[i] = Math.max(-1, Math.min(1, trimmed[i] * gain))
  }
  return normalized
}

function writeAscii(view: DataView, offset: number, text: string) {
  for (let i = 0; i < text.length; i += 1) view.setUint8(offset + i, text.charCodeAt(i))
}

function handleClick(event?: MouseEvent) {
  event?.preventDefault()
  event?.stopPropagation()
  if (suppressNextClick) {
    suppressNextClick = false
    return
  }
  if (busy.value) return
  if (recording.value) {
    void stopAndSend()
    return
  }
  void startRecording()
}

function buttonSize() {
  const rect = buttonRef.value?.getBoundingClientRect()
  return {
    width: rect?.width || Math.min(318, window.innerWidth - 32),
    height: rect?.height || 66,
  }
}

function clampPosition(x: number, y: number) {
  const margin = 8
  const { width, height } = buttonSize()
  return {
    x: Math.min(Math.max(margin, x), Math.max(margin, window.innerWidth - width - margin)),
    y: Math.min(Math.max(margin, y), Math.max(margin, window.innerHeight - height - margin)),
  }
}

function savePosition() {
  if (!position.value) return
  localStorage.setItem(positionStorageKey, JSON.stringify(position.value))
}

function restorePosition() {
  try {
    const saved = JSON.parse(localStorage.getItem(positionStorageKey) || 'null') as { x?: number; y?: number } | null
    if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) {
      position.value = clampPosition(Number(saved.x), Number(saved.y))
      return
    }
  } catch {
    localStorage.removeItem(positionStorageKey)
  }
  const { width, height } = buttonSize()
  position.value = clampPosition(window.innerWidth - width - 28, window.innerHeight - height - 28)
}

function handlePointerMove(event: PointerEvent) {
  if (dragPointerId !== event.pointerId) return
  const deltaX = event.clientX - dragStartX
  const deltaY = event.clientY - dragStartY
  if (!dragMoved && Math.hypot(deltaX, deltaY) < 5) return
  dragMoved = true
  dragging.value = true
  position.value = clampPosition(dragOriginX + deltaX, dragOriginY + deltaY)
}

function finishDrag(event: PointerEvent) {
  if (dragPointerId !== event.pointerId) return
  buttonRef.value?.releasePointerCapture?.(event.pointerId)
  if (dragMoved) {
    suppressNextClick = true
    savePosition()
  }
  dragPointerId = null
  dragging.value = false
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', finishDrag)
  window.removeEventListener('pointercancel', finishDrag)
}

function startDrag(event: PointerEvent) {
  event.stopPropagation()
  if (!event.isPrimary || event.button !== 0) return
  const rect = buttonRef.value?.getBoundingClientRect()
  if (!rect) return
  dragPointerId = event.pointerId
  dragStartX = event.clientX
  dragStartY = event.clientY
  dragOriginX = rect.left
  dragOriginY = rect.top
  dragMoved = false
  buttonRef.value?.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', finishDrag)
  window.addEventListener('pointercancel', finishDrag)
}

function handleResize() {
  if (!position.value) return
  position.value = clampPosition(position.value.x, position.value.y)
  savePosition()
}

function chooseApprovalMode(mode: AgentApprovalMode) {
  approvalMode.value = mode
  setAgentApprovalMode(mode)
  ElMessage.success(mode === 'request' ? 'Agent 已切换为请求批准模式' : 'Agent 已切换为直接执行模式')
}

function handleExternalToggle() {
  handleClick()
}

onMounted(() => {
  restorePosition()
  window.addEventListener('resize', handleResize)
  window.addEventListener('smart-logistics:toggle-voice-assistant', handleExternalToggle)
})

onUnmounted(() => {
  clearTimers()
  stopStream()
  document.body.classList.remove('voice-capturing')
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('smart-logistics:toggle-voice-assistant', handleExternalToggle)
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', finishDrag)
  window.removeEventListener('pointercancel', finishDrag)
})
</script>

<template>
  <div class="agent-permission-switch" :style="permissionStyle" @pointerdown.stop @click.stop>
    <span>AGENT 权限</span>
    <button type="button" :class="{ active: approvalMode === 'request' }" @click="chooseApprovalMode('request')">请求批准</button>
    <button type="button" :class="{ active: approvalMode === 'direct' }" @click="chooseApprovalMode('direct')">直接执行</button>
  </div>
  <button
    ref="buttonRef"
    class="voice-assistant-button"
    :class="{ recording, busy, dragging }"
    :style="dragStyle"
    type="button"
    :title="title"
    :disabled="busy"
    @pointerdown="startDrag"
    @click="handleClick"
  >
    <span class="capsule-scan" aria-hidden="true"></span>
    <span class="capsule-label">VOICE</span>
    <span class="wave-core" aria-hidden="true">
      <svg viewBox="0 0 116 48" role="presentation">
        <path class="wave-path under" d="M5 25 C 19 30, 30 20, 45 24 C 60 28, 70 13, 86 18 C 98 21, 105 27, 111 20" />
        <path class="wave-path main" d="M5 26 C 17 28, 29 23, 42 24 C 58 25, 69 14, 83 16 C 96 18, 104 25, 111 20" />
        <path class="wave-path flare" d="M12 24 C 25 27, 34 25, 45 23 C 60 19, 70 16, 84 18 C 95 19, 103 22, 109 20" />
      </svg>
    </span>
    <span class="capsule-label status">{{ statusLabel }}</span>
    <span v-if="detailMessage" class="voice-hint" :class="{ error: status === 'idle' && detailMessage !== lastText }">{{ detailMessage }}</span>
    <span v-if="lastText && !recording && !busy" class="last-text">{{ lastText }}</span>
  </button>
</template>

<style scoped>
.agent-permission-switch {
  position: fixed;
  right: 28px;
  bottom: 102px;
  z-index: 10021;
  display: flex;
  align-items: center;
  gap: 5px;
  width: 318px;
  padding: 5px 8px;
  border: 1px solid rgba(139, 255, 230, 0.24);
  border-radius: 999px;
  color: rgba(222, 255, 249, 0.72);
  background: rgba(3, 13, 17, 0.92);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(12px);
}

.agent-permission-switch > span {
  margin: 0 auto 0 5px;
  font: 700 10px/1 "Courier New", monospace;
  letter-spacing: .08em;
}

.agent-permission-switch button {
  padding: 5px 9px;
  border: 1px solid transparent;
  border-radius: 999px;
  color: rgba(221, 244, 242, .72);
  background: transparent;
  cursor: pointer;
  font-size: 11px;
}

.agent-permission-switch button.active {
  border-color: rgba(98, 255, 215, .42);
  color: #ecfffb;
  background: rgba(33, 185, 148, .2);
  box-shadow: 0 0 14px rgba(65, 255, 210, .14);
}

.voice-assistant-button {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 10020;
  pointer-events: auto;
  display: grid;
  grid-template-columns: 74px 1fr 78px;
  align-items: center;
  width: 318px;
  max-width: calc(100vw - 40px);
  height: 66px;
  padding: 0 18px;
  border: 1px solid rgba(139, 255, 230, 0.42);
  border-radius: 999px;
  color: #eaffff;
  background:
    radial-gradient(circle at 50% 100%, rgba(94, 255, 214, 0.18), transparent 45%),
    linear-gradient(180deg, rgba(7, 18, 20, 0.96), rgba(2, 8, 12, 0.98));
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.04) inset,
    0 16px 48px rgba(0, 0, 0, 0.42),
    0 0 28px rgba(67, 255, 215, 0.18);
  cursor: grab;
  overflow: visible;
  touch-action: none;
  transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
}

.voice-assistant-button::before {
  content: "";
  position: absolute;
  inset: 7px;
  border: 1px solid rgba(141, 255, 230, 0.16);
  border-radius: inherit;
  pointer-events: none;
}

.voice-assistant-button::after {
  content: "";
  position: absolute;
  inset: -20px 16px auto;
  height: 40px;
  background: radial-gradient(ellipse at center, rgba(185, 255, 247, 0.32), transparent 65%);
  filter: blur(18px);
  opacity: 0.45;
  pointer-events: none;
}

.voice-assistant-button:hover {
  transform: translateY(-3px);
  border-color: rgba(181, 255, 239, 0.82);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.06) inset,
    0 20px 54px rgba(0, 0, 0, 0.48),
    0 0 38px rgba(67, 255, 215, 0.3);
}

.voice-assistant-button.dragging,
.voice-assistant-button.dragging:hover {
  cursor: grabbing;
  transform: none;
  transition: none;
  user-select: none;
}

.voice-assistant-button:disabled {
  cursor: wait;
}

.voice-assistant-button.recording {
  border-color: rgba(255, 238, 191, 0.82);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.06) inset,
    0 20px 54px rgba(0, 0, 0, 0.48),
    0 0 42px rgba(255, 224, 138, 0.28);
}

.voice-assistant-button.busy {
  border-color: rgba(147, 197, 253, 0.72);
}

.voice-hint {
  position: absolute;
  right: 10px;
  bottom: 116px;
  max-width: 320px;
  padding: 8px 12px;
  border: 1px solid rgba(141, 255, 230, 0.28);
  border-radius: 8px;
  color: #eaffff;
  background: rgba(4, 14, 18, 0.9);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.3);
  font-size: 12px;
  line-height: 1.45;
  text-align: left;
  white-space: normal;
}

.voice-hint.error {
  border-color: rgba(255, 138, 138, 0.42);
  color: #ffe6e6;
}

.capsule-scan {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background:
    repeating-linear-gradient(90deg, transparent 0 34px, rgba(141, 255, 230, 0.09) 35px 36px),
    linear-gradient(90deg, transparent, rgba(141, 255, 230, 0.16), transparent);
  background-size: auto, 130px 100%;
  background-position: 0 0, -160px 0;
  opacity: 0.65;
  pointer-events: none;
  animation: capsule-scan 4.4s linear infinite;
}

.capsule-label {
  position: relative;
  z-index: 1;
  color: rgba(236, 254, 255, 0.92);
  font-family: "Courier New", monospace;
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
  text-align: left;
  text-shadow: 0 0 12px rgba(184, 255, 246, 0.42);
}

.capsule-label.status {
  text-align: right;
  color: rgba(224, 252, 231, 0.88);
}

.wave-core {
  position: relative;
  z-index: 1;
  height: 48px;
  border-left: 1px solid rgba(141, 255, 230, 0.1);
  border-right: 1px solid rgba(141, 255, 230, 0.1);
  overflow: hidden;
  filter: drop-shadow(0 0 10px rgba(227, 255, 255, 0.38));
}

.wave-core::before,
.wave-core::after {
  content: "";
  position: absolute;
  top: 50%;
  width: 36px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(206, 255, 250, 0.8), transparent);
}

.wave-core::before {
  left: 2px;
}

.wave-core::after {
  right: 2px;
}

.wave-core svg {
  width: 100%;
  height: 100%;
}

.wave-path {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 28 18;
  animation: wave-flow 2.7s linear infinite;
  transform-origin: 50% 50%;
}

.wave-path.under {
  stroke: rgba(105, 255, 225, 0.34);
  stroke-width: 2;
  filter: blur(1.5px);
}

.wave-path.main {
  stroke: rgba(247, 255, 255, 0.96);
  stroke-width: 2.8;
}

.wave-path.flare {
  stroke: rgba(164, 255, 239, 0.58);
  stroke-width: 1.4;
  stroke-dasharray: 12 22;
  animation-duration: 2.1s;
}

.recording .wave-path {
  animation-duration: 1.2s;
  transform: scaleY(1.55);
}

.busy .wave-path {
  animation-duration: 0.9s;
}

.last-text {
  position: absolute;
  right: 16px;
  bottom: 114px;
  max-width: 280px;
  padding: 7px 12px;
  border: 1px solid rgba(141, 255, 230, 0.22);
  border-radius: 8px;
  color: #dffdfa;
  background: rgba(4, 14, 18, 0.86);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.28);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes capsule-scan {
  to { background-position: 0 0, 420px 0; }
}

@keyframes wave-flow {
  to { stroke-dashoffset: -92; }
}

@media (max-width: 560px) {
  .agent-permission-switch {
    right: 16px;
    bottom: 85px;
    width: calc(100vw - 32px);
  }
  .voice-assistant-button {
    right: 16px;
    bottom: 18px;
    grid-template-columns: 62px 1fr 60px;
    width: calc(100vw - 32px);
    height: 60px;
    padding: 0 14px;
  }

  .capsule-label {
    font-size: 13px;
  }

  .last-text {
    right: 0;
    max-width: calc(100vw - 32px);
  }
}
</style>
