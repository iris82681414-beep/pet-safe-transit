<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, reactive, ref } from 'vue'
import type { Component } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useLogisticsStore } from '@/stores/logistics'
import type { NotificationItem, UserRole } from '@/types'
import { useRoute, useRouter } from 'vue-router'
import LoginPortal from '@/components/LoginPortal.vue'
import SpatialAtmosphere from '@/components/SpatialAtmosphere.vue'
import VoiceAssistantButton from '@/components/VoiceAssistant/VoiceAssistantButton.vue'
import UserAccountMenu from '@/components/UserAccountMenu.vue'
import { preloadAMap } from '@/services/amap'

const store = useLogisticsStore()
const route = useRoute()
const router = useRouter()
const loadOverviewView = () => import('@/views/OverviewView.vue')
const OverviewView = defineAsyncComponent(loadOverviewView)
const TrackingView = defineAsyncComponent(() => import('@/views/TrackingView.vue'))
const DispatchView = defineAsyncComponent(() => import('@/views/DispatchView.vue'))
const AlertsView = defineAsyncComponent(() => import('@/views/AlertsView.vue'))
const WarehouseView = defineAsyncComponent(() => import('@/views/WarehouseView.vue'))
const AssistantView = defineAsyncComponent(() => import('@/views/AssistantView.vue'))
const DriverView = defineAsyncComponent(() => import('@/views/DriverView.vue'))
const PersonnelView = defineAsyncComponent(() => import('@/views/PersonnelView.vue'))
const PortalView = defineAsyncComponent(() => import('@/views/PortalView.vue'))
const PetOwnerDashboardView = defineAsyncComponent(() => import('@/views/PetOwnerDashboardView.vue'))

const { user, vehicles, transitCount, pendingAlertCount, unreadCount, notifications } = storeToRefs(store)
const active = computed({
  get: () => String(route.name || 'portal'),
  set: (page: string) => { void router.push({ name: page }) },
})
const collapsed = ref(false)
const notificationDrawer = ref(false)
const loginLoading = ref(false)
const loginTransitioning = ref(false)
const loginForm = reactive<{ username: string; password: string; role: UserRole }>({
  username: 'dispatcher',
  password: '123456',
  role: 'DISPATCHER',
})

const loginSlides = [
  {
    title: '宠物中转照护',
    image: 'https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=640&q=80',
  },
  {
    title: '宠物专车调度',
    image: 'https://images.unsplash.com/photo-1566576721346-d4a3b4eaeb55?auto=format&fit=crop&w=640&q=80',
  },
  {
    title: '跨城安心托运',
    image: 'https://images.unsplash.com/photo-1494412519320-aa613dfb7738?auto=format&fit=crop&w=640&q=80',
  },
  {
    title: '宠物身份核验',
    image: 'https://images.unsplash.com/photo-1578575437130-527eed3abbec?auto=format&fit=crop&w=640&q=80',
  },
  {
    title: '温湿度与健康监护',
    image: 'https://images.unsplash.com/photo-1587293852726-70cdb56c2866?auto=format&fit=crop&w=640&q=80',
  },
]

const menus = [
  { key: 'pet-owner', label: '宠物家长安心旅程', icon: 'FirstAidKit', roles: ['SHIPPER', 'ADMIN'] },
  { key: 'assistant', label: '伴生智能助手', icon: 'ChatDotRound', roles: ['SHIPPER', 'WAREHOUSE', 'DISPATCHER', 'DRIVER', 'ADMIN'] },
  { key: 'overview', label: '托运运营总览', icon: 'DataBoard', roles: ['SHIPPER', 'WAREHOUSE', 'DISPATCHER', 'DRIVER', 'ADMIN'] },
  { key: 'tracking', label: '宠物旅程追踪', icon: 'Location', roles: ['SHIPPER', 'DISPATCHER', 'ADMIN'] },
  { key: 'dispatch', label: '托运车辆调度', icon: 'Van', roles: ['DISPATCHER', 'ADMIN'] },
  { key: 'personnel', label: '司机 / 照护员管理', icon: 'UserFilled', roles: ['DISPATCHER', 'ADMIN'] },
  { key: 'driver', label: '司机与照护任务', icon: 'Guide', roles: ['DRIVER', 'DISPATCHER', 'ADMIN'] },
  { key: 'alerts', label: '动物福利风险中心', icon: 'Warning', roles: ['WAREHOUSE', 'DISPATCHER', 'ADMIN'] },
  { key: 'warehouse', label: '宠物中转与笼位管理', icon: 'Box', roles: ['WAREHOUSE', 'DISPATCHER', 'ADMIN'] },
]
const availableMenus = computed(() => menus.filter((item) => user.value && item.roles.includes(user.value.role)))

