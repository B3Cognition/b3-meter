// Copyright 2024-2026 b3meter Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
import { describe, it, expect, beforeEach } from 'vitest';
import { useRunStore } from '../store/runStore.js';
import type { MetricsBucket } from '../types/sse-events.js';

/** Helper: create a minimal MetricsBucket. */
function makeBucket(label: string, timestamp = '2024-01-01T00:00:00Z'): MetricsBucket {
  return {
    timestamp,
    samplerLabel: label,
    sampleCount: 1,
    errorCount: 0,
    avgResponseTime: 100,
    percentile90: 120,
    percentile95: 150,
    percentile99: 200,
    samplesPerSecond: 10,
  };
}

beforeEach(() => {
  useRunStore.getState().reset();
});

describe('runStore — initial state', () => {
  it('starts in idle status with no runId', () => {
    const { runId, status, samples, startedAt } = useRunStore.getState();
    expect(runId).toBeNull();
    expect(status).toBe('idle');
    expect(samples).toHaveLength(0);
    expect(startedAt).toBeNull();
  });
});

describe('runStore — setRunId', () => {
  it('sets the runId', () => {
    useRunStore.getState().setRunId('run-123');
    expect(useRunStore.getState().runId).toBe('run-123');
  });

  it('clears the runId when null is passed', () => {
    useRunStore.getState().setRunId('run-123');
    useRunStore.getState().setRunId(null);
    expect(useRunStore.getState().runId).toBeNull();
  });
});

describe('runStore — setStatus', () => {
  it('transitions from idle to starting', () => {
    useRunStore.getState().setStatus('starting');
    expect(useRunStore.getState().status).toBe('starting');
  });

  it('transitions through all valid status values', () => {
    const statuses = ['starting', 'running', 'stopping', 'stopped', 'error', 'idle'] as const;
    for (const s of statuses) {
      useRunStore.getState().setStatus(s);
      expect(useRunStore.getState().status).toBe(s);
    }
  });
});

describe('runStore — addSample ring buffer', () => {
  it('accumulates samples up to the cap', () => {
    for (let i = 0; i < 100; i++) {
      useRunStore.getState().addSample(makeBucket(`sampler-${i}`));
    }
    expect(useRunStore.getState().samples).toHaveLength(100);
  });

  it('caps samples at 300 entries', () => {
    for (let i = 0; i < 310; i++) {
      useRunStore.getState().addSample(makeBucket(`sampler-${i}`));
    }
    expect(useRunStore.getState().samples).toHaveLength(300);
  });

  it('drops the oldest entry when the buffer overflows', () => {
    // Fill to exactly 300
    for (let i = 0; i < 300; i++) {
      useRunStore.getState().addSample(makeBucket(`sampler-${i}`));
    }
    // 301st entry should evict the first
    useRunStore.getState().addSample(makeBucket('sampler-new'));
    const samples = useRunStore.getState().samples;
    expect(samples).toHaveLength(300);
    expect(samples[0]?.samplerLabel).toBe('sampler-1');
    expect(samples[299]?.samplerLabel).toBe('sampler-new');
  });

  it('preserves insertion order within the ring buffer', () => {
    useRunStore.getState().addSample(makeBucket('a'));
    useRunStore.getState().addSample(makeBucket('b'));
    useRunStore.getState().addSample(makeBucket('c'));
    const labels = useRunStore.getState().samples.map((s) => s.samplerLabel);
    expect(labels).toEqual(['a', 'b', 'c']);
  });
});

describe('runStore — clearSamples', () => {
  it('empties the sample buffer', () => {
    useRunStore.getState().addSample(makeBucket('a'));
    useRunStore.getState().addSample(makeBucket('b'));
    useRunStore.getState().clearSamples();
    expect(useRunStore.getState().samples).toHaveLength(0);
  });

  it('does not affect other state fields', () => {
    useRunStore.getState().setRunId('run-1');
    useRunStore.getState().setStatus('running');
    useRunStore.getState().clearSamples();
    expect(useRunStore.getState().runId).toBe('run-1');
    expect(useRunStore.getState().status).toBe('running');
  });
});

describe('runStore — reset', () => {
  it('resets all state to initial values', () => {
    useRunStore.getState().setRunId('run-999');
    useRunStore.getState().setStatus('running');
    useRunStore.getState().addSample(makeBucket('x'));
    useRunStore.getState().reset();
    const { runId, status, samples, startedAt } = useRunStore.getState();
    expect(runId).toBeNull();
    expect(status).toBe('idle');
    expect(samples).toHaveLength(0);
    expect(startedAt).toBeNull();
  });
});
