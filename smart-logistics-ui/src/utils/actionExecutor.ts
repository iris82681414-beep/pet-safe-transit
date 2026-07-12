import { h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { router } from '@/router'
import { request } from '@/utils/request'
import { useLogisticsStore } from '@/stores/logistics'
import { callAgentFunction, getAgentFunctionDefinition, normalizeAgentFunctionName, openAgentResultPage } from './agentFunctions'
import { shouldRequestAgentApproval } from './agentApproval'
import { routeNameMap, routeWhiteList } from './routeWhiteList'

type AgentAction = Record<string, any>

function actionRouteName(action: AgentAction) {
  return action.routeName || action.route || action.targetValue || action.targetPage
}

function normalizePlate(value: unknown) {
  return String(value || '').replace(/[\s·•.\-_]/g, '')
}

function resolveActionUrl(action: AgentAction) {
  return action.url || action.apiPath
}

function resolveActionBody(action: AgentAction) {
  return action.body || action.apiBody || {}
}

function commandTypeText(commandType: unknown) {
  const value = String(commandType || '').toUpperCase()
  const map: Record<string, string> = {
    REROUTE: '下发路线调整指令',
    STOP: '下发停车指令',
    RETURN: '下发返程指令',
    NOTIFY: '发送车辆提醒',
    ACKNOWLEDGE_ALERT: '确认告警',
    RESOLVE_ALERT: '关闭告警',
  }
  return map[value] || '调用业务接口'
}

function actionTargetText(action: AgentAction) {
  const body = resolveActionBody(action) as Record<string, any>
  return action.plate || action.targetValue || body.plate || body.vehiclePlate || body.alertId || action.url || action.apiPath || '当前业务对象'
}

function actionRiskText(action: AgentAction) {
  if (action.riskText) return action.riskText
  const body = resolveActionBody(action) as Record<string, any>
  const commandType = String(body.commandType || body.type || '').toUpperCase()
  if (commandType === 'REROUTE') return '该操作会通知司机并可能改变运输路线。'
  if (String(action.url || action.apiPath || '').includes('/alerts/')) return '该操作会改变告警处理状态并写入操作记录。'
  return '该操作会调用后端业务接口并写入操作记录。'
}

function mapRouteName(routeName: string) {
  return routeNameMap[routeName] || routeName
}

function openPortalFloatingPage(page: string, action: AgentAction = {}) {
  window.dispatchEvent(new CustomEvent('smart-logistics:open-floating-page', {
    detail: {
      page,
      action,
    },
  }))
}

function isPortalRoute() {
  return String(router.currentRoute.value.name || '') === 'portal'
}

function actionQuery(action: AgentAction) {
  const query = { ...(action.query || {}) }
  if (action.targetType === 'ORDER' && action.targetValue) query.orderId = action.targetValue
  if (action.targetType === 'VEHICLE' && (action.plate || action.targetValue)) query.plate = normalizePlate(action.plate || action.targetValue)
  return query
}

export async function executeAgentAction(action: AgentAction) {
  if (!action?.type) return

  switch (action.type) {
    case 'NAVIGATE':
      return await executeNavigate(action)
    case 'HIGHLIGHT_MAP_TARGET':
      return await executeHighlightMapTarget(action)
    case 'OPEN_MODAL':
      return executeOpenModal(action)
    case 'SHOW_RESULT':
      return await executeShowResult(action)
    case 'CALL_API':
      return await executeCallApi(action)
    case 'SMART_ALERT_ACTION':
      return await executeSmartAlertAction(action)
    case 'CALL_FUNCTION':
    case 'FUNCTION_CALL':
    case 'TOOL_CALL':
      return await executeFunctionCall(action)
    case 'NOOP':
      ElMessage.info(action.message || action.reply || '暂无可执行操作')
      break
    default:
      ElMessage.warning('未知语音操作')
  }
}

function resolveFunctionName(action: AgentAction) {
  return normalizeAgentFunctionName(
    action.functionName
      || action.toolName
      || action.name
      || action.function?.name
      || action.functionCall?.name
      || action.toolCall?.function?.name,
  )
}

function resolveFunctionArguments(action: AgentAction): Record<string, any> {
  const raw = action.arguments
    ?? action.functionArgs
    ?? action.parameters
    ?? action.function?.arguments
    ?? action.functionCall?.arguments
    ?? action.toolCall?.function?.arguments
    ?? action.apiBody
    ?? action.body
    ?? {}
  if (typeof raw !== 'string') return raw && typeof raw === 'object' ? raw : {}
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    throw new Error('Agent 函数参数不是有效的 JSON')
  }
}

