export type ConnectionStatus =
  | 'disconnected'
  | 'connecting'
  | 'connected'
  | 'reconnecting';

export interface WebSocketState {
  status: ConnectionStatus;
  error: string | null;
  reconnectAttempt: number;
}

export interface StompMessage<T = unknown> {
  type: string;
  payload: T;
  timestamp: number;
}
