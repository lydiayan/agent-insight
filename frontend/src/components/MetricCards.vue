<script setup lang="ts">
import type { DashboardSummary } from '../api/dashboard'

const props = defineProps<{ summary: DashboardSummary | null }>()

const cards = () => {
  const s = props.summary
  if (!s) return []
  return [
    { label: '请求量', key: 'requestCount' as const, icon: '📊', color: '#6366f1', bg: '#eef2ff' },
    { label: '平均耗时', key: 'avgLatency' as const, icon: '⏱️', color: '#3b82f6', bg: '#eff6ff' },
    { label: 'Tool 成功率', key: 'toolSuccessRate' as const, icon: '✅', color: '#8b5cf6', bg: '#f5f3ff' },
    { label: 'RAG 命中率', key: 'ragHitRate' as const, icon: '🎯', color: '#f59e0b', bg: '#fffbeb' },
    { label: '平均 Token', key: 'avgTokens' as const, icon: '🔤', color: '#06b6d4', bg: '#ecfeff' },
    { label: '评测通过率', key: 'evalPassRate' as const, icon: '⭐', color: '#10b981', bg: '#ecfdf5' },
  ]
}

const fmt = (v: number) => {
  if (v >= 10000) return (v / 10000).toFixed(1) + '万'
  if (v % 1 !== 0) return v.toFixed(1)
  return v.toLocaleString()
}
</script>

<template>
  <div class="metric-grid">
    <div v-for="c in cards()" :key="c.key" class="card card-hover metric-card">
      <div class="card-top">
        <div class="icon-box" :style="{ background: c.bg, color: c.color }">
          {{ c.icon }}
        </div>
        <div
          v-if="summary?.[c.key]?.changePercent != null"
          :class="['trend-badge', summary![c.key].trend === 'up' ? 'up' : 'down']"
        >
          <span class="arrow">{{ summary![c.key].trend === 'up' ? '↑' : '↓' }}</span>
          {{ Math.abs(summary![c.key].changePercent!).toFixed(1) }}%
        </div>
      </div>
      <div class="card-label">{{ c.label }}</div>
      <div class="card-value">{{ fmt(summary?.[c.key]?.value ?? 0) }}
        <span class="unit">{{ summary?.[c.key]?.unit ?? '' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
@media (max-width: 1200px) {
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 768px) {
  .metric-grid { grid-template-columns: 1fr; }
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.icon-box {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}
.trend-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 2px;
}
.trend-badge.up { background: #ecfdf5; color: #059669; }
.trend-badge.down { background: #fef2f2; color: #dc2626; }
.arrow { font-size: 14px; }

.card-label {
  font-size: 13px;
  color: #6b7280;
  margin-top: 4px;
}
.card-value {
  font-size: 32px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}
.unit {
  font-size: 14px;
  font-weight: 400;
  color: #9ca3af;
  margin-left: 2px;
}
</style>
