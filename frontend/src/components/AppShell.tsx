import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import Image from '@tiptap/extension-image'
import { Search, Plus, Upload, CalendarDays, Database, ChevronLeft, ChevronRight } from 'lucide-react'
import { api } from '../lib/api'
import { WorkspaceItem, useWorkspaceStore } from '../store/workspace'
import { Button, Input } from './ui'
import { usePopupStore } from '../store/popup'

const STATUS_LABEL: Record<string, string> = {
  todo: '할 일',
  doing: '진행 중',
  done: '완료'
}

const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토']

const TEMPLATE_OPTIONS = [
  { value: 'free', label: '자유 형식' },
  { value: 'worklog', label: '업무일지' },
  { value: 'meeting', label: '회의록' }
] as const

type TemplateType = 'free' | 'worklog' | 'meeting'

type BlockPayload = {
  id?: string
  sortOrder: number
  type: string
  content: string
}

function statusClass(status?: string) {
  if (status === 'done') return 'bg-emerald-100 text-emerald-700'
  if (status === 'doing') return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-700'
}

function ymd(date: Date) {
  const y = date.getFullYear()
  const m = `${date.getMonth() + 1}`.padStart(2, '0')
  const d = `${date.getDate()}`.padStart(2, '0')
  return `${y}-${m}-${d}`
}

