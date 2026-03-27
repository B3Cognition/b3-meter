/**
 * Run store — tracks the active JMeter test run state and metrics ring buffer.
 */

import { create } from 'zustand';
import type { MetricsBucket } from '../types/sse-events.js';

/** Maximum number of MetricsBucket entries kept in memory. */
const RING_BUFFER_MAX = 300;

export type RunStatus = 'idle' | 'starting' | 'running' | 'stopping' | 'stopped' | 'error';

export interface RunState {
  runId: string | null;
  status: RunStatus;
  /** Ring buffer capped at RING_BUFFER_MAX entries. */
  samples: MetricsBucket[];
  startedAt: string | null;
  setRunId: (id: string | null) => void;
  setStatus: (status: RunStatus) => void;
  addSample: (bucket: MetricsBucket) => void;
  clearSamples: () => void;
  reset: () => void;
}

export const useRunStore = create<RunState>()((set) => ({
  runId: null,
  status: 'idle',
  samples: [],
  startedAt: null,

  setRunId: (id) => set({ runId: id }),

  setStatus: (status) => set({ status }),

  addSample: (bucket) =>
    set((state) => {
      const samples =
        state.samples.length < RING_BUFFER_MAX
          ? [...state.samples, bucket]
          : [...state.samples.slice(1), bucket];
      return { samples };
    }),

  clearSamples: () => set({ samples: [] }),

  reset: () =>
    set({
      runId: null,
      status: 'idle',
      samples: [],
      startedAt: null,
    }),
}));
