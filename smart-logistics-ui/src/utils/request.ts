import { apiClient } from '@/services/http'
import type { RequestOptions } from '@/services/http'

type RequestFn = {
  <T = unknown, D = unknown>(options: RequestOptions<D>): Promise<T>
  post<T = unknown, D = unknown>(url: string, data?: D, options?: RequestOptions<D>): Promise<T>
  upload<T = unknown>(url: string, formData: FormData, options?: RequestOptions<FormData>): Promise<T>
}

export const request: RequestFn = Object.assign(
  <T = unknown, D = unknown>(options: RequestOptions<D>) => apiClient.request<T, D>(options),
  {
    post: <T = unknown, D = unknown>(url: string, data?: D, options: RequestOptions<D> = {}) => apiClient.post<T, D>(url, data, options),
    upload: <T = unknown>(url: string, formData: FormData, options: RequestOptions<FormData> = {}) => apiClient.upload<T>(url, formData, options),
  },
)
