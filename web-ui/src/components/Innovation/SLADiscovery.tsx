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
import { useState, useCallback, useRef } from 'react';
import { Activity, Play, Square } from 'lucide-react';
import { startRun, stopRun } from '../../api/runs.js';
import { createPlan } from '../../api/plans.js';
import { useLogStore } from '../../store/logStore.js';
import './Innovation.css';

/** Build a minimal JMX that hits a single URL with N virtual users for 10 seconds. */
function buildProbeJmx(targetUrl: string, virtualUsers: number): string {
  let url: URL;
  try {
    url = new URL(targetUrl);
  } catch {
    url = new URL('http://localhost' + (targetUrl.startsWith('/') ? '' : '/') + targetUrl);
  }
  const domain = url.hostname;
  const protocol = url.protocol.replace(':', '');
  const port = url.port || (protocol === 'https' ? '443' : '80');
  const path = url.pathname;
  const query = url.search ? url.search.substring(1) : '';
  const queryEscaped = query.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

  return `<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="3.2" jmeter="5.6">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="SLA Probe" enabled="true"/>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Probe" enabled="true">
        <intProp name="ThreadGroup.num_threads">${virtualUsers}</intProp>
        <intProp name="ThreadGroup.ramp_time">2</intProp>
        <longProp name="ThreadGroup.duration">10</longProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <intProp name="LoopController.loops">-1</intProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="SLA Probe" enabled="true">
          <stringProp name="HTTPSampler.domain">${domain}</stringProp>
          <stringProp name="HTTPSampler.protocol">${protocol}</stringProp>
          <stringProp name="HTTPSampler.port">${port}</stringProp>
          <stringProp name="HTTPSampler.path">${path}</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
          <stringProp name="HTTPSampler.arguments">${queryEscaped}</stringProp>
        </HTTPSamplerProxy>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>`;
}

interface ProbeResult {
  virtualUsers: number;
  p95: number;
  throughput: number;
  errorRate: number;
  breached: boolean;
}

