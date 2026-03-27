/**
 * Plan-specific API calls.
 *
 * Each function maps to a single REST endpoint on the backend.
 * All HTTP mechanics are delegated to the typed client in client.ts.
 */

import { get, post, put, del, uploadFile, downloadBlob } from './client.js';
import type {
  PlanResponse,
  CreatePlanRequest,
  SavePlanRequest,
} from '../types/api.js';

/** List all saved test plans. */
export async function listPlans(): Promise<PlanResponse[]> {
  return get<PlanResponse[]>('/plans');
}

/** Fetch a single test plan by id. */
export async function getPlan(id: string): Promise<PlanResponse> {
  return get<PlanResponse>(`/plans/${id}`);
}

/** Create a new test plan with the given name. */
export async function createPlan(req: CreatePlanRequest): Promise<PlanResponse> {
  return post<PlanResponse>('/plans', req);
}

/** Overwrite the tree of an existing test plan. */
export async function updatePlan(id: string, req: SavePlanRequest): Promise<PlanResponse> {
  return put<PlanResponse>(`/plans/${id}`, req);
}

/** Permanently delete a test plan. */
export async function deletePlan(id: string): Promise<void> {
  return del(`/plans/${id}`);
}

/** Import a .jmx file as a new test plan. */
export async function importJmx(file: File): Promise<PlanResponse> {
  return uploadFile<PlanResponse>('/plans/import', file);
}

/** Export a test plan as a .jmx blob. */
export async function exportJmx(id: string): Promise<Blob> {
  return downloadBlob(`/plans/${id}/export`);
}
