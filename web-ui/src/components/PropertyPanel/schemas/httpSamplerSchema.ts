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

export const httpSamplerSchema = z.object({
  /** Target hostname or IP address. */
  domain: z.string().min(1, 'Domain is required'),

  /** TCP port number. Must be between 1 and 65535. */
  port: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(1, 'Must be between 1 and 65535')
    .max(65535, 'Must be between 1 and 65535'),

  /** URL path, e.g. "/api/users". */
  path: z.string(),

  /** HTTP method. */
  method: z.enum(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'], {
    errorMap: () => ({ message: 'Must be a valid HTTP method' }),
  }),

  /** Request protocol. */
  protocol: z.enum(['http', 'https'], {
    errorMap: () => ({ message: 'Must be http or https' }),
  }),

  /** Content encoding, e.g. "UTF-8". */
  contentEncoding: z.string().optional().default(''),
});

export type HttpSamplerFormValues = z.infer<typeof httpSamplerSchema>;

export const httpSamplerDefaults: HttpSamplerFormValues = {
  domain: '',
  port: 80,
  path: '/',
  method: 'GET',
  protocol: 'http',
  contentEncoding: '',
};
