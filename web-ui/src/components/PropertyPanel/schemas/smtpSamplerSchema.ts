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

export const smtpSamplerSchema = z.object({
  /** SMTP server hostname or IP address. */
  server: z.string().min(1, 'Server is required'),

  /** SMTP port number. Must be between 1 and 65535. */
  port: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(1, 'Must be between 1 and 65535')
    .max(65535, 'Must be between 1 and 65535'),

  /** Sender email address. */
  from: z.string().email('Must be a valid email address'),

  /** Recipient email address(es), comma-separated. */
  to: z.string().min(1, 'Recipient is required'),

  /** Email subject line. */
  subject: z.string(),

  /** Email body / message text. */
  body: z.string(),

  /** Whether to use SSL/TLS for the connection. */
  useSSL: z.boolean(),

  /** Whether to upgrade the connection with STARTTLS. */
  useStartTLS: z.boolean(),

  /** SMTP authentication username. */
  username: z.string(),

  /** SMTP authentication password. */
  password: z.string(),
});

export type SmtpSamplerFormValues = z.infer<typeof smtpSamplerSchema>;

export const smtpSamplerDefaults: SmtpSamplerFormValues = {
  server: '',
  port: 25,
  from: '',
  to: '',
  subject: '',
  body: '',
  useSSL: false,
  useStartTLS: false,
  username: '',
  password: '',
};
