<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { CSSProperties } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useLogisticsStore } from '@/stores/logistics'
import type { UserRole } from '@/types'
import mapImage from '@/assets/login-map-hd.png'
import overviewLogisticsImage from '../../图1.png'
import overviewAirImage from '../../图2.png'
import overviewDriverImage from '../../图3.png'

const emit = defineEmits<{
  transitionStart: []
  transitionEnd: []
}>()

const store = useLogisticsStore()
const router = useRouter()
const form = reactive<{ username: string; password: string; role: UserRole }>({
  username: 'dispatcher',
  password: '123456',
  role: 'DISPATCHER',
})
const mode = ref<'password' | 'face'>('password')
const phase = ref<'idle' | 'authenticating' | 'locating' | 'complete'>('idle')
const loading = ref(false)
const loginError = ref('')
const cameraActive = ref(false)
const root = ref<HTMLElement | null>(null)
const mapPanel = ref<HTMLElement | null>(null)
const loginButton = ref<HTMLButtonElement | null>(null)
const video = ref<HTMLVideoElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
const locationLabel = ref('动态画布接入点')
const ipLabel = ref('身份链路已聚合')
const target = reactive({ x: 69, y: 55 })
let cameraStream: MediaStream | null = null
let sceneFrame = 0
let pendingTiltX = 0
let pendingTiltY = 0
const routeGeometry = reactive({
  width: 1000,
  height: 600,
  originX: 820,
  originY: 470,
  targetX: 330,
  targetY: 320,
  branchX: 210,
  branchY: 410,
  mapLeft: 0,
  mapTop: 0,
  mapWidth: 640,
  mapHeight: 720,
})

const isTransitioning = computed(() => phase.value === 'locating' || phase.value === 'complete')
const fiberPaths = computed(() => {
  const { originX, originY, mapLeft, mapTop, mapWidth, mapHeight } = routeGeometry
  const fibers = [
    { delay: .04, main: true, spread: 0 },
    { delay: .12, spread: -92 },
    { delay: .2, spread: 74 },
    { delay: .28, spread: -148 },
    { delay: .36, spread: 132 },
    { delay: .44, spread: 190 },
  ]
  const endX = mapLeft + mapWidth * target.x / 100
  const endY = mapTop + mapHeight * target.y / 100
  return fibers.map((item, index) => {
    const controlOneX = originX - 112 - index * 10
    const controlOneY = originY + item.spread * .42
    const controlTwoX = endX + Math.max(95, (originX - endX) * .34)
    const controlTwoY = endY + item.spread
    return {
      d: `M ${originX} ${originY} C ${controlOneX} ${controlOneY}, ${controlTwoX} ${controlTwoY}, ${endX} ${endY}`,
      delay: item.delay,
      main: Boolean(item.main),
    }
  })
})
const targetStyle = computed(() => ({ left: `${target.x}%`, top: `${target.y}%` }))
const routeViewBox = computed(() => `0 0 ${routeGeometry.width} ${routeGeometry.height}`)
const originStyle = computed(() => ({
  left: `${routeGeometry.originX}px`,
  top: `${routeGeometry.originY}px`,
}))
const sceneStyle = computed(() => ({
  '--scene-rx': '0deg',
  '--scene-ry': '0deg',
  '--focus-x': `${routeGeometry.targetX}px`,
  '--focus-y': `${routeGeometry.targetY}px`,
  '--focus-x-percent': `${routeGeometry.width ? routeGeometry.targetX / routeGeometry.width * 100 : 50}%`,
  '--focus-y-percent': `${routeGeometry.height ? routeGeometry.targetY / routeGeometry.height * 100 : 50}%`,
}) as CSSProperties)