async function executeFunctionCall(action: AgentAction) {
  const functionName = resolveFunctionName(action)
  const definition = getAgentFunctionDefinition(functionName)
  if (!definition) throw new Error(`未知 Agent 函数：${functionName || '未指定'}`)
  const args = resolveFunctionArguments(action)

  if (definition.mutating && shouldRequestAgentApproval(action)) {
    const target = args.plate || args.vehiclePlate || args.cargoId || args.orderId || args.alertId || args.commandId || args.name || '当前业务数据'
    await ElMessageBox.confirm(
      h('section', { style: 'display:grid;gap:10px;min-width:320px;' }, [
        h('div', { style: 'line-height:1.6;color:#0f172a;' }, definition.description),
        h('div', { style: 'color:#64748b;font-size:12px;' }, `调用函数：${definition.name}`),
        h('div', { style: 'color:#0f766e;font-size:12px;' }, `影响对象：${String(target)}`),
      ]),
      action.title || 'Agent 函数调用确认',
      {
        confirmButtonText: '确认执行',
        cancelButtonText: '取消',
        type: 'warning',
        customClass: 'agent-confirm-message-box',
      },
    )
  }

  const result = await callAgentFunction(functionName, args)
  openAgentResultPage(result.targetPage)
  window.dispatchEvent(new CustomEvent('smart-logistics:voice-action-complete', {
    detail: { action, functionName, arguments: args, result },
  }))
  if (functionName.includes('alert')) {
    window.dispatchEvent(new CustomEvent('agent-alert-action', { detail: result.data || args }))
  }
  ElMessage.success(action.successText || result.message)
  return result
}

async function executeSmartAlertAction(action: AgentAction) {
  const operation = String(action.operation || '').toUpperCase()
  const statuses = Array.isArray(action.statuses) && action.statuses.length
    ? action.statuses.map((item: unknown) => String(item))
    : [operation === 'RESOLVE' ? 'ACKNOWLEDGED' : 'PENDING']
  const severity = action.severity ? String(action.severity) : undefined
  const store = useLogisticsStore()

  let alert: Record<string, any> | null = null
  alert = findLocalAlert(store.alerts, statuses, severity)
  if (!alert && !store.usingDemo) {
    await store.loadAlerts().catch(() => undefined)
    alert = findLocalAlert(store.alerts, statuses, severity)
  }

  for (const status of statuses) {
    if (alert) break
    const page = await request<Record<string, any>>({
      url: '/alerts',
      method: 'GET',
      params: {
        status,
        severity,
        page: 1,
        size: 1,
      },
    })
    const content = Array.isArray(page?.content) ? page.content : []
    if (content[0]) {
      alert = content[0]
      break
    }
  }

  if (!alert) {
    ElMessage.warning(action.emptyText || '没有找到可处理的告警')
    return
  }

  const alertId = alert.alertId || alert.id
  const title = alert.title || alert.alertType || alertId
  const currentStatus = String(alert.status || '')
  const nextStatusText = operation === 'RESOLVE' ? '关闭告警' : '确认告警'
  const operationHint = operation === 'RESOLVE' && currentStatus === 'PENDING'
    ? '该告警当前仍为待处理，将先确认再关闭。'
    : `当前状态：${alertStatusText(currentStatus)}`
  if (shouldRequestAgentApproval(action)) {
    await ElMessageBox.confirm(
      h('section', { style: 'display:grid;gap:10px;min-width:320px;' }, [
        h('div', { style: 'line-height:1.6;' }, `将${nextStatusText}：${title}`),
        h('div', { style: 'color:#64748b;font-size:12px;' }, `告警编号：${alertId}`),
        h('div', { style: 'color:#0f766e;font-size:12px;' }, operationHint),
      ]),
      action.title || '语音告警操作确认',
      {
        confirmButtonText: '确认执行',
        cancelButtonText: '取消',
        type: 'warning',
        customClass: 'agent-confirm-message-box',
      },
    )
  }

  await applyAlertOperation(alertId, operation, action, currentStatus)

  const nextStatus = operation === 'RESOLVE' ? 'RESOLVED' : 'ACKNOWLEDGED'
  window.dispatchEvent(new CustomEvent('agent-alert-action', { detail: { ...action, alertId, status: nextStatus } }))
  if (!store.usingDemo) await store.loadAlerts().catch(() => undefined)
  window.dispatchEvent(new CustomEvent('smart-logistics:voice-action-complete', { detail: { ...action, alertId, status: nextStatus } }))
  ElMessage.success(action.successText || `${nextStatusText}已执行`)
}

