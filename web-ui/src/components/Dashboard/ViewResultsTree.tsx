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
import { useState, useMemo } from 'react';
import { useRunStore } from '../../store/runStore.js';
import type { MetricsBucket } from '../../types/sse-events.js';

export type ResultFilter = 'all' | 'pass' | 'fail';

/** Per-sample result entry (synthetic — derived from MetricsBucket). */
export interface SampleResult {
  id: string;
  label: string;
  timestamp: string;
  responseTime: number;
  success: boolean;
  errorCount: number;
  throughput: number;
  /** Mock request details */
  requestUrl: string;
  requestMethod: string;
  /** Mock response details */
  responseCode: string;
  responseMessage: string;
  responseSize: number;
}

/** Project a MetricsBucket into a SampleResult for display purposes. */
function bucketToResult(bucket: MetricsBucket, index: number): SampleResult {
  const success = bucket.errorCount === 0;
  return {
    id: `${bucket.timestamp}-${index}`,
    label: bucket.samplerLabel,
    timestamp: bucket.timestamp,
    responseTime: bucket.avgResponseTime,
    success,
    errorCount: bucket.errorCount,
    throughput: bucket.samplesPerSecond,
    requestUrl: `http://target-host/api/${bucket.samplerLabel.toLowerCase().replace(/\s+/g, '-')}`,
    requestMethod: 'GET',
    responseCode: success ? '200' : '500',
    responseMessage: success ? 'OK' : 'Internal Server Error',
    responseSize: Math.round(bucket.avgResponseTime * 2.5),
  };
}

function PassIcon() {
  return (
    <span
      className="vrt-result-icon vrt-result-icon--pass"
      aria-label="pass"
      title="Pass"
    >
      ✓
    </span>
  );
}

function FailIcon() {
  return (
    <span
      className="vrt-result-icon vrt-result-icon--fail"
      aria-label="fail"
      title="Fail"
    >
      ✗
    </span>
  );
}

interface SampleDetailProps {
  result: SampleResult;
}

function SampleDetail({ result }: SampleDetailProps) {
  return (
    <div className="vrt-detail" data-testid="vrt-detail">
      <div className="vrt-detail__section">
        <h4 className="vrt-detail__heading">Sampler Result</h4>
        <table className="vrt-detail__table">
          <tbody>
            <tr><td>Label</td><td>{result.label}</td></tr>
            <tr><td>Timestamp</td><td>{new Date(result.timestamp).toLocaleTimeString()}</td></tr>
            <tr><td>Response Time</td><td>{result.responseTime} ms</td></tr>
            <tr>
              <td>Success</td>
              <td className={result.success ? 'vrt-detail__value--pass' : 'vrt-detail__value--fail'}>
                {result.success ? 'true' : 'false'}
              </td>
            </tr>
            <tr><td>Throughput</td><td>{result.throughput.toFixed(1)} samples/sec</td></tr>
          </tbody>
        </table>
      </div>

      <div className="vrt-detail__section">
        <h4 className="vrt-detail__heading">Request</h4>
        <table className="vrt-detail__table">
          <tbody>
            <tr><td>Method</td><td>{result.requestMethod}</td></tr>
            <tr><td>URL</td><td className="vrt-detail__url">{result.requestUrl}</td></tr>
          </tbody>
        </table>
      </div>

      <div className="vrt-detail__section">
        <h4 className="vrt-detail__heading">Response</h4>
        <table className="vrt-detail__table">
          <tbody>
            <tr><td>Response Code</td><td>{result.responseCode}</td></tr>
            <tr><td>Response Message</td><td>{result.responseMessage}</td></tr>
            <tr><td>Response Size</td><td>{result.responseSize} bytes</td></tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}

interface ViewResultsTreeProps {
  /** Samples to render. Defaults to runStore samples when not provided (used in tests). */
  samples?: MetricsBucket[];
}

export function ViewResultsTree({ samples: propSamples }: ViewResultsTreeProps = {}) {
  const storeSamples = useRunStore((s) => s.samples);
  const samples = propSamples ?? storeSamples;

  const [filter, setFilter] = useState<ResultFilter>('all');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const results: SampleResult[] = useMemo(
    () => samples.map((b, i) => bucketToResult(b, i)),
    [samples],
  );

  const filtered = useMemo(() => {
    if (filter === 'pass') return results.filter((r) => r.success);
    if (filter === 'fail') return results.filter((r) => !r.success);
    return results;
  }, [results, filter]);

  const selected = useMemo(
    () => filtered.find((r) => r.id === selectedId) ?? null,
    [filtered, selectedId],
  );

  return (
    <div className="vrt" data-testid="view-results-tree">
      {/* Filter toolbar */}
      <div className="vrt-toolbar" role="toolbar" aria-label="Result filter">
        <span className="vrt-toolbar__label">Show:</span>
        {(['all', 'pass', 'fail'] as ResultFilter[]).map((f) => (
          <button
            key={f}
            className={`vrt-filter-btn${filter === f ? ' vrt-filter-btn--active' : ''}`}
            onClick={() => {
              setFilter(f);
              setSelectedId(null);
            }}
            aria-pressed={filter === f}
            data-testid={`filter-${f}`}
          >
            {f.charAt(0).toUpperCase() + f.slice(1)}
          </button>
        ))}
        <span className="vrt-toolbar__count">{filtered.length} result(s)</span>
      </div>

      <div className="vrt-body">
        {/* Left panel — result list */}
        <div className="vrt-list" role="listbox" aria-label="Sample results">
          {filtered.length === 0 ? (
            <div className="vrt-list__empty" data-testid="vrt-empty">
              {results.length === 0
                ? 'No data yet. Start a test run.'
                : 'No results match the current filter.'}
            </div>
          ) : (
            filtered.map((r) => (
              <div
                key={r.id}
                role="option"
                aria-selected={r.id === selectedId}
                className={`vrt-list-item${r.id === selectedId ? ' vrt-list-item--selected' : ''}${
                  r.success ? '' : ' vrt-list-item--fail'
                }`}
                onClick={() => setSelectedId(r.id === selectedId ? null : r.id)}
                data-testid={`result-item-${r.id}`}
              >
                {r.success ? <PassIcon /> : <FailIcon />}
                <span className="vrt-list-item__label">{r.label}</span>
                <span className="vrt-list-item__time">{r.responseTime} ms</span>
              </div>
            ))
          )}
        </div>

        {/* Right panel — detail view */}
        <div className="vrt-panel">
          {selected ? (
            <SampleDetail result={selected} />
          ) : (
            <div className="vrt-panel__placeholder" data-testid="vrt-placeholder">
              Select a result to view details.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
