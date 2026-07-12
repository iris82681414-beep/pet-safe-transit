<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLogisticsStore } from '@/stores/logistics'
import type { Vehicle } from '@/types'

interface DriverProfile {
  key: string
  name: string
  phone: string
  vehicles: Vehicle[]
  onlineVehicles: number
  activeTasks: number
  manual: boolean
}

const store = useLogisticsStore()
const { vehicles, cargo, onlineCount, transitCount } = storeToRefs(store)
const failedVehicleImages = ref<Set<string>>(new Set())
const driverDialogVisible = ref(false)
const driverSaving = ref(false)
const deletingDriverKey = ref('')
const manualDriverStorageKey = 'smart-logistics-manual-drivers'
const noDriverText = '未分配司机'
const noPhoneText = '暂无电话'
const driverForm = reactive({ name: '', phone: '' })

interface ManualDriver {
  id: string
  name: string
  phone: string
}

function restoreManualDrivers() {
  try {
    const saved = JSON.parse(localStorage.getItem(manualDriverStorageKey) || '[]') as ManualDriver[]
    if (Array.isArray(saved)) return saved
  } catch {
    localStorage.removeItem(manualDriverStorageKey)
  }
  return []
}

const manualDrivers = ref<ManualDriver[]>(restoreManualDrivers())

function syncManualDrivers() {
  manualDrivers.value = restoreManualDrivers()
}

onMounted(() => window.addEventListener('smart-logistics:drivers-changed', syncManualDrivers))
onUnmounted(() => window.removeEventListener('smart-logistics:drivers-changed', syncManualDrivers))

const statusText = { IN_TRANSIT: '运输中', IDLE: '待命', OFFLINE: '离线' }

watch(manualDrivers, () => {
  localStorage.setItem(manualDriverStorageKey, JSON.stringify(manualDrivers.value))
}, { deep: true })

function profileKey(name: string, phone: string, fallback: string) {
  const normalizedPhone = phone.trim()
  if (normalizedPhone && normalizedPhone !== noPhoneText) return `phone:${normalizedPhone}`
  return `name:${name.trim() || noDriverText}:${fallback}`
}

const drivers = computed(() => {
  const profiles = new Map<string, DriverProfile>()
  manualDrivers.value.forEach((driver) => {
    const name = driver.name.trim() || noDriverText
    const phone = driver.phone.trim() || noPhoneText
    const key = profileKey(name, phone, driver.id)
    profiles.set(key, { key, name, phone, vehicles: [], onlineVehicles: 0, activeTasks: 0, manual: true })
  })
  vehicles.value.forEach((vehicle) => {
    const name = vehicle.driver?.trim() || noDriverText
    const phone = vehicle.phone?.trim() || noPhoneText
    const key = profileKey(name, phone, vehicle.plate)
    if (!profiles.has(key)) {
      profiles.set(key, { key, name, phone, vehicles: [], onlineVehicles: 0, activeTasks: 0, manual: false })
    }
    const profile = profiles.get(key)
    if (!profile) return
    profile.manual = profile.manual || manualDrivers.value.some((driver) => profileKey(driver.name, driver.phone || noPhoneText, driver.id) === key)
    profile.vehicles.push(vehicle)
    if (vehicle.status !== 'OFFLINE') profile.onlineVehicles += 1
    if (vehicle.cargoId || cargo.value.some((item) => item.vehiclePlate === vehicle.plate)) {
      profile.activeTasks += 1
    }
  })
  return Array.from(profiles.values()).sort((a, b) => {
    if (a.name === noDriverText && b.name !== noDriverText) return 1
    if (b.name === noDriverText && a.name !== noDriverText) return -1
    if (a.activeTasks !== b.activeTasks) return b.activeTasks - a.activeTasks
    return a.name.localeCompare(b.name, 'zh-CN')
  })
})

const assignedDriverCount = computed(() => drivers.value.filter((item) => item.name !== noDriverText).length)
const boundCargoCount = computed(() => cargo.value.filter((item) => item.vehiclePlate).length)

