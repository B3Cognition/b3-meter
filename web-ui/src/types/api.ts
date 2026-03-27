/**
 * API response types matching the REST contracts exposed by the backend.
 */

import type { TestPlanTree } from './test-plan.js';


/** Generic envelope returned by all endpoints. */
export interface ApiResponse<T> {
  data: T;
  error?: string;
}

/** POST /api/runs — start a new test run */
export interface StartRunRequest {
  planId: string;
  durationSeconds?: number;
  workerAddresses?: string[];
}

export interface StartRunResponse {
  id: string;
  runId?: string; // kept for backwards compat
  startedAt: string;
}

/** GET /api/runs/:id */
export interface RunSummary {
  runId: string;
  planId: string;
  status: 'idle' | 'starting' | 'running' | 'stopping' | 'stopped' | 'error';
  startedAt: string | null;
  stoppedAt: string | null;
}

/** POST /api/runs/:id/stop */
export interface StopRunResponse {
  runId: string;
  stoppedAt: string;
}

/** GET /api/runs/:id/metrics — flat object from backend MetricsDto */
export interface MetricsResponse {
  runId: string;
  timestamp: string;
  samplerLabel: string;
  sampleCount: number;
  errorCount: number;
  avgResponseTime: number;
  minResponseTime: number;
  maxResponseTime: number;
  percentile90: number;
  percentile95: number;
  percentile99: number;
  samplesPerSecond: number;
  errorPercent: number;
}

/** GET /api/plans/:id */
export interface PlanResponse {
  id: string;
  planId?: string; // alias for backwards compat
  name: string;
  ownerId?: string;
  treeData?: string;
  tree?: TestPlanTree;
  createdAt?: string;
  updatedAt?: string;
}

/** POST /api/plans — create a new plan */
export interface CreatePlanRequest {
  name: string;
}

/** PUT /api/plans/:id */
export interface SavePlanRequest {
  tree: TestPlanTree;
}

export interface SavePlanResponse {
  planId: string;
  updatedAt: string;
}

/** Worker node status values */
export type WorkerStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE';

/** GET /api/workers — single worker in the list */
export interface WorkerSummary {
  id: string;
  hostname: string;
  port: number;
  status: WorkerStatus;
  lastHeartbeat: string | null;
  registeredAt: string;
}

/** POST /api/workers — register a new worker */
export interface RegisterWorkerRequest {
  hostname: string;
  port: number;
}

/** Plugin status values returned by the backend. */
export type PluginStatus = 'PENDING' | 'ACTIVE' | 'QUARANTINED';

/** GET /api/plugins — single plugin in the list */
export interface PluginSummary {
  id: string;
  name: string;
  version: string;
  status: PluginStatus;
  installedBy: string | null;
  installedAt: string;
}
