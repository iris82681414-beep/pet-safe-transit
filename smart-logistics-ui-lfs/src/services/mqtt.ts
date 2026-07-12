import { connect, type IClientOptions, type MqttClient } from 'mqtt'
import { isDemoMode, serviceConfig } from './config'

export interface GpsMessage {
  plate?: string
  imei: string
  lat: number
  lng: number
  speed: number
  heading: number
  timestamp: string
}

export interface HeartbeatMessage {
  imei: string
  battery?: number
  signal?: number
  timestamp: string
}

export interface CargoEventMessage {
  imei: string
  type: string
  timestamp: string
  data?: Record<string, unknown>
}

export type MqttPayload = GpsMessage | HeartbeatMessage | CargoEventMessage | Record<string, unknown>
export type MqttMessageHandler = (topic: string, payload: MqttPayload) => void

export const toVinTopic = (plate: string) => plate.replace('·', '-')

class MqttService {
  private client: MqttClient | null = null
  private readonly handlers = new Map<string, Set<MqttMessageHandler>>()

  connect(options: IClientOptions = {}) {
    if (isDemoMode() || !serviceConfig.mqttUrl || this.client?.connected) return
    this.client = connect(serviceConfig.mqttUrl, {
      reconnectPeriod: 3_000,
      connectTimeout: 10_000,
      clean: true,
      ...options,
    })
    this.client.on('connect', () => {
      this.handlers.forEach((_, topic) => this.client?.subscribe(topic, { qos: 0 }))
    })
    this.client.on('message', (topic, buffer) => {
      try {
        const payload = JSON.parse(buffer.toString()) as MqttPayload
        this.handlers.get(topic)?.forEach((handler) => handler(topic, payload))
        this.handlers.forEach((topicHandlers, pattern) => {
          if (pattern.endsWith('/#') && topic.startsWith(pattern.slice(0, -1))) {
            topicHandlers.forEach((handler) => handler(topic, payload))
          }
        })
      } catch {
        // 设备消息必须是 JSON；格式错误时忽略该条消息。
      }
    })
  }

  disconnect() {
    this.client?.end(true)
    this.client = null
  }

  subscribe(topic: string, handler: MqttMessageHandler): () => void {
    const handlers = this.handlers.get(topic) || new Set<MqttMessageHandler>()
    handlers.add(handler)
    this.handlers.set(topic, handlers)
    if (this.client?.connected) this.client.subscribe(topic, { qos: 0 })
    return () => {
      handlers.delete(handler)
      if (!handlers.size) {
        this.handlers.delete(topic)
        this.client?.unsubscribe(topic)
      }
    }
  }

  subscribeVehicle(plate: string, handler: MqttMessageHandler) {
    return this.subscribe(`vehicle/${toVinTopic(plate)}/#`, handler)
  }
}

// 浏览器端 MQTT 仅用于设备联调观察；正式业务数据优先使用 API + WebSocket。
export const mqttService = new MqttService()
