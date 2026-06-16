import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('../layouts/AgentConsoleLayout.vue'),
      children: [
        { path: '', redirect: '/overview' },
        { path: 'overview', name: 'Overview', component: () => import('../views/OverviewView.vue') },
        { path: 'knowledge', name: 'Knowledge', component: () => import('../views/KnowledgeView.vue') },
        { path: 'memory', name: 'Memory', component: () => import('../views/MemoryView.vue') },
        { path: 'agent', name: 'DataAgent', component: () => import('../views/DataAgentView.vue') },
        { path: 'sources', name: 'DataSources', component: () => import('../views/DataSourcesView.vue') },
        { path: 'datalake', name: 'Datalake', component: () => import('../views/DatalakeView.vue') },
      ],
    },
  ],
})

router.onError((error, to) => {
  if (
    error.message.includes('Failed to fetch dynamically imported module') ||
    error.message.includes('Importing a module script failed') ||
    error.message.includes('Loading chunk')
  ) {
    const key = 'dbay_agent_last_chunk_reload'
    const last = sessionStorage.getItem(key)
    const now = Date.now()
    if (!last || now - Number(last) > 10000) {
      sessionStorage.setItem(key, String(now))
      window.location.assign(to.fullPath)
    }
  }
})

export default router
