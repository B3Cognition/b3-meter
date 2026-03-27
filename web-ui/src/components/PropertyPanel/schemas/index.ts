/**
 * Schema registry — maps a node type string to its Zod schema and default values.
 *
 * To add support for a new element type, add an entry here.
 */

import { z } from 'zod';
import { testPlanSchema, testPlanDefaults } from './testPlanSchema.js';
import { threadGroupSchema, threadGroupDefaults } from './threadGroupSchema.js';
import { httpSamplerSchema, httpSamplerDefaults } from './httpSamplerSchema.js';
import { jdbcSamplerSchema, jdbcSamplerDefaults } from './jdbcSamplerSchema.js';
import { jmsSamplerSchema, jmsSamplerDefaults } from './jmsSamplerSchema.js';
import { tcpSamplerSchema, tcpSamplerDefaults } from './tcpSamplerSchema.js';
import { ldapSamplerSchema, ldapSamplerDefaults } from './ldapSamplerSchema.js';
import { smtpSamplerSchema, smtpSamplerDefaults } from './smtpSamplerSchema.js';
import { ftpSamplerSchema, ftpSamplerDefaults } from './ftpSamplerSchema.js';
import { boltSamplerSchema, boltSamplerDefaults } from './boltSamplerSchema.js';

// Protocol samplers (Phase 6)
import {
  grpcSamplerSchema, grpcSamplerDefaults,
  mqttSamplerSchema, mqttSamplerDefaults,
  webrtcSamplerSchema, webrtcSamplerDefaults,
  dashSamplerSchema, dashSamplerDefaults,
  hlsSamplerSchema, hlsSamplerDefaults,
  sseSamplerSchema, sseSamplerDefaults,
  webSocketSamplerSchema, webSocketSamplerDefaults,
} from './protocolSchemas.js';

// Timers
import {
  constantTimerSchema, constantTimerDefaults,
  gaussianTimerSchema, gaussianTimerDefaults,
  uniformTimerSchema, uniformTimerDefaults,
  constantThroughputTimerSchema, constantThroughputTimerDefaults,
  synchronizingTimerSchema, synchronizingTimerDefaults,
  poissonRandomTimerSchema, poissonRandomTimerDefaults,
  beanShellTimerSchema, beanShellTimerDefaults,
  jsr223TimerSchema, jsr223TimerDefaults,
} from './timerSchemas.js';

// Listeners
import {
  backendListenerSchema, backendListenerDefaults,
  viewResultsTableSchema, viewResultsTableDefaults,
} from './listenerSchemas.js';

// Assertions
import {
  responseAssertionSchema, responseAssertionDefaults,
  durationAssertionSchema, durationAssertionDefaults,
  sizeAssertionSchema, sizeAssertionDefaults,
  jsonAssertionSchema, jsonAssertionDefaults,
  xpathAssertionSchema, xpathAssertionDefaults,
  xmlAssertionSchema, xmlAssertionDefaults,
  jsr223AssertionSchema, jsr223AssertionDefaults,
  htmlAssertionSchema, htmlAssertionDefaults,
  beanShellAssertionSchema, beanShellAssertionDefaults,
  compareAssertionSchema, compareAssertionDefaults,
  xpath2AssertionSchema, xpath2AssertionDefaults,
} from './assertionSchemas.js';

// Config Elements
import {
  csvDataSetSchema, csvDataSetDefaults,
  headerManagerSchema, headerManagerDefaults,
  cookieManagerSchema, cookieManagerDefaults,
  userDefinedVariablesSchema, userDefinedVariablesDefaults,
  cacheManagerSchema, cacheManagerDefaults,
  authManagerSchema, authManagerDefaults,
  counterConfigSchema, counterConfigDefaults,
  randomVariableConfigSchema, randomVariableConfigDefaults,
  jdbcConnectionConfigSchema, jdbcConnectionConfigDefaults,
  dnsCacheManagerSchema, dnsCacheManagerDefaults,
  keystoreConfigSchema, keystoreConfigDefaults,
  loginConfigSchema, loginConfigDefaults,
  ldapDefaultsSchema, ldapDefaultsDefaults,
} from './configSchemas.js';

// Controllers
import {
  loopControllerSchema, loopControllerDefaults,
  ifControllerSchema, ifControllerDefaults,
  whileControllerSchema, whileControllerDefaults,
  transactionControllerSchema, transactionControllerDefaults,
  simpleControllerSchema, simpleControllerDefaults,
} from './controllerSchemas.js';

