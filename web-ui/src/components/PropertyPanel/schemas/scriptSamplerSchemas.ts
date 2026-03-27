/**
 * Zod validation schemas for scriptable sampler elements.
 *
 * Covers: JSR223 Sampler, BeanShell Sampler, OS Process Sampler, and Debug Sampler.
 */

import { z } from 'zod';

/* --- JSR223 Sampler --- */

export const jsr223SamplerSchema = z.object({
  /** Inline script text. */
  script: z.string().default(
    '// Write your script here\nSampleResult.setResponseData("Hello from script", "UTF-8");\nSampleResult.setResponseCode("200");',
  ),

  /** Script language engine name. */
  scriptLanguage: z.enum(['groovy', 'javascript', 'python']).default('groovy'),

  /** Path to external script file (alternative to inline script). */
  scriptFile: z.string().default(''),

  /** Space-separated parameters passed as Parameters and args[]. */
  parameters: z.string().default(''),
});

export type JSR223SamplerFormValues = z.infer<typeof jsr223SamplerSchema>;

export const jsr223SamplerDefaults: JSR223SamplerFormValues = {
  script:
    '// Write your script here\nSampleResult.setResponseData("Hello from script", "UTF-8");\nSampleResult.setResponseCode("200");',
  scriptLanguage: 'groovy',
  scriptFile: '',
  parameters: '',
};

/* --- BeanShell Sampler --- */

export const beanShellSamplerSchema = z.object({
  /** Inline BeanShell/Java-like script. */
  script: z.string().default(''),

  /** External script file path. */
  filename: z.string().default(''),

  /** Parameters string. */
  parameters: z.string().default(''),

  /** Reset interpreter between calls. */
  resetInterpreter: z.boolean().default(false),
});

export type BeanShellSamplerFormValues = z.infer<typeof beanShellSamplerSchema>;

export const beanShellSamplerDefaults: BeanShellSamplerFormValues = {
  script: '',
  filename: '',
  parameters: '',
  resetInterpreter: false,
};

/* --- OS Process Sampler --- */

export const osProcessSamplerSchema = z.object({
  /** The command to execute (e.g., "curl", "/bin/bash"). */
  command: z.string().min(1, 'Command is required'),

  /** Newline-separated arguments. */
  arguments: z.string().default(''),

  /** Working directory (default "."). */
  workingDirectory: z.string().default('.'),

  /** Newline-separated KEY=VALUE environment variable pairs. */
  environment: z.string().default(''),

  /** Timeout in milliseconds. */
  timeout: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be non-negative')
    .default(60000),

  /** Expected return code. */
  expectedReturnCode: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .default(0),
});

export type OSProcessSamplerFormValues = z.infer<typeof osProcessSamplerSchema>;

export const osProcessSamplerDefaults: OSProcessSamplerFormValues = {
  command: '',
  arguments: '',
  workingDirectory: '.',
  environment: '',
  timeout: 60000,
  expectedReturnCode: 0,
};

/* --- Debug Sampler --- */

export const debugSamplerSchema = z.object({
  /** Display JMeter variables in output. */
  displayJMeterVariables: z.boolean().default(true),

  /** Display system properties in output. */
  displaySystemProperties: z.boolean().default(false),
});

export type DebugSamplerFormValues = z.infer<typeof debugSamplerSchema>;

export const debugSamplerDefaults: DebugSamplerFormValues = {
  displayJMeterVariables: true,
  displaySystemProperties: false,
};
