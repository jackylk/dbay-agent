import { onMounted, ref } from 'vue'
import type { ApiResult } from '../api/client'

export function useAsyncData<T>(loader: () => Promise<ApiResult<T>>) {
  const data = ref<T | null>(null)
  const loading = ref(true)
  const error = ref<string | null>(null)
  const status = ref<number>(0)

  async function refresh() {
    loading.value = true
    const result = await loader()
    data.value = result.data
    error.value = result.error
    status.value = result.status
    loading.value = false
  }

  onMounted(refresh)

  return { data, loading, error, status, refresh }
}
