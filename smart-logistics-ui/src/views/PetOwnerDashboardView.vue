<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'
import OwnerVoiceDialog from '@/components/PetOwner/OwnerVoiceDialog.vue'
import { amapApi, cargoApi, orderExtensionApi, shipperApi } from '@/services/api'
import type { AddressChangeImpactResult, CargoDto, CargoEta, CargoPosition, CargoTimeline, OrderDriverRatingResult, ShipperEnvironment, ShipperNotification } from '@/services/types'
import { useLogisticsStore } from '@/stores/logistics'

const router = useRouter()
const store = useLogisticsStore()
const { user } = storeToRefs(store)
const profileOpen = ref(false)
const voiceDialog = ref<InstanceType<typeof OwnerVoiceDialog> | null>(null)
const loading = ref(false)
const submitting = ref(false)
const orders = ref<CargoDto[]>([])
const selectedId = ref('')
const position = ref<CargoPosition | null>(null)
const eta = ref<CargoEta | null>(null)
const timeline = ref<CargoTimeline | null>(null)
const environmentState = ref<ShipperEnvironment | null>(null)
const notifications = ref<ShipperNotification[]>([])
const ratingState = ref<OrderDriverRatingResult | null>(null)
const orderCenterVisible = ref(false)
const journeyVisible = ref(false)
const alertsVisible = ref(false)
const petProfileVisible = ref(false)
const createVisible = ref(false)
const addressVisible = ref(false)
const ratingVisible = ref(false)
const addressImpact = ref<AddressChangeImpactResult | null>(null)
const impactLoading = ref(false)

const createForm = reactive({
  petName: '', petType: '犬', petBreed: '', petAge: '', petGender: '公', weight: 0,
  origin: '', destination: '', contactName: '', contactPhone: '', receiverName: '', receiverPhone: '', requestNote: '',
})
const addressForm = reactive({ address: '', contactName: '', contactPhone: '', reason: '' })
const ratingForm = reactive({ score: 5, tags: [] as string[], comment: '' })

const statusMeta: Record<string, { label: string; tone: string }> = {
  CREATED: { label: '待受理', tone: 'waiting' },
  LOADED: { label: '已受理', tone: 'accepted' },
  IN_TRANSIT: { label: '运输中', tone: 'transit' },
  DELIVERED: { label: '已送达', tone: 'done' },
  CANCELLED: { label: '已取消', tone: 'cancelled' },
}
const orbitActions = [
  { key: 'tracking', title: '实时追踪', note: '查看位置轨迹', icon: 'LocationFilled', tone: 'blue', position: 'top-left' },
  { key: 'alerts', title: '告警中心', note: '异常主动提醒', icon: 'BellFilled', tone: 'purple', position: 'top-right' },
  { key: 'orders', title: '运输管理', note: '我的运输订单', icon: 'Van', tone: 'green', position: 'mid-left' },
  { key: 'profile', title: '宠物档案', note: '健康与资料管理', icon: 'Postcard', tone: 'orange', position: 'mid-right' },
  { key: 'history', title: '历史记录', note: '运输时间线统计', icon: 'TrendCharts', tone: 'indigo', position: 'bottom-left' },
  { key: 'settings', title: '系统设置', note: '偏好与账户设置', icon: 'Setting', tone: 'cyan', position: 'bottom-right' },
]

const selected = computed(() => orders.value.find((item) => item.cargoId === selectedId.value) || null)
const stats = computed(() => ({
  transit: orders.value.filter((item) => ['LOADED', 'IN_TRANSIT'].includes(item.status)).length,
  delivered: orders.value.filter((item) => item.status === 'DELIVERED').length,
  alert: notifications.value.filter((item) => !['RESOLVED', 'CLOSED'].includes(item.status)).length,
}))
const environment = computed(() => [
  { label: '温度', value: environmentState.value?.temperature != null ? `${environmentState.value.temperature}°C` : '—', icon: 'Thermometer', tone: 'red' },
  { label: '湿度', value: environmentState.value?.humidity != null ? `${environmentState.value.humidity}%` : '—', icon: 'Pouring', tone: 'blue' },
  { label: '空气质量', value: environmentState.value?.airQuality || '—', icon: 'Cloudy', tone: 'green' },
  { label: '震动', value: environmentState.value?.vibration || '—', icon: 'Histogram', tone: 'cyan' },
  { label: '设备状态', value: environmentState.value?.status === 'NO_DATA' ? '暂无数据' : '正常', icon: 'Connection', tone: 'orange' },
])
const recentAlerts = computed(() => notifications.value.slice(0, 3))
const currentLocation = computed(() => position.value?.locationDesc
  || (position.value?.lat != null ? `${Number(position.value.lat).toFixed(5)}, ${Number(position.value.lng).toFixed(5)}` : '等待定位上报'))
const arrivalText = computed(() => eta.value?.eta ? formatClock(eta.value.eta) : '待计算')
const remainingText = computed(() => eta.value?.remainingMinutes != null ? `${Math.floor(eta.value.remainingMinutes / 60)}小时${eta.value.remainingMinutes % 60}分` : '等待实时数据')

function statusOf(order?: CargoDto | null) {
  return order ? (statusMeta[order.status] || { label: order.status, tone: 'waiting' }) : { label: '暂无订单', tone: 'waiting' }
}
function formatTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}
function formatClock(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}
function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

async function loadOrders(preferredId?: string) {
  loading.value = true
  try {
    const page = await cargoApi.list({ page: 1, size: 100 })
    orders.value = page.content || []
    const preferred = orders.value.find((item) => item.cargoId === preferredId)
    const active = orders.value.find((item) => item.status === 'IN_TRANSIT')
      || orders.value.find((item) => item.status === 'LOADED')
      || orders.value.find((item) => item.status === 'CREATED')
      || orders.value[0]
    selectedId.value = (preferred || active)?.cargoId || ''
  } catch (error) {
    ElMessage.error(errorMessage(error, '本人运输订单加载失败'))
  } finally {
    loading.value = false
  }
}

async function loadSelected(order: CargoDto | null) {
  position.value = null; eta.value = null; timeline.value = null; environmentState.value = null; notifications.value = []; ratingState.value = null
  if (!order) return
  const cargoId = order.cargoId
  const results = await Promise.allSettled([
    cargoApi.position(cargoId), cargoApi.eta(cargoId), cargoApi.timeline(cargoId),
    shipperApi.environment(cargoId), shipperApi.notifications(cargoId), orderExtensionApi.driverRating(cargoId),
  ])
  if (selectedId.value !== cargoId) return
  if (results[0].status === 'fulfilled') position.value = results[0].value
  if (results[1].status === 'fulfilled') eta.value = results[1].value
  if (results[2].status === 'fulfilled') timeline.value = results[2].value
  if (results[3].status === 'fulfilled') environmentState.value = results[3].value
  if (results[4].status === 'fulfilled') notifications.value = results[4].value
  if (results[5].status === 'fulfilled') ratingState.value = results[5].value
}

