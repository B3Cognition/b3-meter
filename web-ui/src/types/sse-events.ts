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
