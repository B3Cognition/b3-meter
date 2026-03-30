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

export const ftpSamplerSchema = z.object({
  /** FTP server hostname or IP address. */
  server: z.string().min(1, 'Server is required'),

  /** FTP port number. Must be between 1 and 65535. */
  port: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(1, 'Must be between 1 and 65535')
    .max(65535, 'Must be between 1 and 65535'),

  /** FTP account username. */
  username: z.string(),

  /** FTP account password. */
  password: z.string(),

  /** Remote file path on the FTP server. */
  remotePath: z.string().min(1, 'Remote path is required'),

  /** Local file path for upload (put) or download destination (get). */
  localPath: z.string().min(1, 'Local path is required'),

  /** Transfer direction: download (get) or upload (put). */
  mode: z.enum(['get', 'put'], {
    errorMap: () => ({ message: 'Must be get or put' }),
  }),

  /** Whether to use binary transfer mode. False means ASCII mode. */
  binary: z.boolean(),
});

export type FtpSamplerFormValues = z.infer<typeof ftpSamplerSchema>;

export const ftpSamplerDefaults: FtpSamplerFormValues = {
  server: '',
  port: 21,
  username: '',
  password: '',
  remotePath: '',
  localPath: '',
  mode: 'get',
  binary: true,
};
