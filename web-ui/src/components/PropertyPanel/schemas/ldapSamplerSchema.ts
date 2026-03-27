/**
 * Zod validation schema for LDAPSampler elements.
 */

import { z } from 'zod';

export const ldapSamplerSchema = z.object({
  /** LDAP server URL, e.g. "ldap://localhost:389". */
  serverUrl: z.string().min(1, 'Server URL is required'),

  /** Root distinguished name for the directory. */
  rootDn: z.string(),

  /** Distinguished name to use as the search base. */
  searchBase: z.string().min(1, 'Search base is required'),

  /** LDAP search filter expression, e.g. "(uid=jsmith)". */
  searchFilter: z.string().min(1, 'Search filter is required'),

  /** Search scope. */
  scope: z.enum(['base', 'one', 'sub'], {
    errorMap: () => ({ message: 'Must be base, one, or sub' }),
  }),

  /** Authentication type used for the bind operation. */
  authType: z.enum(['simple', 'none'], {
    errorMap: () => ({ message: 'Must be simple or none' }),
  }),
});

export type LdapSamplerFormValues = z.infer<typeof ldapSamplerSchema>;

export const ldapSamplerDefaults: LdapSamplerFormValues = {
  serverUrl: '',
  rootDn: '',
  searchBase: '',
  searchFilter: '(objectClass=*)',
  scope: 'sub',
  authType: 'simple',
};
