import { ButtonHTMLAttributes, InputHTMLAttributes, PropsWithChildren } from 'react'

export function Button(props: ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button {...props} className={`rounded-md px-3 py-2 bg-ink text-white hover:bg-slate-800 disabled:opacity-50 ${props.className ?? ''}`} />
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`w-full rounded-md border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-mint ${props.className ?? ''}`} />
}

export function Card({ children, className = '' }: PropsWithChildren<{ className?: string }>) {
  return <div className={`rounded-xl border border-slate-200 bg-white p-4 shadow-sm ${className}`}>{children}</div>
}
