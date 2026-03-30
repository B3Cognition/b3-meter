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
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { PluginList } from '../components/PluginManager/PluginList.js';
import type { PluginSummary } from '../types/api.js';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makePlugin(overrides: Partial<PluginSummary> = {}): PluginSummary {
  return {
    id:          'plugin-1',
    name:        'My Plugin',
    version:     '1.0.0',
    status:      'PENDING',
    installedBy: 'system',
    installedAt: '2026-03-25T10:00:00Z',
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Loading state
// ---------------------------------------------------------------------------

describe('PluginList — loading state', () => {
  it('renders loading indicator when isLoading is true', () => {
    render(<PluginList plugins={[]} isLoading />);
    expect(screen.getByTestId('plugin-list-loading')).toBeInTheDocument();
  });

  it('does not render the table when isLoading is true', () => {
    render(<PluginList plugins={[]} isLoading />);
    expect(screen.queryByRole('table')).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

describe('PluginList — empty state', () => {
  it('renders empty message when plugins array is empty', () => {
    render(<PluginList plugins={[]} />);
    expect(screen.getByTestId('plugin-list-empty')).toBeInTheDocument();
  });

  it('does not render a table when the list is empty', () => {
    render(<PluginList plugins={[]} />);
    expect(screen.queryByRole('table')).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Plugin rows
// ---------------------------------------------------------------------------

describe('PluginList — plugin rows', () => {
  it('renders a row for each plugin', () => {
    const plugins = [
      makePlugin({ id: 'p-1', name: 'Alpha', version: '1.0.0' }),
      makePlugin({ id: 'p-2', name: 'Beta',  version: '2.0.0' }),
    ];
    render(<PluginList plugins={plugins} />);

    expect(screen.getByTestId('plugin-row-p-1')).toBeInTheDocument();
    expect(screen.getByTestId('plugin-row-p-2')).toBeInTheDocument();
  });

  it('displays the plugin name', () => {
    render(<PluginList plugins={[makePlugin({ name: 'Awesome Plugin' })]} />);
    expect(screen.getByText('Awesome Plugin')).toBeInTheDocument();
  });

  it('displays the plugin version', () => {
    render(<PluginList plugins={[makePlugin({ version: '3.2.1' })]} />);
    expect(screen.getByText('3.2.1')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Status badge
// ---------------------------------------------------------------------------

describe('PluginList — status badge', () => {
  it('shows "Pending" for PENDING status', () => {
    render(<PluginList plugins={[makePlugin({ id: 'p1', status: 'PENDING' })]} />);
    expect(screen.getByTestId('plugin-status-p1')).toHaveTextContent('Pending');
  });

  it('shows "Active" for ACTIVE status', () => {
    render(<PluginList plugins={[makePlugin({ id: 'p1', status: 'ACTIVE' })]} />);
    expect(screen.getByTestId('plugin-status-p1')).toHaveTextContent('Active');
  });

  it('shows "Quarantined" for QUARANTINED status', () => {
    render(<PluginList plugins={[makePlugin({ id: 'p1', status: 'QUARANTINED' })]} />);
    expect(screen.getByTestId('plugin-status-p1')).toHaveTextContent('Quarantined');
  });

  it('applies the correct CSS class for ACTIVE status', () => {
    render(<PluginList plugins={[makePlugin({ id: 'p1', status: 'ACTIVE' })]} />);
    expect(screen.getByTestId('plugin-status-p1')).toHaveClass('plugin-status--active');
  });

  it('applies the correct CSS class for QUARANTINED status', () => {
    render(<PluginList plugins={[makePlugin({ id: 'p1', status: 'QUARANTINED' })]} />);
    expect(screen.getByTestId('plugin-status-p1')).toHaveClass('plugin-status--quarantined');
  });
});

// ---------------------------------------------------------------------------
// Admin actions
// ---------------------------------------------------------------------------

describe('PluginList — admin actions visible when isAdmin=true', () => {
  it('renders a Remove button for each plugin', () => {
    const plugins = [makePlugin({ id: 'p1', name: 'Plugin A' })];
    render(<PluginList plugins={plugins} isAdmin />);
    expect(screen.getByRole('button', { name: /Remove Plugin A/i })).toBeInTheDocument();
  });

  it('renders an Activate button for PENDING plugins', () => {
    const plugins = [makePlugin({ id: 'p1', name: 'Plugin A', status: 'PENDING' })];
    render(<PluginList plugins={plugins} isAdmin />);
    expect(screen.getByRole('button', { name: /Activate Plugin A/i })).toBeInTheDocument();
  });

  it('renders an Activate button for QUARANTINED plugins', () => {
    const plugins = [makePlugin({ id: 'p1', name: 'Plugin Q', status: 'QUARANTINED' })];
    render(<PluginList plugins={plugins} isAdmin />);
    expect(screen.getByRole('button', { name: /Activate Plugin Q/i })).toBeInTheDocument();
  });

  it('does NOT render Activate button for ACTIVE plugins', () => {
    const plugins = [makePlugin({ id: 'p1', name: 'Active Plugin', status: 'ACTIVE' })];
    render(<PluginList plugins={plugins} isAdmin />);
    expect(screen.queryByRole('button', { name: /Activate Active Plugin/i })).toBeNull();
  });

  it('calls onDelete with the correct id when Remove is clicked', () => {
    const onDelete = vi.fn();
    const plugins  = [makePlugin({ id: 'p-del', name: 'Delete Me' })];
    render(<PluginList plugins={plugins} isAdmin onDelete={onDelete} />);

    fireEvent.click(screen.getByRole('button', { name: /Remove Delete Me/i }));
    expect(onDelete).toHaveBeenCalledOnce();
    expect(onDelete).toHaveBeenCalledWith('p-del');
  });

  it('calls onActivate with the correct id when Activate is clicked', () => {
    const onActivate = vi.fn();
    const plugins    = [makePlugin({ id: 'p-act', name: 'Activate Me', status: 'PENDING' })];
    render(<PluginList plugins={plugins} isAdmin onActivate={onActivate} />);

    fireEvent.click(screen.getByRole('button', { name: /Activate Activate Me/i }));
    expect(onActivate).toHaveBeenCalledOnce();
    expect(onActivate).toHaveBeenCalledWith('p-act');
  });
});

describe('PluginList — admin actions hidden when isAdmin=false', () => {
  it('does not render Remove button when isAdmin is false', () => {
    render(<PluginList plugins={[makePlugin({ name: 'Hidden Plugin' })]} isAdmin={false} />);
    expect(screen.queryByRole('button', { name: /Remove/i })).toBeNull();
  });

  it('does not render Activate button when isAdmin is false', () => {
    render(<PluginList plugins={[makePlugin({ status: 'PENDING' })]} isAdmin={false} />);
    expect(screen.queryByRole('button', { name: /Activate/i })).toBeNull();
  });

  it('does not render the Actions column header when isAdmin is false', () => {
    render(<PluginList plugins={[makePlugin()]} isAdmin={false} />);
    expect(screen.queryByRole('columnheader', { name: /Actions/i })).toBeNull();
  });
});
