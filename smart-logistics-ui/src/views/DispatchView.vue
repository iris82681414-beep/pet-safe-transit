<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useLogisticsStore } from '@/stores/logistics'
import type { Vehicle } from '@/types'
import { fileApi, routeApi, vehicleApi } from '@/services/api'
import type { RoutePlanResult } from '@/services/types'
import SmartMap from '@/components/SmartMap.vue'

const store = useLogisticsStore()
const { vehicles, commands, cargo } = storeToRefs(store)
const keyword = ref('')
const status = ref('')
const selected = ref<Vehicle | null>(vehicles.value[0] || null)
const dialogVisible = ref(false)
const command = reactive({ type: 'ROUTE_ADJUST', content: '请回到 G60 推荐路线，保持 30 秒一次心跳上报。' })
const submitting = ref(false)
const vehicleImageInput = ref<HTMLInputElement | null>(null)
const phoneDialogVisible = ref(false)
const phoneDialog = reactive({ name: '', phone: '' })
const selectedRoute = ref<RoutePlanResult | null>(null)
const routeLoading = ref(false)
const routeCommandLoading = ref(false)

const filtered = computed(() => vehicles.value.filter((item) => {
  const matchesKeyword = !keyword.value || `${item.plate}${item.driver}`.includes(keyword.value)
  return matchesKeyword && (!status.value || item.status === status.value)
}))
const selectedCargoCount = computed(() => selected.value?.cargoId ? selected.value.cargoId.split('、').filter(Boolean).length : 0)
const selectedTaskWidth = computed(() => `${Math.min(100, Math.max(8, selectedCargoCount.value * 32))}%`)
const selectedLocationText = computed(() => locationText(selected.value))
const selectedCargo = computed(() => {
  if (!selected.value) return null
  const ids = selected.value.cargoId?.split('、').filter(Boolean) || []
  return cargo.value.find((item) => item.vehiclePlate === selected.value?.plate || ids.includes(item.id)) || null
})
const dispatchRoutePoints = computed(() => selectedRoute.value?.polyline || [])

function coordinateText(lat?: number, lng?: number) {
  if (typeof lat !== 'number' || typeof lng !== 'number') return ''
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return ''
  return `${lat.toFixed(6)}, ${lng.toFixed(6)}`
}

function locationText(vehicle?: Vehicle | null) {
  if (!vehicle) return '暂无位置'
  const location = vehicle.location?.trim()
  if (location && location !== '暂无位置' && location !== '位置未知') return location
  return coordinateText(vehicle.lat, vehicle.lng) || '暂无位置'
}

