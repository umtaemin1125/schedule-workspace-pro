import { FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useAuthStore } from '../store/auth'
import { Button, Card, Input } from './ui'

export function LoginPage() {
  const navigate = useNavigate()
  const setAccessToken = useAuthStore((s) => s.setAccessToken)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const res = await api.post('/api/auth/login', { email, password })
    setAccessToken(res.data.accessToken)
    navigate('/')
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-sand to-white flex items-center justify-center px-4">
      <Card className="w-full max-w-md">
        <h1 className="text-2xl font-bold text-ink">업무 일정 관리 로그인</h1>
        <form onSubmit={submit} className="mt-4 space-y-3">
          <Input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder="이메일" />
          <Input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="비밀번호" />
          <Button type="submit" className="w-full">로그인</Button>
        </form>
      </Card>
    </div>
  )
}
