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
import { useMemo } from 'react';
import { useRunStore } from '../../store/runStore.js';
import type { MetricsBucket } from '../../types/sse-events.js';

/** Per-label aggregate computed from MetricsBucket ring buffer. */
export interface LabelStats {
  label: string;
  samples: number;
  average: number;
  min: number;
  max: number;
  /** Approximated std dev (sqrt of population variance of avgResponseTime across buckets). */
  stdDev: number;
  errorPct: number;
  throughput: number;
  p90: number;
  p95: number;
  p99: number;
}

/**
 * Aggregate a flat list of MetricsBucket entries into per-label stats.
 * This is a best-effort approximation — the ring buffer contains pre-aggregated
 * 1-second buckets, so we derive overall stats from bucket-level values.
 */
export function aggregateByLabel(samples: MetricsBucket[]): LabelStats[] {
  const groups = new Map<string, MetricsBucket[]>();

  for (const s of samples) {
    const list = groups.get(s.samplerLabel) ?? [];
    list.push(s);
    groups.set(s.samplerLabel, list);
  }

  const rows: LabelStats[] = [];

  for (const [label, buckets] of groups.entries()) {
    const totalSamples = buckets.reduce((acc, b) => acc + b.sampleCount, 0);
    const totalErrors  = buckets.reduce((acc, b) => acc + b.errorCount, 0);

    // Weighted average of avgResponseTime
    const weightedSum = buckets.reduce((acc, b) => acc + b.avgResponseTime * b.sampleCount, 0);
    const average = totalSamples > 0 ? weightedSum / totalSamples : 0;

    // Approximate min/max from bucket-level avg (best effort without raw samples)
    const min = Math.min(...buckets.map((b) => b.avgResponseTime));
    const max = Math.max(...buckets.map((b) => b.avgResponseTime));

    // Population std dev of per-bucket avgResponseTime values (sample-count weighted)
    const variance =
      totalSamples > 0
        ? buckets.reduce((acc, b) => acc + b.sampleCount * Math.pow(b.avgResponseTime - average, 2), 0) /
          totalSamples
        : 0;
    const stdDev = Math.sqrt(variance);

    const errorPct = totalSamples > 0 ? (totalErrors / totalSamples) * 100 : 0;

    // Throughput: weighted average of samplesPerSecond across buckets
    const throughput =
      buckets.reduce((acc, b) => acc + b.samplesPerSecond * b.sampleCount, 0) /
      (totalSamples || 1);

    // Percentiles: simple average of bucket-level percentile values (approximation)
    const avg = (fn: (b: MetricsBucket) => number) =>
      buckets.reduce((acc, b) => acc + fn(b), 0) / buckets.length;

    rows.push({
      label,
      samples: totalSamples,
      average: Math.round(average),
      min:     Math.round(min),
      max:     Math.round(max),
      stdDev:  Math.round(stdDev),
      errorPct,
      throughput,
      p90: Math.round(avg((b) => b.percentile90)),
      p95: Math.round(avg((b) => b.percentile95)),
      p99: Math.round(avg((b) => b.percentile99)),
    });
  }

  // Sort alphabetically by label, with "all" pinned last
  rows.sort((a, b) => {
    if (a.label === 'all') return 1;
    if (b.label === 'all') return -1;
    return a.label.localeCompare(b.label);
  });

  return rows;
}

function fmt(n: number, decimals = 1): string {
  return n.toFixed(decimals);
}

interface SummaryReportProps {
  /** Samples to render. Defaults to runStore samples when not provided (used in tests). */
  samples?: MetricsBucket[];
}

export function SummaryReport({ samples: propSamples }: SummaryReportProps = {}) {
  const storeSamples = useRunStore((s) => s.samples);
  const samples = propSamples ?? storeSamples;

  const rows = useMemo(() => aggregateByLabel(samples), [samples]);

  if (rows.length === 0) {
    return (
      <div className="report-empty" data-testid="summary-report-empty">
        No data yet. Start a test run to see per-label statistics.
      </div>
    );
  }

  return (
    <div className="summary-report" data-testid="summary-report">
      <table className="report-table">
        <thead>
          <tr>
            <th>Label</th>
            <th># Samples</th>
            <th>Average (ms)</th>
            <th>Min (ms)</th>
            <th>Max (ms)</th>
            <th>Std Dev</th>
            <th>Error %</th>
            <th>Throughput/sec</th>
            <th>P90 (ms)</th>
            <th>P95 (ms)</th>
            <th>P99 (ms)</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row.label}
              className={row.errorPct > 0 ? 'report-table__row--error' : ''}
              data-testid={`summary-row-${row.label}`}
            >
              <td className="report-table__label">{row.label}</td>
              <td>{row.samples}</td>
              <td>{row.average}</td>
              <td>{row.min}</td>
              <td>{row.max}</td>
              <td>{row.stdDev}</td>
              <td className={row.errorPct > 0 ? 'report-table__cell--error' : ''}>
                {fmt(row.errorPct)}%
              </td>
              <td>{fmt(row.throughput)}</td>
              <td>{row.p90}</td>
              <td>{row.p95}</td>
              <td>{row.p99}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
