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

export const threadGroupSchema = z.object({
  /** Action to take when a sampler error occurs. */
  onSampleError: z
    .enum(['continue', 'startnextloop', 'stopthread', 'stoptest', 'stoptestnow'])
    .default('continue'),

  /** Number of virtual users. Must be a positive integer, max 1,000,000. */
  num_threads: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(1, 'Must be at least 1')
    .max(1_000_000, 'Must be at most 1,000,000'),

  /** Time (seconds) to ramp up to full thread count. Must be non-negative. */
  ramp_time: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),

  /**
   * Loop count. -1 means loop forever.
   * Range: -1 to 2,147,483,647 (Java int max).
   */
  loops: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(-1, 'Must be at least -1')
    .max(2_147_483_647, 'Must be at most 2,147,483,647'),

  /** UI toggle: when true, loops is set to -1 (infinite). */
  infinite: z.boolean().default(false),

  /** Whether to use the same user on the next iteration. */
  sameUserOnNextIteration: z.boolean().default(true),

  /** Whether the scheduler is enabled. */
  scheduler: z.boolean().default(false),

  /** Test duration in seconds. Must be non-negative. */
  duration: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),

  /** Startup delay in seconds. Must be non-negative. */
  delay: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),
});

export type ThreadGroupFormValues = z.infer<typeof threadGroupSchema>;

export const threadGroupDefaults: ThreadGroupFormValues = {
  onSampleError: 'continue',
  num_threads: 1,
  ramp_time: 1,
  loops: 1,
  infinite: false,
  sameUserOnNextIteration: true,
  scheduler: false,
  duration: 0,
  delay: 0,
};
