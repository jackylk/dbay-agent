<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">Datalake</h1>
        <p class="page-subtitle">Ray、Notebook 与批处理 worker 使用 CCI 弹性资源；CCE 只承载控制入口。</p>
      </div>
      <button class="btn btn-primary" disabled>提交作业</button>
    </div>

    <div class="kpi-grid">
      <div class="kpi"><span>数据集</span><strong>{{ datasets.length }}</strong></div>
      <div class="kpi"><span>作业</span><strong>{{ jobs.length }}</strong></div>
      <div class="kpi"><span>运行中</span><strong>{{ runningJobs }}</strong></div>
      <div class="kpi"><span>执行资源</span><strong>CCI</strong></div>
    </div>

    <ModulePanel title="数据集" description="数据库导出、作业产出和外部文件注册后的数据资产。">
      <div v-if="datasetsState.loading.value" class="loading">加载中...</div>
      <div v-else-if="datasetsState.error.value">
        <EmptyState title="Datalake 数据集 API 暂不可用" :description="datasetsState.error.value" />
      </div>
      <div v-else-if="!datasets.length">
        <EmptyState title="还没有数据集" description="后续会恢复数据集详情、版本和导入流程。" />
      </div>
      <table v-else class="data-table">
        <thead><tr><th>名称</th><th>来源</th><th>行数</th><th>大小</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="dataset in datasets" :key="dataset.id">
            <td><strong>{{ dataset.name }}</strong><small>{{ dataset.id }}</small></td>
            <td>{{ dataset.sourceType || '-' }}</td>
            <td>{{ dataset.rowCount ?? '-' }}</td>
            <td>{{ formatSize(dataset.sizeBytes) }}</td>
            <td><StatusPill :status="dataset.status" /></td>
          </tr>
        </tbody>
      </table>
    </ModulePanel>

    <ModulePanel title="作业" description="Ray、Python 和 Notebook 背景作业。">
      <div v-if="jobsState.loading.value" class="loading">加载中...</div>
      <div v-else-if="jobsState.error.value">
        <EmptyState title="Datalake 作业 API 暂不可用" :description="jobsState.error.value" />
      </div>
      <div v-else-if="!jobs.length">
        <EmptyState title="还没有作业" description="后续会恢复作业提交、日志和重跑。" />
      </div>
      <table v-else class="data-table">
        <thead><tr><th>名称</th><th>类型</th><th>状态</th><th>创建时间</th></tr></thead>
        <tbody>
          <tr v-for="job in jobs" :key="job.id">
            <td><strong>{{ job.name }}</strong><small>{{ job.id }}</small></td>
            <td>{{ job.type || '-' }}</td>
            <td><StatusPill :status="job.status" /></td>
            <td>{{ formatTime(job.createdAt) }}</td>
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
import { listDatalakeDatasets, listDatalakeJobs } from '../api/modules'
import { useAsyncData } from '../composables/useAsyncData'
import { formatSize, formatTime } from '../utils/format'

const datasetsState = useAsyncData(listDatalakeDatasets)
const jobsState = useAsyncData(listDatalakeJobs)
const datasets = computed(() => datasetsState.data.value || [])
const jobs = computed(() => jobsState.data.value || [])
const runningJobs = computed(() => jobs.value.filter((job) => job.status === 'RUNNING').length)
</script>