function navigate(page: string) {
  if (page === 'tracking') journeyVisible.value = true
  else if (page === 'alerts') alertsVisible.value = true
  else if (page === 'orders' || page === 'history') orderCenterVisible.value = true
  else if (page === 'profile') petProfileVisible.value = true
  else if (page === 'settings') profileOpen.value = true
}
function selectOrder(order: CargoDto) {
  selectedId.value = order.cargoId
  orderCenterVisible.value = false
  journeyVisible.value = true
}
function startConversation() { void voiceDialog.value?.openAndListen() }
function openCreate() {
  Object.assign(createForm, { petName: '', petType: '犬', petBreed: '', petAge: '', petGender: '公', weight: 0, origin: '', destination: '', contactName: user.value?.name || '', contactPhone: user.value?.phone || '', receiverName: '', receiverPhone: '', requestNote: '' })
  createVisible.value = true
}
async function geocode(address: string) {
  const result = await amapApi.geocode({ address })
  if (result.lat == null || result.lng == null) throw new Error(`无法识别地址：${address}`)
  return { name: result.formattedAddress || address, lat: result.lat, lng: result.lng }
}
async function submitCreate() {
  if (!createForm.petName || !createForm.origin || !createForm.destination || !createForm.contactName || !createForm.contactPhone) return ElMessage.warning('请填写宠物、起点、目的地和联系人必填信息')
  submitting.value = true
  try {
    const [origin, destination] = await Promise.all([geocode(createForm.origin), geocode(createForm.destination)])
    const cargoId = `PET-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${String(Date.now()).slice(-6)}`
    await cargoApi.create({ cargoId, cargoType: createForm.petType, petName: createForm.petName, petBreed: createForm.petBreed, petAge: createForm.petAge, petGender: createForm.petGender, weight: Number(createForm.weight || 0), origin, destination, contactName: createForm.contactName, contactPhone: createForm.contactPhone, receiverName: createForm.receiverName || createForm.contactName, receiverPhone: createForm.receiverPhone || createForm.contactPhone, requestNote: createForm.requestNote })
    createVisible.value = false
    await loadOrders(cargoId)
    ElMessage.success('宠物运输需求已提交，等待平台受理')
  } catch (error) { ElMessage.error(errorMessage(error, '运输需求提交失败')) } finally { submitting.value = false }
}
function openAddressChange() {
  if (!selected.value) return
  Object.assign(addressForm, { address: '', contactName: selected.value.receiverName || selected.value.contactName || '', contactPhone: selected.value.receiverPhone || selected.value.contactPhone || '', reason: '' })
  addressImpact.value = null; addressVisible.value = true
}
async function calculateImpact() {
  if (!selected.value || !addressForm.address) return
  impactLoading.value = true
  try {
    const target = await geocode(addressForm.address)
    addressImpact.value = await orderExtensionApi.addressChangeImpact(selected.value.cargoId, { newAddress: { detail: target.name, lat: target.lat, lng: target.lng } })
  } catch (error) { ElMessage.error(errorMessage(error, '改址影响测算失败')) } finally { impactLoading.value = false }
}
async function submitAddressChange() {
  if (!selected.value || !addressForm.address) return
  submitting.value = true
  try {
    const target = await geocode(addressForm.address)
    await orderExtensionApi.createAddressChange(selected.value.cargoId, { newAddress: { detail: target.name, lat: target.lat, lng: target.lng }, contactName: addressForm.contactName, contactPhone: addressForm.contactPhone, reason: addressForm.reason })
    addressVisible.value = false; ElMessage.success('改址申请已提交审核，不会直接命令司机改道')
  } catch (error) { ElMessage.error(errorMessage(error, '改址申请提交失败')) } finally { submitting.value = false }
}
async function confirmReceipt() {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`确认已安全收到宠物“${selected.value.petName || selected.value.cargoType}”吗？`, '确认签收', { type: 'success' })
    await shipperApi.confirmReceipt(selected.value.cargoId); await loadOrders(selected.value.cargoId); journeyVisible.value = false
    ElMessage.success('已确认收到宠物')
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error, '确认签收失败')) }
}
function openRating() { Object.assign(ratingForm, { score: 5, tags: [], comment: '' }); ratingVisible.value = true }
async function submitRating() {
  if (!selected.value) return
  submitting.value = true
  try {
    await orderExtensionApi.submitDriverRating(selected.value.cargoId, ratingForm); ratingVisible.value = false; await loadSelected(selected.value); ElMessage.success('评价已提交')
  } catch (error) { ElMessage.error(errorMessage(error, '评价提交失败')) } finally { submitting.value = false }
}

watch(selected, (order) => { void loadSelected(order) }, { immediate: true })
onMounted(() => { void loadOrders() })
</script>

