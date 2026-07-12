<script setup lang="ts">
import AMapLoader from '@amap/amap-jsapi-loader'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { serviceConfig } from '@/services/config'
import type { Vehicle } from '@/types'

interface RoutePoint {
  lat: number
  lng: number
}

const props = withDefaults(defineProps<{
  vehicles: Vehicle[]
  selectedPlate?: string
  height?: string
  showRoute?: boolean
  routePoints?: RoutePoint[]
  tooltipStyle?: 'photo' | 'compact'
  initialZoom?: number
  fitMinZoom?: number
  fitMaxZoom?: number
}>(), {
  selectedPlate: '',
  height: '360px',
  showRoute: true,
  routePoints: () => [],
  tooltipStyle: 'photo',
  initialZoom: 9.2,
  fitMinZoom: 8.6,
  fitMaxZoom: 13,
})

const emit = defineEmits<{ select: [vehicle: Vehicle] }>()

const mapEl = ref<HTMLElement>()
const usingRealMap = ref(false)
const mapLoadError = ref('')
let map: any
let AMapRef: any
let markers: any[] = []
let polyline: any
let hasFitInitialView = false

const bounds = {
  minLng: 119.75,
  maxLng: 121.65,
  minLat: 30.05,
  maxLat: 31.55,
}

function coordinateFor(vehicle: Vehicle) {
  const prototype = vehicle.lat >= 0 && vehicle.lat <= 100 && vehicle.lng >= 0 && vehicle.lng <= 100
  if (!prototype) return { lat: vehicle.lat, lng: vehicle.lng, prototype }
  return {
    lat: bounds.minLat + (1 - vehicle.lng / 100) * (bounds.maxLat - bounds.minLat),
    lng: bounds.minLng + (vehicle.lat / 100) * (bounds.maxLng - bounds.minLng),
    prototype,
  }
}

const fallbackPoints = computed(() => props.vehicles.map((vehicle) => {
  const coordinate = coordinateFor(vehicle)
  const leftPercent = coordinate.prototype
    ? Math.min(94, Math.max(6, vehicle.lat))
    : Math.min(94, Math.max(6, ((coordinate.lng - bounds.minLng) / (bounds.maxLng - bounds.minLng)) * 100))
  const topPercent = coordinate.prototype
    ? Math.min(92, Math.max(8, vehicle.lng))
    : Math.min(92, Math.max(8, (1 - (coordinate.lat - bounds.minLat) / (bounds.maxLat - bounds.minLat)) * 100))
  return {
    vehicle,
    left: `${leftPercent}%`,
    top: `${topPercent}%`,
    placement: tooltipPlacement(leftPercent, topPercent),
  }
}))

const fallbackRoutePoints = computed(() => props.routePoints.map((point) => {
  const coordinate = coordinateFor({
    plate: '',
    driver: '',
    phone: '',
    status: 'IN_TRANSIT',
    speed: 0,
    lat: point.lat,
    lng: point.lng,
    location: '',
    heartbeat: '',
  })
  const leftPercent = Math.min(96, Math.max(4, ((coordinate.lng - bounds.minLng) / (bounds.maxLng - bounds.minLng)) * 100))
  const topPercent = Math.min(94, Math.max(6, (1 - (coordinate.lat - bounds.minLat) / (bounds.maxLat - bounds.minLat)) * 100))
  return `${leftPercent},${topPercent}`
}))

const truckIcon = '<span class="amap-truck-icon"><i class="body"></i><i class="cab"></i><i class="wheel a"></i><i class="wheel b"></i></span>'
const vehicleImages = [
  'https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=420&q=88',
  'https://images.unsplash.com/photo-1565891741441-64926e441838?auto=format&fit=crop&w=420&q=88',
  'https://images.unsplash.com/photo-1601584115197-04ecc0da31d7?auto=format&fit=crop&w=420&q=88',
]

function vehicleImageFor(plate: string) {
  const seed = Array.from(plate).reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return vehicleImages[seed % vehicleImages.length]
}

function tooltipPlacement(leftPercent: number, topPercent: number) {
  if (topPercent < 34) return 'bottom'
  if (leftPercent > 72) return 'left'
  if (leftPercent < 28) return 'right'
  return 'top'
}