function moveScene(event: PointerEvent) {
  const rect = root.value?.getBoundingClientRect()
  if (!rect || isTransitioning.value) return
  pendingTiltX = (.5 - (event.clientY - rect.top) / rect.height) * 1.4
  pendingTiltY = ((event.clientX - rect.left) / rect.width - .5) * 1.8
  if (sceneFrame) return
  sceneFrame = requestAnimationFrame(() => {
    sceneFrame = 0
    root.value?.style.setProperty('--scene-rx', `${pendingTiltX}deg`)
    root.value?.style.setProperty('--scene-ry', `${pendingTiltY}deg`)
  })
}

function resetScene() {
  pendingTiltX = 0
  pendingTiltY = 0
  root.value?.style.setProperty('--scene-rx', '0deg')
  root.value?.style.setProperty('--scene-ry', '0deg')
}

function updateRouteGeometry() {
  const rootRect = root.value?.getBoundingClientRect()
  const mapRect = mapPanel.value?.getBoundingClientRect()
  const buttonRect = loginButton.value?.getBoundingClientRect()
  if (!rootRect || !mapRect || !buttonRect) return
  routeGeometry.width = rootRect.width
  routeGeometry.height = rootRect.height
  routeGeometry.originX = buttonRect.left - rootRect.left + buttonRect.width / 2
  routeGeometry.originY = buttonRect.top - rootRect.top + buttonRect.height / 2
  routeGeometry.targetX = mapRect.left - rootRect.left + mapRect.width * target.x / 100
  routeGeometry.targetY = mapRect.top - rootRect.top + mapRect.height * target.y / 100
  routeGeometry.branchX = mapRect.left - rootRect.left + mapRect.width * .32
  routeGeometry.branchY = mapRect.top - rootRect.top + mapRect.height * .66
  routeGeometry.mapLeft = mapRect.left - rootRect.left
  routeGeometry.mapTop = mapRect.top - rootRect.top
  routeGeometry.mapWidth = mapRect.width
  routeGeometry.mapHeight = mapRect.height
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

async function playEntrySequence() {
  phase.value = 'locating'
  await nextTick()
  updateRouteGeometry()
  await delay(520)
  phase.value = 'complete'
  await delay(280)
  await router.push('/portal')
  emit('transitionEnd')
}

async function passwordLogin() {
  loginError.value = ''
  if (!form.username.trim()) {
    loginError.value = '请输入用户名'
    ElMessage.warning(loginError.value)
    return
  }
  if (!form.password) {
    loginError.value = '请输入密码'
    ElMessage.warning(loginError.value)
    return
  }
  loading.value = true
  phase.value = 'authenticating'
  emit('transitionStart')
  try {
    await store.login(form.username, form.password, form.role)
    await playEntrySequence()
  } catch (error) {
    phase.value = 'idle'
    emit('transitionEnd')
    loginError.value = error instanceof Error ? error.message : '登录失败，请检查账号或服务配置'
    ElMessage.error(loginError.value)
  } finally {
    loading.value = false
  }
}

async function startCamera() {
  if (!window.isSecureContext && !['localhost', '127.0.0.1'].includes(window.location.hostname)) {
    await showFaceError('浏览器要求 HTTPS 才能使用摄像头，请使用 https 地址访问系统')
    return
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    await showFaceError('当前浏览器不支持摄像头调用，请更换 Chrome 或 Edge 后重试')
    return
  }
  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: { ideal: 720 }, height: { ideal: 540 } },
      audio: false,
    })
    cameraActive.value = true
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => {
        if (video.value && cameraStream) {
          video.value.srcObject = cameraStream
          void video.value.play()
        }
        resolve()
      })
    })
  } catch (error) {
    const message = error instanceof Error && error.name === 'NotAllowedError'
      ? '未获得摄像头权限，请在浏览器地址栏允许摄像头后重试'
      : error instanceof Error
        ? `摄像头启动失败：${error.message}`
        : '摄像头启动失败，请检查设备权限'
    await showFaceError(message)
  }
}

function stopCamera() {
  cameraStream?.getTracks().forEach((track) => track.stop())
  cameraStream = null
  cameraActive.value = false
}

