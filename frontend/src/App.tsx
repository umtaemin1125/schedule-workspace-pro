import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from './components/LoginPage'
import { AppShell } from './components/AppShell'
import { useAuthStore } from './store/auth'
import { api } from './lib/api'

export function App() {
  const token = useAuthStore((s) => s.accessToken)
  const initialized = useAuthStore((s) => s.initialized)
  const setToken = useAuthStore((s) => s.setAccessToken)
  const setInitialized = useAuthStore((s) => s.setInitialized)

  useEffect(() => {
    const init = async () => {
      try {
        const csrf = document.cookie.split('; ').find((v) => v.startsWith('csrf_token='))?.split('=')[1]
        const res = await api.post('/api/auth/refresh', null, { headers: { 'X-CSRF-TOKEN': csrf ?? '' } })
        setToken(res.data.accessToken)
      } catch {
        setToken(null)
      } finally {
        setInitialized(true)
      }
    }
    init()
  }, [setInitialized, setToken])

  if (!initialized) return <div className="p-6">초기화 중...</div>

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={token ? <AppShell /> : <Navigate to="/login" replace />} />
    </Routes>
  )
}
