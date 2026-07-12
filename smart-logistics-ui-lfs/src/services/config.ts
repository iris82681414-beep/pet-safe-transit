export type DataMode = 'mock' | 'api' | 'auto'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
const wsUrl = import.meta.env.VITE_WS_URL || ''
const mqttUrl = import.meta.env.VITE_MQTT_URL || ''
const amapKey = import.meta.env.VITE_AMAP_KEY || ''
const amapSecurityCode = import.meta.env.VITE_AMAP_SECURITY_CODE || ''
const dataMode = (import.meta.env.VITE_DATA_MODE || 'auto') as DataMode

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
