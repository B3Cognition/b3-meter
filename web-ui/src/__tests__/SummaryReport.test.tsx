/**
 * Tests for SummaryReport — verifies table rendering and statistics computation.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { SummaryReport, aggregateByLabel } from '../components/Dashboard/SummaryReport.js';
import { useRunStore } from '../store/runStore.js';
import type { MetricsBucket } from '../types/sse-events.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeBucket(
  label: string,
  overrides: Partial<MetricsBucket> = {},
): MetricsBucket {
  return {
    timestamp: new Date().toISOString(),
    samplerLabel: label,
    sampleCount: 10,
    errorCount: 0,
    avgResponseTime: 100,
    percentile90: 150,
    percentile95: 200,
    percentile99: 300,
    samplesPerSecond: 5,
    ...overrides,
  };
}

beforeEach(() => {
  useRunStore.getState().reset();
});

afterEach(() => {
  useRunStore.getState().reset();
});

// ---------------------------------------------------------------------------
// aggregateByLabel unit tests
// ---------------------------------------------------------------------------

describe('aggregateByLabel — computation', () => {
  it('returns empty array for empty input', () => {
    expect(aggregateByLabel([])).toHaveLength(0);
  });

  it('groups buckets by samplerLabel', () => {
    const samples = [
      makeBucket('login'),
      makeBucket('login'),
      makeBucket('search'),
    ];
    const rows = aggregateByLabel(samples);
    expect(rows).toHaveLength(2);
    const labels = rows.map((r) => r.label);
    expect(labels).toContain('login');
    expect(labels).toContain('search');
  });

  it('sums sampleCount across buckets for the same label', () => {
    const samples = [
      makeBucket('api', { sampleCount: 10 }),
      makeBucket('api', { sampleCount: 20 }),
    ];
    const [row] = aggregateByLabel(samples);
    expect(row!.samples).toBe(30);
  });

  it('computes weighted average response time', () => {
    const samples = [
      makeBucket('api', { sampleCount: 10, avgResponseTime: 100 }),
      makeBucket('api', { sampleCount: 10, avgResponseTime: 200 }),
    ];
    const [row] = aggregateByLabel(samples);
    // (10*100 + 10*200) / 20 = 150
    expect(row!.average).toBe(150);
  });

  it('reports min as smallest avgResponseTime across buckets', () => {
    const samples = [
      makeBucket('api', { avgResponseTime: 80 }),
      makeBucket('api', { avgResponseTime: 120 }),
    ];
    const [row] = aggregateByLabel(samples);
    expect(row!.min).toBe(80);
  });

  it('reports max as largest avgResponseTime across buckets', () => {
    const samples = [
      makeBucket('api', { avgResponseTime: 80 }),
      makeBucket('api', { avgResponseTime: 120 }),
    ];
    const [row] = aggregateByLabel(samples);
    expect(row!.max).toBe(120);
  });

  it('computes error percentage correctly', () => {
    const samples = [
      makeBucket('api', { sampleCount: 10, errorCount: 2 }),
      makeBucket('api', { sampleCount: 10, errorCount: 0 }),
    ];
    const [row] = aggregateByLabel(samples);
    // 2 errors out of 20 total = 10%
    expect(row!.errorPct).toBeCloseTo(10, 1);
  });

  it('reports 0% error when no errors', () => {
    const [row] = aggregateByLabel([makeBucket('api', { errorCount: 0 })]);
    expect(row!.errorPct).toBe(0);
  });

  it('averages percentile values across buckets', () => {
    const samples = [
      makeBucket('api', { percentile90: 100, percentile95: 150, percentile99: 200 }),
      makeBucket('api', { percentile90: 200, percentile95: 250, percentile99: 400 }),
    ];
    const [row] = aggregateByLabel(samples);
    expect(row!.p90).toBe(150);
    expect(row!.p95).toBe(200);
    expect(row!.p99).toBe(300);
  });

  it('pins "all" label to the end of the sorted list', () => {
    const samples = [
      makeBucket('all'),
      makeBucket('api'),
      makeBucket('login'),
    ];
    const rows = aggregateByLabel(samples);
    expect(rows[rows.length - 1]!.label).toBe('all');
  });

  it('sorts remaining labels alphabetically', () => {
    const samples = [makeBucket('zebra'), makeBucket('alpha'), makeBucket('mango')];
    const rows = aggregateByLabel(samples);
    expect(rows.map((r) => r.label)).toEqual(['alpha', 'mango', 'zebra']);
  });

  it('computes throughput as weighted average of samplesPerSecond', () => {
    const samples = [
      makeBucket('api', { sampleCount: 10, samplesPerSecond: 4 }),
      makeBucket('api', { sampleCount: 10, samplesPerSecond: 6 }),
    ];
    const [row] = aggregateByLabel(samples);
    expect(row!.throughput).toBeCloseTo(5, 1);
  });
});

// ---------------------------------------------------------------------------
// SummaryReport rendering tests
// ---------------------------------------------------------------------------

describe('SummaryReport — empty state', () => {
  it('shows empty placeholder when no samples', () => {
    render(<SummaryReport samples={[]} />);
    expect(screen.getByTestId('summary-report-empty')).toBeInTheDocument();
  });

  it('does not render a table when no samples', () => {
    render(<SummaryReport samples={[]} />);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});

describe('SummaryReport — table structure', () => {
  const samples = [
    makeBucket('login', { sampleCount: 50, avgResponseTime: 120 }),
    makeBucket('search', { sampleCount: 30, avgResponseTime: 200 }),
  ];

  it('renders the report table', () => {
    render(<SummaryReport samples={samples} />);
    expect(screen.getByTestId('summary-report')).toBeInTheDocument();
    expect(screen.getByRole('table')).toBeInTheDocument();
  });

  it('renders a header row with the expected column headers', () => {
    render(<SummaryReport samples={samples} />);
    expect(screen.getByText('Label')).toBeInTheDocument();
    expect(screen.getByText('# Samples')).toBeInTheDocument();
    expect(screen.getByText('Average (ms)')).toBeInTheDocument();
    expect(screen.getByText('Throughput/sec')).toBeInTheDocument();
    expect(screen.getByText('P90 (ms)')).toBeInTheDocument();
    expect(screen.getByText('P95 (ms)')).toBeInTheDocument();
    expect(screen.getByText('P99 (ms)')).toBeInTheDocument();
    expect(screen.getByText('Error %')).toBeInTheDocument();
  });

  it('renders one data row per unique label', () => {
    render(<SummaryReport samples={samples} />);
    expect(screen.getByTestId('summary-row-login')).toBeInTheDocument();
    expect(screen.getByTestId('summary-row-search')).toBeInTheDocument();
  });

  it('shows correct sample count in the login row', () => {
    render(<SummaryReport samples={samples} />);
    const row = screen.getByTestId('summary-row-login');
    expect(within(row).getByText('50')).toBeInTheDocument();
  });

  it('shows correct sample count in the search row', () => {
    render(<SummaryReport samples={samples} />);
    const row = screen.getByTestId('summary-row-search');
    expect(within(row).getByText('30')).toBeInTheDocument();
  });

  it('shows avg response time for login', () => {
    const uniqueSamples = [
      makeBucket('login', { sampleCount: 50, avgResponseTime: 120 }),
    ];
    render(<SummaryReport samples={uniqueSamples} />);
    const row = screen.getByTestId('summary-row-login');
    // avgResponseTime=120 appears as avg, min, and max — verify at least one
    expect(within(row).getAllByText('120').length).toBeGreaterThanOrEqual(1);
  });

  it('shows 0.0% error for rows with no errors', () => {
    render(<SummaryReport samples={samples} />);
    const row = screen.getByTestId('summary-row-login');
    expect(within(row).getByText('0.0%')).toBeInTheDocument();
  });
});

describe('SummaryReport — error highlighting', () => {
  it('applies error row class when error count > 0', () => {
    const errSamples = [makeBucket('failing', { errorCount: 3, sampleCount: 10 })];
    render(<SummaryReport samples={errSamples} />);
    const row = screen.getByTestId('summary-row-failing');
    expect(row.classList.contains('report-table__row--error')).toBe(true);
  });

  it('shows non-zero error percentage', () => {
    const errSamples = [makeBucket('failing', { errorCount: 5, sampleCount: 10 })];
    render(<SummaryReport samples={errSamples} />);
    const row = screen.getByTestId('summary-row-failing');
    expect(within(row).getByText('50.0%')).toBeInTheDocument();
  });
});

describe('SummaryReport — store integration', () => {
  it('reads samples from runStore when no prop is provided', () => {
    useRunStore.getState().addSample(makeBucket('store-label'));
    render(<SummaryReport />);
    expect(screen.getByTestId('summary-row-store-label')).toBeInTheDocument();
  });
});
