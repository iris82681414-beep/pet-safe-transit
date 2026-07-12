import { router } from '@/router'
import { addressChangeApi, orderExtensionApi } from '@/services/api'
import { useLogisticsStore } from '@/stores/logistics'
import type { AlertItem, Cargo, Vehicle } from '@/types'
import { routeNameMap, routeWhiteList } from './routeWhiteList'

export interface AgentFunctionDefinition {
  name: string
  description: string
  mutating: boolean
  parameters: Record<string, string>
}

export interface AgentFunctionResult {
  message: string
  data?: unknown
  targetPage?: string
}

type AgentArguments = Record<string, any>

export const agentFunctionDefinitions: AgentFunctionDefinition[] = [
  { name: 'navigate_page', description: '打开伴生云途工作人员页面', mutating: false, parameters: { page: '目标页面路由名称' } },
  { name: 'locate_vehicle', description: '在地图上定位车辆', mutating: false, parameters: { plate: '车牌号' } },
  { name: 'dispatch_vehicle', description: '向指定车辆下发改道、停车、返程或提醒指令', mutating: true, parameters: { plate: '车牌号', commandType: 'REROUTE/STOP/RETURN/NOTIFY', content: '指令内容' } },
  { name: 'create_vehicle', description: '新增车辆', mutating: true, parameters: { plate: '车牌号', driver: '司机姓名', phone: '手机号', vehicleType: '车辆类型', capacity: '载重吨数' } },
  { name: 'update_vehicle', description: '修改车辆、司机、联系方式、状态或设备信息', mutating: true, parameters: { plate: '当前车牌号', driver: '司机姓名', phone: '手机号', status: 'IN_TRANSIT/IDLE/OFFLINE', deviceImei: '设备IMEI' } },
  { name: 'delete_vehicle', description: '删除车辆并解除宠物托运任务安排', mutating: true, parameters: { plate: '车牌号' } },
  { name: 'create_cargo', description: '新增宠物托运任务', mutating: true, parameters: { cargoId: '托运任务编号', name: '宠物称呼', category: '宠物种类或品种', origin: '接宠地点', destination: '交接地点', weight: '宠物体重' } },
  { name: 'bind_cargo_vehicle', description: '把宠物托运任务安排到车辆', mutating: true, parameters: { cargoId: '托运任务编号', plate: '车牌号' } },
  { name: 'unbind_cargo_vehicle', description: '解除宠物托运任务与车辆的安排', mutating: true, parameters: { cargoId: '托运任务编号' } },
  { name: 'update_cargo_status', description: '更新宠物旅程状态', mutating: true, parameters: { cargoId: '托运任务编号', status: 'CREATED/LOADED/IN_TRANSIT/DELIVERED/CANCELLED', remark: '照护备注' } },
  { name: 'confirm_receipt', description: '确认宠物安全交接', mutating: true, parameters: { cargoId: '托运任务编号' } },
  { name: 'submit_address_change', description: '为宠物托运任务提交交接地址变更申请', mutating: true, parameters: { cargoId: '托运任务编号', address: '新交接地址', city: '城市', lat: '纬度', lng: '经度', reason: '改址原因' } },
  { name: 'confirm_unload_address', description: '确认宠物交接地址', mutating: true, parameters: { cargoId: '托运任务编号', address: '宠物交接地址', lat: '纬度', lng: '经度' } },
  { name: 'report_unload_abnormal', description: '上报宠物交接地址异常', mutating: true, parameters: { cargoId: '托运任务编号', reason: '异常原因', currentAddress: '当前位置' } },
  { name: 'rate_driver', description: '为已完成的宠物旅程评价司机与照护服务', mutating: true, parameters: { cargoId: '托运任务编号', score: '1至5分', comment: '评价内容' } },
  { name: 'approve_address_change', description: '通过改址申请', mutating: true, parameters: { requestId: '改址申请编号', remark: '审核备注' } },
  { name: 'reject_address_change', description: '驳回改址申请', mutating: true, parameters: { requestId: '改址申请编号', reason: '驳回原因' } },
  { name: 'driver_confirm_address_change', description: '司机确认改址申请', mutating: true, parameters: { requestId: '改址申请编号', accepted: '是否接受', remark: '备注' } },
  { name: 'acknowledge_alert', description: '确认指定或最新待处理告警', mutating: true, parameters: { alertId: '告警编号，可省略', severity: 'CRITICAL/WARNING/INFO，可省略' } },
  { name: 'resolve_alert', description: '关闭指定或最新告警，待处理告警会先确认', mutating: true, parameters: { alertId: '告警编号，可省略', resolution: '处理结果' } },
  { name: 'delete_resolved_alert', description: '删除并归档已关闭的告警', mutating: true, parameters: { alertId: '告警编号' } },
  { name: 'execute_dispatch_command', description: '确认执行司机收到的调度指令', mutating: true, parameters: { commandId: '调度指令编号，可省略' } },
  { name: 'add_driver', description: '新增司机档案', mutating: true, parameters: { name: '司机姓名', phone: '手机号' } },
  { name: 'delete_driver', description: '删除司机档案并清空车辆司机信息', mutating: true, parameters: { name: '司机姓名或手机号' } },
  { name: 'mark_notifications_read', description: '将通知设为已读', mutating: true, parameters: { notificationId: '通知编号，可省略；省略为全部' } },
  { name: 'clear_read_notifications', description: '清除已读通知', mutating: true, parameters: {} },
  { name: 'simulate_alert', description: '在演示模式生成一条告警', mutating: true, parameters: {} },
  { name: 'reset_demo_data', description: '重置所有本地演示数据', mutating: true, parameters: {} },
  { name: 'query_vehicle', description: '查询车辆详情', mutating: false, parameters: { plate: '车牌号' } },
  { name: 'query_cargo', description: '查询宠物托运详情', mutating: false, parameters: { cargoId: '托运任务编号' } },
  { name: 'query_alerts', description: '查询告警数量', mutating: false, parameters: { status: 'PENDING/ACKNOWLEDGED/RESOLVED', severity: 'CRITICAL/WARNING/INFO' } },
]

