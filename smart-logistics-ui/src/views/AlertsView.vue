<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useLogisticsStore } from '@/stores/logistics'
import type { AlertItem, AlertSeverity, AlertStatus } from '@/types'
import { alertApi } from '@/services/api'

const store = useLogisticsStore()
const { alerts, archivedAlerts } = storeToRefs(store)
const selected = ref<AlertItem | null>(alerts.value[0] || null)
const severity = ref('')
const status = ref('')
const type = ref('')
const vehiclePlate = ref('')
const actionDialog = ref(false)
const historyDialog = ref(false)
const actionKind = ref<'ACKNOWLEDGED' | 'RESOLVED'>('ACKNOWLEDGED')
const actionForm = reactive({ resolution: '', remark: '' })
const saving = ref(false)
const stats = reactive({ critical: 0, pending: 0, resolved: 0 })
const filtered = computed(() => alerts.value.filter((item) =>
  (!severity.value || item.severity === severity.value)
  && (!status.value || item.status === status.value)
  && (!type.value || item.type === type.value)
  && (!vehiclePlate.value || item.plate === vehiclePlate.value),
))
const alertTypes = computed(() => [...new Set(alerts.value.map((item) => item.type))])
const plates = computed(() => [...new Set(alerts.value.map((item) => item.plate).filter(Boolean))])
const criticalPendingCount = computed(() => alerts.value.filter((item) => item.severity === 'CRITICAL' && item.status === 'PENDING').length)
const pendingCount = computed(() => alerts.value.filter((item) => item.status === 'PENDING').length)
const acknowledgedCount = computed(() => alerts.value.filter((item) => item.status === 'ACKNOWLEDGED').length)
const resolvedCount = computed(() => alerts.value.filter((item) => item.status === 'RESOLVED').length)
const severityText: Record<AlertSeverity, string> = { CRITICAL: '严重', WARNING: '警告', INFO: '提示' }
const statusText: Record<AlertStatus, string> = { PENDING: '待处理', ACKNOWLEDGED: '已确认', RESOLVED: '已关闭' }
function alertTone(typeValue?: string) {
  if (typeValue === 'ROUTE_DEVIATION') return 'alert-tone-route'
  if (typeValue === 'DEVICE_OFFLINE') return 'alert-tone-offline'
  if (typeValue === 'ABNORMAL_STOP') return 'alert-tone-stop'
  return 'alert-tone-other'
}

function formatAlertTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function openAction(statusValue: 'ACKNOWLEDGED' | 'RESOLVED') {
  if (statusValue === 'RESOLVED' && selected.value?.status === 'PENDING') {
    ElMessage.warning('请先确认风险事件，再完成关闭')
    return
  }
  actionKind.value = statusValue
  Object.assign(actionForm, { resolution: '', remark: '' })
  actionDialog.value = true
}

function update() {
  if (saving.value) return
  if (!selected.value || !actionForm.remark.trim()) return ElMessage.warning('请填写处理备注')
  if (actionKind.value === 'ACKNOWLEDGED' && selected.value.status !== 'PENDING') return ElMessage.warning('当前告警不需要重复确认')
  if (actionKind.value === 'RESOLVED' && selected.value.status !== 'ACKNOWLEDGED') return ElMessage.warning('请先确认告警，再关闭告警')
  if (actionKind.value === 'RESOLVED' && !actionForm.resolution.trim()) return ElMessage.warning('请填写解决方案')
  saving.value = true
  const alertId = selected.value.id
  const nextStatus = actionKind.value
  const resolution = actionForm.resolution
  const remark = actionForm.remark
  const target = alerts.value.find((item) => item.id === alertId)
  const previousStatus = target?.status

  if (target) target.status = nextStatus
  selected.value = target || selected.value
  actionDialog.value = false
  saving.value = false
  Object.assign(actionForm, { resolution: '', remark: '' })
  ElMessage.success(nextStatus === 'ACKNOWLEDGED' ? '风险事件已确认，正在后台同步' : '风险事件已关闭，正在后台同步')

  void store.updateAlert(alertId, nextStatus, resolution, remark)
    .then(() => {
      const updated = alerts.value.find((item) => item.id === alertId)
      selected.value = updated || filtered.value[0] || null
      void refreshStats()
    })
    .catch((error) => {
      if (target && previousStatus) target.status = previousStatus
      ElMessage.error(error instanceof Error ? `后台同步失败：${error.message}` : '后台同步失败')
      void store.loadAlerts().catch(() => undefined)
    })
}

function canDeleteAlert(item: AlertItem) {
  return item.status === 'RESOLVED'
}

function deleteAlert(alertId: string, event: MouseEvent) {
  event.stopPropagation()
  const target = alerts.value.find((item) => item.id === alertId)
  if (!target) return
  if (!canDeleteAlert(target)) {
    ElMessage.warning('请先确认并关闭告警后，再删除该条记录')
    return
  }
  store.removeAlerts([alertId])
  if (selected.value?.id === alertId) selected.value = filtered.value[0] || null
  ElMessage.success('风险事件已归档，可在“历史风险”中查看')
}

