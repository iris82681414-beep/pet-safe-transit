<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useLogisticsStore } from '@/stores/logistics'
import type { Cargo } from '@/types'


const store = useLogisticsStore()
const { cargo, commands } = storeToRefs(store)
const selectedCargoId = ref(cargo.value.find((item) => item.vehiclePlate)?.id || '')
const nextStatus = ref<Cargo['status']>('IN_TRANSIT')
const selectedCargo = computed(() => cargo.value.find((item) => item.id === selectedCargoId.value))
const commandStatus = { SENT: '已发送', RECEIVED: '待执行', EXECUTED: '已执行', REJECTED: '已拒绝', FAILED: '失败' }
const cargoStatus = { CREATED: '待装货', LOADED: '已装货', IN_TRANSIT: '运输中', DELIVERED: '已送达', CANCELLED: '已取消' }

async function reportStatus() {
  if (!selectedCargoId.value) return
  try {
    await store.updateCargoStatus(selectedCargoId.value, nextStatus.value)
    ElMessage.success(`状态已上报：${cargoStatus[nextStatus.value]}`)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '状态上报失败') }
}

function execute(id: string) {
  store.executeCommand(id)
  ElMessage.success('指令已确认执行，回执已发送')
}
</script>

<template>
  <div class="view-stack">
    <section class="driver-hero">
      <div><span class="section-kicker">DRIVER WORKBENCH</span><h2>司机任务工作台</h2><p>上报货物状态、查看调度指令并发送执行回执。</p></div>
      <div class="driver-online"><i></i><span>车载终端在线</span><strong>心跳刚刚</strong></div>
    </section>

    <section class="driver-grid">
      <article class="panel status-report-card">
        <div class="panel-head"><div><span class="section-kicker">STATUS REPORT</span><h3>货物状态上报</h3></div><el-icon><UploadFilled /></el-icon></div>
        <el-form label-position="top" size="large">
          <el-form-item label="当前运单">
            <el-select v-model="selectedCargoId" style="width: 100%">
              <el-option v-for="item in cargo.filter(c => c.vehiclePlate)" :key="item.id" :label="`${item.id} · ${item.name}`" :value="item.id" />
            </el-select>
          </el-form-item>
          <div v-if="selectedCargo" class="current-shipment">
            <div><span>承运车辆</span><strong>{{ selectedCargo.vehiclePlate }}</strong></div>
            <div><span>当前状态</span><strong>{{ cargoStatus[selectedCargo.status] }}</strong></div>
            <div><span>目的地</span><strong>{{ selectedCargo.destination }}</strong></div>
          </div>
          <el-form-item label="更新为">
            <div
              class="pill-radio-container driver-status-pills"
              :class="`status-${nextStatus.toLowerCase().replace('_', '-')}`"
            >
              <input id="driver-status-loaded" v-model="nextStatus" type="radio" value="LOADED">
              <label for="driver-status-loaded">已装货</label>
              <input id="driver-status-transit" v-model="nextStatus" type="radio" value="IN_TRANSIT">
              <label for="driver-status-transit">运输中</label>
              <input id="driver-status-delivered" v-model="nextStatus" type="radio" value="DELIVERED">
              <label for="driver-status-delivered">已送达</label>
              <span class="pill-indicator"></span>
            </div>
          </el-form-item>
          <el-button type="primary" icon="Upload" @click="reportStatus">确认上报状态</el-button>
        </el-form>
      </article>

      <article class="panel command-tasks">
        <div class="panel-head"><div><span class="section-kicker">COMMAND INBOX</span><h3>调度指令与执行回执</h3></div><span class="live-text"><i></i>实时接收</span></div>
        <div class="command-list">
          <article v-for="item in commands" :key="item.id">
            <span class="command-icon"><el-icon><Promotion /></el-icon></span>
            <div class="command-main"><span>{{ item.id }} · {{ item.createdAt }}</span><strong>{{ item.type }} · {{ item.plate }}</strong><p>{{ item.content }}</p></div>
            <span class="command-status" :class="item.status.toLowerCase()">{{ commandStatus[item.status] }}</span>
            <el-button v-if="item.status === 'RECEIVED'" type="primary" plain @click="execute(item.id)">确认执行</el-button>
            <small v-else-if="item.executedAt">执行于 {{ item.executedAt }}</small>
          </article>
        </div>
      </article>
    </section>

    <section class="panel safety-tip">
      <el-icon><Warning /></el-icon>
      <div><strong>安全提示</strong><p>请在车辆安全停靠后操作工作台。行驶过程中收到的指令将由车载终端语音播报。</p></div>
    </section>
  </div>
</template>
