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
import { get, uploadFile, del, post } from './client.js';
import type { PluginSummary } from '../types/api.js';

/** List all installed plugins. */
export async function listPlugins(): Promise<PluginSummary[]> {
  return get<PluginSummary[]>('/plugins');
}

/**
 * Upload a plugin JAR.
 *
 * The file must be a valid JAR and must not exceed 50 MB.
 * The returned plugin will be in PENDING status.
 */
export async function uploadPlugin(file: File): Promise<PluginSummary> {
  return uploadFile<PluginSummary>('/plugins', file);
}

/**
 * Permanently remove a plugin.
 *
 * Deletes both the database record and the stored JAR file.
 * Admin-only: throws ApiError(403) when auth is active for non-admin callers.
 */
export async function deletePlugin(id: string): Promise<void> {
  return del(`/plugins/${id}`);
}

/**
 * Promote a PENDING or QUARANTINED plugin to ACTIVE.
 *
 * Admin-only: throws ApiError(403) when auth is active for non-admin callers.
 */
export async function activatePlugin(id: string): Promise<PluginSummary> {
  return post<PluginSummary>(`/plugins/${id}/activate`);
}
