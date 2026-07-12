<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'

export interface MapMarker {
  id: string
  lat: number
  lng: number
  label?: string
  status?: string
}

const props = withDefaults(defineProps<{
  markers: MapMarker[]
  trajectory?: Array<{ lat: number; lng: number }>
  zoom?: number
}>(), { trajectory: () => [], zoom: 8 })

const emit = defineEmits<{ select: [id: string] }>()
const host = ref<HTMLElement>()
let map: L.Map | undefined
let markerLayer: L.LayerGroup | undefined
let trajectoryLayer: L.Polyline | undefined
let resizeObserver: ResizeObserver | undefined
let layoutFrame = 0

function fallbackPoint(seed: string, index: number): L.LatLngTuple {
  const hash = [...seed].reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return [
    30.25 + ((hash + index * 17) % 70) / 100,
    120.75 + ((hash + index * 29) % 105) / 100,
  ]
}

function normalize(point: { lat: number; lng: number }, seed = '', index = 0): L.LatLngTuple {
  const lat = Number(point.lat)
  const lng = Number(point.lng)
  let normalized: L.LatLngTuple

  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return fallbackPoint(seed, index)
  }
  if (lng >= 100 && lng <= 125 && lat >= 20 && lat <= 40) {
    normalized = [lat, lng]
  } else if (lat >= 100 && lat <= 125 && lng >= 20 && lng <= 40) {
    normalized = [lng, lat]
  } else if (lat >= 0 && lat <= 100 && lng >= 0 && lng <= 100) {
    normalized = [30.2 + (lng - 30) * 0.025, 120.75 + (lat - 20) * 0.018]
  } else {
    return fallbackPoint(seed, index)
  }

  if (normalized[0] < 27 || normalized[0] > 34 || normalized[1] < 116 || normalized[1] > 124) {
    return fallbackPoint(seed, index)
  }
  return normalized
}

function renderLayers() {
  if (!map) return
  markerLayer?.clearLayers()
  const normalizedMarkers = props.markers.map((item, index) => ({
    item,
    point: normalize(item, item.id, index),
  }))
  normalizedMarkers.forEach(({ item, point }) => {
    const marker = L.marker(point, {
      icon: L.divIcon({
        className: 'fleet-map-marker',
        html: `<span class="${(item.status || '').toLowerCase()}">🚚</span><b>${item.label || item.id}</b>`,
        iconSize: [120, 34],
        iconAnchor: [17, 17],
      }),
      zIndexOffset: item.status === 'IN_TRANSIT' ? 1000 : item.status === 'IDLE' ? 500 : 0,
      riseOnHover: true,
    })
    marker.on('click', () => emit('select', item.id))
    marker.addTo(markerLayer!)
  })

  trajectoryLayer?.remove()
  const normalizedTrajectory = props.trajectory.map((point, index) => normalize(point, 'trajectory', index))
  if (props.trajectory.length > 1) {
    trajectoryLayer = L.polyline(normalizedTrajectory, { color: '#168c72', weight: 5, opacity: 0.8 }).addTo(map)
  }

  const points = [...normalizedMarkers.map(({ point }) => point), ...normalizedTrajectory] as L.LatLngExpression[]
  if (points.length > 1) map.fitBounds(L.latLngBounds(points), { padding: [42, 42], maxZoom: 12 })
  else if (points.length === 1) map.setView(points[0], 12)
}

function refreshLayout() {
  window.cancelAnimationFrame(layoutFrame)
  layoutFrame = window.requestAnimationFrame(() => {
    if (!map) return
    map.invalidateSize({ pan: false })
    renderLayers()
  })
}

onMounted(() => {
  if (!host.value) return
  const regionalBounds = L.latLngBounds([27, 116], [34, 124])
  map = L.map(host.value, {
    zoomControl: true,
    minZoom: 6,
    maxBounds: regionalBounds.pad(0.55),
    maxBoundsViscosity: 1,
    worldCopyJump: false,
  }).setView([30.75, 120.9], props.zoom)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    minZoom: 6,
    maxZoom: 18,
    noWrap: true,
    bounds: regionalBounds.pad(0.75),
    attribution: '© OpenStreetMap',
  }).addTo(map)
  markerLayer = L.layerGroup().addTo(map)
  renderLayers()
  resizeObserver = new ResizeObserver(refreshLayout)
  resizeObserver.observe(host.value)
  map.whenReady(refreshLayout)
})

watch(() => [props.markers, props.trajectory], renderLayers, { deep: true })

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = undefined
  window.cancelAnimationFrame(layoutFrame)
  map?.remove()
  map = undefined
})
</script>

<template>
  <div ref="host" class="leaflet-fleet-map" aria-label="实时车辆地图"></div>
</template>
