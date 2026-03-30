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
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { DistributedConfig } from '../components/DistributedConfig/DistributedConfig.js';

// ---------------------------------------------------------------------------
// Mock the API module
// ---------------------------------------------------------------------------

vi.mock('../api/workers.js', () => ({
  listWorkers:    vi.fn(),
  registerWorker: vi.fn(),
  removeWorker:   vi.fn(),
}));

import * as workersApi from '../api/workers.js';
import type { WorkerSummary } from '../types/api.js';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeWorker(overrides: Partial<WorkerSummary> = {}): WorkerSummary {
  return {
    id:            'worker-1',
    hostname:      'worker-1.internal',
    port:          1099,
    status:        'AVAILABLE',
    lastHeartbeat: '2026-03-25T10:00:00Z',
    registeredAt:  '2026-03-25T09:00:00Z',
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

beforeEach(() => {
  vi.clearAllMocks();
});

// ---------------------------------------------------------------------------
// Loading + empty state
// ---------------------------------------------------------------------------

describe('DistributedConfig — loading and empty states', () => {
  it('shows loading indicator while fetching workers', async () => {
    vi.mocked(workersApi.listWorkers).mockReturnValue(new Promise(() => {})); // never resolves
    render(<DistributedConfig />);
    expect(screen.getByText(/Loading workers/i)).toBeInTheDocument();
  });

  it('shows empty message when no workers registered', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([]);
    await act(async () => {
      render(<DistributedConfig />);
    });
    await waitFor(() => {
      expect(screen.getByText(/No workers registered/i)).toBeInTheDocument();
    });
  });

  it('shows error message when listWorkers fails', async () => {
    vi.mocked(workersApi.listWorkers).mockRejectedValue(new Error('network error'));
    await act(async () => {
      render(<DistributedConfig />);
    });
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('network error');
    });
  });
});

// ---------------------------------------------------------------------------
// Worker list rendering
// ---------------------------------------------------------------------------

describe('DistributedConfig — worker list rendering', () => {
  it('renders hostname and port for each worker', async () => {
    const worker = makeWorker();
    vi.mocked(workersApi.listWorkers).mockResolvedValue([worker]);

    await act(async () => {
      render(<DistributedConfig />);
    });

    await waitFor(() => {
      expect(screen.getByText('worker-1.internal')).toBeInTheDocument();
      expect(screen.getByText(':1099')).toBeInTheDocument();
    });
  });

  it('shows Online badge for AVAILABLE worker', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([makeWorker({ status: 'AVAILABLE' })]);
    await act(async () => { render(<DistributedConfig />); });
    await waitFor(() => {
      expect(screen.getByTestId('worker-status-worker-1')).toHaveTextContent('Online');
    });
  });

  it('shows Busy badge for BUSY worker', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([makeWorker({ status: 'BUSY' })]);
    await act(async () => { render(<DistributedConfig />); });
    await waitFor(() => {
      expect(screen.getByTestId('worker-status-worker-1')).toHaveTextContent('Busy');
    });
  });

  it('shows Offline badge for OFFLINE worker', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([makeWorker({ status: 'OFFLINE' })]);
    await act(async () => { render(<DistributedConfig />); });
    await waitFor(() => {
      expect(screen.getByTestId('worker-status-worker-1')).toHaveTextContent('Offline');
    });
  });

  it('renders multiple workers', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([
      makeWorker({ id: 'w1', hostname: 'host-a.internal', port: 1099 }),
      makeWorker({ id: 'w2', hostname: 'host-b.internal', port: 2099 }),
    ]);
    await act(async () => { render(<DistributedConfig />); });
    await waitFor(() => {
      expect(screen.getByText('host-a.internal')).toBeInTheDocument();
      expect(screen.getByText('host-b.internal')).toBeInTheDocument();
    });
  });
});

// ---------------------------------------------------------------------------
// Selection behaviour
// ---------------------------------------------------------------------------

