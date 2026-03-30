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
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LiveDashboard } from '../components/Dashboard/LiveDashboard.js';
import { useRunStore } from '../store/runStore.js';
import type { MetricsBucket } from '../types/sse-events.js';
import type { ReactNode } from 'react';

// ---------------------------------------------------------------------------
// Mock recharts ResponsiveContainer so children render in jsdom (no dimensions)
// ---------------------------------------------------------------------------

vi.mock('recharts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('recharts')>();
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: ReactNode }) => (
      <div data-testid="responsive-container">{children}</div>
    ),
  };
});

// ---------------------------------------------------------------------------
// Stub EventSource so useRunStream doesn't fail in jsdom
// ---------------------------------------------------------------------------

class NoopEventSource {
  addEventListener(): void {}
  close(): void {}
}

beforeEach(() => {
  vi.stubGlobal('EventSource', NoopEventSource);
  useRunStore.getState().reset();
});

afterEach(() => {
  vi.unstubAllGlobals();
  useRunStore.getState().reset();
});

// ---------------------------------------------------------------------------
// Recharts uses ResizeObserver — provide a stub for jsdom.
// Must be done before any render calls; using a module-level assignment so
// it is in place before React fires any effects.
// ---------------------------------------------------------------------------

class StubResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

// Install globally so Recharts' ResponsiveContainer finds it
Object.defineProperty(globalThis, 'ResizeObserver', {
  writable: true,
  configurable: true,
  value: StubResizeObserver,
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeBase(offsetSeconds = 0): MetricsBucket {
  const ts = new Date(Date.now() - offsetSeconds * 1000).toISOString();
  return {
    timestamp: ts,
    samplerLabel: 'all',
    sampleCount: 20,
    errorCount: 1,
    avgResponseTime: 120,
    percentile90: 180,
    percentile95: 220,
    percentile99: 350,
    samplesPerSecond: 10,
  };
}

function loadSamples(buckets: MetricsBucket[]): void {
  for (const b of buckets) useRunStore.getState().addSample(b);
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('LiveDashboard — structure', () => {
  it('renders the Live Dashboard heading', () => {
    render(<LiveDashboard />);
    expect(screen.getByText('Live Dashboard')).toBeInTheDocument();
  });

  it('shows Idle status when no runId is provided', () => {
    render(<LiveDashboard />);
    expect(screen.getByText(/Idle/)).toBeInTheDocument();
  });

  it('includes the runId in the status label when provided', () => {
    useRunStore.getState().setStatus('running');
    render(<LiveDashboard runId="run-xyz" />);
    expect(screen.getByText(/run-xyz/)).toBeInTheDocument();
  });
});

describe('LiveDashboard — empty state', () => {
  it('shows waiting placeholders when there are no samples', () => {
    render(<LiveDashboard runId="run-1" />);
    const waiting = screen.getAllByText(/Waiting for data/i);
    // Three charts, each with an empty placeholder
    expect(waiting.length).toBeGreaterThanOrEqual(3);
  });
});

describe('LiveDashboard — chart titles', () => {
  it('renders the Throughput chart title', () => {
    loadSamples([makeBase(5), makeBase(4), makeBase(3)]);
    render(<LiveDashboard runId="run-1" />);
    expect(screen.getByText(/Throughput/i)).toBeInTheDocument();
  });

  it('renders the Response Time chart title', () => {
    loadSamples([makeBase(5), makeBase(4), makeBase(3)]);
    render(<LiveDashboard runId="run-1" />);
    expect(screen.getByText(/Response Time/i)).toBeInTheDocument();
  });

  it('renders the Error Rate chart title', () => {
    loadSamples([makeBase(5), makeBase(4), makeBase(3)]);
    render(<LiveDashboard runId="run-1" />);
    expect(screen.getByText(/Error Rate/i)).toBeInTheDocument();
  });
});

describe('LiveDashboard — data-driven rendering', () => {
  it('renders charts when samples are present (no waiting placeholders)', () => {
    loadSamples([makeBase(3), makeBase(2), makeBase(1)]);
    render(<LiveDashboard runId="run-1" />);
    const waiting = screen.queryAllByText(/Waiting for data/i);
    expect(waiting).toHaveLength(0);
  });

  it('responds to store updates — shows charts after samples added', () => {
    const { rerender } = render(<LiveDashboard runId="run-1" />);
    expect(screen.getAllByText(/Waiting for data/i).length).toBeGreaterThan(0);

    loadSamples([makeBase(2), makeBase(1)]);
    rerender(<LiveDashboard runId="run-1" />);

    expect(screen.queryAllByText(/Waiting for data/i)).toHaveLength(0);
  });
});

describe('LiveDashboard — status display', () => {
  it('shows Running status', () => {
    useRunStore.getState().setStatus('running');
    render(<LiveDashboard runId="run-1" />);
    expect(screen.getByText(/Running/)).toBeInTheDocument();
  });

  it('shows Stopped status', () => {
    useRunStore.getState().setStatus('stopped');
    render(<LiveDashboard runId="run-1" />);
    expect(screen.getByText(/Stopped/)).toBeInTheDocument();
  });

  it('shows Error status', () => {
    useRunStore.getState().setStatus('error');
    render(<LiveDashboard runId="run-1" />);
    // The status label contains "Error"; use getAllByText because "Error Rate"
    // chart title is also present — verify at least one match in the status div
    const matches = screen.getAllByText(/Error/);
    expect(matches.length).toBeGreaterThan(0);
    // Confirm the status indicator div specifically contains "Error"
    const statusEl = document.querySelector('.live-dashboard__status');
    expect(statusEl?.textContent).toContain('Error');
  });
});

describe('LiveDashboard — response time chart', () => {
  it('renders the ResponseTimeChart card when samples are present', () => {
    loadSamples([makeBase(2), makeBase(1)]);
    render(<LiveDashboard runId="run-1" />);
    // The chart title is always rendered inside the card
    expect(screen.getByText('Response Time (ms)')).toBeInTheDocument();
    // A responsive container wrapper should be present for the chart
    const containers = document.querySelectorAll('[data-testid="responsive-container"]');
    expect(containers.length).toBeGreaterThanOrEqual(1);
  });

  it('shows all three chart cards (throughput, response time, error rate)', () => {
    loadSamples([makeBase(2), makeBase(1)]);
    render(<LiveDashboard runId="run-1" />);
    expect(screen.getByText('Throughput (samples/sec)')).toBeInTheDocument();
    expect(screen.getByText('Response Time (ms)')).toBeInTheDocument();
    expect(screen.getByText('Error Rate (%)')).toBeInTheDocument();
  });
});
