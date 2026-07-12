<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { mockTimeline } from '@/data/mock'
import { chongqingMockTimeline, chongqingVehiclePlate, getChongqingGpsTrajectory, getLatestChongqingGpsPoint } from '@/data/chongqingGps'
import { useLogisticsStore } from '@/stores/logistics'
import { amapApi, cargoApi, orderExtensionApi, routeApi } from '@/services/api'
import SmartMap from '@/components/SmartMap.vue'
import type { TimelineItem } from '@/types'
import type {
  AddressChangeImpactResult,
  AmapInputTip,
  DelayPrediction,
  DeviationCheckResult,
  ExceptionSummary,
  OrderDriverRatingResult,
  RiskScore,
  RoutePlanResult,
  UnloadAddressSuggestion,
} from '@/services/types'

const store = useLogisticsStore()
const { cargo, vehicles } = storeToRefs(store)
const timeline = ref<TimelineItem[]>(structuredClone(mockTimeline))
const trajectory = ref<Array<{ lat: number; lng: number }>>([])
const correctedTrajectory = ref<Array<{ lat: number; lng: number }>>([])
const plannedRoute = ref<RoutePlanResult | null>(null)
const routeMode = ref<'trajectory' | 'planned' | 'corrected'>('trajectory')
const loading = ref(false)
const loadError = ref('')
const insightLoading = ref(false)
const correctionLoading = ref(false)
const rerouteLoading = ref(false)
const selectedId = ref(cargo.value[0]?.id || '')
const selected = computed(() => cargo.value.find((item) => item.id === selectedId.value) || cargo.value[0])
const vehicle = computed(() => vehicles.value.find((item) => item.plate === selected.value?.vehiclePlate))
const cargoGroups = computed(() => ({
  notStarted: cargo.value.filter((item) => item.status === 'CREATED'),
  inTransit: cargo.value.filter((item) => item.status === 'LOADED' || item.status === 'IN_TRANSIT'),
  completed: cargo.value.filter((item) => item.status === 'DELIVERED' || item.status === 'CANCELLED'),
}))
const driverInitial = computed(() => (vehicle.value?.driver || '张建国').slice(0, 1))
const driverAvatarTone = computed(() => {
  const seed = Array.from(vehicle.value?.plate || selected.value?.id || 'default').reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return `tone-${seed % 4}`
})
let fallbackTimer: number | undefined
const statusText = { CREATED: '待装货', LOADED: '已装货', IN_TRANSIT: '运输中', DELIVERED: '已送达', CANCELLED: '已取消' }
const refreshedAt = ref('12 秒前')
const trackingHeroImage = new URL('../assets/login-map-hd.png', import.meta.url).href
const delayPrediction = ref<DelayPrediction | null>(null)
const riskScore = ref<RiskScore | null>(null)
const exceptionSummary = ref<ExceptionSummary | null>(null)
const deviationResult = ref<DeviationCheckResult | null>(null)
const driverRating = ref<OrderDriverRatingResult | null>(null)
const remainingDistanceText = computed(() => {
  const value = selected.value?.distanceRemaining
  if (typeof value !== 'number' || value < 0) return '暂无'
  return `${value.toFixed(value >= 100 ? 0 : 1)} km`
})
const remainingTimeText = computed(() => {
  const minutes = selected.value?.remainingMinutes
  return formatRemainingMinutes(minutes)
})
const phoneDialogVisible = ref(false)
const mapRoutePoints = computed(() => {
  if (routeMode.value === 'corrected' && correctedTrajectory.value.length > 1) return correctedTrajectory.value
  if (routeMode.value === 'planned' && plannedRoute.value?.polyline?.length) return plannedRoute.value.polyline
  return trajectory.value
})
const routeSourceText = computed(() => {
  if (routeMode.value === 'corrected') return '纠偏轨迹'
  if (routeMode.value === 'planned') return plannedRoute.value?.source === 'LOCAL_FALLBACK' ? '规划路线（本地降级）' : '规划路线'
  return '运输轨迹'
})
const delayStatusText = computed(() => {
  const status = delayPrediction.value?.delayStatus
  if (!status) return '暂无'
  if (status === 'ON_TIME') return '正常'
  if (status === 'DELAYED') return '可能延误'
  if (status === 'EARLY') return '可能提前'
  return status
})
const riskLevelText = computed(() => riskScore.value ? `${riskScore.value.level} · ${riskScore.value.score} 分` : '暂无')
const canRateDriver = computed(() => selected.value?.status === 'DELIVERED')

