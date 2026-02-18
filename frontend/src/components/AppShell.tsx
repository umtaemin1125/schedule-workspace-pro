import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import Image from '@tiptap/extension-image'
import { CalendarDays, ChevronLeft, ChevronRight, Database, Plus, Search, Upload } from 'lucide-react'
import { api } from '../lib/api'
import { WorkspaceItem, useWorkspaceStore } from '../store/workspace'
import { Button, Input } from './ui'
import { usePopupStore } from '../store/popup'

type TemplateType = 'free' | 'worklog' | 'meeting'
type Status = 'todo' | 'doing' | 'done'

type BlockPayload = {
  id?: string
  sortOrder: number
  type: string
  content: string
}

type BoardRow = {
  id: string
  parentId: string | null
  dueDate: string
  title: string
  status: Status
  templateType: TemplateType
  todayWork: string
  issue: string
  memo: string
  checklistTotal: number
  checklistDone: number
}

type WorklogForm = {
  requester: string
  menu: string
  requestChannel: string
  requestContent: string
  processContent1: string
  processContent2: string
  processContent3: string
}

const STATUS_LABEL: Record<Status, string> = {
  todo: '할 일',
  doing: '진행 중',
  done: '완료'
}

const TEMPLATE_OPTIONS = [
  { value: 'free', label: '자유 형식' },
  { value: 'worklog', label: '업무일지' },
  { value: 'meeting', label: '회의록' }
] as const

const EMPTY_WORKLOG: WorklogForm = {
  requester: '',
  menu: '학사행정 - 학생관리 - 학생활동관리',
  requestChannel: '[내선] / [불편신고]',
  requestContent: '',
  processContent1: '',
  processContent2: '',
  processContent3: ''
}

function ymd(date: Date) {
  const y = date.getFullYear()
  const m = `${date.getMonth() + 1}`.padStart(2, '0')
  const d = `${date.getDate()}`.padStart(2, '0')
  return `${y}-${m}-${d}`
}

function ym(date: Date) {
  const y = date.getFullYear()
  const m = `${date.getMonth() + 1}`.padStart(2, '0')
  return `${y}-${m}`
}

function addMonth(date: Date, diff: number) {
  return new Date(date.getFullYear(), date.getMonth() + diff, 1)
}

