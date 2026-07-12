import type { AlertItem, Cargo, TimelineItem, Vehicle } from '@/types'

export const mockVehicles: Vehicle[] = [
  { plate: '沪A·C0291', driver: '张建国', phone: '138****0291', status: 'IN_TRANSIT', speed: 76, lat: 52, lng: 45, cargoId: 'SH20260629001', location: 'G60 嘉善段', heartbeat: '刚刚' },
  { plate: '浙B·L8821', driver: '林若晨', phone: '136****8821', status: 'IN_TRANSIT', speed: 62, lat: 68, lng: 55, cargoId: 'HZ20260629008', location: '杭州余杭', heartbeat: '12 秒前' },
  { plate: '苏E·Q6715', driver: '陈宇', phone: '159****6715', status: 'IDLE', speed: 0, lat: 34, lng: 63, location: '苏州吴江仓', heartbeat: '28 秒前' },
  { plate: '皖A·M1208', driver: '周师傅', phone: '137****1208', status: 'OFFLINE', speed: 0, lat: 78, lng: 34, cargoId: 'HF20260629003', location: '常台高速苏州段', heartbeat: '8 分钟前' },
  { plate: '沪D·K9302', driver: '王海', phone: '135****9302', status: 'IDLE', speed: 0, lat: 23, lng: 31, cargoId: 'SH20260630006', location: '上海仓储中心', heartbeat: '45 秒前' },
]

export const mockCargo: Cargo[] = [
  { id: 'SH20260629001', name: '智能控制器', category: '电子产品', origin: '上海仓储中心', destination: '杭州余杭物流中心', progress: 68, status: 'IN_TRANSIT', vehiclePlate: '沪A·C0291', eta: '今天 16:42' },
  { id: 'HZ20260629008', name: '工业传感器', category: '精密器件', origin: '宁波北仑仓', destination: '苏州工业园', progress: 43, status: 'IN_TRANSIT', vehiclePlate: '浙B·L8821', eta: '今天 18:10' },
  { id: 'HF20260629003', name: '冷链药品', category: '医药冷链', origin: '合肥医药仓', destination: '上海浦东仓', progress: 25, status: 'IN_TRANSIT', vehiclePlate: '皖A·M1208', eta: '明天 09:30' },
  { id: 'SH20260630006', name: '纺织面料', category: '普通货物', origin: '上海松江仓', destination: '嘉兴仓', progress: 100, status: 'DELIVERED', vehiclePlate: '沪D·K9302', eta: '已于 13:20 到达' },
]

export const mockAlerts: AlertItem[] = [
  { id: 'ALT-0629-01', title: '车辆偏离预设路线', type: 'ROUTE_DEVIATION', severity: 'CRITICAL', status: 'PENDING', plate: '沪A·C0291', location: 'G320 国道海宁段', createdAt: '14:28', description: '车辆偏离 G60 推荐路线约 8 公里，已持续 12 分钟。' },
  { id: 'ALT-0629-02', title: '设备心跳中断', type: 'DEVICE_OFFLINE', severity: 'WARNING', status: 'PENDING', plate: '皖A·M1208', location: '常台高速苏州段', createdAt: '14:17', description: '车载终端超过 90 秒未上报心跳，请联系司机确认设备状态。' },
  { id: 'ALT-0629-03', title: '异常停车', type: 'ABNORMAL_STOP', severity: 'WARNING', status: 'ACKNOWLEDGED', plate: '浙B·L8821', location: '嘉兴服务区', createdAt: '13:52', description: '车辆在非计划站点停留超过 15 分钟，调度员已确认。' },
  { id: 'ALT-0629-04', title: '货箱异常开箱', type: 'CARGO_OPEN', severity: 'INFO', status: 'RESOLVED', plate: '苏E·Q6715', location: '苏州吴江仓', createdAt: '12:36', description: '仓内装卸触发开箱事件，已由仓管确认关闭。' },
]

export const mockTimeline: TimelineItem[] = [
  { time: '08:30', title: '货物装车', description: '上海仓储中心 · 张建国确认装货' },
  { time: '09:05', title: '离开仓库', description: '驶出上海仓储中心，进入 G60' },
  { time: '11:42', title: '途经嘉兴', description: '嘉兴服务区 · 短暂停靠' },
  { time: '14:28', title: '偏航告警', description: '偏离 G60，进入 G320 国道', active: true },
  { time: '16:42', title: '预计到达', description: '杭州余杭物流中心' },
]
