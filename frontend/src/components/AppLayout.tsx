import { Link, Outlet, useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useAuthStore } from '../store/auth'
import { Button } from './ui'

export function AppLayout() {
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const setToken = useAuthStore((s) => s.setAccessToken)
  const setUser = useAuthStore((s) => s.setUser)

  const logout = async () => {
    try {
      await api.post('/api/auth/logout')
    } finally {
      setToken(null)
      setUser(null)
      navigate('/login')
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <header className="border-b bg-white">
        <div className="mx-auto max-w-[1400px] px-4 h-14 flex items-center gap-4">
          <Link to="/" className="font-bold text-ink">업무 일정 관리</Link>
          <nav className="flex items-center gap-3 text-sm">
            <Link to="/" className="hover:underline">워크스페이스</Link>
            {user?.role === 'ADMIN' ? (
              <Link to="/admin" className="hover:underline text-ink font-semibold">관리자 대시보드</Link>
            ) : (
              <span className="text-slate-400 cursor-not-allowed" title="관리자 권한 필요">관리자 대시보드</span>
            )}
          </nav>
          <div className="ml-auto flex items-center gap-3 text-sm text-slate-600">
            <span>{user?.nickname || user?.email}</span>
            <span className="rounded bg-slate-100 px-2 py-1">{user?.role}</span>
            <Button onClick={logout}>로그아웃</Button>
          </div>
        </div>
      </header>

      <main className="flex-1 mx-auto w-full max-w-[1400px] p-4">
        <Outlet />
      </main>

      <footer className="border-t bg-white">
        <div className="mx-auto max-w-[1400px] px-4 h-12 flex items-center text-xs text-slate-500">
          © {new Date().getFullYear()} 업무 일정 관리 플랫폼 · 운영/보안/백업 기준 적용
        </div>
      </footer>
    </div>
  )
}
