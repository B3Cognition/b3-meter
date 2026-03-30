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
import { listWorkers, registerWorker, removeWorker } from '../../api/workers.js';
import './DistributedConfig.css';
import type { WorkerSummary, WorkerStatus } from '../../types/api.js';

// ---------------------------------------------------------------------------
// Status helpers
// ---------------------------------------------------------------------------

const STATUS_LABEL: Record<WorkerStatus, string> = {
  AVAILABLE: 'Online',
  BUSY:      'Busy',
  OFFLINE:   'Offline',
};

const STATUS_CLASS: Record<WorkerStatus, string> = {
  AVAILABLE: 'distributed-config__badge--available',
  BUSY:      'distributed-config__badge--busy',
  OFFLINE:   'distributed-config__badge--offline',
};

function statusClass(status: string): string {
  return STATUS_CLASS[status as WorkerStatus] ?? 'distributed-config__badge--offline';
}

function statusLabel(status: string): string {
  return STATUS_LABEL[status as WorkerStatus] ?? status;
}

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

export interface DistributedConfigProps {
  /**
   * Plan identifier for the distributed run. When provided and at least one
   * worker is selected the "Start Distributed Run" button becomes active.
   */
  planId?: string;
  /**
   * Callback fired when the user confirms a distributed run.
   * Receives the selected worker addresses formatted as "hostname:port".
   */
  onStartDistributedRun?: (workerAddresses: string[]) => void;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function DistributedConfig({ planId, onStartDistributedRun }: DistributedConfigProps) {
  const [workers, setWorkers]           = useState<WorkerSummary[]>([]);
  const [selectedIds, setSelectedIds]   = useState<Set<string>>(new Set());
  const [loading, setLoading]           = useState(false);
  const [error, setError]               = useState<string | null>(null);
  const [showForm, setShowForm]         = useState(false);
  const [formHostname, setFormHostname] = useState('');
  const [formPort, setFormPort]         = useState('1099');
  const [formError, setFormError]       = useState<string | null>(null);
  const [saving, setSaving]             = useState(false);

  // -------------------------------------------------------------------------
  // Load workers on mount
  // -------------------------------------------------------------------------

  const loadWorkers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listWorkers();
      setWorkers(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load workers');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadWorkers();
  }, [loadWorkers]);

  // -------------------------------------------------------------------------
  // Selection
  // -------------------------------------------------------------------------

