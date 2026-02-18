import axios from 'axios'
import { useAuthStore } from '../store/auth'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  withCredentials: true
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let refreshing = false
let queued: Array<() => void> = []

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true
      if (!refreshing) {
        refreshing = true
        try {
          const csrf = document.cookie.split('; ').find((v) => v.startsWith('csrf_token='))?.split('=')[1]
          const res = await api.post('/api/auth/refresh', null, { headers: { 'X-CSRF-TOKEN': csrf ?? '' } })
          useAuthStore.getState().setAccessToken(res.data.accessToken)
          queued.forEach((cb) => cb())
          queued = []
        } finally {
          refreshing = false
        }
      }
      await new Promise<void>((resolve) => queued.push(resolve))
      return api(original)
    }
    return Promise.reject(error)
  }
)
