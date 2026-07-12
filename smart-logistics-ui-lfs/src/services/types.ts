export type UserRole = 'SHIPPER' | 'WAREHOUSE' | 'DISPATCHER' | 'DRIVER' | 'ADMIN'
export type VehicleApiStatus = 'MOVING' | 'STOPPED' | 'OFFLINE'
export type DeviceStatus = 'ONLINE' | 'OFFLINE'
export type CargoStatus = 'CREATED' | 'LOADED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED'
export type AlertSeverity = 'CRITICAL' | 'WARNING' | 'INFO'
export type AlertStatus = 'PENDING' | 'ACKNOWLEDGED' | 'RESOLVED'
export type CommandStatus = 'SENT' | 'RECEIVED' | 'EXECUTED' | 'REJECTED' | 'FAILED'

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  timestamp: string
  requestId: string
}

export interface PageResult<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface PageQuery {
  page?: number
  size?: number
}

export interface GeoPoint {
  lat: number
  lng: number
}

export type RoutePlanMode = 'DRIVING' | 'TRUCK' | string

export interface AmapInputTip {
  name: string
  district?: string
  address?: string
  adcode?: string
  lng?: number
  lat?: number
}

export interface AmapAddress {
  formattedAddress: string
  province?: string
  city?: string
  district?: string
  road?: string
  poiName?: string
  level?: string
  adcode?: string
  lng?: number
  lat?: number
  source?: string
}

export interface AmapGeocodeRequest {
  address: string
  city?: string
}

export interface AmapRegeoRequest extends GeoPoint {}

export interface TruckInfo {
  plate?: string
  size?: string
  heightMeters?: number
  widthMeters?: number
  loadWeightTons?: number
  totalWeightTons?: number
  axis?: number
}

export interface RouteStep {
  instruction: string
  roadName?: string
  distanceMeters: number
  durationSeconds: number
  distanceKm: number
  durationMinutes: number
}

export interface RoutePlanRequest {
  mode?: RoutePlanMode
  origin: GeoPoint
  destination: GeoPoint
  waypoints?: GeoPoint[]
  strategy?: string
  plate?: string
  truck?: TruckInfo
  vehicle?: TruckInfo
}

export interface RoutePlanResult {
  routeId: string
  routeType: string
  distanceMeters: number
  durationSeconds: number
  distanceKm: number
  durationMinutes: number
  tolls?: number
  tollCost?: number
  trafficLights?: number
  restriction?: string
  restrictionWarnings?: string[]
  polyline: GeoPoint[]
  steps: RouteStep[]
  source?: string
}

export interface GpsCorrectPoint extends GeoPoint {
  time?: string
  speed?: number
  heading?: number
  accuracy?: number
}

export interface TrajectoryCorrectRequest {
  cargoId?: string
  vehiclePlate?: string
  points: GpsCorrectPoint[]
}

export interface TrajectoryCorrectResult {
  cargoId?: string
  vehiclePlate?: string
  rawPointCount: number
  correctedPointCount: number
  correctedPoints: GeoPoint[]
  source: string
  correctedAt: string
}

export interface DeviationCheckRequest {
  cargoId?: string
  vehiclePlate?: string
  currentLocation: GeoPoint
  routePolyline: GeoPoint[]
  thresholdKm?: number
}

export interface DeviationCheckResult {
  cargoId?: string
  vehiclePlate?: string
  deviated: boolean
  distanceToRouteKm: number
  thresholdKm: number
  level: string
  suggestion: string
  checkedAt: string
}

export interface RerouteSuggestionRequest {
  cargoId?: string
  vehiclePlate?: string
  plate?: string
  currentLocation: GeoPoint
  destination: GeoPoint
  strategy?: string
}

export interface RerouteSuggestionResult {
  cargoId?: string
  vehiclePlate?: string
  route: RoutePlanResult
  commandType: string
  message: string
  createdAt: string
}

export interface AddressPayload extends GeoPoint {
  province?: string
  city?: string
  district?: string
  detail: string
}

export interface AddressChangeImpactRequest {
  newAddress: AddressPayload
}

export interface AddressChangeImpactResult {
  orderId: string
  currentStatus?: string
  canChange: boolean
  impactLevel: string
  oldAddress?: string
  newAddress: string
  extraDistanceKm: number
  estimatedDelayMinutes: number
  extraCost: number
  needDispatcherReview: boolean
  needDriverConfirm: boolean
  affectedResources: string[]
  warnings: string[]
  route?: RoutePlanResult
}

export interface AddressChangeCreateRequest {
  newAddress: AddressPayload
  contactName?: string
  contactPhone?: string
  reason?: string
}

export interface AddressChangeLog {
  action: string
  operatorRole?: string
  remark?: string
  createdAt?: string
}

