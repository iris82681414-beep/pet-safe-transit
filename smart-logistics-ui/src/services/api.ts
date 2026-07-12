import { apiClient } from './http'
import { serviceConfig } from './config'
import { tokenManager } from './token'
import type {
  ActiveVehicleTask,
  AddressChangeApproveRequest,
  AddressChangeCreateRequest,
  AddressChangeHistory,
  AddressChangeImpactRequest,
  AddressChangeImpactResult,
  AddressChangeRecord,
  AddressChangeRejectRequest,
  AlertDto,
  AlertListQuery,
  AlertStats,
  AmapAddress,
  AmapGeocodeRequest,
  AmapInputTip,
  AmapRegeoRequest,
  AssistantChatRequest,
  AssistantChatResponse,
  AssistantSource,
  AssistantMessage,
  CargoDeviceEvent,
  CargoDto,
  CargoEta,
  CargoListQuery,
  CargoPosition,
  CargoStatusLog,
  CargoTimeline,
  CargoTrajectory,
  CommandDto,
  CreateCargoRequest,
  CreateVehicleRequest,
  DelayPrediction,
  DeviationCheckRequest,
  DeviationCheckResult,
  DeviceDto,
  DeviceHeartbeat,
  DeviceStatusResult,
  DriverConfirmRequest,
  DriverRatingCreateRequest,
  DriverRatingResult,
  DriverRatingSummary,
  ExceptionSummary,
  FileUploadResult,
  FaceLoginRequest,
  FaceRegisterRequest,
  FaceRegisterResult,
  FaceStatusResult,
  HealthStatus,
  KnowledgeDocument,
  KnowledgeIndexResult,
  LoginRequest,
  LoginResponse,
  OrderDriverRatingResult,
  PageQuery,
  PageResult,
  RerouteSuggestionRequest,
  RerouteSuggestionResult,
  RiskScore,
  RoutePlanRequest,
  RoutePlanResult,
  SendCommandRequest,
  StatusVerifyRequest,
  StatusVerifyResult,
  TrajectoryCorrectRequest,
  TrajectoryCorrectResult,
  TrajectoryQuery,
  UnloadAddressAbnormalRequest,
  UnloadAddressConfirmRequest,
  UnloadAddressSuggestionsResult,
  UpdateCargoStatusRequest,
  UpdateVehicleRequest,
  UserUpdateRequest,
  UserProfile,
  VehicleDto,
  VehicleListQuery,
} from './types'

const encode = encodeURIComponent

export interface AssistantStreamMeta {
  sessionId?: string
  sources?: AssistantSource[]
  context?: Record<string, unknown>
  answeredAt?: string
}

export interface AssistantChatStreamOptions {
  onToken?: (token: string) => void
  onMeta?: (meta: AssistantStreamMeta) => void
  signal?: AbortSignal
}

function apiUrl(path: string) {
  const base = serviceConfig.apiBaseUrl.replace(/\/$/, '')
  return `${base}${path}`
}

function parseSseLineValue(line: string, prefix: string) {
  let value = line.slice(prefix.length)
  if (value.startsWith(' ')) value = value.slice(1)
  return value
}

function dispatchSseBlock(block: string, handlers: AssistantChatStreamOptions) {
  let event = 'message'
  const dataLines: string[] = []

  block.split(/\r?\n/).forEach((line) => {
    if (line.startsWith('event:')) event = parseSseLineValue(line, 'event:')
    if (line.startsWith('data:')) dataLines.push(parseSseLineValue(line, 'data:'))
  })

  if (!dataLines.length) return
  const data = dataLines.join('\n')

  if (event === 'token') {
    handlers.onToken?.(parseSseText(data, 'text'))
    return
  }

  if (event === 'meta') {
    handlers.onMeta?.(JSON.parse(data) as AssistantStreamMeta)
    return
  }

  if (event === 'error') {
    throw new Error(parseSseText(data, 'message'))
  }
}

