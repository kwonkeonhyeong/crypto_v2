import { useEffect, useRef, useCallback } from 'react';
import { useSetAtom } from 'jotai';
import { StompClient } from '@/lib/stomp';
import { prayerCountAtom, pendingPrayersAtom } from '@/stores/prayerStore';
import { websocketStateAtom } from '@/stores/websocketStore';
import { addToastAtom } from '@/stores/toastStore';
import type { PrayerCount, Side } from '@/types/prayer';
import type { Ticker } from '@/types/ticker';
import type { Liquidation } from '@/types/liquidation';

const BATCH_INTERVAL = 500; // 500ms batching

interface UsePrayerSocketOptions {
  onTicker?: (ticker: Ticker) => void;
  onLiquidation?: (liquidation: Liquidation) => void;
}

export function usePrayerSocket(options: UsePrayerSocketOptions = {}) {
  const clientRef = useRef<StompClient | null>(null);
  const batchRef = useRef<{ up: number; down: number }>({ up: 0, down: 0 });
  const batchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const optionsRef = useRef(options);

  // Keep options ref updated
  optionsRef.current = options;

  const setPrayerCount = useSetAtom(prayerCountAtom);
  const setPendingPrayers = useSetAtom(pendingPrayersAtom);
  const setWebsocketState = useSetAtom(websocketStateAtom);
  const addToast = useSetAtom(addToastAtom);

  // Flush batch to server
  const flushBatch = useCallback(() => {
    const client = clientRef.current;
    if (!client?.isConnected()) return;

    const { up, down } = batchRef.current;

    if (up > 0) {
      client.send('/app/prayer', { side: 'up', count: up });
    }
    if (down > 0) {
      client.send('/app/prayer', { side: 'down', count: down });
    }

    batchRef.current = { up: 0, down: 0 };
    batchTimerRef.current = null;
  }, []);

  // Pray action (batched)
  const pray = useCallback(
    (side: Side) => {
      // Optimistic update
      setPendingPrayers((prev) => [
        ...prev,
        { side, count: 1, timestamp: Date.now() },
      ]);

      // Add to batch
      batchRef.current[side]++;

      // Set batch timer
      if (!batchTimerRef.current) {
        batchTimerRef.current = setTimeout(flushBatch, BATCH_INTERVAL);
      }
    },
    [flushBatch, setPendingPrayers]
  );

  // WebSocket connection
  useEffect(() => {
    const wsUrl =
      import.meta.env.VITE_WS_URL ||
      `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`;

    const client = new StompClient({
      brokerURL: wsUrl,
      onConnect: () => {
        setWebsocketState({ status: 'connected', error: null, reconnectAttempt: 0 });

        // Subscribe to prayer updates
        client.subscribe('/topic/prayer', (message) => {
          const data: PrayerCount = JSON.parse(message.body);
          setPrayerCount(data);
          // Clear pending on server response
          setPendingPrayers([]);
        });

        // Subscribe to ticker updates
        client.subscribe('/topic/ticker', (message) => {
          const data: Ticker = JSON.parse(message.body);
          optionsRef.current.onTicker?.(data);
        });

        // Subscribe to liquidation updates
        client.subscribe('/topic/liquidation', (message) => {
          const data = JSON.parse(message.body);
          const liquidation: Liquidation = {
            ...data,
            id: `${data.timestamp}-${Math.random().toString(36).substring(2, 9)}`,
          };
          optionsRef.current.onLiquidation?.(liquidation);
        });

        // Subscribe to personal error queue
        client.subscribe('/user/queue/errors', (message) => {
          const error = JSON.parse(message.body);
          if (error.code === 'RATE_LIMIT_EXCEEDED') {
            addToast({
              message: 'Too fast! Please slow down.',
              type: 'warning',
            });
            // Rollback pending
            setPendingPrayers([]);
          }
        });
      },
      onDisconnect: () => {
        setWebsocketState((prev) => ({ ...prev, status: 'disconnected' }));
      },
      onError: (error) => {
        setWebsocketState((prev) => ({ ...prev, error }));
      },
      onReconnecting: (attempt) => {
        setWebsocketState((prev) => ({
          ...prev,
          status: 'reconnecting',
          reconnectAttempt: attempt,
        }));
      },
    });

    clientRef.current = client;

    setWebsocketState({ status: 'connecting', error: null, reconnectAttempt: 0 });
    client.connect();

    return () => {
      client.disconnect();

      if (batchTimerRef.current) {
        clearTimeout(batchTimerRef.current);
      }
    };
  }, [setPrayerCount, setPendingPrayers, setWebsocketState, addToast]);

  return { pray };
}
