/**
 * Discriminated union of all SSE event types emitted by the backend.
 */

export interface MetricsBucket {
  timestamp: string;
  samplerLabel: string;
  sampleCount: number;
  errorCount: number;
  avgResponseTime: number;
  percentile90: number;
  percentile95: number;
  percentile99: number;
  samplesPerSecond: number;
}

export interface RunStatusEvent {
  runId: string;
  status: 'idle' | 'starting' | 'running' | 'stopping' | 'stopped' | 'error';
  message?: string;
}

export interface WorkerStatusEvent {
  workerId: string;
  address: string;
  connected: boolean;
  activeSamplers: number;
}

export interface ErrorEvent {
  code: string;
  message: string;
  detail?: string;
}

export type SseEvent =
  | { type: 'metrics'; data: MetricsBucket }
  | { type: 'run_status'; data: RunStatusEvent }
  | { type: 'worker_status'; data: WorkerStatusEvent }
  | { type: 'error'; data: ErrorEvent }
  | { type: 'ping' };
