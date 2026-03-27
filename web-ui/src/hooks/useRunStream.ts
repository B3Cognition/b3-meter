/**
 * useRunStream — React hook that opens an SSE connection to a run's event stream
 * and feeds MetricsBucket data into runStore.
 *
 * Features:
 * - Connects to /api/v1/stream/{runId}
 * - Parses incoming SSE data as MetricsBucket and calls runStore.addSample()
 * - Reconnects on error with exponential backoff: 1s, 2s, 4s, … capped at 30s
 * - Sends Last-Event-ID header on reconnect (via EventSource's built-in support)
 * - Cleans up EventSource on component unmount
 */

import { useEffect, useRef } from 'react';
import { useRunStore } from '../store/runStore.js';
import type { MetricsBucket } from '../types/sse-events.js';

const SSE_STREAM_BASE = '/api/v1/stream';

/** Backoff schedule in milliseconds; last value is the ceiling. */
const BACKOFF_MS = [1000, 2000, 4000, 8000, 16000, 30000];

function getBackoff(attempt: number): number {
  const idx = Math.min(attempt, BACKOFF_MS.length - 1);
  return BACKOFF_MS[idx]!;
}

export interface UseRunStreamOptions {
  /** Suspend the connection when false (e.g. run is terminal). */
  enabled?: boolean;
}

export function useRunStream(
  runId: string | null,
  options: UseRunStreamOptions = {},
): void {
  const { enabled = true } = options;
  const addSample = useRunStore((s) => s.addSample);

  /** Tracks the last event ID seen so we can resume after reconnect. */
  const lastEventIdRef = useRef<string>('');
  /** Tracks how many consecutive connection failures have occurred. */
  const attemptRef = useRef<number>(0);
  /** Holds the active reconnect timer so it can be cancelled on cleanup. */
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** Holds the active EventSource so it can be closed on cleanup. */
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!runId || !enabled) {
      return;
    }

    let cancelled = false;

    function connect(): void {
      if (cancelled) return;

      const url = new URL(
        `${SSE_STREAM_BASE}/${encodeURIComponent(runId!)}`,
        window.location.href,
      );

      // The EventSource API automatically sends Last-Event-ID when the
      // browser reconnects, but since we manage reconnects manually we
      // append it as a query param so the backend can restore position.
      if (lastEventIdRef.current) {
        url.searchParams.set('lastEventId', lastEventIdRef.current);
      }

      const es = new EventSource(url.toString());
      esRef.current = es;

      es.addEventListener('sample.bucket', (event: MessageEvent<string>) => {
        // Track Last-Event-ID for resume capability
        if (event.lastEventId) {
          lastEventIdRef.current = event.lastEventId;
        }

        let bucket: MetricsBucket;
        try {
          bucket = JSON.parse(event.data) as MetricsBucket;
        } catch {
          // Ignore malformed frames
          return;
        }

        addSample(bucket);
        // Successful message resets the backoff counter
        attemptRef.current = 0;
      });

      es.addEventListener('error', () => {
        es.close();
        esRef.current = null;

        if (cancelled) return;

        const delay = getBackoff(attemptRef.current);
        attemptRef.current += 1;

        timerRef.current = setTimeout(() => {
          if (!cancelled) connect();
        }, delay);
      });
    }

    connect();

    return () => {
      cancelled = true;
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      if (esRef.current) {
        esRef.current.close();
        esRef.current = null;
      }
      // Reset counters so a fresh mount starts clean
      attemptRef.current = 0;
      lastEventIdRef.current = '';
    };
  }, [runId, enabled, addSample]);
}
