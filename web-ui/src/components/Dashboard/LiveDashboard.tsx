/**
 * LiveDashboard — chart container that subscribes to runStore and renders
 * real-time throughput, response time, and error rate charts.
 *
 * Usage:
 *   <LiveDashboard runId="abc-123" />
 *
 * The component mounts useRunStream internally; pass runId={null} or omit it
 * when no run is active.
 */

import './Dashboard.css';
import { useRunStore } from '../../store/runStore.js';

import { ThroughputChart } from './ThroughputChart.js';
import { ResponseTimeChart } from './ResponseTimeChart.js';
import { ErrorRateChart } from './ErrorRateChart.js';
import type { RunStatus } from '../../store/runStore.js';

interface LiveDashboardProps {
  /** The active run ID to stream metrics for. Pass null when idle. */
  runId?: string | null;
}

const STATUS_LABEL: Record<RunStatus, string> = {
  idle:     'Idle',
  starting: 'Starting…',
  running:  'Running',
  stopping: 'Stopping…',
  stopped:  'Stopped',
  error:    'Error',
};

export function LiveDashboard({ runId = null }: LiveDashboardProps) {
  const samples = useRunStore((s) => s.samples);
  const status  = useRunStore((s) => s.status);

  // SSE stream is managed by ResultsTabs (parent) to stay alive across tab switches

  const isLive = status === 'running';

  return (
    <div className="live-dashboard">
      <div className="live-dashboard__header">
        <h2 className="live-dashboard__title">Live Dashboard</h2>
        <div
          className={`live-dashboard__status${isLive ? ' live-dashboard__status--running' : ''}`}
        >
          <span className="live-dashboard__indicator" />
          {STATUS_LABEL[status]}
          {runId ? ` · ${runId}` : ''}
        </div>
      </div>

      <div className="live-dashboard__charts">
        <ThroughputChart    samples={samples} />
        <ResponseTimeChart  samples={samples} />
        <ErrorRateChart     samples={samples} />
      </div>
    </div>
  );
}
