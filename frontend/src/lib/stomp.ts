import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { ExponentialBackoff } from './exponentialBackoff';

export interface StompConfig {
  brokerURL: string;
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (error: string) => void;
  onReconnecting?: (attempt: number) => void;
}

export class StompClient {
  private client: Client;
  private backoff: ExponentialBackoff;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private config: StompConfig;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private isManualDisconnect = false;

  constructor(config: StompConfig) {
    this.config = config;
    this.backoff = new ExponentialBackoff();

    this.client = new Client({
      brokerURL: config.brokerURL,
      reconnectDelay: 0, // Manual reconnection management
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => this.handleConnect(),
      onDisconnect: () => this.handleDisconnect(),
      onStompError: (frame) => this.handleError(frame.body),
      onWebSocketError: () => this.handleError('WebSocket error'),
    });
  }

  connect(): void {
    this.isManualDisconnect = false;
    if (!this.client.active) {
      this.client.activate();
    }
  }

  disconnect(): void {
    this.isManualDisconnect = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.client.deactivate();
  }

  subscribe(
    destination: string,
    callback: (message: IMessage) => void
  ): () => void {
    // If already connected, subscribe immediately
    if (this.client.connected) {
      return this.doSubscribe(destination, callback);
    }

    // Otherwise, queue the subscription for when we connect
    const unsubscribe = () => {
      const sub = this.subscriptions.get(destination);
      if (sub) {
        sub.unsubscribe();
        this.subscriptions.delete(destination);
      }
    };

    // Store the callback for later subscription
    const originalOnConnect = this.config.onConnect;
    this.config.onConnect = () => {
      originalOnConnect?.();
      if (!this.subscriptions.has(destination)) {
        this.doSubscribe(destination, callback);
      }
    };

    return unsubscribe;
  }

  private doSubscribe(
    destination: string,
    callback: (message: IMessage) => void
  ): () => void {
    if (this.subscriptions.has(destination)) {
      this.subscriptions.get(destination)?.unsubscribe();
    }

    const subscription = this.client.subscribe(destination, callback);
    this.subscriptions.set(destination, subscription);

    return () => {
      subscription.unsubscribe();
      this.subscriptions.delete(destination);
    };
  }

  send(destination: string, body: object): void {
    if (this.client.connected) {
      this.client.publish({
        destination,
        body: JSON.stringify(body),
      });
    }
  }

  private handleConnect(): void {
    this.backoff.reset();
    this.config.onConnect?.();
  }

  private handleDisconnect(): void {
    this.config.onDisconnect?.();
    if (!this.isManualDisconnect) {
      this.scheduleReconnect();
    }
  }

  private handleError(error: string): void {
    this.config.onError?.(error);
    if (!this.isManualDisconnect) {
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) return;

    const delay = this.backoff.nextDelayMs();
    this.config.onReconnecting?.(this.backoff.getAttempt());

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }

  isConnected(): boolean {
    return this.client.connected;
  }
}
