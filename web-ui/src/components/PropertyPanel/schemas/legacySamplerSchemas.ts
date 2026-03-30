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

/* --- SOAP Sampler --- */

export const soapSamplerSchema = z.object({
  /** SOAP endpoint hostname. */
  domain: z.string().min(1, 'Domain is required'),

  /** Endpoint port. */
  port: z.number().int().min(0).default(80),

  /** Endpoint path. */
  path: z.string().default('/'),

  /** Protocol (http or https). */
  protocol: z.enum(['http', 'https']).default('http'),

  /** SOAP XML envelope body. */
  xmlData: z.string().default(''),

  /** SOAPAction HTTP header value. */
  soapAction: z.string().default(''),

  /** Connect timeout in ms. */
  connectTimeout: z.number().int().min(0).default(0),

  /** Response timeout in ms. */
  responseTimeout: z.number().int().min(0).default(0),
});

export type SoapSamplerFormValues = z.infer<typeof soapSamplerSchema>;

export const soapSamplerDefaults: SoapSamplerFormValues = {
  domain: '',
  port: 80,
  path: '/',
  protocol: 'http',
  xmlData: '',
  soapAction: '',
  connectTimeout: 0,
  responseTimeout: 0,
};

/* --- Mail Reader Sampler --- */

export const mailReaderSamplerSchema = z.object({
  /** Mail server hostname. */
  host: z.string().min(1, 'Host is required'),

  /** Mail server port. */
  port: z.number().int().min(0).default(993),

  /** Mail protocol. */
  protocol: z.enum(['imap', 'imaps', 'pop3', 'pop3s']).default('imaps'),

  /** Login username. */
  username: z.string().default(''),

  /** Login password. */
  password: z.string().default(''),

  /** Mailbox folder. */
  folder: z.string().default('INBOX'),

  /** Number of messages to read (-1 = all). */
  numMessages: z.number().int().default(-1),

  /** Delete messages after reading. */
  deleteMessages: z.boolean().default(false),

  /** Store raw MIME content. */
  storeMimeMessage: z.boolean().default(false),
});

export type MailReaderSamplerFormValues = z.infer<typeof mailReaderSamplerSchema>;

export const mailReaderSamplerDefaults: MailReaderSamplerFormValues = {
  host: '',
  port: 993,
  protocol: 'imaps',
  username: '',
  password: '',
  folder: 'INBOX',
  numMessages: -1,
  deleteMessages: false,
  storeMimeMessage: false,
};

/* --- BSF Sampler --- */

export const bsfSamplerSchema = z.object({
  /** Scripting language name. */
  language: z.string().default('javascript'),

  /** Inline script text. */
  script: z.string().default(''),

  /** External script file path. */
  filename: z.string().default(''),

  /** Parameters string. */
  parameters: z.string().default(''),
});

export type BsfSamplerFormValues = z.infer<typeof bsfSamplerSchema>;

export const bsfSamplerDefaults: BsfSamplerFormValues = {
  language: 'javascript',
  script: '',
  filename: '',
  parameters: '',
};

/* --- Access Log Sampler --- */

export const accessLogSamplerSchema = z.object({
  /** Path to Apache access log file. */
  logFile: z.string().min(1, 'Log file path is required'),

  /** Optional filter class name. */
  filterClass: z.string().default(''),

  /** Target domain for replaying requests. */
  domain: z.string().default(''),

  /** Target port. */
  port: z.number().int().min(0).default(80),

  /** Protocol (http or https). */
  protocol: z.enum(['http', 'https']).default('http'),
});

export type AccessLogSamplerFormValues = z.infer<typeof accessLogSamplerSchema>;

export const accessLogSamplerDefaults: AccessLogSamplerFormValues = {
  logFile: '',
  filterClass: '',
  domain: '',
  port: 80,
  protocol: 'http',
};

/* --- AJP Sampler --- */