// Post-Processors
import {
  regexExtractorSchema, regexExtractorDefaults,
  jsonExtractorSchema, jsonExtractorDefaults,
  xpathExtractorSchema, xpathExtractorDefaults,
  cssExtractorSchema, cssExtractorDefaults,
  boundaryExtractorSchema, boundaryExtractorDefaults,
  debugPostProcessorSchema, debugPostProcessorDefaults,
  jsr223PostProcessorSchema, jsr223PostProcessorDefaults,
  beanShellPostProcessorSchema, beanShellPostProcessorDefaults,
} from './postProcessorSchemas.js';

// Pre-Processors
import {
  jsr223PreProcessorSchema, jsr223PreProcessorDefaults,
  beanShellPreProcessorSchema, beanShellPreProcessorDefaults,
  userParametersSchema, userParametersDefaults,
  regExUserParametersSchema, regExUserParametersDefaults,
  htmlLinkParserSchema, htmlLinkParserDefaults,
  urlRewritingModifierSchema, urlRewritingModifierDefaults,
} from './preProcessorSchemas.js';

// Controllers (Phase 2)
import {
  forEachControllerSchema, forEachControllerDefaults,
  throughputControllerSchema, throughputControllerDefaults,
  onceOnlyControllerSchema, onceOnlyControllerDefaults,
  recordingControllerSchema, recordingControllerDefaults,
  runtimeControllerSchema, runtimeControllerDefaults,
} from './controllerSchemas2.js';

// Controllers (Phase 3)
import {
  switchControllerSchema, switchControllerDefaults,
  randomControllerSchema, randomControllerDefaults,
  interleaveControllerSchema, interleaveControllerDefaults,
  randomOrderControllerSchema, randomOrderControllerDefaults,
  moduleControllerSchema, moduleControllerDefaults,
  includeControllerSchema, includeControllerDefaults,
} from './controllerSchemas3.js';

// Script Samplers
import {
  jsr223SamplerSchema, jsr223SamplerDefaults,
  beanShellSamplerSchema, beanShellSamplerDefaults,
  osProcessSamplerSchema, osProcessSamplerDefaults,
  debugSamplerSchema, debugSamplerDefaults,
} from './scriptSamplerSchemas.js';

// Legacy/Niche Samplers
import {
  soapSamplerSchema, soapSamplerDefaults,
  mailReaderSamplerSchema, mailReaderSamplerDefaults,
  bsfSamplerSchema, bsfSamplerDefaults,
  accessLogSamplerSchema, accessLogSamplerDefaults,
  ajpSamplerSchema, ajpSamplerDefaults,
  junitSamplerSchema, junitSamplerDefaults,
  jmsPublisherSchema, jmsPublisherDefaults,
  jmsSubscriberSchema, jmsSubscriberDefaults,
} from './legacySamplerSchemas.js';

