<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">记忆库</h1>
        <p class="page-subtitle">保存长期记忆、反思洞察和用户模式，支持 DataAgent 跨会话学习。</p>
      </div>
      <button class="btn btn-primary" disabled>创建记忆库</button>
    </div>

    <ModulePanel title="记忆构建流程" description="对话产生消息，后台提取记忆、生成 trait，并在后续任务中召回。">
      <div class="flow-row memory">
        <span>对话</span><i></i><span>提取</span><i></i><span>反思</span><i></i><span>召回</span>
      </div>
    </ModulePanel>

    <ModulePanel title="记忆库列表" description="沿用原 DBay console 的记忆库入口。">
      <div v-if="state.loading.value" class="loading">加载中...</div>
      <div v-else-if="state.error.value">
        <EmptyState title="记忆库 API 尚未接入 dbay-agent" :description="state.error.value" />
      </div>
      <div v-else-if="!state.data.value?.length">
        <EmptyState title="还没有记忆库" description="后续迁移会恢复记忆浏览、反思洞察、图谱和 digest 操作。" />
      </div>
      <table v-else class="data-table">
        <thead>
          <tr><th>名称</th><th>场景</th><th>记忆</th><th>洞察</th><th>状态</th></tr>
        </thead>
        <tbody>
          <tr v-for="memory in state.data.value" :key="memory.id">
            <td><strong>{{ memory.name }}</strong><small>{{ memory.description || memory.id }}</small></td>
            <td>{{ sceneLabel(memory.scene) }}</td>
            <td>{{ memory.memory_count ?? 0 }}</td>
            <td>{{ memory.trait_count ?? 0 }}</td>
            <td><StatusPill :status="memory.status" /></td>
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
import { listMemoryBases } from '../api/modules'
import { useAsyncData } from '../composables/useAsyncData'

const state = useAsyncData(listMemoryBases)

function sceneLabel(scene?: string) {
  if (scene === 'DEVELOPER_TOOL') return '开发者工具'
  if (scene === 'CHAT_ASSISTANT') return '对话助理'
  return scene || '-'
}
</script>
