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
import type { PluginSummary, PluginStatus } from '../../types/api.js';
import './PluginManager.css';

// ---------------------------------------------------------------------------
// Status helpers
// ---------------------------------------------------------------------------

const STATUS_LABEL: Record<PluginStatus, string> = {
  PENDING:     'Pending',
  ACTIVE:      'Active',
  QUARANTINED: 'Quarantined',
};

const STATUS_CLASS: Record<PluginStatus, string> = {
  PENDING:     'plugin-status--pending',
  ACTIVE:      'plugin-status--active',
  QUARANTINED: 'plugin-status--quarantined',
};

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

export interface PluginListProps {
  plugins: PluginSummary[];
  onDelete?: (id: string) => void;
  onActivate?: (id: string) => void;
  /** When true, Delete and Activate buttons are rendered. */
  isAdmin?: boolean;
  isLoading?: boolean;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function PluginList({
  plugins,
  onDelete,
  onActivate,
  isAdmin = false,
  isLoading = false,
}: PluginListProps) {
  if (isLoading) {
    return (
      <div className="plugin-list plugin-list--loading" data-testid="plugin-list-loading">
        Loading plugins…
      </div>
    );
  }

  if (plugins.length === 0) {
    return (
      <div className="plugin-list plugin-list--empty" data-testid="plugin-list-empty">
        No plugins installed.
      </div>
    );
  }

  return (
    <div className="plugin-list" data-testid="plugin-list">
      <table className="plugin-list__table" aria-label="Installed plugins">
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col">Version</th>
            <th scope="col">Status</th>
            <th scope="col">Installed At</th>
            {isAdmin && <th scope="col">Actions</th>}
          </tr>
        </thead>
        <tbody>
          {plugins.map((plugin) => (
            <tr key={plugin.id} data-testid={`plugin-row-${plugin.id}`}>
              <td className="plugin-list__name">{plugin.name}</td>
              <td className="plugin-list__version">{plugin.version}</td>
              <td>
                <span
                  className={`plugin-status ${STATUS_CLASS[plugin.status]}`}
                  data-testid={`plugin-status-${plugin.id}`}
                >
                  {STATUS_LABEL[plugin.status]}
                </span>
              </td>
              <td className="plugin-list__date">
                {new Date(plugin.installedAt).toLocaleString()}
              </td>
              {isAdmin && (
                <td className="plugin-list__actions">
                  {(plugin.status === 'PENDING' || plugin.status === 'QUARANTINED') && (
                    <button
                      className="plugin-list__btn plugin-list__btn--activate"
                      onClick={() => onActivate?.(plugin.id)}
                      aria-label={`Activate ${plugin.name}`}
                    >
                      Activate
                    </button>
                  )}
                  <button
                    className="plugin-list__btn plugin-list__btn--delete"
                    onClick={() => onDelete?.(plugin.id)}
                    aria-label={`Remove ${plugin.name}`}
                  >
                    Remove
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
