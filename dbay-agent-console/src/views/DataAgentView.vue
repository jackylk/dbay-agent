<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">DataAgent</h1>
        <p class="page-subtitle">任务运行、分支、证据包和策略审计会集中在这里。</p>
      </div>
    </div>

    <div class="kpi-grid">
      <div class="kpi"><span>任务</span><strong>{{ tasks.length }}</strong></div>
      <div class="kpi"><span>运行中</span><strong>{{ runningCount }}</strong></div>
      <div class="kpi"><span>证据</span><strong>{{ evidenceCount }}</strong></div>
      <div class="kpi"><span>分支</span><strong>{{ branchCount }}</strong></div>
    </div>

    <ModulePanel title="任务运行" description="从原智能体数据平台迁移过来的任务列表入口。">
      <div v-if="state.loading.value" class="loading">加载中...</div>
      <div v-else-if="state.error.value">
        <EmptyState title="DataAgent API 尚未接入 dbay-agent" :description="state.error.value" />
      </div>
      <div v-else-if="!tasks.length">
        <EmptyState title="还没有任务运行" description="后续会恢复任务详情、分支图、证据包和审计事件页面。" />
      </div>
      <table v-else class="data-table">
        <thead>
          <tr><th>任务</th><th>Harness</th><th>分支</th><th>证据</th><th>状态</th><th>创建时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="task in tasks" :key="task.id">
            <td><strong>{{ task.goal || task.id }}</strong><small>{{ task.id }}</small></td>
            <td>{{ task.harnessId || '-' }}</td>
            <td>{{ task.branchCount ?? 0 }}</td>
            <td>{{ task.evidenceCount ?? 0 }}</td>
            <td><StatusPill :status="task.status" /></td>
            <td>{{ formatTime(task.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </ModulePanel>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import ModulePanel from '../components/ModulePanel.vue'
import StatusPill from '../components/StatusPill.vue'
import { listAgentTasks } from '../api/modules'
import { useAsyncData } from '../composables/useAsyncData'
import { formatTime } from '../utils/format'

const state = useAsyncData(listAgentTasks)
const tasks = computed(() => state.data.value || [])
const runningCount = computed(() => tasks.value.filter((task) => task.status === 'RUNNING' || task.status === 'running').length)
const evidenceCount = computed(() => tasks.value.reduce((total, task) => total + (task.evidenceCount || 0), 0))
const branchCount = computed(() => tasks.value.reduce((total, task) => total + (task.branchCount || 0), 0))
</script>
