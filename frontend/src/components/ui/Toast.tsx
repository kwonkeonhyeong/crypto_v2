import { useSetAtom } from 'jotai';
import { clsx } from 'clsx';
import { removeToastAtom, type Toast as ToastType } from '@/stores/toastStore';

interface ToastProps {
  toast: ToastType;
}

export function Toast({ toast }: ToastProps) {
  const removeToast = useSetAtom(removeToastAtom);

  const bgColor = {
    info: 'bg-blue-500',
    success: 'bg-green-500',
    error: 'bg-red-500',
    warning: 'bg-yellow-500',
  }[toast.type];

  return (
    <div
      className={clsx(
        'px-4 py-3 rounded-lg text-white shadow-lg',
        'animate-slide-up',
        bgColor
      )}
    >
      <div className="flex items-center gap-2">
        <span>{toast.message}</span>
        <button
          onClick={() => removeToast(toast.id)}
          className="ml-2 opacity-70 hover:opacity-100 transition-opacity"
          aria-label="Dismiss"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    </div>
  );
}
