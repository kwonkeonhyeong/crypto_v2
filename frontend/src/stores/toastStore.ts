import { atom } from 'jotai';

export interface Toast {
  id: string;
  message: string;
  type: 'info' | 'success' | 'error' | 'warning';
  duration?: number;
}

export const toastsAtom = atom<Toast[]>([]);

// Add toast action
export const addToastAtom = atom(
  null,
  (get, set, toast: Omit<Toast, 'id'>) => {
    const id = `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
    const newToast: Toast = { ...toast, id };

    set(toastsAtom, [...get(toastsAtom), newToast]);

    // Auto remove after duration
    const duration = toast.duration ?? 3000;
    setTimeout(() => {
      set(toastsAtom, (prev) => prev.filter((t) => t.id !== id));
    }, duration);
  }
);

// Remove toast action
export const removeToastAtom = atom(null, (_get, set, id: string) => {
  set(toastsAtom, (prev) => prev.filter((t) => t.id !== id));
});
