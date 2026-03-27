/**
 * Zod validation schemas for Timer elements.
 */

import { z } from 'zod';

// ---------------------------------------------------------------------------
// Constant Timer
// ---------------------------------------------------------------------------

export const constantTimerSchema = z.object({
  /** Thread Delay in milliseconds. */
  delay: z.number().min(0, 'Delay must be non-negative').default(300),
});

export type ConstantTimerFormValues = z.infer<typeof constantTimerSchema>;

export const constantTimerDefaults: ConstantTimerFormValues = {
  delay: 300,
};

// ---------------------------------------------------------------------------
// Gaussian Random Timer
// ---------------------------------------------------------------------------

export const gaussianTimerSchema = z.object({
  /** Deviation in milliseconds. */
  deviation: z.number().min(0, 'Deviation must be non-negative').default(100),
  /** Constant Delay Offset in milliseconds. */
  delay: z.number().min(0, 'Delay must be non-negative').default(300),
});

export type GaussianTimerFormValues = z.infer<typeof gaussianTimerSchema>;

export const gaussianTimerDefaults: GaussianTimerFormValues = {
  deviation: 100,
  delay: 300,
};

// ---------------------------------------------------------------------------
// Uniform Random Timer
// ---------------------------------------------------------------------------

export const uniformTimerSchema = z.object({
  /** Random Delay Maximum in milliseconds. */
  maxDelay: z.number().min(0, 'Max delay must be non-negative').default(1000),
  /** Constant Delay Offset in milliseconds. */
  delay: z.number().min(0, 'Delay must be non-negative').default(0),
});

export type UniformTimerFormValues = z.infer<typeof uniformTimerSchema>;

export const uniformTimerDefaults: UniformTimerFormValues = {
  maxDelay: 1000,
  delay: 0,
};

// ---------------------------------------------------------------------------
// Constant Throughput Timer
// ---------------------------------------------------------------------------

export const constantThroughputTimerSchema = z.object({
  /** Target throughput in samples per minute. */
  throughput: z.number().min(0, 'Throughput must be non-negative').default(60),
  /** Calculation mode: 0 = this thread only, 1 = all active threads. */
  calcMode: z.number().min(0).max(1).default(0),
});

export type ConstantThroughputTimerFormValues = z.infer<typeof constantThroughputTimerSchema>;

export const constantThroughputTimerDefaults: ConstantThroughputTimerFormValues = {
  throughput: 60,
  calcMode: 0,
};

// ---------------------------------------------------------------------------
// Synchronizing Timer
// ---------------------------------------------------------------------------

export const synchronizingTimerSchema = z.object({
  /** Number of threads to synchronize (0 = all threads in group). */
  groupSize: z.number().min(0, 'Group size must be non-negative').default(0),
  /** Timeout in milliseconds (0 = wait forever). */
  timeoutInMs: z.number().min(0, 'Timeout must be non-negative').default(0),
});

export type SynchronizingTimerFormValues = z.infer<typeof synchronizingTimerSchema>;

export const synchronizingTimerDefaults: SynchronizingTimerFormValues = {
  groupSize: 0,
  timeoutInMs: 0,
};

// ---------------------------------------------------------------------------
// Poisson Random Timer
// ---------------------------------------------------------------------------

export const poissonRandomTimerSchema = z.object({
  /** Lambda (average delay in milliseconds). */
  lambda: z.number().min(0, 'Lambda must be non-negative').default(300),
  /** Constant delay offset in milliseconds. */
  constantDelay: z.number().min(0, 'Constant delay must be non-negative').default(100),
});

export type PoissonRandomTimerFormValues = z.infer<typeof poissonRandomTimerSchema>;

export const poissonRandomTimerDefaults: PoissonRandomTimerFormValues = {
  lambda: 300,
  constantDelay: 100,
};

// ---------------------------------------------------------------------------
// BeanShell Timer
// ---------------------------------------------------------------------------

export const beanShellTimerSchema = z.object({
  /** Inline BeanShell script that returns delay in ms. */
  script: z.string().default(''),
  /** External script file path. */
  filename: z.string().default(''),
  /** Parameters passed to the script. */
  parameters: z.string().default(''),
});

export type BeanShellTimerFormValues = z.infer<typeof beanShellTimerSchema>;

export const beanShellTimerDefaults: BeanShellTimerFormValues = {
  script: '',
  filename: '',
  parameters: '',
};

// ---------------------------------------------------------------------------
// JSR223 Timer
// ---------------------------------------------------------------------------

export const jsr223TimerSchema = z.object({
  /** Script language (e.g. groovy, javascript). */
  scriptLanguage: z.string().default('groovy'),
  /** Inline script that returns delay in ms. */
  script: z.string().default(''),
  /** External script file path. */
  scriptFile: z.string().default(''),
  /** Parameters passed to the script. */
  parameters: z.string().default(''),
});

export type JSR223TimerFormValues = z.infer<typeof jsr223TimerSchema>;

export const jsr223TimerDefaults: JSR223TimerFormValues = {
  scriptLanguage: 'groovy',
  script: '',
  scriptFile: '',
  parameters: '',
};
