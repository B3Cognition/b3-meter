/**
 * Zod validation schema for TCPSampler elements.
 */

import { z } from 'zod';

export const tcpSamplerSchema = z.object({
  /** Target server hostname or IP address. */
  server: z.string().min(1, 'Server is required'),

  /** TCP port number. Must be between 1 and 65535. */
  port: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(1, 'Must be between 1 and 65535')
    .max(65535, 'Must be between 1 and 65535'),

  /** Read timeout in milliseconds. 0 means no timeout. */
  timeout: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),

  /** Whether to reuse an existing TCP connection between samples. */
  reUseConnection: z.boolean(),

  /** Whether to close the TCP connection after each sample. */
  closeConnection: z.boolean(),

  /** Text payload to send to the server. */
  textToSend: z.string(),

  /**
   * End-of-line byte value (0–255). Used to detect end of server response.
   * -1 disables EOL detection.
   */
  eolByte: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(-1, 'Must be -1 (disabled) or 0–255')
    .max(255, 'Must be -1 (disabled) or 0–255'),
});

export type TcpSamplerFormValues = z.infer<typeof tcpSamplerSchema>;

export const tcpSamplerDefaults: TcpSamplerFormValues = {
  server: '',
  port: 4000,
  timeout: 0,
  reUseConnection: true,
  closeConnection: false,
  textToSend: '',
  eolByte: -1,
};