function monthLabel(date: Date) {
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월`
}

function addMonth(date: Date, diff: number) {
  return new Date(date.getFullYear(), date.getMonth() + diff, 1)
}

function makeCalendarCells(monthDate: Date) {
  const year = monthDate.getFullYear()
  const month = monthDate.getMonth()
  const first = new Date(year, month, 1)
  const start = new Date(year, month, 1 - first.getDay())
  return Array.from({ length: 42 }, (_, i) => {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    return {
      key: ymd(d),
      day: d.getDate(),
      inMonth: d.getMonth() === month,
      isToday: ymd(d) === ymd(new Date())
    }
  })
}

function templateHtml(templateType: TemplateType) {
  if (templateType === 'worklog') {
    return [
      '<h2>업무일지 템플릿</h2>',
      '<p>- 요청자 : </p>',
      '<p>- 메뉴 : <code>학사행정 - 학생관리 - 학생활동관리</code></p>',
      '<h3>요청내용</h3>',
      '<pre><code>[내선] / [불편신고]\\n요청 상세 내용</code></pre>',
      '<hr />',
      '<pre><code>처리 내용 / 쿼리 / 변경사항</code></pre>',
      '<pre><code>추가 메모</code></pre>'
    ].join('')
  }
  if (templateType === 'meeting') {
    return [
      '<h2>회의록 템플릿</h2>',
      '<p>- 회의명 : </p>',
      '<p>- 참석자 : </p>',
      '<p>- 일시 : </p>',
      '<h3>안건</h3>',
      '<ul><li>안건 1</li><li>안건 2</li></ul>',
      '<h3>결정사항</h3>',
      '<pre><code>결정된 내용</code></pre>',
      '<h3>후속 작업</h3>',
      '<ul><li>[ ] 담당자 / 마감일</li></ul>'
    ].join('')
  }
  return '<p>자유롭게 작성하세요.</p>'
}

function blocksToHtml(blocks: BlockPayload[]) {
  if (!blocks || blocks.length === 0) return '<p></p>'
  const sorted = [...blocks].sort((a, b) => a.sortOrder - b.sortOrder)
  const htmlFromFirst = parseHtmlContent(sorted[0].content)
  if (htmlFromFirst) return htmlFromFirst

  return sorted
    .map((block) => {
      const parsed = safeParse(block.content)
      const text = typeof parsed?.text === 'string' ? parsed.text : String(block.content ?? '')
      if (block.type.startsWith('heading')) {
        const level = block.type.replace('heading', '')
        return `<h${level}>${escapeHtml(text)}</h${level}>`
      }
      if (block.type === 'checklist') return `<p>☐ ${escapeHtml(text)}</p>`
      if (block.type === 'list') return `<p>• ${escapeHtml(text)}</p>`
      return `<p>${escapeHtml(text)}</p>`
    })
    .join('')
}

function parseHtmlContent(content: string) {
  const parsed = safeParse(content)
  if (parsed && typeof parsed.html === 'string') return parsed.html
  return ''
}

function safeParse(value: string) {
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

export function AppShell() {
  const queryClient = useQueryClient()
  const selectedItemId = useWorkspaceStore((s) => s.selectedItemId)
  const setSelected = useWorkspaceStore((s) => s.setSelectedItemId)
  const openPopup = usePopupStore((s) => s.openPopup)

  const [search, setSearch] = useState('')
  const [selectedDate, setSelectedDate] = useState(ymd(new Date()))
  const [visibleMonth, setVisibleMonth] = useState(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
  const [migrationReport, setMigrationReport] = useState<any>(null)
  const [titleDraft, setTitleDraft] = useState('')
  const [statusDraft, setStatusDraft] = useState<'todo' | 'doing' | 'done'>('todo')
  const [dueDateDraft, setDueDateDraft] = useState('')
  const [templateTypeDraft, setTemplateTypeDraft] = useState<TemplateType>('free')

  const itemsQuery = useQuery<WorkspaceItem[]>({
    queryKey: ['items', search, selectedDate],
    queryFn: async () => {
      const params: Record<string, string> = { dueDate: selectedDate }
      if (search) params.q = search
      return (await api.get('/api/workspace/items', { params })).data
    }
  })

  const allItemsQuery = useQuery<WorkspaceItem[]>({
    queryKey: ['items-calendar'],
    queryFn: async () => (await api.get('/api/workspace/items')).data
  })

  const selected = itemsQuery.data?.find((v) => v.id === selectedItemId)

  useEffect(() => {
    if (!selected) {
      setTitleDraft('')
      setStatusDraft('todo')
      setDueDateDraft(selectedDate)
      setTemplateTypeDraft('free')
      return
    }
    setTitleDraft(selected.title)
    setStatusDraft((selected.status as 'todo' | 'doing' | 'done') ?? 'todo')
    setDueDateDraft(selected.dueDate ?? selectedDate)
    setTemplateTypeDraft((selected.templateType ?? 'free') as TemplateType)
  }, [selected?.id, selected?.title, selected?.status, selected?.dueDate, selected?.templateType, selectedDate])

  const createMutation = useMutation({
    mutationFn: async () => (await api.post('/api/workspace/items', {
      title: '새 일정 항목',
      dueDate: selectedDate,
      templateType: templateTypeDraft
    })).data,
    onSuccess: async (item: WorkspaceItem) => {
      if (templateTypeDraft !== 'free') {
        await api.put(`/api/content/${item.id}/blocks`, {
          blocks: [{ sortOrder: 0, type: 'paragraph', content: JSON.stringify({ html: templateHtml(templateTypeDraft) }) }]
        })
      }
      queryClient.invalidateQueries({ queryKey: ['items'] })
      queryClient.invalidateQueries({ queryKey: ['items-calendar'] })
      queryClient.invalidateQueries({ queryKey: ['blocks', item.id] })
      setSelected(item.id)
      openPopup({
        title: '일정 생성 완료',
        message: `${selectedDate} 일정이 생성되었습니다.`,
        confirmText: '확인'
      })
    }
  })

  const updateMutation = useMutation({
    mutationFn: async () => {
      if (!selectedItemId) return
      await api.patch(`/api/workspace/items/${selectedItemId}`, {
        title: titleDraft,
        status: statusDraft,
        dueDate: dueDateDraft || null,
        templateType: templateTypeDraft
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['items'] })
      queryClient.invalidateQueries({ queryKey: ['items-calendar'] })
      openPopup({ title: '저장 완료', message: '일정 속성이 저장되었습니다.' })
    }
  })

  const blocksQuery = useQuery<{ itemId: string; blocks: BlockPayload[] }>({
    queryKey: ['blocks', selectedItemId],
    enabled: !!selectedItemId,
    queryFn: async () => (await api.get(`/api/content/${selectedItemId}/blocks`)).data
  })

  const editor = useEditor({
    extensions: [StarterKit, TaskList, TaskItem.configure({ nested: true }), Image],
    content: '<p>항목을 선택하세요.</p>',
    onUpdate: () => {
      if (!selectedItemId) return
      scheduleSave()
    }
  })

  const saveMutation = useMutation({
    mutationFn: async (html: string) => {
      if (!selectedItemId) return
      await api.put(`/api/content/${selectedItemId}/blocks`, {
        blocks: [{ sortOrder: 0, type: 'paragraph', content: JSON.stringify({ html }) }]
      })
    }
  })

  const scheduleSave = useMemo(() => {
    let timer: number | undefined
    return () => {
      if (timer) window.clearTimeout(timer)
      timer = window.setTimeout(() => {
        const html = editor?.getHTML() ?? '<p></p>'
        saveMutation.mutate(html)
      }, 700)
    }
  }, [editor, saveMutation])

  const loadedItemRef = useRef<string | null>(null)
  useEffect(() => {
    if (!editor) return
    if (!selectedItemId) {
      loadedItemRef.current = null
      editor.commands.setContent('<p>항목을 선택하세요.</p>', false)
      return
    }
    if (!blocksQuery.data || loadedItemRef.current === selectedItemId) return
    const html = blocksToHtml(blocksQuery.data.blocks ?? [])
    editor.commands.setContent(html || '<p></p>', false)
    loadedItemRef.current = selectedItemId
  }, [editor, selectedItemId, blocksQuery.data])

  const applyTemplateToCurrent = async () => {
    if (!selectedItemId) return
    const html = templateHtml(templateTypeDraft)
    editor?.commands.setContent(html)
    await api.put(`/api/content/${selectedItemId}/blocks`, {
      blocks: [{ sortOrder: 0, type: 'paragraph', content: JSON.stringify({ html }) }]
    })
    queryClient.invalidateQueries({ queryKey: ['blocks', selectedItemId] })
    openPopup({ title: '템플릿 적용 완료', message: '선택한 템플릿이 편집기에 반영되었습니다.' })
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
    openPopup({ title: '백업 다운로드', message: '백업 ZIP 다운로드가 시작되었습니다.' })
  }

  const importBackup = async (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    await api.post('/api/backup/import', fd)
    queryClient.invalidateQueries({ queryKey: ['items'] })
    queryClient.invalidateQueries({ queryKey: ['items-calendar'] })
    openPopup({ title: '복원 완료', message: '백업 복원이 완료되었습니다.' })
  }

  const importMigrationZip = async (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    const res = await api.post('/api/migration/import', fd)
    setMigrationReport(res.data)
    queryClient.invalidateQueries({ queryKey: ['items'] })
    queryClient.invalidateQueries({ queryKey: ['items-calendar'] })
    openPopup({ title: '이관 완료', message: '외부 ZIP 이관 처리가 완료되었습니다.' })
  }

  const selectedDateItems = itemsQuery.data ?? []
  const allItems = allItemsQuery.data ?? []
  const calendarCountMap = useMemo(() => {
    const map = new Map<string, number>()
    allItems.forEach((item) => {
      if (!item.dueDate) return
      map.set(item.dueDate, (map.get(item.dueDate) ?? 0) + 1)
    })
    return map
  }, [allItems])

  const monthCells = useMemo(() => makeCalendarCells(visibleMonth), [visibleMonth])

  return (
    <div className="space-y-4">
      <section className="rounded-lg border bg-white p-4">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <CalendarDays size={16} />
            <h3 className="font-semibold">날짜별 일정 탐색</h3>
          </div>
          <div className="text-sm text-slate-600">선택일: <b>{selectedDate}</b> · {selectedDateItems.length}개</div>
        </div>

        <div className="grid grid-cols-1 xl:grid-cols-[1.2fr_1fr] gap-4">
          <div className="rounded-md border p-3">
            <div className="flex items-center justify-between mb-3">
              <Button className="px-2 py-1" onClick={() => setVisibleMonth(addMonth(visibleMonth, -1))}><ChevronLeft size={16} /></Button>
              <p className="font-semibold">{monthLabel(visibleMonth)}</p>
              <Button className="px-2 py-1" onClick={() => setVisibleMonth(addMonth(visibleMonth, 1))}><ChevronRight size={16} /></Button>
            </div>

            <div className="grid grid-cols-7 gap-1 text-center text-xs text-slate-500 mb-1">
              {WEEKDAY.map((d) => <div key={d}>{d}</div>)}
            </div>
            <div className="grid grid-cols-7 gap-1">
              {monthCells.map((cell) => {
                const count = calendarCountMap.get(cell.key) ?? 0
                const selectedCell = cell.key === selectedDate
                return (
                  <button
                    key={cell.key}
                    onClick={() => setSelectedDate(cell.key)}
                    className={`rounded-md border p-2 text-left min-h-16 ${selectedCell ? 'bg-mint text-white border-mint' : 'hover:bg-slate-50'} ${cell.inMonth ? '' : 'opacity-45'}`}
                  >
                    <div className="text-xs font-semibold flex items-center justify-between">
                      <span>{cell.day}</span>
                      {cell.isToday && <span className={`rounded px-1 ${selectedCell ? 'bg-white/20' : 'bg-emerald-100 text-emerald-700'}`}>오늘</span>}
                    </div>
                    <div className={`mt-2 text-[11px] ${selectedCell ? 'text-white/90' : 'text-slate-500'}`}>{count > 0 ? `${count}개 일정` : '일정 없음'}</div>
                  </button>
                )
              })}
            </div>
          </div>

          <div className="rounded-md border p-3">
            <div className="flex items-center gap-2">
              <Search size={16} />
              <Input placeholder="선택 날짜 내 제목 검색" value={search} onChange={(e) => setSearch(e.target.value)} />
            </div>
            <div className="mt-3 grid grid-cols-[1fr_auto] gap-2">
              <select className="rounded-md border border-slate-300 px-3 py-2 text-sm" value={templateTypeDraft} onChange={(e) => setTemplateTypeDraft(e.target.value as TemplateType)}>
                {TEMPLATE_OPTIONS.map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
              </select>
              <Button className="flex items-center justify-center gap-2" onClick={() => createMutation.mutate()}>
                <Plus size={16} /> 일정 추가
              </Button>
            </div>

            <ul className="mt-3 space-y-1 max-h-[380px] overflow-auto">
              {selectedDateItems.map((item) => (
                <li key={item.id}>
                  <button onClick={() => setSelected(item.id)} className={`w-full text-left rounded px-2 py-2 ${item.id === selectedItemId ? 'bg-mint text-white' : 'hover:bg-slate-100'}`}>
                    <div className="font-medium">{item.title}</div>
                    <div className="text-xs mt-1 flex items-center gap-2 opacity-90">
                      <span className={`px-2 py-0.5 rounded ${item.id === selectedItemId ? 'bg-white/20 text-white' : statusClass(item.status)}`}>{STATUS_LABEL[item.status] ?? item.status}</span>
                      <span>{item.dueDate ?? '날짜 미지정'}</span>
                      <span>{TEMPLATE_OPTIONS.find((v) => v.value === item.templateType)?.label ?? '자유 형식'}</span>
                    </div>
                  </button>
                </li>
              ))}
              {selectedDateItems.length === 0 && <li className="text-sm text-slate-500 py-8 text-center">선택한 날짜에 일정이 없습니다.</li>}
            </ul>
          </div>
        </div>
      </section>

      <main>
        <div className="mb-3 rounded-lg bg-white border p-3 grid grid-cols-1 md:grid-cols-[1fr_140px_160px_150px_auto] gap-2 items-center">
          <Input value={titleDraft} onChange={(e) => setTitleDraft(e.target.value)} placeholder="일정 제목" disabled={!selectedItemId} />
          <select className="rounded-md border border-slate-300 px-3 py-2" value={statusDraft} disabled={!selectedItemId} onChange={(e) => setStatusDraft(e.target.value as 'todo' | 'doing' | 'done')}>
            <option value="todo">할 일</option>
            <option value="doing">진행 중</option>
            <option value="done">완료</option>
          </select>
          <Input type="date" value={dueDateDraft} onChange={(e) => setDueDateDraft(e.target.value)} disabled={!selectedItemId} />
          <select className="rounded-md border border-slate-300 px-3 py-2" value={templateTypeDraft} disabled={!selectedItemId} onChange={(e) => setTemplateTypeDraft(e.target.value as TemplateType)}>
            {TEMPLATE_OPTIONS.map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
          </select>
          <div className="flex items-center gap-2 justify-end">
            <Button onClick={() => updateMutation.mutate()} disabled={!selectedItemId}>속성 저장</Button>
            <Button onClick={applyTemplateToCurrent} disabled={!selectedItemId}>템플릿 적용</Button>
            <label className="inline-flex items-center gap-2 cursor-pointer text-sm border rounded-md px-3 py-2">
              <Upload size={16} /> 이미지
              <input type="file" accept="image/*" className="hidden" onChange={(e) => e.target.files?.[0] && uploadImage(e.target.files[0])} />
            </label>
          </div>
        </div>

        <div className="rounded-lg border bg-white p-4 min-h-[64vh]">
          <div className="text-xs text-slate-500 mb-2">문서 입력 시 자동 저장됩니다. 유형별 템플릿(업무일지/회의록/자유형식)을 지원합니다.</div>
          <EditorContent editor={editor} className="prose max-w-none" />
        </div>

        <div className="mt-4 rounded-lg border bg-white p-4">
          <div className="flex items-center gap-2">
            <Database size={16} />
            <h3 className="font-semibold">데이터 관리</h3>
          </div>
          <p className="text-xs text-slate-500 mt-1">외부 Export ZIP(중첩 ZIP 포함)을 업로드하면 CSV/Markdown/HTML/이미지를 분석하여 저장합니다.</p>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <Button onClick={exportBackup}>백업 ZIP 다운로드</Button>
            <label className="inline-flex items-center gap-2 cursor-pointer text-sm px-3 py-2 rounded-md border">
              백업 ZIP 복원
              <input type="file" accept=".zip" className="hidden" onChange={(e) => e.target.files?.[0] && importBackup(e.target.files[0])} />
            </label>
            <label className="inline-flex items-center gap-2 cursor-pointer text-sm px-3 py-2 rounded-md border">
              외부 Export ZIP 이관
              <input type="file" accept=".zip" className="hidden" onChange={(e) => e.target.files?.[0] && importMigrationZip(e.target.files[0])} />
            </label>
          </div>

          {migrationReport && (
            <div className="mt-4 text-sm rounded-md bg-slate-50 border p-3 space-y-1">
              <p><b>이관 결과</b></p>
              <p>저장 항목 수: {migrationReport.persistedItems}</p>
              <p>저장 이미지 수: {migrationReport.persistedFiles ?? 0}</p>
              <p>탐지 패턴: {(migrationReport.detectedPatterns ?? []).join(', ') || '-'}</p>
              {(migrationReport.failures ?? []).length > 0 && <p>실패: {migrationReport.failures.join(' | ')}</p>}
              {(migrationReport.manualFixHints ?? []).length > 0 && <p>수동 보정: {migrationReport.manualFixHints.join(' | ')}</p>}
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