function parseSseText(data: string, key: 'text' | 'message') {
  try {
    const payload = JSON.parse(data) as Record<string, unknown>
    const value = payload[key]
    return typeof value === 'string' ? value : data
  } catch {
    return data
  }
}

async function chatStream(payload: AssistantChatRequest, handlers: AssistantChatStreamOptions = {}) {
  if (!handlers.signal && tokenManager.isExpiring(serviceConfig.tokenRefreshLeewaySeconds)) {
    await apiClient.refreshAccessToken()
  }

  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
    'Content-Type': 'application/json',
    'X-Client': 'smart-logistics-web',
  }
  const token = tokenManager.getAccessToken()
  if (token && token !== 'demo-token') headers.Authorization = `Bearer ${token}`

  const response = await fetch(apiUrl('/assistant/chat/stream'), {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
    signal: handlers.signal,
  })

  if (!response.ok) {
    throw new Error(`智能问答流式请求失败：HTTP ${response.status}`)
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式读取响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() || ''
    blocks.forEach((block) => dispatchSseBlock(block, handlers))
  }

  buffer += decoder.decode()
  if (buffer.trim()) dispatchSseBlock(buffer, handlers)
}

async function synthesizeAssistantSpeech(text: string) {
  const headers: Record<string, string> = {
    Accept: 'audio/mpeg',
    'Content-Type': 'application/json',
    'X-Client': 'smart-logistics-web',
  }
  const token = tokenManager.getAccessToken()
  if (token && token !== 'demo-token') headers.Authorization = `Bearer ${token}`
  const response = await fetch(apiUrl('/assistant/speech'), {
    method: 'POST',
    headers,
    body: JSON.stringify({ text }),
  })
  if (!response.ok) throw new Error(`桌宠语音生成失败：HTTP ${response.status}`)
  return response.blob()
}

async function streamAssistantSpeech(text: string) {
  const headers: Record<string, string> = {
    Accept: 'audio/mpeg',
    'Content-Type': 'application/json',
    'X-Client': 'smart-logistics-web',
  }
  const token = tokenManager.getAccessToken()
  if (token && token !== 'demo-token') headers.Authorization = `Bearer ${token}`
  const response = await fetch(apiUrl('/assistant/speech/stream'), {
    method: 'POST',
    headers,
    body: JSON.stringify({ text }),
  })
  if (!response.ok) throw new Error(`豆包流式语音生成失败：HTTP ${response.status}`)
  if (!response.body) throw new Error('当前浏览器不支持流式音频读取')
  return response
}

export const authApi = {
  async login(payload: LoginRequest): Promise<LoginResponse> {
    const result = await apiClient.post<LoginResponse, LoginRequest>('/auth/login', payload, { skipAuth: true, skipTokenRefresh: true })
    tokenManager.save(result, result.user)
    return result
  },
  async faceLogin(payload: FaceLoginRequest): Promise<LoginResponse> {
    const result = await apiClient.post<LoginResponse, FaceLoginRequest>('/auth/face-login', payload, { skipAuth: true, skipTokenRefresh: true })
    tokenManager.save(result, result.user)
    return result
  },
  me: () => apiClient.get<UserProfile>('/users/me'),
  async logout(): Promise<void> {
    try {
      await apiClient.post<void>('/auth/logout')
    } finally {
      tokenManager.clear()
    }
  },
  refresh: () => apiClient.refreshAccessToken(),
}

export const userApi = {
  update: (id: string, payload: UserUpdateRequest) => apiClient.put<UserProfile, UserUpdateRequest>(`/users/${encode(id)}`, payload),
  faceStatus: (id: string) => apiClient.get<FaceStatusResult>(`/users/${encode(id)}/face/status`),
  registerFace: (id: string, payload: FaceRegisterRequest) => apiClient.post<FaceRegisterResult, FaceRegisterRequest>(`/users/${encode(id)}/face/register`, payload),
  updateFace: (id: string, payload: FaceRegisterRequest) => apiClient.put<FaceRegisterResult, FaceRegisterRequest>(`/users/${encode(id)}/face`, payload),
  deleteFace: (id: string) => apiClient.delete<{ userId: string; deleted: boolean }>(`/users/${encode(id)}/face`),
}