const addressDialogVisible = ref(false)
const addressTips = ref<AmapInputTip[]>([])
const addressImpact = ref<AddressChangeImpactResult | null>(null)
const addressSearching = ref(false)
const addressImpactLoading = ref(false)
const addressSubmitting = ref(false)
const addressForm = reactive({
  keywords: '',
  city: '杭州',
  detail: '',
  lng: undefined as number | undefined,
  lat: undefined as number | undefined,
  contactName: '',
  contactPhone: '',
  reason: '',
})

const unloadDialogVisible = ref(false)
const unloadSuggestions = ref<UnloadAddressSuggestion[]>([])
const unloadLoading = ref(false)
const unloadSubmitting = ref(false)
const unloadForm = reactive({
  address: '',
  lng: undefined as number | undefined,
  lat: undefined as number | undefined,
  remark: '',
})

const ratingDialogVisible = ref(false)
const ratingLoading = ref(false)
const ratingSubmitting = ref(false)
const ratingForm = reactive({
  score: 5,
  punctuality: 5,
  serviceAttitude: 5,
  cargoIntegrity: 5,
  communication: 5,
  tags: [] as string[],
  comment: '',
})
const ratingTagOptions = ['准时送达', '服务热情', '货物完好', '沟通及时', '路线稳定', '主动反馈']