function vehiclePlates(profile: DriverProfile) {
  return profile.vehicles.map((vehicle) => vehicle.plate).join('、') || '暂无车辆'
}

function formatCapacity(value?: number) {
  if (value == null) return '未填写'
  return `${value} 吨`
}

function vehicleTask(vehicle: Vehicle) {
  return vehicle.cargoId || cargo.value.find((item) => item.vehiclePlate === vehicle.plate)?.id || '暂无任务'
}

function vehicleLocation(vehicle: Vehicle) {
  if (vehicle.location && vehicle.location !== '暂无位置') return vehicle.location
  if (Number.isFinite(vehicle.lat) && Number.isFinite(vehicle.lng)) return `${vehicle.lat.toFixed(5)}, ${vehicle.lng.toFixed(5)}`
  return '暂无位置'
}

function vehicleImageAvailable(vehicle: Vehicle) {
  return Boolean(vehicle.image && !failedVehicleImages.value.has(vehicle.plate))
}

function markVehicleImageFailed(plate: string) {
  const failed = new Set(failedVehicleImages.value)
  failed.add(plate)
  failedVehicleImages.value = failed
}

function openDriverDialog() {
  Object.assign(driverForm, { name: '', phone: '' })
  driverDialogVisible.value = true
}

function addDriver() {
  const name = driverForm.name.trim()
  const phone = driverForm.phone.trim()
  if (!name) return ElMessage.warning('请填写司机姓名')
  if (!phone) return ElMessage.warning('请填写联系电话')
  const exists = drivers.value.some((driver) => driver.name === name || driver.phone === phone)
  if (exists) return ElMessage.warning('该司机姓名或手机号已存在')
  manualDrivers.value.unshift({ id: `DRV-${Date.now()}`, name, phone })
  driverDialogVisible.value = false
  ElMessage.success('司机已添加')
}

