/**
 * Tests for RunControls component.
 *
 * Covers:
 *   - Run button disabled when no plan is loaded
 *   - Run button disabled when status is "running"
 *   - Run button disabled when hasValidationErrors is true
 *   - Click Run calls startRun API and transitions to running
 *   - Click Stop calls stopRun API and transitions to stopped
 *   - Click Stop Now calls stopRunNow API and transitions to stopped
 *   - Stop Now appears only when running (starting/running/stopping)
 *   - Status badge displays correctly for each status
 *   - Error message shown on API failure
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { RunControls } from '../components/RunControls/RunControls.js';
import { useRunStore } from '../store/runStore.js';
import { useTestPlanStore } from '../store/testPlanStore.js';
import type { TestPlanNode, TestPlanTree } from '../types/test-plan.js';

// ---------------------------------------------------------------------------
// Mock the API module
// ---------------------------------------------------------------------------

vi.mock('../api/runs.js', () => ({
  startRun:   vi.fn(),
  stopRun:    vi.fn(),
  stopRunNow: vi.fn(),
  getRun:     vi.fn(),
  getMetrics: vi.fn(),
}));

import * as runsApi from '../api/runs.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeTree(): TestPlanTree {
  const root: TestPlanNode = {
    id: 'plan-1',
    type: 'TestPlan',
    name: 'My Plan',
    enabled: true,
    properties: {},
    children: [],
  };
  return { root };
}

// ---------------------------------------------------------------------------
// Setup / teardown
// ---------------------------------------------------------------------------

beforeEach(() => {
  useRunStore.getState().reset();
  useTestPlanStore.setState({ tree: null, selectedNodeId: null });
  vi.clearAllMocks();
});

afterEach(() => {
  useRunStore.getState().reset();
  useTestPlanStore.setState({ tree: null, selectedNodeId: null });
});

// ---------------------------------------------------------------------------
// Run button — disabled states
// ---------------------------------------------------------------------------

describe('RunControls — Run button disabled', () => {
  it('is disabled when no plan is loaded', () => {
    render(<RunControls />);
    const runBtn = screen.getByRole('button', { name: /Run/i });
    expect(runBtn).toBeDisabled();
  });

  it('is disabled when status is "running"', () => {
    useTestPlanStore.setState({ tree: makeTree() });
    useRunStore.getState().setStatus('running');
    render(<RunControls />);
    // When running, Stop and Stop Now are shown — Run button is absent
    expect(screen.queryByRole('button', { name: /^Run$/i })).toBeNull();
  });

  it('is disabled when hasValidationErrors is true', () => {
    useTestPlanStore.setState({ tree: makeTree() });
    render(<RunControls hasValidationErrors />);
    const runBtn = screen.getByRole('button', { name: /Run/i });
    expect(runBtn).toBeDisabled();
  });

  it('is enabled when a plan is loaded and status is idle', () => {
    useTestPlanStore.setState({ tree: makeTree() });
    render(<RunControls />);
    const runBtn = screen.getByRole('button', { name: /Run/i });
    expect(runBtn).not.toBeDisabled();
  });

  it('is enabled again after a previous run has stopped', () => {
    useTestPlanStore.setState({ tree: makeTree() });
    useRunStore.getState().setStatus('stopped');
    render(<RunControls />);
    const runBtn = screen.getByRole('button', { name: /Run/i });
    expect(runBtn).not.toBeDisabled();
  });
});

// ---------------------------------------------------------------------------
// Click Run calls startRun API
// ---------------------------------------------------------------------------

describe('RunControls — Click Run', () => {
  it('calls startRun with the root node id as planId', async () => {
    useTestPlanStore.setState({ tree: makeTree() });
    vi.mocked(runsApi.startRun).mockResolvedValue({ id: 'run-abc', runId: 'run-abc', startedAt: '2024-01-01T00:00:00Z' });

    render(<RunControls />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Run/i }));
    });

    expect(runsApi.startRun).toHaveBeenCalledWith({ planId: 'plan-1' });
  });

  it('transitions status to "running" after startRun resolves', async () => {
    useTestPlanStore.setState({ tree: makeTree() });
    vi.mocked(runsApi.startRun).mockResolvedValue({ id: 'run-abc', runId: 'run-abc', startedAt: '2024-01-01T00:00:00Z' });

    render(<RunControls />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Run/i }));
    });

    await waitFor(() => {
      expect(screen.getByTestId('run-status-badge')).toHaveTextContent('Running');
    });
  });

  it('sets status to "error" when startRun rejects', async () => {
    useTestPlanStore.setState({ tree: makeTree() });
    vi.mocked(runsApi.startRun).mockRejectedValue(new Error('network error'));

    render(<RunControls />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Run/i }));
    });

    await waitFor(() => {
      expect(screen.getByTestId('run-status-badge')).toHaveTextContent('Error');
    });
  });

  it('shows error message when startRun fails', async () => {
    useTestPlanStore.setState({ tree: makeTree() });
    vi.mocked(runsApi.startRun).mockRejectedValue(new Error('network error'));

    render(<RunControls />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Run/i }));
    });

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('network error');
    });
  });
});

// ---------------------------------------------------------------------------
// Click Stop calls stopRun API
// ---------------------------------------------------------------------------

describe('RunControls — Click Stop', () => {
  it('calls stopRun with the current runId', async () => {
    useTestPlanStore.setState({ tree: makeTree() });
    useRunStore.getState().setRunId('run-abc');
    useRunStore.getState().setStatus('running');
    vi.mocked(runsApi.stopRun).mockResolvedValue({ runId: 'run-abc', stoppedAt: '2024-01-01T01:00:00Z' });

    render(<RunControls />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /^Stop$/i }));
    });

    expect(runsApi.stopRun).toHaveBeenCalledWith('run-abc');
  });

  it('transitions status to "stopped" after stopRun resolves', async () => {
    useTestPlanStore.setState({ tree: makeTree() });
    useRunStore.getState().setRunId('run-abc');
    useRunStore.getState().setStatus('running');
    vi.mocked(runsApi.stopRun).mockResolvedValue({ runId: 'run-abc', stoppedAt: '2024-01-01T01:00:00Z' });

    render(<RunControls />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /^Stop$/i }));
    });

    await waitFor(() => {
      expect(screen.getByTestId('run-status-badge')).toHaveTextContent('Stopped');
    });
  });
});

// ---------------------------------------------------------------------------
// Click Stop Now calls stopRunNow API
// ---------------------------------------------------------------------------

describe('RunControls — Click Stop Now', () => {
  it('calls stopRunNow with the current runId', async () => {
    useTestPlanStore.setState({ tree: makeTree() });
    useRunStore.getState().setRunId('run-abc');
    useRunStore.getState().setStatus('running');
    vi.mocked(runsApi.stopRunNow).mockResolvedValue({ runId: 'run-abc', stoppedAt: '2024-01-01T01:00:00Z' });

    render(<RunControls />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Stop Now/i }));
    });

    expect(runsApi.stopRunNow).toHaveBeenCalledWith('run-abc');
  });

  it('transitions status to "stopped" after stopRunNow resolves', async () => {
    useTestPlanStore.setState({ tree: makeTree() });
    useRunStore.getState().setRunId('run-abc');
    useRunStore.getState().setStatus('running');
    vi.mocked(runsApi.stopRunNow).mockResolvedValue({ runId: 'run-abc', stoppedAt: '2024-01-01T01:00:00Z' });

    render(<RunControls />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Stop Now/i }));
    });

    await waitFor(() => {
      expect(screen.getByTestId('run-status-badge')).toHaveTextContent('Stopped');
    });
  });
});

// ---------------------------------------------------------------------------
// Stop Now visibility — only during active states
// ---------------------------------------------------------------------------

describe('RunControls — Stop Now visibility', () => {
  it('is not shown when status is idle', () => {
    useTestPlanStore.setState({ tree: makeTree() });
    render(<RunControls />);
    expect(screen.queryByRole('button', { name: /Stop Now/i })).toBeNull();
  });

  it('is not shown when status is stopped', () => {
    useTestPlanStore.setState({ tree: makeTree() });
    useRunStore.getState().setStatus('stopped');
    render(<RunControls />);
    expect(screen.queryByRole('button', { name: /Stop Now/i })).toBeNull();
  });

  it('is not shown when status is error', () => {
    useTestPlanStore.setState({ tree: makeTree() });
    useRunStore.getState().setStatus('error');
    render(<RunControls />);
    expect(screen.queryByRole('button', { name: /Stop Now/i })).toBeNull();
  });

  it('is shown when status is running', () => {
    useRunStore.getState().setStatus('running');
    useRunStore.getState().setRunId('run-abc');
    render(<RunControls />);
    expect(screen.getByRole('button', { name: /Stop Now/i })).toBeInTheDocument();
  });

  it('is shown when status is starting', () => {
    useRunStore.getState().setStatus('starting');
    useRunStore.getState().setRunId('run-abc');
    render(<RunControls />);
    expect(screen.getByRole('button', { name: /Stop Now/i })).toBeInTheDocument();
  });

  it('is shown when status is stopping', () => {
    useRunStore.getState().setStatus('stopping');
    useRunStore.getState().setRunId('run-abc');
    render(<RunControls />);
    expect(screen.getByRole('button', { name: /Stop Now/i })).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Status badge display
// ---------------------------------------------------------------------------

describe('RunControls — status badge', () => {
  it('shows Idle badge by default', () => {
    render(<RunControls />);
    expect(screen.getByTestId('run-status-badge')).toHaveTextContent('Idle');
  });

  it('shows Starting… badge when status is starting', () => {
    useRunStore.getState().setStatus('starting');
    render(<RunControls />);
    expect(screen.getByTestId('run-status-badge')).toHaveTextContent('Starting');
  });

  it('shows Running badge when status is running', () => {
    useRunStore.getState().setStatus('running');
    render(<RunControls />);
    expect(screen.getByTestId('run-status-badge')).toHaveTextContent('Running');
  });

  it('shows Stopping… badge when status is stopping', () => {
    useRunStore.getState().setStatus('stopping');
    render(<RunControls />);
    expect(screen.getByTestId('run-status-badge')).toHaveTextContent('Stopping');
  });

  it('shows Stopped badge when status is stopped', () => {
    useRunStore.getState().setStatus('stopped');
    render(<RunControls />);
    expect(screen.getByTestId('run-status-badge')).toHaveTextContent('Stopped');
  });

  it('shows Error badge when status is error', () => {
    useRunStore.getState().setStatus('error');
    render(<RunControls />);
    expect(screen.getByTestId('run-status-badge')).toHaveTextContent('Error');
  });
});
