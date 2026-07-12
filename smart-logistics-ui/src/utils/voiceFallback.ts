type AgentAction = Record<string, any>

const digitMap: Record<string, string> = {
  零: '0',
  〇: '0',
  幺: '1',
  一: '1',
  二: '2',
  两: '2',
  三: '3',
  四: '4',
  五: '5',
  六: '6',
  七: '7',
  八: '8',
  九: '9',
}

const provinceChars = '京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼'

function normalizeText(value: unknown) {
  return String(value || '')
    .replace(/[零〇幺一二两三四五六七八九]/g, (char) => digitMap[char] || char)
    .toUpperCase()
    .replace(/[，。！？、,.!?;；:：'"“”‘’()[\]{}<>《》]/g, '')
    .replace(/\s+/g, '')
}

function includesAny(text: string, words: string[]) {
  return words.some((word) => text.includes(normalizeText(word)))
}

function hasOpenIntent(text: string) {
  return includesAny(text, ['打开', '进入', '去', '看', '查看', '切到', '切换', '到'])
}

function extractPlate(raw: string) {
  const text = normalizeText(raw).replace(/[·•.\-_]/g, '')
  const match = text.match(new RegExp(`[${provinceChars}][A-Z][A-Z0-9]{4,6}`))
  if (!match) return ''
  const value = match[0]
  return `${value.slice(0, 2)}·${value.slice(2)}`
}

function extractOrderId(raw: string) {
  const text = normalizeText(raw).replace(/[·•.\-_]/g, '')
  const documented = text.match(/[A-Z]{4}\d{12}/)
  if (documented) return documented[0]
  const compact = text.match(/[A-Z]{2}\d{11}/)
  if (compact) return compact[0]
  const ord = text.match(/ORD\d{6,}/)
  if (ord) return ord[0]
  return ''
}

function navigate(routeName: string, reply: string, query: Record<string, string> = {}, params: Record<string, string> = {}): AgentAction {
  return { type: 'NAVIGATE', routeName, targetValue: routeName, query, params, reply, fallback: true }
}

function functionCall(functionName: string, args: Record<string, any>, recognizedText: string, reply: string): AgentAction {
  return {
    type: 'CALL_FUNCTION',
    functionName,
    arguments: args,
    recognizedText,
    reply,
    fallback: true,
  }
}

function locateVehicle(plate: string): AgentAction {
  return {
    type: 'HIGHLIGHT_MAP_TARGET',
    targetType: 'VEHICLE',
    plate,
    targetValue: plate,
    mapAction: 'CENTER_AND_ZOOM',
    zoom: 15,
    reply: `正在定位 ${plate}`,
    fallback: true,
  }
}

function rerouteVehicle(plate: string, recognizedText: string): AgentAction {
  return functionCall('dispatch_vehicle', {
    plate,
    commandType: 'REROUTE',
    content: '语音指令触发改道，请按调度建议路线行驶。',
  }, recognizedText, `已识别为给 ${plate} 下发改道指令，下发前需要确认`)
}

function vehicleCommand(plate: string, commandType: string, recognizedText: string, message?: string): AgentAction {
  const commandText: Record<string, string> = {
    STOP: '停车',
    RETURN: '返程',
    REROUTE: '改道',
    NOTIFY: '提醒',
  }
  const label = commandText[commandType] || '调度'
  const payloadContent = message || `语音指令触发${label}，请按调度要求执行。`
  return functionCall('dispatch_vehicle', { plate, commandType, content: payloadContent }, recognizedText, `已识别为给 ${plate} 下发${label}指令，下发前需要确认`)
}

function latestAlertAction(operation: 'ACKNOWLEDGE' | 'RESOLVE', recognizedText: string, severity?: string): AgentAction {
  const isResolve = operation === 'RESOLVE'
  return {
    type: 'SMART_ALERT_ACTION',
    operation: isResolve ? 'RESOLVE' : 'ACKNOWLEDGE',
    recognizedText,
    severity,
    statuses: isResolve ? ['ACKNOWLEDGED', 'PENDING'] : ['PENDING'],
    title: isResolve ? '关闭最新告警' : '确认最新告警',
    remark: '由语音助手执行',
    resolution: '语音指令关闭',
    emptyText: severity ? `没有找到可处理的${severity === 'CRITICAL' ? '严重' : '指定级别'}告警` : '没有找到可处理的告警',
    successText: isResolve ? '最新告警已关闭' : '最新告警已确认',
    reply: isResolve ? '已识别为关闭最新告警，执行前需要确认' : '已识别为确认最新告警，执行前需要确认',
    fallback: true,
  }
}

function extractNotifyMessage(raw: string) {
  const text = String(raw || '')
  const markers = ['提醒', '通知', '消息', '告诉']
  for (const marker of markers) {
    const index = text.indexOf(marker)
    if (index >= 0) {
      const value = text.slice(index + marker.length).replace(/^[：:，,\s]+/, '').trim()
      if (value) return value
    }
  }
  return ''
}

function extractPhone(raw: string) {
  return String(raw || '').replace(/\s+/g, '').match(/1\d{10}/)?.[0] || ''
}

function extractDriverName(raw: string) {
  return String(raw || '').match(/(?:司机|驾驶员)[：:\s]*([\u4e00-\u9fa5]{2,4}?)(?=\s*(?:手机号|电话|号码|1\d|，|,|。|$))/)?.[1] || ''
}

export function resolveVoiceFallbackAction(payload: Record<string, any>) {
  const recognizedText = payload.recognizedText || payload.text || ''
  const text = normalizeText(recognizedText)
  if (!text) return null

  const plate = extractPlate(recognizedText)
  const orderId = extractOrderId(recognizedText)

  if (plate && orderId && includesAny(text, ['调度货物', '分配货物', '绑定车辆', '装到', '装入', '派给'])) {
    return functionCall('bind_cargo_vehicle', { cargoId: orderId, plate }, recognizedText, `准备将货物 ${orderId} 调度到 ${plate}`)
  }
  if (orderId && includesAny(text, ['解除绑定', '取消绑定', '移出车辆', '卸下货物'])) {
    return functionCall('unbind_cargo_vehicle', { cargoId: orderId }, recognizedText, `准备解除货物 ${orderId} 的车辆绑定`)
  }
  if (orderId && includesAny(text, ['确认收货', '完成收货', '签收货物', '货物签收'])) {
    return functionCall('confirm_receipt', { cargoId: orderId }, recognizedText, `准备确认货物 ${orderId} 收货`)
  }
  if (orderId && includesAny(text, ['更新状态', '改成已装货', '设为已装货', '开始运输', '设为运输中', '取消货物', '取消订单'])) {
    const status = includesAny(text, ['已装货']) ? 'LOADED'
      : includesAny(text, ['运输中', '开始运输']) ? 'IN_TRANSIT'
        : includesAny(text, ['取消']) ? 'CANCELLED' : 'CREATED'
    return functionCall('update_cargo_status', { cargoId: orderId, status }, recognizedText, `准备更新货物 ${orderId} 状态`)
  }
  if (orderId && includesAny(text, ['创建货物', '新增货物', '新建货物', '创建订单', '新增订单'])) {
    return functionCall('create_cargo', { cargoId: orderId }, recognizedText, `准备创建货物 ${orderId}`)
  }
  if (plate && includesAny(text, ['删除车辆', '移除车辆'])) {
    return functionCall('delete_vehicle', { plate }, recognizedText, `准备删除车辆 ${plate}`)
  }
  if (plate && includesAny(text, ['新增车辆', '添加车辆', '创建车辆'])) {
    return functionCall('create_vehicle', { plate }, recognizedText, `准备新增车辆 ${plate}`)
  }
  if (plate && includesAny(text, ['设为离线', '改为离线', '设为待命', '改为待命', '设为运输中', '改为运输中'])) {
    const status = includesAny(text, ['离线']) ? 'OFFLINE' : includesAny(text, ['待命']) ? 'IDLE' : 'IN_TRANSIT'
    return functionCall('update_vehicle', { plate, status }, recognizedText, `准备更新车辆 ${plate} 状态`)
  }

  const phone = extractPhone(recognizedText)
  const driverName = extractDriverName(recognizedText)
  if (driverName && phone && includesAny(text, ['新增司机', '添加司机', '新增驾驶员', '添加驾驶员'])) {
    return functionCall('add_driver', { name: driverName, phone }, recognizedText, `准备新增司机 ${driverName}`)
  }
  if (driverName && includesAny(text, ['删除司机', '移除司机', '删除驾驶员'])) {
    return functionCall('delete_driver', { name: driverName }, recognizedText, `准备删除司机 ${driverName}`)
  }

  if (includesAny(text, ['执行调度指令', '执行最新指令', '确认执行指令', '完成调度指令'])) {
    return functionCall('execute_dispatch_command', {}, recognizedText, '准备执行最新收到的调度指令')
  }
  if (includesAny(text, ['全部通知已读', '标记全部通知', '所有通知已读'])) {
    return functionCall('mark_notifications_read', {}, recognizedText, '准备将全部通知设为已读')
  }
  if (includesAny(text, ['清除已读通知', '删除已读通知'])) {
    return functionCall('clear_read_notifications', {}, recognizedText, '准备清除已读通知')
  }
  if (includesAny(text, ['生成演示告警', '模拟告警', '新增测试告警'])) {
    return functionCall('simulate_alert', {}, recognizedText, '准备生成一条演示告警')
  }
  if (includesAny(text, ['重置演示数据', '恢复演示数据'])) {
    return functionCall('reset_demo_data', {}, recognizedText, '准备重置本地演示数据')
  }
  const wantsReroute = includesAny(text, ['改道', '改到', '绕行', '路线调整', '重新规划', '重规划']) && includesAny(text, ['下发', '发送', '发给', '通知', '指令', '执行'])
  if (plate && wantsReroute) return rerouteVehicle(plate, recognizedText)

  const wantsStop = plate && includesAny(text, ['停车', '靠边停', '停止行驶', '暂停运输', '紧急停车']) && includesAny(text, ['下发', '发送', '通知', '指令', '执行'])
  if (plate && wantsStop) return vehicleCommand(plate, 'STOP', recognizedText)

  const wantsReturn = plate && includesAny(text, ['返程', '返回', '回仓', '回库', '返回仓库']) && includesAny(text, ['下发', '发送', '通知', '指令', '执行'])
  if (plate && wantsReturn) return vehicleCommand(plate, 'RETURN', recognizedText)

  const wantsNotify = plate && includesAny(text, ['提醒', '通知', '消息', '告诉']) && includesAny(text, ['下发', '发送', '发给', '通知'])
  if (plate && wantsNotify) return vehicleCommand(plate, 'NOTIFY', recognizedText, extractNotifyMessage(recognizedText))

  const wantsAckAlert = includesAny(text, ['确认告警', '处理告警', '接收告警', '确认最新告警', '处理最新告警'])
  if (wantsAckAlert) return latestAlertAction('ACKNOWLEDGE', recognizedText, includesAny(text, ['严重', '最高', '紧急']) ? 'CRITICAL' : undefined)

  const wantsResolveAlert = includesAny(text, ['关闭告警', '解决告警', '消除告警', '关闭最新告警', '解决最新告警'])
  if (wantsResolveAlert) return latestAlertAction('RESOLVE', recognizedText, includesAny(text, ['严重', '最高', '紧急']) ? 'CRITICAL' : undefined)

  const wantsDeleteAlert = includesAny(text, ['删除告警', '移除告警', '删除最新告警'])
  if (wantsDeleteAlert) return functionCall('delete_resolved_alert', {}, recognizedText, '准备删除最新已关闭告警')

  const wantsLocation = includesAny(text, ['定位', '位置', '在哪', '到哪', '找车', '找到', '聚焦', '跟踪'])
  if (plate && wantsLocation) return locateVehicle(plate)

  if (includesAny(text, ['订单', '运单'])) {
    if (orderId) return navigate('OrderDetail', `正在打开订单 ${orderId}`, { orderId }, { orderId })
    if (hasOpenIntent(text)) return navigate('OrderList', '正在打开订单列表')
  }

  if (includesAny(text, ['首页', '主页', '总览', '仪表盘', '大屏'])) return navigate('Dashboard', '正在打开首页')
  if (includesAny(text, ['车辆调度', '车俩调度', '车量调度', '车队总览', '调度台', '调度页面'])) return navigate('FleetOverview', '正在打开车辆调度')
  if (includesAny(text, ['货物追踪', '货物跟踪', '货物运输', '物流追踪', '追踪页'])) return navigate('CargoTracking', '正在打开货物追踪')
  if (includesAny(text, ['人员管理', '人员页面', '员工管理', '人员列表', '司机管理', '驾驶员管理'])) return navigate('PersonnelManagement', '正在打开人员管理')
  if (includesAny(text, ['司机任务', '司机页面', '司机工作台', '司机端', '驾驶任务', '任务页面'])) return navigate('DriverTasks', '正在打开司机任务')
  if (includesAny(text, ['智能问答', '智能助手', '问答页面', '问答中心', 'AI问答', '助手页面', '知识库问答'])) return navigate('SmartAssistant', '正在打开智能问答')
  if (includesAny(text, ['仓库管理', '仓储管理', '仓库页面', '仓储页面', '设备管理', '库存管理'])) return navigate('WarehouseManagement', '正在打开仓库管理')
  if (includesAny(text, ['设备在线', '在线设备', '设备状态', '终端在线', 'GPS在线', 'GPS状态'])) return navigate('DeviceStatus', '正在打开设备在线')
  if (includesAny(text, ['风险中心', '风险订单', '告警中心', '报警中心', '异常中心', '未处理告警', '未处理报警'])) return navigate('RiskCenter', '正在打开风险中心')
  if (includesAny(text, ['返回导航', '导航窗口', '导航界面', '回到导航', '打开导航'])) return navigate('NavigationPortal', '正在返回导航窗口')
  if (includesAny(text, ['司机评分', '司机评价', '驾驶员评分', '张建国评分'])) return navigate('DriverRating', '正在打开司机评分', text.includes('张建国') ? { driverName: '张建国' } : {})
  if (includesAny(text, ['改址审核', '地址审核', '改地址审核'])) return navigate('AddressChangeReview', '正在打开改址审核')
  if (includesAny(text, ['卸货地址', '卸货点', '卸货确认'])) return navigate('UnloadAddressConfirm', '正在打开卸货地址确认')

  return null
}
