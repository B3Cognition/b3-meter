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
// JSR223 PreProcessor
// ---------------------------------------------------------------------------

export const jsr223PreProcessorSchema = z.object({
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

export type JSR223PreProcessorFormValues = z.infer<typeof jsr223PreProcessorSchema>;

export const jsr223PreProcessorDefaults: JSR223PreProcessorFormValues = {
  scriptLanguage: 'groovy',
  parameters: '',
  cacheKey: 'true',
  script: '',
  filename: '',
};

// ---------------------------------------------------------------------------
// BeanShell PreProcessor
// ---------------------------------------------------------------------------

export const beanShellPreProcessorSchema = z.object({
  /** Path to an external script file. */
  filename: z.string().default(''),

  /** Space-separated parameters. */
  parameters: z.string().default(''),

  /** Whether to reset the BeanShell interpreter between calls. */
  resetInterpreter: z.boolean().default(false),

  /** Inline BeanShell script. */
  script: z.string().default(''),
});

export type BeanShellPreProcessorFormValues = z.infer<typeof beanShellPreProcessorSchema>;

export const beanShellPreProcessorDefaults: BeanShellPreProcessorFormValues = {
  filename: '',
  parameters: '',
  resetInterpreter: false,
  script: '',
};

// ---------------------------------------------------------------------------
// User Parameters
// ---------------------------------------------------------------------------

export const userParametersSchema = z.object({
  /** Comma-separated list of parameter names. */
  names: z.string().default(''),

  /** Per-thread value sets (one row per thread, comma-separated values). */
  threadValues: z.string().default(''),

  /** Whether to update parameters on each iteration. */
  perIteration: z.boolean().default(false),
});

export type UserParametersFormValues = z.infer<typeof userParametersSchema>;

export const userParametersDefaults: UserParametersFormValues = {
  names: '',
  threadValues: '',
  perIteration: false,
};

// ---------------------------------------------------------------------------
// RegEx User Parameters
// ---------------------------------------------------------------------------

export const regExUserParametersSchema = z.object({
  /** Regular expression reference name (prefix for extracted variables). */
  regexRefName: z.string().default(''),

  /** Group number for parameter names. */
  paramNamesGroupNr: z.string().default('1'),

  /** Group number for parameter values. */
  paramValuesGroupNr: z.string().default('1'),
});

export type RegExUserParametersFormValues = z.infer<typeof regExUserParametersSchema>;

export const regExUserParametersDefaults: RegExUserParametersFormValues = {
  regexRefName: '',
  paramNamesGroupNr: '1',
  paramValuesGroupNr: '1',
};

// ---------------------------------------------------------------------------
// HTML Link Parser
// ---------------------------------------------------------------------------

export const htmlLinkParserSchema = z.object({});

export type HtmlLinkParserFormValues = z.infer<typeof htmlLinkParserSchema>;

export const htmlLinkParserDefaults: HtmlLinkParserFormValues = {};

// ---------------------------------------------------------------------------
// HTTP URL Re-writing Modifier
// ---------------------------------------------------------------------------

export const urlRewritingModifierSchema = z.object({
  /** Session parameter name to search for (e.g. jsessionid, PHPSESSID). */
  argument: z.string().default(''),

  /** Append as path extension (;name=value) instead of query param. */
  pathExtension: z.boolean().default(false),

  /** Omit the equals sign when using path extension. */
  pathExtensionNoEquals: z.boolean().default(false),

  /** No ? separator when adding as query param. */
  pathExtensionNoQuestionmark: z.boolean().default(false),
});

export type UrlRewritingModifierFormValues = z.infer<typeof urlRewritingModifierSchema>;

export const urlRewritingModifierDefaults: UrlRewritingModifierFormValues = {
  argument: '',
  pathExtension: false,
  pathExtensionNoEquals: false,
  pathExtensionNoQuestionmark: false,
};
