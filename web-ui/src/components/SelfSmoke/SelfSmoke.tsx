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
import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Shield, Play, Square, RefreshCw,
  CheckCircle, XCircle, AlertCircle, SkipForward,
  Server, Flame,
} from 'lucide-react';
import {
  getMockStatus, startMocks, stopMocks, runSmoke,
  type MockServerStatus, type SmokePlanResult, type SmokeResponse,
} from '../../api/mocks.js';
import { useLogStore } from '../../store/logStore.js';
import './SelfSmoke.css';

/** Protocol metadata for display purposes. */
const PROTOCOL_ORDER = [
  'http-mock', 'ws-mock', 'sse-mock', 'hls-mock', 'mqtt-mock',
  'grpc-mock', 'dash-mock', 'stun-mock', 'webrtc-signaling',
  'ftp-mock', 'ldap-mock', 'tcp-mock', 'smtp-mock',
];

export function SelfSmoke() {
  const [serverStatus, setServerStatus] = useState<Record<string, MockServerStatus>>({});
  const [statusLoading, setStatusLoading] = useState(false);
  const [startingServers, setStartingServers] = useState(false);
  const [stoppingServers, setStoppingServers] = useState(false);
  const [smokeRunning, setSmokeRunning] = useState(false);
  const [smokeResults, setSmokeResults] = useState<SmokeResponse | null>(null);
  const [duration, setDuration] = useState(30);
  const [error, setError] = useState<string | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const addLog = useLogStore((s) => s.addLog);

  // ── Fetch status ──
  const fetchStatus = useCallback(async () => {
    setStatusLoading(true);
    try {
      const data = await getMockStatus();
      setServerStatus(data);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to fetch mock status');
    } finally {
      setStatusLoading(false);
    }
  }, []);

  // Auto-start servers on first load if any are down
  useEffect(() => {
    getMockStatus().then(data => {
      const downCount = Object.values(data).filter(s => s.status === 'down').length;
      if (downCount > 0) {
        startMocks().then(() => {
          // Refresh status after starting
          setTimeout(() => fetchStatus(), 3000);
        });
      }
    }).catch(() => {});
  }, []); // only on mount

  // Auto-refresh status every 5 seconds
  useEffect(() => {
    fetchStatus();
    pollRef.current = setInterval(fetchStatus, 5000);
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, [fetchStatus]);

  // ── Start all servers ──
  const handleStartAll = useCallback(async () => {
    setStartingServers(true);
    setError(null);
    try {
      const res = await startMocks();
      addLog('INFO', `Started ${res.started} mock servers: ${res.servers.join(', ')}`);
      if (res.errors && res.errors.length > 0) {
        addLog('WARN', `Mock start errors: ${res.errors.join('; ')}`);
      }
      // Refresh status after a short delay to let servers boot
      setTimeout(fetchStatus, 1500);
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Failed to start mock servers';
      setError(msg);
      addLog('ERROR', msg);
    } finally {
      setStartingServers(false);
    }
  }, [fetchStatus, addLog]);

  // ── Stop all servers ──
  const handleStopAll = useCallback(async () => {
    setStoppingServers(true);
    setError(null);
    try {
      const res = await stopMocks();
      addLog('INFO', `Stopped ${res.stopped} mock servers`);
      setTimeout(fetchStatus, 500);
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Failed to stop mock servers';
      setError(msg);
      addLog('ERROR', msg);
    } finally {
      setStoppingServers(false);
    }
  }, [fetchStatus, addLog]);

  // ── Run smoke tests ──
  const handleRunSmoke = useCallback(async () => {
    setSmokeRunning(true);
    setSmokeResults(null);
    setError(null);
    addLog('INFO', 'Starting self-smoke regression suite...');

    try {
      const res = await runSmoke(duration);
      setSmokeResults(res);

      const { summary } = res;
      addLog(
        summary.failed > 0 ? 'ERROR' : 'INFO',
        `Self-smoke complete: ${summary.passed}/${summary.total} passed, ` +
        `${summary.totalSamples} samples, ${summary.overallErrorPercent}% errors`
      );
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Smoke run failed';
      setError(msg);
      addLog('ERROR', msg);
    } finally {
      setSmokeRunning(false);
    }
  }, [addLog]);

  // ── Derived counts ──
  const entries = Object.entries(serverStatus);
  const sortedEntries = PROTOCOL_ORDER
    .filter((name) => serverStatus[name])
    .map((name) => [name, serverStatus[name]] as [string, MockServerStatus]);

  // Include any servers not in the predefined order
  for (const [name, status] of entries) {
    if (!PROTOCOL_ORDER.includes(name)) {
      sortedEntries.push([name, status]);
    }
  }

  const upCount = sortedEntries.filter(([, s]) => s.status === 'up').length;
  const downCount = sortedEntries.filter(([, s]) => s.status === 'down').length;

  return (
    <div className="selfsmoke-panel">
      {/* ── Header ── */}
      <div className="selfsmoke-header">
        <Shield size={18} />
        <h3>Self Smoke</h3>
        <span className="selfsmoke-subtitle">Regression Test Suite</span>
      </div>
      <p className="selfsmoke-desc">
        Start mock servers, then run smoke tests against each protocol to verify end-to-end functionality.
      </p>

      {error && <div className="selfsmoke-error">{error}</div>}

      {/* ── Mock Server Status Section ── */}
      <div className="selfsmoke-section">
        <div className="selfsmoke-section-header">
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <Server size={14} style={{ marginRight: 6, color: 'var(--accent)' }} />
            <span className="selfsmoke-section-title">Mock Servers</span>
            {upCount > 0 && (
              <span className="selfsmoke-count selfsmoke-count-up">{upCount} up</span>
            )}
            {downCount > 0 && (
              <span className="selfsmoke-count selfsmoke-count-down">{downCount} down</span>
            )}
          </div>
          <div className="selfsmoke-section-actions">
            <button
              className="selfsmoke-btn"
              onClick={fetchStatus}
              disabled={statusLoading}
              title="Refresh status"
            >
              <RefreshCw size={12} className={statusLoading ? 'selfsmoke-spin-icon' : ''} />
              Refresh
            </button>
            <button
              className="selfsmoke-btn selfsmoke-btn-start"
              onClick={handleStartAll}
              disabled={startingServers || smokeRunning}
            >
              <Play size={12} />
              {startingServers ? 'Starting...' : 'Start All'}
            </button>
            <button
              className="selfsmoke-btn selfsmoke-btn-stop"
              onClick={handleStopAll}
              disabled={stoppingServers || smokeRunning}
            >
              <Square size={12} />
              {stoppingServers ? 'Stopping...' : 'Stop All'}
            </button>
          </div>
        </div>

        <table className="selfsmoke-table">
          <thead>
            <tr>
              <th>Server</th>
              <th>Port</th>
              <th>Protocol</th>
              <th>Status</th>
              <th>Response Time</th>
            </tr>
          </thead>
          <tbody>
            {sortedEntries.length === 0 && (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-secondary)', padding: 16 }}>
                  {statusLoading ? 'Loading...' : 'No servers found. Is the backend running?'}
                </td>
              </tr>
            )}
            {sortedEntries.map(([name, s]) => (
              <tr key={name}>
                <td style={{ fontWeight: 600 }}>{name}</td>
                <td className="selfsmoke-mono">{s.port}</td>
                <td>{s.protocol}</td>
                <td>
                  <div className={`selfsmoke-status-cell ${s.status === 'up' ? 'selfsmoke-status-up' : 'selfsmoke-status-down'}`}>
                    <span className={`selfsmoke-dot ${s.status === 'up' ? 'selfsmoke-dot-up' : 'selfsmoke-dot-down'}`} />
                    {s.status.toUpperCase()}
                  </div>
                </td>
                <td className="selfsmoke-mono">
                  {s.responseTime !== undefined ? `${s.responseTime}ms` : '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="selfsmoke-divider" />

      {/* ── Smoke Test Runner Section ── */}
      <div className="selfsmoke-section">
        <div className="selfsmoke-section-header">
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <Flame size={14} style={{ marginRight: 6, color: '#8b5cf6' }} />
            <span className="selfsmoke-section-title">Smoke Tests</span>
          </div>
          <div className="selfsmoke-section-actions">
            <select
              className="selfsmoke-btn"
              value={duration}
              onChange={e => setDuration(Number(e.target.value))}
              disabled={smokeRunning}
              style={{ marginRight: 4 }}
            >
              <option value={10}>10 seconds</option>
              <option value={30}>30 seconds</option>
              <option value={60}>1 minute</option>
              <option value={180}>3 minutes</option>
              <option value={300}>5 minutes</option>
            </select>
            <button
              className="selfsmoke-btn selfsmoke-btn-smoke"
              onClick={handleRunSmoke}
              disabled={smokeRunning}
            >
              {smokeRunning ? (
                <>
                  <span className="selfsmoke-spinner" />
                  Running...
                </>
              ) : (
                <>
                  <Play size={12} />
                  Run Self Smoke
                </>
              )}
            </button>
          </div>
        </div>

        {smokeRunning && !smokeResults && (
          <div className="selfsmoke-running-plan">
            <span className="selfsmoke-spinner" />
            Executing smoke plans sequentially... This may take a minute.
          </div>
        )}

        {smokeResults && (
          <>
            <table className="selfsmoke-table">
              <thead>
                <tr>
                  <th>Plan</th>
                  <th>Samples</th>
                  <th>Avg</th>
                  <th>P95</th>
                  <th>Err %</th>
                  <th>Result</th>
                </tr>
              </thead>
              <tbody>
                {smokeResults.results.map((r) => (
                  <tr key={r.plan} className={getRowClass(r.status)}>
                    <td style={{ fontWeight: 600 }}>{r.plan}</td>
                    <td className="selfsmoke-mono">{r.samples || '-'}</td>
                    <td className="selfsmoke-mono">
                      {r.samples > 0 ? `${r.avgResponseTime}ms` : '-'}
                    </td>
                    <td className="selfsmoke-mono">
                      {r.samples > 0 ? `${r.p95}ms` : '-'}
                    </td>
                    <td className="selfsmoke-mono">
                      {r.samples > 0 ? `${r.errorPercent.toFixed(1)}%` : '-'}
                    </td>
                    <td>
                      <ResultBadge status={r.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div className="selfsmoke-summary">
              <div className="selfsmoke-summary-item">
                <span className={`selfsmoke-summary-value ${smokeResults.summary.failed === 0 ? 'selfsmoke-summary-pass' : 'selfsmoke-summary-fail'}`}>
                  {smokeResults.summary.passed}/{smokeResults.summary.total}
                </span>
                <span className="selfsmoke-summary-label">plans passed</span>
              </div>
              <div className="selfsmoke-summary-item">
                <span className="selfsmoke-summary-value">{smokeResults.summary.totalSamples}</span>
                <span className="selfsmoke-summary-label">total samples</span>
              </div>
              <div className="selfsmoke-summary-item">
                <span className="selfsmoke-summary-label">overall error rate:</span>
                <span className={`selfsmoke-summary-value ${smokeResults.summary.overallErrorPercent > 0 ? 'selfsmoke-summary-fail' : 'selfsmoke-summary-pass'}`}>
                  {smokeResults.summary.overallErrorPercent.toFixed(1)}%
                </span>
              </div>
              {smokeResults.summary.skipped > 0 && (
                <div className="selfsmoke-summary-item">
                  <span className="selfsmoke-summary-value" style={{ color: 'var(--text-secondary)' }}>
                    {smokeResults.summary.skipped}
                  </span>
                  <span className="selfsmoke-summary-label">skipped</span>
                </div>
              )}
            </div>
          </>
        )}

        {!smokeResults && !smokeRunning && (
          <div style={{
            padding: '24px 16px',
            textAlign: 'center',
            color: 'var(--text-secondary)',
            fontSize: 13,
          }}>
            Click "Run Self Smoke" to execute all smoke plans and see results here.
          </div>
        )}
      </div>
    </div>
  );
}

/** Returns the CSS class for a smoke result row. */
function getRowClass(status: SmokePlanResult['status']): string {
  switch (status) {
    case 'PASS': return 'selfsmoke-row-pass';
    case 'WARN': return 'selfsmoke-row-warn';
    case 'FAIL': return 'selfsmoke-row-fail';
    case 'SKIP': return 'selfsmoke-row-skip';
    case 'ERROR': return 'selfsmoke-row-fail';
    default: return '';
  }
}

/** Renders the pass/fail/skip/warn badge with icon. */
function ResultBadge({ status }: { status: SmokePlanResult['status'] }) {
  switch (status) {
    case 'PASS':
      return (
        <span className="selfsmoke-badge selfsmoke-badge-pass">
          <CheckCircle size={10} /> PASS
        </span>
      );
    case 'WARN':
      return (
        <span className="selfsmoke-badge selfsmoke-badge-warn">
          <AlertCircle size={10} /> WARN
        </span>
      );
    case 'FAIL':
      return (
        <span className="selfsmoke-badge selfsmoke-badge-fail">
          <XCircle size={10} /> FAIL
        </span>
      );
    case 'SKIP':
      return (
        <span className="selfsmoke-badge selfsmoke-badge-skip">
          <SkipForward size={10} /> SKIP
        </span>
      );
    case 'ERROR':
      return (
        <span className="selfsmoke-badge selfsmoke-badge-error">
          <XCircle size={10} /> ERROR
        </span>
      );
    default:
      return null;
  }
}
