import type { Cargo, TimelineItem, Vehicle } from '@/types'
import csvText from './chongqing_gps_points.csv?raw'

export interface ChongqingGpsPoint {
  time: string
  vehicleId: string
  cargoId: string
  imei: string
  lat: number
  lng: number
  speed: number
  heading: number
  accuracy: number
}

export const chongqingVehiclePlate = '渝A·0291'

function normalizeTime(value: string) {
  return value.replace(' ', 'T').replace(/\+(\d{2})$/, '+$1:00')
}

function displayTime(value: string) {
  const date = new Date(normalizeTime(value))
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function parseCsv(text: string): ChongqingGpsPoint[] {
  return text
    .trim()
    .split(/\r?\n/)
    .slice(1)
    .map((line) => {
      const [time, vehicleId, cargoId, imei, lat, lng, speed, heading, accuracy] = line.split(',')
      return {
        time,
        vehicleId,
        cargoId,
        imei,
        lat: Number(lat),
        lng: Number(lng),
        speed: Number(speed),
        heading: Number(heading),
        accuracy: Number(accuracy),
      }
    })
    .filter((point) => Number.isFinite(point.lat) && Number.isFinite(point.lng))
}

export const chongqingGpsPoints = parseCsv(csvText)
export const chongqingCargoId = chongqingGpsPoints[0]?.cargoId || 'SH-HZ-20260629-0291'
export const chongqingDeviceImei = chongqingGpsPoints[0]?.imei || '861234567890123'

const firstPoint = chongqingGpsPoints[0]
const latestPoint = chongqingGpsPoints[chongqingGpsPoints.length - 1] || firstPoint

export const chongqingMockVehicle: Vehicle = {
  id: Number(latestPoint?.vehicleId || 1),
  plate: chongqingVehiclePlate,
  driver: '硬件实测车',
  phone: '861234567890123',
  status: 'IN_TRANSIT',
  speed: latestPoint?.speed ?? 0,
  lat: latestPoint?.lat ?? 29.61965,
  lng: latestPoint?.lng ?? 106.50319,
  heading: latestPoint?.heading ?? 0,
  cargoId: chongqingCargoId,
  location: '重庆实测点位',
  heartbeat: latestPoint ? displayTime(latestPoint.time) : '刚刚',
  deviceImei: chongqingDeviceImei,
  vehicleType: 'IOT_TEST_TRUCK',
  capacity: 10,
}

export const chongqingMockCargo: Cargo = {
  id: chongqingCargoId,
  name: '重庆硬件 GPS 实测货物',
  category: '硬件实测数据',
  origin: '重庆实测起点',
  destination: '重庆实测终点',
  progress: 82,
  status: 'IN_TRANSIT',
  vehicleId: chongqingMockVehicle.id,
  vehiclePlate: chongqingVehiclePlate,
  eta: latestPoint ? `实测至 ${displayTime(latestPoint.time)}` : '实测轨迹已接入',
  originLat: firstPoint?.lat,
  originLng: firstPoint?.lng,
  destinationLat: latestPoint?.lat,
  destinationLng: latestPoint?.lng,
  remainingMinutes: 0,
  distanceRemaining: 0,
  createdAt: firstPoint?.time,
  updatedAt: latestPoint?.time,
}

const timelineSamples = [
  { ratio: 0, title: '硬件开始上报', active: false },
  { ratio: .25, title: 'GPS 轨迹持续采集', active: false },
  { ratio: .5, title: '进入重庆实测路段', active: false },
  { ratio: .75, title: '终端持续在线', active: false },
  { ratio: 1, title: '最新实测点位', active: true },
]

export const chongqingMockTimeline = timelineSamples.reduce<TimelineItem[]>((items, sample) => {
  const index = Math.min(chongqingGpsPoints.length - 1, Math.max(0, Math.round((chongqingGpsPoints.length - 1) * sample.ratio)))
  const point = chongqingGpsPoints[index]
  if (point) {
    items.push({
      time: displayTime(point.time),
      title: sample.title,
      description: `${point.lat.toFixed(5)}, ${point.lng.toFixed(5)} · 精度 ${point.accuracy}m`,
      active: sample.active,
    })
  }
  return items
}, [])

export function getChongqingGpsTrajectory(cargoId?: string) {
  if (cargoId && cargoId !== chongqingCargoId) return []
  return chongqingGpsPoints.map((point) => ({ lat: point.lat, lng: point.lng }))
}

export function getLatestChongqingGpsPoint(cargoId?: string) {
  if (cargoId && cargoId !== chongqingCargoId) return undefined
  return latestPoint
}