export const vehicleApi = {
  list: (params: VehicleListQuery = {}) => apiClient.get<PageResult<VehicleDto>>('/vehicles', { params }),
  detail: (plate: string) => apiClient.get<VehicleDto>(`/vehicles/plate/${encode(plate)}`),
  detailById: (id: number) => apiClient.get<VehicleDto>(`/vehicles/${id}`),
  create: (payload: CreateVehicleRequest) => apiClient.post<VehicleDto, CreateVehicleRequest>('/vehicles', payload),
  update: (id: number, payload: UpdateVehicleRequest) => apiClient.put<VehicleDto, UpdateVehicleRequest>(`/vehicles/${id}`, payload),
  remove: (id: number) => apiClient.delete<void>(`/vehicles/${id}`),
  activeTask: (plate: string) => apiClient.get<ActiveVehicleTask>(`/vehicles/${encode(plate)}/active-task`),
  sendCommand: (plate: string, payload: SendCommandRequest) => apiClient.post<CommandDto, SendCommandRequest>(`/vehicles/${encode(plate)}/command`, payload),
  commandStatus: (plate: string, commandId: string) => apiClient.get<CommandDto>(`/vehicles/${encode(plate)}/command/${encode(commandId)}`),
  commands: (plate: string, params: PageQuery = {}) => apiClient.get<PageResult<CommandDto>>(`/vehicles/${encode(plate)}/commands`, { params }),
}

export const cargoApi = {
  list: (params: CargoListQuery = {}) => apiClient.get<PageResult<CargoDto>>('/cargo', { params }),
  create: (payload: CreateCargoRequest) => apiClient.post<CargoDto, CreateCargoRequest>('/cargo', payload),
  detail: (cargoId: string) => apiClient.get<CargoDto>(`/cargo/${encode(cargoId)}`),
  bind: (cargoId: string, vehicleId: number) => apiClient.post<CargoDto, { cargoId: string; vehicleId: number }>('/cargo/bind', { cargoId, vehicleId }),
  unbind: (cargoId: string) => apiClient.post<CargoDto, { cargoId: string }>('/cargo/unbind', { cargoId }),
  updateStatus: (cargoId: string, payload: UpdateCargoStatusRequest) => apiClient.put<CargoDto, UpdateCargoStatusRequest>(`/cargo/${encode(cargoId)}/status`, payload),
  statusLogs: (cargoId: string) => apiClient.get<CargoStatusLog[]>(`/cargo/${encode(cargoId)}/status-logs`),
  position: (cargoId: string) => apiClient.get<CargoPosition>(`/cargo/${encode(cargoId)}/position`),
  trajectory: (cargoId: string, params: TrajectoryQuery = {}) => apiClient.get<CargoTrajectory>(`/cargo/${encode(cargoId)}/trajectory`, { params }),
  eta: (cargoId: string) => apiClient.get<CargoEta>(`/cargo/${encode(cargoId)}/eta`),
  timeline: (cargoId: string) => apiClient.get<CargoTimeline>(`/cargo/${encode(cargoId)}/timeline`),
}

export const amapApi = {
  inputTips: (keywords: string, city?: string) => apiClient.get<AmapInputTip[]>('/amap/input-tips', { params: { keywords, city } }),
  geocode: (payload: AmapGeocodeRequest) => apiClient.post<AmapAddress, AmapGeocodeRequest>('/amap/geocode', payload),
  regeo: (payload: AmapRegeoRequest) => apiClient.post<AmapAddress, AmapRegeoRequest>('/amap/regeo', payload),
}

