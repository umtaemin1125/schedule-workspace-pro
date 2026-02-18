import { create } from 'zustand'

type PopupPayload = {
  title: string
  message: string
  confirmText?: string
  onConfirm?: () => void
}

type PopupState = {
  popup: PopupPayload | null
  openPopup: (payload: PopupPayload) => void
  closePopup: () => void
}

export const usePopupStore = create<PopupState>((set, get) => ({
  popup: null,
  openPopup: (payload) => set({ popup: payload }),
  closePopup: () => {
    const current = get().popup
    set({ popup: null })
    if (current?.onConfirm) current.onConfirm()
  }
}))