const aliases: Record<string, string> = {
  issue_vehicle_command: 'dispatch_vehicle',
  send_vehicle_command: 'dispatch_vehicle',
  dispatch_cargo: 'bind_cargo_vehicle',
  bind_cargo: 'bind_cargo_vehicle',
  unbind_cargo: 'unbind_cargo_vehicle',
  cargo_status: 'update_cargo_status',
  receive_cargo: 'confirm_receipt',
  change_delivery_address: 'submit_address_change',
  confirm_unload: 'confirm_unload_address',
  submit_driver_rating: 'rate_driver',
  ack_alert: 'acknowledge_alert',
  close_alert: 'resolve_alert',
  remove_alert: 'delete_resolved_alert',
  execute_command: 'execute_dispatch_command',
}

export function normalizeAgentFunctionName(value: unknown) {
  const name = String(value || '').trim().toLowerCase()
  return aliases[name] || name
}

export function getAgentFunctionDefinition(name: unknown) {
  const normalized = normalizeAgentFunctionName(name)
  return agentFunctionDefinitions.find((item) => item.name === normalized)
}

function required(value: unknown, label: string) {
  const result = String(value ?? '').trim()
  if (!result) throw new Error(`缺少${label}`)
  return result
}

function plateKey(value: unknown) {
  return String(value || '').toUpperCase().replace(/[\s·•.\-_]/g, '')
}

function findVehicle(vehicles: Vehicle[], value: unknown) {
  const key = plateKey(value)
  const vehicle = vehicles.find((item) => plateKey(item.plate) === key)
  if (!vehicle) throw new Error(`没有找到车辆 ${String(value || '')}`)
  return vehicle
}

