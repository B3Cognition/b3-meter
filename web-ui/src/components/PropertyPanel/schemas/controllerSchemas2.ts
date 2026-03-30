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
// ForEach Controller
// ---------------------------------------------------------------------------

export const forEachControllerSchema = z.object({
  /** Variable name prefix to iterate over (e.g. "inputVar" reads inputVar_1, inputVar_2, ...). */
  inputVal: z.string().default(''),

  /** Output variable name set to the current value on each iteration. */
  returnVal: z.string().default(''),

  /** Start index (iteration begins at startIndex + 1). */
  startIndex: z.number().default(0),

  /** End index (exclusive). 0 means no upper bound. */
  endIndex: z.number().default(0),

  /** Whether to use "_" separator between prefix and index. */
  useSeparator: z.boolean().default(true),
});

export type ForEachControllerFormValues = z.infer<typeof forEachControllerSchema>;

export const forEachControllerDefaults: ForEachControllerFormValues = {
  inputVal: '',
  returnVal: '',
  startIndex: 0,
  endIndex: 0,
  useSeparator: true,
};

// ---------------------------------------------------------------------------
// Throughput Controller
// ---------------------------------------------------------------------------

export const throughputControllerSchema = z.object({
  /** Execution mode: 0 = Percent Executions, 1 = Total Executions. */
  style: z.number().default(0),

  /** Apply count/percent per thread (vs globally). */
  perThread: z.boolean().default(false),

  /** Total number of executions (used when style = 1). */
  maxThroughput: z.number().default(1),

  /** Percentage of iterations to execute (used when style = 0). */
  percentThroughput: z.number().default(100.0),
});

export type ThroughputControllerFormValues = z.infer<typeof throughputControllerSchema>;

export const throughputControllerDefaults: ThroughputControllerFormValues = {
  style: 0,
  perThread: false,
  maxThroughput: 1,
  percentThroughput: 100.0,
};

// ---------------------------------------------------------------------------
// Once Only Controller
// ---------------------------------------------------------------------------

/** Once Only Controller has no additional fields beyond Name and Comments. */
export const onceOnlyControllerSchema = z.object({});

export type OnceOnlyControllerFormValues = z.infer<typeof onceOnlyControllerSchema>;

export const onceOnlyControllerDefaults: OnceOnlyControllerFormValues = {};

// ---------------------------------------------------------------------------
// Recording Controller
// ---------------------------------------------------------------------------

/** Recording Controller is a container — no additional fields beyond Name and Comments. */
export const recordingControllerSchema = z.object({});

export type RecordingControllerFormValues = z.infer<typeof recordingControllerSchema>;

export const recordingControllerDefaults: RecordingControllerFormValues = {};

// ---------------------------------------------------------------------------
// Runtime Controller
// ---------------------------------------------------------------------------

export const runtimeControllerSchema = z.object({
  /** Runtime duration in seconds. */
  seconds: z.number().min(0, 'Seconds must be non-negative').default(10),
});

export type RuntimeControllerFormValues = z.infer<typeof runtimeControllerSchema>;

export const runtimeControllerDefaults: RuntimeControllerFormValues = {
  seconds: 10,
};
