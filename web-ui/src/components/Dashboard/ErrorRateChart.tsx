/**
 * ErrorRateChart — displays error rate (%) as an area chart over the last 60 seconds.
 */

import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import type { MetricsBucket } from '../../types/sse-events.js';

interface ErrorRateChartProps {
  samples: MetricsBucket[];
}

interface TooltipPayloadItem {
  value: number;
  dataKey: string;
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: TooltipPayloadItem[];
  label?: string;
}

function CustomTooltip({ active, payload, label }: CustomTooltipProps) {
  if (!active || !payload?.length) return null;
  return (
    <div className="custom-tooltip">
      <p className="custom-tooltip__label">{label}</p>
      <p style={{ color: '#f87171', margin: 0 }}>
        Error rate: {payload[0]?.value?.toFixed(2)} %
      </p>
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

/** Derive error rate percentage from a MetricsBucket. */
function errorRate(s: MetricsBucket): number {
  if (s.sampleCount === 0) return 0;
  return (s.errorCount / s.sampleCount) * 100;
}

export function ErrorRateChart({ samples }: ErrorRateChartProps) {
  const windowed = trimToWindow(samples);

  const data = windowed.map((s) => ({
    time: formatTime(s.timestamp),
    errorRate: errorRate(s),
  }));

  if (!data.length) {
    return (
      <div className="chart-card">
        <h3 className="chart-card__title">Error Rate</h3>
        <div className="chart-card__empty">Waiting for data…</div>
      </div>
    );
  }

  return (
    <div className="chart-card">
      <h3 className="chart-card__title">Error Rate (%)</h3>
      <ResponsiveContainer width="100%" height={140}>
        <AreaChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id="errorGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%"  stopColor="#f87171" stopOpacity={0.3} />
              <stop offset="95%" stopColor="#f87171" stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
          <XAxis dataKey="time" tick={{ fill: '#64748b', fontSize: 11 }} interval="preserveStartEnd" />
          <YAxis
            tick={{ fill: '#64748b', fontSize: 11 }}
            width={40}
            domain={[0, 'auto']}
            tickFormatter={(v: number) => `${v}%`}
          />
          <Tooltip content={<CustomTooltip />} />
          <Area
            type="monotone"
            dataKey="errorRate"
            stroke="#f87171"
            fill="url(#errorGradient)"
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
