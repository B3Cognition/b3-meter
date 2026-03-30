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
import type { Column } from '../EditableTable/EditableTable.js';

// ---------------------------------------------------------------------------
// Human-readable type names
// ---------------------------------------------------------------------------

export const TYPE_DISPLAY_NAMES: Record<string, string> = {
  TestPlan: 'Test Plan',
  ThreadGroup: 'Thread Group',
  HTTPSampler: 'HTTP Request',
  HTTPSamplerProxy: 'HTTP Request',
  TCPSampler: 'TCP Sampler',
  JDBCSampler: 'JDBC Request',
  FTPSampler: 'FTP Request',
  SMTPSampler: 'SMTP Sampler',
  BoltSampler: 'Bolt Request',
  LDAPSampler: 'LDAP Request',
  JMSSampler: 'JMS Sampler',
  ResponseAssertion: 'Response Assertion',
  DurationAssertion: 'Duration Assertion',
  SizeAssertion: 'Size Assertion',
  ConstantTimer: 'Constant Timer',
  GaussianRandomTimer: 'Gaussian Random Timer',
  UniformRandomTimer: 'Uniform Random Timer',
  CSVDataSet: 'CSV Data Set Config',
  HeaderManager: 'HTTP Header Manager',
  HTTPHeaderManager: 'HTTP Header Manager',
  CookieManager: 'HTTP Cookie Manager',
  HTTPCookieManager: 'HTTP Cookie Manager',
  UserDefinedVariables: 'User Defined Variables',
  HTTPRequestDefaults: 'HTTP Request Defaults',
  LoopController: 'Loop Controller',
  IfController: 'If Controller',
  WhileController: 'While Controller',
  TransactionController: 'Transaction Controller',
  SimpleController: 'Simple Controller',
  RegexExtractor: 'Regular Expression Extractor',
  JSONPathExtractor: 'JSON Path Extractor',
  JSONPostProcessor: 'JSON Extractor',
  ResultCollector: 'View Results Tree',
  ViewResultsTree: 'View Results Tree',
  Summariser: 'Generate Summary Results',
  SummaryReport: 'Summary Report',
  AggregateReport: 'Aggregate Report',
};

export function getDisplayName(type: string): string {
  return TYPE_DISPLAY_NAMES[type] ?? type.replace(/([A-Z])/g, ' $1').trim();
}

// ---------------------------------------------------------------------------
// HTTP Sampler fieldset grouping
// ---------------------------------------------------------------------------

/** Defines field groups for HTTPSampler / HTTPSamplerProxy fieldsets. */
export const HTTP_FIELDSETS: { legend: string; fields: string[] }[] = [
  { legend: 'Web Server', fields: ['protocol', 'domain', 'port'] },
  { legend: 'HTTP Request', fields: ['method', 'path', 'contentEncoding'] },
];

/** Returns the set of fields belonging to fieldsets for a given type. */
export function getFieldsets(nodeType: string): { legend: string; fields: string[] }[] | null {
  if (nodeType === 'HTTPSampler' || nodeType === 'HTTPSamplerProxy') {
    return HTTP_FIELDSETS;
  }
  return null;
}

// ---------------------------------------------------------------------------
// HTTP Sampler sub-tab table column definitions
// ---------------------------------------------------------------------------

export const HTTP_PARAM_COLUMNS: Column[] = [
  { key: 'name', label: 'Name', type: 'text', width: '30%' },
  { key: 'value', label: 'Value', type: 'text', width: '30%' },
  { key: 'urlEncode', label: 'URL Encode?', type: 'checkbox', width: '15%' },
  { key: 'contentType', label: 'Content-Type', type: 'text', width: '25%' },
];

export const HTTP_FILES_COLUMNS: Column[] = [
  { key: 'filePath', label: 'File Path', type: 'text', width: '40%' },
  { key: 'paramName', label: 'Parameter Name', type: 'text', width: '30%' },
  { key: 'mimeType', label: 'MIME Type', type: 'text', width: '30%' },
];

// ---------------------------------------------------------------------------
// ON_SAMPLE_ERROR labels for radio buttons
// ---------------------------------------------------------------------------

export const ON_SAMPLE_ERROR_OPTIONS: { value: string; label: string }[] = [
  { value: 'continue', label: 'Continue' },
  { value: 'startnextloop', label: 'Start Next Thread Loop' },
  { value: 'stopthread', label: 'Stop Thread' },
  { value: 'stoptest', label: 'Stop Test' },
  { value: 'stoptestnow', label: 'Stop Test Now' },
];

// ---------------------------------------------------------------------------
// User Defined Variables column definitions
// ---------------------------------------------------------------------------

export const UDV_COLUMNS: Column[] = [
  { key: 'name', label: 'Name', type: 'text', width: '30%' },
  { key: 'value', label: 'Value', type: 'text', width: '35%' },
  { key: 'description', label: 'Description', type: 'text', width: '35%' },
];