function findCargo(cargo: Cargo[], value: unknown) {
  const original = required(value, '托运任务编号')
  const key = original.toUpperCase().replace(/[^A-Z0-9]/g, '')
  const item = cargo.find((entry) => entry.id.toUpperCase().replace(/[^A-Z0-9]/g, '') === key)
  if (!item) throw new Error(`没有找到宠物托运任务 ${String(value || '')}`)
  return item
}

function findAlert(alerts: AlertItem[], args: AgentArguments, statuses: string[]) {
  const alertId = String(args.alertId || args.id || '').trim()
  if (alertId) {
    const item = alerts.find((entry) => entry.id.toUpperCase() === alertId.toUpperCase())
    if (!item) throw new Error(`没有找到告警 ${alertId}`)
    return item
  }
  const severity = String(args.severity || '').toUpperCase()
  const item = alerts.find((entry) => statuses.includes(entry.status) && (!severity || entry.severity === severity))
  if (!item) throw new Error('没有找到可处理的告警')
  return item
}

function cargoStatus(value: unknown): Cargo['status'] {
  const raw = String(value || '').toUpperCase()
  const map: Record<string, Cargo['status']> = {
    CREATED: 'CREATED', '待装货': 'CREATED',
    LOADED: 'LOADED', '已装货': 'LOADED',
    IN_TRANSIT: 'IN_TRANSIT', TRANSIT: 'IN_TRANSIT', '运输中': 'IN_TRANSIT',
    DELIVERED: 'DELIVERED', '已送达': 'DELIVERED',
    CANCELLED: 'CANCELLED', CANCELED: 'CANCELLED', '已取消': 'CANCELLED',
  }
  const result = map[raw]
  if (!result) throw new Error(`不支持的宠物旅程状态 ${String(value || '')}`)
  return result
}

function vehicleStatus(value: unknown): Vehicle['status'] | undefined {
  if (value == null || value === '') return undefined
  const raw = String(value).toUpperCase()
  const map: Record<string, Vehicle['status']> = {
    IN_TRANSIT: 'IN_TRANSIT', MOVING: 'IN_TRANSIT', '运输中': 'IN_TRANSIT',
    IDLE: 'IDLE', STOPPED: 'IDLE', '待命': 'IDLE',
    OFFLINE: 'OFFLINE', '离线': 'OFFLINE',
  }
  const result = map[raw]
  if (!result) throw new Error(`不支持的车辆状态 ${String(value)}`)
  return result
}

function parseManualDrivers() {
  try {
    const value = JSON.parse(localStorage.getItem('smart-logistics-manual-drivers') || '[]')
    return Array.isArray(value) ? value as Array<{ id: string; name: string; phone: string }> : []
  } catch {
    return []
  }
}

function saveManualDrivers(value: Array<{ id: string; name: string; phone: string }>) {
  localStorage.setItem('smart-logistics-manual-drivers', JSON.stringify(value))
  window.dispatchEvent(new CustomEvent('smart-logistics:drivers-changed', { detail: value }))
}

