import { Card, Button } from './ui'
import { usePopupStore } from '../store/popup'

export function GlobalPopup() {
  const popup = usePopupStore((s) => s.popup)
  const closePopup = usePopupStore((s) => s.closePopup)

  if (!popup) return null

  return (
    <div className="fixed inset-0 bg-black/40 z-[60] flex items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <h3 className="text-xl font-bold text-ink">{popup.title}</h3>
        <p className="text-sm text-slate-600 mt-2 whitespace-pre-line">{popup.message}</p>
        <div className="mt-4 flex justify-end">
          <Button onClick={closePopup}>{popup.confirmText ?? '확인'}</Button>
        </div>
      </Card>
    </div>
  )
}
