import { FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
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
    <div className="min-h-screen bg-gradient-to-br from-sand to-white flex items-center justify-center px-4">
      <Card className="w-full max-w-md">
        <h1 className="text-2xl font-bold text-ink">업무 일정 관리 로그인</h1>
        <form onSubmit={submit} className="mt-4 space-y-3">
          <Input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder="이메일" />
          <Input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="비밀번호" />
          {error && <p className="text-sm text-red-600">{error}</p>}
          <Button type="submit" className="w-full">로그인</Button>
          <Link className="block text-center text-sm text-slate-600 hover:underline" to="/register">계정이 없으면 회원가입</Link>
        </form>
      </Card>
    </div>
  )
}