export async function callAgentFunction(name: unknown, args: AgentArguments = {}): Promise<AgentFunctionResult> {
  const functionName = normalizeAgentFunctionName(name)
  const store = useLogisticsStore()

  switch (functionName) {
    case 'navigate_page': {
      const rawPage = required(args.page || args.routeName || args.targetPage, '目标页面')
      if (!routeWhiteList.includes(rawPage)) throw new Error(`不允许打开页面 ${rawPage}`)
      const page = routeNameMap[rawPage] || rawPage
      if (String(router.currentRoute.value.name || '') === 'portal' && page !== 'portal') {
        window.dispatchEvent(new CustomEvent('smart-logistics:open-floating-page', { detail: { page } }))
      } else if (page === 'portal' && String(router.currentRoute.value.name || '') === 'portal') {
        window.dispatchEvent(new CustomEvent('smart-logistics:close-floating-page'))
      } else {
        await router.push({ name: page })
      }
      return { message: `正在打开${rawPage}` }
    }
    case 'locate_vehicle': {
      const vehicle = findVehicle(store.vehicles, args.plate || args.vehiclePlate)
      const detail = { plate: vehicle.plate, targetValue: vehicle.plate, mapAction: 'CENTER_AND_ZOOM', zoom: 15 }
      const current = String(router.currentRoute.value.name || '')
      if (current === 'portal') {
        window.dispatchEvent(new CustomEvent('smart-logistics:open-floating-page', { detail: { page: 'dispatch' } }))
        window.setTimeout(() => window.dispatchEvent(new CustomEvent('agent-map-action', { detail })), 420)
      } else {
        if (!['dispatch', 'tracking', 'overview'].includes(current)) await router.push({ name: 'dispatch' })
        window.setTimeout(() => window.dispatchEvent(new CustomEvent('agent-map-action', { detail })), 80)
      }
      return { message: `正在定位 ${vehicle.plate}`, data: vehicle }
    }
    case 'dispatch_vehicle': {
      const vehicle = findVehicle(store.vehicles, args.plate || args.vehiclePlate)
      const commandType = required(args.commandType || args.type || 'NOTIFY', '指令类型').toUpperCase()
      if (!['REROUTE', 'STOP', 'RETURN', 'NOTIFY'].includes(commandType)) throw new Error(`不支持的调度指令 ${commandType}`)
      const content = String(args.content || args.message || ({ REROUTE: '请按调度建议路线行驶', STOP: '请在安全位置停车', RETURN: '请按指令返程', NOTIFY: '请注意最新调度通知' } as Record<string, string>)[commandType])
      const command = await store.issueCommand(vehicle.plate, commandType, content)
      return { message: `已向 ${vehicle.plate} 下发${commandType}指令`, data: command, targetPage: 'dispatch' }
    }
    case 'create_vehicle': {
      const plate = required(args.plate || args.vehiclePlate, '车牌号')
      if (store.vehicles.some((item) => plateKey(item.plate) === plateKey(plate))) throw new Error(`车辆 ${plate} 已存在`)
      const vehicle: Vehicle = {
        plate, driver: String(args.driver || args.driverName || ''), phone: String(args.phone || args.driverPhone || ''),
        status: vehicleStatus(args.status) || 'IDLE', speed: 0,
        lat: Number(args.lat) || 29.563, lng: Number(args.lng) || 106.551,
        location: String(args.location || '重庆'), heartbeat: '刚刚',
        vehicleType: String(args.vehicleType || 'TRUCK'), capacity: Number(args.capacity) || 10,
        deviceImei: String(args.deviceImei || ''),
      }
      await store.addVehicle(vehicle)
      return { message: `车辆 ${plate} 已新增`, data: vehicle, targetPage: 'warehouse' }
    }
    case 'update_vehicle': {
      const vehicle = findVehicle(store.vehicles, args.plate || args.vehiclePlate)
      const patch: Partial<Vehicle> = {}
      if (args.driver != null || args.driverName != null) patch.driver = String(args.driver ?? args.driverName)
      if (args.phone != null || args.driverPhone != null) patch.phone = String(args.phone ?? args.driverPhone)
      if (args.status != null) patch.status = vehicleStatus(args.status)
      if (args.deviceImei != null) patch.deviceImei = String(args.deviceImei)
      if (args.vehicleType != null) patch.vehicleType = String(args.vehicleType)
      if (args.capacity != null) patch.capacity = Number(args.capacity)
      if (args.location != null) patch.location = String(args.location)
      await store.updateVehicle(vehicle.plate, patch)
      return { message: `车辆 ${vehicle.plate} 已更新`, data: patch, targetPage: 'dispatch' }
    }
    case 'delete_vehicle': {
      const vehicle = findVehicle(store.vehicles, args.plate || args.vehiclePlate)
      await store.removeVehicle(vehicle.plate)
      return { message: `车辆 ${vehicle.plate} 已删除`, targetPage: 'warehouse' }
    }
    case 'create_cargo': {
      const id = required(args.cargoId || args.orderId || args.id, '托运任务编号')
      if (store.cargo.some((item) => item.id.toUpperCase() === id.toUpperCase())) throw new Error(`宠物托运任务 ${id} 已存在`)
      const item: Cargo = {
        id, name: String(args.name || args.cargoName || '待登记宠物'), category: String(args.category || args.cargoType || '待确认品种'),
        origin: String(args.origin || '重庆宠物照护中心'), destination: String(args.destination || '待指定交接点'),
        progress: 0, status: 'CREATED', eta: String(args.eta || '待调度'), weight: Number(args.weight) || 0,
        originLat: Number(args.originLat) || undefined, originLng: Number(args.originLng) || undefined,
        destinationLat: Number(args.destinationLat) || undefined, destinationLng: Number(args.destinationLng) || undefined,
      }
      await store.addCargo(item)
      return { message: `宠物托运任务 ${id} 已新增`, data: item, targetPage: 'warehouse' }
    }
    case 'bind_cargo_vehicle': {
      const item = findCargo(store.cargo, args.cargoId || args.orderId)
      const vehicle = findVehicle(store.vehicles, args.plate || args.vehiclePlate)
      await store.bindCargo(item.id, vehicle.plate)
      return { message: `已将宠物托运任务 ${item.id} 安排到车辆 ${vehicle.plate}`, targetPage: 'warehouse' }
    }
    case 'unbind_cargo_vehicle': {
      const item = findCargo(store.cargo, args.cargoId || args.orderId)
      await store.unbindCargo(item.id)
      return { message: `宠物托运任务 ${item.id} 已解除车辆安排`, targetPage: 'warehouse' }
    }
    case 'update_cargo_status': {
      const item = findCargo(store.cargo, args.cargoId || args.orderId)
      const status = cargoStatus(args.status)
      await store.updateCargoStatus(item.id, status, String(args.remark || '由语音 Agent 更新'))
      return { message: `宠物旅程 ${item.id} 已更新为 ${status}`, targetPage: 'tracking' }
    }
    case 'confirm_receipt': {
      const item = findCargo(store.cargo, args.cargoId || args.orderId)
      await store.confirmReceipt(item.id)
      return { message: `宠物旅程 ${item.id} 已确认安全交接`, targetPage: 'tracking' }
    }
    case 'submit_address_change': {
      const item = findCargo(store.cargo, args.cargoId || args.orderId)
      const address = required(args.address || args.detail, '新交接地址')
      const lat = Number(args.lat)
      const lng = Number(args.lng)
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) throw new Error('改址申请缺少有效经纬度')
      const result = await orderExtensionApi.createAddressChange(item.id, {
        newAddress: { detail: address, city: String(args.city || ''), lat, lng },
        contactName: String(args.contactName || ''), contactPhone: String(args.contactPhone || ''),
        reason: String(args.reason || '由语音 Agent 提交'),
      })
      return { message: `宠物旅程 ${item.id} 的交接改址申请已提交`, data: result, targetPage: 'tracking' }
    }
    case 'confirm_unload_address': {
      const item = findCargo(store.cargo, args.cargoId || args.orderId)
      const address = required(args.address, '宠物交接地址')
      const lat = Number(args.lat)
      const lng = Number(args.lng)
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) throw new Error('宠物交接地址缺少有效经纬度')
      const result = await orderExtensionApi.confirmUnloadAddress(item.id, { address, lat, lng, remark: String(args.remark || '由语音 Agent 确认') })
      item.destination = address
      item.destinationLat = lat
      item.destinationLng = lng
      return { message: `宠物旅程 ${item.id} 的交接地址已确认`, data: result, targetPage: 'tracking' }
    }
    case 'report_unload_abnormal': {
      const item = findCargo(store.cargo, args.cargoId || args.orderId)
      const result = await orderExtensionApi.reportUnloadAddressAbnormal(item.id, {
        type: String(args.type || 'ADDRESS_MISMATCH'),
        description: `${required(args.reason, '异常原因')}；当前位置：${String(args.currentAddress || args.address || item.destination)}`,
        lat: Number(args.currentLat ?? args.lat) || undefined,
        lng: Number(args.currentLng ?? args.lng) || undefined,
        photos: Array.isArray(args.photos) ? args.photos.map(String) : [],
      })
      return { message: `宠物旅程 ${item.id} 的交接地址异常已上报`, data: result, targetPage: 'tracking' }
    }
    case 'rate_driver': {
      const item = findCargo(store.cargo, args.cargoId || args.orderId)
      const score = Math.max(1, Math.min(5, Number(args.score) || 5))
      const result = await orderExtensionApi.submitDriverRating(item.id, {
        score,
        dimensions: {
          punctuality: Number(args.punctuality) || score,
          serviceAttitude: Number(args.serviceAttitude) || score,
          cargoIntegrity: Number(args.cargoIntegrity) || score,
          communication: Number(args.communication) || score,
        },
        tags: Array.isArray(args.tags) ? args.tags.map(String) : [],
        comment: String(args.comment || '由语音 Agent 提交评价'),
      })
      return { message: `宠物旅程 ${item.id} 的司机与照护服务评价已提交`, data: result, targetPage: 'tracking' }
    }
    case 'approve_address_change': {
      const requestId = required(args.requestId || args.id, '改址申请编号')
      const result = await addressChangeApi.approve(requestId, { remark: String(args.remark || '由语音 Agent 审核通过') })
      return { message: `改址申请 ${requestId} 已通过`, data: result, targetPage: 'address-change-review' }
    }
    case 'reject_address_change': {
      const requestId = required(args.requestId || args.id, '改址申请编号')
      const result = await addressChangeApi.reject(requestId, { reason: required(args.reason, '驳回原因'), suggestion: String(args.suggestion || '') })
      return { message: `改址申请 ${requestId} 已驳回`, data: result, targetPage: 'address-change-review' }
    }
    case 'driver_confirm_address_change': {
      const requestId = required(args.requestId || args.id, '改址申请编号')
      const result = await addressChangeApi.driverConfirm(requestId, { confirmed: args.accepted !== false && args.confirmed !== false, remark: String(args.remark || '由语音 Agent 确认') })
      return { message: `司机已确认改址申请 ${requestId}`, data: result, targetPage: 'driver' }
    }
    case 'acknowledge_alert': {
      const alert = findAlert(store.alerts, args, ['PENDING'])
      await store.updateAlert(alert.id, 'ACKNOWLEDGED', '', String(args.remark || '由语音 Agent 确认'))
      return { message: `告警 ${alert.id} 已确认`, data: { alertId: alert.id, status: 'ACKNOWLEDGED' }, targetPage: 'alerts' }
    }
    case 'resolve_alert': {
      const alert = findAlert(store.alerts, args, ['ACKNOWLEDGED', 'PENDING'])
      if (alert.status === 'PENDING') await store.updateAlert(alert.id, 'ACKNOWLEDGED', '', '由语音 Agent 自动确认后关闭')
      await store.updateAlert(alert.id, 'RESOLVED', String(args.resolution || '语音 Agent 已处理'), String(args.remark || '由语音 Agent 关闭'))
      return { message: `告警 ${alert.id} 已关闭`, data: { alertId: alert.id, status: 'RESOLVED' }, targetPage: 'alerts' }
    }
    case 'delete_resolved_alert': {
      const alert = findAlert(store.alerts, args, ['RESOLVED'])
      if (alert.status !== 'RESOLVED') throw new Error('告警必须确认并关闭后才能删除')
      store.removeAlerts([alert.id])
      return { message: `告警 ${alert.id} 已删除并归档`, targetPage: 'alerts' }
    }
    case 'execute_dispatch_command': {
      const id = String(args.commandId || args.id || '')
      const command = id
        ? store.commands.find((item) => item.id.toUpperCase() === id.toUpperCase())
        : store.commands.find((item) => item.status === 'RECEIVED')
      if (!command) throw new Error('没有找到可执行的调度指令')
      store.executeCommand(command.id)
      return { message: `调度指令 ${command.id} 已确认执行`, targetPage: 'driver' }
    }
    case 'add_driver': {
      const driverName = required(args.name || args.driverName, '司机姓名')
      const phone = required(args.phone || args.driverPhone, '联系电话')
      const drivers = parseManualDrivers()
      if (drivers.some((item) => item.name === driverName || item.phone === phone) || store.vehicles.some((item) => item.driver === driverName || item.phone === phone)) throw new Error('该司机姓名或手机号已存在')
      drivers.unshift({ id: `DRV-${Date.now()}`, name: driverName, phone })
      saveManualDrivers(drivers)
      return { message: `司机 ${driverName} 已添加`, targetPage: 'personnel' }
    }
    case 'delete_driver': {
      const keyword = required(args.name || args.driverName || args.phone, '司机姓名或手机号')
      const drivers = parseManualDrivers().filter((item) => item.name !== keyword && item.phone !== keyword)
      const vehicles = store.vehicles.filter((item) => item.driver === keyword || item.phone === keyword)
      for (const vehicle of vehicles) await store.updateVehicle(vehicle.plate, { driver: '', phone: '' })
      saveManualDrivers(drivers)
      return { message: `司机 ${keyword} 已删除，相关车辆已解除司机信息`, targetPage: 'personnel' }
    }
    case 'mark_notifications_read': {
      const id = String(args.notificationId || args.id || '')
      if (id) store.markNotificationRead(id)
      else store.markNotificationsRead()
      return { message: id ? `通知 ${id} 已设为已读` : '全部通知已设为已读' }
    }
    case 'clear_read_notifications':
      store.removeReadNotifications()
      return { message: '已读通知已清除' }
    case 'simulate_alert':
      store.simulateAlert()
      return { message: '已生成一条演示告警', targetPage: 'alerts' }
    case 'reset_demo_data':
      if (!store.usingDemo) throw new Error('真实接口模式不能重置演示数据')
      store.resetDemoData()
      return { message: '本地演示数据已重置' }
    case 'query_vehicle': {
      const vehicle = findVehicle(store.vehicles, args.plate || args.vehiclePlate)
      return { message: `${vehicle.plate}：${vehicle.driver || '未分配司机'}，${vehicle.location}，时速 ${vehicle.speed} km/h`, data: vehicle, targetPage: 'dispatch' }
    }
    case 'query_cargo': {
      const item = findCargo(store.cargo, args.cargoId || args.orderId)
      return { message: `${item.id}：${item.origin} → ${item.destination}，状态 ${item.status}`, data: item, targetPage: 'tracking' }
    }
    case 'query_alerts': {
      const status = String(args.status || '').toUpperCase()
      const severity = String(args.severity || '').toUpperCase()
      const alerts = store.alerts.filter((item) => (!status || item.status === status) && (!severity || item.severity === severity))
      return { message: `符合条件的告警共 ${alerts.length} 条`, data: alerts, targetPage: 'alerts' }
    }
    default:
      throw new Error(`不支持的 Agent 函数：${functionName || '未指定'}`)
  }
}

export function openAgentResultPage(page?: string) {
  if (!page) return
  const current = String(router.currentRoute.value.name || '')
  if (current === 'portal') {
    window.dispatchEvent(new CustomEvent('smart-logistics:open-floating-page', { detail: { page } }))
  }
}
