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
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import type { MetricsBucket } from '../../types/sse-events.js';

interface ThroughputChartProps {
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
      <p style={{ color: '#38bdf8', margin: 0 }}>
        {payload[0]?.value?.toFixed(1)} samples/sec
      </p>
    </div>
  );
}

/** Keep only the last 60 seconds of data. */
function trimToWindow(samples: MetricsBucket[]): MetricsBucket[] {
  if (!samples.length) return samples;
  const cutoff = new Date(samples[samples.length - 1]!.timestamp).getTime() - 60_000;
  return samples.filter((s) => new Date(s.timestamp).getTime() >= cutoff);
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;
}

export function ThroughputChart({ samples }: ThroughputChartProps) {
  const windowed = trimToWindow(samples);

  const data = windowed.map((s) => ({
    time: formatTime(s.timestamp),
    value: s.samplesPerSecond,
  }));

  if (!data.length) {
    return (
      <div className="chart-card">
        <h3 className="chart-card__title">Throughput</h3>
        <div className="chart-card__empty">Waiting for data…</div>
      </div>
    );
  }

  return (
    <div className="chart-card">
      <h3 className="chart-card__title">Throughput (samples/sec)</h3>
      <ResponsiveContainer width="100%" height={160}>
        <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
          <XAxis dataKey="time" tick={{ fill: '#64748b', fontSize: 11 }} interval="preserveStartEnd" />
          <YAxis tick={{ fill: '#64748b', fontSize: 11 }} width={40} />
          <Tooltip content={<CustomTooltip />} />
          <Line
            type="monotone"
            dataKey="value"
            stroke="#38bdf8"
            dot={false}
            strokeWidth={2}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
