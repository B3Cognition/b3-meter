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

/** Status of a single mock server. */
export interface MockServerStatus {
  port: number;
  protocol: string;
  status: 'up' | 'down';
  responseTime?: number;
  httpCode?: number;
  error?: string;
}

/** Response from POST /mocks/start. */
export interface StartMocksResponse {
  started: number;
  servers: string[];
  errors?: string[];
}

/** Response from POST /mocks/stop. */
export interface StopMocksResponse {
  stopped: number;
}

/** Result for a single smoke plan execution. */
export interface SmokePlanResult {
  plan: string;
  status: 'PASS' | 'WARN' | 'FAIL' | 'SKIP' | 'ERROR';
  samples: number;
  avgResponseTime: number;
  errorPercent: number;
  p95: number;
  reason?: string;
  error?: string;
}

/** Response from POST /mocks/smoke. */
export interface SmokeResponse {
  results: SmokePlanResult[];
  summary: {
    total: number;
    passed: number;
    skipped: number;
    failed: number;
    totalSamples: number;
    overallErrorPercent: number;
  };
}

/** Fetch health status of all mock servers. */
export async function getMockStatus(): Promise<Record<string, MockServerStatus>> {
  return get<Record<string, MockServerStatus>>('/mocks/status');
}

/** Start all mock servers. */
export async function startMocks(): Promise<StartMocksResponse> {
  return post<StartMocksResponse>('/mocks/start');
}

/** Stop all mock servers. */
export async function stopMocks(): Promise<StopMocksResponse> {
  return post<StopMocksResponse>('/mocks/stop');
}

/** Run all smoke test plans sequentially. */
export async function runSmoke(durationSeconds?: number): Promise<SmokeResponse> {
  return post<SmokeResponse>('/mocks/smoke', durationSeconds ? { durationSeconds } : undefined);
}