export type { TestPlanFormValues, UserDefinedVariable } from './testPlanSchema.js';
export type { ThreadGroupFormValues } from './threadGroupSchema.js';
export type { HttpSamplerFormValues } from './httpSamplerSchema.js';
export type { JdbcSamplerFormValues } from './jdbcSamplerSchema.js';
export type { JmsSamplerFormValues } from './jmsSamplerSchema.js';
export type { TcpSamplerFormValues } from './tcpSamplerSchema.js';
export type { LdapSamplerFormValues } from './ldapSamplerSchema.js';
export type { SmtpSamplerFormValues } from './smtpSamplerSchema.js';
export type { FtpSamplerFormValues } from './ftpSamplerSchema.js';
export type { BoltSamplerFormValues } from './boltSamplerSchema.js';
export type {
  GrpcSamplerFormValues,
  MqttSamplerFormValues,
  WebrtcSamplerFormValues,
  DashSamplerFormValues,
  HlsSamplerFormValues,
  SseSamplerFormValues,
  WebSocketSamplerFormValues,
} from './protocolSchemas.js';
export type { ConstantTimerFormValues, GaussianTimerFormValues, UniformTimerFormValues, ConstantThroughputTimerFormValues, SynchronizingTimerFormValues, PoissonRandomTimerFormValues, BeanShellTimerFormValues, JSR223TimerFormValues } from './timerSchemas.js';
export type { BackendListenerFormValues, ViewResultsTableFormValues } from './listenerSchemas.js';
export type { ResponseAssertionFormValues, DurationAssertionFormValues, SizeAssertionFormValues, JsonAssertionFormValues, XpathAssertionFormValues, XmlAssertionFormValues, Jsr223AssertionFormValues, HtmlAssertionFormValues, BeanShellAssertionFormValues, CompareAssertionFormValues } from './assertionSchemas.js';
export type { CsvDataSetFormValues, HeaderManagerFormValues, CookieManagerFormValues, UserDefinedVariablesFormValues, CacheManagerFormValues, AuthManagerFormValues, CounterConfigFormValues, RandomVariableConfigFormValues, JdbcConnectionConfigFormValues, DnsCacheManagerFormValues, KeystoreConfigFormValues, LoginConfigFormValues, LdapDefaultsFormValues } from './configSchemas.js';
export type { LoopControllerFormValues, IfControllerFormValues, WhileControllerFormValues, TransactionControllerFormValues, SimpleControllerFormValues } from './controllerSchemas.js';
export type { RegexExtractorFormValues, JsonExtractorFormValues, XpathExtractorFormValues, CssExtractorFormValues, BoundaryExtractorFormValues, DebugPostProcessorFormValues, JSR223PostProcessorFormValues, BeanShellPostProcessorFormValues } from './postProcessorSchemas.js';
export type { JSR223PreProcessorFormValues, BeanShellPreProcessorFormValues, UserParametersFormValues, RegExUserParametersFormValues, HtmlLinkParserFormValues, UrlRewritingModifierFormValues } from './preProcessorSchemas.js';
export type { ForEachControllerFormValues, ThroughputControllerFormValues, OnceOnlyControllerFormValues, RecordingControllerFormValues, RuntimeControllerFormValues } from './controllerSchemas2.js';
export type { SwitchControllerFormValues, RandomControllerFormValues, InterleaveControllerFormValues, RandomOrderControllerFormValues, ModuleControllerFormValues, IncludeControllerFormValues } from './controllerSchemas3.js';
export type { JSR223SamplerFormValues, BeanShellSamplerFormValues, OSProcessSamplerFormValues, DebugSamplerFormValues } from './scriptSamplerSchemas.js';
export type { SoapSamplerFormValues, MailReaderSamplerFormValues, BsfSamplerFormValues, AccessLogSamplerFormValues, AjpSamplerFormValues, JunitSamplerFormValues, JmsPublisherFormValues, JmsSubscriberFormValues } from './legacySamplerSchemas.js';
export type { Xpath2AssertionFormValues } from './assertionSchemas.js';

export type AnySchema = z.ZodTypeAny;

export interface SchemaEntry {
  schema: AnySchema;
  defaults: Record<string, unknown>;
}