const pageMap: Record<string, Component> = {
  overview: OverviewView,
  tracking: TrackingView,
  dispatch: DispatchView,
  personnel: PersonnelView,
  alerts: AlertsView,
  warehouse: WarehouseView,
  assistant: AssistantView,
  driver: DriverView,
  portal: PortalView,
  'pet-owner': PetOwnerDashboardView,
}

const pageTitle = computed(() => active.value === 'portal' ? '伴生云途导航' : menus.find((item) => item.key === active.value)?.label || '托运运营总览')
const liveStatusText = computed(() => {
  if (store.usingDemo) return '演示数据模式'
  return store.realtimeState === 'open' ? '实时连接正常' : '实时连接恢复中'
})

async function enterPlatform() {
  loginLoading.value = true
  try {
    await store.login(loginForm.username, loginForm.password, loginForm.role)
    await router.push('/portal')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败，请检查账号或服务地址')
  } finally {
    loginLoading.value = false
  }
}

function changeRole(role: UserRole) {
  store.switchRole(role)
  if (active.value !== 'portal' && !availableMenus.value.some((item) => item.key === active.value)) active.value = 'portal'
}

async function logout() {
  await store.logout()
  await router.push({ name: 'login' })
}

function openNotifications() {
  notificationDrawer.value = true
}

function openNotification(item: NotificationItem) {
  store.markNotificationRead(item.id)
  const targetPage = item.targetPage || (item.type === 'alert' ? 'alerts' : item.type === 'command' ? 'driver' : 'overview')
  if (availableMenus.value.some((menu) => menu.key === targetPage)) {
    active.value = targetPage
    notificationDrawer.value = false
  } else {
    ElMessage.warning('当前身份暂时没有该页面权限')
  }
}

function closeNotification(id: string, event: MouseEvent) {
  event.stopPropagation()
  store.removeNotification(id)
}

function clearReadNotifications() {
  store.removeReadNotifications()
  ElMessage.success('已清理已读通知')
}

function resetDemo() {
  store.resetDemoData()
  active.value = 'portal'
  ElMessage.success('演示数据已恢复为初始状态')
}

function backToPortal() {
  active.value = 'portal'
}

async function handleAuthExpired() {
  await store.logout()
  await router.push({ name: 'login' })
  ElMessage.error('登录状态已失效，请重新登录')
}

onMounted(() => {
  window.addEventListener('smart-logistics:auth-expired', handleAuthExpired)
  if (store.user) void store.initialize().catch(() => ElMessage.error(store.error))
  window.setTimeout(() => { void loadOverviewView() }, 250)
  window.setTimeout(() => { void preloadAMap().catch(() => undefined) }, 550)
})
onUnmounted(() => window.removeEventListener('smart-logistics:auth-expired', handleAuthExpired))
</script>