function realMapLabelOptions(vehicle: Vehicle) {
  const leftPercent = ((vehicle.lng - bounds.minLng) / (bounds.maxLng - bounds.minLng)) * 100
  const topPercent = (1 - (vehicle.lat - bounds.minLat) / (bounds.maxLat - bounds.minLat)) * 100
  const placement = tooltipPlacement(leftPercent, topPercent)
  const offsets: Record<string, [number, number]> = {
    top: [0, -10],
    bottom: [0, 10],
    left: [-10, 0],
    right: [10, 0],
  }
  return { direction: placement, offset: offsets[placement] }
}

function clearMarkers() {
  markers.forEach((marker) => marker.setMap(null))
  markers = []
  if (polyline) {
    polyline.setMap(null)
    polyline = null
  }
}

function fitMapToVehicles(path: number[][]) {
  if (!map || !AMapRef || !path.length) return
  if (path.length === 1) {
    map.setZoomAndCenter(12, path[0], false)
    return
  }
  const lngs = path.map((item) => item[0])
  const lats = path.map((item) => item[1])
  const southWest = new AMapRef.LngLat(Math.min(...lngs) - 0.08, Math.min(...lats) - 0.08)
  const northEast = new AMapRef.LngLat(Math.max(...lngs) + 0.08, Math.max(...lats) + 0.08)
  map.setBounds(new AMapRef.Bounds(southWest, northEast), false, [58, 58, 58, 58])
  if (!hasFitInitialView) {
    hasFitInitialView = true
    window.setTimeout(() => {
      const zoom = map.getZoom?.()
      if (typeof zoom === 'number' && zoom < props.fitMinZoom) map.setZoom(props.fitMinZoom)
      if (typeof zoom === 'number' && zoom > props.fitMaxZoom) map.setZoom(props.fitMaxZoom)
    }, 80)
  }
}

function renderMapData() {
  if (!map || !AMapRef) return
  clearMarkers()
  const path: number[][] = []
  const routePath = props.routePoints
    .filter((point) => point.lat != null && point.lng != null)
    .map((point) => {
      const coordinate = coordinateFor({
        plate: '',
        driver: '',
        phone: '',
        status: 'IN_TRANSIT',
        speed: 0,
        lat: point.lat,
        lng: point.lng,
        location: '',
        heartbeat: '',
      })
      return [coordinate.lng, coordinate.lat]
    })
  if (props.showRoute && routePath.length >= 2) {
    polyline = new AMapRef.Polyline({
      path: routePath,
      showDir: true,
      strokeColor: '#2563eb',
      strokeOpacity: 0.88,
      strokeWeight: 6,
      strokeStyle: 'solid',
      lineJoin: 'round',
      lineCap: 'round',
      zIndex: 40,
    })
    polyline.setMap(map)
  }
  props.vehicles.forEach((vehicle) => {
    if (!vehicle.lat || !vehicle.lng) return
    const coordinate = coordinateFor(vehicle)
    const active = props.selectedPlate === vehicle.plate
    const marker = new AMapRef.Marker({
      position: [coordinate.lng, coordinate.lat],
      offset: new AMapRef.Pixel(-23, -23),
      content: `<div class="amap-truck-marker ${vehicle.status.toLowerCase()} ${active ? 'selected' : ''}" title="${vehicle.plate} · ${vehicle.speed} km/h"><button type="button" aria-label="${vehicle.plate}">${truckIcon}</button></div>`,
    })
    const hideLabel = () => marker.setLabel({ content: '' })
    marker.on('click', () => emit('select', vehicle))
    marker.on('mouseover', () => {
      const label = realMapLabelOptions(vehicle)
      marker.setLabel({
        direction: label.direction,
        offset: new AMapRef.Pixel(label.offset[0], label.offset[1]),
        content: props.tooltipStyle === 'photo'
          ? `<div class="amap-truck-label"><img src="${vehicleImageFor(vehicle.plate)}" alt=""><strong>${vehicle.plate}</strong><span>${vehicle.speed} km/h · ${vehicle.location}</span></div>`
          : `<div class="amap-truck-label compact"><strong>${vehicle.plate}</strong><small>${vehicle.driver} · ${vehicle.speed} km/h</small><em>${vehicle.location}</em></div>`,
      })
    })
    marker.on('mouseout', hideLabel)
    marker.on('mouseleave', hideLabel)
    marker.setMap(map)
    markers.push(marker)
    path.push([coordinate.lng, coordinate.lat])
  })
  fitMapToVehicles(routePath.length ? [...routePath, ...path] : path)
}

