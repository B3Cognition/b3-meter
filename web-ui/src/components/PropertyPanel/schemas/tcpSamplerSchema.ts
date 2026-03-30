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
