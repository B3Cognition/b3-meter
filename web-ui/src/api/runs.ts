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
