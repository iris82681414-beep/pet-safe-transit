<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useLogisticsStore } from '@/stores/logistics'
import type { AlertItem, UserRole } from '@/types'

type PetMood = 'idle' | 'happy' | 'thinking' | 'working' | 'notice' | 'warning' | 'critical' | 'voice' | 'comfort' | 'sleep' | 'offline' | 'speaking' | 'analysis' | 'received'
type Priority = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

const petImage = new URL('../../assets/iot-sheep-pet.png', import.meta.url).href
const store = useLogisticsStore()
const router = useRouter()
const { alerts, commands, user, realtimeState } = storeToRefs(store)
const rootRef = ref<HTMLElement | null>(null)
const collapsed = ref(localStorage.getItem('yang-smart-collapsed') !== 'false')
const mood = ref<PetMood>('idle')
const message = ref('你好，我是羊小智。运输状态和风险提醒都可以问我。')
const priority = ref<Priority>('INFO')
const currentAlertId = ref('')
const dragging = ref(false)
const position = ref<{ x: number; y: number } | null>(null)
const unreadRisk = computed(() => alerts.value.filter((item) => item.status === 'PENDING').length)
const positionKey = 'yang-smart-position'
let pointerId: number | null = null
let startX = 0
let startY = 0
let originX = 0
let originY = 0
let moved = false
let suppressClick = false
let moodTimer: number | null = null
let sleepTimer: number | null = null
let criticalTimer: number | null = null
const recentEvents = new Map<string, number>()

const size = computed(() => collapsed.value ? { width: 100, height: 100 } : { width: 300, height: 420 })
const rootStyle = computed(() => position.value ? { left: `${position.value.x}px`, top: `${position.value.y}px`, right: 'auto', bottom: 'auto' } : undefined)
const moodCopy: Record<PetMood, string> = {
  idle: '待命中', happy: '任务顺利', thinking: '正在思考', working: '处理中', notice: '照护提醒', warning: '风险提醒', critical: '严重风险',
  voice: '正在倾听', comfort: '安心陪伴', sleep: '休息一下', offline: '连接中断', speaking: '正在播报', analysis: '数据分析', received: '已经收到',
}

const roleShortcuts = computed(() => {
  const map: Record<UserRole, Array<{ label: string; page: string }>> = {
    SHIPPER: [{ label: '旅程追踪', page: 'tracking' }, { label: '运输助手', page: 'assistant' }],
    WAREHOUSE: [{ label: '中转管理', page: 'warehouse' }, { label: '风险中心', page: 'alerts' }],
    DISPATCHER: [{ label: '车辆调度', page: 'dispatch' }, { label: '风险中心', page: 'alerts' }],
    DRIVER: [{ label: '照护任务', page: 'driver' }, { label: '旅程追踪', page: 'tracking' }],
    ADMIN: [{ label: '全局总览', page: 'overview' }, { label: '风险中心', page: 'alerts' }],
  }
  return map[user.value?.role || 'DISPATCHER']
})

function clamp(x: number, y: number) {
  const margin = 8
  return {
    x: Math.min(Math.max(margin, x), Math.max(margin, window.innerWidth - size.value.width - margin)),
    y: Math.min(Math.max(margin, y), Math.max(margin, window.innerHeight - size.value.height - margin)),
  }
}

function savePosition() {
  if (position.value) localStorage.setItem(positionKey, JSON.stringify(position.value))
}

function restorePosition() {
  try {
    const saved = JSON.parse(localStorage.getItem(positionKey) || 'null')
    if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) {
      position.value = clamp(saved.x, saved.y)
      return
    }
  } catch { localStorage.removeItem(positionKey) }
  position.value = clamp(window.innerWidth - size.value.width - 26, window.innerHeight - size.value.height - 112)
}

function setMood(next: PetMood, text?: string, nextPriority: Priority = 'INFO', resetAfter = 0) {
  if (moodTimer != null) window.clearTimeout(moodTimer)
  moodTimer = null
  mood.value = next
  if (text) message.value = text
  priority.value = nextPriority
  if (resetAfter > 0 && next !== 'critical') {
    moodTimer = window.setTimeout(() => {
      mood.value = 'idle'
      priority.value = 'INFO'
      message.value = '我会继续关注车辆、设备和运输任务。'
    }, resetAfter)
  }
}

function shouldShowEvent(key: string, force = false) {
  const now = Date.now()
  const last = recentEvents.get(key) || 0
  if (!force && now - last < 10 * 60_000) return false
  recentEvents.set(key, now)
  return true
}

