/**
 * ResponseTimeChart — displays avg, p90, p95, p99 response times over the last 60 seconds.
 */

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import type { MetricsBucket } from '../../types/sse-events.js';

interface ResponseTimeChartProps {
  samples: MetricsBucket[];
}

interface TooltipPayloadItem {
  color: string;
  name: string;
  value: number;
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: TooltipPayloadItem[];
  label?: string;
}

const LINES = [
  { key: 'avg',  color: '#a78bfa', label: 'Avg' },
  { key: 'p90',  color: '#38bdf8', label: 'P90' },
  { key: 'p95',  color: '#fb923c', label: 'P95' },
  { key: 'p99',  color: '#f87171', label: 'P99' },
] as const;

function CustomTooltip({ active, payload, label }: CustomTooltipProps) {
  if (!active || !payload?.length) return null;
  return (
    <div className="custom-tooltip">
      <p className="custom-tooltip__label">{label}</p>
      {payload.map((entry) => (
        <p key={entry.name} style={{ color: entry.color, margin: 0 }}>
          {entry.name}: {entry.value.toFixed(0)} ms
        </p>
      ))}
    </div>
  );
}

function trimToWindow(samples: MetricsBucket[]): MetricsBucket[] {
  if (!samples.length) return samples;
  const cutoff = new Date(samples[samples.length - 1]!.timestamp).getTime() - 60_000;
  return samples.filter((s) => new Date(s.timestamp).getTime() >= cutoff);
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;
}

export function ResponseTimeChart({ samples }: ResponseTimeChartProps) {
  const windowed = trimToWindow(samples);

  const data = windowed.map((s) => ({
    time: formatTime(s.timestamp),
    avg: s.avgResponseTime,
    p90: s.percentile90,
    p95: s.percentile95,
    p99: s.percentile99,
  }));

  if (!data.length) {
    return (
      <div className="chart-card">
        <h3 className="chart-card__title">Response Time</h3>
        <div className="chart-card__empty">Waiting for data…</div>
      </div>
    );
  }

  return (
    <div className="chart-card">
      <h3 className="chart-card__title">Response Time (ms)</h3>
      <ResponsiveContainer width="100%" height={160}>
        <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
          <XAxis dataKey="time" tick={{ fill: '#64748b', fontSize: 11 }} interval="preserveStartEnd" />
          <YAxis tick={{ fill: '#64748b', fontSize: 11 }} width={48} />
          <Tooltip content={<CustomTooltip />} />
          <Legend
            wrapperStyle={{ fontSize: '11px', paddingTop: '4px' }}
            iconType="plainline"
          />
          {LINES.map(({ key, color, label }) => (
            <Line
              key={key}
              type="monotone"
              dataKey={key}
              stroke={color}
              name={label}
              dot={false}
              strokeWidth={2}
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
