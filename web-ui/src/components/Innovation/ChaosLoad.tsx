/**
 * ChaosLoad — Chaos + Load Fusion panel.
 *
 * Configures chaos engineering parameters that are injected into load tests:
 *   - Latency injection (random delay for X% of requests)
 *   - Error injection (5xx responses for X% of requests)
 *   - Connection drops (kill X% of connections mid-request)
 *   - Network partition (pause all traffic for N seconds at time T)
 *
 * Configuration is stored and included in the run request. Results display
 * the active chaos config alongside performance metrics.
 */

import { useState, useCallback } from 'react';
import { Zap, Save, RotateCcw } from 'lucide-react';
import { useLogStore } from '../../store/logStore.js';
import './Innovation.css';

export interface ChaosConfig {
  latencyInjection: {
    enabled: boolean;
    minDelayMs: number;
    maxDelayMs: number;
    percentAffected: number;
  };
  errorInjection: {
    enabled: boolean;
    statusCode: number;
    percentAffected: number;
  };
  connectionDrops: {
    enabled: boolean;
    percentAffected: number;
  };
  networkPartition: {
    enabled: boolean;
    pauseDurationSec: number;
    triggerAtSec: number;
  };
}

const DEFAULT_CONFIG: ChaosConfig = {
  latencyInjection: { enabled: false, minDelayMs: 100, maxDelayMs: 2000, percentAffected: 10 },
  errorInjection: { enabled: false, statusCode: 503, percentAffected: 5 },
  connectionDrops: { enabled: false, percentAffected: 3 },
  networkPartition: { enabled: false, pauseDurationSec: 5, triggerAtSec: 30 },
};

// Store the chaos config globally so the run API can pick it up
let _savedChaosConfig: ChaosConfig | null = null;
export function getSavedChaosConfig(): ChaosConfig | null { return _savedChaosConfig; }

