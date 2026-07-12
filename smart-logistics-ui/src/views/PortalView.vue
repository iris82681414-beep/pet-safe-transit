<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import type { Component } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useLogisticsStore } from '@/stores/logistics'
import UserAccountMenu from '@/components/UserAccountMenu.vue'
import type { UserRole } from '@/types'

const router = useRouter()
const store = useLogisticsStore()
const { user, unreadCount, notifications } = storeToRefs(store)
const floatingPage = ref('')
const notificationDrawer = ref(false)

const pageRoles: Record<string, UserRole[]> = {
  overview: ['SHIPPER', 'WAREHOUSE', 'DISPATCHER', 'DRIVER', 'ADMIN'],
  tracking: ['SHIPPER', 'DISPATCHER', 'ADMIN'],
  dispatch: ['DISPATCHER', 'ADMIN'],
  personnel: ['DISPATCHER', 'ADMIN'],
  driver: ['DRIVER', 'DISPATCHER', 'ADMIN'],
  alerts: ['WAREHOUSE', 'DISPATCHER', 'ADMIN'],
  warehouse: ['WAREHOUSE', 'DISPATCHER', 'ADMIN'],
  assistant: ['SHIPPER', 'WAREHOUSE', 'DISPATCHER', 'DRIVER', 'ADMIN'],
}

const pageLabels: Record<string, string> = {
  overview: '运营总览',
  tracking: '货物追踪',
  dispatch: '车辆调度',
  personnel: '人员管理',
  driver: '司机任务',
  alerts: '告警中心',
  warehouse: '仓库管理',
  assistant: '智能问答',
}

const pageMap: Record<string, Component> = {
  overview: defineAsyncComponent(() => import('@/views/OverviewView.vue')),
  tracking: defineAsyncComponent(() => import('@/views/TrackingView.vue')),
  dispatch: defineAsyncComponent(() => import('@/views/DispatchView.vue')),
  personnel: defineAsyncComponent(() => import('@/views/PersonnelView.vue')),
  driver: defineAsyncComponent(() => import('@/views/DriverView.vue')),
  alerts: defineAsyncComponent(() => import('@/views/AlertsView.vue')),
  warehouse: defineAsyncComponent(() => import('@/views/WarehouseView.vue')),
  assistant: defineAsyncComponent(() => import('@/views/AssistantView.vue')),
}

const allowedPages = computed(() => {
  const role = store.user?.role
  if (!role) return []
  return Object.entries(pageRoles)
    .filter(([, roles]) => roles.includes(role))
    .map(([page]) => page)
})

const iframeSrc = computed(() => {
  const params = new URLSearchParams({
    role: store.user?.role || '',
    pages: allowedPages.value.join(','),
    v: 'portal-20260707-06',
  })
  return `/active-theory/index.html?${params.toString()}`
})

const floatingTitle = computed(() => pageLabels[floatingPage.value] || '业务页面')
const floatingComponent = computed(() => pageMap[floatingPage.value])

async function backToLogin() {
  await store.logout()
  await router.push('/login')
}

function canOpenPage(page: string) {
  const role = store.user?.role
  return Boolean(pageRoles[page] && role && pageRoles[page].includes(role))
}

function openFloatingPage(page: string) {
  if (!canOpenPage(page)) {
    ElMessage.warning('当前身份暂时没有该页面权限')
    return
  }
  floatingPage.value = page
}

function closeFloatingPage() {
  floatingPage.value = ''
}

function handleInnerNavigate(page: string) {
  openFloatingPage(page)
}

function handleVoiceNavigate(event: Event) {
  const detail = (event as CustomEvent<{ page?: string }>).detail
  const page = String(detail?.page || '')
  if (page) openFloatingPage(page)
}

function openNotifications() {
  notificationDrawer.value = true
}

function openNotification(item: { id: string; type: string; targetPage?: string }) {
  store.markNotificationRead(item.id)
  const targetPage = item.targetPage || (item.type === 'alert' ? 'alerts' : item.type === 'command' ? 'driver' : 'overview')
  openFloatingPage(targetPage)
  notificationDrawer.value = false
}

function closeNotification(id: string, event: MouseEvent) {
  event.stopPropagation()
  store.removeNotification(id)
}

function clearReadNotifications() {
  store.removeReadNotifications()
  ElMessage.success('已清理已读通知')
}

async function logout() {
  await store.logout()
  await router.push({ name: 'login' })
}

function handleMessage(event: MessageEvent) {
  if (event.origin !== window.location.origin) return
  if (event.data?.type !== 'smart-logistics:navigate') return

  const page = String(event.data.page || '')
  openFloatingPage(page)
}

onMounted(() => {
  window.addEventListener('message', handleMessage)
  window.addEventListener('smart-logistics:open-floating-page', handleVoiceNavigate)
  window.addEventListener('smart-logistics:close-floating-page', closeFloatingPage)
})
onBeforeUnmount(() => {
  window.removeEventListener('message', handleMessage)
  window.removeEventListener('smart-logistics:open-floating-page', handleVoiceNavigate)
  window.removeEventListener('smart-logistics:close-floating-page', closeFloatingPage)
})
</script>

<template>
  <section class="portal-view" :class="{ 'has-floating-page': floatingComponent }">
    <button class="portal-back" type="button" @click="backToLogin">
      返回登录
    </button>
    <iframe
      :key="store.user?.role"
      :src="iframeSrc"
      title="伴生云途导航窗口"
      allow="fullscreen; autoplay; microphone"
    />
    <button v-if="floatingComponent" class="portal-page-return" type="button" @click="closeFloatingPage">
      返回导航窗口
    </button>
    <div v-if="user && !floatingComponent" class="portal-nav-user">
      <button class="portal-notify-button" title="消息通知" type="button" @click="openNotifications">
        <el-icon><Bell /></el-icon>
        <b v-if="unreadCount">{{ unreadCount }}</b>
      </button>
      <UserAccountMenu @logout="logout" />
    </div>
    <transition name="portal-float">
      <div v-if="floatingComponent" class="portal-floating-page" @dragover.prevent @drop.prevent>
        <header class="portal-floating-header">
          <div class="portal-floating-title">
            <span>伴生云途 · 智能宠物托运与全程感知平台</span>
            <strong>{{ floatingTitle }}</strong>
          </div>
        </header>
        <div class="portal-floating-body">
          <component :is="floatingComponent" @navigate="handleInnerNavigate" />
        </div>
      </div>
    </transition>
    <el-drawer v-model="notificationDrawer" title="实时通知" size="380px">
      <div class="notification-toolbar">
        <el-button size="small" text @click="store.markNotificationsRead()">全部已读</el-button>
        <el-button size="small" text type="danger" @click="clearReadNotifications">清理已读</el-button>
      </div>
      <div class="notification-list">
        <article
          v-for="item in notifications"
          :key="item.id"
          :class="[item.type, { read: item.read }]"
          @click="openNotification(item)"
        >
          <button class="notification-close" type="button" aria-label="删除通知" @click="closeNotification(item.id, $event)">
            <el-icon><Close /></el-icon>
          </button>
          <span class="notification-icon"><el-icon><WarningFilled v-if="item.type === 'alert'" /><Promotion v-else-if="item.type === 'command'" /><BellFilled v-else /></el-icon></span>
          <div><strong>{{ item.title }}</strong><p>{{ item.content }}</p><small>{{ item.time }}</small></div>
        </article>
        <el-empty v-if="!notifications.length" description="暂无通知" :image-size="72" />
      </div>
    </el-drawer>
  </section>
</template>
