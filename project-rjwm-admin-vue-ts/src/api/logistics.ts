import request from '@/utils/request'

const API_PREFIX = '/v1'

export const authLogin = (data: any) =>
  request({
    url: `${API_PREFIX}/auth/login`,
    method: 'post',
    data
  })

export const authLogout = () =>
  request({
    url: `${API_PREFIX}/auth/logout`,
    method: 'post'
  })

export const getCurrentUser = () =>
  request({
    url: `${API_PREFIX}/users/me`,
    method: 'get'
  })

export const getVehicles = (params?: any) =>
  request({
    url: `${API_PREFIX}/vehicles`,
    method: 'get',
    params
  })

export const getVehicleDetail = (plate: string) =>
  request({
    url: `${API_PREFIX}/vehicles/${encodeURIComponent(plate)}`,
    method: 'get'
  })

export const createVehicle = (data: any) =>
  request({
    url: `${API_PREFIX}/vehicles`,
    method: 'post',
    data
  })

export const getCargoList = (params?: any) =>
  request({
    url: `${API_PREFIX}/cargo`,
    method: 'get',
    params
  })

export const getCargoDetail = (cargoId: string) =>
  request({
    url: `${API_PREFIX}/cargo/${encodeURIComponent(cargoId)}`,
    method: 'get'
  })

export const bindCargoVehicle = (data: any) =>
  request({
    url: `${API_PREFIX}/cargo/bind`,
    method: 'post',
    data
  })

export const getCargoPosition = (cargoId: string) =>
  request({
    url: `${API_PREFIX}/cargo/${encodeURIComponent(cargoId)}/position`,
    method: 'get'
  })

export const getCargoTrajectory = (cargoId: string, params?: any) =>
  request({
    url: `${API_PREFIX}/cargo/${encodeURIComponent(cargoId)}/trajectory`,
    method: 'get',
    params
  })

export const getCargoEta = (cargoId: string) =>
  request({
    url: `${API_PREFIX}/cargo/${encodeURIComponent(cargoId)}/eta`,
    method: 'get'
  })

export const getCargoTimeline = (cargoId: string) =>
  request({
    url: `${API_PREFIX}/cargo/${encodeURIComponent(cargoId)}/timeline`,
    method: 'get'
  })

export const getAlerts = (params?: any) =>
  request({
    url: `${API_PREFIX}/alerts`,
    method: 'get',
    params
  })

export const getAlertStats = (params?: any) =>
  request({
    url: `${API_PREFIX}/alerts/stats`,
    method: 'get',
    params
  })

export const acknowledgeAlert = (alertId: string, data: any) =>
  request({
    url: `${API_PREFIX}/alerts/${encodeURIComponent(alertId)}/acknowledge`,
    method: 'post',
    data
  })

export const resolveAlert = (alertId: string, data: any) =>
  request({
    url: `${API_PREFIX}/alerts/${encodeURIComponent(alertId)}/resolve`,
    method: 'post',
    data
  })

export const getDeviceStatus = (params?: any) =>
  request({
    url: `${API_PREFIX}/devices/status`,
    method: 'get',
    params
  })

export const getAssistantSuggestions = (params?: any) =>
  request({
    url: `${API_PREFIX}/assistant/suggestions`,
    method: 'get',
    params
  })

export const chatWithAssistant = (data: any) =>
  request({
    url: `${API_PREFIX}/assistant/chat`,
    method: 'post',
    data
  })