function monthLabel(ymValue: string) {
  const [y, m] = ymValue.split('-')
  return `${y}년 ${Number(m)}월`
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function parseContent(content: string) {
  try {
    return JSON.parse(content)
  } catch {
    return null
  }
}

function blocksToHtml(blocks: BlockPayload[]) {
  if (!blocks || blocks.length === 0) return '<p></p>'
  const sorted = [...blocks].sort((a, b) => a.sortOrder - b.sortOrder)
  const parsed = parseContent(sorted[0].content)
  if (parsed && typeof parsed.html === 'string') return parsed.html
  return '<p></p>'
}

function worklogToHtml(worklog: WorklogForm) {
  const html = [
    '<h1>업무일지</h1>',
    `<p>- 요청자 : ${escapeHtml(worklog.requester)}</p>`,
    `<p>- 메뉴 : <code>${escapeHtml(worklog.menu)}</code></p>`,
    '<h3>요청내용</h3>',
    `<pre><code>${escapeHtml(worklog.requestChannel)}\n${escapeHtml(worklog.requestContent)}</code></pre>`,
    '<hr />',
    '<h3>처리내용</h3>',
    `<pre><code>${escapeHtml(worklog.processContent1)}</code></pre>`
  ]
  if (worklog.processContent2.trim()) html.push(`<pre><code>${escapeHtml(worklog.processContent2)}</code></pre>`)
  if (worklog.processContent3.trim()) html.push(`<pre><code>${escapeHtml(worklog.processContent3)}</code></pre>`)
  return html.join('')
}

function parseWorklogFromPayload(blocks: BlockPayload[]) {
  if (!blocks || blocks.length === 0) return null
  const parsed = parseContent(blocks[0].content)
  if (!parsed || typeof parsed.worklog !== 'object') return null
  const w = parsed.worklog as Record<string, unknown>
  return {
    requester: String(w.requester ?? ''),
    menu: String(w.menu ?? ''),
    requestChannel: String(w.requestChannel ?? ''),
    requestContent: String(w.requestContent ?? ''),
    processContent1: String(w.processContent1 ?? ''),
    processContent2: String(w.processContent2 ?? ''),
    processContent3: String(w.processContent3 ?? '')
  } as WorklogForm
}

function parseWorklogFromHtml(html: string) {
  if (!html || typeof window === 'undefined') return null
  try {
    const doc = new DOMParser().parseFromString(html, 'text/html')
    const lines = Array.from(doc.querySelectorAll('p')).map((p) => p.textContent?.trim() ?? '')
    const pres = Array.from(doc.querySelectorAll('pre code')).map((v) => v.textContent ?? '')

    const requesterLine = lines.find((v) => v.startsWith('- 요청자')) ?? ''
    const menuLine = lines.find((v) => v.startsWith('- 메뉴')) ?? ''
    const requester = requesterLine.includes(':') ? requesterLine.split(':').slice(1).join(':').trim() : ''
    const menu = menuLine.includes(':') ? menuLine.split(':').slice(1).join(':').trim() : ''

    const requestBlock = pres[0] ?? ''
    const requestLines = requestBlock.split('\n')
    const requestChannel = requestLines[0] ?? '[내선] / [불편신고]'
    const requestContent = requestLines.slice(1).join('\n').trim()

    return {
      requester,
      menu,
      requestChannel,
      requestContent,
      processContent1: pres[1] ?? '',
      processContent2: pres[2] ?? '',
      processContent3: pres[3] ?? ''
    } as WorklogForm
  } catch {
    return null
  }
}

function templateHtml(templateType: TemplateType) {
  if (templateType === 'meeting') {
    return [
      '<h2>회의록</h2>',
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

function statusClass(status?: string) {
  if (status === 'done') return 'bg-emerald-100 text-emerald-700'
  if (status === 'doing') return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-700'
}

function short(value?: string) {
  if (!value) return '-'
  const compact = value.replace(/\s+/g, ' ').trim()
  return compact.length > 90 ? `${compact.slice(0, 90)}...` : compact
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
  const [statusDraft, setStatusDraft] = useState<Status>('todo')
  const [dueDateDraft, setDueDateDraft] = useState('')
  const [templateTypeDraft, setTemplateTypeDraft] = useState<TemplateType>('free')

  const [worklogForm, setWorklogForm] = useState<WorklogForm>(EMPTY_WORKLOG)
  const [worklogReady, setWorklogReady] = useState(false)
  const [worklogFallbackHtml, setWorklogFallbackHtml] = useState('')

  const monthKey = ym(visibleMonth)

  const boardQuery = useQuery<BoardRow[]>({
    queryKey: ['board', monthKey],
    queryFn: async () => (await api.get('/api/workspace/items/board', { params: { month: monthKey } })).data
  })

  const allItemsQuery = useQuery<WorkspaceItem[]>({
    queryKey: ['items-all'],
    queryFn: async () => (await api.get('/api/workspace/items')).data
  })

  const selected = useMemo(() => {
    return (allItemsQuery.data ?? []).find((v) => v.id === selectedItemId)
  }, [allItemsQuery.data, selectedItemId])

  useEffect(() => {
    if (!selected) {
      setTitleDraft('')
      setStatusDraft('todo')
      setDueDateDraft(selectedDate)
      setTemplateTypeDraft('free')
      setWorklogForm(EMPTY_WORKLOG)
      setWorklogReady(false)
      setWorklogFallbackHtml('')
      return
    }
    setTitleDraft(selected.title)
    setStatusDraft(selected.status ?? 'todo')
    setDueDateDraft(selected.dueDate ?? selectedDate)
    setTemplateTypeDraft((selected.templateType ?? 'free') as TemplateType)
  }, [selected?.id, selected?.title, selected?.status, selected?.dueDate, selected?.templateType, selectedDate])

  const createMutation = useMutation({
    mutationFn: async () => (await api.post('/api/workspace/items', {
      title: `업무 ${selectedDate}`,
      dueDate: selectedDate,
      templateType: templateTypeDraft
    })).data,
    onSuccess: async (item: WorkspaceItem) => {
      if (templateTypeDraft === 'meeting') {
        const html = templateHtml('meeting')
        await api.put(`/api/content/${item.id}/blocks`, {
          blocks: [{ sortOrder: 0, type: 'paragraph', content: JSON.stringify({ html }) }]
        })
      }
      if (templateTypeDraft === 'worklog') {
        const html = worklogToHtml(EMPTY_WORKLOG)
        await api.put(`/api/content/${item.id}/blocks`, {
          blocks: [{ sortOrder: 0, type: 'worklog', content: JSON.stringify({ html, worklog: EMPTY_WORKLOG }) }]
        })
      }
      queryClient.invalidateQueries({ queryKey: ['board'] })
      queryClient.invalidateQueries({ queryKey: ['items-all'] })
      queryClient.invalidateQueries({ queryKey: ['blocks', item.id] })
      setSelected(item.id)
      openPopup({ title: '일정 생성 완료', message: `${selectedDate} 일정이 생성되었습니다.` })
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
      queryClient.invalidateQueries({ queryKey: ['board'] })
      queryClient.invalidateQueries({ queryKey: ['items-all'] })
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
      if (!selectedItemId || templateTypeDraft === 'worklog') return
      scheduleSave()
    }
  })

  const saveMutation = useMutation({
    mutationFn: async (html: string) => {
      if (!selectedItemId) return
      await api.put(`/api/content/${selectedItemId}/blocks`, {
        blocks: [{ sortOrder: 0, type: 'paragraph', content: JSON.stringify({ html }) }]
      })
      queryClient.invalidateQueries({ queryKey: ['board'] })
    }
  })

  const scheduleSave = useMemo(() => {
    let timer: number | undefined
    return () => {
      if (timer) window.clearTimeout(timer)
      timer = window.setTimeout(() => {
        const html = editor?.getHTML() ?? '<p></p>'
        saveMutation.mutate(html)
      }, 650)
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

    const blocks = blocksQuery.data.blocks ?? []
    const html = blocksToHtml(blocks)

    if (templateTypeDraft === 'worklog') {
      const fromPayload = parseWorklogFromPayload(blocks)
      if (fromPayload) {
        setWorklogForm(fromPayload)
        setWorklogReady(true)
        setWorklogFallbackHtml('')
      } else {
        const parsed = parseWorklogFromHtml(html)
        if (parsed) {
          setWorklogForm(parsed)
          setWorklogReady(true)
          setWorklogFallbackHtml('')
        } else {
          setWorklogReady(false)
          setWorklogFallbackHtml(html)
        }
      }
    } else {
      editor.commands.setContent(html || '<p></p>', false)
    }

    loadedItemRef.current = selectedItemId
  }, [editor, selectedItemId, blocksQuery.data, templateTypeDraft])

  const saveWorklogMutation = useMutation({
    mutationFn: async (form: WorklogForm) => {
      if (!selectedItemId) return
      const html = worklogToHtml(form)
      await api.put(`/api/content/${selectedItemId}/blocks`, {
        blocks: [{ sortOrder: 0, type: 'worklog', content: JSON.stringify({ html, worklog: form, format: 'worklog-v1' }) }]
      })
      queryClient.invalidateQueries({ queryKey: ['board'] })
    }
  })

  useEffect(() => {
    if (!selectedItemId || templateTypeDraft !== 'worklog' || !worklogReady) return
    const timer = window.setTimeout(() => {
      saveWorklogMutation.mutate(worklogForm)
    }, 450)
    return () => window.clearTimeout(timer)
  }, [selectedItemId, templateTypeDraft, worklogReady, worklogForm])

  const applyTemplateToCurrent = async () => {
    if (!selectedItemId) return
    if (templateTypeDraft === 'worklog') {
      setWorklogReady(true)
      await saveWorklogMutation.mutateAsync(worklogForm)
      openPopup({ title: '업무일지 적용', message: '업무일지 폼 구조가 적용되었습니다.' })
      return
    }
    const html = templateHtml(templateTypeDraft)
    editor?.commands.setContent(html)
    await api.put(`/api/content/${selectedItemId}/blocks`, {
      blocks: [{ sortOrder: 0, type: 'paragraph', content: JSON.stringify({ html }) }]
    })
    queryClient.invalidateQueries({ queryKey: ['blocks', selectedItemId] })
    queryClient.invalidateQueries({ queryKey: ['board'] })
    openPopup({ title: '템플릿 적용 완료', message: '선택한 템플릿이 반영되었습니다.' })
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
    queryClient.invalidateQueries({ queryKey: ['board'] })
    queryClient.invalidateQueries({ queryKey: ['items-all'] })
    openPopup({ title: '복원 완료', message: '백업 복원이 완료되었습니다.' })
  }

  const importMigrationZip = async (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    const res = await api.post('/api/migration/import', fd)
    setMigrationReport(res.data)
    queryClient.invalidateQueries({ queryKey: ['board'] })
    queryClient.invalidateQueries({ queryKey: ['items-all'] })
    openPopup({ title: '이관 완료', message: '외부 ZIP 이관 처리가 완료되었습니다.' })
  }

  const rows = useMemo(() => {
    const src = boardQuery.data ?? []
    const filtered = search.trim()
      ? src.filter((v) => `${v.title} ${v.todayWork} ${v.issue} ${v.memo}`.toLowerCase().includes(search.toLowerCase()))
      : src
    return [...filtered].sort((a, b) => (a.dueDate < b.dueDate ? 1 : -1))
  }, [boardQuery.data, search])

  const monthTabs = useMemo(() => {
    return Array.from({ length: 6 }, (_, idx) => {
      const d = addMonth(visibleMonth, -idx)
      return ym(d)
    })
  }, [visibleMonth])

  return (
    <div className="space-y-4">
      <section className="rounded-2xl border bg-white p-4 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <CalendarDays size={18} />
            <h3 className="font-semibold text-lg">월간 업무 보드</h3>
          </div>
          <div className="flex items-center gap-2">
            <Button className="px-2 py-1" onClick={() => setVisibleMonth(addMonth(visibleMonth, -1))}><ChevronLeft size={16} /></Button>
            <p className="text-sm font-semibold">{monthLabel(monthKey)}</p>
            <Button className="px-2 py-1" onClick={() => setVisibleMonth(addMonth(visibleMonth, 1))}><ChevronRight size={16} /></Button>
          </div>
        </div>

        <div className="mt-3 flex flex-wrap gap-2">
          {monthTabs.map((tab) => (
            <button
              key={tab}
              onClick={() => setVisibleMonth(new Date(Number(tab.slice(0, 4)), Number(tab.slice(5, 7)) - 1, 1))}
              className={`rounded-full border px-3 py-1 text-sm ${tab === monthKey ? 'bg-ink text-white border-ink' : 'hover:bg-slate-100'}`}
            >
              {monthLabel(tab)}
            </button>
          ))}
        </div>

        <div className="mt-3 grid grid-cols-1 md:grid-cols-[1fr_auto_auto] gap-2">
          <div className="flex items-center gap-2 rounded-md border px-3 py-2">
            <Search size={16} className="text-slate-400" />
            <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="제목/업무/이슈 검색" className="w-full outline-none text-sm" />
          </div>
          <Input type="date" value={selectedDate} onChange={(e) => setSelectedDate(e.target.value)} />
          <Button className="flex items-center gap-2" onClick={() => createMutation.mutate()}><Plus size={16} /> {selectedDate} 일정 추가</Button>
        </div>

        <div className="mt-4 overflow-auto rounded-xl border">
          <table className="min-w-[980px] w-full text-sm">
            <thead className="bg-slate-50 text-slate-600">
              <tr>
                <th className="px-3 py-2 text-left">날짜</th>
                <th className="px-3 py-2 text-left">제목</th>
                <th className="px-3 py-2 text-left">오늘의 업무</th>
                <th className="px-3 py-2 text-left">이슈</th>
                <th className="px-3 py-2 text-left">메모</th>
                <th className="px-3 py-2 text-left">체크</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr
                  key={row.id}
                  onClick={() => {
                    setSelected(row.id)
                    setSelectedDate(row.dueDate)
                  }}
                  className={`cursor-pointer border-t hover:bg-slate-50 ${row.id === selectedItemId ? 'bg-cyan-50' : ''}`}
                >
                  <td className="px-3 py-2 whitespace-nowrap">{row.dueDate}</td>
                  <td className="px-3 py-2">
                    <div className="font-medium">{row.title}</div>
                    <div className="mt-1 inline-flex items-center gap-2">
                      <span className={`px-2 py-0.5 rounded text-xs ${statusClass(row.status)}`}>{STATUS_LABEL[row.status]}</span>
                      <span className="text-xs text-slate-500">{TEMPLATE_OPTIONS.find((v) => v.value === row.templateType)?.label}</span>
                    </div>
                  </td>
                  <td className="px-3 py-2 text-slate-700">{short(row.todayWork)}</td>
                  <td className="px-3 py-2 text-slate-700">{short(row.issue)}</td>
                  <td className="px-3 py-2 text-slate-700">{short(row.memo)}</td>
                  <td className="px-3 py-2 whitespace-nowrap">{row.checklistTotal > 0 ? `${row.checklistDone}/${row.checklistTotal}` : '-'}</td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-3 py-10 text-center text-slate-500">선택한 월에 일정이 없습니다.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="grid grid-cols-1 xl:grid-cols-[1.2fr_1fr] gap-4">
        <div className="rounded-2xl border bg-white p-4 shadow-sm">
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
              <Button onClick={() => updateMutation.mutate()} disabled={!selectedItemId}>속성 저장</Button>
              <Button onClick={applyTemplateToCurrent} disabled={!selectedItemId}>템플릿 적용</Button>
              <label className="inline-flex items-center gap-2 cursor-pointer text-sm border rounded-md px-3 py-2">
                <Upload size={16} /> 이미지
                <input type="file" accept="image/*" className="hidden" onChange={(e) => e.target.files?.[0] && uploadImage(e.target.files[0])} />
              </label>
            </div>
          </div>

          {templateTypeDraft === 'worklog' && selectedItemId && (
            <div className="space-y-4">
              {!worklogReady && (
                <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                  마이그레이션된 원문을 감지했습니다. 폼 변환을 시작하면 필드 기반으로 편집할 수 있습니다.
                  <div className="mt-2">
                    <Button onClick={() => { setWorklogForm(parseWorklogFromHtml(worklogFallbackHtml) ?? EMPTY_WORKLOG); setWorklogReady(true) }}>폼 변환 시작</Button>
                  </div>
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-slate-500">요청자</label>
                  <Input value={worklogForm.requester} onChange={(e) => setWorklogForm((v) => ({ ...v, requester: e.target.value }))} disabled={!worklogReady} />
                </div>
                <div>
                  <label className="text-xs text-slate-500">메뉴</label>
                  <Input value={worklogForm.menu} onChange={(e) => setWorklogForm((v) => ({ ...v, menu: e.target.value }))} disabled={!worklogReady} />
                </div>
              </div>

              <div>
                <label className="text-xs text-slate-500">요청 채널</label>
                <Input value={worklogForm.requestChannel} onChange={(e) => setWorklogForm((v) => ({ ...v, requestChannel: e.target.value }))} disabled={!worklogReady} />
              </div>

              <div>
                <label className="text-xs text-slate-500">요청내용</label>
                <textarea className="w-full rounded-md border border-slate-300 px-3 py-2 min-h-32" value={worklogForm.requestContent} onChange={(e) => setWorklogForm((v) => ({ ...v, requestContent: e.target.value }))} disabled={!worklogReady} />
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div>
                  <label className="text-xs text-slate-500">처리내용 1</label>
                  <textarea className="w-full rounded-md border border-slate-300 px-3 py-2 min-h-32" value={worklogForm.processContent1} onChange={(e) => setWorklogForm((v) => ({ ...v, processContent1: e.target.value }))} disabled={!worklogReady} />
                </div>
                <div>
                  <label className="text-xs text-slate-500">처리내용 2</label>
                  <textarea className="w-full rounded-md border border-slate-300 px-3 py-2 min-h-32" value={worklogForm.processContent2} onChange={(e) => setWorklogForm((v) => ({ ...v, processContent2: e.target.value }))} disabled={!worklogReady} />
                </div>
                <div>
                  <label className="text-xs text-slate-500">처리내용 3</label>
                  <textarea className="w-full rounded-md border border-slate-300 px-3 py-2 min-h-32" value={worklogForm.processContent3} onChange={(e) => setWorklogForm((v) => ({ ...v, processContent3: e.target.value }))} disabled={!worklogReady} />
                </div>
              </div>
            </div>
          )}

          {templateTypeDraft !== 'worklog' && (
            <div className="rounded-xl border bg-slate-50 p-4 min-h-[60vh]">
              <div className="text-xs text-slate-500 mb-2">문서 입력 시 자동 저장됩니다.</div>
              <EditorContent editor={editor} className="prose max-w-none prose-pre:bg-slate-900 prose-pre:text-slate-100 prose-code:text-red-600" />
            </div>
          )}
        </div>

        <aside className="space-y-4">
          <div className="rounded-2xl border bg-[#fcfcfb] p-5 shadow-sm">
            <h3 className="font-semibold mb-3">미리보기</h3>
            <div className="mx-auto rounded-lg border bg-white shadow-sm p-6 min-h-[340px]">
              <div className="prose max-w-none prose-pre:bg-slate-900 prose-pre:text-slate-100 prose-code:text-red-600" dangerouslySetInnerHTML={{ __html: templateTypeDraft === 'worklog' ? (worklogReady ? worklogToHtml(worklogForm) : (worklogFallbackHtml || '<p>표시할 내용이 없습니다.</p>')) : (editor?.getHTML() || '<p>항목을 선택하세요.</p>') }} />
            </div>
          </div>

          <div className="rounded-2xl border bg-white p-4 shadow-sm">
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
        </aside>
      </section>
    </div>
  )
}
