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

export const jdbcSamplerSchema = z.object({
  /** JDBC data source name. */
  dataSource: z.string().min(1, 'Data source is required'),

  /** Type of query to execute. */
  queryType: z.enum(['select', 'update', 'callable'], {
    errorMap: () => ({ message: 'Must be select, update, or callable' }),
  }),

  /** SQL query string. */
  query: z.string(),

  /** Comma-separated parameter values for prepared statements. */
  parameterValues: z.string(),

  /** Variable name to store the result set. */
  resultVariable: z.string(),

  /** Query timeout in seconds. 0 means no timeout. */
  queryTimeout: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),

  /** Maximum number of rows to return. Must be between 1 and 100,000. */
  maxRows: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(1, 'Must be between 1 and 100000')
    .max(100_000, 'Must be between 1 and 100000'),
});

export type JdbcSamplerFormValues = z.infer<typeof jdbcSamplerSchema>;

export const jdbcSamplerDefaults: JdbcSamplerFormValues = {
  dataSource: '',
  queryType: 'select',
  query: '',
  parameterValues: '',
  resultVariable: '',
  queryTimeout: 0,
  maxRows: 1000,
};