function simulateAlert() {
  selected.value = store.simulateAlert()
  ElMessage.error('收到一条新的严重告警')
}

async function selectAlert(item: AlertItem) {
  selected.value = item
  if (store.usingDemo) return
  try {
    const detail = await alertApi.detail(item.id)
    item.description = detail.description || detail.summary || item.description
    item.logs = detail.logs
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '告警详情加载失败')
  }
}

async function refreshStats() {
  if (store.usingDemo) return
  try {
    const result = await alertApi.stats()
    stats.critical = result.pending.critical
    stats.pending = result.pending.critical + result.pending.warning + result.pending.info
    stats.resolved = result.resolvedToday
  } catch {
    // 列表仍可使用，统计接口失败时显示列表聚合结果。
  }
}

function handleVoiceAlertAction(event: Event) {
  const detail = (event as CustomEvent<{ alertId?: string }>).detail || {}
  if (!detail.alertId) return
  const updated = alerts.value.find((item) => item.id === detail.alertId)
  if (updated) selected.value = updated
  void refreshStats()
}

onMounted(async () => {
  window.addEventListener('agent-alert-action', handleVoiceAlertAction)
  window.addEventListener('smart-logistics:voice-action-complete', handleVoiceAlertAction)
  await refreshStats()
})

onUnmounted(() => {
  window.removeEventListener('agent-alert-action', handleVoiceAlertAction)
  window.removeEventListener('smart-logistics:voice-action-complete', handleVoiceAlertAction)
})
</script>

