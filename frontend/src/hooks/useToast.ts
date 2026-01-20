import { useSetAtom, useAtomValue } from 'jotai';
import { toastsAtom, addToastAtom, removeToastAtom, type Toast } from '@/stores/toastStore';

export function useToast() {
  const toasts = useAtomValue(toastsAtom);
  const addToast = useSetAtom(addToastAtom);
  const removeToast = useSetAtom(removeToastAtom);

  const toast = (options: Omit<Toast, 'id'>) => {
    addToast(options);
  };

  const info = (message: string, duration?: number) => {
    addToast({ message, type: 'info', duration });
  };

  const success = (message: string, duration?: number) => {
    addToast({ message, type: 'success', duration });
  };

  const error = (message: string, duration?: number) => {
    addToast({ message, type: 'error', duration });
  };

  const warning = (message: string, duration?: number) => {
    addToast({ message, type: 'warning', duration });
  };

  return {
    toasts,
    toast,
    info,
    success,
    error,
    warning,
    dismiss: removeToast,
  };
}