  const toggleWorker = useCallback((id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  // -------------------------------------------------------------------------
  // Register new worker
  // -------------------------------------------------------------------------

  const handleRegister = useCallback(async () => {
    const portNum = parseInt(formPort, 10);
    if (!formHostname.trim()) {
      setFormError('Hostname is required');
      return;
    }
    if (isNaN(portNum) || portNum < 1 || portNum > 65535) {
      setFormError('Port must be between 1 and 65535');
      return;
    }

    setSaving(true);
    setFormError(null);
    try {
      const created = await registerWorker({ hostname: formHostname.trim(), port: portNum });
      setWorkers((prev) => [...prev, created]);
      setFormHostname('');
      setFormPort('1099');
      setShowForm(false);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to register worker');
    } finally {
      setSaving(false);
    }
  }, [formHostname, formPort]);

  const handleCancelForm = useCallback(() => {
    setShowForm(false);
    setFormHostname('');
    setFormPort('1099');
    setFormError(null);
  }, []);

  // -------------------------------------------------------------------------
  // Remove worker
  // -------------------------------------------------------------------------

  const handleRemove = useCallback(async (id: string) => {
    setError(null);
    try {
      await removeWorker(id);
      setWorkers((prev) => prev.filter((w) => w.id !== id));
      setSelectedIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to remove worker');
    }
  }, []);

  // -------------------------------------------------------------------------
  // Start distributed run
  // -------------------------------------------------------------------------

  const handleStartDistributedRun = useCallback(() => {
    if (!onStartDistributedRun) return;
    const addresses = workers
      .filter((w) => selectedIds.has(w.id))
      .map((w) => `${w.hostname}:${w.port}`);
    onStartDistributedRun(addresses);
  }, [workers, selectedIds, onStartDistributedRun]);

  const canStart = planId !== undefined && planId !== '' && selectedIds.size > 0;

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------

  return (
    <div className="distributed-config">
      {/* Header */}
      <div className="distributed-config__header">
        <h3 className="distributed-config__title">Distributed Workers</h3>
        <button
          className="distributed-config__add-btn"
          onClick={() => setShowForm((v) => !v)}
          aria-label="Add worker"
        >
          + Add Worker
        </button>
      </div>

      {/* Inline add-worker form */}
      {showForm && (
        <div className="distributed-config__form">
          <div className="distributed-config__field">
            <label htmlFor="dc-hostname">Hostname / IP</label>
            <input
              id="dc-hostname"
              type="text"
              placeholder="worker-1.internal"
              value={formHostname}
              onChange={(e) => setFormHostname(e.target.value)}
              aria-label="Worker hostname"
            />
          </div>
          <div className="distributed-config__field">
            <label htmlFor="dc-port">Port</label>
            <input
              id="dc-port"
              type="number"
              min="1"
              max="65535"
              value={formPort}
              onChange={(e) => setFormPort(e.target.value)}
              aria-label="Worker port"
              style={{ minWidth: 80 }}
            />
          </div>
          <div className="distributed-config__form-actions">
            <button
              className="distributed-config__save-btn"
              onClick={() => { void handleRegister(); }}
              disabled={saving}
              aria-label="Save worker"
            >
              {saving ? 'Saving…' : 'Save'}
            </button>
            <button
              className="distributed-config__cancel-btn"
              onClick={handleCancelForm}
              aria-label="Cancel"
            >
              Cancel
            </button>
          </div>
          {formError !== null && (
            <span className="distributed-config__error" role="alert">
              {formError}
            </span>
          )}
        </div>
      )}

      {/* Worker list */}
      <div className="distributed-config__list" data-testid="worker-list">
        {loading && (
          <span className="distributed-config__loading">Loading workers…</span>
        )}
        {!loading && workers.length === 0 && (
          <span className="distributed-config__empty">
            No workers registered. Add a worker to enable distributed runs.
          </span>
        )}
        {workers.map((worker) => (
          <div
            key={worker.id}
            className={[
              'distributed-config__worker',
              selectedIds.has(worker.id) ? 'distributed-config__worker--selected' : '',
            ].join(' ').trim()}
            data-testid={`worker-row-${worker.id}`}
          >
            <input
              type="checkbox"
              className="distributed-config__worker-checkbox"
              checked={selectedIds.has(worker.id)}
              onChange={() => toggleWorker(worker.id)}
              aria-label={`Select worker ${worker.hostname}:${worker.port}`}
            />
            <div className="distributed-config__worker-info">
              <span className="distributed-config__worker-address">{worker.hostname}</span>
              <span className="distributed-config__worker-port">:{worker.port}</span>
            </div>
            <span
              className={`distributed-config__badge ${statusClass(worker.status)}`}
              data-testid={`worker-status-${worker.id}`}
            >
              {statusLabel(worker.status)}
            </span>
            <button
              className="distributed-config__remove-btn"
              onClick={() => { void handleRemove(worker.id); }}
              aria-label={`Remove worker ${worker.hostname}:${worker.port}`}
            >
              Remove
            </button>
          </div>
        ))}
      </div>

      {/* API-level error */}
      {error !== null && (
        <span className="distributed-config__error" role="alert">
          {error}
        </span>
      )}

      {/* Footer — Start Distributed Run */}
      <div className="distributed-config__footer">
        <span className="distributed-config__selection-info">
          {selectedIds.size === 0
            ? 'Select workers to enable distributed run'
            : `${selectedIds.size} worker${selectedIds.size === 1 ? '' : 's'} selected`}
        </span>
        <button
          className="distributed-config__start-btn"
          onClick={handleStartDistributedRun}
          disabled={!canStart}
          aria-label="Start Distributed Run"
          data-testid="start-distributed-run-btn"
        >
          Start Distributed Run
        </button>
      </div>
    </div>
  );
}
