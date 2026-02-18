import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { Card } from './ui'

type Stats = { totalUsers: number; totalItems: number; totalBlocks: number; totalFiles: number }
type UserRow = { id: string; email: string; role: string; failedLoginCount: number }

export function AdminDashboard() {
  const statsQuery = useQuery<Stats>({
    queryKey: ['admin-stats'],
    queryFn: async () => (await api.get('/api/admin/stats')).data
  })

  const usersQuery = useQuery<UserRow[]>({
    queryKey: ['admin-users'],
    queryFn: async () => (await api.get('/api/admin/users')).data
  })

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-ink">관리자 대시보드</h1>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Card><p className="text-xs text-slate-500">사용자</p><p className="text-2xl font-bold">{statsQuery.data?.totalUsers ?? '-'}</p></Card>
        <Card><p className="text-xs text-slate-500">항목</p><p className="text-2xl font-bold">{statsQuery.data?.totalItems ?? '-'}</p></Card>
        <Card><p className="text-xs text-slate-500">블록</p><p className="text-2xl font-bold">{statsQuery.data?.totalBlocks ?? '-'}</p></Card>
        <Card><p className="text-xs text-slate-500">파일</p><p className="text-2xl font-bold">{statsQuery.data?.totalFiles ?? '-'}</p></Card>
      </div>

      <Card>
        <h2 className="font-semibold mb-3">사용자 목록</h2>
        <div className="overflow-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left border-b">
                <th className="py-2">이메일</th>
                <th className="py-2">권한</th>
                <th className="py-2">로그인 실패</th>
              </tr>
            </thead>
            <tbody>
              {usersQuery.data?.map((u) => (
                <tr key={u.id} className="border-b">
                  <td className="py-2">{u.email}</td>
                  <td className="py-2">{u.role}</td>
                  <td className="py-2">{u.failedLoginCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}
