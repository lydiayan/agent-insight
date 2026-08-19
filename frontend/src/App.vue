<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const timeRange = ref('24h')
const sidebarCollapsed = ref(false)
const mobileSidebarOpen = ref(false)

const isDashboard = computed(() => route.name === 'dashboard')

const navItems = [
  { path: '/', icon: '📊', label: '概览大盘' },
  { path: '/online-quality', icon: '📡', label: '线上质量' },
  { path: '/cases', icon: '📋', label: '测试用例' },
  { path: '/eval', icon: '🧪', label: '评测任务' },
]

const onRangeChange = (val: string) => {
  timeRange.value = val
}

const toggleSidebar = () => {
  if (window.matchMedia('(max-width: 760px)').matches) {
    mobileSidebarOpen.value = !mobileSidebarOpen.value
    return
  }
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const closeMobileSidebar = () => {
  mobileSidebarOpen.value = false
}
</script>

<template>
  <div class="app-shell">
    <!-- 左侧侧边栏 -->
    <button
      v-if="mobileSidebarOpen"
      class="sidebar-backdrop"
      type="button"
      aria-label="关闭导航菜单"
      @click="closeMobileSidebar"
    />
    <aside :class="['sidebar', { collapsed: sidebarCollapsed, 'mobile-open': mobileSidebarOpen }]">
      <div class="sidebar-brand" @click="toggleSidebar">
        <div class="logo">🤖</div>
        <span v-show="!sidebarCollapsed || mobileSidebarOpen" class="brand-text">AgentInsight</span>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="sidebar-item"
          active-class="sidebar-active"
          @click="closeMobileSidebar"
        >
          <span class="sidebar-icon">{{ item.icon }}</span>
          <span v-show="!sidebarCollapsed || mobileSidebarOpen" class="sidebar-label">{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>

    <!-- 右侧主体区域 -->
    <div class="main-area">
      <!-- 顶部栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <button class="collapse-btn" type="button" aria-label="导航菜单" title="导航菜单" @click="toggleSidebar">
            ☰
          </button>
        </div>

        <div class="topbar-right">
          <div v-if="isDashboard" class="time-tabs">
            <button
              v-for="r in [
                { k: '24h', label: '近24小时' },
                { k: '7d', label: '近7日' },
                { k: '30d', label: '近30日' },
              ]"
              :key="r.k"
              :class="['tab', { active: timeRange === r.k }]"
              @click="onRangeChange(r.k)"
            >
              {{ r.label }}
            </button>
          </div>
          <div class="user-info">管理员</div>
        </div>
      </header>

      <!-- 页面内容 -->
      <main>
        <router-view :time-range="timeRange" />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  background: #f0f2f5;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 200px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  transition: width 0.2s;
  position: sticky;
  top: 0;
  height: 100vh;
  z-index: 200;
}
.sidebar.collapsed { width: 60px; }
.sidebar-backdrop { display: none; }

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 16px 20px;
  cursor: pointer;
  user-select: none;
}
.logo {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}
.brand-text {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
}

.sidebar-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 8px;
}
.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #4b5563;
  text-decoration: none;
  transition: all 0.15s;
  white-space: nowrap;
}
.sidebar-item:hover { background: #f3f4f6; }
.sidebar-active {
  background: #eef2ff;
  color: #6366f1;
}
.sidebar-icon { font-size: 18px; flex-shrink: 0; }
.sidebar-label { overflow: hidden; }

/* ===== 右侧主体 ===== */
.main-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 100;
}
.collapse-btn {
  border: none;
  background: none;
  font-size: 18px;
  cursor: pointer;
  color: #6b7280;
  padding: 4px 8px;
  border-radius: 6px;
}
.collapse-btn:hover { background: #f3f4f6; color: #1f2937; }

.topbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.time-tabs {
  display: flex;
  background: #f3f4f6;
  border-radius: 8px;
  padding: 3px;
}
.tab {
  border: none;
  background: none;
  padding: 5px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s;
}
.tab.active {
  background: #fff;
  color: #6366f1;
  box-shadow: 0 1px 2px rgba(0,0,0,.06);
}
.tab:hover:not(.active) { color: #374151; }

.user-info {
  font-size: 14px;
  color: #374151;
  font-weight: 500;
}

main {
  flex: 1;
  max-width: 1440px;
  width: 100%;
  margin: 0 auto;
  padding: 24px;
}

@media (max-width: 760px) {
  .sidebar,
  .sidebar.collapsed {
    position: fixed;
    left: 0;
    width: 200px;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
    box-shadow: 8px 0 24px rgba(15, 23, 42, 0.12);
  }
  .sidebar.mobile-open { transform: translateX(0); }
  .sidebar-backdrop {
    display: block;
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    left: 200px;
    z-index: 150;
    border: 0;
    background: rgba(15, 23, 42, 0.28);
  }
  .topbar { padding: 0 16px; }
  main { padding: 16px; }
}
</style>
