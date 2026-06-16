import { apiGet } from './client'

export type HealthStatus = {
  status: string
  service?: string
  groups?: string[]
}

export type KnowledgeBase = {
  id: string
  name: string
  description?: string | null
  status: string
  type?: string
  document_count?: number
  updated_at?: string
}

export type MemoryBase = {
  id: string
  name: string
  description?: string | null
  status: string
  scene?: string
  memory_count?: number
  trait_count?: number
  updated_at?: string
}

export type AgentTaskRun = {
  id: string
  goal?: string
  status?: string
  harnessId?: string
  branchCount?: number
  evidenceCount?: number
  createdAt?: string
}

export type DatalakeDataset = {
  id: string
  name: string
  status: string
  sourceType?: string
  rowCount?: number
  sizeBytes?: number
  createdAt?: string
}

export type DatalakeJob = {
  id: string
  name: string
  type?: string
  status: string
  createdAt?: string
  finishedAt?: string | null
}

export function getAgentHealth() {
  return apiGet<HealthStatus>('/health')
}

export function getLakebaseHealth() {
  return apiGet<HealthStatus>('/lakebase/health')
}

export function listKnowledgeBases() {
  return apiGet<KnowledgeBase[]>('/knowledge/bases')
}

export function listMemoryBases() {
  return apiGet<MemoryBase[]>('/memory/bases')
}

export function listAgentTasks() {
  return apiGet<AgentTaskRun[]>('/agent-state/task-runs')
}

export function listDatalakeDatasets() {
  return apiGet<DatalakeDataset[]>('/datalake/datasets')
}

export function listDatalakeJobs() {
  return apiGet<DatalakeJob[]>('/datalake/jobs')
}
