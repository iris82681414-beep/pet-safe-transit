<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLogisticsStore } from '@/stores/logistics'
import type { Cargo, Vehicle } from '@/types'

const store = useLogisticsStore()
const { vehicles, cargo, devices, onlineCount } = storeToRefs(store)

const selectedPlate = ref('')
const draggedCargoId = ref('')
const truckDropActive = ref(false)
const cargoDialog = ref(false)
const vehicleDialog = ref(false)
const detailCargoId = ref('')

const cargoForm = reactive({
  id: '',
  name: '',
  category: '普通货物',
  weight: 800,
  origin: '上海仓储中心',
  destination: '杭州分拨中心',
  eta: '明天 12:00',
})

const vehicleForm = reactive({
  plate: '',
  driver: '',
  phone: '',
  location: '上海仓储中心',
  vehicleType: 'TRUCK',
  capacity: 10,
  deviceImei: '',
})

watch(vehicles, () => {
  if (!selectedPlate.value && vehicles.value[0]) selectedPlate.value = vehicles.value[0].plate
}, { immediate: true })

function inferCargoWeight(item: Cargo) {
  const storedWeight = Number(item.weight)
  if (Number.isFinite(storedWeight) && storedWeight > 0) return Math.round(storedWeight)
  const seed = Array.from(`${item.id}${item.name}${item.category}`).reduce((sum, char) => sum + char.charCodeAt(0), 0)
  const baseWeights = [680, 920, 1180, 1460]
  return baseWeights[seed % baseWeights.length] + (seed % 180)
}

watch(cargo, () => {
  cargo.value.forEach((item) => {
    if (!item.weight || item.weight <= 0) item.weight = inferCargoWeight(item)
  })
}, { immediate: true, deep: true })

const selectedVehicle = computed(() => vehicles.value.find((item) => item.plate === selectedPlate.value) || vehicles.value[0])
const unassignedCargo = computed(() => cargo.value.filter((item) => !item.vehiclePlate))
const selectedVehicleCargo = computed(() => cargo.value.filter((item) => item.vehiclePlate === selectedVehicle.value?.plate))
const selectedCargo = computed(() => cargo.value.find((item) => item.id === detailCargoId.value))
const detailDialog = computed({
  get: () => Boolean(detailCargoId.value),
  set: (value: boolean) => {
    if (!value) detailCargoId.value = ''
  },
})

const loadedWeight = computed(() => selectedVehicleCargo.value.reduce((sum, item) => sum + inferCargoWeight(item), 0))
const capacityKg = computed(() => Math.max(1, (selectedVehicle.value?.capacity || 10) * 1000))
const loadPercent = computed(() => Math.min(100, Math.round((loadedWeight.value / capacityKg.value) * 100)))
const trailerSlots = computed(() => {
  const count = Math.max(8, selectedVehicleCargo.value.length + 2)
  return Array.from({ length: count }, (_, index) => selectedVehicleCargo.value[index])
})

const statusText: Record<Vehicle['status'], string> = {
  IN_TRANSIT: '运输中',
  IDLE: '待命',
  OFFLINE: '离线',
}

const cargoStatus: Record<Cargo['status'], string> = {
  CREATED: '待装货',
  LOADED: '已装货',
  IN_TRANSIT: '运输中',
  DELIVERED: '已送达',
  CANCELLED: '已取消',
}

