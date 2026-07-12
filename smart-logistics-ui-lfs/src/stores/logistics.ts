import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { mockAlerts, mockCargo, mockVehicles } from '@/data/mock'
import { alertApi, authApi, cargoApi, deviceApi, vehicleApi } from '@/services/api'
import { isDemoMode } from '@/services/config'
import { tokenManager } from '@/services/token'
import { realtimeClient, type ConnectionState } from '@/services/websocket'
import type { AlertDto, CargoDto, CommandDto, DeviceDto, UserProfile, VehicleDto } from '@/services/types'
import type { AlertItem, AlertStatus, Cargo, CommandRecord, Device, NotificationItem, UserRole, Vehicle } from '@/types'

const roleLabels: Record<UserRole, string> = {
  SHIPPER: '货主',
  WAREHOUSE: '仓库管理员',
  DISPATCHER: '调度员',
  DRIVER: '司机',
  ADMIN: '系统管理员',
}

export const useLogisticsStore = defineStore('logistics', () => {
  const defaultCommands = (): CommandRecord[] => [
    { id: 'CMD-0629-001', plate: '沪A·C0291', type: '路线调整', content: '请回到 G60 推荐路线，保持 30 秒一次心跳上报。', status: 'RECEIVED', createdAt: '14:31' },
    { id: 'CMD-0629-002', plate: '浙B·L8821', type: '停靠指令', content: '请在下一服务区安全停靠并检查货箱。', status: 'EXECUTED', createdAt: '13:58', executedAt: '14:06' },
  ]
  const defaultNotifications = (): NotificationItem[] => [
    { id: 'NTF-1', title: '严重偏航告警', content: '沪A·C0291 偏离 G60 推荐路线约 8 公里。', type: 'alert', time: '14:28', read: false, targetPage: 'alerts', targetId: 'ALT-0629-01' },
    { id: 'NTF-2', title: '调度指令已送达', content: '路线调整指令已送达沪A·C0291车载终端。', type: 'command', time: '14:31', read: false, targetPage: 'driver', targetId: 'CMD-0629-001' },
    { id: 'NTF-3', title: '设备恢复在线', content: 'GPS-浙B-L8821 已恢复心跳上报。', type: 'system', time: '13:40', read: true, targetPage: 'dispatch' },
  ]
  let saved: {
    vehicles: Vehicle[]
    cargo: Cargo[]
    alerts: typeof mockAlerts
    commands: CommandRecord[]
    notifications: NotificationItem[]
  } | null = null
  try {
    saved = JSON.parse(localStorage.getItem('smart-logistics-mock-data') || 'null')
  } catch {
    localStorage.removeItem('smart-logistics-mock-data')
  }
  let savedVehicleImages: Record<string, string> = {}
  try {
    savedVehicleImages = JSON.parse(localStorage.getItem('smart-logistics-vehicle-images') || '{}')
  } catch {
    localStorage.removeItem('smart-logistics-vehicle-images')
  }

  const vehicles = ref<Vehicle[]>(saved?.vehicles || structuredClone(mockVehicles))
  const vehicleImages = ref<Record<string, string>>(savedVehicleImages)
  vehicles.value.forEach((item) => {
    item.image = vehicleImages.value[item.plate] || item.image
  })
  const cargo = ref<Cargo[]>(saved?.cargo || structuredClone(mockCargo))
  const alerts = ref(saved?.alerts || structuredClone(mockAlerts))
  const devices = ref<Device[]>([])
  const loading = ref(false)
  const error = ref('')
  const realtimeState = ref<ConnectionState>('idle')
  const avatar = ref(localStorage.getItem('smart-logistics-avatar') || '')
  const savedRole = (sessionStorage.getItem('smart-logistics-role') || 'DISPATCHER') as UserRole
  const savedApiUser = tokenManager.getUser()
  const user = ref<{ name: string; role: UserRole; roleLabel: string } | null>(
    savedApiUser
      ? { name: savedApiUser.name, role: savedApiUser.role, roleLabel: roleLabels[savedApiUser.role] }
      : sessionStorage.getItem('smart-logistics-token')
        ? { name: '林若晨', role: savedRole, roleLabel: roleLabels[savedRole] }
        : null,
  )
  const commands = ref<CommandRecord[]>((saved?.commands || defaultCommands()).map((item) => ({
    ...item,
    status: item.status === ('DELIVERED' as CommandRecord['status'])
      ? 'RECEIVED'
      : item.status === ('PENDING' as CommandRecord['status'])
        ? 'SENT'
        : item.status,
  })))
  const notifications = ref<NotificationItem[]>(saved?.notifications || defaultNotifications())

  watch([vehicles, cargo, alerts, commands, notifications], () => {
    localStorage.setItem('smart-logistics-mock-data', JSON.stringify({
      vehicles: vehicles.value,
      cargo: cargo.value,
      alerts: alerts.value,
      commands: commands.value,
      notifications: notifications.value,
    }))
  }, { deep: true })

  const onlineCount = computed(() => vehicles.value.filter((item) => item.status !== 'OFFLINE').length)
  const transitCount = computed(() => vehicles.value.filter((item) => item.status === 'IN_TRANSIT').length)
  const pendingAlertCount = computed(() => alerts.value.filter((item) => item.status === 'PENDING').length)
  const unreadCount = computed(() => notifications.value.filter((item) => !item.read).length)

  const usingDemo = computed(() => isDemoMode())

  function cargoProgress(status: CargoDto['status'], fallback?: number) {
    if (typeof fallback === 'number') return fallback
    if (status === 'DELIVERED') return 100
    if (status === 'IN_TRANSIT') return 68
    if (status === 'LOADED') return 25
    return 0
  }

  function nowText() {
    return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  function formatLocation(item: VehicleDto) {
    if (item.locationDesc) return item.locationDesc
    if (item.position?.lat != null && item.position?.lng != null) {
      return `${item.position.lat.toFixed(5)}, ${item.position.lng.toFixed(5)}`
    }
    return '暂无位置'
  }

  function addNotification(item: Omit<NotificationItem, 'id' | 'time' | 'read'> & Partial<Pick<NotificationItem, 'id' | 'time' | 'read'>>) {
    notifications.value.unshift({
      id: item.id || `NTF-${Date.now()}-${Math.round(Math.random() * 999)}`,
      title: item.title,
      content: item.content,
      type: item.type,
      time: item.time || nowText(),
      read: item.read ?? false,
      targetPage: item.targetPage,
      targetId: item.targetId,
    })
  }

  function syncVehicleCargoBindings() {
    const cargosByPlate = cargo.value.reduce<Record<string, string[]>>((result, item) => {
      if (item.vehiclePlate) {
        if (!result[item.vehiclePlate]) result[item.vehiclePlate] = []
        result[item.vehiclePlate].push(item.id)
      }
      return result
    }, {})
    vehicles.value.forEach((vehicle) => {
      const bound = cargosByPlate[vehicle.plate]
      vehicle.cargoId = bound?.length ? bound.join(', ') : undefined
    })
  }

  function mapVehicle(item: VehicleDto): Vehicle {
    return {
      id: item.id,
      plate: item.plate,
      image: vehicleImages.value[item.plate],
      driver: item.driverName,
      phone: item.driverPhone,
      status: item.status === 'MOVING' ? 'IN_TRANSIT' : item.status === 'STOPPED' ? 'IDLE' : 'OFFLINE',
      speed: item.position?.speed || 0,
      lat: item.position?.lat || 31.2304,
      lng: item.position?.lng || 121.4737,
      heading: item.position?.heading,
      cargoId: item.cargoId,
      location: formatLocation(item),
      heartbeat: item.updatedAt,
      deviceImei: item.deviceImei,
      vehicleType: item.vehicleType,
      capacity: item.capacity,
    }
  }

  function mapCargo(item: CargoDto): Cargo {
    return {
      id: item.cargoId,
      name: item.cargoType,
      category: item.cargoType,
      origin: item.origin.name || `${item.origin.lat}, ${item.origin.lng}`,
      destination: item.destination.name || `${item.destination.lat}, ${item.destination.lng}`,
      progress: cargoProgress(item.status, item.progress),
      status: item.status,
      vehicleId: item.vehicleId,
      vehiclePlate: item.vehiclePlate,
      eta: item.eta || '待计算',
      weight: item.weight,
      originLat: item.origin.lat,
      originLng: item.origin.lng,
      destinationLat: item.destination.lat,
      destinationLng: item.destination.lng,
      distanceRemaining: item.distanceRemaining,
      createdAt: item.createdAt,
      updatedAt: item.updatedAt,
      loadedAt: item.loadedAt,
      deliveredAt: item.deliveredAt,
      received: item.status === 'DELIVERED',
    }
  }

  function mapAlert(item: AlertDto): AlertItem {
    return {
      id: item.alertId,
      title: item.title,
      type: item.alertType,
      severity: item.severity,
      status: item.status,
      plate: item.vehiclePlate,
      location: item.location ? `${item.location.lat}, ${item.location.lng}` : '位置未知',
      createdAt: item.triggeredAt,
      description: item.description || item.summary || '',
      logs: item.logs,
    }
  }

  function mapCommand(item: CommandDto): CommandRecord {
    return {
      id: item.commandId,
      plate: item.plate,
      type: item.commandType,
      content: String(item.payload?.content || ''),
      status: item.status === 'FAILED' ? 'FAILED' : item.status,
      createdAt: item.createdAt || new Date().toISOString(),
    }
  }

  async function login(username: string, password: string, role: UserRole = 'DISPATCHER') {
    error.value = ''
    if (usingDemo.value) {
      sessionStorage.setItem('smart-logistics-token', 'demo-token')
      sessionStorage.setItem('smart-logistics-role', role)
      user.value = { name: username || '林若晨', role, roleLabel: roleLabels[role] }
      return
    }
    const result = await authApi.login({ username, password })
    setApiUser(result.user)
    await initialize()
  }

  async function loginWithFace(image: Blob, role: UserRole = 'DISPATCHER') {
    error.value = ''
    if (usingDemo.value) {
      sessionStorage.setItem('smart-logistics-token', 'demo-token')
      sessionStorage.setItem('smart-logistics-role', role)
      user.value = { name: '人脸识别用户', role, roleLabel: roleLabels[role] }
      return
    }
    const result = await authApi.faceLogin(image)
    setApiUser(result.user)
    await initialize()
  }

  function setApiUser(profile: UserProfile) {
    user.value = { name: profile.name, role: profile.role, roleLabel: roleLabels[profile.role] }
  }

  async function logout() {
    realtimeClient.disconnect()
    window.clearInterval(pollingTimer)
    if (!usingDemo.value && tokenManager.getAccessToken()) {
      try { await authApi.logout() } catch { tokenManager.clear() }
    }
    sessionStorage.removeItem('smart-logistics-token')
    sessionStorage.removeItem('smart-logistics-role')
    user.value = null
  }

  function switchRole(role: UserRole) {
    if (!user.value) return
    sessionStorage.setItem('smart-logistics-role', role)
    user.value = { ...user.value, role, roleLabel: roleLabels[role] }
  }

  function setAvatar(image: string) {
    avatar.value = image
    if (image) localStorage.setItem('smart-logistics-avatar', image)
    else localStorage.removeItem('smart-logistics-avatar')
  }

  function setVehicleImage(plate: string, image: string) {
    if (image) vehicleImages.value[plate] = image
    else delete vehicleImages.value[plate]
    localStorage.setItem('smart-logistics-vehicle-images', JSON.stringify(vehicleImages.value))
    const target = vehicles.value.find((item) => item.plate === plate)
    if (target) target.image = image || undefined
  }

  async function updateAlert(id: string, status: AlertStatus, resolution = '', remark = '') {
    if (!usingDemo.value) {
      const updated = status === 'ACKNOWLEDGED'
        ? await alertApi.acknowledge(id, remark)
        : await alertApi.resolve(id, resolution, remark)
      const index = alerts.value.findIndex((item) => item.id === id)
      if (index >= 0) alerts.value[index] = mapAlert(updated)
      return
    }
    const target = alerts.value.find((item) => item.id === id)
    if (target) target.status = status
  }

  async function addVehicle(vehicle: Vehicle) {
    if (!usingDemo.value) {
      await vehicleApi.create({
        plate: vehicle.plate,
        vehicleType: vehicle.vehicleType || 'TRUCK',
        capacity: vehicle.capacity || 10,
        driverName: vehicle.driver,
        driverPhone: vehicle.phone,
        deviceImei: vehicle.deviceImei || '',
      })
      await Promise.all([loadVehicles(), loadDevices()])
      return
    }
    vehicles.value.unshift(vehicle)
  }

  async function addCargo(item: Cargo) {
    if (!usingDemo.value) {
      await cargoApi.create({
        cargoId: item.id,
        cargoType: item.category,
        weight: item.weight || 0,
        origin: { name: item.origin, lat: item.originLat || 0, lng: item.originLng || 0 },
        destination: { name: item.destination, lat: item.destinationLat || 0, lng: item.destinationLng || 0 },
      })
      await loadCargo()
      return
    }
    cargo.value.unshift(item)
  }

  async function updateVehicle(plate: string, patch: Partial<Vehicle>) {
    if (!usingDemo.value) {
      const current = vehicles.value.find((item) => item.plate === plate)
      if (!current) return
      const vehicleId = current.id || (await vehicleApi.detail(plate)).id
      await vehicleApi.update(vehicleId, {
        plate: current.plate,
        vehicleType: patch.vehicleType || current.vehicleType || 'TRUCK',
        capacity: patch.capacity || current.capacity || 10,
        driverName: patch.driver || current.driver,
        driverPhone: patch.phone || current.phone,
        deviceImei: patch.deviceImei || current.deviceImei || '',
        status: current.status === 'IN_TRANSIT' ? 'MOVING' : current.status === 'IDLE' ? 'STOPPED' : 'OFFLINE',
      })
      await Promise.all([loadVehicles(), loadDevices()])
      return
    }
    const index = vehicles.value.findIndex((item) => item.plate === plate)
    if (index >= 0) vehicles.value[index] = { ...vehicles.value[index], ...patch }
  }

  async function removeVehicle(plate: string) {
    if (!usingDemo.value) {
      const current = vehicles.value.find((item) => item.plate === plate)
      const vehicleId = current?.id || (await vehicleApi.detail(plate)).id
      await vehicleApi.remove(vehicleId)
      setVehicleImage(plate, '')
      await Promise.all([loadVehicles(), loadCargo(), loadDevices()])
      return
    }
    vehicles.value = vehicles.value.filter((item) => item.plate !== plate)
    setVehicleImage(plate, '')
    cargo.value.forEach((item) => {
      if (item.vehiclePlate === plate) item.vehiclePlate = undefined
    })
  }

  async function bindCargo(cargoId: string, plate: string) {
    const applyLocalBinding = () => {
      const item = cargo.value.find((entry) => entry.id === cargoId)
      const vehicle = vehicles.value.find((entry) => entry.plate === plate)
      if (item) {
        item.vehiclePlate = plate
        item.vehicleId = vehicle?.id
        item.status = 'IN_TRANSIT'
      }
      syncVehicleCargoBindings()
    }

    if (!usingDemo.value) {
      const vehicle = vehicles.value.find((entry) => entry.plate === plate)
      const vehicleId = vehicle?.id || (await vehicleApi.detail(plate)).id
      await cargoApi.bind(cargoId, vehicleId)
      applyLocalBinding()
      return
    }
    applyLocalBinding()
  }

  async function unbindCargo(cargoId: string) {
    const applyLocalUnbind = () => {
      const item = cargo.value.find((entry) => entry.id === cargoId)
      if (!item?.vehiclePlate) return
      item.vehiclePlate = undefined
      item.vehicleId = undefined
      syncVehicleCargoBindings()
    }

    if (!usingDemo.value) {
      await cargoApi.unbind(cargoId)
      applyLocalUnbind()
      return
    }
    applyLocalUnbind()
  }

  async function updateCargoStatus(cargoId: string, status: Cargo['status'], remark = '') {
    if (!usingDemo.value) {
      const target = cargo.value.find((item) => item.id === cargoId)
      const vehicle = vehicles.value.find((item) => item.plate === target?.vehiclePlate)
      const updated = await cargoApi.updateStatus(cargoId, {
        status,
        remark,
        lat: vehicle?.lat,
        lng: vehicle?.lng,
        operatorId: tokenManager.getUser()?.id,
      })
      const index = cargo.value.findIndex((item) => item.id === cargoId)
      if (index >= 0) cargo.value[index] = mapCargo(updated)
      syncVehicleCargoBindings()
      addNotification({
        title: '货物状态已更新',
        content: `${cargoId} 已更新为 ${status}`,
        type: 'system',
        targetPage: 'tracking',
        targetId: cargoId,
      })
      return
    }
    const item = cargo.value.find((entry) => entry.id === cargoId)
    if (!item) return
    item.status = status
    if (status === 'DELIVERED') item.progress = 100
    addNotification({
      title: '货物状态已更新',
      content: `${cargoId} 已更新为 ${status}`,
      type: 'system',
      targetPage: 'tracking',
      targetId: cargoId,
    })
  }

  async function confirmReceipt(cargoId: string) {
    const item = cargo.value.find((entry) => entry.id === cargoId)
    if (!item) return
    if (item.status === 'DELIVERED') return
    await updateCargoStatus(cargoId, 'DELIVERED', '前端确认收货')
    const updated = cargo.value.find((entry) => entry.id === cargoId)
    if (updated) updated.received = true
  }

  async function issueCommand(plate: string, type: string, content: string) {
    if (!usingDemo.value) {
      const result = await vehicleApi.sendCommand(plate, {
        commandType: type,
        priority: 'NORMAL',
        payload: { content },
      })
      const record = mapCommand(result)
      commands.value.unshift(record)
      return record
    }
    const record: CommandRecord = {
      id: `CMD-${Date.now().toString().slice(-8)}`,
      plate,
      type,
      content,
      status: 'SENT',
      createdAt: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    }
    commands.value.unshift(record)
    window.setTimeout(() => {
      record.status = 'RECEIVED'
      addNotification({
        title: '调度指令已送达',
        content: `${record.id} 已送达 ${plate} 车载终端，等待司机执行。`,
        type: 'command',
        targetPage: 'driver',
        targetId: record.id,
      })
    }, 800)
    return record
  }

  function executeCommand(id: string) {
    const record = commands.value.find((item) => item.id === id)
    if (!record) return
    record.status = 'EXECUTED'
    record.executedAt = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    addNotification({
      title: '调度指令执行完成',
      content: `${record.plate} 已确认执行「${record.type}」。`,
      type: 'command',
      time: record.executedAt,
      targetPage: 'driver',
      targetId: id,
    })
  }

  function markNotificationsRead(ids?: string[]) {
    const targetIds = ids ? new Set(ids) : null
    notifications.value.forEach((item) => {
      if (!targetIds || targetIds.has(item.id)) item.read = true
    })
  }

  function markNotificationRead(id: string) {
    const target = notifications.value.find((item) => item.id === id)
    if (target) target.read = true
  }

  function removeNotification(id: string) {
    notifications.value = notifications.value.filter((item) => item.id !== id)
  }

  function removeReadNotifications() {
    notifications.value = notifications.value.filter((item) => !item.read)
  }

  function removeAlerts(ids: string[]) {
    const targetIds = new Set(ids)
    alerts.value = alerts.value.filter((item) => !targetIds.has(item.id))
    if (!usingDemo.value) {
      addNotification({
        title: '告警列表已整理',
        content: `已从当前工作台移除 ${ids.length} 条已处理告警。`,
        type: 'system',
        targetPage: 'alerts',
      })
    }
  }

  function refreshLiveData() {
    vehicles.value.forEach((item) => {
      if (item.status === 'IN_TRANSIT') {
        item.lat = Math.max(8, Math.min(92, item.lat + Number((Math.random() * 2 - 0.4).toFixed(1))))
        item.lng = Math.max(8, Math.min(92, item.lng + Number((Math.random() * 1.4 - 0.3).toFixed(1))))
        item.speed = Math.max(45, Math.min(92, item.speed + Math.round(Math.random() * 10 - 5)))
        item.heartbeat = '刚刚'
      }
    })
  }

  function resetDemoData() {
    vehicles.value = structuredClone(mockVehicles)
    vehicles.value.forEach((item) => {
      item.image = vehicleImages.value[item.plate] || item.image
    })
    cargo.value = structuredClone(mockCargo)
    alerts.value = structuredClone(mockAlerts)
    commands.value = defaultCommands()
    notifications.value = defaultNotifications()
    localStorage.removeItem('smart-logistics-mock-data')
  }

  function simulateAlert() {
    const now = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    const item: AlertItem = {
      id: `ALT-${Date.now().toString().slice(-6)}`,
      title: '冷链温度异常',
      type: 'TEMPERATURE_HIGH',
      severity: 'CRITICAL',
      status: 'PENDING',
      plate: '浙B·L8821',
      location: '嘉兴服务区',
      createdAt: now,
      description: '货箱温度连续 3 分钟高于设定阈值，请立即联系司机检查制冷设备。',
    }
    alerts.value.unshift(item)
    addNotification({
      title: item.title,
      content: `${item.plate} · ${item.location}，请立即处理。`,
      type: 'alert',
      time: now,
      targetPage: 'alerts',
      targetId: item.id,
    })
    return item
  }

  async function loadVehicles() {
    if (usingDemo.value) return
    vehicles.value = (await vehicleApi.list({ page: 1, size: 200 })).content.map(mapVehicle)
    syncVehicleCargoBindings()
  }

  async function loadCargo() {
    if (usingDemo.value) return
    cargo.value = (await cargoApi.list({ page: 1, size: 200 })).content.map(mapCargo)
    syncVehicleCargoBindings()
  }

  async function loadAlerts() {
    if (usingDemo.value) return
    alerts.value = (await alertApi.list({ page: 1, size: 200 })).content.map(mapAlert)
  }

  async function loadDevices() {
    if (usingDemo.value) {
      devices.value = vehicles.value.map((item) => ({
        imei: item.deviceImei || `GPS-${item.plate.replace('·', '-')}`,
        plate: item.plate,
        status: item.status === 'OFFLINE' ? 'OFFLINE' : 'ONLINE',
        lastHeartbeat: item.heartbeat,
      }))
      return
    }
    const result = await deviceApi.status({ page: 1, size: 200 })
    devices.value = result.devices.map((item: DeviceDto) => ({
      imei: item.imei,
      plate: item.plate,
      status: item.status,
      lastHeartbeat: item.lastHeartbeat,
      battery: item.battery,
      signal: item.signal,
    }))
  }

  let pollingTimer: number | undefined
  let realtimeStarted = false
  function startRealtime() {
    if (realtimeStarted) {
      realtimeClient.connect()
      return
    }
    realtimeStarted = true
    realtimeClient.onStateChange((state) => {
      realtimeState.value = state
      window.clearInterval(pollingTimer)
      if (!usingDemo.value && state !== 'open') {
        pollingTimer = window.setInterval(() => {
          void Promise.all([loadVehicles(), loadAlerts()]).catch((cause) => {
            error.value = cause instanceof Error ? cause.message : '轮询数据失败'
          })
        }, 15_000)
      }
    })
    realtimeClient.on('vehicle.position', (event) => {
      const target = vehicles.value.find((item) => item.plate === event.plate)
      if (target) Object.assign(target, { lat: event.lat, lng: event.lng, speed: event.speed, heading: event.heading, heartbeat: event.timestamp })
    })
    realtimeClient.on('alert.triggered', (event) => {
      if (!alerts.value.some((item) => item.id === event.alertId)) {
        alerts.value.unshift({
          id: event.alertId, title: event.title, type: event.alertType, severity: event.severity,
          status: 'PENDING', plate: event.vehiclePlate, location: '实时上报', createdAt: event.triggeredAt, description: event.title,
        })
        addNotification({
          title: event.title,
          content: `${event.vehiclePlate} 触发实时告警，请进入告警中心处理。`,
          type: 'alert',
          time: event.triggeredAt ? new Date(event.triggeredAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : undefined,
          targetPage: 'alerts',
          targetId: event.alertId,
        })
      }
    })
    realtimeClient.on('command.ack', (event) => {
      const target = commands.value.find((item) => item.id === event.commandId)
      if (target) target.status = event.status === 'FAILED' ? 'FAILED' : event.status
      addNotification({
        title: '调度指令回执',
        content: `${event.commandId} 状态更新为 ${event.status}`,
        type: 'command',
        targetPage: 'driver',
        targetId: event.commandId,
      })
    })
    realtimeClient.subscribe('vehicle.position', ['*'])
    realtimeClient.subscribe('alert.triggered', ['*'])
    realtimeClient.subscribe('command.ack', ['*'])
  }

  async function initialize() {
    loading.value = true
    error.value = ''
    try {
      if (!usingDemo.value && tokenManager.getAccessToken()) {
        const profile = await authApi.me()
        setApiUser(profile)
        await Promise.all([loadVehicles(), loadCargo(), loadAlerts(), loadDevices()])
      } else {
        await loadDevices()
      }
      startRealtime()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '数据加载失败'
      throw cause
    } finally {
      loading.value = false
    }
  }

  return {
    vehicles,
    cargo,
    alerts,
    user,
    avatar,
    onlineCount,
    transitCount,
    pendingAlertCount,
    unreadCount,
    commands,
    notifications,
    devices,
    loading,
    error,
    realtimeState,
    usingDemo,
    initialize,
    loadVehicles,
    loadCargo,
    loadAlerts,
    loadDevices,
    login,
    loginWithFace,
    logout,
    switchRole,
    setAvatar,
    setVehicleImage,
    updateAlert,
    addVehicle,
    addCargo,
    updateVehicle,
    removeVehicle,
    bindCargo,
    unbindCargo,
    updateCargoStatus,
    confirmReceipt,
    issueCommand,
    executeCommand,
    addNotification,
    markNotificationsRead,
    markNotificationRead,
    removeNotification,
    removeReadNotifications,
    removeAlerts,
    refreshLiveData,
    resetDemoData,
    simulateAlert,
  }
})
