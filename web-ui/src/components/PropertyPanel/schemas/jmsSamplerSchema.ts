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

export const jmsSamplerSchema = z.object({
  /** JNDI provider URL, e.g. "tcp://localhost:61616". */
  providerUrl: z.string().min(1, 'Provider URL is required'),

  /** JNDI name for the connection factory. */
  connectionFactory: z.string().min(1, 'Connection factory is required'),

  /** JNDI name of the destination queue or topic. */
  destination: z.string().min(1, 'Destination is required'),

  /** Type of JMS message to send. */
  messageType: z.enum(['text', 'object', 'map', 'bytes'], {
    errorMap: () => ({ message: 'Must be text, object, map, or bytes' }),
  }),

  /** Message content / body. */
  content: z.string(),

  /** Additional JNDI properties as key=value pairs, one per line. */
  jndiProperties: z.string(),
});

export type JmsSamplerFormValues = z.infer<typeof jmsSamplerSchema>;

export const jmsSamplerDefaults: JmsSamplerFormValues = {
  providerUrl: '',
  connectionFactory: '',
  destination: '',
  messageType: 'text',
  content: '',
  jndiProperties: '',
};