async function submitCommand() {
  if (!selected.value || !command.content.trim()) return
  submitting.value = true
  try {
    const type = command.type === 'ROUTE_ADJUST' ? 'REROUTE' : command.type
    await store.issueCommand(selected.value.plate, type, command.content)
    dialogVisible.value = false
    ElMessage.success(`调度指令已下发至 ${selected.value.plate}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '指令下发失败')
  } finally {
    submitting.value = false
  }
}
const commandStatus = { SENT: '已发送', RECEIVED: '已接收', EXECUTED: '已执行', REJECTED: '已拒绝', FAILED: '失败' }

async function selectVehicle(vehicle: Vehicle) {
  selected.value = vehicle
  selectedRoute.value = null
  if (store.usingDemo) return
  try {
    const detail = await vehicleApi.detail(vehicle.plate)
    const task = await vehicleApi.activeTask(vehicle.plate).catch(() => null)
    const cargoIds = task?.cargos?.map((item) => item.cargoId).filter(Boolean) || []
    const lat = detail.position?.lat ?? vehicle.lat
    const lng = detail.position?.lng ?? vehicle.lng
    Object.assign(vehicle, {
      driver: detail.driverName,
      phone: detail.driverPhone,
      speed: detail.position?.speed || 0,
      lat,
      lng,
      heading: detail.position?.heading,
      location: detail.locationDesc || coordinateText(lat, lng) || vehicle.location,
      heartbeat: detail.updatedAt,
      cargoId: cargoIds.length ? cargoIds.join('、') : detail.cargoId,
    })
    await planSelectedRoute(false)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '车辆详情加载失败')
  }
}

function selectedOriginPoint() {
  if (!selected.value) return null
  if (!Number.isFinite(selected.value.lat) || !Number.isFinite(selected.value.lng)) return null
  return { lat: selected.value.lat, lng: selected.value.lng }
}

function selectedDestinationPoint() {
  const item = selectedCargo.value
  if (!item) return null
  if (typeof item.destinationLat !== 'number' || typeof item.destinationLng !== 'number') return null
  if (!Number.isFinite(item.destinationLat) || !Number.isFinite(item.destinationLng)) return null
  return { lat: item.destinationLat, lng: item.destinationLng }
}

async function planSelectedRoute(showMessage = true) {
  if (!selected.value) return
  const origin = selectedOriginPoint()
  const destination = selectedDestinationPoint()
  if (!origin || !destination) {
    if (showMessage) ElMessage.warning('当前车辆或宠物托运任务缺少坐标，暂时不能规划路线')
    return
  }
  routeLoading.value = true
  try {
    selectedRoute.value = await routeApi.truckPlan({
      mode: 'TRUCK',
      origin,
      destination,
      plate: selected.value.plate,
    })
    if (showMessage) ElMessage.success('路线规划完成')
  } catch (error) {
    if (showMessage) ElMessage.error(error instanceof Error ? error.message : '路线规划失败')
  } finally {
    routeLoading.value = false
  }
}

async function sendSelectedRoute() {
  if (!selected.value) return
  if (!selectedRoute.value) await planSelectedRoute(false)
  if (!selectedRoute.value) return ElMessage.warning('请先规划路线')
  routeCommandLoading.value = true
  try {
    await store.issueCommand(
      selected.value.plate,
      'REROUTE',
      `请按新路线行驶：预计 ${selectedRoute.value.distanceKm.toFixed(1)} km，约 ${selectedRoute.value.durationMinutes.toFixed(0)} 分钟`,
    )
    ElMessage.success('新路线已下发至车载终端')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '新路线下发失败')
  } finally {
    routeCommandLoading.value = false
  }
}

function contactDriver() {
  phoneDialog.name = selected.value?.driver || '司机'
  phoneDialog.phone = selected.value?.phone || '暂无司机电话'
  phoneDialogVisible.value = true
}

async function uploadVehicleImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !selected.value) return
  try {
    const result = await fileApi.uploadImage(file)
    store.setVehicleImage(selected.value.plate, result.url)
    ElMessage.success(`${selected.value.plate} 的车辆图片已上传到后端文件服务`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '车辆图片上传失败')
  } finally {
    input.value = ''
  }
}
</script>

<template>
  <div class="dispatch-layout">
    <section class="panel fleet-sidebar">
      <div class="panel-head"><div><span class="section-kicker">FLEET CONTROL</span><h3>车辆筛选与指令</h3></div><span>{{ filtered.length }} 辆</span></div>
      <el-input v-model="keyword" placeholder="搜索车牌 / 司机" prefix-icon="Search" clearable />
      <div class="status-tabs">
        <button :class="{ active: status === '' }" @click="status = ''">全部</button>
        <button :class="{ active: status === 'IN_TRANSIT' }" @click="status = 'IN_TRANSIT'">行驶中</button>
        <button :class="{ active: status === 'IDLE' }" @click="status = 'IDLE'">待命</button>
        <button :class="{ active: status === 'OFFLINE' }" @click="status = 'OFFLINE'">离线</button>
      </div>
      <div class="dispatch-list">
        <button v-for="vehicle in filtered" :key="vehicle.plate" :class="{ active: selected?.plate === vehicle.plate }" @click="selectVehicle(vehicle)">
          <span class="vehicle-badge"><el-icon><Van /></el-icon></span>
          <span><strong>{{ vehicle.plate }}</strong><small>{{ vehicle.driver }} · {{ locationText(vehicle) }}</small></span>
          <i :class="vehicle.status.toLowerCase()"></i>
        </button>
        <el-empty v-if="!filtered.length" description="暂无符合条件的车辆" :image-size="72" />
      </div>
    </section>

    <section class="panel map-panel dispatch-map">
      <div class="panel-head">
        <div><span class="section-kicker">REAL-TIME FLEET</span><h3>车辆实时态势</h3></div>
      </div>
      <SmartMap :vehicles="vehicles" :selected-plate="selected?.plate" :route-points="dispatchRoutePoints" height="580px" tooltip-style="compact" @select="selectVehicle" />
    </section>

    <aside v-if="selected" class="panel vehicle-detail">
      <div class="vehicle-photo-card">
        <img v-if="selected.image" :src="selected.image" :alt="`${selected.plate} 车辆图片`" />
        <div v-else class="vehicle-photo-empty"><el-icon><Picture /></el-icon><span>暂无车辆图片</span></div>
        <button class="vehicle-photo-action" @click="vehicleImageInput?.click()">
          <el-icon><Upload /></el-icon>{{ selected.image ? '更换图片' : '上传车辆图片' }}
        </button>
        <input ref="vehicleImageInput" class="file-input-hidden" type="file" accept="image/*" @change="uploadVehicleImage" />
      </div>
      <div class="detail-hero">
        <span class="vehicle-badge large-icon"><el-icon><Van /></el-icon></span>
        <div><span class="status-chip success">{{ selected.status === 'IN_TRANSIT' ? '行驶中' : selected.status === 'IDLE' ? '待命' : '离线' }}</span><h3>{{ selected.plate }}</h3><p>{{ selected.driver }} · {{ selected.phone }}</p></div>
      </div>
      <div class="detail-stats">
        <div><span>当前速度</span><strong>{{ selected.speed }} <small>km/h</small></strong></div>
        <div><span>设备心跳</span><strong>{{ selected.heartbeat }}</strong></div>
        <div><span>当前位置</span><strong>{{ selectedLocationText }}</strong></div>
        <div><span>托运任务</span><strong>{{ selected.cargoId || '暂无' }}</strong></div>
      </div>
      <div class="detail-route"><span>当前宠物旅程</span><strong>{{ selectedCargoCount }} 项</strong><div><i :style="{ width: selectedTaskWidth }"></i></div><small>{{ selected.cargoId || '暂无托运任务' }}</small></div>
      <div class="detail-route dispatch-route-plan">
        <span>规划路线</span>
        <strong>{{ selectedRoute ? `${selectedRoute.distanceKm.toFixed(1)} km · ${selectedRoute.durationMinutes.toFixed(0)} 分钟` : '暂未规划' }}</strong>
        <div><i :style="{ width: selectedRoute ? '100%' : '8%' }"></i></div>
        <small>{{ selectedCargo?.destination || '请选择已安排宠物托运任务的车辆' }}</small>
      </div>
      <div class="quick-create-actions">
        <el-button :loading="routeLoading" @click="planSelectedRoute()">规划路线</el-button>
        <el-button type="primary" :loading="routeCommandLoading" @click="sendSelectedRoute">下发新路线</el-button>
      </div>
      <button class="dispatch-push-button primary" type="button" @click="dialogVisible = true">
        <span class="push-shadow"></span>
        <span class="push-edge"></span>
        <span class="push-front"><el-icon><Promotion /></el-icon><span>下发调度指令</span></span>
      </button>
      <button class="dispatch-push-button secondary" type="button" @click="contactDriver">
        <span class="push-shadow"></span>
        <span class="push-edge"></span>
        <span class="push-front"><el-icon><Phone /></el-icon><span>联系司机</span></span>
      </button>
      <div class="receipt-preview">
        <span class="section-kicker">COMMAND RECEIPTS</span>
        <h4>最近指令回执</h4>
        <div v-for="item in commands.filter(c => c.plate === selected?.plate).slice(0, 3)" :key="item.id">
          <span><strong>{{ item.type }}</strong><small>{{ item.createdAt }}</small></span>
          <b :class="item.status.toLowerCase()">{{ commandStatus[item.status] }}</b>
        </div>
        <p v-if="!commands.some(c => c.plate === selected?.plate)">暂无调度记录</p>
      </div>
    </aside>

    <el-dialog v-model="dialogVisible" title="下发调度指令" width="480px">
      <el-form label-position="top">
        <el-form-item label="目标车辆"><el-input :model-value="selected?.plate" disabled /></el-form-item>
        <el-form-item label="指令类型">
          <el-select v-model="command.type" style="width: 100%">
            <el-option label="路线调整" value="ROUTE_ADJUST" /><el-option label="停靠指令" value="STOP" /><el-option label="返仓指令" value="RETURN" />
          </el-select>
        </el-form-item>
        <el-form-item label="调度说明"><el-input v-model="command.content" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCommand">下发至车载终端</el-button></template>
    </el-dialog>

    <el-dialog v-model="phoneDialogVisible" :title="`联系 ${phoneDialog.name}`" width="360px" class="phone-dialog">
      <div class="phone-dialog-body">
        <span>联系电话</span>
        <strong>{{ phoneDialog.phone }}</strong>
      </div>
      <template #footer>
        <el-button type="primary" @click="phoneDialogVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>
