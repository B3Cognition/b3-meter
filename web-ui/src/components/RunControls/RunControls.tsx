/**
 * RunControls — Run/Stop/Stop Now buttons with status state machine.
 *
 * - Run button is enabled only when a plan is loaded, the status is idle/stopped/error,
 *   and there are no validation errors.
 * - Clicking Run calls startRun API and transitions status to "starting".
 * - While running (starting/running/stopping), Stop and Stop Now buttons are shown.
 * - Stop triggers a graceful stop; Stop Now triggers an immediate stop.
 * - A status badge with colour coding reflects the current RunStatus.
 */

import { useState, useCallback, useEffect, useRef } from 'react';
import { useRunStore } from '../../store/runStore.js';
import { useTestPlanStore } from '../../store/testPlanStore.js';
import { startRun, stopRun, stopRunNow } from '../../api/runs.js';
import './RunControls.css';
import type { RunStatus } from '../../store/runStore.js';

// ---------------------------------------------------------------------------
// Status helpers
// ---------------------------------------------------------------------------

const STATUS_LABEL: Record<RunStatus, string> = {
  idle:     'Idle',
  starting: 'Starting…',
  running:  'Running',
  stopping: 'Stopping…',
  stopped:  'Stopped',
  error:    'Error',
};

const STATUS_CLASS: Record<RunStatus, string> = {
  idle:     'run-controls__badge--idle',
  starting: 'run-controls__badge--starting',
  running:  'run-controls__badge--running',
  stopping: 'run-controls__badge--stopping',
  stopped:  'run-controls__badge--stopped',
  error:    'run-controls__badge--error',
};

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

export interface RunControlsProps {
  /** When true the Run button is additionally disabled (validation errors exist). */
  hasValidationErrors?: boolean;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function RunControls({ hasValidationErrors = false }: RunControlsProps) {
  const tree      = useTestPlanStore((s) => s.tree);
  const runId     = useRunStore((s) => s.runId);
  const status    = useRunStore((s) => s.status);
  const setRunId  = useRunStore((s) => s.setRunId);
  const setStatus = useRunStore((s) => s.setStatus);

  const [error, setError] = useState<string | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Poll for run completion — handles fast runs that finish before SSE connects
  useEffect(() => {
    if (status === 'running' && runId) {
      pollRef.current = setInterval(async () => {
        try {
          const res = await fetch(`/api/v1/runs/${runId}`);
          if (res.ok) {
            const data = await res.json();
            const s = (data.status || '').toUpperCase();
            if (s === 'STOPPED' || s === 'ERROR' || s === 'COMPLETED') {
              setStatus(s === 'ERROR' ? 'error' : 'stopped');
              if (pollRef.current) clearInterval(pollRef.current);
            }
          }
        } catch { /* ignore poll errors */ }
      }, 2000);
    }
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [status, runId, setStatus]);

  const isPlanLoaded = tree !== null;
  const isActive     = status === 'starting' || status === 'running' || status === 'stopping';
  const canRun       = isPlanLoaded && !isActive && !hasValidationErrors;

  // -------------------------------------------------------------------------
  // Handlers
  // -------------------------------------------------------------------------

  const handleRun = useCallback(async () => {
    if (!canRun || tree === null) return;
    setError(null);
    setStatus('starting');
    try {
      // Use the root node id as the plan id; the store carries the plan id there.
      const response = await startRun({ planId: tree.root.id });
      setRunId(response.id ?? response.runId ?? null);
      setStatus('running');
    } catch (err) {
      setStatus('error');
      setError(err instanceof Error ? err.message : 'Failed to start run');
    }
  }, [canRun, tree, setRunId, setStatus]);

  const handleStop = useCallback(async () => {
    if (!runId) return;
    setError(null);
    setStatus('stopping');
    try {
      await stopRun(runId);
      setStatus('stopped');
    } catch (err) {
      setStatus('error');
      setError(err instanceof Error ? err.message : 'Failed to stop run');
    }
  }, [runId, setStatus]);

  const handleStopNow = useCallback(async () => {
    if (!runId) return;
    setError(null);
    setStatus('stopping');
    try {
      await stopRunNow(runId);
      setStatus('stopped');
    } catch (err) {
      setStatus('error');
      setError(err instanceof Error ? err.message : 'Failed to stop run immediately');
    }
  }, [runId, setStatus]);

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------

  return (
    <div className="run-controls">
      <div className="run-controls__actions">
        {/* Run button — shown when not active */}
        {!isActive && (
          <button
            className="run-controls__btn run-controls__btn--run"
            onClick={() => { void handleRun(); }}
            disabled={!canRun}
            aria-label="Run"
          >
            Run
          </button>
        )}

        {/* Stop + Stop Now — shown when active */}
        {isActive && (
          <>
            <button
              className="run-controls__btn run-controls__btn--stop"
              onClick={() => { void handleStop(); }}
              disabled={status === 'stopping'}
              aria-label="Stop"
            >
              Stop
            </button>
            <button
              className="run-controls__btn run-controls__btn--stop-now"
              onClick={() => { void handleStopNow(); }}
              aria-label="Stop Now"
            >
              Stop Now
            </button>
          </>
        )}
      </div>

      {/* Status badge */}
      <span
        className={`run-controls__badge ${STATUS_CLASS[status]}`}
        data-testid="run-status-badge"
      >
        {STATUS_LABEL[status]}
      </span>

      {/* Inline error */}
      {error !== null && (
        <span className="run-controls__error" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}