async function faceLogin() {
  if (!cameraActive.value) {
    await startCamera()
    return
  }
  const source = video.value
  const output = canvas.value
  if (!source || !output || !source.videoWidth) {
    ElMessage.warning('摄像头正在初始化，请稍后重试')
    return
  }
  output.width = source.videoWidth
  output.height = source.videoHeight
  output.getContext('2d')?.drawImage(source, 0, 0)
  const imageBase64 = output.toDataURL('image/jpeg', .88).replace(/^data:image\/\w+;base64,/, '')
  if (!imageBase64) return

  loading.value = true
  phase.value = 'authenticating'
  emit('transitionStart')
  try {
    await store.loginWithFace(imageBase64, form.role)
    stopCamera()
    await playEntrySequence()
  } catch (error) {
    phase.value = 'idle'
    emit('transitionEnd')
    const message = error instanceof Error ? error.message : '人脸识别失败，请改用账号密码登录'
    await showFaceError(message)
  } finally {
    loading.value = false
  }
}

function switchMode(nextMode: 'password' | 'face') {
  mode.value = nextMode
  if (nextMode === 'password') stopCamera()
}

async function showFaceError(message: string) {
  ElMessage.error(message)
  await ElMessageBox.alert(message, '人脸验证失败', {
    confirmButtonText: '知道了',
    type: 'error',
    customClass: 'face-auth-message-box',
  }).catch(() => undefined)
}

onBeforeUnmount(stopCamera)
onMounted(() => {
  window.addEventListener('resize', updateRouteGeometry)
  const preload = () => [overviewLogisticsImage, overviewAirImage, overviewDriverImage].forEach((src) => {
    const image = new Image()
    image.decoding = 'async'
    image.src = src
  })
  window.setTimeout(preload, 300)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', updateRouteGeometry)
  if (sceneFrame) cancelAnimationFrame(sceneFrame)
})
</script>