function startDrag(item: Cargo, event: DragEvent) {
  draggedCargoId.value = item.id
  truckDropActive.value = true
  event.dataTransfer?.setData('application/x-smart-cargo', item.id)
  event.dataTransfer?.setData('text/plain', item.id)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function endDrag() {
  draggedCargoId.value = ''
  truckDropActive.value = false
}

async function dropCargo(event?: DragEvent, cargoId = draggedCargoId.value || event?.dataTransfer?.getData('application/x-smart-cargo') || event?.dataTransfer?.getData('text/plain')) {
  truckDropActive.value = false
  event?.preventDefault()
  event?.stopPropagation()
  const vehicle = selectedVehicle.value
  if (!vehicle || !cargoId) return
  const item = cargo.value.find((entry) => entry.id === cargoId)
  if (!item) return
  if (item.vehiclePlate === vehicle.plate) {
    draggedCargoId.value = ''
    return
  }
  try {
    await store.bindCargo(cargoId, vehicle.plate)
    ElMessage.success(`${item.name} 已装入 ${vehicle.plate}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '装车绑定失败')
  } finally {
    draggedCargoId.value = ''
  }
}

async function unbindCargo(cargoId: string) {
  try {
    await store.unbindCargo(cargoId)
    ElMessage.success('已移出车厢')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '解除绑定失败')
  }
}

async function createCargo() {
  if (!cargoForm.name.trim() || !cargoForm.origin.trim() || !cargoForm.destination.trim()) {
    ElMessage.warning('请填写货物名称和运输路线')
    return
  }
  const id = cargoForm.id.trim() || `YD${new Date().toISOString().slice(0, 10).replace(/-/g, '')}${String(cargo.value.length + 1).padStart(3, '0')}`
  try {
    await store.addCargo({
      id,
      name: cargoForm.name.trim(),
      category: cargoForm.category,
      origin: cargoForm.origin,
      destination: cargoForm.destination,
      progress: 0,
      status: 'CREATED',
      eta: cargoForm.eta,
      weight: cargoForm.weight,
      originLat: 31.2304,
      originLng: 121.4737,
      destinationLat: 30.2741,
      destinationLng: 120.1551,
    })
    Object.assign(cargoForm, { id: '', name: '', category: '普通货物', weight: 800, origin: '上海仓储中心', destination: '杭州分拨中心', eta: '明天 12:00' })
    cargoDialog.value = false
    ElMessage.success('货物已创建')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '货物创建失败')
  }
}

async function createVehicle() {
  if (!vehicleForm.plate.trim() || !vehicleForm.driver.trim()) {
    ElMessage.warning('请填写车牌号和司机')
    return
  }
  const plate = vehicleForm.plate.trim().toUpperCase()
  if (vehicles.value.some((item) => item.plate === plate)) {
    ElMessage.warning('该车辆已存在')
    return
  }
  try {
    await store.addVehicle({
      plate,
      driver: vehicleForm.driver.trim(),
      phone: vehicleForm.phone.trim(),
      status: 'IDLE',
      speed: 0,
      lat: 31.2304,
      lng: 121.4737,
      location: vehicleForm.location,
      heartbeat: '刚刚',
      vehicleType: vehicleForm.vehicleType,
      capacity: vehicleForm.capacity,
      deviceImei: vehicleForm.deviceImei,
    })
    selectedPlate.value = plate
    Object.assign(vehicleForm, { plate: '', driver: '', phone: '', location: '上海仓储中心', vehicleType: 'TRUCK', capacity: 10, deviceImei: '' })
    vehicleDialog.value = false
    ElMessage.success('车辆已新增')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '车辆新增失败')
  }
}

async function removeVehicle(plate: string) {
  try {
    await ElMessageBox.confirm(`确定删除车辆 ${plate} 吗？相关货物会自动解除绑定。`, '删除车辆', { type: 'warning' })
    await store.removeVehicle(plate)
    if (selectedPlate.value === plate) selectedPlate.value = vehicles.value[0]?.plate || ''
    ElMessage.success('车辆已删除')
  } catch {
    // canceled
  }
}
</script>

<template>
  <div class="warehouse-planner view-stack">
    <section class="warehouse-hero">
      <div>
        <span class="section-kicker">LOAD PLANNING</span>
        <h2>仓库装车管理</h2>
        <p>把左侧货物拖进车厢槽位，系统会自动完成货物与车辆绑定。</p>
      </div>
      <div class="warehouse-actions">
        <el-button icon="Box" @click="cargoDialog = true">新建货物</el-button>
        <el-button type="primary" icon="Plus" @click="vehicleDialog = true">新增车辆</el-button>
      </div>
    </section>

    <section class="warehouse-stats">
      <div><span class="vehicle-badge"><el-icon><Van /></el-icon></span><p><strong>{{ vehicles.length }}</strong><span>车辆总数</span></p></div>
      <div><span class="vehicle-badge green-bg"><el-icon><Connection /></el-icon></span><p><strong>{{ onlineCount }}</strong><span>在线设备</span></p></div>
      <div><span class="vehicle-badge orange-bg"><el-icon><Box /></el-icon></span><p><strong>{{ unassignedCargo.length }}</strong><span>待装货物</span></p></div>
      <div><span class="vehicle-badge purple-bg"><el-icon><Link /></el-icon></span><p><strong>{{ cargo.filter(c => c.vehiclePlate).length }}</strong><span>已绑定运单</span></p></div>
    </section>

    <section class="load-workbench">
      <aside class="cargo-pool">
        <div class="panel-title">
          <span>待装货物</span>
          <b>{{ unassignedCargo.length }}</b>
        </div>
        <article
          v-for="item in unassignedCargo"
          :key="item.id"
          class="cargo-drag-card"
          draggable="true"
          @dragstart.stop="startDrag(item, $event)"
          @dragend="endDrag"
          @click="detailCargoId = item.id"
        >
          <strong>{{ item.name }}</strong>
          <span>{{ item.id }} / {{ item.category }}</span>
          <small>{{ item.origin }} -> {{ item.destination }}</small>
          <em>{{ inferCargoWeight(item) }} kg</em>
        </article>
        <el-empty v-if="!unassignedCargo.length" description="暂无待装货物" />
      </aside>

      <main class="truck-planner">
        <div class="vehicle-rail">
          <button
            v-for="vehicle in vehicles"
            :key="vehicle.plate"
            :class="{ active: vehicle.plate === selectedVehicle?.plate }"
            type="button"
            @click="selectedPlate = vehicle.plate"
          >
            <span>{{ vehicle.plate }}</span>
            <small>{{ vehicle.driver }} / {{ statusText[vehicle.status] }}</small>
          </button>
        </div>

        <div class="truck-stage">
          <div class="truck-meta">
            <div>
              <span class="section-kicker">CURRENT VEHICLE</span>
              <h3>{{ selectedVehicle?.plate || '暂无车辆' }}</h3>
              <p>{{ selectedVehicle?.driver || '-' }} / {{ selectedVehicle?.location || '-' }}</p>
            </div>
            <div class="load-meter">
              <strong>{{ loadPercent }}%</strong>
              <span>{{ loadedWeight }} / {{ capacityKg }} kg</span>
            </div>
          </div>

          <div
            class="truck-cross-section"
            :class="{ 'drop-active': truckDropActive }"
            @dragenter.prevent="truckDropActive = true"
            @dragover.prevent="truckDropActive = true"
            @dragleave.self="truckDropActive = false"
            @drop="dropCargo($event)"
          >
            <div class="truck-cab">
              <i></i>
              <span>{{ selectedVehicle?.plate || 'TRUCK' }}</span>
            </div>
            <div class="truck-body">
              <div class="slot-grid">
                <button
                  v-for="(item, index) in trailerSlots"
                  :key="index"
                  class="cargo-slot"
                  :class="{ occupied: item }"
                  type="button"
                  @dragover.prevent
                  @drop.stop="dropCargo($event)"
                  @click="item && (detailCargoId = item.id)"
                >
                  <template v-if="item">
                    <strong>{{ item.name }}</strong>
                    <span>{{ inferCargoWeight(item) }}kg</span>
                  </template>
                  <template v-else>
                    <span>空槽 {{ index + 1 }}</span>
                  </template>
                </button>
              </div>
            </div>
            <div class="truck-wheels"><i></i><i></i><i></i></div>
          </div>
        </div>
      </main>

      <aside class="load-manifest">
        <div class="panel-title">
          <span>装载清单</span>
          <b>{{ selectedVehicleCargo.length }}</b>
        </div>
        <article v-for="item in selectedVehicleCargo" :key="item.id" class="manifest-row">
          <div>
            <strong>{{ item.name }}</strong>
            <span>{{ item.id }} / {{ cargoStatus[item.status] }}</span>
          </div>
          <el-button link type="warning" @click="unbindCargo(item.id)">移出</el-button>
        </article>
        <el-empty v-if="!selectedVehicleCargo.length" description="车厢暂无货物" />
        <div class="device-summary">
          <span>车载终端</span>
          <strong>{{ devices.find(d => d.plate === selectedVehicle?.plate)?.imei || selectedVehicle?.deviceImei || '未绑定' }}</strong>
        </div>
        <el-button v-if="selectedVehicle" text type="danger" @click="removeVehicle(selectedVehicle.plate)">删除当前车辆</el-button>
      </aside>
    </section>

    <el-dialog v-model="cargoDialog" title="新建货物" width="520px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="货物编号"><el-input v-model="cargoForm.id" placeholder="留空自动生成" /></el-form-item>
          <el-form-item label="货物名称"><el-input v-model="cargoForm.name" /></el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="货物类型"><el-input v-model="cargoForm.category" /></el-form-item>
          <el-form-item label="重量 kg"><el-input-number v-model="cargoForm.weight" :min="0" /></el-form-item>
        </div>
        <el-form-item label="始发地"><el-input v-model="cargoForm.origin" /></el-form-item>
        <el-form-item label="目的地"><el-input v-model="cargoForm.destination" /></el-form-item>
        <el-form-item label="预计到达"><el-input v-model="cargoForm.eta" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cargoDialog = false">取消</el-button>
        <el-button type="primary" @click="createCargo">创建货物</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="vehicleDialog" title="新增车辆" width="520px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="车牌号"><el-input v-model="vehicleForm.plate" placeholder="如：沪A-C0291" /></el-form-item>
          <el-form-item label="司机"><el-input v-model="vehicleForm.driver" /></el-form-item>
        </div>
        <el-form-item label="联系电话"><el-input v-model="vehicleForm.phone" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="车辆类型"><el-input v-model="vehicleForm.vehicleType" /></el-form-item>
          <el-form-item label="载重 吨"><el-input-number v-model="vehicleForm.capacity" :min="1" /></el-form-item>
        </div>
        <el-form-item label="设备 IMEI"><el-input v-model="vehicleForm.deviceImei" /></el-form-item>
        <el-form-item label="当前位置"><el-input v-model="vehicleForm.location" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="vehicleDialog = false">取消</el-button>
        <el-button type="primary" @click="createVehicle">确认新增</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialog" title="货物详情" width="480px">
      <div v-if="selectedCargo" class="cargo-detail-dialog">
        <div class="cargo-detail-title">
          <span class="vehicle-badge orange-bg"><el-icon><Box /></el-icon></span>
          <div><strong>{{ selectedCargo.name }}</strong><small>{{ selectedCargo.id }} / {{ selectedCargo.category }}</small></div>
          <span class="status-chip success">{{ cargoStatus[selectedCargo.status] }}</span>
        </div>
        <dl class="info-list">
          <div><dt>始发地</dt><dd>{{ selectedCargo.origin }}</dd></div>
          <div><dt>目的地</dt><dd>{{ selectedCargo.destination }}</dd></div>
          <div><dt>承运车辆</dt><dd>{{ selectedCargo.vehiclePlate || '暂未装车' }}</dd></div>
          <div><dt>预计到达</dt><dd>{{ selectedCargo.eta }}</dd></div>
          <div><dt>重量</dt><dd>{{ selectedCargo ? inferCargoWeight(selectedCargo) : 0 }} kg</dd></div>
        </dl>
      </div>
      <template #footer>
        <el-button @click="detailCargoId = ''">关闭</el-button>
        <el-button v-if="selectedCargo?.vehiclePlate" type="warning" @click="unbindCargo(selectedCargo.id); detailCargoId = ''">移出车厢</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.warehouse-planner {
  --warehouse-ink: #102820;
  --warehouse-muted: #637c74;
  --warehouse-line: rgba(39, 121, 96, .16);
  --warehouse-mint: #30e39a;
  --warehouse-cyan: #6de7ff;
  --warehouse-panel: #fbfefd;
  color: var(--warehouse-ink);
}
.warehouse-hero,
.load-workbench,
.warehouse-stats {
  border: 1px solid var(--warehouse-line);
  border-radius: 8px;
  background: rgba(255, 255, 255, .94);
  box-shadow:
    0 24px 70px rgba(15, 40, 34, .09),
    0 1px 0 rgba(255, 255, 255, .9) inset,
    0 -2px 6px rgba(10, 37, 31, .06) inset;
}
.warehouse-hero {
  position: relative;
  min-height: 126px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 26px;
  background:
    radial-gradient(circle at 86% 18%, rgba(109, 231, 255, .26), transparent 34%),
    radial-gradient(circle at 30% 120%, rgba(48, 227, 154, .18), transparent 36%),
    linear-gradient(120deg, rgba(241, 249, 245, .98), rgba(229, 246, 241, .95)),
    url('../../登录绿色亮点.jpg') center / cover;
  overflow: hidden;
}
.warehouse-hero::after {
  content: '';
  position: absolute;
  inset: 1px;
  pointer-events: none;
  border-radius: 7px;
  background: repeating-conic-gradient(from 18deg, rgba(255,255,255,.0) 0 .0001%, rgba(61, 115, 102, .22) .00015% .00028%) 60% 60% / 700% 700%;
  opacity: .16;
}
.warehouse-hero > * { position: relative; z-index: 1; }
.warehouse-hero h2 { margin: 6px 0 8px; font-size: 28px; color: #10241f; }
.warehouse-hero p { margin: 0; color: #66827a; }
.warehouse-actions { display: flex; gap: 10px; }
.warehouse-actions :deep(.el-button),
.cargo-detail-dialog + :deep(.dialog-footer .el-button) {
  position: relative;
}
.warehouse-planner :deep(.el-button) {
  height: 36px;
  border-radius: 18px;
  font-weight: 800;
}
.warehouse-actions :deep(.el-button),
.warehouse-planner :deep(.el-dialog__footer .el-button:not(.is-link):not(.is-circle)) {
  min-width: 116px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, .86) !important;
  color: #0d3026 !important;
  background:
    radial-gradient(circle at 12% 15%, rgba(255, 255, 255, .9), transparent 34%),
    linear-gradient(135deg, #f4fff9, #bbffdc 48%, #85eff5) !important;
  box-shadow:
    inset 0 0 14px rgba(255, 255, 255, .72),
    inset 0 -4px 4px rgba(12, 58, 48, .12),
    0 3px 0 rgba(21, 111, 83, .5),
    0 12px 26px rgba(32, 181, 125, .18) !important;
  transition: transform .2s ease, box-shadow .2s ease;
}
.warehouse-actions :deep(.el-button::before),
.warehouse-planner :deep(.el-dialog__footer .el-button:not(.is-link):not(.is-circle)::before) {
  content: '';
  position: absolute;
  left: -70%;
  top: -170%;
  width: 240%;
  aspect-ratio: 1;
  background: radial-gradient(ellipse at 65% 180%, #fff, #54ffc0, #6de7ff, #fff, #34d399, #fff);
  mix-blend-mode: soft-light;
  animation: warehouse-button-spin 9s linear infinite;
}
.warehouse-actions :deep(.el-button span),
.warehouse-actions :deep(.el-button .el-icon),
.warehouse-planner :deep(.el-dialog__footer .el-button span) {
  position: relative;
  z-index: 1;
}
.warehouse-actions :deep(.el-button:hover) {
  transform: translateY(-2px);
  box-shadow: inset 0 0 16px rgba(255, 255, 255, .86), 0 16px 32px rgba(32, 181, 125, .24);
}
@keyframes warehouse-button-spin { to { transform: rotate(360deg); } }
.warehouse-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 12px;
  background:
    linear-gradient(180deg, rgba(252, 255, 254, .96), rgba(238, 248, 244, .9));
}
.warehouse-stats > div {
  position: relative;
  min-height: 86px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px;
  overflow: hidden;
  border: 1px solid rgba(35, 112, 92, .12);
  border-radius: 8px;
  background: rgba(255, 255, 255, .78);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.95), 0 12px 28px rgba(25,83,66,.06);
}
.warehouse-stats > div::after {
  content: '';
  position: absolute;
  inset: auto -20px -44px auto;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(48, 227, 154, .18), transparent 68%);
}
.warehouse-stats p { position: relative; z-index: 1; margin: 0; display: grid; gap: 3px; }
.warehouse-stats strong { color: #102820; font-size: 24px; line-height: 1; }
.warehouse-stats span { color: #6b817b; font-size: 12px; }
.load-workbench {
  min-height: 560px;
  display: grid;
  grid-template-columns: 268px minmax(0, 1fr) 268px;
  gap: 0;
  overflow: hidden;
}
.cargo-pool,
.load-manifest {
  padding: 18px;
  background:
    linear-gradient(180deg, rgba(252, 255, 254, .92), rgba(239, 249, 245, .82));
}
.cargo-pool { border-right: 1px solid rgba(30, 103, 85, .12); }
.load-manifest { border-left: 1px solid rgba(30, 103, 85, .12); }
.panel-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; color: #18312b; font-weight: 800; }
.panel-title b { min-width: 28px; height: 24px; display: grid; place-items: center; border-radius: 6px; color: #0f5d42; background: #dcf8ea; }
.cargo-drag-card,
.manifest-row,
.vehicle-rail button {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(35, 112, 92, .14);
  border-radius: 8px;
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 10px 24px rgba(25, 83, 66, .06);
}
.cargo-drag-card::before,
.manifest-row::before,
.vehicle-rail button::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: repeating-conic-gradient(from 20deg, rgba(255,255,255,0) 0 .00012%, rgba(44, 111, 94, .28) .00018% .0003%) 60% 60% / 650% 650%;
  opacity: .12;
}
.cargo-drag-card > *,
.manifest-row > *,
.vehicle-rail button > * {
  position: relative;
  z-index: 1;
}
.cargo-drag-card {
  position: relative;
  display: grid;
  gap: 5px;
  margin-bottom: 10px;
  padding: 13px;
  cursor: grab;
  transition: transform .18s, box-shadow .18s, border-color .18s;
}
.cargo-drag-card:hover { transform: translateY(-2px); border-color: rgba(28, 180, 123, .42); box-shadow: 0 12px 30px rgba(30, 88, 70, .12); }
.cargo-drag-card strong { color: #17342d; }
.cargo-drag-card span,
.cargo-drag-card small { color: #6b817b; }
.cargo-drag-card em { position: absolute; top: 12px; right: 12px; color: #0f8f61; font-style: normal; font-size: 12px; font-weight: 800; }
.truck-planner { min-width: 0; padding: 18px 22px 24px; background: linear-gradient(180deg, #fbfdfc, #eef7f4); }
.vehicle-rail { display: flex; gap: 10px; overflow-x: auto; padding-bottom: 12px; }
.vehicle-rail button {
  flex: 0 0 158px;
  min-height: 62px;
  padding: 10px 12px;
  text-align: left;
}
.vehicle-rail button.active {
  border-color: #2fd18f;
  background:
    radial-gradient(circle at 80% 15%, rgba(109,231,255,.2), transparent 38%),
    #e9fff4;
  box-shadow: inset 0 0 0 1px rgba(47, 209, 143, .28), 0 14px 28px rgba(32, 181, 125, .12);
}
.vehicle-rail span,
.vehicle-rail small { display: block; }
.vehicle-rail span { color: #14352d; font-weight: 900; }
.vehicle-rail small { margin-top: 5px; color: #6c837c; }
.truck-stage { padding-top: 10px; }
.truck-meta { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 22px; }
.truck-meta h3 { margin: 6px 0; font-size: 30px; color: #142821; }
.truck-meta p { margin: 0; color: #6c817c; }
.load-meter { text-align: right; }
.load-meter strong { display: block; color: #109060; font-size: 30px; }
.load-meter span { color: #6b817b; font-size: 12px; }
.truck-cross-section {
  position: relative;
  min-height: 292px;
  display: grid;
  grid-template-columns: 142px minmax(0, 1fr);
  align-items: end;
  gap: 10px;
  padding: 28px 20px 48px;
  border: 1px dashed rgba(31, 126, 94, .32);
  border-radius: 8px;
  background:
    radial-gradient(circle at 78% 18%, rgba(109, 231, 255, .18), transparent 35%),
    linear-gradient(180deg, rgba(240, 247, 244, .4), rgba(221, 236, 231, .7)),
    linear-gradient(90deg, rgba(37, 106, 84, .05) 1px, transparent 1px),
    linear-gradient(rgba(37, 106, 84, .05) 1px, transparent 1px);
  background-size: auto, 34px 34px, 34px 34px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .62), inset 0 -28px 44px rgba(20, 82, 62, .08);
  transition: border-color .2s ease, box-shadow .2s ease, background-color .2s ease;
}
.truck-cross-section.drop-active {
  border-color: rgba(48, 227, 154, .86);
  box-shadow: inset 0 0 0 2px rgba(48, 227, 154, .28), 0 0 38px rgba(48, 227, 154, .22);
}
.truck-cab {
  height: 146px;
  align-self: end;
  display: grid;
  place-items: center;
  border-radius: 70px 18px 12px 14px;
  color: #12342c;
  background: linear-gradient(135deg, #ffffff, #e5ece9);
  border: 1px solid rgba(20, 56, 48, .14);
  box-shadow: 0 16px 28px rgba(24, 61, 51, .14);
}
.truck-cab i { width: 54px; height: 42px; border-radius: 20px 8px 8px 14px; background: #13251f; }
.truck-cab span { font-size: 12px; font-weight: 900; }
.truck-body {
  min-width: 0;
  min-height: 202px;
  max-height: 238px;
  overflow-y: auto;
  padding: 12px;
  border-radius: 8px 16px 16px 8px;
  border: 2px solid rgba(22, 58, 49, .22);
  background:
    linear-gradient(90deg, rgba(255,255,255,.55), transparent 14% 86%, rgba(255,255,255,.4)),
    linear-gradient(180deg, #f7faf9, #dce7e3);
  box-shadow: inset 0 -22px 28px rgba(39, 72, 62, .12), 0 18px 34px rgba(20, 49, 41, .14);
}
.slot-grid {
  min-height: 178px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  grid-auto-rows: minmax(72px, 1fr);
  gap: 8px;
}
.cargo-slot {
  min-width: 0;
  min-height: 72px;
  padding: 8px 7px;
  border: 1px dashed rgba(42, 119, 97, .28);
  border-radius: 6px;
  color: #7b918a;
  background: rgba(255, 255, 255, .62);
  transition: transform .18s ease, border-color .18s ease, background .18s ease, box-shadow .18s ease;
}
.cargo-slot.occupied {
  color: #10342b;
  border-style: solid;
  border-color: rgba(31, 184, 123, .55);
  background:
    radial-gradient(circle at 82% 12%, rgba(109, 231, 255, .28), transparent 42%),
    linear-gradient(135deg, rgba(223, 249, 236, .98), rgba(255, 255, 255, .96));
  box-shadow: inset 0 0 16px rgba(48, 227, 154, .16), 0 8px 18px rgba(31, 126, 94, .12);
}
.cargo-slot:hover {
  transform: translateY(-1px);
  border-color: rgba(48, 227, 154, .62);
}
.cargo-slot strong,
.cargo-slot span { display: block; }
.cargo-slot strong {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cargo-slot span { margin-top: 5px; font-size: 11px; }
.truck-wheels { position: absolute; left: 98px; right: 60px; bottom: 20px; display: flex; justify-content: space-between; pointer-events: none; }
.truck-wheels i { width: 46px; height: 46px; border: 8px solid #17241f; border-radius: 50%; background: #6c7672; box-shadow: inset 0 0 0 6px #d5dedb; }
.manifest-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 9px; padding: 11px; }
.manifest-row strong,
.manifest-row span { display: block; }
.manifest-row strong { color: #18312b; }
.manifest-row span { margin-top: 4px; color: #728981; font-size: 12px; }
.device-summary { margin: 18px 0 12px; padding: 13px; border-radius: 8px; background: #eef8f3; }
.device-summary span,
.device-summary strong { display: block; }
.device-summary span { color: #6b817b; font-size: 12px; }
.device-summary strong { margin-top: 5px; color: #17342d; }
@media (max-width: 1180px) {
  .load-workbench { grid-template-columns: 1fr; }
  .cargo-pool,
  .load-manifest { border: 0; }
  .truck-cross-section { grid-template-columns: 130px minmax(0, 1fr); overflow-x: auto; }
}
@media (max-width: 720px) {
  .warehouse-hero { align-items: flex-start; flex-direction: column; }
  .warehouse-actions { width: 100%; }
  .warehouse-actions .el-button { flex: 1; }
  .slot-grid { grid-template-columns: repeat(2, minmax(120px, 1fr)); }
  .truck-cross-section { grid-template-columns: 1fr; }
  .truck-cab { display: none; }
}
</style>
