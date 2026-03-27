/**
 * Zod validation schemas for Post-Processor elements.
 */

import { z } from 'zod';

// ---------------------------------------------------------------------------
// Regular Expression Extractor
// ---------------------------------------------------------------------------

export const regexExtractorSchema = z.object({
  /** Name of the variable to store the extracted value in. */
  refName: z.string().default(''),

  /** Regular expression with capturing groups. */
  regex: z.string().default(''),

  /** Template string using $N$ references (e.g. "$1$"). */
  template: z.string().default('$1$'),

  /** Which match to use. 0 = random, -1 = all, N = Nth match. */
  matchNumber: z.number().default(1),

  /** Default value when no match is found. */
  defaultValue: z.string().default(''),

  /** Which part of the response to apply the regex against. */
  testField: z
    .enum(['body', 'headers', 'url', 'code', 'message', 'request_headers', 'request_data'])
    .default('body'),
});

export type RegexExtractorFormValues = z.infer<typeof regexExtractorSchema>;

export const regexExtractorDefaults: RegexExtractorFormValues = {
  refName: '',
  regex: '',
  template: '$1$',
  matchNumber: 1,
  defaultValue: '',
  testField: 'body',
};

// ---------------------------------------------------------------------------
// JSON Extractor (JSONPath)
// ---------------------------------------------------------------------------

export const jsonExtractorSchema = z.object({
  /** Semicolon-separated variable names to store extracted values. */
  refNames: z.string().default(''),

  /** Semicolon-separated JSONPath expressions. */
  jsonPathExprs: z.string().default(''),

  /** Which match to use. 0 = random, -1 = all, N = Nth match. */
  matchNumber: z.number().default(1),

  /** Semicolon-separated default values when no match is found. */
  defaultValues: z.string().default(''),

  /** Compute concatenation of all matches and store as _ALL variable. */
  computeConcatenation: z.boolean().default(false),
});

export type JsonExtractorFormValues = z.infer<typeof jsonExtractorSchema>;

export const jsonExtractorDefaults: JsonExtractorFormValues = {
  refNames: '',
  jsonPathExprs: '',
  matchNumber: 1,
  defaultValues: '',
  computeConcatenation: false,
};

// ---------------------------------------------------------------------------
// XPath Extractor
// ---------------------------------------------------------------------------

export const xpathExtractorSchema = z.object({
  /** Variable name for extracted value. */
  refName: z.string().default(''),

  /** XPath expression. */
  xpathQuery: z.string().default(''),

  /** Default value if not found. */
  defaultValue: z.string().default(''),

  /** Match number (0=random, -1=all). */
  matchNumber: z.string().default('1'),

  /** Validate XML. */
  validate: z.boolean().default(false),

  /** Ignore whitespace. */
  whitespace: z.boolean().default(false),

  /** Tolerant parsing (Tidy). */
  tolerant: z.boolean().default(false),

  /** Namespace-aware. */
  namespace: z.boolean().default(false),
});

export type XpathExtractorFormValues = z.infer<typeof xpathExtractorSchema>;

export const xpathExtractorDefaults: XpathExtractorFormValues = {
  refName: '',
  xpathQuery: '',
  defaultValue: '',
  matchNumber: '1',
  validate: false,
  whitespace: false,
  tolerant: false,
  namespace: false,
};

// ---------------------------------------------------------------------------
// CSS/JQuery Extractor (HtmlExtractor)
// ---------------------------------------------------------------------------

export const cssExtractorSchema = z.object({
  /** Variable name for extracted value. */
  refName: z.string().default(''),

  /** CSS selector expression. */
  expr: z.string().default(''),

  /** Attribute to extract (empty = text content). */
  attribute: z.string().default(''),

  /** Default value if not found. */
  defaultValue: z.string().default(''),

  /** Match number (0=random, -1=all). */
  matchNumber: z.string().default('1'),

  /** Implementation: JSOUP or JODD. */
  extractorImpl: z.string().default('JSOUP'),
});

export type CssExtractorFormValues = z.infer<typeof cssExtractorSchema>;

export const cssExtractorDefaults: CssExtractorFormValues = {
  refName: '',
  expr: '',
  attribute: '',
  defaultValue: '',
  matchNumber: '1',
  extractorImpl: 'JSOUP',
};

// ---------------------------------------------------------------------------
// Boundary Extractor
// ---------------------------------------------------------------------------

export const boundaryExtractorSchema = z.object({
  /** Variable name. */
  refName: z.string().default(''),

  /** Left boundary string. */
  lBoundary: z.string().default(''),

  /** Right boundary string. */
  rBoundary: z.string().default(''),

  /** Default value. */
  defaultValue: z.string().default(''),

  /** Match number. */
  matchNumber: z.string().default('1'),

  /** Search in headers (vs body). */
  useHeaders: z.string().default('false'),
});

export type BoundaryExtractorFormValues = z.infer<typeof boundaryExtractorSchema>;

export const boundaryExtractorDefaults: BoundaryExtractorFormValues = {
  refName: '',
  lBoundary: '',
  rBoundary: '',
  defaultValue: '',
  matchNumber: '1',
  useHeaders: 'false',
};

// ---------------------------------------------------------------------------
// Debug PostProcessor
// ---------------------------------------------------------------------------

export const debugPostProcessorSchema = z.object({
  /** Include JMeter properties. */
  displayJMeterProperties: z.boolean().default(false),

  /** Include JMeter variables. */
  displayJMeterVariables: z.boolean().default(true),

  /** Include system properties. */
  displaySystemProperties: z.boolean().default(false),
});

export type DebugPostProcessorFormValues = z.infer<typeof debugPostProcessorSchema>;

export const debugPostProcessorDefaults: DebugPostProcessorFormValues = {
  displayJMeterProperties: false,
  displayJMeterVariables: true,
  displaySystemProperties: false,
};

// ---------------------------------------------------------------------------
// JSR223 PostProcessor
// ---------------------------------------------------------------------------

export const jsr223PostProcessorSchema = z.object({
  /** Scripting language (e.g. groovy, javascript, beanshell). */
  scriptLanguage: z.string().default('groovy'),

  /** Space-separated parameters passed to the script. */
  parameters: z.string().default(''),

  /** Whether to cache the compiled script. */
  cacheKey: z.string().default('true'),

  /** Inline script text. */
  script: z.string().default(''),

  /** Path to an external script file (alternative to inline). */
  filename: z.string().default(''),
});

export type JSR223PostProcessorFormValues = z.infer<typeof jsr223PostProcessorSchema>;

export const jsr223PostProcessorDefaults: JSR223PostProcessorFormValues = {
  scriptLanguage: 'groovy',
  parameters: '',
  cacheKey: 'true',
  script: '',
  filename: '',
};

// ---------------------------------------------------------------------------
// BeanShell PostProcessor
// ---------------------------------------------------------------------------

export const beanShellPostProcessorSchema = z.object({
  /** Path to an external script file. */
  filename: z.string().default(''),

  /** Space-separated parameters. */
  parameters: z.string().default(''),

  /** Inline BeanShell script. */
  script: z.string().default(''),
});

export type BeanShellPostProcessorFormValues = z.infer<typeof beanShellPostProcessorSchema>;

export const beanShellPostProcessorDefaults: BeanShellPostProcessorFormValues = {
  filename: '',
  parameters: '',
  script: '',
};
