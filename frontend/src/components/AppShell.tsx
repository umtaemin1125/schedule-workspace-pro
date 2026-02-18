import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import Image from '@tiptap/extension-image'
import { CalendarDays, ChevronLeft, ChevronRight, Database, Plus, Search, Trash2, Upload } from 'lucide-react'
import { api } from '../lib/api'
import { WorkspaceItem, useWorkspaceStore } from '../store/workspace'
import { Button, Input } from './ui'
import { usePopupStore } from '../store/popup'

type TemplateType = 'free' | 'worklog' | 'meeting'
type Status = 'todo' | 'doing' | 'done'
type SearchMode = 'day' | 'global'

type BlockPayload = {
  id?: string
  sortOrder: number
  type: string
  content: string
}

type BoardRow = {
  id: string
  dueDate: string
}

type DayNote = {
  dueDate: string
  issue: string
  memo: string
}

const STATUS_LABEL: Record<Status, string> = { todo: '할 일', doing: '진행 중', done: '완료' }
const TEMPLATE_OPTIONS = [
  { value: 'free', label: '자유 형식' },
  { value: 'worklog', label: '업무일지' },
  { value: 'meeting', label: '회의록' }
] as const

function ymd(date: Date) {
  const y = date.getFullYear()
  const m = `${date.getMonth() + 1}`.padStart(2, '0')
  const d = `${date.getDate()}`.padStart(2, '0')
  return `${y}-${m}-${d}`
}

function addDay(dateText: string, diff: number) {
  const [y, m, d] = dateText.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  date.setDate(date.getDate() + diff)
  return ymd(date)
}

function ymFromDay(day: string) {
  return day.slice(0, 7)
}

function monthLabel(ym: string) {
  const [y, m] = ym.split('-')
  return `${y}년 ${m}월`
}

function moveMonth(ym: string, diff: number) {
  const [y, m] = ym.split('-').map(Number)
  const d = new Date(y, m - 1 + diff, 1)
  return `${d.getFullYear()}-${`${d.getMonth() + 1}`.padStart(2, '0')}`
}

function buildCalendarDays(ym: string) {
  const [y, m] = ym.split('-').map(Number)
  const first = new Date(y, m - 1, 1)
  const startWeekDay = first.getDay()
  const lastDate = new Date(y, m, 0).getDate()
  const cells: Array<{ day: string; inMonth: boolean }> = []

  for (let i = 0; i < startWeekDay; i += 1) cells.push({ day: '', inMonth: false })
  for (let d = 1; d <= lastDate; d += 1) {
    cells.push({ day: `${ym}-${`${d}`.padStart(2, '0')}`, inMonth: true })
  }
  while (cells.length % 7 !== 0) cells.push({ day: '', inMonth: false })
  return cells
}

function parseContent(content: string) {
  try {
    return JSON.parse(content)
  } catch {
    return null
  }
}