export function ChaosLoad() {
  const [config, setConfig] = useState<ChaosConfig>(() => {
    // Try to load from localStorage
    try {
      const saved = localStorage.getItem('jmeter-next-chaos-config');
      if (saved) return JSON.parse(saved);
    } catch { /* ignore */ }
    return { ...DEFAULT_CONFIG };
  });
  const [saved, setSaved] = useState(false);
  const addLog = useLogStore((s) => s.addLog);

  const updateLatency = useCallback((patch: Partial<ChaosConfig['latencyInjection']>) => {
    setConfig((prev) => ({
      ...prev,
      latencyInjection: { ...prev.latencyInjection, ...patch },
    }));
    setSaved(false);
  }, []);

  const updateError = useCallback((patch: Partial<ChaosConfig['errorInjection']>) => {
    setConfig((prev) => ({
      ...prev,
      errorInjection: { ...prev.errorInjection, ...patch },
    }));
    setSaved(false);
  }, []);

  const updateDrops = useCallback((patch: Partial<ChaosConfig['connectionDrops']>) => {
    setConfig((prev) => ({
      ...prev,
      connectionDrops: { ...prev.connectionDrops, ...patch },
    }));
    setSaved(false);
  }, []);

  const updatePartition = useCallback((patch: Partial<ChaosConfig['networkPartition']>) => {
    setConfig((prev) => ({
      ...prev,
      networkPartition: { ...prev.networkPartition, ...patch },
    }));
    setSaved(false);
  }, []);

  const handleSave = useCallback(() => {
    _savedChaosConfig = config;
    try {
      localStorage.setItem('jmeter-next-chaos-config', JSON.stringify(config));
    } catch { /* ignore */ }
    setSaved(true);

    const active: string[] = [];
    if (config.latencyInjection.enabled) active.push(`Latency(${config.latencyInjection.percentAffected}%)`);
    if (config.errorInjection.enabled) active.push(`Errors(${config.errorInjection.percentAffected}%)`);
    if (config.connectionDrops.enabled) active.push(`Drops(${config.connectionDrops.percentAffected}%)`);
    if (config.networkPartition.enabled) active.push(`Partition(${config.networkPartition.pauseDurationSec}s@${config.networkPartition.triggerAtSec}s)`);

    addLog('INFO', `Chaos config saved: ${active.length > 0 ? active.join(', ') : 'all disabled'}`);
  }, [config, addLog]);

  const handleReset = useCallback(() => {
    setConfig({ ...DEFAULT_CONFIG });
    _savedChaosConfig = null;
    try { localStorage.removeItem('jmeter-next-chaos-config'); } catch { /* ignore */ }
    setSaved(false);
    addLog('INFO', 'Chaos config reset to defaults');
  }, [addLog]);

  const activeCount = [
    config.latencyInjection.enabled,
    config.errorInjection.enabled,
    config.connectionDrops.enabled,
    config.networkPartition.enabled,
  ].filter(Boolean).length;

  return (
    <div className="innovation-panel">
      <div className="innovation-header">
        <Zap size={18} />
        <h3>Chaos + Load Fusion</h3>
        {activeCount > 0 && (
          <span className="innovation-badge innovation-badge-warn">{activeCount} active</span>
        )}
      </div>
      <p className="innovation-desc">
        Inject chaos during load tests to validate resilience. Configuration is included in run requests.
      </p>

      {/* Latency Injection */}
      <fieldset className="chaos-fieldset">
        <legend>
          <label className="chaos-toggle">
            <input
              type="checkbox"
              checked={config.latencyInjection.enabled}
              onChange={(e) => updateLatency({ enabled: e.target.checked })}
            />
            Inject Latency
          </label>
        </legend>
        {config.latencyInjection.enabled && (
          <div className="chaos-fields">
            <div className="innovation-field">
              <label>Min Delay (ms)</label>
              <input
                type="number"
                value={config.latencyInjection.minDelayMs}
                onChange={(e) => updateLatency({ minDelayMs: Number(e.target.value) })}
                min={0}
              />
            </div>
            <div className="innovation-field">
              <label>Max Delay (ms)</label>
              <input
                type="number"
                value={config.latencyInjection.maxDelayMs}
                onChange={(e) => updateLatency({ maxDelayMs: Number(e.target.value) })}
                min={0}
              />
            </div>
            <div className="innovation-field">
              <label>% of Requests Affected</label>
              <input
                type="number"
                value={config.latencyInjection.percentAffected}
                onChange={(e) => updateLatency({ percentAffected: Number(e.target.value) })}
                min={0}
                max={100}
              />
            </div>
          </div>
        )}
      </fieldset>

      {/* Error Injection */}
      <fieldset className="chaos-fieldset">
        <legend>
          <label className="chaos-toggle">
            <input
              type="checkbox"
              checked={config.errorInjection.enabled}
              onChange={(e) => updateError({ enabled: e.target.checked })}
            />
            Inject Errors
          </label>
        </legend>
        {config.errorInjection.enabled && (
          <div className="chaos-fields">
            <div className="innovation-field">
              <label>HTTP Status Code</label>
              <select
                value={config.errorInjection.statusCode}
                onChange={(e) => updateError({ statusCode: Number(e.target.value) })}
              >
                <option value={500}>500 Internal Server Error</option>
                <option value={502}>502 Bad Gateway</option>
                <option value={503}>503 Service Unavailable</option>
                <option value={504}>504 Gateway Timeout</option>
              </select>
            </div>
            <div className="innovation-field">
              <label>% of Requests Affected</label>
              <input
                type="number"
                value={config.errorInjection.percentAffected}
                onChange={(e) => updateError({ percentAffected: Number(e.target.value) })}
                min={0}
                max={100}
              />
            </div>
          </div>
        )}
      </fieldset>

      {/* Connection Drops */}
      <fieldset className="chaos-fieldset">
        <legend>
          <label className="chaos-toggle">
            <input
              type="checkbox"
              checked={config.connectionDrops.enabled}
              onChange={(e) => updateDrops({ enabled: e.target.checked })}
            />
            Connection Drops
          </label>
        </legend>
        {config.connectionDrops.enabled && (
          <div className="chaos-fields">
            <div className="innovation-field">
              <label>% of Connections Dropped</label>
              <input
                type="number"
                value={config.connectionDrops.percentAffected}
                onChange={(e) => updateDrops({ percentAffected: Number(e.target.value) })}
                min={0}
                max={100}
              />
            </div>
          </div>
        )}
      </fieldset>

      {/* Network Partition */}
      <fieldset className="chaos-fieldset">
        <legend>
          <label className="chaos-toggle">
            <input
              type="checkbox"
              checked={config.networkPartition.enabled}
              onChange={(e) => updatePartition({ enabled: e.target.checked })}
            />
            Network Partition
          </label>
        </legend>
        {config.networkPartition.enabled && (
          <div className="chaos-fields">
            <div className="innovation-field">
              <label>Pause Duration (seconds)</label>
              <input
                type="number"
                value={config.networkPartition.pauseDurationSec}
                onChange={(e) => updatePartition({ pauseDurationSec: Number(e.target.value) })}
                min={1}
              />
            </div>
            <div className="innovation-field">
              <label>Trigger at (seconds into test)</label>
              <input
                type="number"
                value={config.networkPartition.triggerAtSec}
                onChange={(e) => updatePartition({ triggerAtSec: Number(e.target.value) })}
                min={0}
              />
            </div>
          </div>
        )}
      </fieldset>

      {/* Summary */}
      {activeCount > 0 && (
        <div className="chaos-summary">
          <strong>Active chaos rules:</strong>
          <ul>
            {config.latencyInjection.enabled && (
              <li>Latency: {config.latencyInjection.minDelayMs}-{config.latencyInjection.maxDelayMs}ms on {config.latencyInjection.percentAffected}% of requests</li>
            )}
            {config.errorInjection.enabled && (
              <li>Errors: HTTP {config.errorInjection.statusCode} on {config.errorInjection.percentAffected}% of requests</li>
            )}
            {config.connectionDrops.enabled && (
              <li>Connection drops: {config.connectionDrops.percentAffected}% of connections</li>
            )}
            {config.networkPartition.enabled && (
              <li>Network partition: {config.networkPartition.pauseDurationSec}s pause at T+{config.networkPartition.triggerAtSec}s</li>
            )}
          </ul>
        </div>
      )}

      <div className="innovation-actions">
        <button className="innovation-btn innovation-btn-primary" onClick={handleSave}>
          <Save size={14} /> {saved ? 'Saved' : 'Save Config'}
        </button>
        <button className="innovation-btn" onClick={handleReset}>
          <RotateCcw size={14} /> Reset
        </button>
      </div>
    </div>
  );
}
