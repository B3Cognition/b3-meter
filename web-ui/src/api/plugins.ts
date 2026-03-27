/**
 * Plugin-specific API calls.
 *
 * Each function maps to a single REST endpoint on the backend.
 * All HTTP mechanics are delegated to the typed client in client.ts.
 */

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