function findLocalAlert(alerts: Array<Record<string, any>>, statuses: string[], severity?: string) {
  return alerts.find((item) => {
    const statusMatched = statuses.includes(String(item.status || ''))
    const severityMatched = !severity || String(item.severity || '') === severity
    return statusMatched && severityMatched
  }) || null
}

function alertStatusText(status: string) {
  if (status === 'PENDING') return '待处理'
  if (status === 'ACKNOWLEDGED') return '已确认'
  if (status === 'RESOLVED') return '已关闭'
  return status || '未知'
}

async function applyAlertOperation(alertId: string, operation: string, action: AgentAction, currentStatus = '') {
  const store = useLogisticsStore()
  const remark = action.remark || (operation === 'RESOLVE' ? '由语音助手关闭' : '由语音助手确认')
  if (operation === 'RESOLVE') {
    if (currentStatus === 'PENDING') {
      await store.updateAlert(alertId, 'ACKNOWLEDGED', '', '由语音助手自动确认后关闭')
    }
    await store.updateAlert(alertId, 'RESOLVED', action.resolution || '语音指令关闭', remark)
    return
  }
  await store.updateAlert(alertId, 'ACKNOWLEDGED', '', remark)
}

async function executeNavigate(action: AgentAction) {
  const rawRouteName = actionRouteName(action)
  if (!rawRouteName || !routeWhiteList.includes(rawRouteName)) {
    ElMessage.error('该页面不允许通过语音跳转')
    return
  }
  if (rawRouteName === '_BACK') {
    router.back()
    return
  }
  if (rawRouteName === '_REFRESH') {
    window.location.reload()
    return
  }

  const mappedRoute = mapRouteName(rawRouteName)
  if (isPortalRoute() && mappedRoute === 'portal') {
    window.dispatchEvent(new CustomEvent('smart-logistics:close-floating-page'))
    return
  }
  if (isPortalRoute() && mappedRoute !== 'portal') {
    openPortalFloatingPage(mappedRoute, action)
    return
  }

  await router.push({
    name: mappedRoute,
    params: action.params || {},
    query: actionQuery(action),
  })
}

async function executeHighlightMapTarget(action: AgentAction) {
  const plate = normalizePlate(action.plate || action.targetValue)
  const eventAction = { ...action, plate, targetValue: plate }
  const current = String(router.currentRoute.value.name || '')
  if (current === 'portal') {
    openPortalFloatingPage('dispatch', action)
    window.setTimeout(() => {
      window.dispatchEvent(new CustomEvent('agent-map-action', { detail: eventAction }))
    }, 420)
    return
  }
  if (!['dispatch', 'tracking', 'overview'].includes(current)) {
    await router.push({ name: 'dispatch', query: plate ? { plate } : {} })
  }
  window.setTimeout(() => {
    window.dispatchEvent(new CustomEvent('agent-map-action', { detail: eventAction }))
  }, 80)
}

function executeOpenModal(action: AgentAction) {
  window.dispatchEvent(new CustomEvent('agent-open-modal', { detail: action }))
  if (action.targetType === 'HELP') {
    ElMessageBox.alert(
      '可尝试：打开车辆调度、打开订单 ORD-2026-001、定位沪A C0291、下发改道指令、查看未处理告警、查看在线车辆。',
      action.title || '语音助手',
      { confirmButtonText: '知道了' },
    )
    return
  }
  ElMessage.info(action.message || action.reply || '已打开语音操作窗口')
}

