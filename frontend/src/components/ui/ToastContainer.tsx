import { useAtomValue } from 'jotai';
import { toastsAtom } from '@/stores/toastStore';
import { Toast } from './Toast';

export function ToastContainer() {
  const toasts = useAtomValue(toastsAtom);

  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((toast) => (
        <Toast key={toast.id} toast={toast} />
      ))}
    </div>
  );
}