<template>
  <div class="pet-owner-dashboard" @click.self="profileOpen = false">
    <header class="owner-header">
      <button class="owner-brand" type="button" aria-label="返回导航窗口" @click="router.push({ name: 'portal' })">
        <span class="brand-symbol"><el-icon><MostlyCloudy /></el-icon><i>+</i></span>
        <span class="brand-copy">
          <strong>伴生云途——智能宠物托运与全程感知平台</strong>
          <small>萌宠运输 · 安心每一程</small>
        </span>
      </button>

      <div class="header-actions">
        <button class="header-chip alert-chip" type="button" @click="navigate('alerts')">
          <el-icon><Bell /></el-icon><strong>告警</strong><b>{{ stats.alert }}</b>
        </button>
        <div class="header-chip weather-chip" aria-label="当前天气">
          <el-icon><PartlyCloudy /></el-icon>
          <span><strong>26°C</strong><small>多云</small></span>
        </div>
        <div class="account-wrap">
          <button class="header-chip account-chip" type="button" @click.stop="profileOpen = !profileOpen">
            <span class="owner-avatar">👤</span>
            <span><strong>{{ user?.name || '宠物主人' }}</strong><small>宠物主人</small></span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <transition name="profile-pop">
            <div v-if="profileOpen" class="profile-menu">
              <button type="button" @click="navigate('profile')"><el-icon><Postcard /></el-icon>宠物档案</button>
              <button type="button" @click="navigate('settings')"><el-icon><Setting /></el-icon>账户设置</button>
              <button type="button" @click="router.push({ name: 'portal' })"><el-icon><Grid /></el-icon>返回原平台</button>
            </div>
          </transition>
        </div>
      </div>
    </header>

    <main class="owner-content">
      <aside class="side-column left-column">
        <section class="glass-panel overview-panel">
          <div class="panel-heading"><h2>今日运输概览</h2><span>更新时间 10:30:45</span></div>
          <div class="metric-row">
            <article><strong class="blue">{{ stats.transit }}</strong><span>运输中</span></article>
            <article><strong class="green">{{ stats.delivered }}</strong><span>已送达</span></article>
            <article><strong class="red">{{ stats.alert }}</strong><span>异常告警</span></article>
          </div>
          <div class="pet-journey-card" role="button" tabindex="0" @click="navigate('tracking')" @keydown.enter="navigate('tracking')">
            <h3>我的宠物</h3>
            <div v-if="selected" class="pet-summary">
              <span class="pet-avatar">{{ selected.cargoType === '猫' ? '🐱' : '🐶' }}</span>
              <span><strong>{{ selected.petName || '待补充宠物名' }}</strong><small>{{ selected.petBreed || selected.cargoType }} · {{ selected.petAge || '年龄待补充' }} · {{ selected.petGender || '性别待补充' }}</small></span>
              <b>{{ statusOf(selected).label }}</b>
            </div>
            <div v-else class="pet-summary empty-summary">还没有宠物运输订单</div>
            <div class="journey-foot"><span>{{ selected?.vehiclePlate || '待分配车辆' }}</span><strong>预计 {{ arrivalText }} 到达</strong></div>
          </div>
        </section>

        <section class="glass-panel recent-panel">
          <div class="panel-heading"><h2>最近告警</h2><button type="button" @click="navigate('alerts')">更多 <el-icon><ArrowRight /></el-icon></button></div>
          <button v-for="item in recentAlerts" :key="item.alertId" class="alert-row" type="button" @click="navigate('alerts')">
            <span class="alert-symbol" :class="item.severity.toLowerCase()"><el-icon><WarningFilled /></el-icon></span>
            <span><strong>{{ item.title }}</strong><small>{{ selected?.vehiclePlate || selected?.cargoId }} | {{ formatClock(item.triggeredAt) }}</small></span>
            <b :class="!['RESOLVED','CLOSED'].includes(item.status) ? 'pending' : ''">{{ ['RESOLVED','CLOSED'].includes(item.status) ? '已处理' : '待关注' }}</b>
          </button>
          <div v-if="!recentAlerts.length" class="owner-safe-empty"><el-icon><CircleCheckFilled /></el-icon>当前没有风险通知</div>
        </section>
      </aside>

      <section class="assistant-stage" aria-label="羊小智宠物运输助手">
        <div class="radar-ring ring-one"></div>
        <div class="radar-ring ring-two"></div>
        <div class="radar-ring ring-three"></div>
        <span class="radar-line line-a"></span><span class="radar-line line-b"></span>
        <span class="radar-line line-c"></span><span class="radar-line line-d"></span>

        <button
          v-for="item in orbitActions"
          :key="item.title"
          class="orbit-action"
          :class="[item.position, item.tone]"
          type="button"
          @click="navigate(item.key)"
        >
          <span class="orbit-icon"><el-icon><component :is="item.icon" /></el-icon></span>
          <strong>{{ item.title }}</strong><small>{{ item.note }}</small>
        </button>

        <div class="assistant-message">
          <strong>嗨~ 我是<span>智慧小羊</span></strong>
          <p>有什么可以帮您的吗？</p>
        </div>
        <div class="assistant-core">
          <span class="core-glow"></span>
          <img src="@/assets/iot-sheep-pet.png" alt="伴生云途宠物运输助手羊小智" />
        </div>
        <button class="talk-button" type="button" @click="startConversation">
          <el-icon><ChatDotRound /></el-icon><span>点击对话</span>
        </button>
      </section>

      <aside class="side-column right-column">
        <section class="glass-panel transport-panel">
          <div class="panel-heading"><h2>运输状态</h2><button type="button" @click="navigate('tracking')">更多 <el-icon><ArrowRight /></el-icon></button></div>
          <button class="transport-card" type="button" @click="navigate('tracking')">
            <div class="transport-head"><strong>{{ selected?.vehiclePlate || selected?.cargoId || '暂无运输订单' }}</strong><b>{{ statusOf(selected).label }}</b></div>
            <div class="route-line"><strong>{{ selected?.origin?.name || '待填写' }}</strong><span><i></i><el-icon><Right /></el-icon></span><strong>{{ selected?.destination?.name || '待填写' }}</strong></div>
            <small>{{ currentLocation }}</small>
            <div class="truck-scene">
              <span class="tree tree-one"></span><span class="tree tree-two"></span>
              <span class="road"></span><el-icon class="truck"><Van /></el-icon>
            </div>
            <div class="arrival"><span><small>预计到达</small><strong>{{ arrivalText }}</strong></span><p>剩余 <b>{{ remainingText }}</b></p></div>
          </button>
        </section>

        <section class="glass-panel environment-panel">
          <div class="panel-heading"><h2>环境监测</h2><button type="button" @click="navigate('tracking')">更多 <el-icon><ArrowRight /></el-icon></button></div>
          <div class="environment-list">
            <div v-for="item in environment" :key="item.label" class="environment-row">
              <el-icon :class="item.tone"><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span><strong>{{ item.value }}</strong><el-icon class="ok"><CircleCheckFilled /></el-icon>
            </div>
          </div>
        </section>
      </aside>
    </main>

    <el-dialog v-model="orderCenterVisible" title="我的宠物运输订单" width="820px" class="owner-data-dialog">
      <div class="dialog-toolbar"><span>只显示当前账号提交的订单</span><el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>提交运输需求</el-button></div>
      <div v-loading="loading" class="owner-order-list">
        <button v-for="order in orders" :key="order.cargoId" type="button" :class="{ active: order.cargoId === selectedId }" @click="selectOrder(order)">
          <span class="dialog-pet-icon">{{ order.cargoType === '猫' ? '🐱' : '🐶' }}</span>
          <span><strong>{{ order.petName || order.cargoType }}</strong><small>{{ order.cargoId }} · {{ order.origin?.name }} → {{ order.destination?.name }}</small></span>
          <em :class="statusOf(order).tone">{{ statusOf(order).label }}</em>
          <time>{{ formatTime(order.createdAt) }}</time>
        </button>
        <el-empty v-if="!loading && !orders.length" description="还没有运输订单" />
      </div>
    </el-dialog>

    <el-dialog v-model="journeyVisible" :title="`${selected?.petName || '宠物'}的运输旅程`" width="760px" class="owner-data-dialog">
      <div v-if="selected" class="journey-dialog-content">
        <div class="journey-route"><span><small>起点</small><strong>{{ selected.origin?.name }}</strong></span><i><el-icon><Van /></el-icon></i><span><small>目的地</small><strong>{{ selected.destination?.name }}</strong></span></div>
        <div class="journey-live-grid">
          <article><small>当前位置</small><strong>{{ currentLocation }}</strong></article><article><small>车辆 / 司机</small><strong>{{ selected.vehiclePlate || '待分配' }} · {{ selected.driverName || '待分配' }}</strong></article><article><small>预计到达</small><strong>{{ arrivalText }}</strong></article>
        </div>
        <div class="dialog-section-title">完整运输时间线</div>
        <div class="owner-timeline"><div v-for="event in timeline?.events || []" :key="`${event.time}-${event.title}`"><i></i><span><strong>{{ event.title }}</strong><small>{{ event.description }}</small></span><time>{{ formatTime(event.time) }}</time></div><p v-if="!timeline?.events?.length">订单受理后会持续记录装车、运输、照护与签收节点。</p></div>
        <div class="permission-note"><el-icon><Lock /></el-icon>改址只提交审核申请，不会直接命令司机改道。</div>
        <div class="dialog-actions"><el-button v-if="['LOADED','IN_TRANSIT'].includes(selected.status)" @click="openAddressChange">申请改址</el-button><el-button v-if="['LOADED','IN_TRANSIT'].includes(selected.status)" type="success" @click="confirmReceipt">确认收到宠物</el-button><el-button v-if="selected.status === 'DELIVERED' && !ratingState?.rated" type="primary" @click="openRating">评价服务</el-button></div>
      </div>
    </el-dialog>

    <el-dialog v-model="alertsVisible" title="订单风险通知" width="620px" class="owner-data-dialog">
      <div class="owner-alert-list"><article v-for="item in notifications" :key="item.alertId"><span><el-icon><WarningFilled /></el-icon></span><div><strong>{{ item.title }}</strong><p>{{ item.summary || item.alertType }}</p><small>{{ formatTime(item.triggeredAt) }}</small></div><b>{{ ['RESOLVED','CLOSED'].includes(item.status) ? '已处理' : '请关注' }}</b></article><el-empty v-if="!notifications.length" description="当前没有风险通知" /></div>
      <div class="permission-note"><el-icon><Lock /></el-icon>货主只接收本人订单通知，不能关闭或处置系统告警。</div>
    </el-dialog>

    <el-dialog v-model="petProfileVisible" title="宠物运输档案" width="560px" class="owner-data-dialog">
      <div v-if="selected" class="pet-profile-card"><span>{{ selected.cargoType === '猫' ? '🐱' : '🐶' }}</span><div><h3>{{ selected.petName || '待补充宠物名' }}</h3><p>{{ selected.cargoType }} · {{ selected.petBreed || '品种待补充' }} · {{ selected.petAge || '年龄待补充' }} · {{ selected.petGender || '性别待补充' }}</p><small>照护说明：{{ selected.requestNote || '暂无特殊照护说明' }}</small></div></div>
    </el-dialog>

    <el-dialog v-model="createVisible" title="提交宠物运输需求" width="680px" class="owner-data-dialog" destroy-on-close>
      <el-form label-position="top"><div class="owner-form-grid three"><el-form-item label="宠物名字 *"><el-input v-model="createForm.petName" /></el-form-item><el-form-item label="宠物类型"><el-select v-model="createForm.petType"><el-option label="犬" value="犬" /><el-option label="猫" value="猫" /><el-option label="其他" value="其他宠物" /></el-select></el-form-item><el-form-item label="体重（kg）"><el-input-number v-model="createForm.weight" :min="0" :max="100" /></el-form-item></div><div class="owner-form-grid three"><el-form-item label="品种"><el-input v-model="createForm.petBreed" /></el-form-item><el-form-item label="年龄"><el-input v-model="createForm.petAge" /></el-form-item><el-form-item label="性别"><el-radio-group v-model="createForm.petGender"><el-radio-button label="公" /><el-radio-button label="母" /></el-radio-group></el-form-item></div><div class="owner-form-grid"><el-form-item label="起点 *"><el-input v-model="createForm.origin" /></el-form-item><el-form-item label="目的地 *"><el-input v-model="createForm.destination" /></el-form-item><el-form-item label="联系人 *"><el-input v-model="createForm.contactName" /></el-form-item><el-form-item label="联系电话 *"><el-input v-model="createForm.contactPhone" /></el-form-item><el-form-item label="收件人"><el-input v-model="createForm.receiverName" /></el-form-item><el-form-item label="收件电话"><el-input v-model="createForm.receiverPhone" /></el-form-item></div><el-form-item label="宠物照护说明"><el-input v-model="createForm.requestNote" type="textarea" :rows="3" /></el-form-item></el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCreate">提交需求</el-button></template>
    </el-dialog>

    <el-dialog v-model="addressVisible" title="提交目的地改址申请" width="600px" class="owner-data-dialog"><el-alert title="只提交调度审核，不会直接命令司机改道。" type="warning" :closable="false" show-icon /><el-form label-position="top" style="margin-top:16px"><el-form-item label="新目的地"><el-input v-model="addressForm.address" /></el-form-item><div class="owner-form-grid"><el-form-item label="新收件人"><el-input v-model="addressForm.contactName" /></el-form-item><el-form-item label="联系电话"><el-input v-model="addressForm.contactPhone" /></el-form-item></div><el-form-item label="改址原因"><el-input v-model="addressForm.reason" type="textarea" :rows="3" /></el-form-item></el-form><div v-if="addressImpact" class="impact-result">影响等级 {{ addressImpact.impactLevel }} · 增加 {{ addressImpact.extraDistanceKm }} km · 预计延误 {{ addressImpact.estimatedDelayMinutes }} 分钟</div><template #footer><el-button :loading="impactLoading" @click="calculateImpact">测算影响</el-button><el-button type="primary" :loading="submitting" @click="submitAddressChange">提交审核</el-button></template></el-dialog>

    <el-dialog v-model="ratingVisible" title="评价司机与运输服务" width="520px" class="owner-data-dialog"><el-form label-position="top"><el-form-item label="综合评分"><el-rate v-model="ratingForm.score" show-text /></el-form-item><el-form-item label="服务标签"><el-checkbox-group v-model="ratingForm.tags"><el-checkbox-button label="准时送达" /><el-checkbox-button label="沟通及时" /><el-checkbox-button label="照护细致" /><el-checkbox-button label="宠物状态良好" /></el-checkbox-group></el-form-item><el-form-item label="评价内容"><el-input v-model="ratingForm.comment" type="textarea" :rows="4" /></el-form-item></el-form><template #footer><el-button @click="ratingVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitRating">提交评价</el-button></template></el-dialog>

    <OwnerVoiceDialog ref="voiceDialog" :cargo-id="selected?.cargoId" :pet-name="selected?.petName || '我的宠物'" />
  </div>
