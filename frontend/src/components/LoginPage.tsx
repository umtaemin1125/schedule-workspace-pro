import { FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { CalendarCheck2, ShieldCheck } from 'lucide-react'
import { api } from '../lib/api'
import { useAuthStore } from '../store/auth'
import { Button, Card, Input } from './ui'

export function LoginPage() {
  const navigate = useNavigate()
  const setAccessToken = useAuthStore((s) => s.setAccessToken)
  const setUser = useAuthStore((s) => s.setUser)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      const res = await api.post('/api/auth/login', { email, password })
      setAccessToken(res.data.accessToken)
      const me = await api.get('/api/auth/me')
      setUser(me.data)
      navigate('/')
    } catch (err: any) {
      setError(err?.response?.data?.message ?? '로그인에 실패했습니다.')
    }
  }

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_20%_20%,#d1fae5_0%,#f8fafc_36%,#ecfeff_100%)]">
      <div className="mx-auto max-w-5xl px-4 py-10 grid md:grid-cols-2 gap-6 items-stretch">
        <div className="rounded-2xl bg-ink text-white p-8 flex flex-col justify-between">
          <div>
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs">
              <CalendarCheck2 size={14} /> 업무 일정 관리 플랫폼
            </div>
            <h1 className="mt-6 text-3xl font-bold leading-tight">팀 업무를 트리와 문서로 한 화면에서 관리하세요</h1>
            <p className="mt-3 text-sm text-slate-200">로그인 후 바로 워크스페이스, 블록 편집, 백업/이관, 관리자 대시보드까지 접근할 수 있습니다.</p>
          </div>
          <div className="text-xs text-slate-300 flex items-center gap-2">
            <ShieldCheck size={14} /> Refresh Token 자동로그인 · 역할 기반 접근 제어
          </div>
        </div>

        <Card className="w-full max-w-md md:max-w-none self-center">
          <div className="flex items-center justify-between">
            <h2 className="text-2xl font-bold text-ink">로그인</h2>
            <Link className="text-sm text-mint font-semibold hover:underline" to="/register">회원가입</Link>
          </div>

          <form onSubmit={submit} className="mt-4 space-y-3">
            <Input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder="이메일" />
            <Input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="비밀번호" />
            {error && <p className="text-sm text-red-600">{error}</p>}
            <Button type="submit" className="w-full">로그인</Button>
            <p className="text-xs text-slate-500 text-center">계정이 없으면 회원가입으로 이동하세요.</p>
          </form>
        </Card>
      </div>
    </div>
  )
}
