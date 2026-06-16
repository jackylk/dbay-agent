<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">数据源</h1>
        <p class="page-subtitle">这是 DataAgent 的输入层：外部数据库、对象存储、文件目录和 API 会逐步在这里注册。</p>
      </div>
      <button class="btn btn-primary" disabled>注册数据源</button>
    </div>

    <div class="source-grid">
      <ModulePanel title="外部数据库" description="PostgreSQL、MySQL、数据仓库等结构化来源。">
        <EmptyState title="即将接入" description="迁移后作为 DataAgent 可读取和写入的外部资产。" />
      </ModulePanel>
      <ModulePanel title="文件与对象存储" description="本地文件、OBS/S3 Bucket、多模态素材目录。">
        <EmptyState title="即将接入" description="文档、图片、音频和视频会被加工后写入 Knowledge、Memory 或 Datalake。" />
      </ModulePanel>
      <ModulePanel title="DBay 资产" description="来自 dbay.cloud 的 Lakebase 数据库、FS 和数据湖资产。">
        <EmptyState title="依赖 Lakebase API" description="通过 dbay-agent API 读取 dbay.cloud 的资产目录，不直接访问 Lakebase RDS。" />
      </ModulePanel>
    </div>
  </div>
</template>

<script setup lang="ts">
import EmptyState from '../components/EmptyState.vue'
import ModulePanel from '../components/ModulePanel.vue'
</script>
