<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">知识库</h1>
        <p class="page-subtitle">从源文档生成 wiki，并为 DataAgent 提供可引用、可维护的知识层。</p>
      </div>
      <button class="btn btn-primary" disabled>创建知识库</button>
    </div>

    <ModulePanel title="知识构建流程" description="导入源文档，后台生成 wiki、图谱与可检索条目。">
      <div class="flow-row">
        <span>导入</span><i></i><span>Wiki</span><i></i><span>对话</span><i></i><span>沉淀</span>
      </div>
    </ModulePanel>

    <ModulePanel title="知识库列表" description="沿用原 DBay console 的知识库管理入口。">
      <div v-if="state.loading.value" class="loading">加载中...</div>
      <div v-else-if="state.error.value">
        <EmptyState title="知识库 API 尚未接入 dbay-agent" :description="state.error.value" />
      </div>
      <div v-else-if="!state.data.value?.length">
        <EmptyState title="还没有知识库" description="后续迁移会恢复创建、上传、wiki、图谱和对话页面。" />
      </div>
      <table v-else class="data-table">
        <thead>
          <tr><th>名称</th><th>类型</th><th>文档</th><th>状态</th><th>更新时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="kb in state.data.value" :key="kb.id">
            <td><strong>{{ kb.name }}</strong><small>{{ kb.description || kb.id }}</small></td>
            <td>{{ kb.type || 'DOCUMENT' }}</td>
            <td>{{ kb.document_count ?? 0 }}</td>
            <td><StatusPill :status="kb.status" /></td>
            <td>{{ formatTime(kb.updated_at) }}</td>
          </tr>
        </tbody>
      </table>
    </ModulePanel>
  </div>
</template>

<script setup lang="ts">
import EmptyState from '../components/EmptyState.vue'
import ModulePanel from '../components/ModulePanel.vue'
import StatusPill from '../components/StatusPill.vue'
import { listKnowledgeBases } from '../api/modules'
import { useAsyncData } from '../composables/useAsyncData'
import { formatTime } from '../utils/format'

const state = useAsyncData(listKnowledgeBases)
</script>