<template>
  <main
    ref="root"
    class="neo-login neo-login-split"
    :class="{ 'is-routing': isTransitioning, 'is-complete': phase === 'complete' }"
    :style="sceneStyle"
    @pointermove="moveScene"
    @pointerleave="resetScene"
  >
    <div class="scene-depth-stars"></div>
    <div class="scene-depth-plane"></div>
    <section ref="mapPanel" class="neo-map-zone" aria-label="实时物流网络地图">
      <img class="neo-map-image" :src="mapImage" alt="" aria-hidden="true" />
      <div class="native-node-pulses" aria-hidden="true">
        <i v-for="node in 6" :key="node" :class="`pulse-${node}`"></i>
      </div>
      <div class="neo-map-aura"></div>

      <div v-if="isTransitioning" class="ip-target" :style="targetStyle">
        <i></i><i></i><i></i>
        <div><strong>{{ locationLabel }}</strong><span>{{ ipLabel }}</span></div>
      </div>
    </section>
    <div class="neo-auth-zone"></div>
    <div class="neo-scanlines"></div>
    <div class="neo-vignette"></div>
    <div class="scene-focus-dot" aria-hidden="true"><i></i><b></b></div>

    <header class="neo-login-head">
      <div class="neo-brand">
        <span><el-icon><Van /></el-icon></span>
        <div><strong>灵枢物流</strong><small>LINGSHU LOGISTICS CLOUD</small></div>
      </div>
      <div class="neo-security"><i></i> SECURE NODE · CN-07</div>
    </header>

    <section class="neo-auth-card" :class="{ 'face-mode': mode === 'face' }">
      <div class="auth-card-head">
        <div>
          <span>IDENTITY GATEWAY</span>
          <h2>欢迎回来</h2>
        </div>
        <b>01</b>
      </div>

      <div class="login-mode-switch">
        <button :class="{ active: mode === 'password' }" @click="switchMode('password')">账号登录</button>
        <button :class="{ active: mode === 'face' }" @click="switchMode('face')">人脸识别</button>
      </div>

      <form v-if="mode === 'password'" class="neo-login-form" @submit.prevent="passwordLogin">
        <label>
          <span>账号 / ACCOUNT</span>
          <div><el-icon><User /></el-icon><input v-model="form.username" autocomplete="username" placeholder="请输入账号" /></div>
        </label>
        <label>
          <span>密码 / PASSWORD</span>
          <div><el-icon><Lock /></el-icon><input v-model="form.password" type="password" autocomplete="current-password" placeholder="请输入密码" /></div>
        </label>
        <label class="role-select">
          <span>身份 / ROLE</span>
          <select v-model="form.role">
            <option value="DISPATCHER">调度员</option>
            <option value="WAREHOUSE">仓库管理员</option>
            <option value="DRIVER">司机</option>
            <option value="SHIPPER">货主</option>
            <option value="ADMIN">系统管理员</option>
          </select>
        </label>
        <p v-if="loginError" class="login-error-tip">{{ loginError }}</p>
        <button ref="loginButton" class="neo-login-button" type="submit" :disabled="loading">
          <span>{{ loading ? '正在验证身份' : '进入物流网络' }}</span>
          <el-icon><ArrowRight /></el-icon>
          <i class="login-spark"></i>
        </button>
      </form>

      <div v-else class="face-login-panel">
        <div class="camera-frame" :class="{ active: cameraActive }">
          <video ref="video" muted playsinline></video>
          <div v-if="!cameraActive" class="camera-placeholder">
            <el-icon><Camera /></el-icon>
            <strong>启用安全摄像头</strong>
            <span>影像仅用于本次身份验证</span>
          </div>
          <div class="face-corners"><i></i><i></i><i></i><i></i></div>
          <div v-if="cameraActive" class="face-scan"></div>
        </div>
        <canvas ref="canvas" hidden></canvas>
        <button ref="loginButton" class="neo-login-button face-button" :disabled="loading" @click="faceLogin">
          <span>{{ loading ? '正在比对生物特征' : cameraActive ? '扫描并验证' : '打开摄像头' }}</span>
          <el-icon><View /></el-icon>
          <i class="login-spark"></i>
        </button>
        <p>真实接口：<code>POST /auth/face-login</code></p>
      </div>

      <footer><span><i></i> AES-256 ENCRYPTED</span><small>演示账号 dispatcher / 123456</small></footer>
    </section>

    <svg v-if="isTransitioning" class="login-route-beams" :viewBox="routeViewBox" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <filter id="beam-soft-glow" x="-30%" y="-30%" width="160%" height="160%">
          <feGaussianBlur stdDeviation="7" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>
      <g v-for="(fiber, index) in fiberPaths" :key="index" class="fiber-line">
        <path
          class="energy-fiber-glow"
          pathLength="1"
          :d="fiber.d"
          :style="{ '--fiber-delay': `${fiber.delay}s` }"
        />
        <path
          class="energy-fiber"
          :class="{ main: fiber.main }"
          pathLength="1"
          :d="fiber.d"
          :style="{ '--fiber-delay': `${fiber.delay}s` }"
        />
        <circle class="fiber-tip" :class="{ main: fiber.main }" :r="fiber.main ? 5 : 3">
          <animateMotion :path="fiber.d" :begin="`${fiber.delay}s`" dur="1.25s" fill="freeze" />
          <animate attributeName="opacity" values="0;1;1;0" keyTimes="0;.08;.86;1" :begin="`${fiber.delay}s`" dur="1.46s" fill="freeze" />
        </circle>
      </g>
    </svg>

    <div v-if="isTransitioning" class="login-origin-flare login-origin-bloom" :style="originStyle">
      <i v-for="ray in 12" :key="ray" :style="{ '--ray': ray }"></i>
      <b></b>
    </div>

    <div v-if="isTransitioning" class="routing-status">
      <span>{{ phase === 'complete' ? 'ROUTE ESTABLISHED' : 'ESTABLISHING SECURE ROUTE' }}</span>
      <strong>{{ phase === 'complete' ? '身份验证完成，正在进入系统' : '正在建立安全接入链路' }}</strong>
      <i></i>
    </div>
  </main>
</template>
