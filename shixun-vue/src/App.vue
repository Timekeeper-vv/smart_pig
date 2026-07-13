<script setup lang="ts">
import { ref, onMounted, watchEffect } from 'vue'
import type { User, PageName, AlertType, Role } from './types'
import LoginPage from './components/LoginPage.vue'
import Sidebar from './components/Sidebar.vue'
import CreativeDashboard from './components/CreativeDashboard.vue'
import CreativeStudio from './components/CreativeStudio.vue'
import MarketingAssistant from './components/MarketingAssistant.vue'
import BusinessAssistant from './components/BusinessAssistant.vue'
import CommercialMvp from './components/CommercialMvp.vue'
import ScaleUpPlatform from './components/ScaleUpPlatform.vue'
import ProductionManagement from './components/ProductionManagement.vue'
import ArtworkMarketplace from './components/ArtworkMarketplace.vue'
import CreativeOrderCenter from './components/CreativeOrderCenter.vue'
import LogisticsTracking from './components/LogisticsTracking.vue'
import WarehouseManagement from './components/WarehouseManagement.vue'
import DesignerCenter from './components/DesignerCenter.vue'
import UserManagement from './components/UserManagement.vue'
import NotificationPanel from './components/NotificationPanel.vue'
import GlobalAlert from './components/GlobalAlert.vue'
import AiChat from './components/AiChat.vue'

const PAGE_ROLES: Record<string, Role[]> = {
  dashboard:    ['admin', 'technician', 'feeder'],
  studio:       ['admin', 'technician'],
  marketing:    ['admin', 'technician'],
  businessAi:   ['admin', 'technician'],
  commercialMvp:['admin', 'technician'],
  scaleUp:      ['admin', 'technician'],
  production:   ['admin', 'technician'],
  marketplace:  ['admin', 'technician', 'feeder'],
  orders:       ['admin', 'technician', 'feeder'],
  logistics:    ['admin', 'technician'],
  warehouse:    ['admin', 'technician'],
  designers:    ['admin', 'technician'],
  users:        ['admin'],
}

function hasAccess(page: string, role?: Role): boolean {
  return (PAGE_ROLES[page] ?? ['admin']).includes(role || 'admin')
}

function firstAllowedPage(role: Role): PageName {
  return (Object.keys(PAGE_ROLES).find(p => hasAccess(p, role)) || 'marketplace') as PageName
}

const currentUser = ref<User | null>(null)
const currentPage = ref<PageName>('dashboard')
const sidebarCollapsed = ref<boolean>(false)
const alertMsg = ref<string>('')
const alertType = ref<AlertType>('success')
const alertVisible = ref<boolean>(false)
let alertTimer: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  const saved = sessionStorage.getItem('currentUser')
  if (saved) currentUser.value = JSON.parse(saved) as User
})

watchEffect(() => {
  if (!currentUser.value) return
  const role = currentUser.value.role || 'admin'
  if (!hasAccess(currentPage.value, role)) currentPage.value = firstAllowedPage(role)
})

function showAlert(msg: string, type: AlertType = 'success'): void {
  alertMsg.value = msg
  alertType.value = type
  alertVisible.value = true
  if (alertTimer) clearTimeout(alertTimer)
  alertTimer = setTimeout(() => { alertVisible.value = false }, 3000)
}

function onLogin(user: User): void {
  currentUser.value = user
  sessionStorage.setItem('currentUser', JSON.stringify(user))
  currentPage.value = 'dashboard'
}

function onLogout(): void {
  currentUser.value = null
  sessionStorage.removeItem('currentUser')
}

const pageLabels: Record<string, string> = {
  dashboard:    '经营看板',
  studio:       'AI设计工坊',
  marketing:    '营销文案',
  businessAi:   '报价/物流助手',
  commercialMvp:'询盘到报价',
  scaleUp:      '项目制开发',
  production:   'BOM/打样生产',
  marketplace:  'IP商城',
  orders:       '订单履约',
  logistics:    '物流跟踪',
  warehouse:    '智能仓储',
  designers:    '设计师/创作者',
  users:        '账号权限',
}
</script>

