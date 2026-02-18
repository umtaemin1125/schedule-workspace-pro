import { create } from 'zustand'

type AuthState = {
  accessToken: string | null
  initialized: boolean
  setAccessToken: (token: string | null) => void
  setInitialized: (value: boolean) => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  initialized: false,
  setAccessToken: (token) => set({ accessToken: token }),
  setInitialized: (initialized) => set({ initialized })
}))
