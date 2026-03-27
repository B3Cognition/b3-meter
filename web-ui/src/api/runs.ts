/**
 * Run-specific API calls.
 *
 * Each function maps to a single REST endpoint on the backend.
 * All HTTP mechanics are delegated to the typed client in client.ts.
 */

import { get, post } from './client.js';
import type {
  StartRunRequest,
  StartRunResponse,
  RunSummary,
  StopRunResponse,
  MetricsResponse,
} from '../types/api.js';

/** Start a new test run for the given plan. */
export async function startRun(req: StartRunRequest): Promise<StartRunResponse> {
  return post<StartRunResponse>('/runs', req);
}

/** Fetch current status and metadata for a run. */
export async function getRun(id: string): Promise<RunSummary> {
  return get<RunSummary>(`/runs/${id}`);
}

/** Request a graceful stop of an active run. */
export async function stopRun(id: string): Promise<StopRunResponse> {
  return post<StopRunResponse>(`/runs/${id}/stop`);
}

/** Request an immediate (non-graceful) stop of an active run. */
export async function stopRunNow(id: string): Promise<StopRunResponse> {
  return post<StopRunResponse>(`/runs/${id}/stop-now`);
}

/** Fetch aggregated metrics for a completed or active run. */
export async function getMetrics(id: string): Promise<MetricsResponse> {
  return get<MetricsResponse>(`/runs/${id}/metrics`);
}