export interface AddressChangeRecord {
  requestId: string
  orderId: string
  oldAddress?: string
  newAddress: string
  status: string
  impactLevel?: string
  extraDistanceKm?: number
  estimatedDelayMinutes?: number
  extraCost?: number
  needDispatcherReview?: boolean
  needDriverConfirm?: boolean
  createdAt?: string
  logs?: AddressChangeLog[]
  message?: string
}

export interface AddressChangeHistory {
  orderId: string
  requests: AddressChangeRecord[]
}

export interface AddressChangeApproveRequest {
  remark?: string
  notifyDriver?: boolean
  recalculateRoute?: boolean
}

export interface AddressChangeRejectRequest {
  reason?: string
  suggestion?: string
}

export interface DriverConfirmRequest {
  confirmed: boolean
  remark?: string
}

export interface DelayPrediction {
  orderId: string
  plannedArriveAt?: string
  estimatedArriveAt?: string
  delayStatus: string
  delayMinutes: number
  reasons: string[]
}

export interface RiskScoreFactor {
  name: string
  value?: unknown
  impact: number
}

export interface RiskScore {
  orderId: string
  score: number
  level: string
  factors: RiskScoreFactor[]
  suggestion: string
}

export interface StatusVerifyRequest {
  reportedStatus: string
  reportLocation: GeoPoint
}

export interface StatusVerifyResult {
  credible: boolean
  confidence: number
  level: string
  distanceToDestinationKm: number
  reason: string
  suggestion: string
}

export interface ExceptionSummary {
  orderId: string
  hasException: boolean
  level: string
  summary: string
  events: Array<Record<string, unknown>>
}

export interface UnloadAddressSuggestion {
  source: string
  address: string
  lng?: number
  lat?: number
  confidence: number
  reason: string
}

export interface UnloadAddressSuggestionsResult {
  orderId: string
  currentAddress: {
    detail: string
    lng?: number
    lat?: number
  }
  confidence: number
  suggestions: UnloadAddressSuggestion[]
}

export interface UnloadAddressConfirmRequest extends GeoPoint {
  address: string
  remark?: string
}

export interface UnloadAddressAbnormalRequest {
  type: string
  description?: string
  lng?: number
  lat?: number
  photos?: string[]
}

export interface DriverRatingDimensions {
  punctuality?: number
  serviceAttitude?: number
  cargoIntegrity?: number
  communication?: number
}

export interface DriverRatingCreateRequest {
  driverId?: string
  score: number
  dimensions?: DriverRatingDimensions
  tags?: string[]
  comment?: string
}

export interface DriverRatingResult {
  ratingId: string
  orderId: string
  driverId: string
  plate?: string
  score: number
  dimensions?: DriverRatingDimensions
  tags?: string[]
  comment?: string
  createdAt?: string
}

export interface OrderDriverRatingResult {
  rated: boolean
  rating?: DriverRatingResult
}

export interface DriverRatingSummary {
  driverId: string
  averageScore: number
  ratingCount: number
  dimensions?: DriverRatingDimensions
  tags?: Array<{ name: string; count: number }>
}

export interface VehiclePosition extends GeoPoint {
  speed: number
  heading: number
  accuracy?: number
}