async function deleteDriver(profile: DriverProfile) {
  if (profile.name === noDriverText) return ElMessage.warning('未分配司机只是车辆占位信息，不能删除')
  const vehicleCount = profile.vehicles.length
  try {
    await ElMessageBox.confirm(
      vehicleCount
        ? `确定删除司机「${profile.name}」吗？将同步清空 ${vehicleCount} 辆车上的司机姓名和手机号。`
        : `确定删除司机「${profile.name}」吗？`,
      '删除司机',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  deletingDriverKey.value = profile.key
  try {
    for (const vehicle of profile.vehicles) {
      await store.updateVehicle(vehicle.plate, { driver: '', phone: '' })
    }
    manualDrivers.value = manualDrivers.value.filter((driver) => profileKey(driver.name, driver.phone || noPhoneText, driver.id) !== profile.key)
    ElMessage.success('司机已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '司机删除失败')
  } finally {
    deletingDriverKey.value = ''
  }
}
</script>

<template>
  <div class="view-stack personnel-page">
    <section class="filter-strip personnel-head">
      <div class="personnel-banner-copy">
        <span class="section-kicker">PET TRANSPORT STAFF & FLEET</span>
        <h2>司机 / 照护员管理</h2>
        <p class="muted">工作人员可查看运输人员档案、照护资质、车辆归属与当前宠物托运任务。</p>
        <div class="personnel-banner-chips"><span>人员档案</span><span>照护职责</span><span>托运任务</span></div>
      </div>
    </section>

    <section class="warehouse-stats personnel-stats">
      <div><span class="vehicle-badge"><el-icon><UserFilled /></el-icon></span><p><strong>{{ assignedDriverCount }}</strong><span>司机人数</span></p></div>
      <div><span class="vehicle-badge green-bg"><el-icon><Van /></el-icon></span><p><strong>{{ vehicles.length }}</strong><span>车辆总数</span></p></div>
      <div><span class="vehicle-badge orange-bg"><el-icon><Connection /></el-icon></span><p><strong>{{ onlineCount }}</strong><span>在线车辆</span></p></div>
      <div><span class="vehicle-badge purple-bg"><el-icon><Link /></el-icon></span><p><strong>{{ boundCargoCount }}</strong><span>承运任务</span></p></div>
    </section>

    <section class="personnel-layout">
      <article class="panel personnel-table-card">
        <div class="panel-head">
          <div><span class="section-kicker">DRIVER & CARE STAFF</span><h3>运输司机与照护人员</h3></div>
          <div class="personnel-head-actions">
            <el-button type="primary" icon="Plus" @click="openDriverDialog">添加司机</el-button>
            <span class="personnel-stat-note">{{ transitCount }} 辆运输中</span>
          </div>
        </div>
        <div class="personnel-table-scroll">
          <div class="data-table driver-detail-table">
            <div class="table-row table-head"><span>工作人员</span><span>联系电话</span><span>绑定车辆</span><span>在线车辆</span><span>当前托运任务</span><span>操作</span></div>
            <div v-for="driver in drivers" :key="driver.key" class="table-row">
              <span class="table-primary">
                <i class="personnel-avatar">{{ driver.name.slice(0, 1) }}</i>
                <b>{{ driver.name }}</b>
              </span>
              <span>{{ driver.phone }}</span>
              <span>{{ vehiclePlates(driver) }}<small>{{ driver.vehicles.length }} 辆</small></span>
              <span>{{ driver.onlineVehicles }} / {{ driver.vehicles.length }}</span>
              <span>{{ driver.activeTasks ? `${driver.activeTasks} 个任务` : '暂无任务' }}</span>
              <span>
                <el-button
                  link
                  type="danger"
                  :loading="deletingDriverKey === driver.key"
                  :disabled="driver.name === noDriverText"
                  @click="deleteDriver(driver)"
                >
                  删除
                </el-button>
              </span>
            </div>
            <el-empty v-if="!drivers.length" description="暂无司机数据" />
          </div>
        </div>
      </article>

      <article class="panel personnel-table-card">
        <div class="panel-head">
          <div><span class="section-kicker">VEHICLE DETAILS</span><h3>车辆详细情况</h3></div>
          <span class="personnel-stat-note">数据来自车辆接口</span>
        </div>
        <div class="personnel-table-scroll">
          <div class="data-table vehicle-detail-table">
            <div class="table-row table-head"><span>车辆</span><span>司机</span><span>手机号</span><span>车型/载重</span><span>当前位置</span><span>任务</span></div>
            <div v-for="vehicle in vehicles" :key="vehicle.plate" class="table-row">
              <span class="table-primary">
                <i class="vehicle-table-image">
                  <img v-if="vehicleImageAvailable(vehicle)" :src="vehicle.image" alt="" @error="markVehicleImageFailed(vehicle.plate)" />
                  <el-icon v-else><Van /></el-icon>
                </i>
                <b>{{ vehicle.plate }}</b>
              </span>
              <span>{{ vehicle.driver || '未分配司机' }}</span>
              <span>{{ vehicle.phone || '暂无电话' }}</span>
              <span>{{ vehicle.vehicleType || '未填写' }}<small>{{ formatCapacity(vehicle.capacity) }}</small></span>
              <span>{{ vehicleLocation(vehicle) }}<small>{{ statusText[vehicle.status] }} · {{ vehicle.speed }} km/h</small></span>
              <span>{{ vehicleTask(vehicle) }}</span>
            </div>
            <el-empty v-if="!vehicles.length" description="暂无车辆数据" />
          </div>
        </div>
      </article>
    </section>

    <el-dialog v-model="driverDialogVisible" title="添加司机" width="420px">
      <el-form label-position="top">
        <el-form-item label="司机姓名"><el-input v-model="driverForm.name" placeholder="例如：张建国" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="driverForm.phone" placeholder="例如：13800000000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="driverDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="driverSaving" @click="addDriver">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>
