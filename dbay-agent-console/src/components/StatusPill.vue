<template>
  <span class="status-pill" :class="normalized">{{ label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status?: string | null
}>()

const normalized = computed(() => (props.status || 'unknown').toLowerCase())
const label = computed(() => {
  const status = props.status || 'UNKNOWN'
  const labels: Record<string, string> = {
    READY: '就绪',
    ACTIVE: '运行中',
    RUNNING: '运行中',
    SUCCEEDED: '成功',
    COMPLETED: '完成',
    FAILED: '失败',
    PENDING: '等待中',
    STARTING: '启动中',
    BLOCKED: '阻塞',
  }
  return labels[status.toUpperCase()] || status
})
</script>
