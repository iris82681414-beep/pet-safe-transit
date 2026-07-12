export type VehicleStatus = 'IN_TRANSIT' | 'IDLE' | 'OFFLINE'
export type AlertStatus = 'PENDING' | 'ACKNOWLEDGED' | 'RESOLVED'
export type AlertSeverity = 'CRITICAL' | 'WARNING' | 'INFO'
export type UserRole = 'SHIPPER' | 'WAREHOUSE' | 'DISPATCHER' | 'DRIVER' | 'ADMIN'
export type CommandStatus = 'SENT' | 'RECEIVED' | 'EXECUTED' | 'REJECTED' | 'FAILED'

export interface Vehicle {
  id?: number
  plate: string
  image?: string
  driver: string
  phone: string
  status: VehicleStatus
  speed: number
  lat: number
  lng: number
  cargoId?: string
  location: string
  heartbeat: string
  heading?: number
  deviceImei?: string
  vehicleType?: string
  capacity?: number
}

export interface Cargo {
  id: string
  name: string
  category: string
  origin: string
  destination: string
  progress: number
  status: 'CREATED' | 'LOADED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED'
  vehicleId?: number
  vehiclePlate?: string
  eta: string
  distanceRemaining?: number
  remainingMinutes?: number
  received?: boolean
  weight?: number
  originLat?: number
  originLng?: number
  destinationLat?: number
  destinationLng?: number
  createdAt?: string
  updatedAt?: string
  loadedAt?: string
  deliveredAt?: string
}

export interface AlertItem {
  id: string
  title: string
  type: string
  severity: AlertSeverity
  status: AlertStatus
  plate: string
  location: string
  createdAt: string
  description: string
  logs?: Array<{ time: string; operator: string; action: string }>
}

export interface TimelineItem {
  time: string
  title: string
  description: string
  active?: boolean
}

export interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  time: string
  sources?: Array<{ documentId: string; title: string; chunkId: string; score: number }>
}

export interface CommandRecord {
  id: string
  plate: string
  type: string
  content: string
  status: CommandStatus
  createdAt: string
  executedAt?: string
}

export interface Device {
  imei: string
  plate: string
  status: 'ONLINE' | 'OFFLINE'
  lastHeartbeat: string
  battery?: number
  signal?: number
}

export interface NotificationItem {
  id: string
  title: string
  content: string
  type: 'alert' | 'command' | 'system'
  time: string
  read: boolean
  targetPage?: string
  targetId?: string
}
