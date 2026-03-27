/**
 * Tests for useRunStream hook — reconnect logic, data parsing, cleanup.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useRunStream } from '../hooks/useRunStream.js';
import { useRunStore } from '../store/runStore.js';
import type { MetricsBucket } from '../types/sse-events.js';

// ---------------------------------------------------------------------------
// Minimal EventSource mock
// ---------------------------------------------------------------------------

interface MockESInstance {
  url: string;
  listeners: Record<string, ((e: MessageEvent | Event) => void)[]>;
  closed: boolean;
  close: () => void;
  addEventListener: (type: string, cb: (e: MessageEvent | Event) => void) => void;
  /** Test helpers — trigger events programmatically */
  emit: (type: 'message', data: string, lastEventId?: string) => void;
  emitError: () => void;
}

let instances: MockESInstance[] = [];

class MockEventSource implements MockESInstance {
  url: string;
  listeners: Record<string, ((e: MessageEvent | Event) => void)[]> = {};
  closed = false;

  constructor(url: string) {
    this.url = url;
    instances.push(this);
  }

  addEventListener(type: string, cb: (e: MessageEvent | Event) => void): void {
    if (!this.listeners[type]) this.listeners[type] = [];
    this.listeners[type]!.push(cb);
  }

  close(): void {
    this.closed = true;
  }

  emit(type: 'message', data: string, lastEventId = ''): void {
    const event = new MessageEvent('message', { data, lastEventId });
    for (const cb of this.listeners[type] ?? []) cb(event);
  }

  emitError(): void {
    for (const cb of this.listeners['error'] ?? []) cb(new Event('error'));
  }
}