export function SLADiscovery() {
  const [targetUrl, setTargetUrl] = useState('http://localhost:8080/api/health');
  const [initialVUs, setInitialVUs] = useState(10);
  const [maxVUs, setMaxVUs] = useState(500);
  const [slaThreshold, setSlaThreshold] = useState(500);
  const [running, setRunning] = useState(false);
  const [results, setResults] = useState<ProbeResult[]>([]);
  const [breakingPoint, setBreakingPoint] = useState<ProbeResult | null>(null);
  const [statusMsg, setStatusMsg] = useState('');
  const abortRef = useRef(false);
  const addLog = useLogStore((s) => s.addLog);

  const runProbe = useCallback(
    async (vus: number): Promise<ProbeResult> => {
      setStatusMsg(`Probing with ${vus} VUs...`);
      addLog('INFO', `SLA Discovery: probing ${targetUrl} with ${vus} VUs`);

      try {
        // 1. Create a plan with a dynamically-generated JMX for the target URL
        const planName = `sla-probe-${vus}vu-${Date.now()}`;
        const plan = await createPlan({ name: planName });
        const planId = plan.id;

        // 2. Upload JMX content with the actual target URL and VU count
        const jmx = buildProbeJmx(targetUrl, vus);
        // Upload JMX via raw fetch — updatePlan expects typed tree, but we need raw treeData string
        await fetch(`/api/v1/plans/${planId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ treeData: jmx }),
        });

        // 3. Start the run (10 second probe)
        const response = await startRun({
          planId,
          virtualUsers: vus,
          durationSeconds: 10,
        } as any);

        const runId = response.id || response.runId!;

        // 4. Poll for metrics until run completes or timeout (20s)
        let p95 = 0;
        let throughput = 0;
        let errorRate = 0;
        const deadline = Date.now() + 20000;

        while (Date.now() < deadline) {
          await new Promise((r) => setTimeout(r, 1500));
          try {
            const res = await fetch(`/api/v1/runs/${runId}/metrics`);
            if (res.ok) {
              const m = await res.json();
              if (m.sampleCount > 0) {
                p95 = m.percentile95 ?? 0;
                throughput = m.samplesPerSecond ?? 0;
                errorRate = m.errorPercent ?? 0;
              }
            }
          } catch {
            /* continue polling */
          }
          // Check if run finished
          try {
            const statusRes = await fetch(`/api/v1/runs/${runId}`);
            if (statusRes.ok) {
              const data = await statusRes.json();
              const s = (data.status || '').toUpperCase();
              if (s === 'STOPPED' || s === 'ERROR' || s === 'COMPLETED') {
                // Final metrics read
                try {
                  const finalRes = await fetch(`/api/v1/runs/${runId}/metrics`);
                  if (finalRes.ok) {
                    const fm = await finalRes.json();
                    if (fm.sampleCount > 0) {
                      p95 = fm.percentile95 ?? p95;
                      throughput = fm.samplesPerSecond ?? throughput;
                      errorRate = fm.errorPercent ?? errorRate;
                    }
                  }
                } catch { /* use last known */ }
                break;
              }
            }
          } catch {
            /* ignore */
          }
        }

        // Stop the run if it's still going
        try { await stopRun(runId); } catch { /* may already be stopped */ }

        return {
          virtualUsers: vus,
          p95,
          throughput: Math.max(0, throughput),
          errorRate,
          breached: p95 > slaThreshold,
        };
      } catch (err) {
        addLog('ERROR', `SLA probe failed for ${vus} VUs: ${err}`);
        return {
          virtualUsers: vus,
          p95: 0,
          throughput: 0,
          errorRate: 100,
          breached: false,
        };
      }
    },
    [targetUrl, slaThreshold, addLog],
  );

  const handleStart = useCallback(async () => {
    setRunning(true);
    setResults([]);
    setBreakingPoint(null);
    abortRef.current = false;
    addLog('INFO', `SLA Discovery started: target=${targetUrl}, threshold=p95<${slaThreshold}ms, maxVUs=${maxVUs}`);

    const probeResults: ProbeResult[] = [];

    // Phase 1: Exponential ramp-up
    let n = initialVUs;
    let lastGood = 0;

    while (n <= maxVUs && !abortRef.current) {
      const result = await runProbe(n);
      probeResults.push(result);
      setResults([...probeResults]);

      if (!result.breached) {
        lastGood = n;
        n = Math.min(n * 2, maxVUs + 1);
      } else {
        // Found first breach — enter binary search
        break;
      }
    }

    // Phase 2: Binary search between lastGood and n
    if (!abortRef.current && probeResults.length > 0 && probeResults[probeResults.length - 1]?.breached) {
      let lo = lastGood;
      let hi = Math.min(n, maxVUs);
      let iterations = 0;

      while (hi - lo > Math.max(1, initialVUs / 2) && iterations < 8 && !abortRef.current) {
        const mid = Math.round((lo + hi) / 2);
        if (mid === lo || mid === hi) break;

        const result = await runProbe(mid);
        probeResults.push(result);
        setResults([...probeResults]);

        if (result.breached) {
          hi = mid;
        } else {
          lo = mid;
        }
        iterations++;
      }
    }

    // Find the breaking point
    const sorted = [...probeResults].sort((a, b) => a.virtualUsers - b.virtualUsers);
    const bp = sorted.find((r) => r.breached) ?? null;
    setBreakingPoint(bp);

    if (bp) {
      setStatusMsg(`Breaking point: ${bp.virtualUsers} VUs (p95 = ${bp.p95}ms)`);
      addLog('WARN', `SLA Discovery: breaking point at ${bp.virtualUsers} VUs (p95=${bp.p95}ms > ${slaThreshold}ms)`);
    } else {
      setStatusMsg(`SLA maintained up to ${maxVUs} VUs`);
      addLog('INFO', `SLA Discovery: SLA maintained up to ${maxVUs} VUs`);
    }

    setRunning(false);
  }, [targetUrl, initialVUs, maxVUs, slaThreshold, runProbe, addLog]);

  const handleStop = useCallback(() => {
    abortRef.current = true;
    setRunning(false);
    setStatusMsg('Stopped');
    addLog('INFO', 'SLA Discovery: stopped by user');
  }, [addLog]);

  // Chart dimensions
  const chartW = 500;
  const chartH = 200;
  const chartPad = 40;

  const sortedResults = [...results].sort((a, b) => a.virtualUsers - b.virtualUsers);
  const maxP95 = Math.max(slaThreshold * 1.5, ...sortedResults.map((r) => r.p95));
  const maxVU = Math.max(maxVUs, ...sortedResults.map((r) => r.virtualUsers));

  const toX = (vu: number) => chartPad + ((vu / maxVU) * (chartW - chartPad * 2));
  const toY = (p95: number) => chartH - chartPad - ((p95 / maxP95) * (chartH - chartPad * 2));

  return (
    <div className="innovation-panel">
      <div className="innovation-header">
        <Activity size={18} />
        <h3>Genetic SLA Discovery</h3>
      </div>
      <p className="innovation-desc">
        Binary search for the VU count where your SLA threshold is first breached.
      </p>

      <div className="innovation-form">
        <div className="innovation-field">
          <label>Target URL</label>
          <input
            type="text"
            value={targetUrl}
            onChange={(e) => setTargetUrl(e.target.value)}
            disabled={running}
          />
        </div>
        <div className="innovation-row">
          <div className="innovation-field">
            <label>Initial VUs</label>
            <input
              type="number"
              value={initialVUs}
              onChange={(e) => setInitialVUs(Number(e.target.value))}
              disabled={running}
              min={1}
            />
          </div>
          <div className="innovation-field">
            <label>Max VUs</label>
            <input
              type="number"
              value={maxVUs}
              onChange={(e) => setMaxVUs(Number(e.target.value))}
              disabled={running}
              min={1}
            />
          </div>
          <div className="innovation-field">
            <label>SLA: p95 &lt; (ms)</label>
            <input
              type="number"
              value={slaThreshold}
              onChange={(e) => setSlaThreshold(Number(e.target.value))}
              disabled={running}
              min={1}
            />
          </div>
        </div>

        <div className="innovation-actions">
          {!running ? (
            <button className="innovation-btn innovation-btn-primary" onClick={handleStart}>
              <Play size={14} /> Discover
            </button>
          ) : (
            <button className="innovation-btn innovation-btn-danger" onClick={handleStop}>
              <Square size={14} /> Stop
            </button>
          )}
        </div>
      </div>

      {statusMsg && (
        <div className={`innovation-status ${breakingPoint ? 'innovation-status-warn' : ''}`}>
          {statusMsg}
        </div>
      )}

      {/* Chart */}
      {sortedResults.length > 0 && (
        <div className="innovation-chart-container">
          <svg width={chartW} height={chartH} className="innovation-chart">
            {/* Grid lines */}
            <line x1={chartPad} y1={chartH - chartPad} x2={chartW - chartPad} y2={chartH - chartPad} stroke="var(--border, #334155)" strokeWidth={1} />
            <line x1={chartPad} y1={chartPad} x2={chartPad} y2={chartH - chartPad} stroke="var(--border, #334155)" strokeWidth={1} />

            {/* SLA threshold line */}
            <line
              x1={chartPad}
              y1={toY(slaThreshold)}
              x2={chartW - chartPad}
              y2={toY(slaThreshold)}
              stroke="#ef4444"
              strokeWidth={1}
              strokeDasharray="4 3"
            />
            <text x={chartW - chartPad + 4} y={toY(slaThreshold) + 4} fill="#ef4444" fontSize={10}>
              SLA {slaThreshold}ms
            </text>

            {/* Data line */}
            {sortedResults.length > 1 && (
              <polyline
                fill="none"
                stroke="#3b82f6"
                strokeWidth={2}
                points={sortedResults.map((r) => `${toX(r.virtualUsers)},${toY(r.p95)}`).join(' ')}
              />
            )}

            {/* Data points */}
            {sortedResults.map((r, i) => (
              <circle
                key={i}
                cx={toX(r.virtualUsers)}
                cy={toY(r.p95)}
                r={4}
                fill={r.breached ? '#ef4444' : '#22c55e'}
                stroke="var(--bg-primary, #0f172a)"
                strokeWidth={1.5}
              >
                <title>{r.virtualUsers} VUs: p95={r.p95}ms</title>
              </circle>
            ))}

            {/* Axis labels */}
            <text x={chartW / 2} y={chartH - 4} fill="var(--text-secondary, #94a3b8)" fontSize={11} textAnchor="middle">
              Virtual Users
            </text>
            <text x={12} y={chartH / 2} fill="var(--text-secondary, #94a3b8)" fontSize={11} textAnchor="middle" transform={`rotate(-90, 12, ${chartH / 2})`}>
              p95 (ms)
            </text>
          </svg>
        </div>
      )}

      {/* Results table */}
      {sortedResults.length > 0 && (
        <table className="innovation-table">
          <thead>
            <tr>
              <th>VUs</th>
              <th>p95 (ms)</th>
              <th>Throughput (req/s)</th>
              <th>Error %</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {sortedResults.map((r, i) => (
              <tr key={i} className={r.breached ? 'innovation-row-error' : ''}>
                <td>{r.virtualUsers}</td>
                <td>{r.p95}</td>
                <td>{r.throughput.toFixed(1)}</td>
                <td>{r.errorRate.toFixed(1)}%</td>
                <td>
                  <span className={`innovation-badge ${r.breached ? 'innovation-badge-error' : 'innovation-badge-ok'}`}>
                    {r.breached ? 'BREACHED' : 'OK'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {breakingPoint && (
        <div className="innovation-result-box">
          <strong>Breaking Point:</strong> {breakingPoint.virtualUsers} VUs
          <span className="innovation-result-detail">(p95 = {breakingPoint.p95}ms)</span>
        </div>
      )}
    </div>
  );
}
