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

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface ProxyRecorderConfig {
  port: number;
  targetBaseUrl: string | null;
  includePatterns: string[];
  excludePatterns: string[];
  captureHeaders: boolean;
  captureBody: boolean;
}

export interface CapturedRequest {
  id: string;
  timestamp: string;
  method: string;
  url: string;
  headers: Record<string, string>;
  bodyBase64: string | null;
  responseCode: number;
  elapsedMs: number;
}

export interface RecorderStatus {
  recording: boolean;
  config: ProxyRecorderConfig;
}

/** A TestPlanNode-shaped map returned by /apply. */
export interface AppliedNode {
  id: string;
  type: string;
  name: string;
  enabled: boolean;
  properties: Record<string, unknown>;
  children: AppliedNode[];
}

/** Payload for submitting a manually captured request. */
export interface CapturePayload {
  method: string;
  url: string;
  headers?: Record<string, string>;
  bodyBase64?: string;
  responseCode: number;
  elapsedMs: number;
}

// ---------------------------------------------------------------------------
// Default config used by the UI form
// ---------------------------------------------------------------------------

export const DEFAULT_CONFIG: ProxyRecorderConfig = {
  port: 8888,
  targetBaseUrl: null,
  includePatterns: [],
  excludePatterns: ['.*\\.(gif|jpg|jpeg|png|css|js|ico|woff|woff2|ttf|svg|webp)(\\?.*)?$'],
  captureHeaders: true,
  captureBody: true,
};

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** Start recording for the given plan. Returns the effective config. */
export async function startRecording(
  planId: string,
  config?: Partial<ProxyRecorderConfig>,
): Promise<ProxyRecorderConfig> {
  return post<ProxyRecorderConfig>(
    `/proxy-recorder/start?planId=${encodeURIComponent(planId)}`,
    config ?? null,
  );
}

/** Stop recording for the given plan. */
export async function stopRecording(planId: string): Promise<void> {
  return post<void>(
    `/proxy-recorder/stop?planId=${encodeURIComponent(planId)}`,
  );
}

/** Check whether recording is active and retrieve the current config. */
export async function getRecorderStatus(planId: string): Promise<RecorderStatus> {
  return get<RecorderStatus>(
    `/proxy-recorder/status?planId=${encodeURIComponent(planId)}`,
  );
}

/** Fetch all captured requests for the given plan. */
export async function getCapturedRequests(planId: string): Promise<CapturedRequest[]> {
  return get<CapturedRequest[]>(
    `/proxy-recorder/captured?planId=${encodeURIComponent(planId)}`,
  );
}

/** Submit a single captured request from an external agent. */
export async function submitCapturedRequest(
  planId: string,
  payload: CapturePayload,
): Promise<void> {
  return post<void>(
    `/proxy-recorder/capture?planId=${encodeURIComponent(planId)}`,
    payload,
  );
}

/** Convert captured requests to HTTPSampler test-plan nodes. */
export async function applyToTestPlan(planId: string): Promise<AppliedNode[]> {
  return post<AppliedNode[]>(
    `/proxy-recorder/apply?planId=${encodeURIComponent(planId)}`,
  );
}