function formatRemainingMinutes(minutes?: number) {
  if (typeof minutes !== 'number' || minutes < 0) return '暂无'
  if (minutes < 60) return `${Math.round(minutes)} 分钟`
  const hours = Math.floor(minutes / 60)
  const rest = Math.round(minutes % 60)
  return rest > 0 ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`
}

function formatEta(value?: string, remainingMinutes?: number) {
  const etaDate = value ? new Date(value) : null
  if (etaDate && !Number.isNaN(etaDate.getTime())) {
    return etaDate.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  }
  if (typeof remainingMinutes === 'number' && remainingMinutes >= 0) {
    return `约 ${formatRemainingMinutes(remainingMinutes)} 后`
  }
  return '暂无 ETA'
}

function normalizeProgress(value?: number) {
  if (typeof value !== 'number' || Number.isNaN(value)) return selected.value?.progress || 0
  const percent = value > 0 && value <= 1 ? value * 100 : value
  return Math.max(0, Math.min(100, percent))
}

function pickCargo(cargoId: string) {
  selectedId.value = cargoId
}

function pointFromCargo(kind: 'origin' | 'destination') {
  const item = selected.value
  if (!item) return null
  const lat = kind === 'origin' ? item.originLat : item.destinationLat
  const lng = kind === 'origin' ? item.originLng : item.destinationLng
  if (typeof lat !== 'number' || typeof lng !== 'number') return null
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null
  return { lat, lng }
}

function pointFromVehicle() {
  const item = vehicle.value
  if (!item || typeof item.lat !== 'number' || typeof item.lng !== 'number') return null
  if (!Number.isFinite(item.lat) || !Number.isFinite(item.lng)) return null
  return { lat: item.lat, lng: item.lng }
}

async function safeLoad<T>(task: Promise<T>): Promise<T | null> {
  try {
    return await task
  } catch {
    return null
  }
}

async function loadRouteAndInsights() {
  const item = selected.value
  if (!item || store.usingDemo) return
  insightLoading.value = true
  try {
    const origin = pointFromCargo('origin')
    const destination = pointFromCargo('destination')
    const [delay, risk, exception, rating] = await Promise.all([
      safeLoad(orderExtensionApi.delayPrediction(item.id)),
      safeLoad(orderExtensionApi.riskScore(item.id)),
      safeLoad(orderExtensionApi.exceptionSummary(item.id)),
      safeLoad(orderExtensionApi.driverRating(item.id)),
    ])
    delayPrediction.value = delay
    riskScore.value = risk
    exceptionSummary.value = exception
    driverRating.value = rating

    if (origin && destination) {
      const route = await safeLoad(routeApi.truckPlan({
        mode: 'TRUCK',
        origin,
        destination,
        plate: item.vehiclePlate,
      }))
      if (route) {
        plannedRoute.value = route
        if (!trajectory.value.length) routeMode.value = 'planned'
      }
    }

    const currentLocation = pointFromVehicle()
    if (currentLocation && plannedRoute.value?.polyline?.length) {
      deviationResult.value = await safeLoad(routeApi.checkDeviation({
        cargoId: item.id,
        vehiclePlate: item.vehiclePlate,
        currentLocation,
        routePolyline: plannedRoute.value.polyline,
        thresholdKm: 3,
      }))
    }
  } finally {
    insightLoading.value = false
  }
}

async function confirmReceipt() {
  if (!selected.value) return
  if (selected.value.status === 'DELIVERED') return
  try {
    await store.confirmReceipt(selected.value.id)
    ElMessage.success('收货确认成功，运单已完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '确认收货失败')
  }
}

async function refreshPosition(showMessage = true) {
  if (!selected.value) return
  loading.value = true
  loadError.value = ''
  plannedRoute.value = null
  correctedTrajectory.value = []
  deviationResult.value = null
  try {
    if (store.usingDemo) {
      store.refreshLiveData()
      const isChongqingHardwareCargo = selected.value.id === 'SH-HZ-20260629-0291' || selected.value.vehiclePlate === chongqingVehiclePlate
      const hardwareTrajectory = isChongqingHardwareCargo ? getChongqingGpsTrajectory() : getChongqingGpsTrajectory(selected.value.id)
      if (hardwareTrajectory.length) {
        trajectory.value = hardwareTrajectory
        routeMode.value = 'trajectory'
        timeline.value = structuredClone(chongqingMockTimeline)
        const latest = getLatestChongqingGpsPoint()
        const current = vehicle.value
        if (latest && current) {
          Object.assign(current, {
            lat: latest.lat,
            lng: latest.lng,
            speed: latest.speed,
            heading: latest.heading,
            heartbeat: new Date(latest.time.replace(' ', 'T').replace(/\+(\d{2})$/, '+$1:00')).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }),
            location: `重庆实测点位 ${latest.lat.toFixed(5)}, ${latest.lng.toFixed(5)}`,
          })
        }
        selected.value.progress = Math.max(selected.value.progress || 0, 82)
        selected.value.distanceRemaining = 0
        selected.value.remainingMinutes = 0
        selected.value.eta = latest ? '硬件实测轨迹已接入' : selected.value.eta
      } else {
        const current = vehicle.value
        if (current) trajectory.value = [
          { lat: current.lat - 18, lng: current.lng - 9 },
          { lat: current.lat - 9, lng: current.lng - 3 },
          { lat: current.lat, lng: current.lng },
        ]
        timeline.value = structuredClone(mockTimeline)
      }
    } else {
      const [position, route, eta, timelineResult] = await Promise.all([
        cargoApi.position(selected.value.id),
        cargoApi.trajectory(selected.value.id),
        cargoApi.eta(selected.value.id),
        cargoApi.timeline(selected.value.id),
      ])
      const livePosition = position.position || {
        lat: position.lat ?? 0,
        lng: position.lng ?? 0,
        speed: position.speed ?? 0,
        heading: position.heading ?? 0,
        accuracy: position.accuracy,
      }
      const positionPlate = position.vehiclePlate || position.plate || selected.value.vehiclePlate
      const current = vehicles.value.find((item) => item.plate === positionPlate)
      if (current) Object.assign(current, {
        lat: livePosition.lat, lng: livePosition.lng, speed: livePosition.speed,
        heading: livePosition.heading, heartbeat: position.updatedAt, location: position.locationDesc || `${livePosition.lat}, ${livePosition.lng}`,
      })
      trajectory.value = route.points
        .slice()
        .reverse()
        .filter((item) => item.lat != null && item.lng != null)
        .map((item) => ({ lat: item.lat, lng: item.lng }))
      selected.value.distanceRemaining = eta.distanceRemaining
      selected.value.remainingMinutes = eta.remainingMinutes
      selected.value.eta = formatEta(eta.eta, eta.remainingMinutes)
      selected.value.progress = normalizeProgress(eta.progress)
      timeline.value = timelineResult.events.map((item) => ({
        time: new Date(item.time).toLocaleString('zh-CN'),
        title: item.title,
        description: item.description,
        active: Boolean(item.alertId),
      }))
      await loadRouteAndInsights()
    }
    refreshedAt.value = '刚刚'
    if (showMessage) ElMessage.success('实时位置、ETA 与轨迹已刷新')
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '追踪数据加载失败'
    if (showMessage) ElMessage.error(loadError.value)
  } finally {
    loading.value = false
  }
}

function contactDriver() {
  phoneDialogVisible.value = true
}

async function correctRouteTrajectory() {
  if (!selected.value) return
  if (trajectory.value.length < 2) return ElMessage.warning('当前轨迹点不足，暂时不能纠偏')
  correctionLoading.value = true
  try {
    const result = await routeApi.correctTrajectory({
      cargoId: selected.value.id,
      vehiclePlate: selected.value.vehiclePlate,
      points: trajectory.value.map((point) => ({ ...point })),
    })
    correctedTrajectory.value = result.correctedPoints || []
    routeMode.value = correctedTrajectory.value.length > 1 ? 'corrected' : 'trajectory'
    ElMessage.success('轨迹纠偏完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '轨迹纠偏失败')
  } finally {
    correctionLoading.value = false
  }
}

async function sendRerouteCommand() {
  if (!selected.value?.vehiclePlate) return ElMessage.warning('当前货物还没有绑定车辆')
  const currentLocation = pointFromVehicle()
  const destination = pointFromCargo('destination')
  if (!currentLocation || !destination) return ElMessage.warning('当前位置或目的地坐标不足')
  rerouteLoading.value = true
  try {
    const suggestion = await routeApi.rerouteSuggestion({
      cargoId: selected.value.id,
      vehiclePlate: selected.value.vehiclePlate,
      plate: selected.value.vehiclePlate,
      currentLocation,
      destination,
      strategy: '最快路线',
    })
    plannedRoute.value = suggestion.route
    routeMode.value = 'planned'
    await store.issueCommand(selected.value.vehiclePlate, suggestion.commandType || 'REROUTE', suggestion.message || '请按新路线继续运输')
    ElMessage.success('纠偏路线已下发给司机')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '纠偏路线下发失败')
  } finally {
    rerouteLoading.value = false
  }
}

function resetAddressForm() {
  Object.assign(addressForm, {
    keywords: selected.value?.destination || '',
    city: '杭州',
    detail: '',
    lng: undefined,
    lat: undefined,
    contactName: '',
    contactPhone: '',
    reason: '',
  })
  addressTips.value = []
  addressImpact.value = null
}

function openAddressDialog() {
  if (!selected.value) return
  resetAddressForm()
  addressDialogVisible.value = true
}

async function searchAddressTips() {
  if (!addressForm.keywords.trim()) return ElMessage.warning('先输入新收货地址关键词')
  addressSearching.value = true
  try {
    addressTips.value = await amapApi.inputTips(addressForm.keywords.trim(), addressForm.city || undefined)
    if (!addressTips.value.length) ElMessage.info('没有找到地址提示，可以直接点击地址转坐标')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '地址提示查询失败')
  } finally {
    addressSearching.value = false
  }
}

async function selectAddressTip(tip: AmapInputTip) {
  addressForm.detail = [tip.district, tip.name, tip.address].filter(Boolean).join(' ')
  addressForm.lng = tip.lng
  addressForm.lat = tip.lat
  if (typeof addressForm.lng !== 'number' || typeof addressForm.lat !== 'number') {
    await geocodeAddress(false)
    return
  }
  await calculateAddressImpact()
}

async function geocodeAddress(showMessage = true) {
  const address = addressForm.detail.trim() || addressForm.keywords.trim()
  if (!address) return ElMessage.warning('请填写新收货地址')
  addressSearching.value = true
  try {
    const result = await amapApi.geocode({ address, city: addressForm.city || undefined })
    addressForm.detail = result.formattedAddress || address
    addressForm.lng = result.lng
    addressForm.lat = result.lat
    if (showMessage) ElMessage.success('地址坐标已解析')
    await calculateAddressImpact()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '地址转坐标失败')
  } finally {
    addressSearching.value = false
  }
}

async function calculateAddressImpact() {
  if (!selected.value) return
  if (typeof addressForm.lat !== 'number' || typeof addressForm.lng !== 'number') return
  addressImpactLoading.value = true
  try {
    addressImpact.value = await orderExtensionApi.addressChangeImpact(selected.value.id, {
      newAddress: {
        detail: addressForm.detail || addressForm.keywords,
        city: addressForm.city,
        lat: addressForm.lat,
        lng: addressForm.lng,
      },
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '改址影响计算失败')
  } finally {
    addressImpactLoading.value = false
  }
}

async function submitAddressChange() {
  if (!selected.value) return
  if (!addressForm.detail.trim()) return ElMessage.warning('请填写新收货地址')
  if (typeof addressForm.lat !== 'number' || typeof addressForm.lng !== 'number') return ElMessage.warning('请先解析地址坐标')
  addressSubmitting.value = true
  try {
    await orderExtensionApi.createAddressChange(selected.value.id, {
      newAddress: {
        detail: addressForm.detail,
        city: addressForm.city,
        lat: addressForm.lat,
        lng: addressForm.lng,
      },
      contactName: addressForm.contactName,
      contactPhone: addressForm.contactPhone,
      reason: addressForm.reason,
    })
    addressDialogVisible.value = false
    ElMessage.success('改址申请已提交')
    await loadRouteAndInsights()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '改址申请提交失败')
  } finally {
    addressSubmitting.value = false
  }
}

async function openUnloadDialog() {
  if (!selected.value) return
  unloadDialogVisible.value = true
  unloadLoading.value = true
  unloadSuggestions.value = []
  Object.assign(unloadForm, { address: selected.value.destination, lng: selected.value.destinationLng, lat: selected.value.destinationLat, remark: '' })
  try {
    const result = await orderExtensionApi.unloadAddressSuggestions(selected.value.id)
    unloadSuggestions.value = result.suggestions || []
    const first = unloadSuggestions.value[0]
    if (first) selectUnloadSuggestion(first)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '卸货点建议加载失败')
  } finally {
    unloadLoading.value = false
  }
}

function selectUnloadSuggestion(item: UnloadAddressSuggestion) {
  unloadForm.address = item.address
  unloadForm.lng = item.lng
  unloadForm.lat = item.lat
}

async function confirmUnloadAddress() {
  if (!selected.value) return
  if (!unloadForm.address.trim()) return ElMessage.warning('请选择或填写卸货地址')
  if (typeof unloadForm.lat !== 'number' || typeof unloadForm.lng !== 'number') return ElMessage.warning('卸货地址缺少坐标')
  unloadSubmitting.value = true
  try {
    await orderExtensionApi.confirmUnloadAddress(selected.value.id, {
      address: unloadForm.address,
      lat: unloadForm.lat,
      lng: unloadForm.lng,
      remark: unloadForm.remark,
    })
    selected.value.destination = unloadForm.address
    selected.value.destinationLat = unloadForm.lat
    selected.value.destinationLng = unloadForm.lng
    unloadDialogVisible.value = false
    ElMessage.success('卸货地址已确认')
    await loadRouteAndInsights()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '卸货地址确认失败')
  } finally {
    unloadSubmitting.value = false
  }
}

async function openRatingDialog() {
  if (!selected.value) return
  ratingDialogVisible.value = true
  ratingLoading.value = true
  try {
    driverRating.value = await orderExtensionApi.driverRating(selected.value.id)
    if (driverRating.value?.rated) ElMessage.info('该运单已经评价过司机')
  } catch {
    driverRating.value = null
  } finally {
    ratingLoading.value = false
  }
}

async function submitDriverRating() {
  if (!selected.value) return
  ratingSubmitting.value = true
  try {
    await orderExtensionApi.submitDriverRating(selected.value.id, {
      score: ratingForm.score,
      dimensions: {
        punctuality: ratingForm.punctuality,
        serviceAttitude: ratingForm.serviceAttitude,
        cargoIntegrity: ratingForm.cargoIntegrity,
        communication: ratingForm.communication,
      },
      tags: ratingForm.tags,
      comment: ratingForm.comment,
    })
    driverRating.value = await orderExtensionApi.driverRating(selected.value.id)
    ratingDialogVisible.value = false
    ElMessage.success('司机评价已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '司机评价提交失败')
  } finally {
    ratingSubmitting.value = false
  }
}

function configureFallback() {
  window.clearInterval(fallbackTimer)
  if (!store.usingDemo && store.realtimeState !== 'open') {
    fallbackTimer = window.setInterval(() => void refreshPosition(false), 15_000)
  }
}

onMounted(() => void refreshPosition(false))
watch(selectedId, () => void refreshPosition(false))
watch(() => store.realtimeState, configureFallback, { immediate: true })
onUnmounted(() => {
  window.clearInterval(fallbackTimer)
})
</script>

<template>
  <div class="view-stack tracking-v6-page">
    <section class="filter-strip">
      <div>
        <span class="section-kicker">CARGO TRACKING</span>
        <h2>全链路在途监控</h2>
      </div>
      <div class="filter-actions">
        <el-button icon="Refresh" @click="refreshPosition()">刷新位置</el-button>
      </div>
    </section>

    <section class="cargo-stage-grid">
      <article class="panel cargo-stage-panel">
        <div class="panel-head"><div><span class="section-kicker">NOT STARTED</span><h3>未开始运输</h3></div><strong>{{ cargoGroups.notStarted.length }}</strong></div>
        <button
          v-for="item in cargoGroups.notStarted"
          :key="item.id"
          type="button"
          :class="{ active: selectedId === item.id }"
          @click="pickCargo(item.id)"
        >
          <span><strong>{{ item.name }}</strong><small>{{ item.id }}</small></span>
          <em>{{ statusText[item.status] }}</em>
        </button>
        <el-empty v-if="!cargoGroups.notStarted.length" description="暂无未开始货物" :image-size="54" />
      </article>

      <article class="panel cargo-stage-panel">
        <div class="panel-head"><div><span class="section-kicker">IN TRANSIT</span><h3>运输中</h3></div><strong>{{ cargoGroups.inTransit.length }}</strong></div>
        <button
          v-for="item in cargoGroups.inTransit"
          :key="item.id"
          type="button"
          :class="{ active: selectedId === item.id }"
          @click="pickCargo(item.id)"
        >
          <span><strong>{{ item.name }}</strong><small>{{ item.id }}</small></span>
          <em>{{ item.vehiclePlate || '待绑定车辆' }}</em>
        </button>
        <el-empty v-if="!cargoGroups.inTransit.length" description="暂无运输中货物" :image-size="54" />
      </article>

      <article class="panel cargo-stage-panel">
        <div class="panel-head"><div><span class="section-kicker">COMPLETED</span><h3>已完成</h3></div><strong>{{ cargoGroups.completed.length }}</strong></div>
        <button
          v-for="item in cargoGroups.completed"
          :key="item.id"
          type="button"
          :class="{ active: selectedId === item.id }"
          @click="pickCargo(item.id)"
        >
          <span><strong>{{ item.name }}</strong><small>{{ item.id }}</small></span>
          <em>{{ statusText[item.status] }}</em>
        </button>
        <el-empty v-if="!cargoGroups.completed.length" description="暂无已完成货物" :image-size="54" />
      </article>
    </section>

    <section v-if="selected" class="tracking-layout">
      <div class="view-stack">
        <article class="panel shipment-card image-card" :style="{ '--card-image': `url(${trackingHeroImage})` }">
          <div class="shipment-top">
            <div>
              <span class="status-chip success">{{ statusText[selected.status] }}</span>
              <span class="muted">运单号 {{ selected.id }}</span>
              <h3>{{ selected.name }}</h3>
              <p>{{ selected.category }} · 承运车辆 {{ selected.vehiclePlate || '暂未绑定' }}</p>
            </div>
          </div>
          <div class="route-summary">
            <div><i class="route-dot start"></i><span>始发地</span><strong>{{ selected.origin }}</strong><small>今天 08:30</small></div>
            <div class="route-line"><span>预计 {{ selected.eta }} 到达</span><i></i></div>
            <div><i class="route-dot end"></i><span>目的地</span><strong>{{ selected.destination }}</strong><small>剩余约 {{ remainingDistanceText }}</small></div>
          </div>
        </article>

        <article class="panel map-panel tracking-map">
          <div class="panel-head">
            <div><span class="section-kicker">LIVE POSITION</span><h3>实时位置与运输轨迹</h3></div>
            <div class="map-toolbar">
              <el-button-group>
                <el-button size="small" :type="routeMode === 'trajectory' ? 'primary' : ''" @click="routeMode = 'trajectory'">轨迹</el-button>
                <el-button size="small" :type="routeMode === 'planned' ? 'primary' : ''" :disabled="!plannedRoute" @click="routeMode = 'planned'">路线</el-button>
                <el-button size="small" :type="routeMode === 'corrected' ? 'primary' : ''" :disabled="!correctedTrajectory.length" @click="routeMode = 'corrected'">纠偏</el-button>
              </el-button-group>
              <span class="live-text"><i></i>{{ refreshedAt }}更新</span>
            </div>
          </div>
          <div style="position: relative">
            <div v-if="loading && !vehicle" class="state-panel">正在加载位置与轨迹…</div>
            <div v-else-if="loadError && !vehicle" class="state-panel error-panel">{{ loadError }}</div>
            <SmartMap
              v-else
              :vehicles="vehicle ? [vehicle] : vehicles"
              :selected-plate="selected.vehiclePlate"
              :route-points="mapRoutePoints"
              height="560px"
            />
            <div v-if="store.alerts.some(a => a.plate === selected.vehiclePlate && a.status === 'PENDING')" class="deviation-badge"><el-icon><Warning /></el-icon>{{ store.alerts.find(a => a.plate === selected.vehiclePlate && a.status === 'PENDING')?.title }}</div>
            <div v-if="insightLoading" class="map-working-note"><el-icon><Loading /></el-icon>路线与风险计算中...</div>
          </div>
          <div class="route-action-strip">
            <div>
              <span>{{ routeSourceText }}</span>
              <strong v-if="plannedRoute">{{ plannedRoute.distanceKm.toFixed(1) }} km · {{ plannedRoute.durationMinutes.toFixed(0) }} 分钟</strong>
              <strong v-else>等待路线规划</strong>
            </div>
            <div v-if="deviationResult" :class="{ danger: deviationResult.deviated }">
              <span>偏航检测</span>
              <strong>{{ deviationResult.deviated ? '已偏航' : '正常' }} · {{ deviationResult.distanceToRouteKm.toFixed(2) }} km</strong>
            </div>
            <div class="route-actions">
              <el-button size="small" :loading="correctionLoading" @click="correctRouteTrajectory">轨迹纠偏</el-button>
              <el-button size="small" type="primary" :loading="rerouteLoading" @click="sendRerouteCommand">一键纠偏</el-button>
            </div>
          </div>
        </article>
      </div>

      <aside class="view-stack">
        <article class="panel eta-card">
          <span class="section-kicker">ESTIMATED ARRIVAL</span>
          <h3>预计到达时间</h3>
          <strong class="eta-time">{{ selected.eta }}</strong>
          <div class="eta-grid">
            <div><span>剩余距离</span><strong>{{ remainingDistanceText }}</strong></div>
            <div><span>当前速度</span><strong>{{ vehicle?.speed || 0 }} km/h</strong></div>
            <div><span>延误状态</span><strong>{{ delayStatusText }}</strong></div>
            <div><span>运输风险</span><strong>{{ riskLevelText }}</strong></div>
          </div>
          <div class="delay-note"><el-icon><WarningFilled /></el-icon><span>{{ delayPrediction?.reasons?.[0] || `按当前速度预计还需 ${remainingTimeText}` }}</span></div>
        </article>

        <article class="panel order-insight-card">
          <div class="panel-head"><div><span class="section-kicker">AMAP INSIGHT</span><h3>路线与异常摘要</h3></div></div>
          <dl class="info-list compact">
            <div><dt>路线来源</dt><dd>{{ routeSourceText }}</dd></div>
            <div><dt>预计耗时</dt><dd>{{ plannedRoute ? `${plannedRoute.durationMinutes.toFixed(0)} 分钟` : '暂无' }}</dd></div>
            <div><dt>偏航建议</dt><dd>{{ deviationResult?.suggestion || '暂无' }}</dd></div>
            <div><dt>异常摘要</dt><dd>{{ exceptionSummary?.summary || '暂无异常' }}</dd></div>
          </dl>
        </article>

        <article class="panel">
          <div class="panel-head"><div><span class="section-kicker">TIMELINE</span><h3>运输时间线</h3></div></div>
          <div class="timeline">
            <div v-for="item in timeline" :key="`${item.time}-${item.title}`" :class="{ active: item.active }">
              <i></i>
              <span>{{ item.time }}</span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </div>
          </div>
        </article>

        <article class="panel driver-card">
          <div class="driver-avatar large-avatar" :class="driverAvatarTone"><span>{{ driverInitial }}</span></div>
          <div><strong>{{ vehicle?.driver || '张建国' }}</strong><span>承运司机 · 驾龄 12 年 · 信用评分 98</span></div>
          <el-button circle icon="Phone" type="primary" title="联系司机" @click="contactDriver" />
        </article>
        <article class="panel receipt-card">
          <div class="receipt-icon"><el-icon><GoodsFilled /></el-icon></div>
          <div><span class="section-kicker">RECEIPT</span><h3>{{ selected.status === 'DELIVERED' ? '已完成' : '确认收货' }}</h3><p>{{ selected.status === 'DELIVERED' ? '该运单已完成，不能重复确认。' : '核对货物后点击确认，后端会把货物状态更新为已送达。' }}</p></div>
          <div class="receipt-actions">
            <el-button type="primary" plain @click="openAddressDialog">申请改址</el-button>
            <el-button plain @click="openUnloadDialog">确认卸货点</el-button>
            <el-button type="warning" plain :disabled="!canRateDriver" @click="openRatingDialog">{{ driverRating?.rated ? '已评价' : '评价司机' }}</el-button>
            <el-button type="success" :disabled="selected.status === 'DELIVERED'" @click="confirmReceipt">确认收货</el-button>
          </div>
          <el-icon v-if="selected.status === 'DELIVERED'" class="receipt-done"><CircleCheckFilled /></el-icon>
        </article>
      </aside>
    </section>

    <el-dialog v-model="phoneDialogVisible" :title="`联系 ${vehicle?.driver || '承运司机'}`" width="360px" class="phone-dialog">
      <div class="phone-dialog-body">
        <span>联系电话</span>
        <strong>{{ vehicle?.phone || '暂无司机电话' }}</strong>
      </div>
      <template #footer>
        <el-button type="primary" @click="phoneDialogVisible = false">知道了</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="addressDialogVisible" title="申请修改收货地址" width="620px">
      <el-form label-position="top">
        <el-form-item label="地址关键词">
          <div class="inline-form-row">
            <el-input v-model="addressForm.keywords" placeholder="输入新收货地址关键词" clearable @keyup.enter="searchAddressTips" />
            <el-button :loading="addressSearching" @click="searchAddressTips">查找</el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="addressTips.length" label="地址提示">
          <div class="address-tip-list">
            <button v-for="tip in addressTips" :key="`${tip.name}-${tip.address}`" type="button" @click="selectAddressTip(tip)">
              <strong>{{ tip.name }}</strong><span>{{ tip.district }} {{ tip.address }}</span>
            </button>
          </div>
        </el-form-item>
        <el-form-item label="新收货地址">
          <div class="inline-form-row">
            <el-input v-model="addressForm.detail" placeholder="选择提示后自动带入，也可以手动输入" />
            <el-button :loading="addressSearching" @click="geocodeAddress()">地址转坐标</el-button>
          </div>
        </el-form-item>
        <div class="form-coordinate-grid">
          <el-form-item label="经度"><el-input-number v-model="addressForm.lng" :precision="6" :step="0.000001" style="width: 100%" /></el-form-item>
          <el-form-item label="纬度"><el-input-number v-model="addressForm.lat" :precision="6" :step="0.000001" style="width: 100%" /></el-form-item>
        </div>
        <div v-if="addressImpact" class="impact-card">
          <strong>{{ addressImpact.canChange ? '允许改址' : '暂不建议改址' }} · {{ addressImpact.impactLevel }}</strong>
          <span>额外 {{ addressImpact.extraDistanceKm.toFixed(1) }} km，预计延误 {{ addressImpact.estimatedDelayMinutes }} 分钟，费用约 {{ addressImpact.extraCost }} 元</span>
          <small v-if="addressImpact.warnings?.length">{{ addressImpact.warnings.join('；') }}</small>
        </div>
        <div class="form-coordinate-grid">
          <el-form-item label="联系人"><el-input v-model="addressForm.contactName" placeholder="选填" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="addressForm.contactPhone" placeholder="选填" /></el-form-item>
        </div>
        <el-form-item label="修改原因"><el-input v-model="addressForm.reason" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addressDialogVisible = false">取消</el-button>
        <el-button :loading="addressImpactLoading" @click="calculateAddressImpact">重新计算影响</el-button>
        <el-button type="primary" :loading="addressSubmitting" @click="submitAddressChange">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="unloadDialogVisible" title="确认卸货点" width="560px">
      <div v-loading="unloadLoading" class="unload-dialog-body">
        <div class="address-tip-list">
          <button v-for="item in unloadSuggestions" :key="`${item.source}-${item.address}`" type="button" @click="selectUnloadSuggestion(item)">
            <strong>{{ item.address }}</strong>
            <span>{{ item.source }} · 置信度 {{ Math.round(item.confidence * 100) }}% · {{ item.reason }}</span>
          </button>
          <el-empty v-if="!unloadSuggestions.length && !unloadLoading" description="暂无推荐卸货点" :image-size="54" />
        </div>
        <el-form label-position="top">
          <el-form-item label="最终卸货地址"><el-input v-model="unloadForm.address" /></el-form-item>
          <div class="form-coordinate-grid">
            <el-form-item label="经度"><el-input-number v-model="unloadForm.lng" :precision="6" :step="0.000001" style="width: 100%" /></el-form-item>
            <el-form-item label="纬度"><el-input-number v-model="unloadForm.lat" :precision="6" :step="0.000001" style="width: 100%" /></el-form-item>
          </div>
          <el-form-item label="备注"><el-input v-model="unloadForm.remark" type="textarea" :rows="2" /></el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="unloadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="unloadSubmitting" @click="confirmUnloadAddress">确认卸货地址</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ratingDialogVisible" title="评价承运司机" width="520px">
      <div v-loading="ratingLoading">
        <div v-if="driverRating?.rated" class="impact-card">
          <strong>已评价 · {{ driverRating.rating?.score }} 分</strong>
          <span>{{ driverRating.rating?.comment || '暂无文字评价' }}</span>
        </div>
        <el-form v-else label-position="top">
          <el-form-item label="整体评分"><el-rate v-model="ratingForm.score" :max="5" /></el-form-item>
          <div class="rating-grid">
            <label>准时程度<el-rate v-model="ratingForm.punctuality" :max="5" /></label>
            <label>服务态度<el-rate v-model="ratingForm.serviceAttitude" :max="5" /></label>
            <label>货物完好<el-rate v-model="ratingForm.cargoIntegrity" :max="5" /></label>
            <label>沟通及时<el-rate v-model="ratingForm.communication" :max="5" /></label>
          </div>
          <el-form-item label="评价标签">
            <el-checkbox-group v-model="ratingForm.tags">
              <el-checkbox-button v-for="tag in ratingTagOptions" :key="tag" :label="tag" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="文字评价"><el-input v-model="ratingForm.comment" type="textarea" :rows="3" /></el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="ratingDialogVisible = false">关闭</el-button>
        <el-button v-if="!driverRating?.rated" type="primary" :loading="ratingSubmitting" @click="submitDriverRating">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>
