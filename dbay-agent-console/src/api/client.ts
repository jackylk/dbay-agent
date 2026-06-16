export type ApiResult<T> = {
  data: T | null
  error: string | null
  status: number
}

const API_BASE = '/agent-api/api/v1'

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('dbay_agent_api_key') || localStorage.getItem('lakeon_api_key')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function apiGet<T>(path: string): Promise<ApiResult<T>> {
  try {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      ...authHeaders(),
    }
    const response = await fetch(`${API_BASE}${path}`, {
      headers,
    })
    if (!response.ok) {
      return { data: null, error: await errorMessage(response), status: response.status }
    }
    return { data: await response.json() as T, error: null, status: response.status }
  } catch (error) {
    return { data: null, error: error instanceof Error ? error.message : 'Network error', status: 0 }
  }
}

export async function apiPost<T>(path: string, body: unknown): Promise<ApiResult<T>> {
  try {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...authHeaders(),
    }
    const response = await fetch(`${API_BASE}${path}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    })
    if (!response.ok) {
      return { data: null, error: await errorMessage(response), status: response.status }
    }
    return { data: await response.json() as T, error: null, status: response.status }
  } catch (error) {
    return { data: null, error: error instanceof Error ? error.message : 'Network error', status: 0 }
  }
}

async function errorMessage(response: Response) {
  try {
    const json = await response.json()
    return json?.error?.message || json?.message || response.statusText
  } catch {
    return response.statusText
  }
}
