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
import { z } from 'zod';

// ---------------------------------------------------------------------------
// Backend Listener
// ---------------------------------------------------------------------------

export const backendListenerSchema = z.object({
  /** Implementation class: "graphite" or "influxdb". */
  className: z.enum(['graphite', 'influxdb']).default('graphite'),
  /** Metric name prefix. */
  metricPrefix: z.string().default('jmeter'),
  /** Flush interval in milliseconds. */
  flushIntervalMs: z.number().min(1000, 'Flush interval must be at least 1000ms').default(5000),
  /** Graphite host. */
  graphiteHost: z.string().default('localhost'),
  /** Graphite port. */
  graphitePort: z.number().min(1).max(65535).default(2003),
  /** InfluxDB write endpoint URL. */
  influxdbUrl: z.string().default('http://localhost:8086'),
  /** InfluxDB authentication token. */
  influxdbToken: z.string().default(''),
  /** InfluxDB bucket name. */
  influxdbBucket: z.string().default('jmeter'),
  /** InfluxDB organization. */
  influxdbOrg: z.string().default(''),
});

export type BackendListenerFormValues = z.infer<typeof backendListenerSchema>;

export const backendListenerDefaults: BackendListenerFormValues = {
  className: 'graphite',
  metricPrefix: 'jmeter',
  flushIntervalMs: 5000,
  graphiteHost: 'localhost',
  graphitePort: 2003,
  influxdbUrl: 'http://localhost:8086',
  influxdbToken: '',
  influxdbBucket: 'jmeter',
  influxdbOrg: '',
};

// ---------------------------------------------------------------------------
// View Results in Table (ResultCollector variant)
// ---------------------------------------------------------------------------

export const viewResultsTableSchema = z.object({
  /** Output file path for saving results. */
  filename: z.string().default(''),

  /** Log only errors. */
  errorLogging: z.boolean().default(false),
});

export type ViewResultsTableFormValues = z.infer<typeof viewResultsTableSchema>;

export const viewResultsTableDefaults: ViewResultsTableFormValues = {
  filename: '',
  errorLogging: false,
};
