<script setup lang="ts">
import type { AgentRankingItem } from '../api/dashboard'

defineProps<{ items: AgentRankingItem[] }>()

const rankClass = (rank: number) => {
  if (rank === 1) return 'r1'
  if (rank === 2) return 'r2'
  if (rank === 3) return 'r3'
  return 'ro'
}

const fmt = (v: number) => v >= 10000 ? (v / 10000).toFixed(1) + '万' : v.toLocaleString()
</script>

<template>
  <div class="card ranking-card">
    <h3 class="section-title">Agent 调用排行</h3>
    <div v-if="items.length === 0" class="empty">暂无数据</div>
    <div v-else class="rank-list">
      <div
        v-for="item in items"
        :key="item.rank"
        class="rank-row"
      >
        <span :class="['rank-badge', rankClass(item.rank)]">
          <template v-if="item.rank <= 3">#{{ item.rank }}</template>
          <template v-else>{{ item.rank }}</template>
        </span>
        <div class="rank-info">
          <span class="agent-name">{{ item.agentName }}</span>
          <span class="agent-meta">
            <span>{{ item.successRate.toFixed(1) }}%</span>
            <span class="sep">·</span>
            <span>{{ item.avgLatency.toFixed(0) }}ms</span>
          </span>
        </div>
        <div class="rank-count">
          <span class="count-num">{{ fmt(item.requestCount) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ranking-card {
  height: 420px;
  display: flex;
  flex-direction: column;
}
.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 14px;
}
.rank-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
}
.rank-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
  border-radius: 8px;
  transition: background .15s;
}
.rank-row:hover {
  background: #f9fafb;
}
.rank-badge {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}
.r1 { background: #fef3c7; color: #d97706; }
.r2 { background: #f1f5f9; color: #64748b; }
.r3 { background: #fce7d7; color: #c2410c; }
.ro { background: #fafafa; color: #9ca3af; }

.rank-info {
  flex: 1;
  min-width: 0;
}
.agent-name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.agent-meta {
  display: block;
  font-size: 11px;
  color: #9ca3af;
  margin-top: 2px;
}
.sep {
  margin: 0 6px;
}
.count-num {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}
</style>
