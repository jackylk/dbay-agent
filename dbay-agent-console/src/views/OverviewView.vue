<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">DataAgent 工作台</h1>
        <p class="page-subtitle">面向数据智能体的语义层、应用层和分析层控制台；Lakebase 数据库与 FS 继续在 dbay.cloud 管理。</p>
      </div>
    </div>

    <div class="overview-grid">
      <ModulePanel title="数据源" description="注册外部数据库、数据湖、文件和 API，为 DataAgent 提供输入。">
        <router-link class="panel-link" to="/sources">进入数据源</router-link>
      </ModulePanel>
      <ModulePanel title="Knowledge" description="把项目文档、网页和结构化资料整理成 Agent 可读的 wiki。">
        <router-link class="panel-link" to="/knowledge">进入知识库</router-link>
      </ModulePanel>
      <ModulePanel title="Memory" description="沉淀长期记忆、用户偏好和反思洞察，供多个 Agent 共享。">
        <router-link class="panel-link" to="/memory">进入记忆库</router-link>
      </ModulePanel>
      <ModulePanel title="DataAgent" description="查看任务运行、分支、证据包和策略审计。">
        <router-link class="panel-link" to="/agent">进入任务运行</router-link>
      </ModulePanel>
      <ModulePanel title="Datalake" description="Ray、Notebook 和批处理作业使用 CCI 弹性资源运行。">
        <router-link class="panel-link" to="/datalake">进入数据湖</router-link>
      </ModulePanel>
    </div>

    <ModulePanel title="运行状态" description="dbay-agent API 与它依赖的 Lakebase API。">
      <div class="health-grid">
        <div class="health-row">
          <span>dbay-agent API</span>
          <StatusPill :status="agentHealth.data.value?.status || (agentHealth.loading.value ? 'PENDING' : 'FAILED')" />
        </div>
        <div class="health-row">
          <span>Lakebase API</span>
          <StatusPill :status="lakebaseHealth.data.value?.status || (lakebaseHealth.loading.value ? 'PENDING' : 'FAILED')" />
        </div>
      </div>
    </ModulePanel>
  </div>
</template>

<script setup lang="ts">
import ModulePanel from '../components/ModulePanel.vue'
import StatusPill from '../components/StatusPill.vue'
import { getAgentHealth, getLakebaseHealth } from '../api/modules'
import { useAsyncData } from '../composables/useAsyncData'

const agentHealth = useAsyncData(getAgentHealth)
const lakebaseHealth = useAsyncData(getLakebaseHealth)
</script>