export const ajpSamplerSchema = z.object({
  /** AJP server hostname. */
  domain: z.string().min(1, 'Domain is required'),

  /** AJP port (default 8009). */
  port: z.number().int().min(0).default(8009),

  /** Request URI path. */
  path: z.string().default('/'),

  /** HTTP method. */
  method: z.enum(['GET', 'POST', 'PUT', 'DELETE', 'HEAD', 'OPTIONS', 'PATCH']).default('GET'),

  /** Protocol. */
  protocol: z.enum(['http', 'https']).default('http'),

  /** Connect timeout in ms. */
  connectTimeout: z.number().int().min(0).default(0),

  /** Response timeout in ms. */
  responseTimeout: z.number().int().min(0).default(0),
});

export type AjpSamplerFormValues = z.infer<typeof ajpSamplerSchema>;

export const ajpSamplerDefaults: AjpSamplerFormValues = {
  domain: '',
  port: 8009,
  path: '/',
  method: 'GET',
  protocol: 'http',
  connectTimeout: 0,
  responseTimeout: 0,
};

/* --- JUnit Sampler --- */

export const junitSamplerSchema = z.object({
  /** Fully qualified test class name. */
  classname: z.string().min(1, 'Class name is required'),

  /** Constructor argument (optional). */
  constructorString: z.string().default(''),

  /** Test method name to invoke. */
  method: z.string().min(1, 'Method name is required'),

  /** Package filter (informational). */
  packageFilter: z.string().default(''),

  /** Success message. */
  successMessage: z.string().default('Test passed'),

  /** Failure message. */
  failureMessage: z.string().default('Test failed'),

  /** Error message. */
  errorMessage: z.string().default('Test error'),

  /** Execute setUp method. */
  execSetup: z.boolean().default(true),

  /** Execute tearDown method. */
  execTeardown: z.boolean().default(true),

  /** Append assertion errors to response. */
  appendError: z.boolean().default(false),

  /** Append exception details to response. */
  appendException: z.boolean().default(false),
});

export type JunitSamplerFormValues = z.infer<typeof junitSamplerSchema>;

export const junitSamplerDefaults: JunitSamplerFormValues = {
  classname: '',
  constructorString: '',
  method: '',
  packageFilter: '',
  successMessage: 'Test passed',
  failureMessage: 'Test failed',
  errorMessage: 'Test error',
  execSetup: true,
  execTeardown: true,
  appendError: false,
  appendException: false,
};

/* --- JMS Publisher Sampler --- */

export const jmsPublisherSchema = z.object({
  /** JNDI initial context factory class. */
  initialContextFactory: z.string().default(''),

  /** JNDI provider URL. */
  providerUrl: z.string().default(''),

  /** JMS destination (topic or queue). */
  topic: z.string().default(''),

  /** JNDI security principal. */
  securityPrincipal: z.string().default(''),

  /** JNDI security credentials. */
  securityCredentials: z.string().default(''),

  /** Message body. */
  textMessage: z.string().default(''),

  /** JNDI connection factory name. */
  connectionFactory: z.string().default('ConnectionFactory'),
});

export type JmsPublisherFormValues = z.infer<typeof jmsPublisherSchema>;

export const jmsPublisherDefaults: JmsPublisherFormValues = {
  initialContextFactory: '',
  providerUrl: '',
  topic: '',
  securityPrincipal: '',
  securityCredentials: '',
  textMessage: '',
  connectionFactory: 'ConnectionFactory',
};

/* --- JMS Subscriber Sampler --- */

export const jmsSubscriberSchema = z.object({
  /** JNDI initial context factory class. */
  initialContextFactory: z.string().default(''),

  /** JNDI provider URL. */
  providerUrl: z.string().default(''),

  /** JMS destination (topic or queue). */
  topic: z.string().default(''),

  /** JNDI security principal. */
  securityPrincipal: z.string().default(''),

  /** JNDI security credentials. */
  securityCredentials: z.string().default(''),

  /** JNDI connection factory name. */
  connectionFactory: z.string().default('ConnectionFactory'),

  /** Receive timeout in ms. */
  timeout: z.number().int().min(0).default(5000),
});

export type JmsSubscriberFormValues = z.infer<typeof jmsSubscriberSchema>;

export const jmsSubscriberDefaults: JmsSubscriberFormValues = {
  initialContextFactory: '',
  providerUrl: '',
  topic: '',
  securityPrincipal: '',
  securityCredentials: '',
  connectionFactory: 'ConnectionFactory',
  timeout: 5000,
};
