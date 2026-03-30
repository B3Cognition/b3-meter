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
import { get, post, del } from './client.js';
import type { WorkerSummary, RegisterWorkerRequest } from '../types/api.js';

/** Register a new worker node. */
export async function registerWorker(req: RegisterWorkerRequest): Promise<WorkerSummary> {
  return post<WorkerSummary>('/workers', req);
}

/** Fetch all registered workers with current status. */
export async function listWorkers(): Promise<WorkerSummary[]> {
  return get<WorkerSummary[]>('/workers');
}

/** Remove a registered worker by id. */
export async function removeWorker(id: string): Promise<void> {
  return del(`/workers/${id}`);
}
