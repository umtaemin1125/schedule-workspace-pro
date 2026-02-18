import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { Card, Button } from './ui'
import { usePopupStore } from '../store/popup'

type Stats = { totalUsers: number; totalItems: number; totalBlocks: number; totalFiles: number }
type UserRow = {
  id: string
  email: string
  nickname: string
  role: 'USER' | 'ADMIN'
  failedLoginCount: number
  lockedUntil: string | null
  createdAt: string
  itemCount: number
}
type UserItemRow = {
  id: string
  title: string
  status: string
  dueDate: string | null
  templateType: 'free' | 'worklog' | 'meeting'
  updatedAt: string
  blockCount: number
  fileCount: number
}
type BlockRow = { id: string; sortOrder: number; type: string; content: string }
type UserItemBlocksResponse = { userId: string; itemId: string; blocks: BlockRow[] }

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  return new Date(value).toLocaleString('ko-KR')
}

export function AdminDashboard() {
  const queryClient = useQueryClient()
  const openPopup = usePopupStore((s) => s.openPopup)
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null)

  const statsQuery = useQuery<Stats>({
    queryKey: ['admin-stats'],
    queryFn: async () => (await api.get('/api/admin/stats')).data
  })

  const usersQuery = useQuery<UserRow[]>({
    queryKey: ['admin-users'],
    queryFn: async () => (await api.get('/api/admin/users')).data
  })

  const itemsQuery = useQuery<UserItemRow[]>({
    queryKey: ['admin-user-items', selectedUserId],
    enabled: !!selectedUserId,
    queryFn: async () => (await api.get(`/api/admin/users/${selectedUserId}/items`)).data
  })

  const blocksQuery = useQuery<UserItemBlocksResponse>({
    queryKey: ['admin-user-item-blocks', selectedUserId, selectedItemId],
    enabled: !!selectedUserId && !!selectedItemId,
    queryFn: async () => (await api.get(`/api/admin/users/${selectedUserId}/items/${selectedItemId}/blocks`)).data
  })

  const roleMutation = useMutation({
    mutationFn: async ({ userId, role }: { userId: string; role: 'USER' | 'ADMIN' }) => {
      return (await api.patch(`/api/admin/users/${userId}/role`, { role })).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      openPopup({ title: '권한 변경 완료', message: '사용자 권한이 변경되었습니다.' })
    }
  })

  const selectedUser = useMemo(() => usersQuery.data?.find((u) => u.id === selectedUserId) ?? null, [usersQuery.data, selectedUserId])

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-ink">관리자 대시보드</h1>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Card><p className="text-xs text-slate-500">사용자</p><p className="text-2xl font-bold">{statsQuery.data?.totalUsers ?? '-'}</p></Card>
        <Card><p className="text-xs text-slate-500">항목</p><p className="text-2xl font-bold">{statsQuery.data?.totalItems ?? '-'}</p></Card>
        <Card><p className="text-xs text-slate-500">블록</p><p className="text-2xl font-bold">{statsQuery.data?.totalBlocks ?? '-'}</p></Card>
        <Card><p className="text-xs text-slate-500">파일</p><p className="text-2xl font-bold">{statsQuery.data?.totalFiles ?? '-'}</p></Card>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[1.1fr_1fr] gap-4">
        <Card>
          <h2 className="font-semibold mb-3">회원 관리</h2>
          <div className="overflow-auto max-h-[420px]">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left border-b">
                  <th className="py-2">닉네임</th>
                  <th className="py-2">이메일</th>
                  <th className="py-2">권한</th>
                  <th className="py-2">일정 수</th>
                  <th className="py-2">잠금</th>
                </tr>
              </thead>
              <tbody>
                {usersQuery.data?.map((u) => (
                  <tr key={u.id} className={`border-b ${selectedUserId === u.id ? 'bg-slate-50' : ''}`}>
                    <td className="py-2">
                      <button className="font-semibold hover:underline" onClick={() => { setSelectedUserId(u.id); setSelectedItemId(null) }}>
                        {u.nickname}
                      </button>
                    </td>
                    <td className="py-2">{u.email}</td>
                    <td className="py-2">
                      <div className="flex items-center gap-2">
                        <select
                          className="rounded border px-2 py-1"
                          value={u.role}
                          onChange={(e) => roleMutation.mutate({ userId: u.id, role: e.target.value as 'USER' | 'ADMIN' })}
                        >
                          <option value="USER">USER</option>
                          <option value="ADMIN">ADMIN</option>
                        </select>
                      </div>
                    </td>
                    <td className="py-2">{u.itemCount}</td>
                    <td className="py-2">{u.lockedUntil ? formatDateTime(u.lockedUntil) : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        <Card>
          <h2 className="font-semibold mb-3">회원 상세</h2>
          {!selectedUser && <p className="text-sm text-slate-500">왼쪽에서 회원을 선택하면 일정/문서를 조회할 수 있습니다.</p>}
          {selectedUser && (
            <div className="space-y-2 text-sm">
              <p><b>닉네임</b>: {selectedUser.nickname}</p>
              <p><b>이메일</b>: {selectedUser.email}</p>
              <p><b>가입일</b>: {formatDateTime(selectedUser.createdAt)}</p>
              <p><b>로그인 실패</b>: {selectedUser.failedLoginCount}</p>
              <p><b>일정 수</b>: {selectedUser.itemCount}</p>
            </div>
          )}
        </Card>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[1fr_1fr] gap-4">
        <Card>
          <h2 className="font-semibold mb-3">회원 일정 관리</h2>
          {!selectedUserId && <p className="text-sm text-slate-500">회원을 먼저 선택하세요.</p>}
          {selectedUserId && (
            <div className="overflow-auto max-h-[420px]">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left border-b">
                    <th className="py-2">제목</th>
                    <th className="py-2">상태</th>
                    <th className="py-2">날짜</th>
                    <th className="py-2">유형</th>
                    <th className="py-2">블록/파일</th>
                  </tr>
                </thead>
                <tbody>
                  {itemsQuery.data?.map((item) => (
                    <tr key={item.id} className={`border-b ${selectedItemId === item.id ? 'bg-slate-50' : ''}`}>
                      <td className="py-2">
                        <button className="hover:underline" onClick={() => setSelectedItemId(item.id)}>{item.title}</button>
                      </td>
                      <td className="py-2">{item.status}</td>
                      <td className="py-2">{item.dueDate ?? '-'}</td>
                      <td className="py-2">{item.templateType}</td>
                      <td className="py-2">{item.blockCount}/{item.fileCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <Card>
          <h2 className="font-semibold mb-3">선택 일정 문서 블록</h2>
          {!selectedItemId && <p className="text-sm text-slate-500">일정을 선택하면 블록 데이터를 볼 수 있습니다.</p>}
          {selectedItemId && (
            <div className="space-y-2 max-h-[420px] overflow-auto">
              {blocksQuery.data?.blocks?.map((b) => (
                <div key={b.id} className="rounded border p-2">
                  <div className="text-xs text-slate-500">#{b.sortOrder} · {b.type}</div>
                  <pre className="text-xs whitespace-pre-wrap break-all mt-1">{b.content}</pre>
                </div>
              ))}
              {(blocksQuery.data?.blocks?.length ?? 0) === 0 && <p className="text-sm text-slate-500">저장된 블록이 없습니다.</p>}
            </div>
          )}

          <div className="mt-3 flex justify-end">
            <Button onClick={() => {
              queryClient.invalidateQueries({ queryKey: ['admin-users'] })
              if (selectedUserId) queryClient.invalidateQueries({ queryKey: ['admin-user-items', selectedUserId] })
              if (selectedUserId && selectedItemId) queryClient.invalidateQueries({ queryKey: ['admin-user-item-blocks', selectedUserId, selectedItemId] })
            }}>새로고침</Button>
          </div>
        </Card>
      </div>
    </div>
  )
}