describe('DistributedConfig — worker selection', () => {
  it('updates selection count when worker is checked', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([makeWorker()]);
    await act(async () => { render(<DistributedConfig planId="plan-1" />); });

    await waitFor(() => {
      expect(screen.getByText(/Select workers/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('checkbox', { name: /Select worker/i }));

    expect(screen.getByText(/1 worker selected/i)).toBeInTheDocument();
  });

  it('deselects worker on second click', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([makeWorker()]);
    await act(async () => { render(<DistributedConfig planId="plan-1" />); });
    await waitFor(() => screen.getByRole('checkbox'));

    const checkbox = screen.getByRole('checkbox', { name: /Select worker/i });
    fireEvent.click(checkbox);
    fireEvent.click(checkbox);

    expect(screen.getByText(/Select workers/i)).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Start Distributed Run button
// ---------------------------------------------------------------------------

describe('DistributedConfig — Start Distributed Run button', () => {
  it('is disabled when no workers selected', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([makeWorker()]);
    await act(async () => { render(<DistributedConfig planId="plan-1" />); });
    await waitFor(() => screen.getByTestId('start-distributed-run-btn'));

    expect(screen.getByTestId('start-distributed-run-btn')).toBeDisabled();
  });

  it('is disabled when no planId provided', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([makeWorker()]);
    await act(async () => { render(<DistributedConfig />); });
    await waitFor(() => screen.getByRole('checkbox'));

    fireEvent.click(screen.getByRole('checkbox', { name: /Select worker/i }));

    expect(screen.getByTestId('start-distributed-run-btn')).toBeDisabled();
  });

  it('is enabled when planId set and a worker is selected', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([makeWorker()]);
    await act(async () => { render(<DistributedConfig planId="plan-1" />); });
    await waitFor(() => screen.getByRole('checkbox'));

    fireEvent.click(screen.getByRole('checkbox', { name: /Select worker/i }));

    expect(screen.getByTestId('start-distributed-run-btn')).not.toBeDisabled();
  });

  it('calls onStartDistributedRun with worker addresses', async () => {
    const onStart = vi.fn();
    vi.mocked(workersApi.listWorkers).mockResolvedValue([
      makeWorker({ hostname: 'worker-1.internal', port: 1099 }),
    ]);
    await act(async () => {
      render(<DistributedConfig planId="plan-1" onStartDistributedRun={onStart} />);
    });
    await waitFor(() => screen.getByRole('checkbox'));

    fireEvent.click(screen.getByRole('checkbox', { name: /Select worker/i }));
    fireEvent.click(screen.getByTestId('start-distributed-run-btn'));

    expect(onStart).toHaveBeenCalledWith(['worker-1.internal:1099']);
  });

  it('passes multiple selected worker addresses', async () => {
    const onStart = vi.fn();
    vi.mocked(workersApi.listWorkers).mockResolvedValue([
      makeWorker({ id: 'w1', hostname: 'host-a', port: 1099 }),
      makeWorker({ id: 'w2', hostname: 'host-b', port: 2099 }),
    ]);
    await act(async () => {
      render(<DistributedConfig planId="plan-1" onStartDistributedRun={onStart} />);
    });
    await waitFor(() => screen.getAllByRole('checkbox'));

    const checkboxes = screen.getAllByRole('checkbox');
    fireEvent.click(checkboxes[0]!);
    fireEvent.click(checkboxes[1]!);
    fireEvent.click(screen.getByTestId('start-distributed-run-btn'));

    expect(onStart).toHaveBeenCalledWith(
      expect.arrayContaining(['host-a:1099', 'host-b:2099'])
    );
  });
});

// ---------------------------------------------------------------------------
// Add worker form
// ---------------------------------------------------------------------------

describe('DistributedConfig — add worker form', () => {
  it('shows form when "+ Add Worker" is clicked', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([]);
    await act(async () => { render(<DistributedConfig />); });
    await waitFor(() => screen.getByRole('button', { name: /Add Worker/i }));

    fireEvent.click(screen.getByRole('button', { name: /Add Worker/i }));

    expect(screen.getByLabelText('Worker hostname')).toBeInTheDocument();
    expect(screen.getByLabelText('Worker port')).toBeInTheDocument();
  });

  it('hides form when Cancel is clicked', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([]);
    await act(async () => { render(<DistributedConfig />); });
    await waitFor(() => screen.getByRole('button', { name: /Add Worker/i }));

    fireEvent.click(screen.getByRole('button', { name: /Add Worker/i }));
    fireEvent.click(screen.getByRole('button', { name: /Cancel/i }));

    expect(screen.queryByLabelText('Worker hostname')).toBeNull();
  });

  it('registers worker and appends to list on save', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([]);
    const newWorker = makeWorker({ id: 'new-w', hostname: 'new-host.internal', port: 4099 });
    vi.mocked(workersApi.registerWorker).mockResolvedValue(newWorker);

    await act(async () => { render(<DistributedConfig />); });
    await waitFor(() => screen.getByRole('button', { name: /Add Worker/i }));

    fireEvent.click(screen.getByRole('button', { name: /Add Worker/i }));
    fireEvent.change(screen.getByLabelText('Worker hostname'), {
      target: { value: 'new-host.internal' },
    });
    fireEvent.change(screen.getByLabelText('Worker port'), {
      target: { value: '4099' },
    });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Save worker' }));
    });

    await waitFor(() => {
      expect(screen.getByText('new-host.internal')).toBeInTheDocument();
    });
    expect(workersApi.registerWorker).toHaveBeenCalledWith({
      hostname: 'new-host.internal',
      port: 4099,
    });
  });

  it('shows form error when hostname is blank on save', async () => {
    vi.mocked(workersApi.listWorkers).mockResolvedValue([]);
    await act(async () => { render(<DistributedConfig />); });
    await waitFor(() => screen.getByRole('button', { name: /Add Worker/i }));

    fireEvent.click(screen.getByRole('button', { name: /Add Worker/i }));
    fireEvent.click(screen.getByRole('button', { name: 'Save worker' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Hostname is required');
  });
});

// ---------------------------------------------------------------------------
// Remove worker
// ---------------------------------------------------------------------------

describe('DistributedConfig — remove worker', () => {
  it('calls removeWorker API and removes from list', async () => {
    const worker = makeWorker();
    vi.mocked(workersApi.listWorkers).mockResolvedValue([worker]);
    vi.mocked(workersApi.removeWorker).mockResolvedValue(undefined);

    await act(async () => { render(<DistributedConfig />); });
    await waitFor(() => screen.getByRole('button', { name: /Remove worker/i }));

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Remove worker/i }));
    });

    expect(workersApi.removeWorker).toHaveBeenCalledWith('worker-1');
    await waitFor(() => {
      expect(screen.queryByText('worker-1.internal')).toBeNull();
    });
  });

  it('shows error when removeWorker fails', async () => {
    const worker = makeWorker();
    vi.mocked(workersApi.listWorkers).mockResolvedValue([worker]);
    vi.mocked(workersApi.removeWorker).mockRejectedValue(new Error('delete failed'));

    await act(async () => { render(<DistributedConfig />); });
    await waitFor(() => screen.getByRole('button', { name: /Remove worker/i }));

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Remove worker/i }));
    });

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('delete failed');
    });
  });
});
