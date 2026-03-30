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
// Response Assertion
// ---------------------------------------------------------------------------

export const responseAssertionSchema = z.object({
  /** Which part of the response to test against. */
  testField: z
    .enum([
      'response_code',
      'response_message',
      'response_headers',
      'response_body',
      'request_headers',
      'url',
      'request_data',
    ])
    .default('response_code'),

  /** Pattern matching rule. */
  testType: z.enum(['contains', 'matches', 'equals', 'substring']).default('contains'),

  /** When true, the assertion succeeds if the pattern does NOT match. */
  negate: z.boolean().default(false),

  /** Newline-separated patterns to test against the selected field. */
  patterns: z.string().default('200'),

  /** Optional custom failure message shown when the assertion fails. */
  customFailureMessage: z.string().default(''),
});

export type ResponseAssertionFormValues = z.infer<typeof responseAssertionSchema>;

export const responseAssertionDefaults: ResponseAssertionFormValues = {
  testField: 'response_code',
  testType: 'contains',
  negate: false,
  patterns: '200',
  customFailureMessage: '',
};

// ---------------------------------------------------------------------------
// Duration Assertion
// ---------------------------------------------------------------------------

export const durationAssertionSchema = z.object({
  /** Maximum allowed response duration in milliseconds. */
  duration: z.number().min(0, 'Duration must be non-negative').default(1000),
});

export type DurationAssertionFormValues = z.infer<typeof durationAssertionSchema>;

export const durationAssertionDefaults: DurationAssertionFormValues = {
  duration: 1000,
};

// ---------------------------------------------------------------------------
// Size Assertion
// ---------------------------------------------------------------------------

export const sizeAssertionSchema = z.object({
  /** Which part of the response to measure. */
  sizeField: z.enum(['full', 'headers', 'body', 'code', 'message']).default('full'),

  /** Size threshold in bytes. */
  size: z.number().min(0, 'Size must be non-negative').default(0),

  /** Comparison operator: eq, ne, gt, lt, ge, le. */
  comparison: z.enum(['eq', 'ne', 'gt', 'lt', 'ge', 'le']).default('lt'),
});

export type SizeAssertionFormValues = z.infer<typeof sizeAssertionSchema>;

export const sizeAssertionDefaults: SizeAssertionFormValues = {
  sizeField: 'full',
  size: 0,
  comparison: 'lt',
};

// ---------------------------------------------------------------------------
// JSON Assertion (JSONPathAssertion)
// ---------------------------------------------------------------------------

export const jsonAssertionSchema = z.object({
  /** JSONPath expression to evaluate. */
  jsonPath: z.string().default(''),

  /** Expected value to match. */
  expectedValue: z.string().default(''),

  /** Validate the extracted value against expectedValue. */
  jsonValidation: z.boolean().default(true),

  /** Expect a null value. */
  expectNull: z.boolean().default(false),

  /** Invert the assertion result. */
  invert: z.boolean().default(false),

  /** Treat expected value as a regex pattern. */
  isRegex: z.boolean().default(false),
});

export type JsonAssertionFormValues = z.infer<typeof jsonAssertionSchema>;

export const jsonAssertionDefaults: JsonAssertionFormValues = {
  jsonPath: '',
  expectedValue: '',
  jsonValidation: true,
  expectNull: false,
  invert: false,
  isRegex: false,
};

// ---------------------------------------------------------------------------
// XPath Assertion
// ---------------------------------------------------------------------------

export const xpathAssertionSchema = z.object({
  /** XPath expression to evaluate. */
  xpath: z.string().default(''),

  /** Validate XML against DTD. */
  validate: z.boolean().default(false),

  /** Ignore whitespace. */
  whitespace: z.boolean().default(false),

  /** Use tolerant parsing for malformed HTML. */
  tolerant: z.boolean().default(false),

  /** Invert assertion result. */
  negate: z.boolean().default(false),

  /** Enable namespace processing. */
  namespace: z.boolean().default(false),

  /** Download external DTDs. */
  downloadDTDs: z.boolean().default(false),
});

export type XpathAssertionFormValues = z.infer<typeof xpathAssertionSchema>;

export const xpathAssertionDefaults: XpathAssertionFormValues = {
  xpath: '',
  validate: false,
  whitespace: false,
  tolerant: false,
  negate: false,
  namespace: false,
  downloadDTDs: false,
};

// ---------------------------------------------------------------------------
// XML Assertion
// ---------------------------------------------------------------------------

export const xmlAssertionSchema = z.object({});

