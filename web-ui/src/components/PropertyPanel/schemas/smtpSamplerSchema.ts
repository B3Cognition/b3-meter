/**
 * Zod validation schema for SMTPSampler elements.
 */

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