async function initMap() {
  mapLoadError.value = ''
  if (!serviceConfig.amapKey) {
    mapLoadError.value = '未读取到 VITE_AMAP_KEY'
    return
  }
  if (!mapEl.value) {
    mapLoadError.value = '地图容器未挂载'
    return
  }
  try {
    if (serviceConfig.amapSecurityCode) {
      ;(window as any)._AMapSecurityConfig = { securityJsCode: serviceConfig.amapSecurityCode }
    }
    AMapRef = await AMapLoader.load({
      key: serviceConfig.amapKey,
      version: '2.0',
      plugins: ['AMap.Scale', 'AMap.ToolBar'],
    })
    map = new AMapRef.Map(mapEl.value, {
      center: [120.62, 30.92],
      zoom: props.initialZoom,
      zooms: [7.5, 18],
      mapStyle: 'amap://styles/normal',
      viewMode: '2D',
      resizeEnable: true,
    })
    map.addControl(new AMapRef.Scale())
    map.addControl(new AMapRef.ToolBar({ position: { right: '14px', top: '14px' } }))
    usingRealMap.value = true
    renderMapData()
  } catch (error) {
    usingRealMap.value = false
    mapLoadError.value = error instanceof Error ? error.message : String(error)
    console.error('高德地图加载失败', error)
  }
}

watch(() => [props.vehicles, props.selectedPlate, props.routePoints], () => nextTick(renderMapData), { deep: true })

onMounted(initMap)
onUnmounted(() => {
  clearMarkers()
  map?.destroy?.()
})
</script>

<template>
  <div class="smart-map" :style="{ height }">
    <div v-show="usingRealMap" ref="mapEl" class="smart-map-real"></div>
    <div v-if="!usingRealMap" class="smart-map-fallback">
      <svg
        v-if="showRoute && fallbackRoutePoints.length >= 2"
        class="map-route-overlay"
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <polyline class="map-route-shadow" :points="fallbackRoutePoints.join(' ')" />
        <polyline class="map-route-line" :points="fallbackRoutePoints.join(' ')" />
      </svg>
      <div class="map-water"></div>
      <div class="map-road main"></div>
      <div class="map-road branch-a"></div>
      <div class="map-road branch-b"></div>
      <span class="map-city sh">上海</span>
      <span class="map-city hz">杭州</span>
      <span class="map-city sz">苏州</span>
      <button
        v-for="point in fallbackPoints"
        :key="point.vehicle.plate"
        class="map-truck"
        :class="[point.vehicle.status.toLowerCase(), `tip-${point.placement}`, { selected: selectedPlate === point.vehicle.plate }]"
        :style="{ left: point.left, top: point.top }"
        @click="emit('select', point.vehicle)"
      >
        <span class="amap-truck-icon" aria-hidden="true"><i class="body"></i><i class="cab"></i><i class="wheel a"></i><i class="wheel b"></i></span>
        <span class="map-truck-tip" :class="{ compact: tooltipStyle === 'compact' }">
          <img v-if="tooltipStyle === 'photo'" :src="vehicleImageFor(point.vehicle.plate)" alt="" />
          <strong>{{ point.vehicle.plate }}</strong>
          <span v-if="tooltipStyle === 'photo'">{{ point.vehicle.speed }} km/h · {{ point.vehicle.location }}</span>
          <template v-else>
            <small>{{ point.vehicle.driver }} · {{ point.vehicle.speed }} km/h</small>
            <em>{{ point.vehicle.location }}</em>
          </template>
        </span>
      </button>
      <div class="map-legend smart"><span><i class="green-dot"></i>行驶中</span><span><i class="blue-dot"></i>待命</span><span><i class="gray-dot"></i>离线</span></div>
      <div v-if="!serviceConfig.amapKey" class="map-key-tip">配置 VITE_AMAP_KEY 后显示真实高德地图</div>
      <div v-else-if="mapLoadError" class="map-key-tip error">高德地图加载失败：{{ mapLoadError }}</div>
    </div>
  </div>
</template>