function showAlert(alert: AlertItem, repeat = false) {
  if (!shouldShowEvent(`alert:${alert.id}`, repeat)) return
  currentAlertId.value = alert.id
  collapsed.value = false
  localStorage.setItem('yang-smart-collapsed', 'false')
  const text = `${alert.title}：车辆 ${alert.plate || '待确认'}，位置 ${alert.location || '待确认'}。请查看当前指标、阈值、持续时间和处理人，并按建议动作处理。`
  if (alert.severity === 'CRITICAL') setMood('critical', text, 'CRITICAL')
  else if (alert.severity === 'WARNING') setMood('warning', text, 'HIGH', 12_000)
  else setMood('notice', text, 'MEDIUM', 9_000)
  position.value = clamp(position.value?.x ?? 0, position.value?.y ?? 0)
  savePosition()
}

function toggleCollapsed() {
  if (suppressClick) { suppressClick = false; return }
  collapsed.value = !collapsed.value
  localStorage.setItem('yang-smart-collapsed', String(collapsed.value))
  if (position.value) position.value = clamp(position.value.x, position.value.y)
  savePosition()
}

function startDrag(event: PointerEvent) {
  if (!event.isPrimary || event.button !== 0) return
  const rect = rootRef.value?.getBoundingClientRect()
  if (!rect) return
  pointerId = event.pointerId
  startX = event.clientX; startY = event.clientY; originX = rect.left; originY = rect.top; moved = false
  rootRef.value?.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', moveDrag)
  window.addEventListener('pointerup', endDrag)
  window.addEventListener('pointercancel', endDrag)
}

function moveDrag(event: PointerEvent) {
  if (pointerId !== event.pointerId) return
  const dx = event.clientX - startX; const dy = event.clientY - startY
  if (!moved && Math.hypot(dx, dy) < 5) return
  moved = true; dragging.value = true
  position.value = clamp(originX + dx, originY + dy)
}

function endDrag(event: PointerEvent) {
  if (pointerId !== event.pointerId) return
  if (moved) { suppressClick = true; savePosition() }
  pointerId = null; dragging.value = false
  window.removeEventListener('pointermove', moveDrag); window.removeEventListener('pointerup', endDrag); window.removeEventListener('pointercancel', endDrag)
}

function openPage(page: string) {
  if (String(router.currentRoute.value.name || '') === 'portal') window.dispatchEvent(new CustomEvent('smart-logistics:open-floating-page', { detail: { page } }))
  else void router.push({ name: page })
}

function handleBubble() {
  if (currentAlertId.value) openPage('alerts')
  else openPage('assistant')
}

function startVoice() {
  const button = document.querySelector<HTMLButtonElement>('.voice-assistant-button')
  button?.click()
}

function handlePetState(event: Event) {
  const detail = (event as CustomEvent<{ mood?: PetMood; message?: string; priority?: Priority }>).detail || {}
  if (detail.mood) setMood(detail.mood, detail.message, detail.priority || 'INFO')
}

function handleVoiceState(event: Event) {
  const status = String((event as CustomEvent<{ status?: string }>).detail?.status || '')
  if (status === 'requesting' || status === 'recording') setMood('voice', '我在听，请说出你的运输问题或操作指令。')
  else if (status === 'uploading') setMood('thinking', '正在识别语音并理解你的意思…')
  else if (status === 'executing') setMood('working', '正在执行 Agent 任务…')
  else if (status === 'idle' && ['voice', 'thinking', 'working'].includes(mood.value)) setMood('idle', '我会继续关注车辆、设备和运输任务。')
}

function handleAgentComplete(event: Event) {
  const detail = (event as CustomEvent<Record<string, any>>).detail || {}
  const name = String(detail.functionName || detail.action?.functionName || detail.url || '')
  if (name.includes('alert')) setMood('notice', '风险操作已经完成，我会继续观察状态。', 'MEDIUM', 7000)
  else if (name.startsWith('query_')) setMood('analysis', '数据分析完成，结果已经展示。', 'INFO', 6000)
  else setMood('received', '收到，任务已经处理完成。', 'INFO', 6000)
}

function resetSleepTimer() {
  if (sleepTimer != null) window.clearTimeout(sleepTimer)
  if (mood.value === 'sleep') setMood('idle', '我醒啦，有什么可以帮你？')
  sleepTimer = window.setTimeout(() => {
    if (mood.value === 'idle') setMood('sleep', '我先打个小盹，有新消息会马上醒来。')
  }, 2 * 60_000)
}