export interface UserProfile {
  id: string
  username: string
  name: string
  role: UserRole
  phone: string
  permissions: string[]
}

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse extends AuthTokens {
  user: UserProfile
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface RefreshTokenResponse extends AuthTokens {}

export interface VehicleListQuery extends PageQuery {
  status?: VehicleApiStatus
  deviceStatus?: DeviceStatus
  keyword?: string
}

export interface VehicleDto {
  id: number
  plate: string
  vinTopic: string
  vehicleType: string
  capacity: number
  driverName: string
  driverPhone: string
  deviceImei: string
  deviceStatus: DeviceStatus
  status: VehicleApiStatus
  cargoId?: string
  position?: VehiclePosition
  locationDesc?: string
  registeredAt?: string
  updatedAt: string
}

export interface CreateVehicleRequest {
  plate: string
  vehicleType: string
  capacity: number
  driverName: string
  driverPhone: string
  deviceImei: string
}

export interface UpdateVehicleRequest extends CreateVehicleRequest {
  status?: VehicleApiStatus
  deviceStatus?: DeviceStatus
}

export interface ActiveVehicleCargo {
  cargoId: string
  cargoType: string
  weight: number
  status: CargoStatus
  originName: string
  destinationName: string
  boundAt: string
}

export interface ActiveVehicleTask {
  vehicleId: number
  plate: string
  vinTopic: string
  driverName: string
  driverPhone: string
  status: VehicleApiStatus
  deviceStatus: DeviceStatus
  cargos: ActiveVehicleCargo[]
}

export interface LocationDto extends GeoPoint {
  name?: string
}

export interface CreateCargoRequest {
  cargoId: string
  cargoType: string
  weight: number
  origin: LocationDto
  destination: LocationDto
}

export interface CargoListQuery extends PageQuery {
  status?: CargoStatus
  keyword?: string
}

export interface CargoDto {
  cargoId: string
  cargoType: string
  weight: number
  vehicleId?: number
  vehiclePlate?: string
  driverName?: string
  driverPhone?: string
  status: CargoStatus
  origin: LocationDto
  destination: LocationDto
  loadedAt?: string
  deliveredAt?: string
  createdAt?: string
  updatedAt?: string
  eta?: string
  progress?: number
  distanceTotal?: number
  distanceRemaining?: number
}

export interface UpdateCargoStatusRequest {
  status: CargoStatus
  lat?: number
  lng?: number
  remark?: string
  operatorId?: string
}

export interface CargoStatusLog {
  id: string
  cargoId: string
  status: CargoStatus
  lat?: number
  lng?: number
  remark?: string
  operatorId?: string
  createdAt: string
}

export interface CargoPosition {
  cargoId: string
  vehiclePlate: string
  vehicleId?: number
  driverName?: string
  plate?: string
  lat?: number
  lng?: number
  speed?: number
  heading?: number
  accuracy?: number
  position?: VehiclePosition
  status?: CargoStatus | string
  locationDesc?: string
  updatedAt: string
  source: string
  deviceImei?: string
}

export interface TrajectoryQuery {
  startTime?: string
  endTime?: string
  interval?: number
}

export interface TrajectoryPoint extends VehiclePosition {
  time: string
}

export interface CargoTrajectory {
  cargoId: string
  vehiclePlate: string
  startTime: string
  endTime: string
  points: TrajectoryPoint[]
}

export interface CargoEta {
  cargoId: string
  eta?: string
  remainingMinutes: number
  distanceRemaining: number
  progress: number
  trend: 'ON_TRACK' | 'EARLY' | 'DELAYED' | 'STOPPED' | 'NO_VEHICLE' | 'NO_GPS' | 'ERROR' | string
  calculatedAt: string
}

export interface TimelineEvent {
  time: string
  type: string
  title: string
  description: string
  location?: GeoPoint
  alertId?: string
}

export interface CargoTimeline {
  cargoId: string
  events: TimelineEvent[]
}

export interface SendCommandRequest {
  commandType: 'REROUTE' | 'STOP' | 'RETURN' | string
  priority: 'LOW' | 'NORMAL' | 'HIGH'
  payload: Record<string, unknown>
}

export interface CommandDto {
  commandId: string
  plate: string
  vinTopic?: string
  mqttTopic?: string
  commandType: string
  priority: string
  payload?: Record<string, unknown>
  status: CommandStatus
  createdAt?: string
  timeline?: Array<{ time: string; status: CommandStatus; source: string }>
}

export interface AlertListQuery extends PageQuery {
  severity?: AlertSeverity
  type?: string
  status?: AlertStatus
  vehiclePlate?: string
}

export interface AlertDto {
  alertId: string
  alertType: string
  severity: AlertSeverity
  status: AlertStatus
  vehiclePlate: string
  cargoId?: string
  driverName?: string
  title: string
  summary?: string
  description?: string
  triggeredAt: string
  location?: GeoPoint
  logs?: Array<{ time: string; operator: string; action: string }>
}

export interface AlertStats {
  pending: Record<'critical' | 'warning' | 'info', number>
  resolvedToday: number
  totalThisMonth: number
  averageResolveTimeMinutes: number
  byType: Record<string, number>
}

export interface DeviceDto {
  imei: string
  plate: string
  status: DeviceStatus
  lastHeartbeat: string
  battery: number
  signal: number
  gnssSatellites: number
  temp: number
}

export interface DeviceStatusResult {
  devices: DeviceDto[]
  onlineCount: number
  offlineCount: number
  total: number
}

export interface DeviceHeartbeat {
  time: string
  status?: DeviceStatus
  battery?: number
  signal?: number
  temp?: number
}

export interface CargoDeviceEvent {
  eventId: string
  imei: string
  type: string
  time: string
  data?: Record<string, unknown>
}

export interface AssistantChatRequest {
  question: string
  sessionId?: string
  cargoId?: string
}

export interface AssistantSource {
  documentId: string
  title: string
  chunkId: string
  score: number
}

export interface AssistantChatResponse {
  sessionId: string
  answer: string
  sources: AssistantSource[]
  confidence: number
  answeredAt: string
}

export interface AssistantMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

export interface KnowledgeDocument {
  documentId: string
  title: string
  category?: string
  status: string
  objectKey?: string
  chunkCount?: number
}

export interface KnowledgeIndexResult {
  documentId: string
  status: string
  chunkCount: number
  embeddingModel: string
}

export interface FileUploadResult {
  url: string
  fileName: string
  originalName: string
  size: number
  contentType: string
}

export interface HealthStatus {
  status: string
  components?: Record<string, { status: string; details?: Record<string, unknown> }>
}