function makeBucket(overrides: Partial<MetricsBucket> = {}): MetricsBucket {
  return {
    timestamp: new Date().toISOString(),
    samplerLabel: 'test-sampler',
    sampleCount: 10,
    errorCount: 0,
    avgResponseTime: 100,
    percentile90: 120,
    percentile95: 150,
    percentile99: 200,
    samplesPerSecond: 5,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

beforeEach(() => {
  instances = [];
  vi.useFakeTimers();
  // Replace global EventSource
  vi.stubGlobal('EventSource', MockEventSource);
  useRunStore.getState().reset();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('useRunStream — initial connection', () => {
  it('opens an EventSource when runId is provided', () => {
    renderHook(() => useRunStream('run-1'));
    expect(instances).toHaveLength(1);
    expect(instances[0]!.url).toContain('run-1');
  });

  it('does not open a connection when runId is null', () => {
    renderHook(() => useRunStream(null));
    expect(instances).toHaveLength(0);
  });

  it('does not open a connection when enabled is false', () => {
    renderHook(() => useRunStream('run-1', { enabled: false }));
    expect(instances).toHaveLength(0);
  });
});

describe('useRunStream — data parsing', () => {
  it('parses a valid MetricsBucket message and adds it to runStore', () => {
    renderHook(() => useRunStream('run-abc'));
    const es = instances[0]!;
    const bucket = makeBucket({ samplesPerSecond: 42, avgResponseTime: 99 });

    act(() => {
      es.emit('message', JSON.stringify(bucket));
    });

    const samples = useRunStore.getState().samples;
    expect(samples).toHaveLength(1);
    expect(samples[0]!.samplesPerSecond).toBe(42);
    expect(samples[0]!.avgResponseTime).toBe(99);
  });

  it('ignores malformed JSON without throwing', () => {
    renderHook(() => useRunStream('run-abc'));
    const es = instances[0]!;

    act(() => {
      es.emit('message', 'not-valid-json');
    });

    expect(useRunStore.getState().samples).toHaveLength(0);
  });

  it('stores lastEventId from the SSE frame', () => {
    const { unmount } = renderHook(() => useRunStream('run-abc'));
    const es = instances[0]!;
    const bucket = makeBucket();

    act(() => {
      es.emit('message', JSON.stringify(bucket), 'evt-42');
    });

    // Trigger a reconnect to verify lastEventId is sent in the new URL
    act(() => {
      es.emitError();
    });
    act(() => {
      vi.advanceTimersByTime(1100);
    });

    expect(instances[1]!.url).toContain('lastEventId=evt-42');
    unmount();
  });
});

describe('useRunStream — reconnect with exponential backoff', () => {
  it('reconnects after the first error with a 1 second delay', () => {
    renderHook(() => useRunStream('run-1'));
    expect(instances).toHaveLength(1);

    act(() => { instances[0]!.emitError(); });
    expect(instances).toHaveLength(1); // not yet reconnected

    act(() => { vi.advanceTimersByTime(999); });
    expect(instances).toHaveLength(1); // still waiting

    act(() => { vi.advanceTimersByTime(1); });
    expect(instances).toHaveLength(2); // reconnected after 1 s
  });

  it('doubles the delay on successive failures (2 s, 4 s)', () => {
    renderHook(() => useRunStream('run-1'));

    // First failure → 1 s backoff
    act(() => { instances[0]!.emitError(); });
    act(() => { vi.advanceTimersByTime(1000); });
    expect(instances).toHaveLength(2);

    // Second failure → 2 s backoff
    act(() => { instances[1]!.emitError(); });
    act(() => { vi.advanceTimersByTime(1999); });
    expect(instances).toHaveLength(2); // not yet
    act(() => { vi.advanceTimersByTime(1); });
    expect(instances).toHaveLength(3); // reconnected after 2 s

    // Third failure → 4 s backoff
    act(() => { instances[2]!.emitError(); });
    act(() => { vi.advanceTimersByTime(3999); });
    expect(instances).toHaveLength(3);
    act(() => { vi.advanceTimersByTime(1); });
    expect(instances).toHaveLength(4);
  });

  it('caps backoff at 30 seconds regardless of failure count', () => {
    renderHook(() => useRunStream('run-1'));

    // Exhaust the backoff table (6 entries, last = 30 s)
    // BACKOFF_MS = [1000, 2000, 4000, 8000, 16000, 30000]
    const delays = [1000, 2000, 4000, 8000, 16000];
    for (const delay of delays) {
      act(() => { instances[instances.length - 1]!.emitError(); });
      act(() => { vi.advanceTimersByTime(delay); });
    }

    const countBefore = instances.length;
    // 6th failure should use 30 s ceiling
    act(() => { instances[instances.length - 1]!.emitError(); });
    act(() => { vi.advanceTimersByTime(29999); });
    expect(instances).toHaveLength(countBefore);
    act(() => { vi.advanceTimersByTime(1); });
    expect(instances).toHaveLength(countBefore + 1);
  });

  it('resets backoff counter after a successful message', () => {
    renderHook(() => useRunStream('run-1'));

    // Cause one failure + reconnect
    act(() => { instances[0]!.emitError(); });
    act(() => { vi.advanceTimersByTime(1000); });
    expect(instances).toHaveLength(2);

    // Receive a successful message (resets attempt counter)
    act(() => {
      instances[1]!.emit('message', JSON.stringify(makeBucket()));
    });

    // Next failure should again use 1 s backoff
    act(() => { instances[1]!.emitError(); });
    act(() => { vi.advanceTimersByTime(999); });
    expect(instances).toHaveLength(2);
    act(() => { vi.advanceTimersByTime(1); });
    expect(instances).toHaveLength(3);
  });
});

describe('useRunStream — cleanup on unmount', () => {
  it('closes the EventSource when the component unmounts', () => {
    const { unmount } = renderHook(() => useRunStream('run-1'));
    const es = instances[0]!;
    expect(es.closed).toBe(false);
    unmount();
    expect(es.closed).toBe(true);
  });

  it('cancels a pending reconnect timer on unmount', () => {
    const { unmount } = renderHook(() => useRunStream('run-1'));

    act(() => { instances[0]!.emitError(); });
    unmount(); // cancel before 1 s elapses

    act(() => { vi.advanceTimersByTime(2000); });
    // No new instance should have been created
    expect(instances).toHaveLength(1);
  });
});