watch(() => alerts.value.map((item) => `${item.id}:${item.status}`).join('|'), () => {
  const latest = alerts.value.find((item) => item.status === 'PENDING')
  if (latest) showAlert(latest)
})
watch(() => commands.value.map((item) => `${item.id}:${item.status}`).join('|'), () => {
  const command = commands.value.find((item) => item.status === 'RECEIVED')
  if (command && shouldShowEvent(`command:${command.id}`)) setMood('notice', `${command.plate} 已收到调度指令，等待司机确认执行。`, 'MEDIUM', 9000)
})
watch(realtimeState, (state) => {
  if (!store.usingDemo && state !== 'open') setMood('offline', '实时连接暂时中断，我正在尝试恢复。', 'HIGH')
  else if (mood.value === 'offline') setMood('happy', '实时连接已经恢复。', 'INFO', 5000)
})

onMounted(() => {
  restorePosition()
  window.addEventListener('resize', restorePosition)
  window.addEventListener('pointerdown', resetSleepTimer)
  window.addEventListener('keydown', resetSleepTimer)
  window.addEventListener('smart-logistics:desk-pet-state', handlePetState)
  window.addEventListener('smart-logistics:voice-state', handleVoiceState)
  window.addEventListener('smart-logistics:voice-action-complete', handleAgentComplete)
  resetSleepTimer()
  window.setTimeout(() => {
    const critical = alerts.value.find((item) => item.status === 'PENDING' && item.severity === 'CRITICAL')
    if (critical) showAlert(critical)
  }, 900)
  criticalTimer = window.setInterval(() => {
    const critical = alerts.value.find((item) => item.status === 'PENDING' && item.severity === 'CRITICAL')
    if (critical) showAlert(critical, true)
  }, 3 * 60_000)
})

onUnmounted(() => {
  window.removeEventListener('resize', restorePosition); window.removeEventListener('pointerdown', resetSleepTimer); window.removeEventListener('keydown', resetSleepTimer)
  window.removeEventListener('smart-logistics:desk-pet-state', handlePetState); window.removeEventListener('smart-logistics:voice-state', handleVoiceState); window.removeEventListener('smart-logistics:voice-action-complete', handleAgentComplete)
  if (moodTimer != null) window.clearTimeout(moodTimer); if (sleepTimer != null) window.clearTimeout(sleepTimer); if (criticalTimer != null) window.clearInterval(criticalTimer)
})
</script>

