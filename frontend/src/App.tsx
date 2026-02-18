import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from './components/LoginPage'
import { RegisterPage } from './components/RegisterPage'
import { AppShell } from './components/AppShell'
import { AppLayout } from './components/AppLayout'
import { AdminDashboard } from './components/AdminDashboard'
import { useAuthStore } from './store/auth'
import { api } from './lib/api'

export function App() {
  const token = useAuthStore((s) => s.accessToken)
  const user = useAuthStore((s) => s.user)
  const initialized = useAuthStore((s) => s.initialized)
  const setToken = useAuthStore((s) => s.setAccessToken)
  const setUser = useAuthStore((s) => s.setUser)
  const setInitialized = useAuthStore((s) => s.setInitialized)

  useEffect(() => {
    const init = async () => {
      try {
        const csrf = document.cookie.split('; ').find((v) => v.startsWith('csrf_token='))?.split('=')[1]
        const res = await api.post('/api/auth/refresh', null, { headers: { 'X-CSRF-TOKEN': csrf ?? '' } })
        setToken(res.data.accessToken)
      } catch {
        setToken(null)
        setUser(null)
      } finally {
        setInitialized(true)
      }
    }
    init()
  }, [setInitialized, setToken, setUser])

  useEffect(() => {
    const fetchMe = async () => {
      if (!token) return
      try {
        const me = await api.get('/api/auth/me')
        setUser(me.data)
      } catch {
        setToken(null)
        setUser(null)
      }
    }
    fetchMe()
  }, [token, setToken, setUser])

  if (!initialized) return <div className="p-6">초기화 중...</div>

  return (
    <Routes>
      <Route path="/login" element={token ? <Navigate to="/" replace /> : <LoginPage />} />
      <Route path="/register" element={token ? <Navigate to="/" replace /> : <RegisterPage />} />
      <Route path="/" element={token ? <AppLayout /> : <Navigate to="/login" replace />}>
        <Route index element={<AppShell />} />
        <Route path="admin" element={user?.role === 'ADMIN' ? <AdminDashboard /> : <Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