</template>

<style scoped>
.pet-owner-dashboard {
  min-height: 100vh;
  overflow: hidden;
  color: #102a61;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  background:
    radial-gradient(circle at 50% 46%, rgba(103, 190, 255, .18), transparent 34%),
    linear-gradient(rgba(72, 147, 255, .035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(72, 147, 255, .035) 1px, transparent 1px),
    linear-gradient(145deg, #fbfdff 0%, #eef6ff 52%, #f9fcff 100%);
  background-size: auto, 40px 40px, 40px 40px, auto;
}

.owner-header { height: 94px; display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 0 32px; border-bottom: 1px solid #d5e7fb; background: rgba(255, 255, 255, .72); box-shadow: 0 8px 28px rgba(61, 112, 173, .08); backdrop-filter: blur(18px); }
button { font: inherit; }
.owner-brand { display: flex; align-items: center; gap: 14px; color: inherit; text-align: left; background: transparent; }
.brand-symbol { position: relative; width: 55px; height: 55px; display: grid; place-items: center; border: 3px solid #2f9df4; border-radius: 18px; color: #25b7ef; background: #fff; box-shadow: 0 7px 18px rgba(38, 143, 233, .18), inset 0 0 0 4px #eaf7ff; }
.brand-symbol .el-icon { font-size: 32px; }
.brand-symbol i { position: absolute; left: 20px; top: 16px; color: #1778f2; font-size: 18px; font-weight: 900; font-style: normal; }
.brand-copy { display: flex; flex-direction: column; }
.brand-copy strong { color: #0b2251 !important; font-size: 24px; font-weight: 800; }
.brand-copy small { margin-top: 5px; color: #3685df !important; font-size: 14px; font-weight: 600; }
.header-actions { display: flex; align-items: center; gap: 16px; }
.header-chip { height: 56px; display: flex; align-items: center; gap: 10px; padding: 0 18px; border: 1px solid #c8def8; border-radius: 16px; color: #17366d; background: rgba(255,255,255,.74); box-shadow: 0 7px 20px rgba(51, 110, 182, .09); }
.header-chip:hover { border-color: #7bbcff; transform: translateY(-1px); box-shadow: 0 10px 24px rgba(51, 110, 182, .15); }
.header-chip > .el-icon { color: #3562a8; font-size: 22px; }
.header-chip strong { color: #17366d !important; font-size: 15px; }
.header-chip small { display: block; margin-top: 3px; color: #657da5 !important; font-size: 12px; }
.alert-chip b { width: 22px; height: 22px; display: grid; place-items: center; border-radius: 50%; color: #fff; background: #f26756; font-size: 12px; }
.weather-chip > .el-icon { color: #ffb82e; font-size: 31px; }
.weather-chip span, .account-chip > span:nth-child(2) { text-align: left; }
.account-wrap { position: relative; }
.account-chip { min-width: 168px; }
.owner-avatar { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 50%; background: #f9e6d7; font-size: 23px; }
.account-chip > .el-icon { margin-left: auto; font-size: 14px; }
.profile-menu { position: absolute; right: 0; top: 64px; z-index: 20; width: 180px; padding: 7px; border: 1px solid #c8def8; border-radius: 12px; background: rgba(255,255,255,.96); box-shadow: 0 18px 42px rgba(40, 83, 139, .18); backdrop-filter: blur(16px); }
.profile-menu button { width: 100%; display: flex; align-items: center; gap: 10px; padding: 10px 11px; border-radius: 8px; color: #38537a; background: transparent; text-align: left; }
.profile-menu button:hover { color: #176fe8; background: #edf6ff; }
.profile-pop-enter-active, .profile-pop-leave-active { transition: opacity .16s ease, transform .16s ease; }
.profile-pop-enter-from, .profile-pop-leave-to { opacity: 0; transform: translateY(-6px); }

.owner-content { min-height: calc(100vh - 94px); display: grid; grid-template-columns: minmax(258px, 296px) minmax(510px, 1fr) minmax(270px, 304px); gap: 22px; padding: 22px 24px 26px; }
.side-column { min-width: 0; display: grid; grid-template-rows: minmax(310px, .94fr) minmax(278px, .86fr); gap: 22px; }
.glass-panel { overflow: hidden; padding: 16px; border: 1px solid #c4dcf7; border-radius: 16px; background: rgba(255,255,255,.64); box-shadow: 0 12px 30px rgba(49, 104, 172, .08), inset 0 1px 0 rgba(255,255,255,.9); backdrop-filter: blur(14px); }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.panel-heading h2 { margin: 0; color: #102f6e !important; font-size: 15px; font-weight: 800; }
.panel-heading > span { color: #6f85a8 !important; font-size: 11px; }
.panel-heading button { display: inline-flex; align-items: center; gap: 2px; color: #6380a9; background: transparent; font-size: 11px; }
.panel-heading button:hover { color: #1675ec; }
.metric-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.metric-row article { min-height: 82px; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 1px solid #e0ebf8; border-radius: 11px; background: rgba(255,255,255,.72); box-shadow: 0 6px 17px rgba(47, 103, 169, .05); }
.metric-row strong { font-size: 27px; line-height: 1; }.metric-row span { margin-top: 11px; color: #263f68 !important; font-size: 12px; }.blue { color: #217bf2 !important; }.green { color: #20ab53 !important; }.red { color: #ef5946 !important; }
.pet-journey-card { margin-top: 12px; padding: 12px; border: 1px solid #d5e7f9; border-radius: 11px; background: rgba(255,255,255,.72); cursor: pointer; transition: border-color .18s ease, transform .18s ease; }
.pet-journey-card:hover { border-color: #74b8fb; transform: translateY(-1px); }
.pet-journey-card h3 { margin: 0 0 10px; color: #17396f !important; font-size: 12px; }
.pet-summary { display: flex; align-items: center; gap: 10px; }
.pet-avatar { width: 44px; height: 44px; display: grid; place-items: center; border-radius: 50%; background: #f0dfcb; font-size: 29px; }
.pet-summary > span:nth-child(2) { min-width: 0; display: flex; flex: 1; flex-direction: column; }.pet-summary strong { color: #183765 !important; font-size: 13px; }.pet-summary small { margin-top: 4px; color: #6e83a3 !important; font-size: 11px; }.pet-summary b { padding: 4px 8px; border-radius: 10px; color: #1472df; background: #e8f3ff; font-size: 10px; }
.journey-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; padding-top: 9px; border-top: 1px solid #e4edf8; }.journey-foot span { color: #2a4670 !important; font-size: 11px; }.journey-foot strong { color: #1fa352 !important; font-size: 11px; }
.recent-panel { padding-bottom: 8px; }
.alert-row { width: 100%; display: grid; grid-template-columns: 36px minmax(0,1fr) auto; align-items: center; gap: 9px; padding: 11px 0; border-top: 1px solid #e4edf8; color: inherit; background: transparent; text-align: left; }
.alert-row:hover > span:nth-child(2) strong { color: #176fe8 !important; }.alert-symbol { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 50%; color: #ec5c4d; background: #ffe9e6; }.alert-symbol.info { color: #397fe8; background: #e8f2ff; }.alert-row > span:nth-child(2) { min-width: 0; display: flex; flex-direction: column; }.alert-row strong { color: #263f68 !important; font-size: 12px; }.alert-row small { margin-top: 4px; overflow: hidden; color: #7589a6 !important; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.alert-row b { padding: 4px 7px; border: 1px solid #cadbef; border-radius: 9px; color: #6680a4; font-size: 9px; }.alert-row b.pending { border-color: #ffb0a5; color: #f05b4a; background: #fff5f3; }

.assistant-stage { position: relative; min-height: 626px; overflow: visible; border-radius: 46% 46% 40% 40%; background: radial-gradient(circle at 50% 49%, rgba(255,255,255,.98) 0 18%, rgba(210,237,255,.76) 39%, rgba(188,224,255,.28) 56%, transparent 70%); }
.radar-ring { position: absolute; left: 50%; top: 51%; border: 1px solid rgba(86, 167, 245, .24); border-radius: 50%; transform: translate(-50%, -50%); box-shadow: inset 0 0 32px rgba(68, 164, 255, .08), 0 0 26px rgba(80, 171, 255, .07); }.ring-one { width: 270px; height: 270px; }.ring-two { width: 430px; height: 430px; }.ring-three { width: 570px; height: 570px; border-style: dashed; animation: radar-spin 28s linear infinite; }
.radar-line { position: absolute; left: 50%; top: 51%; width: 250px; height: 1px; background: linear-gradient(90deg, rgba(73,166,244,.55), transparent); transform-origin: left center; }.line-a { transform: rotate(0deg); }.line-b { transform: rotate(90deg); }.line-c { transform: rotate(180deg); }.line-d { transform: rotate(270deg); }
.assistant-core { position: absolute; left: 50%; top: 51%; width: 290px; height: 370px; display: flex; align-items: center; justify-content: center; transform: translate(-50%, -46%); }.assistant-core img { position: relative; z-index: 2; width: 100%; height: 100%; object-fit: contain; filter: drop-shadow(0 18px 22px rgba(38, 107, 182, .17)); animation: pet-float 4s ease-in-out infinite; }.core-glow { position: absolute; inset: 18% 5% 10%; border-radius: 50%; background: rgba(91, 189, 255, .2); filter: blur(26px); }
.assistant-message { position: absolute; left: 50%; top: 76px; z-index: 4; min-width: 208px; padding: 14px 20px; border: 1px solid #bddcff; border-radius: 18px; color: #203d6b; background: rgba(255,255,255,.9); box-shadow: 0 10px 24px rgba(47, 107, 179, .12); text-align: center; transform: translateX(-50%); }.assistant-message::after { content: ''; position: absolute; left: 50%; bottom: -9px; width: 16px; height: 16px; border-right: 1px solid #bddcff; border-bottom: 1px solid #bddcff; background: #fff; transform: translateX(-50%) rotate(45deg); }.assistant-message strong { color: #15366b !important; font-size: 16px; }.assistant-message strong span { margin-left: 5px; color: #1877ec !important; }.assistant-message p { margin: 5px 0 0; color: #425c81 !important; font-size: 12px; }
.orbit-action { position: absolute; z-index: 5; width: 130px; height: 144px; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 1px solid rgba(181, 215, 247, .86); border-radius: 50%; color: #173970; background: rgba(255,255,255,.75); box-shadow: 0 14px 30px rgba(50, 106, 173, .10), inset 0 0 24px rgba(128, 201, 255, .12); backdrop-filter: blur(10px); transition: transform .2s ease, box-shadow .2s ease; }.orbit-action:hover { transform: translateY(-4px) scale(1.02); box-shadow: 0 19px 35px rgba(50, 106, 173, .18), inset 0 0 24px rgba(128, 201, 255, .18); }.orbit-icon { width: 61px; height: 61px; display: grid; place-items: center; margin-bottom: 7px; border-radius: 50%; color: #fff; box-shadow: inset 0 2px 4px rgba(255,255,255,.35), 0 8px 18px currentColor; }.orbit-icon .el-icon { font-size: 30px; }.orbit-action strong { color: #163a75 !important; font-size: 14px; }.orbit-action small { margin-top: 4px; color: #627b9e !important; font-size: 9px; }.orbit-action.blue .orbit-icon { background: linear-gradient(145deg,#5fcfff,#1688f2); color: rgba(45,154,244,.24); }.orbit-action.purple .orbit-icon { background: linear-gradient(145deg,#a98aff,#6949ed); color: rgba(119,76,239,.23); }.orbit-action.green .orbit-icon { background: linear-gradient(145deg,#61df9f,#17ae67); color: rgba(28,181,105,.22); }.orbit-action.orange .orbit-icon { background: linear-gradient(145deg,#ffc464,#f59024); color: rgba(246,151,38,.22); }.orbit-action.indigo .orbit-icon { background: linear-gradient(145deg,#70a8ff,#3b66e8); color: rgba(62,104,232,.22); }.orbit-action.cyan .orbit-icon { background: linear-gradient(145deg,#5de0e8,#14aabd); color: rgba(25,178,194,.22); }
.top-left { left: 15px; top: 28px; }.top-right { right: 15px; top: 28px; }.mid-left { left: -10px; top: 265px; }.mid-right { right: -10px; top: 265px; }.bottom-left { left: 48px; bottom: 9px; }.bottom-right { right: 48px; bottom: 9px; }
.talk-button { position: absolute; left: 50%; bottom: 61px; z-index: 7; min-width: 170px; height: 48px; display: flex; align-items: center; justify-content: center; gap: 8px; border-radius: 24px; color: #fff; background: linear-gradient(135deg, #54b5ff, #346cf0); box-shadow: 0 12px 24px rgba(49, 111, 237, .28), inset 0 1px 0 rgba(255,255,255,.4); transform: translateX(-50%); }.talk-button:hover { filter: brightness(1.06); }.talk-button .el-icon { font-size: 21px; }.talk-button span { color: #fff !important; font-size: 15px; font-weight: 700; }

.transport-card { width: 100%; min-height: 255px; display: flex; flex-direction: column; padding: 16px; border: 1px solid #c8def8; border-radius: 13px; color: inherit; background: linear-gradient(145deg, rgba(255,255,255,.85), rgba(228,242,255,.75)); text-align: left; }.transport-card:hover { border-color: #78b9f7; }.transport-head, .route-line, .arrival { display: flex; align-items: center; justify-content: space-between; }.transport-head strong { color: #142e61 !important; font-size: 15px; }.transport-head b { padding: 5px 9px; border-radius: 10px; color: #2474e8; background: #e5f0ff; font-size: 10px; }.route-line { margin-top: 17px; }.route-line strong { color: #1d3968 !important; font-size: 13px; }.route-line span { flex: 1; display: flex; align-items: center; margin: 0 12px; color: #2c79ef; }.route-line i { flex: 1; height: 2px; background: linear-gradient(90deg, #b4dcff, #2d83f4); }.transport-card > small { margin-top: 7px; color: #7085a4 !important; font-size: 10px; }.truck-scene { position: relative; height: 82px; margin: 8px -16px 0; overflow: hidden; background: linear-gradient(#f8fcff 55%, #dff3eb 56%, #e8f2fb 78%); }.road { position: absolute; left: 0; right: 0; bottom: 13px; height: 2px; background: repeating-linear-gradient(90deg,#9dc4e8 0 18px,transparent 18px 35px); }.truck { position: absolute; left: 47%; bottom: 15px; color: #1c7dd7; font-size: 53px; filter: drop-shadow(0 6px 5px rgba(27,90,143,.18)); }.tree { position: absolute; bottom: 23px; width: 7px; height: 35px; border-radius: 7px; background: #6cc596; }.tree::before { content: ''; position: absolute; left: -7px; top: -8px; width: 21px; height: 25px; border-radius: 50%; background: #77d4a0; }.tree-one { left: 12px; }.tree-two { right: 16px; transform: scale(.8); }.arrival { margin-top: auto; padding-top: 9px; }.arrival > span { display: flex; flex-direction: column; }.arrival small { color: #6b82a3 !important; font-size: 10px; }.arrival strong { margin-top: 2px; color: #276de2 !important; font-size: 23px; }.arrival p { margin: 15px 0 0; color: #647b9e !important; font-size: 10px; }.arrival p b { color: #223d68; font-size: 12px; }
.environment-list { display: flex; flex-direction: column; gap: 7px; }.environment-row { display: grid; grid-template-columns: 25px 1fr auto 18px; align-items: center; gap: 5px; min-height: 34px; }.environment-row > .el-icon:first-child { font-size: 17px; }.environment-row span { color: #30496f !important; font-size: 12px; }.environment-row strong { color: #2d4367 !important; font-size: 11px; }.environment-row .ok { color: #26ae5d; font-size: 13px; }.environment-row .red { color: #e45b50 !important; }.environment-row .blue { color: #2f8df4 !important; }.environment-row .green { color: #25ae63 !important; }.environment-row .cyan { color: #28a9d5 !important; }.environment-row .orange { color: #f4a51e !important; }
.empty-summary,.owner-safe-empty{min-height:54px;display:flex;align-items:center;justify-content:center;color:#7086a6;font-size:11px}.owner-safe-empty{gap:7px;border-top:1px solid #e4edf8}.owner-safe-empty .el-icon{color:#25ae63;font-size:18px}
:global(.owner-data-dialog){border:1px solid #bcd9fa;border-radius:20px!important;background:linear-gradient(145deg,#fff,#f2f8ff)!important;box-shadow:0 24px 70px rgba(41,91,157,.2)!important}:global(.owner-data-dialog .el-dialog__title){color:#15386c;font-weight:800}:global(.owner-data-dialog .el-dialog__body){color:#294a75}.dialog-toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:13px;color:#7890ae;font-size:12px}.owner-order-list{display:flex;max-height:480px;flex-direction:column;gap:8px;overflow:auto}.owner-order-list>button{display:grid;grid-template-columns:48px minmax(0,1fr) auto auto;align-items:center;gap:12px;padding:13px;border:1px solid #d7e7f7;border-radius:13px;color:#28476f;background:#fff;text-align:left}.owner-order-list>button:hover,.owner-order-list>button.active{border-color:#78b9fb;background:#f0f7ff}.dialog-pet-icon{width:44px;height:44px;display:grid;place-items:center;border-radius:50%;background:#eef6ff;font-size:27px}.owner-order-list>button>span:nth-child(2){display:flex;min-width:0;flex-direction:column}.owner-order-list strong{color:#173c72;font-size:13px}.owner-order-list small{overflow:hidden;margin-top:5px;color:#758aa8;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.owner-order-list em{padding:5px 8px;border-radius:10px;font-size:10px;font-style:normal}.owner-order-list em.waiting{color:#a46d19;background:#fff0d1}.owner-order-list em.accepted,.owner-order-list em.transit{color:#176fdb;background:#e5f1ff}.owner-order-list em.done{color:#1b9860;background:#e5f7ef}.owner-order-list em.cancelled{color:#7e8998;background:#edf0f3}.owner-order-list time{color:#91a1b6;font-size:9px}.journey-route{display:grid;grid-template-columns:1fr 130px 1fr;align-items:center;gap:16px;padding:18px;border-radius:14px;background:#eef6ff}.journey-route>span{display:flex;min-width:0;flex-direction:column}.journey-route>span:last-child{text-align:right}.journey-route small{color:#8498b4;font-size:10px}.journey-route strong{overflow:hidden;margin-top:5px;color:#173f75;text-overflow:ellipsis;white-space:nowrap}.journey-route>i{position:relative;height:2px;background:#71aef4}.journey-route>i .el-icon{position:absolute;left:50%;top:50%;width:36px;height:36px;border-radius:50%;color:#fff;background:#2d82ed;transform:translate(-50%,-50%)}.journey-live-grid{display:grid;grid-template-columns:1.4fr 1fr .7fr;gap:9px;margin-top:12px}.journey-live-grid article{display:flex;min-width:0;flex-direction:column;padding:12px;border:1px solid #e0ebf7;border-radius:11px;background:#fff}.journey-live-grid small{color:#8496ae;font-size:9px}.journey-live-grid strong{overflow:hidden;margin-top:5px;color:#284c77;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.dialog-section-title{margin:18px 0 8px;color:#173d72;font-size:13px;font-weight:800}.owner-timeline{max-height:230px;overflow:auto}.owner-timeline>div{display:grid;grid-template-columns:13px 1fr auto;gap:9px;padding:10px 3px;border-top:1px solid #e8eff8}.owner-timeline i{width:8px;height:8px;margin-top:4px;border-radius:50%;background:#3a8df1;box-shadow:0 0 0 4px #e4f1ff}.owner-timeline span{display:flex;flex-direction:column}.owner-timeline strong{color:#315278;font-size:11px}.owner-timeline small{margin-top:3px;color:#8092a9;font-size:9px}.owner-timeline time{color:#94a3b5;font-size:9px}.owner-timeline>p{text-align:center;color:#8193ab;font-size:11px}.permission-note{display:flex;align-items:center;gap:7px;margin-top:13px;padding:10px 12px;border:1px solid #c8def8;border-radius:10px;color:#50739b;background:#eff7ff;font-size:10px}.dialog-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:13px}.owner-alert-list{display:flex;max-height:430px;flex-direction:column;gap:8px;overflow:auto}.owner-alert-list article{display:grid;grid-template-columns:38px 1fr auto;align-items:center;gap:11px;padding:12px;border:1px solid #e0eaf6;border-radius:12px;background:#fff}.owner-alert-list article>span{width:36px;height:36px;display:grid;place-items:center;border-radius:50%;color:#f26756;background:#ffebe8}.owner-alert-list article p{margin:4px 0;color:#66809e;font-size:11px}.owner-alert-list article small{color:#91a0b2;font-size:9px}.owner-alert-list article b{color:#e85e50;font-size:10px}.pet-profile-card{display:flex;align-items:center;gap:17px;padding:20px;border:1px solid #d7e6f6;border-radius:16px;background:#fff}.pet-profile-card>span{width:80px;height:80px;display:grid;place-items:center;border-radius:50%;background:#edf6ff;font-size:50px}.pet-profile-card h3{margin:0;color:#173c71;font-size:22px}.pet-profile-card p{margin:7px 0;color:#577393}.pet-profile-card small{color:#8294aa}.owner-form-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.owner-form-grid.three{grid-template-columns:1fr 1fr 1fr}.impact-result{padding:11px;border-radius:10px;color:#8c641d;background:#fff5df;font-size:11px}

@keyframes pet-float { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-8px); } }
@keyframes radar-spin { to { transform: translate(-50%, -50%) rotate(360deg); } }

:global(.app-shell.pet-owner-mode) { display: block !important; width: 100% !important; min-width: 0 !important; color: #102a61 !important; background: #f4f9ff !important; }
:global(.app-shell.pet-owner-mode::before) { display: none !important; }
:global(.app-shell.pet-owner-mode .workspace), :global(.app-shell.pet-owner-mode .page-content) { width: 100% !important; min-width: 0 !important; min-height: 100vh !important; padding: 0 !important; color: #102a61 !important; background: transparent !important; }
:global(.app-shell.pet-owner-mode .voice-assistant-button) { display: none !important; }

@media (max-width: 1120px) {
  .owner-content { grid-template-columns: minmax(480px, 1.4fr) minmax(270px, .7fr); }
  .assistant-stage { grid-column: 1; grid-row: 1 / span 2; }
  .left-column { grid-column: 2; grid-row: 1; }.right-column { grid-column: 2; grid-row: 2; }
  .side-column { grid-template-rows: auto auto; }
  .overview-panel, .transport-panel { min-height: 310px; }
  .recent-panel, .environment-panel { min-height: 270px; }
  .header-actions { gap: 8px; }.header-chip { padding: 0 12px; }.weather-chip { display: none; }
}

@media (max-width: 820px) {
  .pet-owner-dashboard { overflow-x: hidden; overflow-y: visible; }
  .owner-header { height: auto; min-height: 86px; padding: 14px 18px; }
  .brand-symbol { width: 46px; height: 46px; border-radius: 14px; }.brand-copy strong { font-size: 18px; }.brand-copy small { font-size: 11px; }
  .alert-chip { width: 48px; padding: 0; justify-content: center; }.alert-chip strong { display: none; }.account-chip { min-width: 0; width: 48px; padding: 0; justify-content: center; }.account-chip > span:nth-child(2), .account-chip > .el-icon { display: none; }
  .owner-content { display: flex; flex-direction: column; padding: 14px; }
  .assistant-stage { order: -1; min-height: 610px; }
  .left-column, .right-column { display: grid; grid-template-columns: 1fr 1fr; }
}

@media (max-width: 620px) {
  .owner-header { gap: 8px; }.brand-copy strong { font-size: 16px; }.brand-copy small { display: none; }.brand-symbol { width: 42px; height: 42px; }.header-actions { gap: 6px; }.header-chip { height: 46px; }
  .assistant-stage { min-height: 580px; margin: 0 -8px; overflow: hidden; }.ring-three { width: 500px; height: 500px; }.ring-two { width: 360px; height: 360px; }
  .orbit-action { width: 105px; height: 116px; }.orbit-icon { width: 47px; height: 47px; }.orbit-icon .el-icon { font-size: 24px; }.orbit-action strong { font-size: 12px; }.orbit-action small { display: none; }
  .top-left { left: 4px; top: 35px; }.top-right { right: 4px; top: 35px; }.mid-left { left: -13px; top: 238px; }.mid-right { right: -13px; top: 238px; }.bottom-left { left: 20px; bottom: 22px; }.bottom-right { right: 20px; bottom: 22px; }
  .assistant-message { top: 104px; min-width: 180px; padding: 11px 14px; }.assistant-core { width: 250px; height: 330px; }.talk-button { bottom: 80px; }
  .left-column, .right-column { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) { .assistant-core img, .ring-three { animation: none; } * { scroll-behavior: auto !important; } }
</style>
