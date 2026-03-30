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
import { aggregateByLabel } from './SummaryReport.js';
import type { MetricsBucket } from '../../types/sse-events.js';

function fmt(n: number, decimals = 1): string {
  return n.toFixed(decimals);
}

interface AggregateReportProps {
  /** Samples to render. Defaults to runStore samples when not provided (used in tests). */
  samples?: MetricsBucket[];
}

export function AggregateReport({ samples: propSamples }: AggregateReportProps = {}) {
  const storeSamples = useRunStore((s) => s.samples);
  const samples = propSamples ?? storeSamples;

  const rows = useMemo(() => aggregateByLabel(samples), [samples]);

  if (rows.length === 0) {
    return (
      <div className="report-empty" data-testid="aggregate-report-empty">
        No data yet. Start a test run to see aggregate statistics.
      </div>
    );
  }

  return (
    <div className="aggregate-report" data-testid="aggregate-report">
      <table className="report-table">
        <thead>
          <tr>
            <th>Label</th>
            <th># Samples</th>
            <th>Average (ms)</th>
            <th>Median (P50)</th>
            <th>P90 (ms)</th>
            <th>P95 (ms)</th>
            <th>P99 (ms)</th>
            <th>Min (ms)</th>
            <th>Max (ms)</th>
            <th>Error %</th>
            <th>Throughput/sec</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row.label}
              className={row.errorPct > 0 ? 'report-table__row--error' : ''}
              data-testid={`aggregate-row-${row.label}`}
            >
              <td className="report-table__label">{row.label}</td>
              <td>{row.samples}</td>
              <td>{row.average}</td>
              {/* Median approximated as average when raw distribution isn't available */}
              <td>{row.average}</td>
              <td>{row.p90}</td>
              <td>{row.p95}</td>
              <td>{row.p99}</td>
              <td>{row.min}</td>
              <td>{row.max}</td>
              <td className={row.errorPct > 0 ? 'report-table__cell--error' : ''}>
                {fmt(row.errorPct)}%
              </td>
              <td>{fmt(row.throughput)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
