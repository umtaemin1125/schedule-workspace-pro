import { create } from 'zustand'

type AuthUser = {
  id: string
  email: string
  nickname: string
  role: 'USER' | 'ADMIN'
}

type AuthState = {
  accessToken: string | null
  user: AuthUser | null
  initialized: boolean
  setAccessToken: (token: string | null) => void
  setUser: (user: AuthUser | null) => void
  setInitialized: (value: boolean) => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  initialized: false,
  setAccessToken: (token) => set({ accessToken: token }),
  setUser: (user) => set({ user }),
  setInitialized: (initialized) => set({ initialized })
}))
