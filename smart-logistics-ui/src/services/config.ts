export type DataMode = 'mock' | 'api' | 'auto'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
const wsUrl = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/api/v1/ws'
const mqttUrl = import.meta.env.VITE_MQTT_URL || 'ws://localhost:8083/mqtt'
const amapKey = import.meta.env.VITE_AMAP_KEY || '8958124e98848d6bbfffc631bb4b58f8'
const amapSecurityCode = import.meta.env.VITE_AMAP_SECURITY_CODE || '418b7d352a45f90c3870a1d36531d23e'
// The pet-safe demo is self-contained by default. Set VITE_DATA_MODE=api explicitly
// when a matching backend is available.
const dataMode = (import.meta.env.VITE_DATA_MODE || 'mock') as DataMode

export const serviceConfig = {
  apiBaseUrl,
  wsUrl,
  mqttUrl,
  amapKey,
  amapSecurityCode,
  dataMode,
  timeout: 12_000,
  tokenRefreshLeewaySeconds: 30,
  websocket: {
    reconnectBaseDelay: 1_000,
    reconnectMaxDelay: 30_000,
    maxSeenEventIds: 500,
  },
} as const

export const isDemoMode = () => {
  if (serviceConfig.dataMode === 'mock') return true
  if (serviceConfig.dataMode === 'api') return false
  return !import.meta.env.VITE_API_BASE_URL
}
