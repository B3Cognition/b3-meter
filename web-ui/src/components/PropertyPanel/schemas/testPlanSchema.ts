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

/** Schema for a single user-defined variable row. */
export const userDefinedVariableSchema = z.object({
  name: z.string().default(''),
  value: z.string().default(''),
  description: z.string().default(''),
});

export type UserDefinedVariable = z.infer<typeof userDefinedVariableSchema>;

export const testPlanSchema = z.object({
  name: z.string().min(1, 'Plan name is required'),
  comments: z.string().default(''),
  functional_mode: z.boolean().default(false),
  serialize_threadgroups: z.boolean().default(false),
  teardown_on_shutdown: z.boolean().default(true),
  userDefinedVariables: z.array(userDefinedVariableSchema).default([]),
});

export type TestPlanFormValues = z.infer<typeof testPlanSchema>;

export const testPlanDefaults: TestPlanFormValues = {
  name: 'Test Plan',
  comments: '',
  functional_mode: false,
  serialize_threadgroups: false,
  teardown_on_shutdown: true,
  userDefinedVariables: [],
};
