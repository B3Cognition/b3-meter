/**
 * Zod validation schemas for Listener elements.
 */

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
