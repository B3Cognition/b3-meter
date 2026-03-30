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

/* ─── gRPC Sampler ─── */

export const grpcSamplerSchema = z.object({
  /** gRPC server hostname or IP address. */
  host: z.string().min(1, 'Host is required'),

  /** gRPC server port. */
  port: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(1, 'Must be between 1 and 65535')
    .max(65535, 'Must be between 1 and 65535'),

  /** Fully-qualified gRPC service name, e.g. "greeter.Greeter". */
  service: z.string().min(1, 'Service is required'),

  /** RPC method name, e.g. "SayHello". */
  method: z.string().min(1, 'Method is required'),

  /** JSON-encoded request body. */
  requestBody: z.string(),

  /** Whether to use TLS for the gRPC connection. */
  useTls: z.boolean(),
});

export type GrpcSamplerFormValues = z.infer<typeof grpcSamplerSchema>;

export const grpcSamplerDefaults: GrpcSamplerFormValues = {
  host: 'localhost',
  port: 50051,
  service: 'greeter.Greeter',
  method: 'SayHello',
  requestBody: '{"name":"World"}',
  useTls: false,
};

/* ─── MQTT Sampler ─── */

export const mqttSamplerSchema = z.object({
  /** MQTT broker URL, e.g. "tcp://localhost:1883". */
  broker: z.string().min(1, 'Broker URL is required'),

  /** MQTT topic to publish/subscribe. */
  topic: z.string().min(1, 'Topic is required'),

  /** Message payload for publish actions. */
  message: z.string(),

  /** Quality of Service level (0, 1, or 2). */
  qos: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be 0, 1, or 2')
    .max(2, 'Must be 0, 1, or 2'),

  /** Whether to publish or subscribe. */
  action: z.enum(['publish', 'subscribe'], {
    errorMap: () => ({ message: 'Must be publish or subscribe' }),
  }),

  /** Timeout in milliseconds. */
  timeout: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),
});

export type MqttSamplerFormValues = z.infer<typeof mqttSamplerSchema>;

export const mqttSamplerDefaults: MqttSamplerFormValues = {
  broker: 'tcp://localhost:1883',
  topic: 'test/topic',
  message: '',
  qos: 0,
  action: 'publish',
  timeout: 5000,
};

/* ─── WebRTC Sampler ─── */

export const webrtcSamplerSchema = z.object({
  /** HTTP(S) URL for SDP offer/answer exchange. */
  signalingUrl: z.string().min(1, 'Signaling URL is required'),

  /** SDP offer body, or "generate" to use a minimal auto-generated offer. */
  offerSdp: z.string(),

  /** Connection timeout in milliseconds. */
  connectTimeout: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),
});

export type WebrtcSamplerFormValues = z.infer<typeof webrtcSamplerSchema>;

export const webrtcSamplerDefaults: WebrtcSamplerFormValues = {
  signalingUrl: 'http://localhost:8088/offer',
  offerSdp: 'generate',
  connectTimeout: 10000,
};

/* ─── DASH Sampler ─── */

export const dashSamplerSchema = z.object({
  /** URL of the DASH manifest (.mpd). */
  url: z.string().min(1, 'URL is required'),

  /** Quality selection: "best", "worst", or a specific bitrate. */
  quality: z.string(),

  /** Number of media segments to download. */
  segmentCount: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(1, 'Must be at least 1'),

  /** Connection timeout in milliseconds. */
  connectTimeout: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),
});

export type DashSamplerFormValues = z.infer<typeof dashSamplerSchema>;

export const dashSamplerDefaults: DashSamplerFormValues = {
  url: 'http://localhost:8086/live/manifest.mpd',
  quality: 'best',
  segmentCount: 3,
  connectTimeout: 5000,
};

/* ─── HLS Sampler ─── */

export const hlsSamplerSchema = z.object({
  /** URL of the HLS master playlist (.m3u8). */
  url: z.string().min(1, 'URL is required'),

  /** Quality selection: "best", "worst", or a specific bandwidth. */
  quality: z.string(),

  /** Number of media segments to download. */
  segmentCount: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(1, 'Must be at least 1'),

  /** Connection timeout in milliseconds. */
  connectTimeout: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),

  /** Response timeout in milliseconds. */
  responseTimeout: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),
});

export type HlsSamplerFormValues = z.infer<typeof hlsSamplerSchema>;

export const hlsSamplerDefaults: HlsSamplerFormValues = {
  url: 'http://localhost:8084/live/master.m3u8',
  quality: 'best',
  segmentCount: 3,
  connectTimeout: 5000,
  responseTimeout: 10000,
};

/* ─── SSE Sampler ─── */

export const sseSamplerSchema = z.object({
  /** SSE endpoint URL. */
  url: z.string().min(1, 'URL is required'),

  /** Duration in milliseconds to listen for events. */
  duration: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),

  /** Optional SSE event name filter. Empty string means all events. */
  eventName: z.string(),
});

export type SseSamplerFormValues = z.infer<typeof sseSamplerSchema>;

export const sseSamplerDefaults: SseSamplerFormValues = {
  url: 'http://localhost:8083/events',
  duration: 5000,
  eventName: '',
};

/* ─── WebSocket Sampler ─── */

export const webSocketSamplerSchema = z.object({
  /** WebSocket server URL, e.g. "ws://localhost:8082". */
  url: z.string().min(1, 'URL is required'),

  /** Message to send after connecting. Empty string means read-only. */
  message: z.string(),

  /** Connection timeout in milliseconds. */
  connectTimeout: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),

  /** Response timeout in milliseconds. */
  responseTimeout: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),
});

export type WebSocketSamplerFormValues = z.infer<typeof webSocketSamplerSchema>;

export const webSocketSamplerDefaults: WebSocketSamplerFormValues = {
  url: 'ws://localhost:8082',
  message: '',
  connectTimeout: 5000,
  responseTimeout: 10000,
};
