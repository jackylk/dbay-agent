<template>
  <main class="shell">
    <section class="panel">
      <p class="eyebrow">DBay Agent</p>
      <h1>DataAgent 工作台</h1>
      <p>Sources、Knowledge、Memory 和 Datalake 将在这里接入 Lakebase。</p>
      <div class="status">
        <span :class="['dot', apiStatus]"></span>
        <span>{{ statusText }}</span>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type ApiStatus = 'checking' | 'up' | 'down'

const apiStatus = ref<ApiStatus>('checking')
const apiBaseUrl = import.meta.env.VITE_DBAY_AGENT_API_BASE_URL ?? '/agent-api'

const statusText = computed(() => {
  if (!apiBaseUrl) return '未配置 API 地址'
  if (apiStatus.value === 'up') return `API 已连接：${apiBaseUrl}`
  if (apiStatus.value === 'down') return `API 连接失败：${apiBaseUrl}`
  return `正在连接 API：${apiBaseUrl}`
})

onMounted(async () => {
  if (!apiBaseUrl) {
    apiStatus.value = 'down'
    return
  }
  try {
    const response = await fetch(`${apiBaseUrl.replace(/\/$/, '')}/api/v1/health`)
    apiStatus.value = response.ok ? 'up' : 'down'
  } catch {
    apiStatus.value = 'down'
  }
})
</script>

<style scoped>
.shell {
  min-height: 100vh;
  background: #faf8f5;
  color: #2c3e50;
  display: grid;
  place-items: center;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

.panel {
  width: min(720px, calc(100vw - 48px));
  border: 1px solid #e8e4df;
  background: #fff;
  padding: 32px;
}

.eyebrow {
  color: #9a5b25;
  font-size: 13px;
  font-weight: 700;
}

h1 {
  margin: 0 0 12px;
  font-size: 32px;
}

.status {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #5b6472;
  font-size: 14px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #c0a98f;
}

.dot.up {
  background: #2f8f5b;
}

.dot.down {
  background: #b34d3f;
}
</style>