<template>
  <LoginPage v-if="!currentUser" @login="onLogin" />

  <div v-else class="app-shell" :class="{ collapsed: sidebarCollapsed }">
    <!-- Sidebar -->
    <Sidebar
      :current-user="currentUser"
      :current-page="currentPage"
      :collapsed="sidebarCollapsed"
      @switch-page="p => { if (hasAccess(p, currentUser?.role)) currentPage = p }"
      @logout="onLogout"
      @toggle="sidebarCollapsed = !sidebarCollapsed"
    />

    <!-- Right area -->
    <div class="app-body">
      <!-- Top header -->
      <header class="app-header">
        <div class="header-left">
          <button class="toggle-btn" @click="sidebarCollapsed = !sidebarCollapsed" title="Toggle sidebar">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <nav class="breadcrumb">
            <span class="bc-current">{{ pageLabels[currentPage] }}</span>
          </nav>
        </div>
        <div class="header-center-title">之间味道-文创产品智能体平台</div>
        <div class="header-right">
          <NotificationPanel />
          <div class="user-chip">
            <div class="user-avatar">{{ currentUser.username?.[0]?.toUpperCase() }}</div>
            <span class="user-name">{{ currentUser.username }}</span>
          </div>
          <button class="btn btn-secondary btn-sm" @click="onLogout">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
            退出
          </button>
        </div>
      </header>

      <!-- Main content -->
      <main class="app-main">
        <CreativeDashboard    v-if="currentPage === 'dashboard'"   @switch-page="p => { if (hasAccess(p, currentUser?.role)) currentPage = p as PageName }" @alert="showAlert" />
        <CreativeStudio       v-if="currentPage === 'studio'"      @alert="showAlert" />
        <MarketingAssistant   v-if="currentPage === 'marketing'"   @alert="showAlert" />
        <BusinessAssistant    v-if="currentPage === 'businessAi'"  @alert="showAlert" />
        <CommercialMvp       v-if="currentPage === 'commercialMvp'" @alert="showAlert" />
        <ScaleUpPlatform     v-if="currentPage === 'scaleUp'" @alert="showAlert" />
        <ProductionManagement v-if="currentPage === 'production'"  @alert="showAlert" />
        <ArtworkMarketplace   v-if="currentPage === 'marketplace'" @alert="showAlert" />
        <CreativeOrderCenter  v-if="currentPage === 'orders'"      @alert="showAlert" />
        <LogisticsTracking    v-if="currentPage === 'logistics'"  @alert="showAlert" />
        <WarehouseManagement v-if="currentPage === 'warehouse'"  @alert="showAlert" />
        <DesignerCenter       v-if="currentPage === 'designers'"   @alert="showAlert" />
        <UserManagement       v-if="currentPage === 'users'"       @alert="showAlert" />
      </main>
    </div>
  </div>

  <GlobalAlert :msg="alertMsg" :type="alertType" :visible="alertVisible" />
  <AiChat v-if="currentUser" />
</template>

<style>
/* Layout shell */
.app-shell {
  display: flex;
  min-height: 100vh;
}

.app-body {
  flex: 1;
  margin-left: var(--sidebar-w);
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  transition: margin-left .2s ease;
  min-width: 0;
}

.app-shell.collapsed .app-body {
  margin-left: var(--sidebar-collapsed-w);
}

/* Header */
.app-header {
  position: sticky;
  top: 0;
  z-index: 40;
  height: var(--header-h);
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toggle-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--c-border);
  border-radius: var(--r);
  background: var(--c-surface);
  color: var(--c-text-2);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all .15s;
  flex-shrink: 0;
}
.toggle-btn:hover { background: var(--c-bg); color: var(--c-text); }

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.bc-root  { color: var(--c-text-3); }
.bc-root svg { color: var(--c-text-3); }
.bc-current { color: var(--c-text); font-weight: 500; }


.header-center-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  max-width: min(520px, 42vw);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--c-text);
  font-size: 15px;
  font-weight: 800;
  letter-spacing: .04em;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--c-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text);
}

/* Main content */
.app-main {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
