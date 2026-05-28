import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { title: '总览大屏', icon: 'LayoutDashboard' }
  },
  {
    path: '/events',
    name: 'events',
    component: () => import('@/views/EventsView.vue'),
    meta: { title: '告警事件', icon: 'BellRing' }
  },
  {
    path: '/incidents',
    name: 'incidents',
    component: () => import('@/views/IncidentsView.vue'),
    meta: { title: '故障组', icon: 'Flame' }
  },
  {
    path: '/rules',
    name: 'rules',
    component: () => import('@/views/RulesView.vue'),
    meta: { title: '告警规则', icon: 'Sparkles' }
  },
  {
    path: '/objects',
    name: 'objects',
    component: () => import('@/views/ObjectsView.vue'),
    meta: { title: '监控对象', icon: 'Server' }
  },
  {
    path: '/channels',
    name: 'channels',
    component: () => import('@/views/ChannelsView.vue'),
    meta: { title: '通知渠道', icon: 'Send' }
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/SettingsView.vue'),
    meta: { title: '系统设置', icon: 'Settings' }
  },
  {
    path: '/ai-stats',
    name: 'ai-stats',
    redirect: { path: '/settings', query: { tab: 'ai-stats' } },
    meta: { title: 'AI 调用统计', hideInSidebar: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/ErrorPage.vue'),
    props: { variant: '404' },
    meta: { title: '页面未找到', hideInSidebar: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.afterEach((to) => {
  const title = (to.meta?.title as string) || ''
  document.title = title ? `${title} · AIOps Alert` : 'AIOps Alert'
})

export default router
