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
import { useRunStore } from '../store/runStore.js';
import { startRun, stopRun } from '../api/runs.js';
import type { StartRunRequest } from '../types/api.js';
import type { MetricsBucket, RunStatusEvent, SseEvent } from '../types/sse-events.js';

/** Base URL for SSE endpoint — does NOT include /api/v1 prefix (streamed directly). */
const SSE_BASE = '/api/v1/runs';

export function useTestRun() {
  const { setRunId, setStatus, addSample, reset } = useRunStore();

  /**
   * Start a new test run.
   * Updates the store with the new runId and transitions status to 'starting'.
   */
  const start = async (req: StartRunRequest): Promise<string> => {
    setStatus('starting');
    try {
      const response = await startRun(req);
      setRunId(response.id ?? response.runId ?? "");
      return response.id ?? response.runId ?? "";
    } catch (err) {
      setStatus('error');
      throw err;
    }
  };

  /**
   * Request a graceful stop of the active run.
   * Transitions status to 'stopping' immediately; backend confirms via SSE.
   */
  const stop = async (runId: string): Promise<void> => {
    setStatus('stopping');
    try {
      await stopRun(runId);
    } catch (err) {
      setStatus('error');
      throw err;
    }
  };

  /**
   * Open an SSE connection to the given run's event stream.
   * Events are parsed and dispatched to runStore.
   *
   * Returns a cleanup function that closes the EventSource when called.
   */
  const connectSse = (runId: string): (() => void) => {
    const source = new EventSource(`${SSE_BASE}/${runId}/events`);

    source.addEventListener('message', (event: MessageEvent) => {
      let parsed: SseEvent;
      try {
        parsed = JSON.parse(event.data as string) as SseEvent;
      } catch {
        return;
      }

      handleSseEvent(parsed);
    });

    source.addEventListener('error', () => {
      setStatus('error');
      source.close();
    });

    return () => {
      source.close();
    };
  };

  /** Dispatch a parsed SSE event into the appropriate store action. */
  function handleSseEvent(event: SseEvent): void {
    if (event.type === 'metrics') {
      addSample(event.data as MetricsBucket);
    } else if (event.type === 'run_status') {
      const statusEvent = event.data as RunStatusEvent;
      setStatus(statusEvent.status);
      if (statusEvent.status === 'stopped' || statusEvent.status === 'error') {
        // Run is terminal — no further cleanup needed here
      }
    } else if (event.type === 'ping') {
      // Keepalive — no action needed
    }
    // worker_status and error events are informational; store handles status
  }

  /** Reset run store to idle state (e.g. when unmounting or starting fresh). */
  const resetRun = (): void => {
    reset();
  };

  return { start, stop, connectSse, resetRun };
}
