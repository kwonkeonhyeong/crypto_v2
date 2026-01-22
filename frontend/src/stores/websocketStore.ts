import { atom } from 'jotai';
import type { ConnectionStatus, WebSocketState } from '@/types/websocket';

export const websocketStateAtom = atom<WebSocketState>({
  status: 'disconnected',
  error: null,
  reconnectAttempt: 0,
});

export const connectionStatusAtom = atom<ConnectionStatus>(
  (get) => get(websocketStateAtom).status
);

export const isConnectedAtom = atom<boolean>(
  (get) => get(websocketStateAtom).status === 'connected'
);
