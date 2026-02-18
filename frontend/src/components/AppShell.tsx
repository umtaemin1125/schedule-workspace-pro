import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import Image from '@tiptap/extension-image'
import { Search, Plus, Upload } from 'lucide-react'
import { api } from '../lib/api'
import { WorkspaceItem, useWorkspaceStore } from '../store/workspace'
import { Button, Input } from './ui'

export function AppShell() {
  const queryClient = useQueryClient()
  const selectedItemId = useWorkspaceStore((s) => s.selectedItemId)
  const setSelected = useWorkspaceStore((s) => s.setSelectedItemId)
  const [search, setSearch] = useState('')

  const itemsQuery = useQuery<WorkspaceItem[]>({
    queryKey: ['items', search],
    queryFn: async () => (await api.get('/api/workspace/items', { params: search ? { q: search } : undefined })).data
  })

  const createMutation = useMutation({
    mutationFn: async () => (await api.post('/api/workspace/items', { title: '새 항목' })).data,
    onSuccess: (item: WorkspaceItem) => {
      queryClient.invalidateQueries({ queryKey: ['items'] })
      setSelected(item.id)
    }
  })

  const blocksQuery = useQuery({
    queryKey: ['blocks', selectedItemId],
    enabled: !!selectedItemId,
    queryFn: async () => (await api.get(`/api/content/${selectedItemId}/blocks`)).data
  })

  const editor = useEditor({
    extensions: [StarterKit, TaskList, TaskItem.configure({ nested: true }), Image],
    content: '<p>항목을 선택하세요.</p>',
    onUpdate: () => scheduleSave()
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
      }, 600)
    }
  }, [editor, saveMutation])

  if (blocksQuery.data?.blocks && editor) {
    const html = JSON.parse(blocksQuery.data.blocks[0]?.content ?? '{"html":"<p></p>"}').html
    if (html !== editor.getHTML()) editor.commands.setContent(html)
  }

  const selected = itemsQuery.data?.find((v) => v.id === selectedItemId)

  const uploadImage = async (file: File) => {
    if (!selectedItemId) return
    const fd = new FormData()
    fd.append('itemId', selectedItemId)
    fd.append('file', file)
    const res = await api.post('/api/files/upload', fd)
    editor?.chain().focus().setImage({ src: `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}${res.data.url}` }).run()
  }

  return (
    <div className="grid grid-cols-[280px_1fr] gap-4">
      <aside className="border rounded-lg bg-white p-3 overflow-auto min-h-[72vh]">
        <div className="flex items-center gap-2">
          <Search size={16} />
          <Input placeholder="검색" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <Button className="mt-3 w-full flex items-center justify-center gap-2" onClick={() => createMutation.mutate()}>
          <Plus size={16} /> 새 항목
        </Button>
        <ul className="mt-4 space-y-1">
          {itemsQuery.data?.map((item) => (
            <li key={item.id}>
              <button onClick={() => setSelected(item.id)} className={`w-full text-left rounded px-2 py-2 ${item.id === selectedItemId ? 'bg-mint text-white' : 'hover:bg-slate-100'}`}>
                {item.title}
              </button>
            </li>
          ))}
        </ul>
      </aside>

      <main>
        <div className="mb-3 rounded-lg bg-white border p-3 flex items-center gap-3">
          <span className="font-semibold">속성</span>
          <span>상태: {selected?.status ?? '-'}</span>
          <span>날짜: {selected?.dueDate ?? '-'}</span>
          <span>수정일: {selected?.updatedAt?.slice(0, 10) ?? '-'}</span>
          <label className="ml-auto inline-flex items-center gap-2 cursor-pointer text-sm">
            <Upload size={16} /> 이미지
            <input type="file" accept="image/*" className="hidden" onChange={(e) => e.target.files?.[0] && uploadImage(e.target.files[0])} />
          </label>
        </div>

        <div className="rounded-lg border bg-white p-4 min-h-[64vh]">
          <div className="text-xs text-slate-500 mb-2">/ 입력 후 블록 메뉴 확장 가능(Tiptap 확장 포인트)</div>
          <EditorContent editor={editor} className="prose max-w-none" />
        </div>
      </main>
    </div>
  )
}
