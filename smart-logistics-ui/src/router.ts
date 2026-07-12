import { createRouter, createWebHashHistory } from 'vue-router'
import { isDemoMode } from '@/services/config'
import { tokenManager } from '@/services/token'
import type { UserRole } from '@/types'

const EmptyRoute = { template: '<div />' }

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'login', component: EmptyRoute, meta: { public: true } },
  { path: '/portal', name: 'portal', component: EmptyRoute },
  { path: '/overview', name: 'overview', component: EmptyRoute },
  { path: '/tracking', name: 'tracking', component: EmptyRoute, meta: { roles: ['SHIPPER', 'DISPATCHER', 'ADMIN'] } },
  { path: '/dispatch', name: 'dispatch', component: EmptyRoute, meta: { roles: ['DISPATCHER', 'ADMIN'] } },
  { path: '/personnel', name: 'personnel', component: EmptyRoute, meta: { roles: ['DISPATCHER', 'ADMIN'] } },
  { path: '/driver', name: 'driver', component: EmptyRoute, meta: { roles: ['DRIVER', 'DISPATCHER', 'ADMIN'] } },
  { path: '/alerts', name: 'alerts', component: EmptyRoute, meta: { roles: ['WAREHOUSE', 'DISPATCHER', 'ADMIN'] } },
  { path: '/warehouse', name: 'warehouse', component: EmptyRoute, meta: { roles: ['WAREHOUSE', 'DISPATCHER', 'ADMIN'] } },
  { path: '/assistant', name: 'assistant', component: EmptyRoute, meta: { roles: ['SHIPPER', 'WAREHOUSE', 'DISPATCHER', 'DRIVER', 'ADMIN'] } },
  { path: '/:pathMatch(.*)*', redirect: '/portal' },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach((to) => {
  const demoToken = sessionStorage.getItem('smart-logistics-token') || localStorage.getItem('smart-logistics-token')
  const authenticated = isDemoMode()
    ? Boolean(demoToken)
    : Boolean(tokenManager.getAccessToken())

  if (to.meta.public) return true
  if (!authenticated) return { name: 'login', query: { redirect: to.fullPath } }

  const roles = to.meta.roles as UserRole[] | undefined
  const apiRole = tokenManager.getUser()?.role
  const demoRole = (sessionStorage.getItem('smart-logistics-role') || localStorage.getItem('smart-logistics-role')) as UserRole | null
  const role = isDemoMode() ? demoRole : apiRole
  if (roles?.length && (!role || !roles.includes(role))) return { name: 'portal' }
  return true
})
