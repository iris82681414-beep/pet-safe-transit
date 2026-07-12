import AMapLoader from '@amap/amap-jsapi-loader'
import { serviceConfig } from './config'

let loadPromise: Promise<any> | null = null

/** 在整个应用内复用同一个高德 JS API 请求，避免多个业务页重复插入脚本。 */
export function preloadAMap(): Promise<any> {
  if (!serviceConfig.amapKey) return Promise.reject(new Error('未读取到 VITE_AMAP_KEY'))

  if (typeof window !== 'undefined' && serviceConfig.amapSecurityCode) {
    ;(window as any)._AMapSecurityConfig = { securityJsCode: serviceConfig.amapSecurityCode }
  }

  if (!loadPromise) {
    loadPromise = AMapLoader.load({
      key: serviceConfig.amapKey,
      version: '2.0',
      plugins: ['AMap.Scale', 'AMap.ToolBar'],
    }).catch((error) => {
      loadPromise = null
      throw error
    })
  }

  return loadPromise
}
