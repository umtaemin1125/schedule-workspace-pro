import { FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { CalendarPlus2 } from 'lucide-react'
import { api } from '../lib/api'
import { Button, Card, Input } from './ui'
import { usePopupStore } from '../store/popup'

export function RegisterPage() {
  const navigate = useNavigate()
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const openPopup = usePopupStore((s) => s.openPopup)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      await api.post('/api/auth/register', { email, nickname, password })
      openPopup({
        title: '회원가입 완료',
        message: '회원가입이 완료되었습니다. 로그인 화면으로 이동합니다.',
        confirmText: '로그인으로 이동',
        onConfirm: () => navigate('/login')
      })
    } catch (err: any) {
      setError(err?.response?.data?.message ?? '회원가입에 실패했습니다.')
    }
  }

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_90%_10%,#ccfbf1_0%,#f8fafc_45%,#eef2ff_100%)]">
      <div className="mx-auto max-w-5xl px-4 py-10 grid md:grid-cols-2 gap-6 items-stretch">
        <div className="rounded-2xl bg-white border p-8 flex flex-col justify-between">
          <div>
            <div className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-600">
              <CalendarPlus2 size={14} /> 계정 생성
            </div>
            <h1 className="mt-6 text-3xl font-bold text-ink leading-tight">팀용 업무 공간을 지금 시작하세요</h1>
            <p className="mt-3 text-sm text-slate-600">가입 후 즉시 로그인해서 트리 기반 일정, 문서 블록 편집, 백업/이관 기능을 사용할 수 있습니다.</p>
          </div>
          <p className="text-xs text-slate-500">가입 후 관리자 권한이 필요한 경우 운영자 계정으로 별도 로그인하세요.</p>
        </div>

        <Card className="w-full max-w-md md:max-w-none self-center">
          <div className="flex items-center justify-between">
            <h2 className="text-2xl font-bold text-ink">회원가입</h2>
            <Link className="text-sm text-mint font-semibold hover:underline" to="/login">로그인</Link>
          </div>

          <form onSubmit={submit} className="mt-4 space-y-3">
            <Input value={nickname} onChange={(e) => setNickname(e.target.value)} placeholder="닉네임" />
            <Input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder="이메일" />
            <Input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="비밀번호" />
            {error && <p className="text-sm text-red-600">{error}</p>}
            <Button type="submit" className="w-full">회원가입 완료</Button>
          </form>
        </Card>
      </div>
    </div>
  )
}
