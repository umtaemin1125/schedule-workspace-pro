import { create } from 'zustand'

export type WorkspaceItem = {
  id: string
  parentId: string | null
  title: string
  status: 'todo' | 'doing' | 'done'
  dueDate: string | null
  templateType: 'free' | 'worklog' | 'meeting'
  updatedAt: string
}

type WorkspaceState = {
  selectedItemId: string | null
  setSelectedItemId: (id: string | null) => void
}

export const useWorkspaceStore = create<WorkspaceState>((set) => ({
  selectedItemId: null,
  setSelectedItemId: (id) => set({ selectedItemId: id })
}))