async function executeShowResult(action: AgentAction) {
  window.dispatchEvent(new CustomEvent('agent-show-result', { detail: action }))
  if (action.targetType === 'ORDER' && action.targetValue) {
    if (isPortalRoute()) {
      openPortalFloatingPage('tracking', action)
      return
    }
    await router.push({ name: 'tracking', query: { orderId: action.targetValue } })
    return
  }
  if (action.targetType === 'VEHICLE' && (action.plate || action.targetValue)) {
    await executeHighlightMapTarget(action)
    return
  }
  ElMessage.success(action.message || action.reply || '已展示查询结果')
}

async function executeCallApi(action: AgentAction) {
  const url = resolveActionUrl(action)
  if (!url) {
    ElMessage.error('语音操作缺少接口地址')
    return
  }

  if (shouldRequestAgentApproval(action)) await confirmCallApiAction(action)
  const body = resolveActionBody(action) as Record<string, any>
  let res: unknown
  const store = useLogisticsStore()
  if (String(url).includes('/vehicles/') && String(url).includes('/command') && action.plate && body.commandType) {
    res = await store.issueCommand(
      String(action.plate),
      String(body.commandType),
      String(body.payload?.content || body.message || action.reply || '语音调度指令'),
    )
    window.dispatchEvent(new CustomEvent('smart-logistics:voice-action-complete', { detail: { action, result: res } }))
  } else if (String(url).includes('/alerts/') && (String(url).includes('/acknowledge') || String(url).includes('/resolve'))) {
    const alertId = decodeURIComponent(String(url).split('/alerts/')[1]?.split('/')[0] || body.alertId || action.alertId || '')
    if (!alertId) throw new Error('语音告警操作缺少告警编号')
    const operation = String(url).includes('/resolve') ? 'RESOLVE' : 'ACKNOWLEDGE'
    const current = store.alerts.find((item) => item.id === alertId)
    await applyAlertOperation(alertId, operation, { ...action, ...body }, current?.status || '')
    const nextStatus = operation === 'RESOLVE' ? 'RESOLVED' : 'ACKNOWLEDGED'
    window.dispatchEvent(new CustomEvent('agent-alert-action', { detail: { ...action, alertId, status: nextStatus } }))
    window.dispatchEvent(new CustomEvent('smart-logistics:voice-action-complete', { detail: { action, alertId, status: nextStatus } }))
    res = { alertId, status: nextStatus }
  } else {
    res = await request({
      url,
      method: action.method || 'POST',
      data: body,
    })
    await refreshAfterAction(url)
  }
  ElMessage.success(action.successText || '操作已执行')
  return res
}

async function refreshAfterAction(url: string) {
  const store = useLogisticsStore()
  if (url.includes('/alerts/')) {
    await store.loadAlerts()
  } else if (url.includes('/vehicles')) {
    await store.loadVehicles()
  } else if (url.includes('/cargo') || url.includes('/orders')) {
    await store.loadCargo()
  }
  window.dispatchEvent(new CustomEvent('smart-logistics:voice-action-complete', { detail: { url } }))
}

async function confirmCallApiAction(action: AgentAction) {
  const body = resolveActionBody(action) as Record<string, any>
  const recognizedText = action.recognizedText || action.text || action.confirmText || action.reply || '语音指令'
  const rows = [
    ['识别指令', recognizedText],
    ['将执行', action.actionText || commandTypeText(body.commandType || body.type)],
    ['影响对象', actionTargetText(action)],
    ['风险提示', actionRiskText(action)],
  ]

  await ElMessageBox.confirm(
    h('section', { style: 'display:grid;gap:10px;min-width:320px;' }, rows.map(([label, value]) => h('div', {
      style: 'display:grid;grid-template-columns:76px minmax(0,1fr);gap:10px;align-items:start;padding:10px 12px;border:1px solid rgba(14,116,144,.18);border-radius:8px;background:rgba(8,47,73,.05);',
    }, [
      h('span', { style: 'color:#64748b;font-size:12px;' }, label),
      h('strong', { style: 'color:#0f172a;font-size:13px;font-weight:650;line-height:1.45;word-break:break-word;' }, String(value || '-')),
    ]))),
    action.title || '语音操作确认',
    {
      confirmButtonText: '确认执行',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'agent-confirm-message-box',
    },
  )
}
