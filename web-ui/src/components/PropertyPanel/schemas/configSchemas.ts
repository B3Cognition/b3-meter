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
// CSV Data Set Config
// ---------------------------------------------------------------------------

export const csvDataSetSchema = z.object({
  /** Path to the CSV file. */
  filename: z.string().default(''),

  /** File encoding (e.g. UTF-8). */
  fileEncoding: z.string().default('UTF-8'),

  /** Comma-delimited list of variable names. */
  variableNames: z.string().default(''),

  /** Whether to ignore the first line (header row). */
  ignoreFirstLine: z.boolean().default(false),

  /** Column delimiter character. */
  delimiter: z.string().default(','),

  /** Allow quoted data fields. */
  allowQuotedData: z.boolean().default(false),

  /** Recycle back to the first line when EOF is reached. */
  recycleOnEof: z.boolean().default(true),

  /** Stop the thread when EOF is reached (only when recycleOnEof is false). */
  stopThreadOnEof: z.boolean().default(false),

  /** Sharing mode across threads. */
  sharingMode: z.enum(['all', 'group', 'thread']).default('all'),
});

export type CsvDataSetFormValues = z.infer<typeof csvDataSetSchema>;

export const csvDataSetDefaults: CsvDataSetFormValues = {
  filename: '',
  fileEncoding: 'UTF-8',
  variableNames: '',
  ignoreFirstLine: false,
  delimiter: ',',
  allowQuotedData: false,
  recycleOnEof: true,
  stopThreadOnEof: false,
  sharingMode: 'all',
};

// ---------------------------------------------------------------------------
// HTTP Header Manager
// ---------------------------------------------------------------------------

/** Headers are stored as an array in properties and rendered via EditableTable. */
export const headerManagerSchema = z.object({});

export type HeaderManagerFormValues = z.infer<typeof headerManagerSchema>;

export const headerManagerDefaults: HeaderManagerFormValues = {};

// ---------------------------------------------------------------------------
// HTTP Cookie Manager
// ---------------------------------------------------------------------------

export const cookieManagerSchema = z.object({
  /** Clear cookies at the start of each iteration. */
  clearEachIteration: z.boolean().default(false),

  /** Cookie policy implementation. */
  cookiePolicy: z
    .enum(['standard', 'standard-strict', 'netscape', 'ignorecookies', 'default'])
    .default('standard'),
});

export type CookieManagerFormValues = z.infer<typeof cookieManagerSchema>;

export const cookieManagerDefaults: CookieManagerFormValues = {
  clearEachIteration: false,
  cookiePolicy: 'standard',
};

// ---------------------------------------------------------------------------
// User Defined Variables
// ---------------------------------------------------------------------------

/** Variables are stored as key-value pairs in properties, rendered via EditableTable. */
export const userDefinedVariablesSchema = z.object({});

export type UserDefinedVariablesFormValues = z.infer<typeof userDefinedVariablesSchema>;

export const userDefinedVariablesDefaults: UserDefinedVariablesFormValues = {};

// ---------------------------------------------------------------------------
// HTTP Cache Manager
// ---------------------------------------------------------------------------

export const cacheManagerSchema = z.object({
  /** Clear cache at start of each iteration. */
  clearEachIteration: z.boolean().default(false),

  /** Honour Expires/Cache-Control max-age headers. */
  useExpires: z.boolean().default(true),

  /** Maximum number of cached entries. */
  maxSize: z.number().int().min(1).default(5000),

  /** Each VU maintains its own cache. */
  controlledByThread: z.boolean().default(false),
});

export type CacheManagerFormValues = z.infer<typeof cacheManagerSchema>;

export const cacheManagerDefaults: CacheManagerFormValues = {
  clearEachIteration: false,
  useExpires: true,
  maxSize: 5000,
  controlledByThread: false,
};

// ---------------------------------------------------------------------------
// HTTP Authorization Manager
// ---------------------------------------------------------------------------

export const authManagerSchema = z.object({
  /** Clear credentials each iteration. */
  clearEachIteration: z.boolean().default(false),

  /** Scope to thread group. */
  controlledByThreadGroup: z.boolean().default(false),
});

export type AuthManagerFormValues = z.infer<typeof authManagerSchema>;

export const authManagerDefaults: AuthManagerFormValues = {
  clearEachIteration: false,
  controlledByThreadGroup: false,
};

// ---------------------------------------------------------------------------
// Counter Config
// ---------------------------------------------------------------------------

export const counterConfigSchema = z.object({
  /** Starting value. */
  start: z.string().default('1'),

  /** Maximum value (empty = Long.MAX_VALUE). */
  end: z.string().default(''),

  /** Increment per iteration. */
  increment: z.string().default('1'),

  /** Variable name to store counter. */
  variableName: z.string().default('counter'),

  /** DecimalFormat pattern. */
  format: z.string().default(''),

  /** Per-thread counter (vs shared). */
  perUser: z.boolean().default(false),

  /** Reset counter when ThreadGroup loop restarts. */
  resetOnThreadGroupIteration: z.boolean().default(false),
});

export type CounterConfigFormValues = z.infer<typeof counterConfigSchema>;

export const counterConfigDefaults: CounterConfigFormValues = {
  start: '1',
  end: '',
  increment: '1',
  variableName: 'counter',
  format: '',
  perUser: false,
  resetOnThreadGroupIteration: false,
};

// ---------------------------------------------------------------------------
// Random Variable Config
// ---------------------------------------------------------------------------

