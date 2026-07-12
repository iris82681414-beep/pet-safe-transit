import axios, { AxiosError } from 'axios'
import type { AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'
import { serviceConfig } from './config'
import { tokenManager } from './token'
import type { ApiEnvelope, RefreshTokenResponse } from './types'

export interface RequestOptions<D = unknown> extends AxiosRequestConfig<D> {
  skipAuth?: boolean
  skipTokenRefresh?: boolean
  _retried?: boolean
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly code: number,
    public readonly status?: number,
    public readonly requestId?: string,
    public readonly details?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const baseOptions: AxiosRequestConfig = {
  baseURL: serviceConfig.apiBaseUrl,
  timeout: serviceConfig.timeout,
  headers: { Accept: 'application/json' },
}

const http: AxiosInstance = axios.create(baseOptions)
const refreshHttp: AxiosInstance = axios.create(baseOptions)
let refreshPromise: Promise<string> | null = null

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const options = config as InternalAxiosRequestConfig & RequestOptions
  const token = tokenManager.getAccessToken()
  if (!options.skipAuth && token && token !== 'demo-token') {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['X-Client'] = 'smart-logistics-web'
  return config
})

function parseApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (error instanceof AxiosError) {
    const envelope = error.response?.data as Partial<ApiEnvelope<unknown>> | undefined
    return new ApiError(
      envelope?.message || error.message || '网络请求失败',
      envelope?.code || error.response?.status || -1,
      error.response?.status,
      envelope?.requestId,
      envelope?.data,
    )
  }
  return new ApiError(error instanceof Error ? error.message : '未知请求错误', -1)
}

async function refreshAccessToken(): Promise<string> {
  if (refreshPromise) return refreshPromise

  refreshPromise = (async () => {
    const refreshToken = tokenManager.getRefreshToken()
    if (!refreshToken) throw new ApiError('登录状态已失效，请重新登录', 40101, 401)

    const response = await refreshHttp.post<ApiEnvelope<RefreshTokenResponse>>(
      '/auth/refresh',
      { refreshToken },
      { headers: { 'Content-Type': 'application/json' } },
    )
    const envelope = response.data
    if (envelope.code !== 0) {
      throw new ApiError(envelope.message, envelope.code, 401, envelope.requestId, envelope.data)
    }
    tokenManager.updateTokens(envelope.data)
    return envelope.data.accessToken
  })()

  try {
    return await refreshPromise
  } catch (error) {
    tokenManager.clear()
    window.dispatchEvent(new CustomEvent('smart-logistics:auth-expired'))
    throw parseApiError(error)
  } finally {
    refreshPromise = null
  }
}

async function execute<T, D = unknown>(options: RequestOptions<D>): Promise<T> {
  const requestOptions = { ...options }

  if (!requestOptions.skipAuth && !requestOptions.skipTokenRefresh && tokenManager.isExpiring(serviceConfig.tokenRefreshLeewaySeconds)) {
    await refreshAccessToken()
  }

  try {
    const response = await http.request<ApiEnvelope<T>, import('axios').AxiosResponse<ApiEnvelope<T>>, D>(requestOptions)
    const envelope = response.data
    if (envelope.code === 0) return envelope.data

    if (envelope.code === 40101 && !requestOptions._retried && !requestOptions.skipTokenRefresh) {
      await refreshAccessToken()
      return execute<T, D>({ ...requestOptions, _retried: true })
    }
    throw new ApiError(envelope.message, envelope.code, response.status, envelope.requestId, envelope.data)
  } catch (error) {
    const parsed = parseApiError(error)
    if (parsed.status === 401 && !requestOptions._retried && !requestOptions.skipTokenRefresh) {
      await refreshAccessToken()
      return execute<T, D>({ ...requestOptions, _retried: true })
    }
    throw parsed
  }
}

export const apiClient = {
  request: execute,
  get: <T>(url: string, options: RequestOptions = {}) => execute<T>({ ...options, method: 'GET', url }),
  post: <T, D = unknown>(url: string, data?: D, options: RequestOptions<D> = {}) => execute<T, D>({ ...options, method: 'POST', url, data }),
  put: <T, D = unknown>(url: string, data?: D, options: RequestOptions<D> = {}) => execute<T, D>({ ...options, method: 'PUT', url, data }),
  delete: <T>(url: string, options: RequestOptions = {}) => execute<T>({ ...options, method: 'DELETE', url }),
  upload: <T>(url: string, formData: FormData, options: RequestOptions<FormData> = {}) => execute<T, FormData>({
    ...options,
    method: 'POST',
    url,
    data: formData,
    headers: { ...options.headers, 'Content-Type': 'multipart/form-data' },
  }),
  refreshAccessToken,
}