<template>
  <div class="view-stack">
    <section class="metric-grid alert-metrics alert-conveyor-scene">
      <span class="alert-scene-machine" aria-hidden="true"><i></i></span>
      <span class="alert-scene-worker" aria-hidden="true">
        <i class="worker-head"></i><i class="worker-body"></i>
        <i class="worker-arm left"></i><i class="worker-arm right"></i>
        <i class="worker-leg left"></i><i class="worker-leg right"></i>
      </span>
      <div class="alert-conveyor-track">
        <div class="alert-conveyor-set">
          <article class="metric-card red"><span class="box-label">CRIT</span><span class="box-barcode"></span><div class="metric-icon"><el-icon><WarningFilled /></el-icon></div><div><span>严重福利风险</span><strong>{{ store.usingDemo ? criticalPendingCount : stats.critical }}</strong><small>需要工作人员立即处理</small></div></article>
          <article class="metric-card orange"><span class="box-label">TODO</span><span class="box-barcode"></span><div class="metric-icon"><el-icon><BellFilled /></el-icon></div><div><span>待处理风险</span><strong>{{ store.usingDemo ? pendingCount : stats.pending }}</strong><small>来自当前风险列表</small></div></article>
          <article class="metric-card blue"><span class="box-label">ACK</span><span class="box-barcode"></span><div class="metric-icon"><el-icon><CircleCheckFilled /></el-icon></div><div><span>已确认</span><strong>{{ acknowledgedCount }}</strong><small>处理中</small></div></article>
          <article class="metric-card green"><span class="box-label">DONE</span><span class="box-barcode"></span><div class="metric-icon"><el-icon><Select /></el-icon></div><div><span>今日已关闭</span><strong>{{ store.usingDemo ? resolvedCount : stats.resolved }}</strong><small>平均 18 分钟</small></div></article>
        </div>
      </div>
    </section>

    <section class="alerts-layout">
      <article class="panel alerts-table-panel">
        <div class="panel-head">
          <div><span class="section-kicker">ANIMAL WELFARE RISK LOG</span><h3>动物福利风险与处理日志</h3></div>
          <div class="filter-actions">
            <el-button v-if="store.usingDemo" type="danger" plain icon="Bell" @click="simulateAlert">模拟风险推送</el-button>
            <el-button plain icon="Clock" @click="historyDialog = true">历史风险 {{ archivedAlerts.length }}</el-button>
            <el-select v-model="severity" clearable placeholder="风险级别" style="width: 130px"><el-option label="严重" value="CRITICAL" /><el-option label="警告" value="WARNING" /><el-option label="提示" value="INFO" /></el-select>
            <el-select v-model="type" clearable placeholder="风险类型" style="width: 150px"><el-option v-for="item in alertTypes" :key="item" :label="item" :value="item" /></el-select>
            <el-select v-model="status" clearable placeholder="处理状态" style="width: 130px"><el-option label="待处理" value="PENDING" /><el-option label="已确认" value="ACKNOWLEDGED" /><el-option label="已关闭" value="RESOLVED" /></el-select>
            <el-select v-model="vehiclePlate" clearable placeholder="关联车辆" style="width: 150px"><el-option v-for="plate in plates" :key="plate" :label="plate" :value="plate" /></el-select>
          </div>
        </div>
        <div class="alert-color-key">
          <span><i class="danger-dot"></i>路线偏离</span>
          <span><i class="gray-dot"></i>设备离线</span>
          <span><i class="warning-dot"></i>异常停车</span>
          <span><i class="blue-dot"></i>其他事件</span>
        </div>
        <div class="alert-list">
          <button v-for="item in filtered" :key="item.id" :class="{ active: selected?.id === item.id }" @click="selectAlert(item)">
            <span
              class="alert-row-delete"
              :class="{ disabled: !canDeleteAlert(item) }"
              :title="canDeleteAlert(item) ? '归档风险事件' : '确认并关闭后才能归档'"
              @click="deleteAlert(item.id, $event)"
            >
              <el-icon><Close /></el-icon>
            </span>
            <span class="severity-icon" :class="[(item.severity || 'INFO').toLowerCase(), alertTone(item.type)]"><el-icon><Warning /></el-icon></span>
            <span class="alert-main"><strong>{{ item.title }}</strong><small>{{ item.plate }} · {{ item.location }}</small></span>
            <span class="severity-label" :class="(item.severity || 'INFO').toLowerCase()">{{ severityText[item.severity] || '提示' }}</span>
            <span class="alert-status">{{ statusText[item.status] || '未知' }}</span>
            <span class="alert-time">{{ formatAlertTime(item.createdAt) }}</span>
          </button>
          <el-empty v-if="!filtered.length" description="暂无符合条件的风险事件" :image-size="72" />
        </div>
      </article>

      <aside v-if="selected" class="panel alert-detail">
        <div class="alert-detail-head" :class="[(selected.severity || 'INFO').toLowerCase(), alertTone(selected.type)]">
          <span class="severity-icon" :class="[(selected.severity || 'INFO').toLowerCase(), alertTone(selected.type)]"><el-icon><WarningFilled /></el-icon></span>
          <div><span>{{ severityText[selected.severity] || '提示' }}风险</span><h3>{{ selected.title }}</h3><small>{{ selected.id }} · {{ formatAlertTime(selected.createdAt) }}</small></div>
        </div>
        <p class="alert-description" :class="alertTone(selected.type)">{{ selected.description }}</p>
        <dl class="info-list">
          <div><dt>关联车辆</dt><dd>{{ selected.plate }}</dd></div>
          <div><dt>发生位置</dt><dd>{{ selected.location }}</dd></div>
          <div><dt>风险类型</dt><dd>{{ selected.type }}</dd></div>
          <div><dt>当前状态</dt><dd>{{ statusText[selected.status] || '未知' }}</dd></div>
        </dl>
        <div class="recommendation"><el-icon><Opportunity /></el-icon><div><strong>工作人员处理建议</strong><p>先确认宠物状态、车内温湿度与停车安全；路线偏离超过 10 分钟时，请联系司机并下发调整指令。</p></div></div>
        <div v-if="selected.logs?.length" class="timeline">
          <div v-for="log in selected.logs" :key="`${log.time}-${log.action}`"><i></i><span>{{ log.time }}</span><strong>{{ log.action }}</strong><small>{{ log.operator }}</small></div>
        </div>
        <el-button v-if="selected.status === 'PENDING'" type="primary" size="large" @click="openAction('ACKNOWLEDGED')">确认风险事件</el-button>
        <el-button v-if="selected.status === 'ACKNOWLEDGED'" size="large" @click="openAction('RESOLVED')">关闭风险事件</el-button>
        <el-tag v-if="selected.status === 'RESOLVED'" type="success" effect="dark">已确认并关闭，可从列表右侧删除</el-tag>
      </aside>
    </section>

    <el-dialog v-model="actionDialog" :title="actionKind === 'ACKNOWLEDGED' ? '确认风险事件' : '关闭风险事件'" width="480px">
      <el-form label-position="top">
        <el-form-item v-if="actionKind === 'RESOLVED'" label="解决方案（必填）"><el-input v-model="actionForm.resolution" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="处理备注（必填）"><el-input v-model="actionForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="actionDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="update">提交处理</el-button></template>
    </el-dialog>

    <el-dialog v-model="historyDialog" title="历史风险事件" width="760px">
      <div class="deleted-alert-list">
        <article v-for="item in archivedAlerts" :key="`${item.id}-${item.deletedAt}`" class="deleted-alert-row">
          <span class="severity-icon" :class="[(item.severity || 'INFO').toLowerCase(), alertTone(item.type)]"><el-icon><Warning /></el-icon></span>
          <div>
            <strong>{{ item.title }}</strong>
            <small>{{ item.id }} · {{ item.plate }} · {{ item.location }}</small>
            <p>{{ item.description }}</p>
          </div>
          <em>{{ statusText[item.status] || '未知' }} · 删除于 {{ item.deletedAt }}</em>
        </article>
        <el-empty v-if="!archivedAlerts.length" description="暂无已删除告警" />
      </div>
    </el-dialog>
  </div>
</template>
