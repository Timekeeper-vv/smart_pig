<script setup lang="ts">
import { computed } from 'vue'
import type { User, PageName, Role } from '../types'
import andTasteLogo from '../assets/and_taste.png'

interface MenuItem { key: PageName; label: string; roles: Role[]; icon: string }
interface MenuGroup { group: string; items: MenuItem[] }

const props = defineProps<{ currentUser: User; currentPage: PageName; collapsed: boolean }>()
const emit = defineEmits<{ 'switch-page': [page: PageName]; 'logout': []; 'toggle': [] }>()

const roleLabels: Record<Role, string> = { admin: '运营后台', technician: '设计师', feeder: '消费者' }
const roleColors: Record<Role, string> = { admin: '#f97316', technician: '#7c3aed', feeder: '#0d9488' }

const allMenus: MenuGroup[] = [
  { group: '总览', items: [
    { key: 'dashboard', label: '经营看板', roles: ['admin','technician','feeder'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>` },
  ]},
  { group: '第一步：接单报价', items: [
    { key: 'commercialMvp', label: '询盘到报价', roles: ['admin','technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3h18v18H3z"/><path d="M7 8h10"/><path d="M7 12h10"/><path d="M7 16h6"/></svg>` },
    { key: 'businessAi', label: '报价/物流助手', roles: ['admin','technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v20"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7H14a3.5 3.5 0 0 1 0 7H6"/></svg>` },
    { key: 'marketing', label: '营销文案', roles: ['admin','technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16v16H4z"/><path d="M8 8h8"/><path d="M8 12h8"/><path d="M8 16h5"/></svg>` },
  ]},
  { group: '第二步：设计生产', items: [
    { key: 'studio', label: 'AI设计工坊', roles: ['admin','technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l1.8 5.4L19 9l-5.2 1.6L12 16l-1.8-5.4L5 9l5.2-1.6L12 2z"/><path d="M19 15l.9 2.7L22 19l-2.1.7L19 22l-.9-2.3L16 19l2.1-1.3L19 15z"/></svg>` },
    { key: 'scaleUp', label: '项目制开发', roles: ['admin','technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19h16"/><path d="M7 16V8"/><path d="M12 16V5"/><path d="M17 16v-3"/></svg>` },
    { key: 'production', label: 'BOM/打样生产', roles: ['admin','technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 7h16"/><path d="M4 12h16"/><path d="M4 17h10"/><path d="M6 3v18"/><path d="M18 3v10"/></svg>` },
  ]},
  { group: '第三步：销售SaaS', items: [
    { key: 'marketplace', label: 'IP商城', roles: ['admin','technician','feeder'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>` },
    { key: 'orders', label: '订单履约', roles: ['admin','technician','feeder'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 2l1.5 4h9L18 2"/><path d="M3 6h18l-2 15H5L3 6z"/><path d="M9 11h6"/></svg>` },
    { key: 'logistics', label: '物流跟踪', roles: ['admin','technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7h11v10H3z"/><path d="M14 10h4l3 3v4h-7z"/><circle cx="7" cy="18" r="2"/><circle cx="18" cy="18" r="2"/></svg>` },
    { key: 'warehouse', label: '智能仓储', roles: ['admin','technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73L13 2.27a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><path d="M3.3 7L12 12l8.7-5"/><path d="M12 22V12"/></svg>` },
    { key: 'designers', label: '设计师/创作者', roles: ['admin','technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>` },
  ]},
  { group: '系统', items: [
    { key: 'users', label: '账号权限', roles: ['admin'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/></svg>` },
  ]},
]

const menus = computed<MenuGroup[]>(() => {
  const role: Role = props.currentUser?.role || 'admin'
  return allMenus.map(g => ({ ...g, items: g.items.filter(item => item.roles.includes(role)) })).filter(g => g.items.length > 0)
})
const currentRoleLabel = computed<string>(() => roleLabels[props.currentUser?.role] || '运营后台')
const currentRoleColor = computed<string>(() => roleColors[props.currentUser?.role] || '#f97316')
</script>

<template>
  <aside class="sidebar" :class="{ collapsed }">
    <!-- Logo -->
    <div class="sidebar-logo">
      <div class="logo-icon">
        <img :src="andTasteLogo" alt="之间味道 logo" />
      </div>
      <transition name="fade">
        <div v-if="!collapsed" class="logo-text">
          <span class="logo-title">之间智造</span>
          <span class="logo-sub">文创产品智能体平台</span>
        </div>
      </transition>
    </div>

    <!-- Navigation -->
    <nav class="sidebar-nav">
      <template v-for="menu in menus" :key="menu.group">
        <div class="nav-group-label" v-if="!collapsed">{{ menu.group }}</div>
        <div v-else class="nav-group-divider"></div>
        <button
          v-for="item in menu.items"
          :key="item.key"
          class="nav-item"
          :class="{ active: currentPage === item.key }"
          :title="collapsed ? item.label : ''"
          @click="emit('switch-page', item.key)"
        >
          <span class="nav-icon" v-html="item.icon"></span>
          <transition name="fade">
            <span v-if="!collapsed" class="nav-label">{{ item.label }}</span>
          </transition>
          <transition name="fade">
            <span v-if="!collapsed && currentPage === item.key" class="nav-active-bar"></span>
          </transition>
        </button>
      </template>
    </nav>

    <!-- Footer -->
    <div class="sidebar-footer">
      <div v-if="!collapsed" class="footer-user">
        <div class="footer-avatar">{{ currentUser.username?.[0]?.toUpperCase() }}</div>
        <div class="footer-info">
          <span class="footer-name">{{ currentUser.username }}</span>
          <span class="footer-role" :style="{ color: currentRoleColor }">{{ currentRoleLabel }}</span>
        </div>
      </div>
      <div v-else class="footer-avatar-only">
        <div class="footer-avatar">{{ currentUser.username?.[0]?.toUpperCase() }}</div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  width: var(--sidebar-w);
  height: 100vh;
  background: #0F172A;
  display: flex;
  flex-direction: column;
  z-index: 50;
  transition: width .2s ease;
  overflow: hidden;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-w);
}

/* Logo area */
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  height: var(--header-h);
  border-bottom: 1px solid rgba(255,255,255,.06);
  flex-shrink: 0;
}

.logo-icon {
  width: 32px;
  height: 32px;
  background: #fff;
  border-radius: var(--r);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.logo-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.logo-text {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  white-space: nowrap;
}

.logo-title {
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  line-height: 1.2;
}

.logo-sub {
  font-size: 11px;
  color: rgba(255,255,255,.35);
  letter-spacing: 0.5px;
}

/* Navigation */
.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px 0;
  scrollbar-width: thin;
  scrollbar-color: rgba(255,255,255,.08) transparent;
}

.sidebar-nav::-webkit-scrollbar { width: 3px; }
.sidebar-nav::-webkit-scrollbar-thumb { background: rgba(255,255,255,.08); }

.nav-group-label {
  padding: 12px 16px 4px;
  font-size: 10px;
  font-weight: 600;
  color: rgba(255,255,255,.25);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  white-space: nowrap;
}

.nav-group-divider {
  margin: 8px 12px;
  border-top: 1px solid rgba(255,255,255,.06);
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  height: 40px;
  width: 100%;
  border: none;
  background: transparent;
  color: rgba(255,255,255,.55);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font);
  cursor: pointer;
  transition: color .15s, background .15s;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
}

.nav-item:hover {
  color: rgba(255,255,255,.9);
  background: rgba(255,255,255,.05);
}

.nav-item.active {
  color: #fff;
  background: rgba(13,148,136,.2);
}

.nav-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
}

.nav-active-bar {
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  background: var(--c-primary);
  border-radius: 0 2px 2px 0;
}

.nav-label {
  flex: 1;
  white-space: nowrap;
}

/* Footer */
.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid rgba(255,255,255,.06);
  flex-shrink: 0;
}

.footer-user {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow: hidden;
}

.footer-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--c-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.footer-avatar-only {
  display: flex;
  justify-content: center;
}

.footer-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.footer-name {
  font-size: 13px;
  font-weight: 500;
  color: rgba(255,255,255,.85);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.footer-role {
  font-size: 11px;
  color: rgba(255,255,255,.3);
}

/* Transitions */
.fade-enter-active, .fade-leave-active { transition: opacity .15s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