function normalizeFileUrls(html: string) {
  if (!html) return '<p></p>'
  const apiBase = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')
  if (typeof DOMParser === 'undefined') {
    return html.replace(/(src|href)=\"\/files\//g, `$1="${apiBase}/files/`)
  }
  const doc = new DOMParser().parseFromString(html, 'text/html')
  doc.querySelectorAll('img[src],a[href]').forEach((el) => {
    const attr = el.tagName === 'IMG' ? 'src' : 'href'
    const value = el.getAttribute(attr)
    if (value?.startsWith('/files/')) el.setAttribute(attr, `${apiBase}${value}`)
  })
  return doc.body.innerHTML
}

function blocksToHtml(blocks: BlockPayload[]) {
  if (!blocks || blocks.length === 0) return '<p></p>'
  const parsed = parseContent(blocks[0].content)
  if (parsed && typeof parsed.html === 'string') return normalizeFileUrls(parsed.html)
  return '<p></p>'
}

function denormalizeFileUrls(html: string) {
  const apiBase = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')
  const escaped = apiBase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return html.replace(new RegExp(`${escaped}\\/files\\/`, 'g'), '/files/')
}

function templateHtml(templateType: TemplateType, dateText: string) {
  if (templateType === 'meeting') {
    return [
      `<h1>${dateText} 회의록</h1>`,
      '<p><strong>회의명</strong> :</p>',
      '<p><strong>참석자</strong> :</p>',
      '<p><strong>일시</strong> :</p>',
      '<h2>안건</h2>',
      '<ul><li>[ ] 안건 1</li><li>[ ] 안건 2</li></ul>',
      '<h2>결정사항</h2>',
      '<pre><code>결정된 내용</code></pre>',
      '<hr />',
      '<h2>후속 작업</h2>',
      '<ul><li>[ ] 담당자 / 마감일</li></ul>'
    ].join('')
  }
  if (templateType === 'worklog') {
    return [
      `<h1>${dateText} 업무일지</h1>`,
      '<p>- 요청자 : </p>',
      '<p>- 메뉴 : <code>학사행정 - 학생관리 - 학생활동관리</code></p>',
      '<h2>요청내용</h2>',
      '<pre><code>[내선] / [불편신고]\n요청 내용</code></pre>',
      '<hr />',
      '<h2>처리내용</h2>',
      '<pre><code>처리내용 1</code></pre>',
      '<pre><code>처리내용 2</code></pre>',
      '<pre><code>처리내용 3</code></pre>'
    ].join('')
  }
  return '<p>자유롭게 작성하세요.</p>'
}

function statusClass(status?: string) {
  if (status === 'done') return 'bg-emerald-100 text-emerald-700'
  if (status === 'doing') return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-700'
}

export function AppShell() {
  const queryClient = useQueryClient()
  const selectedItemId = useWorkspaceStore((s) => s.selectedItemId)
  const setSelected = useWorkspaceStore((s) => s.setSelectedItemId)
  const openPopup = usePopupStore((s) => s.openPopup)

  const [search, setSearch] = useState('')
  const [searchMode, setSearchMode] = useState<SearchMode>('day')
  const [selectedDate, setSelectedDate] = useState(ymd(new Date()))
  const [viewMonth, setViewMonth] = useState(ymFromDay(ymd(new Date())))
  const [migrationReport, setMigrationReport] = useState<any>(null)

  const [titleDraft, setTitleDraft] = useState('')
  const [statusDraft, setStatusDraft] = useState<Status>('todo')
  const [dueDateDraft, setDueDateDraft] = useState('')
  const [templateTypeDraft, setTemplateTypeDraft] = useState<TemplateType>('free')
  const [issueDraft, setIssueDraft] = useState('')
  const [memoDraft, setMemoDraft] = useState('')

  const boardQuery = useQuery<BoardRow[]>({
    queryKey: ['board', viewMonth],
    queryFn: async () => (await api.get('/api/workspace/items/board', { params: { month: viewMonth } })).data
  })

  const dayItemsQuery = useQuery<WorkspaceItem[]>({
    queryKey: ['items-day', selectedDate],
    queryFn: async () => (await api.get('/api/workspace/items', { params: { dueDate: selectedDate } })).data
  })

  const globalItemsQuery = useQuery<WorkspaceItem[]>({
    queryKey: ['items-global-search', search],
    enabled: searchMode === 'global' && search.trim().length > 0,
    queryFn: async () => (await api.get('/api/workspace/items', { params: { q: search } })).data
  })

  const dayNoteQuery = useQuery<DayNote>({
    queryKey: ['day-note', selectedDate],
    queryFn: async () => (await api.get('/api/workspace/items/day-note', { params: { date: selectedDate } })).data
  })

  useEffect(() => {
    setIssueDraft(dayNoteQuery.data?.issue ?? '')
    setMemoDraft(dayNoteQuery.data?.memo ?? '')
  }, [dayNoteQuery.data?.dueDate, dayNoteQuery.data?.issue, dayNoteQuery.data?.memo])

  const listItems = useMemo(() => {
    if (searchMode === 'global') return globalItemsQuery.data ?? []
    const dayList = dayItemsQuery.data ?? []
    if (!search.trim()) return dayList
    return dayList.filter((v) => v.title.toLowerCase().includes(search.toLowerCase()))
  }, [searchMode, globalItemsQuery.data, dayItemsQuery.data, search])

  const selected = useMemo(() => listItems.find((item) => item.id === selectedItemId) ?? null, [listItems, selectedItemId])

  useEffect(() => {
    if (selected?.dueDate && selected.dueDate !== selectedDate) setSelectedDate(selected.dueDate)
  }, [selected?.id])

  useEffect(() => {
    setViewMonth(ymFromDay(selectedDate))
  }, [selectedDate])

  useEffect(() => {
    if (!selected) {
      setTitleDraft('')
      setStatusDraft('todo')
      setDueDateDraft(selectedDate)
      setTemplateTypeDraft('free')
      return
    }
    setTitleDraft(selected.title)
    setStatusDraft(selected.status ?? 'todo')
    setDueDateDraft(selected.dueDate ?? selectedDate)
    setTemplateTypeDraft((selected.templateType ?? 'free') as TemplateType)
  }, [selected?.id, selected?.title, selected?.status, selected?.dueDate, selected?.templateType, selectedDate])

  const blocksQuery = useQuery<{ itemId: string; blocks: BlockPayload[] }>({
    queryKey: ['blocks', selectedItemId],
    enabled: !!selectedItemId,
    queryFn: async () => (await api.get(`/api/content/${selectedItemId}/blocks`)).data
  })

  const editor = useEditor({
    extensions: [StarterKit, TaskList, TaskItem.configure({ nested: true }), Image],
    content: '<p>항목을 선택하세요.</p>'
  })

  const loadedItemRef = useRef<string | null>(null)
  useEffect(() => {
    if (!editor) return
    if (!selectedItemId) {
      loadedItemRef.current = null
      editor.commands.setContent('<p>항목을 선택하세요.</p>', false)
      return
    }
    if (!blocksQuery.data || loadedItemRef.current === selectedItemId) return
    editor.commands.setContent(blocksToHtml(blocksQuery.data.blocks ?? []), false)
    loadedItemRef.current = selectedItemId
  }, [editor, selectedItemId, blocksQuery.data])

  const saveContent = async () => {
    if (!selectedItemId) return
    const html = denormalizeFileUrls(editor?.getHTML() ?? '<p></p>')
    await api.put(`/api/content/${selectedItemId}/blocks`, {
      blocks: [{ sortOrder: 0, type: 'paragraph', content: JSON.stringify({ html }) }]
    })
  }

  const createMutation = useMutation({
    mutationFn: async () => (await api.post('/api/workspace/items', {
      title: `업무 ${selectedDate}`,
      dueDate: selectedDate,
      templateType: templateTypeDraft
    })).data,
    onSuccess: async (item: WorkspaceItem) => {
      const html = templateHtml(templateTypeDraft, selectedDate)
      await api.put(`/api/content/${item.id}/blocks`, {
        blocks: [{ sortOrder: 0, type: 'paragraph', content: JSON.stringify({ html }) }]
      })
      queryClient.invalidateQueries({ queryKey: ['items-day'] })
      queryClient.invalidateQueries({ queryKey: ['board'] })
      setSelected(item.id)
      openPopup({ title: '일정 생성 완료', message: `${selectedDate} 일정이 생성되었습니다.` })
    }
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!selectedItemId) return
      await api.patch(`/api/workspace/items/${selectedItemId}`, {
        title: titleDraft,
        status: statusDraft,
        dueDate: dueDateDraft || null,
        templateType: templateTypeDraft
      })
      await saveContent()
      await api.put('/api/workspace/items/day-note', {
        issue: issueDraft,
        memo: memoDraft
      }, { params: { date: selectedDate } })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['items-day'] })
      queryClient.invalidateQueries({ queryKey: ['items-global-search'] })
      queryClient.invalidateQueries({ queryKey: ['blocks', selectedItemId] })
      queryClient.invalidateQueries({ queryKey: ['day-note', selectedDate] })
      queryClient.invalidateQueries({ queryKey: ['board'] })
      openPopup({ title: '저장 완료', message: '일정/문서/일자 메모가 저장되었습니다.' })
    }
  })

  const deleteMutation = useMutation({
    mutationFn: async (itemId: string) => {
      await api.delete(`/api/workspace/items/${itemId}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['items-day'] })
      queryClient.invalidateQueries({ queryKey: ['items-global-search'] })
      queryClient.invalidateQueries({ queryKey: ['board'] })
      setSelected(null)
      openPopup({ title: '삭제 완료', message: '선택한 업무가 삭제되었습니다.' })
    }
  })

  const applyTemplateToCurrent = async () => {
    if (!selectedItemId) return
    const html = templateHtml(templateTypeDraft, dueDateDraft || selectedDate)
    editor?.commands.setContent(html)
    await api.put(`/api/content/${selectedItemId}/blocks`, {
      blocks: [{ sortOrder: 0, type: 'paragraph', content: JSON.stringify({ html }) }]
    })
    queryClient.invalidateQueries({ queryKey: ['blocks', selectedItemId] })
    openPopup({ title: '템플릿 적용 완료', message: '선택한 템플릿이 반영되었습니다.' })
  }

  const toggleDone = async (item: WorkspaceItem, checked: boolean) => {
    await api.patch(`/api/workspace/items/${item.id}`, { status: checked ? 'done' : 'todo' })
    queryClient.invalidateQueries({ queryKey: ['items-day'] })
    queryClient.invalidateQueries({ queryKey: ['items-global-search'] })
    queryClient.invalidateQueries({ queryKey: ['board'] })
  }

  const uploadImage = async (file: File) => {
    if (!selectedItemId) return
    const fd = new FormData()
    fd.append('itemId', selectedItemId)
    fd.append('file', file)
    const res = await api.post('/api/files/upload', fd)
    editor?.chain().focus().setImage({ src: `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}${res.data.url}` }).run()
    openPopup({ title: '업로드 완료', message: '이미지 업로드가 완료되었습니다.' })
  }

  const exportBackup = async () => {
    const res = await api.get('/api/backup/export', { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = 'backup.zip'
    a.click()
    URL.revokeObjectURL(url)
  }

  const importBackup = async (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    await api.post('/api/backup/import', fd)
    queryClient.invalidateQueries({ queryKey: ['items-day'] })
    queryClient.invalidateQueries({ queryKey: ['items-global-search'] })
    queryClient.invalidateQueries({ queryKey: ['board'] })
    openPopup({ title: '복원 완료', message: '백업 복원이 완료되었습니다.' })
  }

  const importMigrationZip = async (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    const res = await api.post('/api/migration/import', fd)
    setMigrationReport(res.data)
    queryClient.invalidateQueries({ queryKey: ['items-day'] })
    queryClient.invalidateQueries({ queryKey: ['items-global-search'] })
    queryClient.invalidateQueries({ queryKey: ['day-note'] })
    queryClient.invalidateQueries({ queryKey: ['board'] })
    openPopup({ title: '이관 완료', message: '외부 ZIP 이관 처리가 완료되었습니다.' })
  }

  const countMap = useMemo(() => {
    const map = new Map<string, number>()
    ;(boardQuery.data ?? []).forEach((row) => map.set(row.dueDate, (map.get(row.dueDate) ?? 0) + 1))
    return map
  }, [boardQuery.data])

  const calendarCells = useMemo(() => buildCalendarDays(viewMonth), [viewMonth])

  return (
    <div className="space-y-4">
      <section className="rounded-2xl border bg-white p-4 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2"><CalendarDays size={18} /><h3 className="font-semibold text-lg">일정 보기</h3></div>
          <div className="flex items-center gap-2">
            <Button className="px-2 py-1" onClick={() => setSelectedDate(addDay(selectedDate, -1))}><ChevronLeft size={16} /></Button>
            <Input type="date" value={selectedDate} onChange={(e) => setSelectedDate(e.target.value)} className="w-[170px]" />
            <Button className="px-2 py-1" onClick={() => setSelectedDate(addDay(selectedDate, 1))}><ChevronRight size={16} /></Button>
          </div>
        </div>

        <div className="mt-3 grid grid-cols-1 md:grid-cols-[1fr_140px_220px_auto] gap-2">
          <div className="flex items-center gap-2 rounded-md border px-3 py-2"><Search size={16} className="text-slate-400" /><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder={searchMode === 'global' ? '전체 날짜 업무 검색' : '선택 날짜 업무 검색'} className="w-full outline-none text-sm" /></div>
          <select className="rounded-md border border-slate-300 px-3 py-2 text-sm" value={searchMode} onChange={(e) => setSearchMode(e.target.value as SearchMode)}>
            <option value="day">선택 날짜</option>
            <option value="global">전체 날짜</option>
          </select>
          <select className="rounded-md border border-slate-300 px-3 py-2 text-sm" value={templateTypeDraft} onChange={(e) => setTemplateTypeDraft(e.target.value as TemplateType)}>
            {TEMPLATE_OPTIONS.map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
          </select>
          <Button className="flex items-center justify-center gap-2" onClick={() => createMutation.mutate()}><Plus size={16} /> 일정 추가</Button>
        </div>

        <div className="mt-3 rounded-lg border p-3">
          <div className="mb-2 flex items-center justify-between">
            <Button className="px-2 py-1" onClick={() => setViewMonth(moveMonth(viewMonth, -1))}><ChevronLeft size={16} /></Button>
            <div className="font-semibold">{monthLabel(viewMonth)}</div>
            <Button className="px-2 py-1" onClick={() => setViewMonth(moveMonth(viewMonth, 1))}><ChevronRight size={16} /></Button>
          </div>
          <div className="grid grid-cols-7 gap-1 text-center text-xs text-slate-500 mb-1">
            <div>일</div><div>월</div><div>화</div><div>수</div><div>목</div><div>금</div><div>토</div>
          </div>
          <div className="grid grid-cols-7 gap-1">
            {calendarCells.map((cell, idx) => {
              if (!cell.inMonth) return <div key={`empty-${idx}`} className="h-16 rounded border bg-slate-50" />
              const count = countMap.get(cell.day) ?? 0
              const selectedClass = cell.day === selectedDate ? 'border-cyan-500 bg-cyan-50' : 'border-slate-200 bg-white'
              return (
                <button
                  key={cell.day}
                  className={`h-16 rounded border p-1 text-left ${selectedClass}`}
                  onClick={() => setSelectedDate(cell.day)}
                >
                  <div className="text-xs font-semibold">{cell.day.slice(8)}</div>
                  <div className="mt-1 text-[11px] text-slate-600">업무 {count}건</div>
                </button>
              )
            })}
          </div>
        </div>

        <div className="mt-3 rounded-lg border p-3">
          <div className="mb-2 text-sm font-semibold">{searchMode === 'global' ? '전체 날짜 검색 결과' : `${selectedDate} 일정 (${countMap.get(selectedDate) ?? 0}개)`}</div>
          <ul className="space-y-2 max-h-[360px] overflow-auto">
            {listItems.map((item) => (
              <li key={item.id} className={`rounded border p-2 ${item.id === selectedItemId ? 'border-mint bg-cyan-50' : ''}`}>
                <div className="flex items-start gap-2">
                  <input type="checkbox" checked={item.status === 'done'} onChange={(e) => toggleDone(item, e.target.checked)} className="mt-1 h-4 w-4" />
                  <button onClick={() => setSelected(item.id)} className="text-left w-full">
                    <div className="font-medium break-words">{item.title}</div>
                    <div className="mt-1 flex items-center gap-2 text-xs text-slate-600">
                      <span className={`px-2 py-0.5 rounded ${statusClass(item.status)}`}>{STATUS_LABEL[item.status]}</span>
                      <span>{item.dueDate ?? '-'}</span>
                      <span>{TEMPLATE_OPTIONS.find((v) => v.value === item.templateType)?.label}</span>
                    </div>
                  </button>
                </div>
              </li>
            ))}
            {listItems.length === 0 && <li className="text-sm text-slate-500 py-8 text-center">검색 결과가 없습니다.</li>}
          </ul>
        </div>
      </section>

      <section className="rounded-2xl border bg-[#fcfcfb] p-5 shadow-sm">
        <div className="mb-3 grid grid-cols-1 md:grid-cols-[1fr_130px_150px_140px_auto] gap-2">
          <Input value={titleDraft} onChange={(e) => setTitleDraft(e.target.value)} placeholder="일정 제목" disabled={!selectedItemId} />
          <select className="rounded-md border border-slate-300 px-3 py-2" value={statusDraft} disabled={!selectedItemId} onChange={(e) => setStatusDraft(e.target.value as Status)}>
            <option value="todo">할 일</option>
            <option value="doing">진행 중</option>
            <option value="done">완료</option>
          </select>
          <Input type="date" value={dueDateDraft} onChange={(e) => setDueDateDraft(e.target.value)} disabled={!selectedItemId} />
          <select className="rounded-md border border-slate-300 px-3 py-2" value={templateTypeDraft} disabled={!selectedItemId} onChange={(e) => setTemplateTypeDraft(e.target.value as TemplateType)}>
            {TEMPLATE_OPTIONS.map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
          </select>
          <div className="flex items-center gap-2 justify-end">
            <Button onClick={() => saveMutation.mutate()} disabled={!selectedItemId}>저장하기</Button>
            <Button onClick={applyTemplateToCurrent} disabled={!selectedItemId}>템플릿 적용</Button>
            <Button className="bg-rose-600 hover:bg-rose-700" onClick={() => selectedItemId && deleteMutation.mutate(selectedItemId)} disabled={!selectedItemId}><Trash2 size={14} /> 삭제</Button>
            <label className="inline-flex items-center gap-2 cursor-pointer text-sm border rounded-md px-3 py-2">
              <Upload size={16} /> 이미지
              <input type="file" accept="image/*" className="hidden" onChange={(e) => e.target.files?.[0] && uploadImage(e.target.files[0])} />
            </label>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-3">
          <div>
            <label className="text-xs text-slate-500">일자 이슈</label>
            <textarea className="w-full rounded-md border border-slate-300 px-3 py-2 min-h-24" value={issueDraft} onChange={(e) => setIssueDraft(e.target.value)} />
          </div>
          <div>
            <label className="text-xs text-slate-500">일자 메모</label>
            <textarea className="w-full rounded-md border border-slate-300 px-3 py-2 min-h-24" value={memoDraft} onChange={(e) => setMemoDraft(e.target.value)} />
          </div>
        </div>

        <div className="mx-auto max-w-[900px] rounded-xl border bg-white p-8 shadow-sm min-h-[62vh]">
          <EditorContent editor={editor} className="notion-prose" />
        </div>
      </section>

      <section className="rounded-2xl border bg-white p-4 shadow-sm">
        <div className="flex items-center gap-2"><Database size={16} /><h3 className="font-semibold">데이터 관리</h3></div>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <Button onClick={exportBackup}>백업 ZIP 다운로드</Button>
          <label className="inline-flex items-center gap-2 cursor-pointer text-sm px-3 py-2 rounded-md border">백업 ZIP 복원<input type="file" accept=".zip" className="hidden" onChange={(e) => e.target.files?.[0] && importBackup(e.target.files[0])} /></label>
          <label className="inline-flex items-center gap-2 cursor-pointer text-sm px-3 py-2 rounded-md border">외부 Export ZIP 이관<input type="file" accept=".zip" className="hidden" onChange={(e) => e.target.files?.[0] && importMigrationZip(e.target.files[0])} /></label>
        </div>

        {migrationReport && (
          <div className="mt-4 text-sm rounded-md bg-slate-50 border p-3 space-y-1">
            <p><b>이관 결과</b></p>
            <p>저장 항목 수: {migrationReport.persistedItems}</p>
            <p>저장 이미지 수: {migrationReport.persistedFiles ?? 0}</p>
            {(migrationReport.failures ?? []).length > 0 && <p>실패: {migrationReport.failures.join(' | ')}</p>}
          </div>
        )}
      </section>
    </div>
  )
}
