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
