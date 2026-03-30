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
import { useState, useEffect, useCallback } from 'react';
import {
  startRecording,
  stopRecording,
  getRecorderStatus,
  getCapturedRequests,
  applyToTestPlan,
  DEFAULT_CONFIG,
} from '../../api/proxyRecorder.js';
import type {
  ProxyRecorderConfig,
  CapturedRequest,
  AppliedNode,
} from '../../api/proxyRecorder.js';
import './ProxyRecorder.css';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

export interface ProxyRecorderPanelProps {
  /**
   * The plan identifier to record for. When absent the controls are disabled.
   */
  planId?: string;
  /**
   * Called after the user clicks "Apply to Test Plan" with the converted nodes.
   */
  onApply?: (nodes: AppliedNode[]) => void;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function methodClass(method: string): string {
  const known = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'];
  return known.includes(method)
    ? `proxy-recorder__method proxy-recorder__method--${method}`
    : 'proxy-recorder__method proxy-recorder__method--other';
}

function statusCodeClass(code: number): string {
  if (code === 0) return 'proxy-recorder__status-code proxy-recorder__status-code--0';
  if (code < 300) return 'proxy-recorder__status-code proxy-recorder__status-code--2xx';
  if (code < 400) return 'proxy-recorder__status-code proxy-recorder__status-code--3xx';
  if (code < 500) return 'proxy-recorder__status-code proxy-recorder__status-code--4xx';
  return 'proxy-recorder__status-code proxy-recorder__status-code--5xx';
}

function patternListToText(patterns: string[]): string {
  return patterns.join('\n');
}

function textToPatternList(text: string): string[] {
  return text
    .split('\n')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function ProxyRecorderPanel({ planId, onApply }: ProxyRecorderPanelProps) {
  // Config form state
  const [port, setPort]                   = useState(String(DEFAULT_CONFIG.port));
  const [targetUrl, setTargetUrl]         = useState('');
  const [includeText, setIncludeText]     = useState('');
  const [excludeText, setExcludeText]     = useState(
    patternListToText(DEFAULT_CONFIG.excludePatterns),
  );
  const [captureHeaders, setCaptureHeaders] = useState(DEFAULT_CONFIG.captureHeaders);
  const [captureBody, setCaptureBody]       = useState(DEFAULT_CONFIG.captureBody);

  // Recording lifecycle state
  const [recording, setRecording]           = useState(false);
  const [captured, setCaptured]             = useState<CapturedRequest[]>([]);
  const [loading, setLoading]               = useState(false);
  const [error, setError]                   = useState<string | null>(null);
  const [applying, setApplying]             = useState(false);

  // -------------------------------------------------------------------------
  // Load initial status when planId is provided
  // -------------------------------------------------------------------------

  const refreshStatus = useCallback(async () => {
    if (!planId) return;
    setLoading(true);
    setError(null);
    try {
      const status = await getRecorderStatus(planId);
      setRecording(status.recording);
      if (status.recording) {
        const cfg = status.config;
        setPort(String(cfg.port));
        setTargetUrl(cfg.targetBaseUrl ?? '');
        setIncludeText(patternListToText(cfg.includePatterns));
        setExcludeText(patternListToText(cfg.excludePatterns));
        setCaptureHeaders(cfg.captureHeaders);
        setCaptureBody(cfg.captureBody);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load recorder status');
    } finally {
      setLoading(false);
    }
  }, [planId]);

  const refreshCaptured = useCallback(async () => {
    if (!planId) return;
    setLoading(true);
    setError(null);
    try {
      const list = await getCapturedRequests(planId);
      setCaptured(list);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load captured requests');
    } finally {
      setLoading(false);
    }
  }, [planId]);

  useEffect(() => {
    void refreshStatus();
  }, [refreshStatus]);

  // -------------------------------------------------------------------------
  // Start recording
  // -------------------------------------------------------------------------

  const handleStart = useCallback(async () => {
    if (!planId) return;
    const portNum = parseInt(port, 10);
    if (isNaN(portNum) || portNum < 1 || portNum > 65535) {
      setError('Port must be between 1 and 65535');
      return;
    }
    const config: Partial<ProxyRecorderConfig> = {
      port: portNum,
      targetBaseUrl: targetUrl.trim() || null,
      includePatterns: textToPatternList(includeText),
      excludePatterns: textToPatternList(excludeText),
      captureHeaders,
      captureBody,
    };
    setLoading(true);
    setError(null);
    try {
      await startRecording(planId, config);
      setRecording(true);
      setCaptured([]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to start recording');
    } finally {
      setLoading(false);
    }
  }, [planId, port, targetUrl, includeText, excludeText, captureHeaders, captureBody]);

  // -------------------------------------------------------------------------
  // Stop recording
  // -------------------------------------------------------------------------

  const handleStop = useCallback(async () => {
    if (!planId) return;
    setLoading(true);
    setError(null);
    try {
      await stopRecording(planId);
      setRecording(false);
      await refreshCaptured();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to stop recording');
      setLoading(false);
    }
  }, [planId, refreshCaptured]);

  // -------------------------------------------------------------------------
  // Apply to test plan
  // -------------------------------------------------------------------------

  const handleApply = useCallback(async () => {
    if (!planId) return;
    setApplying(true);
    setError(null);
    try {
      const nodes = await applyToTestPlan(planId);
      onApply?.(nodes);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to apply captured requests');
    } finally {
      setApplying(false);
    }
  }, [planId, onApply]);

  // -------------------------------------------------------------------------
  // Computed helpers
  // -------------------------------------------------------------------------

  const disabled = !planId || loading;
  const canStart = !disabled && !recording;
  const canStop  = !disabled && recording;
  const canApply = !!planId && !applying && captured.length > 0;

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------

  return (
    <div className="proxy-recorder" data-testid="proxy-recorder-panel">

      {/* Header */}
      <div className="proxy-recorder__header">
        <h3 className="proxy-recorder__title">HTTP Proxy Recorder</h3>
        <span
          className={`proxy-recorder__status ${
            recording
              ? 'proxy-recorder__status--recording'
              : 'proxy-recorder__status--idle'
          }`}
          data-testid="recorder-status"
        >
          {recording ? 'Recording' : 'Idle'}
        </span>
      </div>

      {/* Configuration form */}
      <div className="proxy-recorder__config" data-testid="recorder-config">
        <div className="proxy-recorder__config-row">
          <div className="proxy-recorder__field">
            <label htmlFor="pr-port">Proxy Port</label>
            <input
              id="pr-port"
              type="number"
              min="1"
              max="65535"
              value={port}
              onChange={(e) => setPort(e.target.value)}
              disabled={recording}
              aria-label="Proxy port"
            />
          </div>
          <div className="proxy-recorder__field">
            <label htmlFor="pr-target">Target Base URL (optional)</label>
            <input
              id="pr-target"
              type="text"
              placeholder="https://api.example.com"
              value={targetUrl}
              onChange={(e) => setTargetUrl(e.target.value)}
              disabled={recording}
              aria-label="Target base URL"
            />
          </div>
        </div>

        <div className="proxy-recorder__config-row">
          <div className="proxy-recorder__field">
            <label htmlFor="pr-include">Include Patterns (one per line)</label>
            <textarea
              id="pr-include"
              placeholder="https://api\.example\.com/.*"
              value={includeText}
              onChange={(e) => setIncludeText(e.target.value)}
              disabled={recording}
              aria-label="Include patterns"
            />
            <span className="proxy-recorder__field-hint">
              Regex. Leave empty to include everything not excluded.
            </span>
          </div>
          <div className="proxy-recorder__field">
            <label htmlFor="pr-exclude">Exclude Patterns (one per line)</label>
            <textarea
              id="pr-exclude"
              placeholder=".*\.(gif|png|css|js|ico)$"
              value={excludeText}
              onChange={(e) => setExcludeText(e.target.value)}
              disabled={recording}
              aria-label="Exclude patterns"
            />
            <span className="proxy-recorder__field-hint">
              Regex. Default excludes common static assets.
            </span>
          </div>
        </div>

        <div className="proxy-recorder__config-row">
          <div className="proxy-recorder__field proxy-recorder__field--checkbox">
            <input
              id="pr-capture-headers"
              type="checkbox"
              checked={captureHeaders}
              onChange={(e) => setCaptureHeaders(e.target.checked)}
              disabled={recording}
              aria-label="Capture headers"
            />
            <label htmlFor="pr-capture-headers">Capture Headers</label>
          </div>
          <div className="proxy-recorder__field proxy-recorder__field--checkbox">
            <input
              id="pr-capture-body"
              type="checkbox"
              checked={captureBody}
              onChange={(e) => setCaptureBody(e.target.checked)}
              disabled={recording}
              aria-label="Capture body"
            />
            <label htmlFor="pr-capture-body">Capture Body</label>
          </div>
        </div>
      </div>

      {/* Start / Stop / Refresh controls */}
      <div className="proxy-recorder__controls">
        <button
          className="proxy-recorder__start-btn"
          onClick={() => { void handleStart(); }}
          disabled={!canStart}
          aria-label="Start recording"
          data-testid="start-recording-btn"
        >
          Start Recording
        </button>
        <button
          className="proxy-recorder__stop-btn"
          onClick={() => { void handleStop(); }}
          disabled={!canStop}
          aria-label="Stop recording"
          data-testid="stop-recording-btn"
        >
          Stop Recording
        </button>
        <button
          className="proxy-recorder__refresh-btn"
          onClick={() => { void refreshCaptured(); }}
          disabled={disabled}
          aria-label="Refresh captured list"
          data-testid="refresh-captured-btn"
        >
          Refresh
        </button>
        {loading && (
          <span className="proxy-recorder__loading" data-testid="loading-indicator">
            Loading…
          </span>
        )}
      </div>

      {/* Captured requests list */}
      <div className="proxy-recorder__captured" data-testid="captured-section">
        <div className="proxy-recorder__captured-header">
          <span className="proxy-recorder__captured-title">Captured Requests</span>
          <span className="proxy-recorder__captured-count" data-testid="captured-count">
            {captured.length} request{captured.length !== 1 ? 's' : ''}
          </span>
        </div>
        <div className="proxy-recorder__captured-list" data-testid="captured-list">
          {captured.length === 0 ? (
            <span className="proxy-recorder__captured-empty">
              {recording
                ? 'Waiting for requests…'
                : 'No requests captured. Start recording and browse your application.'}
            </span>
          ) : (
            captured.map((req) => (
              <div
                key={req.id}
                className="proxy-recorder__captured-item"
                data-testid={`captured-item-${req.id}`}
              >
                <span className={methodClass(req.method)}>{req.method}</span>
                <span className="proxy-recorder__url" title={req.url}>
                  {req.url}
                </span>
                <span
                  className={statusCodeClass(req.responseCode)}
                  data-testid="captured-status-code"
                >
                  {req.responseCode > 0 ? req.responseCode : '-'}
                </span>
                <span className="proxy-recorder__elapsed">
                  {req.elapsedMs > 0 ? `${req.elapsedMs}ms` : ''}
                </span>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Error display */}
      {error !== null && (
        <span className="proxy-recorder__error" role="alert" data-testid="recorder-error">
          {error}
        </span>
      )}

      {/* Footer — Apply to Test Plan */}
      <div className="proxy-recorder__footer">
        <span className="proxy-recorder__footer-info">
          {captured.length === 0
            ? 'Capture requests to enable Apply'
            : `${captured.length} request${captured.length !== 1 ? 's' : ''} ready to apply`}
        </span>
        <button
          className="proxy-recorder__apply-btn"
          onClick={() => { void handleApply(); }}
          disabled={!canApply}
          aria-label="Apply to Test Plan"
          data-testid="apply-btn"
        >
          {applying ? 'Applying…' : 'Apply to Test Plan'}
        </button>
      </div>
    </div>
  );
}
