import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { Card, Button, Input } from './ui'
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
type Detail = {
  userId: string
  itemId: string
  title: string
  status: string
  dueDate: string | null
  templateType: string
  html: string
  issue: string
  memo: string
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  return new Date(value).toLocaleString('ko-KR')
}

export function AdminDashboard() {
  const queryClient = useQueryClient()
  const openPopup = usePopupStore((s) => s.openPopup)

  const [userKeyword, setUserKeyword] = useState('')
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null)

  const [titleDraft, setTitleDraft] = useState('')
  const [statusDraft, setStatusDraft] = useState('todo')
  const [dueDateDraft, setDueDateDraft] = useState('')
  const [templateDraft, setTemplateDraft] = useState('free')
  const [issueDraft, setIssueDraft] = useState('')
  const [memoDraft, setMemoDraft] = useState('')
  const [htmlDraft, setHtmlDraft] = useState('')

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

  const detailQuery = useQuery<Detail>({
    queryKey: ['admin-user-item-detail', selectedUserId, selectedItemId],
    enabled: !!selectedUserId && !!selectedItemId,
    queryFn: async () => (await api.get(`/api/admin/users/${selectedUserId}/items/${selectedItemId}/detail`)).data
  })

  const roleMutation = useMutation({
    mutationFn: async ({ userId, role }: { userId: string; role: 'USER' | 'ADMIN' }) => api.patch(`/api/admin/users/${userId}/role`, { role }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      openPopup({ title: '권한 변경 완료', message: '사용자 권한이 변경되었습니다.' })
    }
  })

  const unlockMutation = useMutation({
    mutationFn: async (userId: string) => api.post(`/api/admin/users/${userId}/unlock`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      openPopup({ title: '잠금 해제 완료', message: '사용자 잠금을 해제했습니다.' })
    }
  })

  const deleteUserMutation = useMutation({
    mutationFn: async (userId: string) => api.delete(`/api/admin/users/${userId}`),
    onSuccess: () => {
      setSelectedUserId(null)
      setSelectedItemId(null)
      queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      queryClient.invalidateQueries({ queryKey: ['admin-user-items'] })
      openPopup({ title: '사용자 삭제 완료', message: '사용자와 관련 데이터가 삭제되었습니다.' })
    }
  })

  const deleteItemMutation = useMutation({
    mutationFn: async (itemId: string) => api.delete(`/api/admin/users/${selectedUserId}/items/${itemId}`),
    onSuccess: () => {
      setSelectedItemId(null)
      queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
      queryClient.invalidateQueries({ queryKey: ['admin-user-items', selectedUserId] })
      openPopup({ title: '일정 삭제 완료', message: '선택 일정이 삭제되었습니다.' })
    }
  })

  const saveDetailMutation = useMutation({
    mutationFn: async () => {
      if (!selectedUserId || !selectedItemId) return
      await api.put(`/api/admin/users/${selectedUserId}/items/${selectedItemId}/detail`, {
        title: titleDraft,
        status: statusDraft,
        dueDate: dueDateDraft || null,
        templateType: templateDraft,
        issue: issueDraft,
        memo: memoDraft,
        html: htmlDraft
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-user-items', selectedUserId] })
      queryClient.invalidateQueries({ queryKey: ['admin-user-item-detail', selectedUserId, selectedItemId] })
      openPopup({ title: '관리자 저장 완료', message: '일정/문서/일자이슈메모를 수정했습니다.' })
    }
  })

  const filteredUsers = useMemo(() => {
    const src = usersQuery.data ?? []
    if (!userKeyword.trim()) return src
    return src.filter((u) => `${u.nickname} ${u.email}`.toLowerCase().includes(userKeyword.toLowerCase()))
  }, [usersQuery.data, userKeyword])

  const selectedUser = useMemo(() => usersQuery.data?.find((u) => u.id === selectedUserId) ?? null, [usersQuery.data, selectedUserId])

  useEffect(() => {
    if (!detailQuery.data || !selectedItemId) return
    setTitleDraft(detailQuery.data.title)
    setStatusDraft(detailQuery.data.status)
    setDueDateDraft(detailQuery.data.dueDate ?? '')
    setTemplateDraft(detailQuery.data.templateType)
    setIssueDraft(detailQuery.data.issue)
    setMemoDraft(detailQuery.data.memo)
    setHtmlDraft(detailQuery.data.html)
  }, [detailQuery.data?.itemId])

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-ink">관리자 대시보드</h1>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Card><p className="text-xs text-slate-500">사용자</p><p className="text-2xl font-bold">{statsQuery.data?.totalUsers ?? '-'}</p></Card>
        <Card><p className="text-xs text-slate-500">항목</p><p className="text-2xl font-bold">{statsQuery.data?.totalItems ?? '-'}</p></Card>
        <Card><p className="text-xs text-slate-500">블록</p><p className="text-2xl font-bold">{statsQuery.data?.totalBlocks ?? '-'}</p></Card>
        <Card><p className="text-xs text-slate-500">파일</p><p className="text-2xl font-bold">{statsQuery.data?.totalFiles ?? '-'}</p></Card>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[1fr_1.1fr] gap-4">
        <Card>
          <div className="flex items-center justify-between gap-2 mb-2">
            <h2 className="font-semibold">회원 관리</h2>
            <Input placeholder="닉네임/이메일 검색" value={userKeyword} onChange={(e) => setUserKeyword(e.target.value)} className="max-w-[240px]" />
          </div>
          <div className="overflow-auto max-h-[460px]">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left border-b">
                  <th className="py-2">닉네임</th><th className="py-2">이메일</th><th className="py-2">권한</th><th className="py-2">잠금</th><th className="py-2">관리</th>
                </tr>
              </thead>
              <tbody>
                {filteredUsers.map((u) => (
                  <tr key={u.id} className={`border-b ${selectedUserId === u.id ? 'bg-slate-50' : ''}`}>
                    <td className="py-2"><button className="font-semibold hover:underline" onClick={() => { setSelectedUserId(u.id); setSelectedItemId(null); setTitleDraft('') }}>{u.nickname}</button></td>
                    <td className="py-2">{u.email}</td>
                    <td className="py-2">
                      <select className="rounded border px-2 py-1" value={u.role} onChange={(e) => roleMutation.mutate({ userId: u.id, role: e.target.value as 'USER' | 'ADMIN' })}>
                        <option value="USER">USER</option><option value="ADMIN">ADMIN</option>
                      </select>
                    </td>
                    <td className="py-2">{u.lockedUntil ? formatDateTime(u.lockedUntil) : '-'}</td>
                    <td className="py-2">
                      <div className="flex gap-2">
                        <Button className="px-2 py-1" onClick={() => unlockMutation.mutate(u.id)}>해제</Button>
                        <Button className="px-2 py-1 bg-rose-600 hover:bg-rose-700" onClick={() => deleteUserMutation.mutate(u.id)}>삭제</Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        <Card>
          <h2 className="font-semibold mb-2">회원 일정 목록</h2>
          {!selectedUserId && <p className="text-sm text-slate-500">왼쪽에서 회원을 선택하세요.</p>}
          {selectedUserId && (
            <div className="overflow-auto max-h-[460px]">
              <table className="w-full text-sm">
                <thead><tr className="text-left border-b"><th className="py-2">제목</th><th className="py-2">상태</th><th className="py-2">날짜</th><th className="py-2">유형</th><th className="py-2">관리</th></tr></thead>
                <tbody>
                  {itemsQuery.data?.map((item) => (
                    <tr key={item.id} className={`border-b ${selectedItemId === item.id ? 'bg-slate-50' : ''}`}>
                      <td className="py-2"><button className="hover:underline" onClick={() => { setSelectedItemId(item.id); setTitleDraft('') }}>{item.title}</button></td>
                      <td className="py-2">{item.status}</td>
                      <td className="py-2">{item.dueDate ?? '-'}</td>
                      <td className="py-2">{item.templateType}</td>
                      <td className="py-2"><Button className="px-2 py-1 bg-rose-600 hover:bg-rose-700" onClick={() => deleteItemMutation.mutate(item.id)}>삭제</Button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {selectedUser && <div className="mt-2 text-xs text-slate-500">선택 사용자: {selectedUser.nickname} ({selectedUser.email})</div>}
        </Card>
      </div>

      <Card>
        <h2 className="font-semibold mb-3">일정 상세 폼 편집</h2>
        {!selectedItemId && <p className="text-sm text-slate-500">일정을 선택하면 폼으로 수정할 수 있습니다.</p>}
        {selectedItemId && (
          <>
            <div className="grid grid-cols-1 md:grid-cols-[1fr_130px_150px_140px] gap-2 mb-3">
              <Input value={titleDraft} onChange={(e) => setTitleDraft(e.target.value)} placeholder="제목" />
              <select className="rounded-md border border-slate-300 px-3 py-2" value={statusDraft} onChange={(e) => setStatusDraft(e.target.value)}>
                <option value="todo">todo</option><option value="doing">doing</option><option value="done">done</option>
              </select>
              <Input type="date" value={dueDateDraft} onChange={(e) => setDueDateDraft(e.target.value)} />
              <select className="rounded-md border border-slate-300 px-3 py-2" value={templateDraft} onChange={(e) => setTemplateDraft(e.target.value)}>
                <option value="free">free</option><option value="worklog">worklog</option><option value="meeting">meeting</option>
              </select>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-3">
              <div><label className="text-xs text-slate-500">일자 이슈</label><textarea className="w-full rounded-md border border-slate-300 px-3 py-2 min-h-24" value={issueDraft} onChange={(e) => setIssueDraft(e.target.value)} /></div>
              <div><label className="text-xs text-slate-500">일자 메모</label><textarea className="w-full rounded-md border border-slate-300 px-3 py-2 min-h-24" value={memoDraft} onChange={(e) => setMemoDraft(e.target.value)} /></div>
            </div>

            <div>
              <label className="text-xs text-slate-500">본문 HTML</label>
              <textarea className="w-full rounded-md border border-slate-300 px-3 py-2 min-h-[320px] font-mono text-sm" value={htmlDraft} onChange={(e) => setHtmlDraft(e.target.value)} />
            </div>

            <div className="mt-3 flex justify-end">
              <Button onClick={() => saveDetailMutation.mutate()}>관리자 저장</Button>
            </div>
          </>
        )}
      </Card>
    </div>
  )
}
