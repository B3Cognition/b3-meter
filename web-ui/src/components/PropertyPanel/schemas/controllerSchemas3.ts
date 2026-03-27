/**
 * Zod validation schemas for additional Controller elements (Phase 3).
 *
 * Extends the controllers defined in controllerSchemas.ts and controllerSchemas2.ts
 * with Switch, Random, Interleave, and Random Order controllers.
 */

import { z } from 'zod';

// ---------------------------------------------------------------------------
// Switch Controller
// ---------------------------------------------------------------------------

export const switchControllerSchema = z.object({
  /** Switch value: numeric index (0-based) or child name to match. */
  value: z.string().default('0'),
});

export type SwitchControllerFormValues = z.infer<typeof switchControllerSchema>;

export const switchControllerDefaults: SwitchControllerFormValues = {
  value: '0',
};

// ---------------------------------------------------------------------------
// Random Controller
// ---------------------------------------------------------------------------

/** Random Controller has no additional fields beyond Name and Comments. */
export const randomControllerSchema = z.object({});

export type RandomControllerFormValues = z.infer<typeof randomControllerSchema>;

export const randomControllerDefaults: RandomControllerFormValues = {};

// ---------------------------------------------------------------------------
// Interleave Controller
// ---------------------------------------------------------------------------

export const interleaveControllerSchema = z.object({
  /** Interleave style: 0 = simple interleave. */
  style: z.number().int().default(0),

  /** Share counter across all VUs (note: typo is intentional, matches JMeter source). */
  accrossThreads: z.boolean().default(false),
});

export type InterleaveControllerFormValues = z.infer<typeof interleaveControllerSchema>;

export const interleaveControllerDefaults: InterleaveControllerFormValues = {
  style: 0,
  accrossThreads: false,
};

// ---------------------------------------------------------------------------
// Random Order Controller
// ---------------------------------------------------------------------------

/** Random Order Controller has no additional fields beyond Name and Comments. */
export const randomOrderControllerSchema = z.object({});

export type RandomOrderControllerFormValues = z.infer<typeof randomOrderControllerSchema>;

export const randomOrderControllerDefaults: RandomOrderControllerFormValues = {};

// ---------------------------------------------------------------------------
// Module Controller
// ---------------------------------------------------------------------------

export const moduleControllerSchema = z.object({
  /** Path to the target controller in the test plan tree (comma-separated segments). */
  nodePath: z.string().default(''),
});

export type ModuleControllerFormValues = z.infer<typeof moduleControllerSchema>;

export const moduleControllerDefaults: ModuleControllerFormValues = {
  nodePath: '',
};

// ---------------------------------------------------------------------------
// Include Controller
// ---------------------------------------------------------------------------

export const includeControllerSchema = z.object({
  /** Path to the external JMX file to include. */
  includePath: z.string().default(''),
});

export type IncludeControllerFormValues = z.infer<typeof includeControllerSchema>;

export const includeControllerDefaults: IncludeControllerFormValues = {
  includePath: '',
};
