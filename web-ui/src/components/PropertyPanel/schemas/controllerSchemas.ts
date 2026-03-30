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
// Loop Controller
// ---------------------------------------------------------------------------

export const loopControllerSchema = z.object({
  /** Number of times to execute the loop body. */
  loops: z.number().default(1),

  /** When true, the loop runs indefinitely (loops value is ignored). */
  infinite: z.boolean().default(false),
});

export type LoopControllerFormValues = z.infer<typeof loopControllerSchema>;

export const loopControllerDefaults: LoopControllerFormValues = {
  loops: 1,
  infinite: false,
};

// ---------------------------------------------------------------------------
// If Controller
// ---------------------------------------------------------------------------

export const ifControllerSchema = z.object({
  /** Condition expression (JavaScript or variable expression). */
  condition: z.string().default(''),

  /** Interpret condition as a __jexl3 / variable expression rather than JavaScript. */
  interpretAsVariableExpression: z.boolean().default(true),

  /** Re-evaluate the condition for every child sampler (not just the first). */
  evaluateForAllChildren: z.boolean().default(false),
});

export type IfControllerFormValues = z.infer<typeof ifControllerSchema>;

export const ifControllerDefaults: IfControllerFormValues = {
  condition: '',
  interpretAsVariableExpression: true,
  evaluateForAllChildren: false,
};

// ---------------------------------------------------------------------------
// While Controller
// ---------------------------------------------------------------------------

export const whileControllerSchema = z.object({
  /** Condition expression — loop continues while this evaluates to true. */
  condition: z.string().default(''),
});

export type WhileControllerFormValues = z.infer<typeof whileControllerSchema>;

export const whileControllerDefaults: WhileControllerFormValues = {
  condition: '',
};

// ---------------------------------------------------------------------------
// Transaction Controller
// ---------------------------------------------------------------------------

export const transactionControllerSchema = z.object({
  /** Generate a parent sample that wraps all child samples. */
  generateParentSample: z.boolean().default(false),

  /** Include timer duration in the generated sample time. */
  includeTimers: z.boolean().default(false),
});

export type TransactionControllerFormValues = z.infer<typeof transactionControllerSchema>;

export const transactionControllerDefaults: TransactionControllerFormValues = {
  generateParentSample: false,
  includeTimers: false,
};

// ---------------------------------------------------------------------------
// Simple Controller
// ---------------------------------------------------------------------------

/** Simple Controller has no additional fields beyond Name and Comments. */
export const simpleControllerSchema = z.object({});

export type SimpleControllerFormValues = z.infer<typeof simpleControllerSchema>;

export const simpleControllerDefaults: SimpleControllerFormValues = {};
