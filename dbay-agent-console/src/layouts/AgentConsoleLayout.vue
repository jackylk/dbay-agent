<template>
  <div class="console-layout">
    <header class="console-header">
      <div class="brand">
        <router-link to="/overview" class="brand-name">DBay</router-link>
        <span class="brand-tag">DataAgent</span>
      </div>

      <div class="header-status">
        <span :class="['health-dot', healthState]"></span>
        <span>{{ healthText }}</span>
      </div>
    </header>

    <div class="console-body">
      <aside class="sidebar">
        <nav class="workspace-rail" aria-label="一级工作区">
          <router-link
            v-for="mode in modes"
            :key="mode.id"
            :to="mode.to"
            class="rail-item"
            :class="{ active: activeMode.id === mode.id }"
          >
            <span class="rail-icon" aria-hidden="true">{{ mode.mark }}</span>
            <span class="rail-label">{{ mode.shortLabel }}</span>
          </router-link>
        </nav>

        <nav class="sidebar-nav" :aria-label="activeMode.label">
          <div class="side-title">
            <span>{{ activeMode.label }}</span>
            <small>{{ activeMode.description }}</small>
          </div>

          <div v-for="group in activeMode.groups" :key="group.title" class="nav-group">
            <div class="nav-group-title">{{ group.title }}</div>
            <router-link
              v-for="item in group.items"
              :key="item.to"
              :to="item.to"
              class="nav-item"
            >
              <span class="nav-marker" aria-hidden="true"></span>
              {{ item.label }}
            </router-link>
          </div>
        </nav>
      </aside>

      <main class="console-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getAgentHealth } from '../api/modules'

type NavItem = { label: string; to: string }
type NavGroup = { title: string; items: NavItem[] }
type Mode = {
  id: 'foundation' | 'semantic' | 'agent' | 'analysis'
  label: string
  shortLabel: string
  mark: string
  description: string
  to: string
  match: string[]
  groups: NavGroup[]
}

const route = useRoute()
const healthState = ref<'checking' | 'up' | 'down'>('checking')

const modes: Mode[] = [
  {
    id: 'foundation',
    label: '数据底座',
    shortLabel: '底座',
    mark: '▦',
    description: '外部数据源与湖仓入口',
    to: '/sources',
    match: ['/sources'],
    groups: [
      {
        title: '数据接入',
        items: [
          { label: '数据源', to: '/sources' },
        ],
      },
    ],
  },
  {
    id: 'semantic',
    label: '语义层',
    shortLabel: '语义',
    mark: '◫',
    description: '知识库与记忆库',
    to: '/knowledge',
    match: ['/knowledge', '/memory'],
    groups: [
      {
        title: '知识',
        items: [
          { label: '知识库', to: '/knowledge' },
        ],
      },
      {
        title: '记忆',
        items: [
          { label: '记忆库', to: '/memory' },
        ],
      },
    ],
  },
  {
    id: 'agent',
    label: '应用层',
    shortLabel: 'Agent',
    mark: '◎',
    description: 'DataAgent 任务与证据',
    to: '/agent',
    match: ['/agent', '/overview'],
    groups: [
      {
        title: 'DataAgent',
        items: [
          { label: '总览', to: '/overview' },
          { label: '任务运行', to: '/agent' },
        ],
      },
    ],
  },
  {
    id: 'analysis',
    label: '数据分析',
    shortLabel: '分析',
    mark: '⌁',
    description: 'Datalake、Ray 与 Notebook',
    to: '/datalake',
    match: ['/datalake'],
    groups: [
      {
        title: 'Datalake',
        items: [
          { label: '数据湖', to: '/datalake' },
        ],
      },
    ],
  },
]

const activeMode = computed(() => {
  return modes.find((mode) => mode.match.some((prefix) => route.path.startsWith(prefix))) || modes[2]
})

const healthText = computed(() => {
  if (healthState.value === 'up') return 'API 已连接'
  if (healthState.value === 'down') return 'API 连接失败'
  return '正在检查 API'
})

onMounted(async () => {
  const result = await getAgentHealth()
  healthState.value = result.data?.status === 'UP' ? 'up' : 'down'
})
</script>
