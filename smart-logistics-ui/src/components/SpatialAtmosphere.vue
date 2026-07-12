<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

type Star = {
  x: number
  y: number
  z: number
  size: number
  speed: number
  alpha: number
}

const canvas = ref<HTMLCanvasElement | null>(null)
let context: CanvasRenderingContext2D | null = null
let frame = 0
let width = 0
let height = 0
let pixelRatio = 1
let stars: Star[] = []
let pointerX = 0
let pointerY = 0
let easedX = 0
let easedY = 0
let observer: ResizeObserver | null = null
let reducedMotion: MediaQueryList | null = null

function createStars() {
  const count = Math.min(190, Math.max(90, Math.round(width * height / 9500)))
  stars = Array.from({ length: count }, () => ({
    x: (Math.random() - .5) * width * 1.7,
    y: (Math.random() - .5) * height * 1.45,
    z: Math.random() * 1100 + 80,
    size: Math.random() * 1.35 + .35,
    speed: Math.random() * .65 + .18,
    alpha: Math.random() * .58 + .2,
  }))
}

function resize() {
  const element = canvas.value
  if (!element) return
  const rect = element.getBoundingClientRect()
  width = Math.max(1, rect.width)
  height = Math.max(1, rect.height)
  pixelRatio = Math.min(window.devicePixelRatio || 1, 1.5)
  element.width = Math.round(width * pixelRatio)
  element.height = Math.round(height * pixelRatio)
  context = element.getContext('2d')
  context?.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
  createStars()
  draw(true)
}

function resetStar(star: Star) {
  star.x = (Math.random() - .5) * width * 1.7
  star.y = (Math.random() - .5) * height * 1.45
  star.z = 1160
}

function draw(staticFrame = false) {
  if (!context || !width || !height) return
  context.clearRect(0, 0, width, height)
  easedX += (pointerX - easedX) * .035
  easedY += (pointerY - easedY) * .035

  const fov = Math.max(420, width * .44)
  const centerX = width * .56 + easedX * 36
  const centerY = height * .48 + easedY * 24

  for (const star of stars) {
    if (!staticFrame) star.z -= star.speed
    if (star.z < 12) resetStar(star)
    const scale = fov / (fov + star.z)
    const x = centerX + (star.x + easedX * star.z * .018) * scale
    const y = centerY + (star.y + easedY * star.z * .012) * scale
    if (x < -30 || x > width + 30 || y < -30 || y > height + 30) continue

    const depth = 1 - star.z / 1180
    const radius = star.size * (.6 + depth * 1.9)
    context.beginPath()
    context.fillStyle = `rgba(158, 255, 205, ${star.alpha * (.22 + depth * .78)})`
    context.shadowColor = 'rgba(102, 255, 194, .7)'
    context.shadowBlur = depth > .72 ? 8 : 0
    context.arc(x, y, radius, 0, Math.PI * 2)
    context.fill()
  }
  context.shadowBlur = 0

  if (!staticFrame && !document.hidden) frame = requestAnimationFrame(() => draw())
}

function onPointerMove(event: PointerEvent) {
  pointerX = event.clientX / Math.max(1, window.innerWidth) - .5
  pointerY = event.clientY / Math.max(1, window.innerHeight) - .5
}

function onVisibilityChange() {
  cancelAnimationFrame(frame)
  if (!document.hidden && !reducedMotion?.matches) frame = requestAnimationFrame(() => draw())
}

onMounted(() => {
  const element = canvas.value
  if (!element) return
  reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  observer = new ResizeObserver(resize)
  observer.observe(element)
  window.addEventListener('pointermove', onPointerMove, { passive: true })
  document.addEventListener('visibilitychange', onVisibilityChange)
  resize()
  if (!reducedMotion.matches) frame = requestAnimationFrame(() => draw())
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  observer?.disconnect()
  window.removeEventListener('pointermove', onPointerMove)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div class="spatial-atmosphere" aria-hidden="true">
    <canvas ref="canvas"></canvas>
    <div class="spatial-horizon"></div>
    <div class="spatial-aurora aurora-a"></div>
    <div class="spatial-aurora aurora-b"></div>
    <svg class="spatial-routes" viewBox="0 0 1600 900" preserveAspectRatio="none">
      <path d="M-80 710 C280 520 370 760 690 570 S1190 360 1700 520" />
      <path d="M180 980 C420 620 760 720 920 470 S1260 190 1590 250" />
      <path d="M-120 280 C260 160 430 390 760 300 S1260 80 1710 170" />
    </svg>
    <div class="spatial-vignette"></div>
  </div>
</template>