<template>
  <section ref="rootRef" class="yang-smart-pet" :class="[`mood-${mood}`, { collapsed, dragging, critical: priority === 'CRITICAL' }]" :style="rootStyle">
    <template v-if="collapsed">
      <button class="pet-collapsed-button" type="button" title="展开羊小智" @pointerdown="startDrag" @click="toggleCollapsed">
        <span v-if="unreadRisk" class="pet-risk-count">{{ unreadRisk }}</span>
        <img :src="petImage" alt="羊小智" />
      </button>
    </template>
    <template v-else>
      <header class="pet-panel-head" @pointerdown="startDrag">
        <span><i></i><strong>羊小智</strong><small>YANG SMART</small></span>
        <button type="button" title="折叠桌宠" @click.stop="toggleCollapsed">—</button>
      </header>
      <button class="pet-message" :class="priority.toLowerCase()" type="button" @click="handleBubble">{{ message }}</button>
      <button class="pet-character" type="button" @pointerdown="startDrag" @click="toggleCollapsed">
        <span class="pet-aura"></span><span class="pet-symbol">{{ mood === 'thinking' ? '?' : mood === 'warning' || mood === 'critical' ? '!' : mood === 'happy' ? '✦' : mood === 'received' ? '✓' : '' }}</span>
        <span v-if="mood === 'voice' || mood === 'speaking'" class="pet-sound-wave">)))</span>
        <span v-if="mood === 'working' || mood === 'analysis'" class="pet-data-panel"><i></i><i></i><i></i></span>
        <img :src="petImage" alt="萌宠运输 IoT 照护助手羊小智" />
        <span class="pet-ground"></span>
      </button>
      <div class="pet-state"><i></i><span>{{ moodCopy[mood] }}</span><b>{{ priority }}</b></div>
      <div class="pet-quick-actions">
        <button v-for="item in roleShortcuts" :key="item.page" type="button" @click="openPage(item.page)">{{ item.label }}</button>
        <button type="button" class="voice" @click="startVoice">语音</button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.yang-smart-pet{position:fixed;right:26px;bottom:112px;z-index:10015;width:300px;height:420px;border:1px solid rgba(57,195,255,.38);border-radius:22px;overflow:hidden;color:#153d62;background:linear-gradient(155deg,rgba(250,254,255,.98),rgba(226,245,255,.97));box-shadow:0 22px 65px rgba(8,55,91,.22),inset 0 0 0 1px rgba(255,255,255,.7);backdrop-filter:blur(18px);transition:width .22s ease,height .22s ease,border-color .2s ease,box-shadow .2s ease;touch-action:none}
.yang-smart-pet::before{content:"";position:absolute;inset:0;background-image:linear-gradient(rgba(57,195,255,.06) 1px,transparent 1px),linear-gradient(90deg,rgba(57,195,255,.06) 1px,transparent 1px);background-size:24px 24px;pointer-events:none}.yang-smart-pet.collapsed{width:100px;height:100px;border-radius:50%;overflow:visible;background:rgba(239,250,255,.96)}.yang-smart-pet.dragging{transition:none;cursor:grabbing}.yang-smart-pet.critical{border-color:rgba(240,68,85,.72);box-shadow:0 20px 70px rgba(240,68,85,.25),0 0 0 4px rgba(240,68,85,.09)}
.pet-collapsed-button{position:absolute;inset:0;border:0;border-radius:50%;overflow:hidden;background:radial-gradient(circle,#fff 25%,#dff5ff);cursor:grab}.pet-collapsed-button img{width:114px;height:114px;object-fit:contain;transform:translate(-7px,-4px);filter:drop-shadow(0 8px 10px rgba(21,105,160,.2));animation:pet-float 3s ease-in-out infinite}.pet-risk-count{position:absolute;z-index:3;right:-2px;top:-4px;display:grid;place-items:center;min-width:27px;height:27px;padding:0 6px;border:3px solid white;border-radius:20px;color:white;background:#f04455;font-size:12px;font-weight:900}
.pet-panel-head{position:relative;z-index:4;display:flex;align-items:center;justify-content:space-between;height:48px;padding:0 13px;border-bottom:1px solid rgba(57,195,255,.18);cursor:grab}.pet-panel-head>span{display:flex;align-items:center;gap:6px}.pet-panel-head i{width:8px;height:8px;border-radius:50%;background:#00e7b7;box-shadow:0 0 0 4px rgba(0,231,183,.12)}.pet-panel-head strong{font-size:13px}.pet-panel-head small{color:#6590ae;font-size:9px;font-weight:800;letter-spacing:.08em}.pet-panel-head button{width:28px;height:28px;border:0;border-radius:9px;color:#47718e;background:rgba(57,195,255,.1);cursor:pointer}
.pet-message{position:relative;z-index:4;display:block;width:calc(100% - 24px);min-height:54px;margin:10px 12px 0;padding:9px 11px;border:1px solid rgba(57,195,255,.23);border-radius:13px 13px 13px 4px;color:#244d6a;background:rgba(255,255,255,.9);box-shadow:0 8px 20px rgba(44,119,167,.09);font-size:11px;font-weight:700;line-height:1.55;text-align:left;cursor:pointer}.pet-message.high{border-color:rgba(255,138,61,.5);background:#fff9f3}.pet-message.critical{border-color:rgba(240,68,85,.58);color:#9f2635;background:#fff4f5}
.pet-character{position:relative;z-index:2;display:block;width:100%;height:226px;border:0;background:transparent;cursor:pointer}.pet-character img{position:absolute;z-index:2;left:50%;bottom:3px;width:198px;height:218px;object-fit:contain;transform:translateX(-50%);transform-origin:50% 92%;filter:drop-shadow(0 12px 15px rgba(20,95,145,.2));animation:pet-float 3.4s ease-in-out infinite}.pet-aura{position:absolute;left:50%;bottom:25px;width:178px;height:178px;border:1px solid rgba(57,195,255,.22);border-radius:50%;transform:translateX(-50%);box-shadow:0 0 40px rgba(57,195,255,.18),inset 0 0 32px rgba(0,231,183,.1);animation:aura 2.6s ease-in-out infinite}.pet-ground{position:absolute;z-index:1;left:50%;bottom:2px;width:135px;height:16px;border-radius:50%;background:rgba(26,91,130,.16);filter:blur(5px);transform:translateX(-50%)}.pet-symbol{position:absolute;z-index:5;right:34px;top:17px;color:#6a5cff;font-size:37px;font-weight:950;text-shadow:0 5px 15px rgba(106,92,255,.25);animation:symbol 1s ease-in-out infinite alternate}.mood-warning .pet-symbol,.mood-critical .pet-symbol{color:#f04455}.mood-happy .pet-symbol{color:#ffd04f}.mood-received .pet-symbol{color:#00ae80}.pet-sound-wave{position:absolute;z-index:5;right:16px;top:80px;color:#1aa9e8;font:900 18px/1 monospace;animation:wave .65s ease-in-out infinite alternate}.pet-data-panel{position:absolute;z-index:5;right:12px;bottom:28px;display:flex;align-items:end;gap:4px;width:58px;height:46px;padding:8px;border:1px solid rgba(57,195,255,.45);border-radius:8px;background:rgba(224,249,255,.82)}.pet-data-panel i{width:8px;border-radius:2px 2px 0 0;background:#39c3ff;animation:bars .75s ease-in-out infinite alternate}.pet-data-panel i:nth-child(1){height:12px}.pet-data-panel i:nth-child(2){height:26px;animation-delay:.16s}.pet-data-panel i:nth-child(3){height:18px;animation-delay:.32s}
.pet-state{position:relative;z-index:4;display:flex;align-items:center;justify-content:center;gap:6px;color:#557a94;font-size:10px;font-weight:800}.pet-state>i{width:7px;height:7px;border-radius:50%;background:#00c997}.pet-state b{padding:2px 6px;border-radius:8px;color:#43728f;background:rgba(57,195,255,.1);font-size:8px}.critical .pet-state>i{background:#f04455;animation:blink .65s infinite}.pet-quick-actions{position:relative;z-index:4;display:grid;grid-template-columns:1fr 1fr 58px;gap:6px;margin:10px 12px}.pet-quick-actions button{height:31px;border:1px solid rgba(57,195,255,.22);border-radius:9px;color:#2e6589;background:rgba(255,255,255,.72);font-size:10px;font-weight:800;cursor:pointer}.pet-quick-actions button:hover{color:#086eab;border-color:#39c3ff;background:#fff}.pet-quick-actions .voice{color:#fff;border-color:#6a5cff;background:#6a5cff}
.mood-happy .pet-character img,.mood-received .pet-character img{animation:pet-happy .72s ease-in-out 2}.mood-thinking .pet-character img,.mood-analysis .pet-character img{animation:pet-think 1.6s ease-in-out infinite}.mood-working .pet-character img{animation:pet-work 1.1s ease-in-out infinite}.mood-warning .pet-character img,.mood-critical .pet-character img{animation:pet-alert .48s ease-in-out infinite}.mood-voice .pet-character img,.mood-speaking .pet-character img{animation:pet-listen .7s ease-in-out infinite}.mood-sleep .pet-character img{animation:pet-sleep 4s ease-in-out infinite;filter:saturate(.65) drop-shadow(0 10px 12px rgba(20,95,145,.12))}.mood-offline .pet-character img{animation:none;filter:grayscale(.7) brightness(.82)}
@keyframes pet-float{50%{transform:translateX(-50%) translateY(-7px) rotate(1deg)}}@keyframes aura{50%{opacity:.6;transform:translateX(-50%) scale(1.07)}}@keyframes symbol{to{transform:translateY(-7px) rotate(6deg) scale(1.12)}}@keyframes wave{to{transform:translateX(6px) scaleX(1.16);opacity:.55}}@keyframes bars{to{height:30px}}@keyframes blink{50%{opacity:.2}}@keyframes pet-happy{50%{transform:translateX(-50%) translateY(-13px) rotate(-3deg) scale(1.03)}}@keyframes pet-think{33%{transform:translateX(calc(-50% - 4px)) translateY(-5px) rotate(-2deg)}66%{transform:translateX(calc(-50% + 4px)) translateY(-3px) rotate(2deg)}}@keyframes pet-work{50%{transform:translateX(calc(-50% + 5px)) translateY(-4px) rotate(2deg)}}@keyframes pet-alert{30%{transform:translateX(calc(-50% - 4px)) rotate(-2deg)}70%{transform:translateX(calc(-50% + 4px)) rotate(2deg)}}@keyframes pet-listen{50%{transform:translateX(-50%) translateY(-5px) scale(1.02,.98)}}@keyframes pet-sleep{50%{transform:translateX(-50%) translateY(2px) scale(1.01,.97)}}
@media(max-width:760px){.yang-smart-pet:not(.collapsed){width:min(300px,calc(100vw - 20px));height:400px}.yang-smart-pet{z-index:10030}}@media(prefers-reduced-motion:reduce){.yang-smart-pet *{animation:none!important}}
</style>
