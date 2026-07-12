<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useLogisticsStore } from '@/stores/logistics'
import type { Vehicle } from '@/types'

const personnelBannerImage = new URL('../assets/personnel-warehouse.jpg', import.meta.url).href


interface DriverProfile {
  name: string
  phone: string
  vehicles: Vehicle[]
  onlineVehicles: number
  activeTasks: number
}

const store = useLogisticsStore()
const { vehicles, cargo, onlineCount, transitCount } = storeToRefs(store)
const failedVehicleImages = ref<Set<string>>(new Set())

const statusText = { IN_TRANSIT: '运输中', IDLE: '待命', OFFLINE: '离线' }

const drivers = computed(() => {
  const profiles = new Map<string, DriverProfile>()
  vehicles.value.forEach((vehicle) => {
    const name = vehicle.driver?.trim() || '未分配司机'
    const phone = vehicle.phone?.trim() || '暂无电话'
    const key = phone !== '暂无电话' ? phone : `${name}-${vehicle.plate}`
    if (!profiles.has(key)) {
      profiles.set(key, { name, phone, vehicles: [], onlineVehicles: 0, activeTasks: 0 })
    }
    const profile = profiles.get(key)
    if (!profile) return
    profile.vehicles.push(vehicle)
    if (vehicle.status !== 'OFFLINE') profile.onlineVehicles += 1
    if (vehicle.cargoId || cargo.value.some((item) => item.vehiclePlate === vehicle.plate)) {
      profile.activeTasks += 1
    }
  })
  return Array.from(profiles.values()).sort((a, b) => b.activeTasks - a.activeTasks)
})

const assignedDriverCount = computed(() => drivers.value.filter((item) => item.name !== '未分配司机').length)
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
</script>

<template>
  <div class="view-stack personnel-page">
    <section class="filter-strip personnel-head">
      <div class="personnel-banner-copy">
        <span class="section-kicker">PERSONNEL & FLEET</span>
        <h2>人员管理</h2>
        <p class="muted">查看司机档案、车辆归属与当前任务状态。</p>
        <div class="personnel-banner-chips"><span>司机档案</span><span>车辆归属</span><span>任务状态</span></div>
      </div>
      <div class="personnel-banner-media" aria-hidden="true">
        <img :src="personnelBannerImage" alt="" />
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
          <div><span class="section-kicker">DRIVER DETAILS</span><h3>司机详细情况</h3></div>
          <span class="personnel-stat-note">{{ transitCount }} 辆运输中</span>
        </div>
        <div class="personnel-table-scroll">
          <div class="data-table driver-detail-table">
            <div class="table-row table-head"><span>司机</span><span>联系电话</span><span>绑定车辆</span><span>在线车辆</span><span>当前任务</span></div>
            <div v-for="driver in drivers" :key="`${driver.name}-${driver.phone}`" class="table-row">
              <span class="table-primary">
                <i class="personnel-avatar">{{ driver.name.slice(0, 1) }}</i>
                <b>{{ driver.name }}</b>
              </span>
              <span>{{ driver.phone }}</span>
              <span>{{ vehiclePlates(driver) }}<small>{{ driver.vehicles.length }} 辆</small></span>
              <span>{{ driver.onlineVehicles }} / {{ driver.vehicles.length }}</span>
              <span>{{ driver.activeTasks ? `${driver.activeTasks} 个任务` : '暂无任务' }}</span>
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
  </div>
</template>
