/**
 * Zod validation schema for BoltSampler (Neo4j) elements.
 */

import { z } from 'zod';

export const boltSamplerSchema = z.object({
  /** Neo4j Bolt connection URL, e.g. "bolt://localhost:7687". */
  serverUrl: z.string().min(1, 'Server URL is required'),

  /** Target Neo4j database name. Leave empty for the default database. */
  database: z.string(),

  /** Cypher query to execute against the database. */
  cypherQuery: z.string().min(1, 'Cypher query is required'),

  /** Neo4j username for authentication. */
  username: z.string(),

  /** Neo4j password for authentication. */
  password: z.string(),

  /** Query timeout in seconds. 0 means no timeout. */
  timeout: z
    .number({ invalid_type_error: 'Must be a number' })
    .int('Must be a whole number')
    .min(0, 'Must be at least 0'),
});

export type BoltSamplerFormValues = z.infer<typeof boltSamplerSchema>;

export const boltSamplerDefaults: BoltSamplerFormValues = {
  serverUrl: '',
  database: '',
  cypherQuery: '',
  username: '',
  password: '',
  timeout: 0,
};
