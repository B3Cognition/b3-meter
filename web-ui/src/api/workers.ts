/**
 * Worker-node API calls.
 *
 * Each function maps to a single REST endpoint on the backend.
 * All HTTP mechanics are delegated to the typed client in client.ts.
 */

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