export type XmlAssertionFormValues = z.infer<typeof xmlAssertionSchema>;

export const xmlAssertionDefaults: XmlAssertionFormValues = {};

// ---------------------------------------------------------------------------
// JSR223 Assertion
// ---------------------------------------------------------------------------

export const jsr223AssertionSchema = z.object({
  /** Scripting language. */
  scriptLanguage: z.string().default('groovy'),

  /** Parameters passed to the script. */
  parameters: z.string().default(''),

  /** Path to external script file. */
  filename: z.string().default(''),

  /** Cache compiled script. */
  cacheKey: z.string().default('true'),

  /** Inline script text. */
  script: z.string().default(''),
});

export type Jsr223AssertionFormValues = z.infer<typeof jsr223AssertionSchema>;

export const jsr223AssertionDefaults: Jsr223AssertionFormValues = {
  scriptLanguage: 'groovy',
  parameters: '',
  filename: '',
  cacheKey: 'true',
  script: '',
};

// ---------------------------------------------------------------------------
// HTML Assertion
// ---------------------------------------------------------------------------

export const htmlAssertionSchema = z.object({
  /** Maximum number of errors before assertion fails. */
  errorThreshold: z.number().min(0).default(0),

  /** Maximum number of warnings before assertion fails. */
  warningThreshold: z.number().min(0).default(0),

  /** Expected DOCTYPE (empty = any). */
  doctype: z.string().default(''),

  /** Response format: html, xhtml, xml. */
  format: z.enum(['html', 'xhtml', 'xml']).default('html'),
});

export type HtmlAssertionFormValues = z.infer<typeof htmlAssertionSchema>;

export const htmlAssertionDefaults: HtmlAssertionFormValues = {
  errorThreshold: 0,
  warningThreshold: 0,
  doctype: '',
  format: 'html',
};

// ---------------------------------------------------------------------------
// BeanShell Assertion
// ---------------------------------------------------------------------------

export const beanShellAssertionSchema = z.object({
  /** Inline BeanShell script. */
  script: z.string().default(''),

  /** Path to external script file. */
  filename: z.string().default(''),

  /** Parameters passed to the script. */
  parameters: z.string().default(''),

  /** Whether to reset the BeanShell interpreter between calls. */
  resetInterpreter: z.boolean().default(false),
});

export type BeanShellAssertionFormValues = z.infer<typeof beanShellAssertionSchema>;

export const beanShellAssertionDefaults: BeanShellAssertionFormValues = {
  script: '',
  filename: '',
  parameters: '',
  resetInterpreter: false,
};

// ---------------------------------------------------------------------------
// Compare Assertion
// ---------------------------------------------------------------------------

export const compareAssertionSchema = z.object({
  /** Whether to compare response content. */
  compareContent: z.boolean().default(true),

  /** Maximum allowed time difference in ms (0 = skip). */
  compareTime: z.number().min(0).default(0),

  /** Whether to compare response headers. */
  compareHeaders: z.boolean().default(false),

  /** Expected response content to compare against. */
  expectedContent: z.string().default(''),

  /** Expected response time in ms. */
  expectedTime: z.number().min(0).default(0),
});

export type CompareAssertionFormValues = z.infer<typeof compareAssertionSchema>;

export const compareAssertionDefaults: CompareAssertionFormValues = {
  compareContent: true,
  compareTime: 0,
  compareHeaders: false,
  expectedContent: '',
  expectedTime: 0,
};

// ---------------------------------------------------------------------------
// XPath2 Assertion
// ---------------------------------------------------------------------------

export const xpath2AssertionSchema = z.object({
  /** XPath 2.0 expression to evaluate. */
  xpath: z.string().default(''),

  /** Validate XML against DTD. */
  validate: z.boolean().default(false),

  /** Ignore whitespace. */
  whitespace: z.boolean().default(false),

  /** Use tolerant parsing for malformed HTML. */
  tolerant: z.boolean().default(false),

  /** Invert assertion result. */
  negate: z.boolean().default(false),

  /** Enable namespace processing. */
  namespace: z.boolean().default(false),

  /** Namespace aliases (prefix=uri, one per line). */
  namespaces: z.string().default(''),
});

export type Xpath2AssertionFormValues = z.infer<typeof xpath2AssertionSchema>;

export const xpath2AssertionDefaults: Xpath2AssertionFormValues = {
  xpath: '',
  validate: false,
  whitespace: false,
  tolerant: false,
  negate: false,
  namespace: false,
  namespaces: '',
};
