/**
 * Zod validation schema for HTTPSampler elements.
 */

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