/** Registry mapping node type → schema + defaults. */
export const schemaRegistry: Record<string, SchemaEntry> = {
  TestPlan: {
    schema: testPlanSchema,
    defaults: testPlanDefaults as Record<string, unknown>,
  },
  ThreadGroup: {
    schema: threadGroupSchema,
    defaults: threadGroupDefaults as Record<string, unknown>,
  },
  HTTPSampler: {
    schema: httpSamplerSchema,
    defaults: httpSamplerDefaults as Record<string, unknown>,
  },
  JDBCSampler: {
    schema: jdbcSamplerSchema,
    defaults: jdbcSamplerDefaults as Record<string, unknown>,
  },
  JMSSampler: {
    schema: jmsSamplerSchema,
    defaults: jmsSamplerDefaults as Record<string, unknown>,
  },
  TCPSampler: {
    schema: tcpSamplerSchema,
    defaults: tcpSamplerDefaults as Record<string, unknown>,
  },
  LDAPSampler: {
    schema: ldapSamplerSchema,
    defaults: ldapSamplerDefaults as Record<string, unknown>,
  },
  SMTPSampler: {
    schema: smtpSamplerSchema,
    defaults: smtpSamplerDefaults as Record<string, unknown>,
  },
  FTPSampler: {
    schema: ftpSamplerSchema,
    defaults: ftpSamplerDefaults as Record<string, unknown>,
  },
  BoltSampler: {
    schema: boltSamplerSchema,
    defaults: boltSamplerDefaults as Record<string, unknown>,
  },

  // --- Protocol Samplers (Phase 6) ---
  GrpcSampler: {
    schema: grpcSamplerSchema,
    defaults: grpcSamplerDefaults as Record<string, unknown>,
  },
  MQTTSampler: {
    schema: mqttSamplerSchema,
    defaults: mqttSamplerDefaults as Record<string, unknown>,
  },
  WebRTCSampler: {
    schema: webrtcSamplerSchema,
    defaults: webrtcSamplerDefaults as Record<string, unknown>,
  },
  DASHSampler: {
    schema: dashSamplerSchema,
    defaults: dashSamplerDefaults as Record<string, unknown>,
  },
  HLSSampler: {
    schema: hlsSamplerSchema,
    defaults: hlsSamplerDefaults as Record<string, unknown>,
  },
  SSESampler: {
    schema: sseSamplerSchema,
    defaults: sseSamplerDefaults as Record<string, unknown>,
  },
  WebSocketSampler: {
    schema: webSocketSamplerSchema,
    defaults: webSocketSamplerDefaults as Record<string, unknown>,
  },

  // --- Timers ---
  ConstantTimer: {
    schema: constantTimerSchema,
    defaults: constantTimerDefaults as Record<string, unknown>,
  },
  GaussianRandomTimer: {
    schema: gaussianTimerSchema,
    defaults: gaussianTimerDefaults as Record<string, unknown>,
  },
  UniformRandomTimer: {
    schema: uniformTimerSchema,
    defaults: uniformTimerDefaults as Record<string, unknown>,
  },
  ConstantThroughputTimer: {
    schema: constantThroughputTimerSchema,
    defaults: constantThroughputTimerDefaults as Record<string, unknown>,
  },
  SynchronizingTimer: {
    schema: synchronizingTimerSchema,
    defaults: synchronizingTimerDefaults as Record<string, unknown>,
  },
  PoissonRandomTimer: {
    schema: poissonRandomTimerSchema,
    defaults: poissonRandomTimerDefaults as Record<string, unknown>,
  },
  BeanShellTimer: {
    schema: beanShellTimerSchema,
    defaults: beanShellTimerDefaults as Record<string, unknown>,
  },
  JSR223Timer: {
    schema: jsr223TimerSchema,
    defaults: jsr223TimerDefaults as Record<string, unknown>,
  },

  // --- Listeners ---
  BackendListener: {
    schema: backendListenerSchema,
    defaults: backendListenerDefaults as Record<string, unknown>,
  },
  ViewResultsTable: {
    schema: viewResultsTableSchema,
    defaults: viewResultsTableDefaults as Record<string, unknown>,
  },

  // --- Assertions ---
  ResponseAssertion: {
    schema: responseAssertionSchema,
    defaults: responseAssertionDefaults as Record<string, unknown>,
  },
  DurationAssertion: {
    schema: durationAssertionSchema,
    defaults: durationAssertionDefaults as Record<string, unknown>,
  },
  SizeAssertion: {
    schema: sizeAssertionSchema,
    defaults: sizeAssertionDefaults as Record<string, unknown>,
  },
  JSONPathAssertion: {
    schema: jsonAssertionSchema,
    defaults: jsonAssertionDefaults as Record<string, unknown>,
  },
  JSONPathAssertion2: {
    schema: jsonAssertionSchema,
    defaults: jsonAssertionDefaults as Record<string, unknown>,
  },
  XPathAssertion: {
    schema: xpathAssertionSchema,
    defaults: xpathAssertionDefaults as Record<string, unknown>,
  },
  XMLAssertion: {
    schema: xmlAssertionSchema,
    defaults: xmlAssertionDefaults as Record<string, unknown>,
  },
  JSR223Assertion: {
    schema: jsr223AssertionSchema,
    defaults: jsr223AssertionDefaults as Record<string, unknown>,
  },
  HTMLAssertion: {
    schema: htmlAssertionSchema,
    defaults: htmlAssertionDefaults as Record<string, unknown>,
  },
  BeanShellAssertion: {
    schema: beanShellAssertionSchema,
    defaults: beanShellAssertionDefaults as Record<string, unknown>,
  },
  CompareAssertion: {
    schema: compareAssertionSchema,
    defaults: compareAssertionDefaults as Record<string, unknown>,
  },

  // --- Config Elements ---
  CSVDataSet: {
    schema: csvDataSetSchema,
    defaults: csvDataSetDefaults as Record<string, unknown>,
  },
  HTTPHeaderManager: {
    schema: headerManagerSchema,
    defaults: headerManagerDefaults as Record<string, unknown>,
  },
  HTTPCookieManager: {
    schema: cookieManagerSchema,
    defaults: cookieManagerDefaults as Record<string, unknown>,
  },
  UserDefinedVariables: {
    schema: userDefinedVariablesSchema,
    defaults: userDefinedVariablesDefaults as Record<string, unknown>,
  },
  CacheManager: {
    schema: cacheManagerSchema,
    defaults: cacheManagerDefaults as Record<string, unknown>,
  },
  AuthManager: {
    schema: authManagerSchema,
    defaults: authManagerDefaults as Record<string, unknown>,
  },
  CounterConfig: {
    schema: counterConfigSchema,
    defaults: counterConfigDefaults as Record<string, unknown>,
  },
  RandomVariableConfig: {
    schema: randomVariableConfigSchema,
    defaults: randomVariableConfigDefaults as Record<string, unknown>,
  },
  JDBCDataSource: {
    schema: jdbcConnectionConfigSchema,
    defaults: jdbcConnectionConfigDefaults as Record<string, unknown>,
  },
  DNSCacheManager: {
    schema: dnsCacheManagerSchema,
    defaults: dnsCacheManagerDefaults as Record<string, unknown>,
  },
  KeystoreConfig: {
    schema: keystoreConfigSchema,
    defaults: keystoreConfigDefaults as Record<string, unknown>,
  },
  LoginConfig: {
    schema: loginConfigSchema,
    defaults: loginConfigDefaults as Record<string, unknown>,
  },
  LDAPSamplerBase: {
    schema: ldapDefaultsSchema,
    defaults: ldapDefaultsDefaults as Record<string, unknown>,
  },

  // --- Controllers ---
  LoopController: {
    schema: loopControllerSchema,
    defaults: loopControllerDefaults as Record<string, unknown>,
  },
  IfController: {
    schema: ifControllerSchema,
    defaults: ifControllerDefaults as Record<string, unknown>,
  },
  WhileController: {
    schema: whileControllerSchema,
    defaults: whileControllerDefaults as Record<string, unknown>,
  },
  TransactionController: {
    schema: transactionControllerSchema,
    defaults: transactionControllerDefaults as Record<string, unknown>,
  },
  SimpleController: {
    schema: simpleControllerSchema,
    defaults: simpleControllerDefaults as Record<string, unknown>,
  },

  // --- Controllers (Phase 2) ---
  ForeachController: {
    schema: forEachControllerSchema,
    defaults: forEachControllerDefaults as Record<string, unknown>,
  },
  ThroughputController: {
    schema: throughputControllerSchema,
    defaults: throughputControllerDefaults as Record<string, unknown>,
  },
  OnceOnlyController: {
    schema: onceOnlyControllerSchema,
    defaults: onceOnlyControllerDefaults as Record<string, unknown>,
  },
  RecordingController: {
    schema: recordingControllerSchema,
    defaults: recordingControllerDefaults as Record<string, unknown>,
  },
  RuntimeController: {
    schema: runtimeControllerSchema,
    defaults: runtimeControllerDefaults as Record<string, unknown>,
  },

  // --- Controllers (Phase 3) ---
  SwitchController: {
    schema: switchControllerSchema,
    defaults: switchControllerDefaults as Record<string, unknown>,
  },
  RandomController: {
    schema: randomControllerSchema,
    defaults: randomControllerDefaults as Record<string, unknown>,
  },
  InterleaveControl: {
    schema: interleaveControllerSchema,
    defaults: interleaveControllerDefaults as Record<string, unknown>,
  },
  RandomOrderController: {
    schema: randomOrderControllerSchema,
    defaults: randomOrderControllerDefaults as Record<string, unknown>,
  },
  ModuleController: {
    schema: moduleControllerSchema,
    defaults: moduleControllerDefaults as Record<string, unknown>,
  },
  IncludeController: {
    schema: includeControllerSchema,
    defaults: includeControllerDefaults as Record<string, unknown>,
  },

  // --- Pre-Processors ---
  JSR223PreProcessor: {
    schema: jsr223PreProcessorSchema,
    defaults: jsr223PreProcessorDefaults as Record<string, unknown>,
  },
  BeanShellPreProcessor: {
    schema: beanShellPreProcessorSchema,
    defaults: beanShellPreProcessorDefaults as Record<string, unknown>,
  },
  UserParameters: {
    schema: userParametersSchema,
    defaults: userParametersDefaults as Record<string, unknown>,
  },
  RegExUserParameters: {
    schema: regExUserParametersSchema,
    defaults: regExUserParametersDefaults as Record<string, unknown>,
  },
  HTMLLinkParser: {
    schema: htmlLinkParserSchema,
    defaults: htmlLinkParserDefaults as Record<string, unknown>,
  },
  HTTPURLRewritingModifier: {
    schema: urlRewritingModifierSchema,
    defaults: urlRewritingModifierDefaults as Record<string, unknown>,
  },

  // --- Post-Processors ---
  RegexExtractor: {
    schema: regexExtractorSchema,
    defaults: regexExtractorDefaults as Record<string, unknown>,
  },
  JSONPostProcessor: {
    schema: jsonExtractorSchema,
    defaults: jsonExtractorDefaults as Record<string, unknown>,
  },
  JSONPathExtractor: {
    schema: jsonExtractorSchema,
    defaults: jsonExtractorDefaults as Record<string, unknown>,
  },
  XPathExtractor: {
    schema: xpathExtractorSchema,
    defaults: xpathExtractorDefaults as Record<string, unknown>,
  },
  HtmlExtractor: {
    schema: cssExtractorSchema,
    defaults: cssExtractorDefaults as Record<string, unknown>,
  },
  BoundaryExtractor: {
    schema: boundaryExtractorSchema,
    defaults: boundaryExtractorDefaults as Record<string, unknown>,
  },
  DebugPostProcessor: {
    schema: debugPostProcessorSchema,
    defaults: debugPostProcessorDefaults as Record<string, unknown>,
  },
  JSR223PostProcessor: {
    schema: jsr223PostProcessorSchema,
    defaults: jsr223PostProcessorDefaults as Record<string, unknown>,
  },
  BeanShellPostProcessor: {
    schema: beanShellPostProcessorSchema,
    defaults: beanShellPostProcessorDefaults as Record<string, unknown>,
  },

  // --- Script Samplers ---
  JSR223Sampler: {
    schema: jsr223SamplerSchema,
    defaults: jsr223SamplerDefaults as Record<string, unknown>,
  },
  BeanShellSampler: {
    schema: beanShellSamplerSchema,
    defaults: beanShellSamplerDefaults as Record<string, unknown>,
  },
  OSProcessSampler: {
    schema: osProcessSamplerSchema,
    defaults: osProcessSamplerDefaults as Record<string, unknown>,
  },
  DebugSampler: {
    schema: debugSamplerSchema,
    defaults: debugSamplerDefaults as Record<string, unknown>,
  },

  // --- Legacy/Niche Samplers ---
  SOAPSampler: {
    schema: soapSamplerSchema,
    defaults: soapSamplerDefaults as Record<string, unknown>,
  },
  SOAPSampler2: {
    schema: soapSamplerSchema,
    defaults: soapSamplerDefaults as Record<string, unknown>,
  },
  MailReaderSampler: {
    schema: mailReaderSamplerSchema,
    defaults: mailReaderSamplerDefaults as Record<string, unknown>,
  },
  BSFSampler: {
    schema: bsfSamplerSchema,
    defaults: bsfSamplerDefaults as Record<string, unknown>,
  },
  AccessLogSampler: {
    schema: accessLogSamplerSchema,
    defaults: accessLogSamplerDefaults as Record<string, unknown>,
  },
  AjpSampler: {
    schema: ajpSamplerSchema,
    defaults: ajpSamplerDefaults as Record<string, unknown>,
  },
  JUnitSampler: {
    schema: junitSamplerSchema,
    defaults: junitSamplerDefaults as Record<string, unknown>,
  },
  PublisherSampler: {
    schema: jmsPublisherSchema,
    defaults: jmsPublisherDefaults as Record<string, unknown>,
  },
  SubscriberSampler: {
    schema: jmsSubscriberSchema,
    defaults: jmsSubscriberDefaults as Record<string, unknown>,
  },

  // --- XPath2 Assertion ---
  XPath2Assertion: {
    schema: xpath2AssertionSchema,
    defaults: xpath2AssertionDefaults as Record<string, unknown>,
  },
};

/**
 * Returns the schema entry for the given node type, or undefined when none
 * has been registered.
 */
export function getSchemaEntry(nodeType: string): SchemaEntry | undefined {
  return schemaRegistry[nodeType];
}