<template>
  <LoginPortal
    v-if="active === 'login' || !user || loginTransitioning"
    @transition-start="loginTransitioning = true"
    @transition-end="loginTransitioning = false"
  />
  <div v-if="false" class="login-page">
    <div class="login-ambient ambient-one"></div>
    <div class="login-ambient ambient-two"></div>

    <section class="login-story">
      <div class="brand-mark large"><el-icon><Van /></el-icon></div>
      <p class="eyebrow">BANSHENG PET JOURNEY CLOUD</p>
      <h1>伴生云途<br />宠物托运协同平台</h1>
      <p class="login-description">
        连接宠物、车辆、中转照护与托运任务，让工作人员及时掌握每一段安心旅程。
      </p>
      <div class="logistics-visual" aria-hidden="true">
        <div class="visual-grid"></div>
        <div class="route route-a"></div>
        <div class="route route-b"></div>
        <div class="route route-c"></div>
        <span class="hub hub-main"><el-icon><Van /></el-icon></span>
        <span class="hub hub-a"></span>
        <span class="hub hub-b"></span>
        <span class="hub hub-c"></span>
        <div class="scan-line"></div>
        <div class="status-card status-card-a">
          <span>在途车辆</span>
          <strong>{{ transitCount }}</strong>
        </div>
        <div class="status-card status-card-b">
          <span>接入车辆</span>
          <strong>{{ vehicles.length }}</strong>
        </div>
        <div class="status-card status-card-c">
          <span>智能调度</span>
          <strong>LIVE</strong>
        </div>
        <div class="flow-panel">
          <span>沪杭干线</span>
          <i></i>
        </div>
      </div>
    </section>

    <section class="login-auth-stack">
      <div class="login-carousel" aria-label="伴生云途宠物托运场景轮播">
        <div class="login-carousel-track">
          <article v-for="slide in loginSlides" :key="slide.title" class="login-slide">
            <img :src="slide.image" :alt="slide.title" />
            <span>{{ slide.title }}</span>
          </article>
          <article v-for="slide in loginSlides" :key="`${slide.title}-copy`" class="login-slide">
            <img :src="slide.image" :alt="slide.title" />
            <span>{{ slide.title }}</span>
          </article>
        </div>
      </div>

      <div class="login-card">
        <div class="mobile-brand">
          <div class="brand-mark"><el-icon><Van /></el-icon></div>
          <strong>伴生云途</strong>
        </div>
        <p class="eyebrow">WELCOME BACK</p>
        <h2>登录工作台</h2>
        <p class="muted">选择工作人员角色进入对应工作台，体验宠物托运、途中照护与安全交接流程。</p>
        <el-form label-position="top" size="large" @submit.prevent="enterPlatform">
          <el-form-item label="账号">
            <el-input v-model="loginForm.username" placeholder="请输入账号" prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" prefix-icon="Lock" />
          </el-form-item>
          <el-form-item label="演示角色">
            <el-select v-model="loginForm.role" style="width: 100%">
              <el-option label="宠物家长服务专员" value="SHIPPER" />
              <el-option label="中转照护员" value="WAREHOUSE" />
              <el-option label="调度员" value="DISPATCHER" />
              <el-option label="司机" value="DRIVER" />
              <el-option label="系统管理员" value="ADMIN" />
            </el-select>
          </el-form-item>
          <el-button class="login-button" type="primary" native-type="submit" :loading="loginLoading" @click="enterPlatform">
            进入伴生云途工作台
          </el-button>
        </el-form>
        <div class="demo-tip"><el-icon><InfoFilled /></el-icon>后端测试账号 dispatcher / 123456</div>
      </div>
    </section>
  </div>

  <div v-if="user && active !== 'login'" class="app-shell" :class="[{ collapsed, 'canvas-mode': active === 'portal', 'portal-mode': active === 'portal', 'content-mode': active !== 'portal' && active !== 'pet-owner', 'pet-owner-mode': active === 'pet-owner' }, `page-${active}`]">
    <SpatialAtmosphere v-if="active !== 'overview' && active !== 'portal' && active !== 'pet-owner'" />
    <aside v-if="false" class="sidebar">
      <div class="brand">
        <div class="brand-mark"><el-icon><Van /></el-icon></div>
        <div class="brand-copy">
          <strong>伴生云途</strong>
          <span>BANSHENG PET JOURNEY</span>
        </div>
      </div>

      <nav class="main-nav">
        <button
          v-for="item in availableMenus"
          :key="item.key"
          class="nav-item"
          :class="{ active: active === item.key }"
          :title="item.label"
          @click="active = item.key"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <b v-if="item.key === 'alerts' && pendingAlertCount">{{ pendingAlertCount }}</b>
        </button>
      </nav>

      <div class="sidebar-footer">
        <div class="system-status"><i></i><span>所有核心服务正常</span></div>
        <button class="collapse-button" @click="collapsed = !collapsed">
          <el-icon><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          <span>收起导航</span>
        </button>
      </div>
    </aside>

    <main class="workspace">
      <header v-if="active !== 'portal' && active !== 'pet-owner'" class="topbar">
        <div>
          <p class="eyebrow">伴生云途 · 工作人员业务中心</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="topbar-actions">
          <button class="portal-return-button" type="button" @click="backToPortal">
            返回导航窗口
          </button>
          <div class="live-pill"><i></i>{{ liveStatusText }}</div>
          <UserAccountMenu @logout="logout" />
        </div>
      </header>

      <div class="page-content">
        <el-alert v-if="store.error && active !== 'pet-owner'" :title="store.error" type="error" show-icon closable style="margin-bottom: 16px" />
        <component :is="pageMap[active]" @navigate="active = $event" />
      </div>
    </main>

    <el-drawer v-model="notificationDrawer" title="实时通知" size="380px">
      <div class="notification-toolbar">
        <el-button size="small" text @click="store.markNotificationsRead()">鍏ㄩ儴宸茶</el-button>
        <el-button size="small" text type="danger" @click="clearReadNotifications">娓呯悊宸茶</el-button>
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

    <VoiceAssistantButton v-if="active !== 'pet-owner'" :context="{ sourcePage: active }" />
  </div>
</template>
