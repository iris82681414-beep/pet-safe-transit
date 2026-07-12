import { isDemoMode, serviceConfig } from './config'
import { tokenManager } from './token'
import type { AlertSeverity, CommandStatus } from './types'

export type RealtimeChannel = 'vehicle.position' | 'alert.triggered' | 'command.ack'
export type ConnectionState = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'mock' | 'closed'

export interface VehiclePositionEvent {
  plate: string
  lat: number
  lng: number
  speed: number
  heading: number
  timestamp: string
}

export interface AlertTriggeredEvent {
  alertId: string
  alertType: string
  severity: AlertSeverity
  vehiclePlate: string
  title: string
  triggeredAt: string
}

export interface CommandAckEvent {
  commandId: string
  plate: string
  status: CommandStatus
  timestamp: string
}

export interface RealtimeEventMap {
  'vehicle.position': VehiclePositionEvent
  'alert.triggered': AlertTriggeredEvent
  'command.ack': CommandAckEvent
}

interface ServerMessage<K extends RealtimeChannel = RealtimeChannel> {
  channel: K
  eventId: string
  data: RealtimeEventMap[K]
}

type EventHandler<K extends RealtimeChannel> = (data: RealtimeEventMap[K], message: ServerMessage<K>) => void
type StateHandler = (state: ConnectionState) => void

class RealtimeClient {
  private socket: WebSocket | null = null
  private reconnectTimer: number | null = null
  private reconnectAttempts = 0
  private manuallyClosed = false
  private state: ConnectionState = 'idle'
  private readonly handlers = new Map<RealtimeChannel, Set<EventHandler<RealtimeChannel>>>()
  private readonly stateHandlers = new Set<StateHandler>()
  private readonly subscriptions = new Map<RealtimeChannel, Set<string>>()
  private readonly seenEventIds = new Set<string>()

  constructor() {
    window.addEventListener('online', () => {
      if (!this.manuallyClosed && this.state !== 'open' && this.state !== 'mock') this.connect()
    })
    window.addEventListener('smart-logistics:session-changed', () => {
      if (this.socket || this.state === 'reconnecting') {
        this.disconnect(false)
        this.connect()
      }
    })
  }

  get connectionState() {
    return this.state
  }

  connect() {
    if (isDemoMode()) {
      this.manuallyClosed = false
      this.setState('mock')
      return
    }
    if (!serviceConfig.wsUrl || this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) return

    const token = tokenManager.getAccessToken()
    if (!token || token === 'demo-token') {
      this.setState('closed')
      return
    }

    this.manuallyClosed = false
    this.setState(this.reconnectAttempts ? 'reconnecting' : 'connecting')
    const url = new URL(serviceConfig.wsUrl, window.location.href)
    url.searchParams.set('token', token)
    this.socket = new WebSocket(url.toString())

    this.socket.addEventListener('open', () => {
      this.reconnectAttempts = 0
      this.setState('open')
      this.resubscribe()
    })

    this.socket.addEventListener('message', (event) => this.handleMessage(event))
    this.socket.addEventListener('close', () => {
      this.socket = null
      if (this.manuallyClosed) this.setState('closed')
      else this.scheduleReconnect()
    })
    this.socket.addEventListener('error', () => this.socket?.close())
  }

  disconnect(manual = true) {
    this.manuallyClosed = manual
    if (this.reconnectTimer !== null) window.clearTimeout(this.reconnectTimer)
    this.reconnectTimer = null
    this.socket?.close(1000, 'client disconnect')
    this.socket = null
    if (manual) this.setState('closed')
  }

  subscribe<K extends RealtimeChannel>(channel: K, plates: string[] = ['*']) {
    const targets = this.subscriptions.get(channel) || new Set<string>()
    plates.forEach((plate) => targets.add(plate))
    this.subscriptions.set(channel, targets)
    this.sendSubscription('SUBSCRIBE', channel, plates)
    if (this.state === 'idle' || this.state === 'closed') this.connect()
  }

  unsubscribe<K extends RealtimeChannel>(channel: K, plates: string[] = ['*']) {
    const targets = this.subscriptions.get(channel)
    plates.forEach((plate) => targets?.delete(plate))
    if (!targets?.size) this.subscriptions.delete(channel)
    this.sendSubscription('UNSUBSCRIBE', channel, plates)
  }

  on<K extends RealtimeChannel>(channel: K, handler: EventHandler<K>): () => void {
    const handlers = this.handlers.get(channel) || new Set<EventHandler<RealtimeChannel>>()
    handlers.add(handler as EventHandler<RealtimeChannel>)
    this.handlers.set(channel, handlers)
    return () => handlers.delete(handler as EventHandler<RealtimeChannel>)
  }

  onStateChange(handler: StateHandler): () => void {
    this.stateHandlers.add(handler)
    handler(this.state)
    return () => this.stateHandlers.delete(handler)
  }

  emitMock<K extends RealtimeChannel>(channel: K, data: RealtimeEventMap[K]) {
    if (!isDemoMode()) return
    const message = { channel, eventId: `MOCK-${Date.now()}`, data } as ServerMessage<K>
    this.dispatch(message)
  }

  private sendSubscription(action: 'SUBSCRIBE' | 'UNSUBSCRIBE', channel: RealtimeChannel, plates: string[]) {
    if (this.socket?.readyState !== WebSocket.OPEN) return
    this.socket.send(JSON.stringify({ action, channel, plates }))
  }

  private resubscribe() {
    this.subscriptions.forEach((plates, channel) => this.sendSubscription('SUBSCRIBE', channel, [...plates]))
  }

  private handleMessage(event: MessageEvent) {
    try {
      const message = JSON.parse(String(event.data)) as ServerMessage
      if (!message.channel || !message.eventId || !message.data || !this.handlers.has(message.channel)) return
      if (this.seenEventIds.has(message.eventId)) return
      this.seenEventIds.add(message.eventId)
      if (this.seenEventIds.size > serviceConfig.websocket.maxSeenEventIds) {
        const oldest = this.seenEventIds.values().next().value
        if (oldest) this.seenEventIds.delete(oldest)
      }
      this.dispatch(message)
    } catch {
      // 忽略非 JSON 或不符合协议的消息，保持连接可用。
    }
  }

  private dispatch<K extends RealtimeChannel>(message: ServerMessage<K>) {
    const handlers = this.handlers.get(message.channel)
    handlers?.forEach((handler) => handler(message.data, message as ServerMessage<RealtimeChannel>))
  }

  private scheduleReconnect() {
    if (this.manuallyClosed || !navigator.onLine) {
      this.setState('closed')
      return
    }
    this.setState('reconnecting')
    const base = Math.min(
      serviceConfig.websocket.reconnectMaxDelay,
      serviceConfig.websocket.reconnectBaseDelay * 2 ** this.reconnectAttempts,
    )
    const delay = base + Math.round(Math.random() * 300)
    this.reconnectAttempts += 1
    this.reconnectTimer = window.setTimeout(() => this.connect(), delay)
  }

  private setState(state: ConnectionState) {
    this.state = state
    this.stateHandlers.forEach((handler) => handler(state))
  }
}

export const realtimeClient = new RealtimeClient()