export const routeApi = {
  plan: (payload: RoutePlanRequest) => apiClient.post<RoutePlanResult, RoutePlanRequest>('/routes/plan', payload),
  truckPlan: (payload: RoutePlanRequest) => apiClient.post<RoutePlanResult, RoutePlanRequest>('/routes/truck-plan', payload),
  replan: (payload: RoutePlanRequest) => apiClient.post<RoutePlanResult, RoutePlanRequest>('/routes/replan', payload),
  checkDeviation: (payload: DeviationCheckRequest) => apiClient.post<DeviationCheckResult, DeviationCheckRequest>('/routes/deviation/check', payload),
  rerouteSuggestion: (payload: RerouteSuggestionRequest) => apiClient.post<RerouteSuggestionResult, RerouteSuggestionRequest>('/routes/reroute-suggestion', payload),
  correctTrajectory: (payload: TrajectoryCorrectRequest) => apiClient.post<TrajectoryCorrectResult, TrajectoryCorrectRequest>('/trajectory/correct', payload),
}

export const orderExtensionApi = {
  addressChangeImpact: (orderId: string, payload: AddressChangeImpactRequest) =>
    apiClient.post<AddressChangeImpactResult, AddressChangeImpactRequest>(`/orders/${encode(orderId)}/address-change-impact`, payload),
  createAddressChange: (orderId: string, payload: AddressChangeCreateRequest) =>
    apiClient.post<AddressChangeRecord, AddressChangeCreateRequest>(`/orders/${encode(orderId)}/address-change-requests`, payload),
  addressChangeHistory: (orderId: string) =>
    apiClient.get<AddressChangeHistory>(`/orders/${encode(orderId)}/address-change-history`),
  delayPrediction: (orderId: string) =>
    apiClient.get<DelayPrediction>(`/orders/${encode(orderId)}/delay-prediction`),
  riskScore: (orderId: string) =>
    apiClient.get<RiskScore>(`/orders/${encode(orderId)}/risk-score`),
  verifyStatus: (orderId: string, payload: StatusVerifyRequest) =>
    apiClient.post<StatusVerifyResult, StatusVerifyRequest>(`/orders/${encode(orderId)}/status/verify`, payload),
  exceptionSummary: (orderId: string) =>
    apiClient.get<ExceptionSummary>(`/orders/${encode(orderId)}/exception-summary`),
  unloadAddressSuggestions: (orderId: string) =>
    apiClient.get<UnloadAddressSuggestionsResult>(`/orders/${encode(orderId)}/unload-address-suggestions`),
  confirmUnloadAddress: (orderId: string, payload: UnloadAddressConfirmRequest) =>
    apiClient.post<AddressChangeRecord, UnloadAddressConfirmRequest>(`/orders/${encode(orderId)}/unload-address/confirm`, payload),
  reportUnloadAddressAbnormal: (orderId: string, payload: UnloadAddressAbnormalRequest) =>
    apiClient.post<AddressChangeRecord, UnloadAddressAbnormalRequest>(`/orders/${encode(orderId)}/unload-address/abnormal`, payload),
  submitDriverRating: (orderId: string, payload: DriverRatingCreateRequest) =>
    apiClient.post<DriverRatingResult, DriverRatingCreateRequest>(`/orders/${encode(orderId)}/driver-rating`, payload),
  driverRating: (orderId: string) =>
    apiClient.get<OrderDriverRatingResult>(`/orders/${encode(orderId)}/driver-rating`),
}

export const addressChangeApi = {
  detail: (requestId: string) => apiClient.get<AddressChangeRecord>(`/address-change-requests/${encode(requestId)}`),
  approve: (requestId: string, payload: AddressChangeApproveRequest = {}) =>
    apiClient.post<AddressChangeRecord, AddressChangeApproveRequest>(`/address-change-requests/${encode(requestId)}/approve`, payload),
  reject: (requestId: string, payload: AddressChangeRejectRequest) =>
    apiClient.post<AddressChangeRecord, AddressChangeRejectRequest>(`/address-change-requests/${encode(requestId)}/reject`, payload),
  driverConfirm: (requestId: string, payload: DriverConfirmRequest) =>
    apiClient.post<AddressChangeRecord, DriverConfirmRequest>(`/address-change-requests/${encode(requestId)}/driver-confirm`, payload),
}