export const randomVariableConfigSchema = z.object({
  /** Variable name to store result. */
  variableName: z.string().default(''),

  /** Minimum value (inclusive). */
  minimumValue: z.string().default('1'),

  /** Maximum value (inclusive). */
  maximumValue: z.string().default('100'),

  /** DecimalFormat pattern (empty = plain number). */
  outputFormat: z.string().default(''),

  /** Each VU gets independent random sequence. */
  perThread: z.boolean().default(true),

  /** Seed for reproducible sequences (empty = random). */
  randomSeed: z.string().default(''),
});

export type RandomVariableConfigFormValues = z.infer<typeof randomVariableConfigSchema>;

export const randomVariableConfigDefaults: RandomVariableConfigFormValues = {
  variableName: '',
  minimumValue: '1',
  maximumValue: '100',
  outputFormat: '',
  perThread: true,
  randomSeed: '',
};

// ---------------------------------------------------------------------------
// JDBC Connection Configuration
// ---------------------------------------------------------------------------

export const jdbcConnectionConfigSchema = z.object({
  /** Variable name to reference this connection pool. */
  dataSource: z.string().default(''),

  /** JDBC connection URL. */
  dbUrl: z.string().default(''),

  /** JDBC driver class name. */
  driver: z.string().default(''),

  /** Database username. */
  username: z.string().default(''),

  /** Database password. */
  password: z.string().default(''),

  /** Validation query. */
  checkQuery: z.string().default('SELECT 1'),

  /** Auto-commit mode. */
  autocommit: z.boolean().default(true),

  /** Max connection age (ms). */
  connectionAge: z.string().default('5000'),

  /** Additional JDBC properties. */
  connectionProperties: z.string().default(''),

  /** Query to run on new connections. */
  initQuery: z.string().default(''),

  /** Keep connections alive. */
  keepAlive: z.boolean().default(true),

  /** Maximum active connections. */
  maxActive: z.string().default('10'),

  /** Maximum idle connections. */
  maxIdle: z.string().default('10'),

  /** Maximum wait for connection (ms). */
  maxWait: z.string().default('10000'),

  /** Pool maximum size. */
  poolMax: z.string().default('10'),

  /** Connection timeout (ms). */
  timeout: z.string().default('10000'),

  /** Transaction isolation level. */
  transactionIsolation: z.string().default('DEFAULT'),

  /** Pre-initialize pool at test start. */
  preinit: z.boolean().default(false),
});

export type JdbcConnectionConfigFormValues = z.infer<typeof jdbcConnectionConfigSchema>;

export const jdbcConnectionConfigDefaults: JdbcConnectionConfigFormValues = {
  dataSource: '',
  dbUrl: '',
  driver: '',
  username: '',
  password: '',
  checkQuery: 'SELECT 1',
  autocommit: true,
  connectionAge: '5000',
  connectionProperties: '',
  initQuery: '',
  keepAlive: true,
  maxActive: '10',
  maxIdle: '10',
  maxWait: '10000',
  poolMax: '10',
  timeout: '10000',
  transactionIsolation: 'DEFAULT',
  preinit: false,
};

// ---------------------------------------------------------------------------
// DNS Cache Manager
// ---------------------------------------------------------------------------

export const dnsCacheManagerSchema = z.object({
  /** Clear DNS cache at start of each iteration. */
  clearEachIteration: z.boolean().default(false),

  /** Use custom DNS resolver. */
  isCustomResolver: z.boolean().default(false),

  /** DNS server addresses (comma-separated). */
  servers: z.string().default(''),
});

export type DnsCacheManagerFormValues = z.infer<typeof dnsCacheManagerSchema>;

export const dnsCacheManagerDefaults: DnsCacheManagerFormValues = {
  clearEachIteration: false,
  isCustomResolver: false,
  servers: '',
};

// ---------------------------------------------------------------------------
// Keystore Configuration
// ---------------------------------------------------------------------------

export const keystoreConfigSchema = z.object({
  /** Preload the keystore at test start. */
  preload: z.boolean().default(true),

  /** Start index for client certificate selection. */
  startIndex: z.string().default('0'),

  /** End index for client certificate selection. */
  endIndex: z.string().default(''),

  /** Variable name containing the client certificate alias. */
  clientCertAliasVarName: z.string().default(''),
});

export type KeystoreConfigFormValues = z.infer<typeof keystoreConfigSchema>;

export const keystoreConfigDefaults: KeystoreConfigFormValues = {
  preload: true,
  startIndex: '0',
  endIndex: '',
  clientCertAliasVarName: '',
};

// ---------------------------------------------------------------------------
// Login Config Element
// ---------------------------------------------------------------------------

export const loginConfigSchema = z.object({
  /** Username for HTTP authentication. */
  username: z.string().default(''),

  /** Password for HTTP authentication. */
  password: z.string().default(''),
});

export type LoginConfigFormValues = z.infer<typeof loginConfigSchema>;

export const loginConfigDefaults: LoginConfigFormValues = {
  username: '',
  password: '',
};

// ---------------------------------------------------------------------------
// LDAP Request Defaults
// ---------------------------------------------------------------------------

export const ldapDefaultsSchema = z.object({
  /** LDAP server hostname. */
  servername: z.string().default(''),

  /** LDAP server port. */
  port: z.string().default('389'),

  /** Root distinguished name. */
  rootdn: z.string().default(''),

  /** Test type (e.g. add, delete, search). */
  test: z.string().default(''),

  /** Base entry DN for operations. */
  base_entry_dn: z.string().default(''),
});

export type LdapDefaultsFormValues = z.infer<typeof ldapDefaultsSchema>;

export const ldapDefaultsDefaults: LdapDefaultsFormValues = {
  servername: '',
  port: '389',
  rootdn: '',
  test: '',
  base_entry_dn: '',
};