export const driverApi = {
  ratingSummary: (driverId: string) => apiClient.get<DriverRatingSummary>(`/drivers/${encode(driverId)}/rating-summary`),
  ratings: (driverId: string, params: PageQuery = {}) => apiClient.get<PageResult<DriverRatingResult>>(`/drivers/${encode(driverId)}/ratings`, { params }),
}

export const alertApi = {
  list: (params: AlertListQuery = {}) => apiClient.get<PageResult<AlertDto>>('/alerts', { params }),
  detail: (alertId: string) => apiClient.get<AlertDto>(`/alerts/${encode(alertId)}`),
  acknowledge: (alertId: string, remark: string) => apiClient.post<AlertDto, { remark: string }>(`/alerts/${encode(alertId)}/acknowledge`, { remark }),
  resolve: (alertId: string, resolution: string, remark: string) => apiClient.post<AlertDto, { resolution: string; remark: string }>(`/alerts/${encode(alertId)}/resolve`, { resolution, remark }),
  stats: (params: { startTime?: string; endTime?: string } = {}) => apiClient.get<AlertStats>('/alerts/stats', { params }),
}

export const deviceApi = {
  status: (params: PageQuery & { status?: 'ONLINE' | 'OFFLINE'; keyword?: string } = {}) => apiClient.get<DeviceStatusResult>('/devices/status', { params }),
  detail: (imei: string) => apiClient.get<DeviceDto>(`/devices/${encode(imei)}`),
  heartbeats: (imei: string, params: { startTime?: string; endTime?: string } = {}) => apiClient.get<DeviceHeartbeat[]>(`/devices/${encode(imei)}/heartbeats`, { params }),
  cargoEvents: (imei: string, params: PageQuery = {}) => apiClient.get<PageResult<CargoDeviceEvent>>(`/devices/${encode(imei)}/cargo-events`, { params }),
}

export const assistantApi = {
  chat: (payload: AssistantChatRequest) => apiClient.post<AssistantChatResponse, AssistantChatRequest>('/assistant/chat', payload),
  chatStream,
  suggestions: (cargoId?: string) => apiClient.get<string[]>('/assistant/suggestions', { params: cargoId ? { cargoId } : undefined }),
  messages: (sessionId: string) => apiClient.get<AssistantMessage[]>(`/assistant/sessions/${encode(sessionId)}/messages`),
  speech: synthesizeAssistantSpeech,
  speechStream: streamAssistantSpeech,
}

export interface AgentCommandResponse {
  logId?: string
  recognizedText?: string
  intent?: string
  reply?: string
  needConfirm?: boolean
  agentMode?: 'LLM_FUNCTION_CALLING' | 'LLM_CHAT' | string
  action?: Record<string, any>
}

export const agentApi = {
  command: (text: string, sourcePage = 'assistant') => apiClient.post<AgentCommandResponse, { text: string; sourcePage: string }>('/agent/command', { text, sourcePage }),
  recognize(audio: Blob) {
    const formData = new FormData()
    formData.append('audio', audio, 'assistant-voice.wav')
    return apiClient.upload<{ text: string; source?: string; message?: string }>('/voice/recognize', formData)
  },
}

export const knowledgeApi = {
  upload(file: File, title: string, category: string) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('title', title)
    formData.append('category', category)
    return apiClient.upload<KnowledgeDocument>('/knowledge/documents', formData)
  },
  index: (documentId: string) => apiClient.post<KnowledgeIndexResult>(`/knowledge/documents/${encode(documentId)}/index`),
  list: (params: PageQuery = {}) => apiClient.get<PageResult<KnowledgeDocument>>('/knowledge/documents', { params }),
  remove: (documentId: string) => apiClient.delete<void>(`/knowledge/documents/${encode(documentId)}`),
}

export const fileApi = {
  uploadImage(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient.upload<FileUploadResult>('/files/upload', formData)
  },
}

export const systemApi = {
  health: () => apiClient.get<HealthStatus>('/health', { skipAuth: true }),
  components: () => apiClient.get<HealthStatus>('/health/components'),
}
