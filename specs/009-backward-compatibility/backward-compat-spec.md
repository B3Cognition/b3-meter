# Backward Compatibility Specification: jMeter Next -- 100% JMeter 5.x Parity

**Author**: INVESTIGATOR agent
**Date**: 2026-03-26
**Status**: Definitive reference for reaching full Apache JMeter 5.x backward compatibility
**Baseline**: Gap analysis from `specs/008-productization/productization-gap-analysis.md`

---

## Current State Summary

### What is BUILT (executes in NodeInterpreter.dispatchNode)

| Category | Elements |
|----------|----------|
| Samplers | HTTPSamplerProxy, HTTPSampler, WebSocketSampler, SSESampler, MQTTSampler, GrpcSampler, HLSSampler, WebRTCSampler, DASHSampler, FTPSampler, LDAPSampler, JSR223Sampler, BeanShellSampler, OSProcessSampler, DebugSampler |
| Controllers | LoopController, IfController, TransactionController (transparent) |
| Assertions | ResponseAssertion |
| Timers | ConstantTimer, GaussianRandomTimer, UniformRandomTimer |
| Post-Processors | RegexExtractor, JSONPostProcessor, JSONPathExtractor |

### What has UI Schema only (schemaRegistry in index.ts)

| Category | Elements |
|----------|----------|
| Samplers | JDBCSampler, JMSSampler, TCPSampler, SMTPSampler, BoltSampler |
| Controllers | WhileController, SimpleController |
| Assertions | DurationAssertion, SizeAssertion |
| Config | CSVDataSet, HTTPHeaderManager, HTTPCookieManager, UserDefinedVariables |

### What has Context Menu entry only (NodeContextMenu DEFAULT_PROPERTIES)

| Category | Elements |
|----------|----------|
| Config | HTTPRequestDefaults, CookieManager, HeaderManager |
| Listeners | ResultCollector, Summariser, AggregateReport |

---

## MISSING ELEMENTS -- Complete Specification

---

## 1. Missing Samplers

### 1.1 JMS Publisher

| Field | Value |
|-------|-------|
| **testclass** | `JMSPublisher` |
| **guiclass** | `JMSSamplerGui` |
| **Category** | Sampler |
| **Description** | Publishes messages to a JMS topic or queue. Supports TextMessage, MapMessage, BytesMessage, and ObjectMessage types. Used for messaging middleware load testing (ActiveMQ, RabbitMQ JMS, IBM MQ). |
| **Priority** | P2 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<JMSPublisher guiclass="JMSSamplerGui" testclass="JMSPublisher" testname="JMS Publisher" enabled="true">
  <stringProp name="jms.initial_context_factory">org.apache.activemq.jndi.ActiveMQInitialContextFactory</stringProp>
  <stringProp name="jms.provider_url">tcp://localhost:61616</stringProp>
  <stringProp name="jms.connection_factory">ConnectionFactory</stringProp>
  <stringProp name="jms.topic">dynamicTopics/TEST.FOO</stringProp>
  <stringProp name="jms.security_principle"></stringProp>
  <stringProp name="jms.security_credentials"></stringProp>
  <stringProp name="jms.text_message">Hello JMS</stringProp>
  <stringProp name="jms.input_file"></stringProp>
  <stringProp name="jms.random_path"></stringProp>
  <stringProp name="jms.config_choice">jms_use_text</stringProp>
  <stringProp name="jms.config_msg_type">jms_text_message</stringProp>
  <intProp name="jms.iterations">1</intProp>
  <boolProp name="jms.authenticate">false</boolProp>
  <boolProp name="jms.read_response">true</boolProp>
  <stringProp name="jms.expiration"></stringProp>
  <stringProp name="jms.priority"></stringProp>
  <boolProp name="jms.non_persistent">false</boolProp>
</JMSPublisher>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `jms.initial_context_factory` | stringProp | `""` | JNDI InitialContextFactory class name |
| `jms.provider_url` | stringProp | `""` | JNDI provider URL |
| `jms.connection_factory` | stringProp | `"ConnectionFactory"` | JNDI name of ConnectionFactory |
| `jms.topic` | stringProp | `""` | Destination name (topic or queue) |
| `jms.security_principle` | stringProp | `""` | Username for JNDI authentication |
| `jms.security_credentials` | stringProp | `""` | Password for JNDI authentication |
| `jms.text_message` | stringProp | `""` | Message body text |
| `jms.input_file` | stringProp | `""` | File containing message body |
| `jms.random_path` | stringProp | `""` | Directory for random file selection |
| `jms.config_choice` | stringProp | `"jms_use_text"` | Message source: jms_use_text, jms_use_file, jms_use_random_file |
| `jms.config_msg_type` | stringProp | `"jms_text_message"` | Message type: jms_text_message, jms_map_message, jms_object_message, jms_bytes_message |
| `jms.iterations` | intProp | `1` | Number of messages to send per sample |
| `jms.authenticate` | boolProp | `false` | Use authentication |
| `jms.read_response` | boolProp | `true` | Read response after send |
| `jms.expiration` | stringProp | `""` | Message expiration (ms) |
| `jms.priority` | stringProp | `""` | Message priority (0-9) |
| `jms.non_persistent` | boolProp | `false` | Use non-persistent delivery |

**Implementation Approach:**
Use `jakarta.jms` API (JDK does not bundle JMS; add `jakarta.jms-api` + a client like `activemq-client` as optional runtime dependency). Create `JMSPublisherExecutor` that looks up ConnectionFactory via JNDI (`javax.naming.InitialContext`), creates Connection/Session/MessageProducer, sends message, and measures round-trip time.

**UI Schema (Zod):**
```typescript
export const jmsPublisherSchema = z.object({
  initialContextFactory: z.string().default('org.apache.activemq.jndi.ActiveMQInitialContextFactory'),
  providerUrl: z.string().default('tcp://localhost:61616'),
  connectionFactory: z.string().default('ConnectionFactory'),
  destination: z.string().default(''),
  securityPrincipal: z.string().default(''),
  securityCredentials: z.string().default(''),
  textMessage: z.string().default(''),
  inputFile: z.string().default(''),
  randomPath: z.string().default(''),
  configChoice: z.enum(['jms_use_text', 'jms_use_file', 'jms_use_random_file']).default('jms_use_text'),
  messageType: z.enum(['jms_text_message', 'jms_map_message', 'jms_object_message', 'jms_bytes_message']).default('jms_text_message'),
  iterations: z.number().int().min(1).default(1),
  authenticate: z.boolean().default(false),
  readResponse: z.boolean().default(true),
  expiration: z.string().default(''),
  priority: z.string().default(''),
  nonPersistent: z.boolean().default(false),
});
```

---

### 1.2 JMS Subscriber

| Field | Value |
|-------|-------|
| **testclass** | `JMSSubscriber` |
| **guiclass** | `JMSSamplerGui` |
| **Category** | Sampler |
| **Description** | Subscribes to a JMS topic or queue and consumes messages. Supports durable subscriptions, message selectors, and timeout-based consumption. |
| **Priority** | P2 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<JMSSubscriber guiclass="JMSSamplerGui" testclass="JMSSubscriber" testname="JMS Subscriber" enabled="true">
  <stringProp name="jms.initial_context_factory">org.apache.activemq.jndi.ActiveMQInitialContextFactory</stringProp>
  <stringProp name="jms.provider_url">tcp://localhost:61616</stringProp>
  <stringProp name="jms.connection_factory">ConnectionFactory</stringProp>
  <stringProp name="jms.topic">dynamicTopics/TEST.FOO</stringProp>
  <stringProp name="jms.security_principle"></stringProp>
  <stringProp name="jms.security_credentials"></stringProp>
  <intProp name="jms.iterations">1</intProp>
  <boolProp name="jms.authenticate">false</boolProp>
  <stringProp name="jms.timeout">2000</stringProp>
  <stringProp name="jms.durableSubscriptionId"></stringProp>
  <stringProp name="jms.client_id"></stringProp>
  <stringProp name="jms.jms_selector"></stringProp>
  <boolProp name="jms.stop_between">true</boolProp>
  <stringProp name="jms.separator"></stringProp>
  <boolProp name="jms.read_response">true</boolProp>
</JMSSubscriber>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `jms.initial_context_factory` | stringProp | `""` | JNDI InitialContextFactory class |
| `jms.provider_url` | stringProp | `""` | JNDI provider URL |
| `jms.connection_factory` | stringProp | `"ConnectionFactory"` | JNDI ConnectionFactory name |
| `jms.topic` | stringProp | `""` | Destination name |
| `jms.security_principle` | stringProp | `""` | Username |
| `jms.security_credentials` | stringProp | `""` | Password |
| `jms.iterations` | intProp | `1` | Number of messages to read per sample |
| `jms.authenticate` | boolProp | `false` | Use authentication |
| `jms.timeout` | stringProp | `"2000"` | Receive timeout (ms) |
| `jms.durableSubscriptionId` | stringProp | `""` | Durable subscription ID |
| `jms.client_id` | stringProp | `""` | JMS client ID |
| `jms.jms_selector` | stringProp | `""` | JMS message selector expression |
| `jms.stop_between` | boolProp | `true` | Stop connection between samples |
| `jms.separator` | stringProp | `""` | Separator for multiple messages |
| `jms.read_response` | boolProp | `true` | Read message body |

**Implementation Approach:**
Same JMS API as Publisher. `JMSSubscriberExecutor` creates a MessageConsumer (or durable TopicSubscriber), calls `receive(timeout)`, captures message body as response data. Measure latency from receive call to message arrival.

**UI Schema (Zod):**
```typescript
export const jmsSubscriberSchema = z.object({
  initialContextFactory: z.string().default('org.apache.activemq.jndi.ActiveMQInitialContextFactory'),
  providerUrl: z.string().default('tcp://localhost:61616'),
  connectionFactory: z.string().default('ConnectionFactory'),
  destination: z.string().default(''),
  securityPrincipal: z.string().default(''),
  securityCredentials: z.string().default(''),
  iterations: z.number().int().min(1).default(1),
  authenticate: z.boolean().default(false),
  timeout: z.string().default('2000'),
  durableSubscriptionId: z.string().default(''),
  clientId: z.string().default(''),
  jmsSelector: z.string().default(''),
  stopBetween: z.boolean().default(true),
  separator: z.string().default(''),
  readResponse: z.boolean().default(true),
});
```

---

### 1.3 SOAP/XML-RPC Request

| Field | Value |
|-------|-------|
| **testclass** | `SoapSampler` |
| **guiclass** | `SoapSamplerGui` |
| **Category** | Sampler |
| **Description** | Sends SOAP XML messages over HTTP. Deprecated in JMeter 5.x (users should use HTTP Request with appropriate headers instead). |
| **Priority** | P3 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<SoapSampler guiclass="SoapSamplerGui" testclass="SoapSampler" testname="SOAP/XML-RPC Request" enabled="true">
  <stringProp name="HTTPSampler.xml_data"></stringProp>
  <stringProp name="HTTPSampler.xml_data_file"></stringProp>
  <stringProp name="SoapSampler.URL_DATA">http://localhost:8080/ws</stringProp>
  <stringProp name="SoapSampler.SOAP_ACTION"></stringProp>
  <boolProp name="SoapSampler.SEND_SOAP_ACTION">true</boolProp>
  <boolProp name="SoapSampler.xml_path_loc">false</boolProp>
</SoapSampler>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `HTTPSampler.xml_data` | stringProp | `""` | Inline SOAP XML body |
| `HTTPSampler.xml_data_file` | stringProp | `""` | File containing SOAP XML body |
| `SoapSampler.URL_DATA` | stringProp | `""` | Target URL for SOAP endpoint |
| `SoapSampler.SOAP_ACTION` | stringProp | `""` | SOAPAction HTTP header value |
| `SoapSampler.SEND_SOAP_ACTION` | boolProp | `true` | Include SOAPAction header |
| `SoapSampler.xml_path_loc` | boolProp | `false` | Use xml_data_file path for loading |

**Implementation Approach:**
Thin wrapper over `java.net.http.HttpClient`. Set `Content-Type: text/xml; charset=utf-8` header, add `SOAPAction` header if enabled, POST the XML body to the URL. No WSDL parsing needed -- this is just a specialized HTTP POST.

**UI Schema (Zod):**
```typescript
export const soapSamplerSchema = z.object({
  url: z.string().default('http://localhost:8080/ws'),
  xmlData: z.string().default(''),
  xmlDataFile: z.string().default(''),
  soapAction: z.string().default(''),
  sendSoapAction: z.boolean().default(true),
  useFile: z.boolean().default(false),
});
```

---

### 1.4 Mail Reader Sampler

| Field | Value |
|-------|-------|
| **testclass** | `MailReaderSampler` |
| **guiclass** | `MailReaderSamplerGui` |
| **Category** | Sampler |
| **Description** | Reads email from a mail server using POP3, POP3S, IMAP, or IMAPS protocols. Can delete messages after reading. Used for email system testing and email-triggered workflow validation. |
| **Priority** | P3 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<MailReaderSampler guiclass="MailReaderSamplerGui" testclass="MailReaderSampler" testname="Mail Reader Sampler" enabled="true">
  <stringProp name="MailReaderSampler.server_type">pop3</stringProp>
  <stringProp name="MailReaderSampler.server">mail.example.com</stringProp>
  <stringProp name="MailReaderSampler.port"></stringProp>
  <stringProp name="MailReaderSampler.username"></stringProp>
  <stringProp name="MailReaderSampler.password"></stringProp>
  <stringProp name="MailReaderSampler.folder">INBOX</stringProp>
  <stringProp name="MailReaderSampler.num_messages">-1</stringProp>
  <boolProp name="MailReaderSampler.delete">false</boolProp>
  <boolProp name="MailReaderSampler.storeMimeMessage">false</boolProp>
  <stringProp name="MailReaderSampler.headerOnly">false</stringProp>
</MailReaderSampler>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `MailReaderSampler.server_type` | stringProp | `"pop3"` | Protocol: pop3, pop3s, imap, imaps |
| `MailReaderSampler.server` | stringProp | `""` | Mail server hostname |
| `MailReaderSampler.port` | stringProp | `""` | Port (empty = protocol default) |
| `MailReaderSampler.username` | stringProp | `""` | Login username |
| `MailReaderSampler.password` | stringProp | `""` | Login password |
| `MailReaderSampler.folder` | stringProp | `"INBOX"` | Mail folder to read |
| `MailReaderSampler.num_messages` | stringProp | `"-1"` | Number of messages to read (-1 = all) |
| `MailReaderSampler.delete` | boolProp | `false` | Delete messages after reading |
| `MailReaderSampler.storeMimeMessage` | boolProp | `false` | Store full MIME message in result |
| `MailReaderSampler.headerOnly` | stringProp | `"false"` | Read headers only |

**Implementation Approach:**
Use `jakarta.mail` API (`jakarta.mail:jakarta.mail-api` + `org.eclipse.angus:angus-mail` implementation). Create `Store` for POP3/IMAP, open `Folder`, fetch messages with `Message[]`, iterate and collect subject/body/headers as response data.

**UI Schema (Zod):**
```typescript
export const mailReaderSamplerSchema = z.object({
  serverType: z.enum(['pop3', 'pop3s', 'imap', 'imaps']).default('pop3'),
  server: z.string().default(''),
  port: z.string().default(''),
  username: z.string().default(''),
  password: z.string().default(''),
  folder: z.string().default('INBOX'),
  numMessages: z.string().default('-1'),
  deleteMessages: z.boolean().default(false),
  storeMimeMessage: z.boolean().default(false),
  headerOnly: z.boolean().default(false),
});
```

---

### 1.5 BSF Sampler

| Field | Value |
|-------|-------|
| **testclass** | `BSFSampler` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Sampler |
| **Description** | Executes scripts using the Bean Scripting Framework (BSF). Deprecated in JMeter 5.x -- users should use JSR223 Sampler instead. Supports JavaScript, Jython, JRuby, etc. |
| **Priority** | P3 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<BSFSampler guiclass="TestBeanGUI" testclass="BSFSampler" testname="BSF Sampler" enabled="true">
  <stringProp name="scriptLanguage">javascript</stringProp>
  <stringProp name="parameters"></stringProp>
  <stringProp name="filename"></stringProp>
  <stringProp name="script"></stringProp>
</BSFSampler>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `scriptLanguage` | stringProp | `"javascript"` | Scripting language (javascript, jython, etc.) |
| `parameters` | stringProp | `""` | Parameters passed to script |
| `filename` | stringProp | `""` | Script file path |
| `script` | stringProp | `""` | Inline script body |

**Implementation Approach:**
Map directly to existing JSR223SamplerExecutor since JDK's `javax.script.ScriptEngineManager` (JSR 223) supersedes BSF. For import compatibility, register `BSFSampler` testclass in NodeInterpreter dispatch and delegate to JSR223SamplerExecutor with language mapping.

**UI Schema (Zod):**
```typescript
export const bsfSamplerSchema = z.object({
  scriptLanguage: z.string().default('javascript'),
  parameters: z.string().default(''),
  filename: z.string().default(''),
  script: z.string().default(''),
});
```

---

### 1.6 Access Log Sampler

| Field | Value |
|-------|-------|
| **testclass** | `AccessLogSampler` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Sampler |
| **Description** | Replays HTTP requests parsed from an Apache/Tomcat access log file. Each log entry becomes an HTTP request. Used for production traffic replay. |
| **Priority** | P3 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<AccessLogSampler guiclass="TestBeanGUI" testclass="AccessLogSampler" testname="Access Log Sampler" enabled="true">
  <stringProp name="logFile">/var/log/apache2/access.log</stringProp>
  <stringProp name="parserClassName">org.apache.jmeter.protocol.http.util.accesslog.TCLogParser</stringProp>
  <stringProp name="domain">localhost</stringProp>
  <stringProp name="port">80</stringProp>
  <boolProp name="portString">false</boolProp>
  <stringProp name="imageParsing">false</stringProp>
</AccessLogSampler>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `logFile` | stringProp | `""` | Path to access log file |
| `parserClassName` | stringProp | `"TCLogParser"` | Parser class (TCLogParser for combined/common format) |
| `domain` | stringProp | `""` | Override target domain |
| `port` | stringProp | `"80"` | Override target port |
| `portString` | boolProp | `false` | Include port in request URL |
| `imageParsing` | stringProp | `"false"` | Parse embedded image resources |

**Implementation Approach:**
Read access log lines with `java.io.BufferedReader`. Parse Common Log Format / Combined Log Format using regex: `^(\S+) \S+ \S+ \[.*?\] "(\S+) (\S+) .*?" (\d+)`. Extract method and path. Delegate to existing HttpSamplerExecutor with extracted method/path applied to configured domain/port.

**UI Schema (Zod):**
```typescript
export const accessLogSamplerSchema = z.object({
  logFile: z.string().default(''),
  parserClassName: z.string().default('TCLogParser'),
  domain: z.string().default(''),
  port: z.string().default('80'),
  portString: z.boolean().default(false),
  imageParsing: z.boolean().default(false),
});
```

---

### 1.7 AJP/1.3 Sampler

| Field | Value |
|-------|-------|
| **testclass** | `AjpSampler` |
| **guiclass** | `AjpSamplerGui` |
| **Category** | Sampler |
| **Description** | Sends requests using the Apache JServ Protocol (AJP/1.3), used for communication between Apache httpd and Tomcat. Tests the AJP connector directly. |
| **Priority** | P3 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<AjpSampler guiclass="AjpSamplerGui" testclass="AjpSampler" testname="AJP/1.3 Sampler" enabled="true">
  <stringProp name="HTTPSampler.domain">localhost</stringProp>
  <stringProp name="HTTPSampler.port">8009</stringProp>
  <stringProp name="HTTPSampler.protocol"></stringProp>
  <stringProp name="HTTPSampler.path">/</stringProp>
  <stringProp name="HTTPSampler.method">GET</stringProp>
  <stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>
  <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
  <boolProp name="HTTPSampler.auto_redirects">false</boolProp>
  <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
</AjpSampler>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `HTTPSampler.domain` | stringProp | `""` | Target server |
| `HTTPSampler.port` | stringProp | `"8009"` | AJP port |
| `HTTPSampler.protocol` | stringProp | `""` | Protocol (empty = http) |
| `HTTPSampler.path` | stringProp | `"/"` | Request path |
| `HTTPSampler.method` | stringProp | `"GET"` | HTTP method |
| `HTTPSampler.contentEncoding` | stringProp | `"UTF-8"` | Content encoding |
| `HTTPSampler.follow_redirects` | boolProp | `true` | Follow redirects |
| `HTTPSampler.auto_redirects` | boolProp | `false` | Use HttpURLConnection redirect handling |
| `HTTPSampler.use_keepalive` | boolProp | `true` | Use keep-alive |

**Implementation Approach:**
Implement AJP/1.3 binary protocol over `java.net.Socket`. AJP is a fixed-format binary protocol: send a prefix packet (0x1234 + length), then AJP request packet (type 0x02 for forward request), read AJP response. The protocol spec is well-documented. Pure JDK socket implementation.

**UI Schema (Zod):**
```typescript
export const ajpSamplerSchema = z.object({
  domain: z.string().default('localhost'),
  port: z.string().default('8009'),
  protocol: z.string().default(''),
  path: z.string().default('/'),
  method: z.enum(['GET', 'POST', 'PUT', 'DELETE', 'HEAD', 'OPTIONS', 'PATCH']).default('GET'),
  contentEncoding: z.string().default('UTF-8'),
  followRedirects: z.boolean().default(true),
  autoRedirects: z.boolean().default(false),
  useKeepAlive: z.boolean().default(true),
});
```

---

### 1.8 JUnit Request

| Field | Value |
|-------|-------|
| **testclass** | `JUnitSampler` |
| **guiclass** | `JUnitTestSamplerGui` |
| **Category** | Sampler |
| **Description** | Executes JUnit test methods as load test samples. Each test method execution is one sample. Supports JUnit 3 (TestCase) and JUnit 4 (@Test) conventions. |
| **Priority** | P3 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<JUnitSampler guiclass="JUnitTestSamplerGui" testclass="JUnitSampler" testname="JUnit Request" enabled="true">
  <stringProp name="junitSampler.classname">com.example.MyTest</stringProp>
  <stringProp name="junitsampler.method">testMethod</stringProp>
  <stringProp name="junitsampler.constructorstring"></stringProp>
  <stringProp name="junitsampler.success">Test successful</stringProp>
  <stringProp name="junitsampler.success.code">1000</stringProp>
  <stringProp name="junitsampler.failure">Test failed</stringProp>
  <stringProp name="junitsampler.failure.code">0001</stringProp>
  <stringProp name="junitsampler.error">An unexpected error occurred</stringProp>
  <stringProp name="junitsampler.error.code">9999</stringProp>
  <stringProp name="junitsampler.pkg.filter"></stringProp>
  <boolProp name="junitsampler.append_error">false</boolProp>
  <boolProp name="junitsampler.append_exception">false</boolProp>
  <boolProp name="junitsampler.createOneInstancePerSample">false</boolProp>
</JUnitSampler>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `junitSampler.classname` | stringProp | `""` | Fully qualified test class name |
| `junitsampler.method` | stringProp | `""` | Test method name |
| `junitsampler.constructorstring` | stringProp | `""` | Constructor argument string |
| `junitsampler.success` | stringProp | `"Test successful"` | Success message |
| `junitsampler.success.code` | stringProp | `"1000"` | Success response code |
| `junitsampler.failure` | stringProp | `"Test failed"` | Failure message |
| `junitsampler.failure.code` | stringProp | `"0001"` | Failure response code |
| `junitsampler.error` | stringProp | `"An unexpected error occurred"` | Error message |
| `junitsampler.error.code` | stringProp | `"9999"` | Error response code |
| `junitsampler.pkg.filter` | stringProp | `""` | Package filter |
| `junitsampler.append_error` | boolProp | `false` | Append assertion errors to response |
| `junitsampler.append_exception` | boolProp | `false` | Append exception details to response |
| `junitsampler.createOneInstancePerSample` | boolProp | `false` | Create new test instance per sample |

**Implementation Approach:**
Use `java.lang.reflect` to load the test class, instantiate it, and invoke the method. Wrap in try/catch: success = test passes without exception, failure = AssertionError, error = other Exception. Measure method execution time as sample latency. No JUnit dependency needed for invocation (pure reflection).

**UI Schema (Zod):**
```typescript
export const junitSamplerSchema = z.object({
  classname: z.string().default(''),
  method: z.string().default(''),
  constructorString: z.string().default(''),
  successMessage: z.string().default('Test successful'),
  successCode: z.string().default('1000'),
  failureMessage: z.string().default('Test failed'),
  failureCode: z.string().default('0001'),
  errorMessage: z.string().default('An unexpected error occurred'),
  errorCode: z.string().default('9999'),
  packageFilter: z.string().default(''),
  appendError: z.boolean().default(false),
  appendException: z.boolean().default(false),
  createOneInstancePerSample: z.boolean().default(false),
});
```

---

## 2. Missing Config Elements

### 2.1 HTTP Cache Manager

| Field | Value |
|-------|-------|
| **testclass** | `CacheManager` |
| **guiclass** | `CacheManagerGui` |
| **Category** | Config Element |
| **Description** | Simulates browser HTTP caching behavior. Caches responses based on Cache-Control, Expires, ETag, and Last-Modified headers. Sends If-None-Match / If-Modified-Since headers on subsequent requests. Critical for realistic browser simulation. |
| **Priority** | P1 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<CacheManager guiclass="CacheManagerGui" testclass="CacheManager" testname="HTTP Cache Manager" enabled="true">
  <boolProp name="clearEachIteration">false</boolProp>
  <boolProp name="useExpires">true</boolProp>
  <intProp name="maxSize">5000</intProp>
  <boolProp name="controlledByThread">false</boolProp>
</CacheManager>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `clearEachIteration` | boolProp | `false` | Clear cache at start of each iteration |
| `useExpires` | boolProp | `true` | Honor Expires/Cache-Control max-age headers |
| `maxSize` | intProp | `5000` | Maximum number of cached entries |
| `controlledByThread` | boolProp | `false` | Each VU maintains its own cache |

**Implementation Approach:**
Implement as a per-VU `ConcurrentHashMap<String, CacheEntry>` where `CacheEntry` stores URL, ETag, Last-Modified, expiry timestamp, and response bytes. Before HTTP requests, check cache: if valid entry exists and not expired, return cached response (304 simulation). If expired but has ETag/Last-Modified, add conditional headers. Integrate into `HttpSamplerExecutor` via a cache lookup hook.

**UI Schema (Zod):**
```typescript
export const cacheManagerSchema = z.object({
  clearEachIteration: z.boolean().default(false),
  useExpires: z.boolean().default(true),
  maxSize: z.number().int().min(1).default(5000),
  controlledByThread: z.boolean().default(false),
});
```

---

### 2.2 HTTP Authorization Manager

| Field | Value |
|-------|-------|
| **testclass** | `AuthManager` |
| **guiclass** | `AuthPanel` |
| **Category** | Config Element |
| **Description** | Manages HTTP authentication credentials (Basic, Digest, Kerberos). Automatically adds Authorization headers to matching requests. Supports multiple URL-credential pairs. |
| **Priority** | P1 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<AuthManager guiclass="AuthPanel" testclass="AuthManager" testname="HTTP Authorization Manager" enabled="true">
  <collectionProp name="AuthManager.auth_list">
    <elementProp name="" elementType="Authorization">
      <stringProp name="Authorization.url">http://example.com</stringProp>
      <stringProp name="Authorization.username">user</stringProp>
      <stringProp name="Authorization.password">pass</stringProp>
      <stringProp name="Authorization.domain"></stringProp>
      <stringProp name="Authorization.realm"></stringProp>
      <stringProp name="Authorization.mechanism">BASIC</stringProp>
    </elementProp>
  </collectionProp>
  <boolProp name="AuthManager.clearEachIteration">false</boolProp>
  <boolProp name="AuthManager.controlledByThreadGroup">false</boolProp>
</AuthManager>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `AuthManager.auth_list` | collectionProp | `[]` | List of Authorization entries |
| `Authorization.url` | stringProp | `""` | URL pattern to match |
| `Authorization.username` | stringProp | `""` | Username |
| `Authorization.password` | stringProp | `""` | Password |
| `Authorization.domain` | stringProp | `""` | NTLM domain |
| `Authorization.realm` | stringProp | `""` | Authentication realm |
| `Authorization.mechanism` | stringProp | `"BASIC"` | Mechanism: BASIC, DIGEST, KERBEROS |
| `AuthManager.clearEachIteration` | boolProp | `false` | Clear credentials each iteration |
| `AuthManager.controlledByThreadGroup` | boolProp | `false` | Scope to thread group |

**Implementation Approach:**
Maintain a list of `(urlPattern, username, password, mechanism)` entries. Before each HTTP request, match the request URL against patterns. For BASIC: `Base64.getEncoder().encodeToString((user+":"+pass).getBytes())` in `Authorization: Basic ...` header. For DIGEST: parse `WWW-Authenticate` header and compute `java.security.MessageDigest` MD5-based response. For KERBEROS: use `javax.security.auth.login.LoginContext` with `com.sun.security.auth.module.Krb5LoginModule`.

**UI Schema (Zod):**
```typescript
export const authManagerSchema = z.object({
  authList: z.array(z.object({
    url: z.string().default(''),
    username: z.string().default(''),
    password: z.string().default(''),
    domain: z.string().default(''),
    realm: z.string().default(''),
    mechanism: z.enum(['BASIC', 'DIGEST', 'KERBEROS']).default('BASIC'),
  })).default([]),
  clearEachIteration: z.boolean().default(false),
  controlledByThreadGroup: z.boolean().default(false),
});
```

---

### 2.3 Random Variable

| Field | Value |
|-------|-------|
| **testclass** | `RandomVariableConfig` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Config Element |
| **Description** | Generates a random number within a specified range and stores it in a JMeter variable. The value is computed per thread per iteration. Used for parameterizing requests with random data. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<RandomVariableConfig guiclass="TestBeanGUI" testclass="RandomVariableConfig" testname="Random Variable" enabled="true">
  <stringProp name="variableName">randVar</stringProp>
  <stringProp name="minimumValue">1</stringProp>
  <stringProp name="maximumValue">100</stringProp>
  <stringProp name="outputFormat"></stringProp>
  <boolProp name="perThread">true</boolProp>
  <stringProp name="randomSeed"></stringProp>
</RandomVariableConfig>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `variableName` | stringProp | `""` | Variable name to store result |
| `minimumValue` | stringProp | `"1"` | Minimum value (inclusive) |
| `maximumValue` | stringProp | `"100"` | Maximum value (inclusive) |
| `outputFormat` | stringProp | `""` | DecimalFormat pattern (empty = plain number) |
| `perThread` | boolProp | `true` | Each VU gets independent random sequence |
| `randomSeed` | stringProp | `""` | Seed for reproducible sequences (empty = random) |

**Implementation Approach:**
Use `java.util.concurrent.ThreadLocalRandom.current().nextLong(min, max+1)`. If outputFormat is set, use `java.text.DecimalFormat`. If seed is set, use `new java.util.Random(seed)` per VU. Store result in VU variable map. Execute during config element phase (before samplers).

**UI Schema (Zod):**
```typescript
export const randomVariableSchema = z.object({
  variableName: z.string().default(''),
  minimumValue: z.string().default('1'),
  maximumValue: z.string().default('100'),
  outputFormat: z.string().default(''),
  perThread: z.boolean().default(true),
  randomSeed: z.string().default(''),
});
```

---

### 2.4 Counter

| Field | Value |
|-------|-------|
| **testclass** | `CounterConfig` |
| **guiclass** | `CounterConfigGui` |
| **Category** | Config Element |
| **Description** | Generates a counter (incrementing/decrementing number) and stores it in a variable. Can be shared across VUs or per-thread. Used for sequential ID generation. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<CounterConfig guiclass="CounterConfigGui" testclass="CounterConfig" testname="Counter" enabled="true">
  <stringProp name="CounterConfig.start">1</stringProp>
  <stringProp name="CounterConfig.end"></stringProp>
  <stringProp name="CounterConfig.incr">1</stringProp>
  <stringProp name="CounterConfig.name">counter</stringProp>
  <stringProp name="CounterConfig.format"></stringProp>
  <boolProp name="CounterConfig.per_user">false</boolProp>
  <boolProp name="CounterConfig.reset_on_tg_iteration">false</boolProp>
</CounterConfig>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `CounterConfig.start` | stringProp | `"1"` | Starting value |
| `CounterConfig.end` | stringProp | `""` | Maximum value (empty = Long.MAX_VALUE) |
| `CounterConfig.incr` | stringProp | `"1"` | Increment per iteration |
| `CounterConfig.name` | stringProp | `""` | Variable name to store counter |
| `CounterConfig.format` | stringProp | `""` | DecimalFormat pattern |
| `CounterConfig.per_user` | boolProp | `false` | Per-thread counter (vs shared) |
| `CounterConfig.reset_on_tg_iteration` | boolProp | `false` | Reset counter when ThreadGroup loop restarts |

**Implementation Approach:**
Use `java.util.concurrent.atomic.AtomicLong` for shared counter (thread-safe increment). For per-user counters, store `long` in VU variable map. Format with `java.text.DecimalFormat` if pattern is set. Wrap around to start when end is reached.

**UI Schema (Zod):**
```typescript
export const counterSchema = z.object({
  start: z.string().default('1'),
  end: z.string().default(''),
  increment: z.string().default('1'),
  variableName: z.string().default('counter'),
  format: z.string().default(''),
  perUser: z.boolean().default(false),
  resetOnThreadGroupIteration: z.boolean().default(false),
});
```

---

### 2.5 DNS Cache Manager

| Field | Value |
|-------|-------|
| **testclass** | `DNSCacheManager` |
| **guiclass** | `DNSCachePanel` |
| **Category** | Config Element |
| **Description** | Controls DNS resolution for HTTP requests. Can use custom DNS servers or static host-to-IP mappings. Simulates DNS caching behavior and enables testing with specific DNS configurations. |
| **Priority** | P2 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<DNSCacheManager guiclass="DNSCachePanel" testclass="DNSCacheManager" testname="DNS Cache Manager" enabled="true">
  <collectionProp name="DNSCacheManager.servers">
    <stringProp name="">8.8.8.8</stringProp>
  </collectionProp>
  <collectionProp name="DNSCacheManager.hosts">
    <elementProp name="example.com" elementType="StaticHost">
      <stringProp name="StaticHost.Name">example.com</stringProp>
      <stringProp name="StaticHost.Address">192.168.1.100</stringProp>
    </elementProp>
  </collectionProp>
  <boolProp name="DNSCacheManager.clearEachIteration">false</boolProp>
  <boolProp name="DNSCacheManager.isCustomResolver">false</boolProp>
</DNSCacheManager>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `DNSCacheManager.servers` | collectionProp | `[]` | List of DNS server IP addresses |
| `DNSCacheManager.hosts` | collectionProp | `[]` | Static host-to-IP mappings |
| `StaticHost.Name` | stringProp | `""` | Hostname |
| `StaticHost.Address` | stringProp | `""` | IP address |
| `DNSCacheManager.clearEachIteration` | boolProp | `false` | Clear DNS cache each iteration |
| `DNSCacheManager.isCustomResolver` | boolProp | `false` | Use custom DNS resolver |

**Implementation Approach:**
For static mappings: maintain a `Map<String, String>` of hostname-to-IP and inject a custom `java.net.spi.InetAddressResolverProvider` (JDK 18+) or configure `HttpClient` with custom DNS resolution. For custom DNS servers: use `javax.naming.directory.InitialDirContext` with `java.naming.provider.url=dns://8.8.8.8` to resolve A records manually.

**UI Schema (Zod):**
```typescript
export const dnsCacheManagerSchema = z.object({
  servers: z.array(z.string()).default([]),
  hosts: z.array(z.object({
    name: z.string().default(''),
    address: z.string().default(''),
  })).default([]),
  clearEachIteration: z.boolean().default(false),
  isCustomResolver: z.boolean().default(false),
});
```

---

### 2.6 Keystore Configuration

| Field | Value |
|-------|-------|
| **testclass** | `KeystoreConfig` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Config Element |
| **Description** | Configures client-side SSL/TLS keystores for mutual TLS (mTLS) authentication. Specifies which client certificates to use for HTTPS requests. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<KeystoreConfig guiclass="TestBeanGUI" testclass="KeystoreConfig" testname="Keystore Configuration" enabled="true">
  <stringProp name="preload">true</stringProp>
  <stringProp name="startIndex">0</stringProp>
  <stringProp name="endIndex">-1</stringProp>
  <stringProp name="clientCertAliasVarName"></stringProp>
</KeystoreConfig>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `preload` | stringProp | `"true"` | Preload keystore at test start |
| `startIndex` | stringProp | `"0"` | Start index of certificate alias range |
| `endIndex` | stringProp | `"-1"` | End index (-1 = all) |
| `clientCertAliasVarName` | stringProp | `""` | Variable name containing cert alias to use |

**Implementation Approach:**
Use `java.security.KeyStore.getInstance("JKS")` / `"PKCS12"` to load keystore from system properties (`javax.net.ssl.keyStore`, `javax.net.ssl.keyStorePassword`). Create `javax.net.ssl.KeyManagerFactory` and configure `SSLContext` with the selected client certificate. Pass SSLContext to `HttpClient.Builder.sslContext()`.

**UI Schema (Zod):**
```typescript
export const keystoreConfigSchema = z.object({
  preload: z.boolean().default(true),
  startIndex: z.string().default('0'),
  endIndex: z.string().default('-1'),
  clientCertAliasVarName: z.string().default(''),
});
```

---

### 2.7 Login Config Element

| Field | Value |
|-------|-------|
| **testclass** | `LoginConfig` |
| **guiclass** | `LoginConfigGui` |
| **Category** | Config Element |
| **Description** | Stores username and password for form-based or basic authentication. Provides credentials to samplers that need login information. |
| **Priority** | P3 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<LoginConfig guiclass="LoginConfigGui" testclass="LoginConfig" testname="Login Config Element" enabled="true">
  <stringProp name="LoginConfig.username">admin</stringProp>
  <stringProp name="LoginConfig.password">secret</stringProp>
</LoginConfig>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `LoginConfig.username` | stringProp | `""` | Username |
| `LoginConfig.password` | stringProp | `""` | Password |

**Implementation Approach:**
Simple config element that stores username/password in VU variables as `LoginConfig.username` and `LoginConfig.password`. Other elements read from these variables. Pure variable map population -- no external API needed.

**UI Schema (Zod):**
```typescript
export const loginConfigSchema = z.object({
  username: z.string().default(''),
  password: z.string().default(''),
});
```

---

### 2.8 LDAP Request Defaults

| Field | Value |
|-------|-------|
| **testclass** | `LDAPArguments` |
| **guiclass** | `LdapConfigGui` |
| **Category** | Config Element |
| **Description** | Provides default values for LDAP sampler properties. Shared configuration applied to all LDAP samplers in the same scope. |
| **Priority** | P3 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<ConfigTestElement guiclass="LdapConfigGui" testclass="ConfigTestElement" testname="LDAP Request Defaults" enabled="true">
  <stringProp name="servername">ldap.example.com</stringProp>
  <stringProp name="port">389</stringProp>
  <stringProp name="rootdn">dc=example,dc=com</stringProp>
  <stringProp name="user_dn"></stringProp>
  <stringProp name="user_pw"></stringProp>
  <stringProp name="base_entry_dn"></stringProp>
</ConfigTestElement>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `servername` | stringProp | `""` | LDAP server hostname |
| `port` | stringProp | `"389"` | LDAP port |
| `rootdn` | stringProp | `""` | Root distinguished name |
| `user_dn` | stringProp | `""` | Bind DN for authentication |
| `user_pw` | stringProp | `""` | Bind password |
| `base_entry_dn` | stringProp | `""` | Base entry for search operations |

**Implementation Approach:**
Config default mechanism: store properties in VU variable map with `LDAPSampler.` prefix. When LDAPSamplerExecutor runs, check VU variables for defaults if sampler-level properties are empty.

**UI Schema (Zod):**
```typescript
export const ldapDefaultsSchema = z.object({
  servername: z.string().default(''),
  port: z.string().default('389'),
  rootdn: z.string().default(''),
  userDn: z.string().default(''),
  userPw: z.string().default(''),
  baseEntryDn: z.string().default(''),
});
```

---

### 2.9 JDBC Connection Configuration

| Field | Value |
|-------|-------|
| **testclass** | `JDBCDataSource` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Config Element |
| **Description** | Configures a JDBC connection pool with a named data source. Must be present before JDBC Request samplers can execute. Manages connection pooling, validation, and lifecycle. Critical dependency for JDBC testing. |
| **Priority** | P1 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<JDBCDataSource guiclass="TestBeanGUI" testclass="JDBCDataSource" testname="JDBC Connection Configuration" enabled="true">
  <stringProp name="dataSource">myDatabase</stringProp>
  <stringProp name="dbUrl">jdbc:mysql://localhost:3306/testdb</stringProp>
  <stringProp name="driver">com.mysql.cj.jdbc.Driver</stringProp>
  <stringProp name="username">root</stringProp>
  <stringProp name="password"></stringProp>
  <stringProp name="checkQuery">SELECT 1</stringProp>
  <stringProp name="autocommit">true</stringProp>
  <stringProp name="connectionAge">5000</stringProp>
  <stringProp name="connectionProperties"></stringProp>
  <stringProp name="initQuery"></stringProp>
  <stringProp name="keepAlive">true</stringProp>
  <stringProp name="maxActive">10</stringProp>
  <stringProp name="maxIdle">10</stringProp>
  <stringProp name="maxWait">10000</stringProp>
  <stringProp name="poolMax">10</stringProp>
  <stringProp name="timeout">10000</stringProp>
  <stringProp name="transactionIsolation">DEFAULT</stringProp>
  <boolProp name="preinit">false</boolProp>
</JDBCDataSource>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `dataSource` | stringProp | `""` | Variable name to reference this connection pool |
| `dbUrl` | stringProp | `""` | JDBC connection URL |
| `driver` | stringProp | `""` | JDBC driver class name |
| `username` | stringProp | `""` | Database username |
| `password` | stringProp | `""` | Database password |
| `checkQuery` | stringProp | `"SELECT 1"` | Validation query |
| `autocommit` | stringProp | `"true"` | Auto-commit mode |
| `connectionAge` | stringProp | `"5000"` | Max connection age (ms) |
| `connectionProperties` | stringProp | `""` | Additional JDBC properties |
| `initQuery` | stringProp | `""` | Query to run on new connections |
| `keepAlive` | stringProp | `"true"` | Keep connections alive |
| `maxActive` | stringProp | `"10"` | Maximum active connections |
| `maxIdle` | stringProp | `"10"` | Maximum idle connections |
| `maxWait` | stringProp | `"10000"` | Maximum wait for connection (ms) |
| `poolMax` | stringProp | `"10"` | Pool maximum size |
| `timeout` | stringProp | `"10000"` | Connection timeout (ms) |
| `transactionIsolation` | stringProp | `"DEFAULT"` | Transaction isolation level |
| `preinit` | boolProp | `false` | Pre-initialize pool at test start |

**Implementation Approach:**
Use pure JDK JDBC: `java.sql.DriverManager.getConnection(url, user, pass)` with a connection pool backed by `java.util.concurrent.LinkedBlockingQueue<Connection>`. Register named pools in a `ConcurrentHashMap<String, ConnectionPool>` keyed by dataSource name. JDBCSamplerExecutor retrieves connections from this map. Validate with checkQuery before returning connections.

**UI Schema (Zod):**
```typescript
export const jdbcConnectionSchema = z.object({
  dataSource: z.string().default(''),
  dbUrl: z.string().default(''),
  driver: z.string().default(''),
  username: z.string().default(''),
  password: z.string().default(''),
  checkQuery: z.string().default('SELECT 1'),
  autocommit: z.boolean().default(true),
  connectionAge: z.string().default('5000'),
  connectionProperties: z.string().default(''),
  initQuery: z.string().default(''),
  keepAlive: z.boolean().default(true),
  maxActive: z.string().default('10'),
  maxIdle: z.string().default('10'),
  maxWait: z.string().default('10000'),
  poolMax: z.string().default('10'),
  timeout: z.string().default('10000'),
  transactionIsolation: z.enum(['DEFAULT', 'TRANSACTION_NONE', 'TRANSACTION_READ_UNCOMMITTED', 'TRANSACTION_READ_COMMITTED', 'TRANSACTION_REPEATABLE_READ', 'TRANSACTION_SERIALIZABLE']).default('DEFAULT'),
  preinit: z.boolean().default(false),
});
```

---

### 2.10 Simple Config Element

| Field | Value |
|-------|-------|
| **testclass** | `ConfigTestElement` |
| **guiclass** | `SimpleConfigGui` |
| **Category** | Config Element |
| **Description** | A generic config element that holds arbitrary string properties. Used as a default-values provider for samplers of the same type in the same scope. |
| **Priority** | P3 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<ConfigTestElement guiclass="SimpleConfigGui" testclass="ConfigTestElement" testname="Simple Config Element" enabled="true">
  <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
    <collectionProp name="Arguments.arguments"/>
  </elementProp>
</ConfigTestElement>
```

**JMX Properties:**
Dynamic -- stores arbitrary properties that merge into sampler defaults.

**Implementation Approach:**
Transparent config merge: iterate all properties of ConfigTestElement and set them as default values in VU variable map. Samplers use variable map lookups for defaults.

**UI Schema (Zod):**
```typescript
export const simpleConfigSchema = z.object({
  properties: z.record(z.string()).default({}),
});
```

---

## 3. Missing Controllers

### 3.1 ForEach Controller

| Field | Value |
|-------|-------|
| **testclass** | `ForeachController` |
| **guiclass** | `ForeachControlPanel` |
| **Category** | Controller |
| **Description** | Iterates over a set of variables named `PREFIX_1`, `PREFIX_2`, ..., `PREFIX_N`, setting a loop variable for each iteration. Commonly used with Regular Expression Extractor (matchNr) to process all matches. |
| **Priority** | P1 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<ForeachController guiclass="ForeachControlPanel" testclass="ForeachController" testname="ForEach Controller" enabled="true">
  <stringProp name="ForeachController.inputVal">inputVar</stringProp>
  <stringProp name="ForeachController.returnVal">returnVar</stringProp>
  <boolProp name="ForeachController.useSeparator">true</boolProp>
  <stringProp name="ForeachController.startIndex">0</stringProp>
  <stringProp name="ForeachController.endIndex"></stringProp>
</ForeachController>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ForeachController.inputVal` | stringProp | `""` | Input variable prefix (looks for PREFIX_1, PREFIX_2, etc.) |
| `ForeachController.returnVal` | stringProp | `""` | Variable name to store current value |
| `ForeachController.useSeparator` | boolProp | `true` | Use underscore separator (PREFIX_1 vs PREFIX1) |
| `ForeachController.startIndex` | stringProp | `"0"` | Start index (0-based, first checked is start+1) |
| `ForeachController.endIndex` | stringProp | `""` | End index (empty = iterate until variable not found) |

**Implementation Approach:**
In `ForEachControllerExecutor.execute()`: read startIndex, iterate from `startIndex+1` upward. For each index `i`, look up `inputVal + (useSeparator ? "_" : "") + i` in VU variable map. If found, set `returnVal` to the value and execute children. Stop when variable is not found or endIndex is reached.

**UI Schema (Zod):**
```typescript
export const forEachControllerSchema = z.object({
  inputVariable: z.string().default(''),
  returnVariable: z.string().default(''),
  useSeparator: z.boolean().default(true),
  startIndex: z.string().default('0'),
  endIndex: z.string().default(''),
});
```

---

### 3.2 Module Controller

| Field | Value |
|-------|-------|
| **testclass** | `ModuleController` |
| **guiclass** | `ModuleControllerGui` |
| **Category** | Controller |
| **Description** | References another controller (by tree path) in the test plan and executes it. Enables reuse of test fragments across multiple Thread Groups. |
| **Priority** | P2 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<ModuleController guiclass="ModuleControllerGui" testclass="ModuleController" testname="Module Controller" enabled="true">
  <collectionProp name="ModuleController.node_path">
    <stringProp name="">Test Plan</stringProp>
    <stringProp name="">Thread Group</stringProp>
    <stringProp name="">Simple Controller</stringProp>
  </collectionProp>
</ModuleController>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ModuleController.node_path` | collectionProp | `[]` | Tree path to target controller (list of ancestor names) |

**Implementation Approach:**
Walk the plan tree from root following the name path in `node_path` collection. When the target node is found, execute its children using `NodeInterpreter.executeChildren()`. Cache the resolved node reference for performance.

**UI Schema (Zod):**
```typescript
export const moduleControllerSchema = z.object({
  nodePath: z.array(z.string()).default([]),
});
```

---

### 3.3 Include Controller

| Field | Value |
|-------|-------|
| **testclass** | `IncludeController` |
| **guiclass** | `IncludeControllerGui` |
| **Category** | Controller |
| **Description** | Includes and executes an external JMX test fragment file. The fragment must contain a Test Fragment (not a Test Plan) as root element. |
| **Priority** | P2 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<IncludeController guiclass="IncludeControllerGui" testclass="IncludeController" testname="Include Controller" enabled="true">
  <stringProp name="IncludeController.includepath">fragment.jmx</stringProp>
</IncludeController>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `IncludeController.includepath` | stringProp | `""` | Path to JMX fragment file (relative or absolute) |

**Implementation Approach:**
Parse the referenced JMX file using existing `JmxTreeWalker`. Extract children from the `TestFragment` root node. Execute children using `NodeInterpreter.executeChildren()`. Cache parsed tree to avoid re-parsing on each iteration.

**UI Schema (Zod):**
```typescript
export const includeControllerSchema = z.object({
  includePath: z.string().default(''),
});
```

---

### 3.4 Switch Controller

| Field | Value |
|-------|-------|
| **testclass** | `SwitchController` |
| **guiclass** | `SwitchControllerGui` |
| **Category** | Controller |
| **Description** | Executes one child element based on a switch value. Value can be numeric (0-based index) or a name matching a child element's name. Acts like a switch/case statement. |
| **Priority** | P1 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<SwitchController guiclass="SwitchControllerGui" testclass="SwitchController" testname="Switch Controller" enabled="true">
  <stringProp name="SwitchController.value">0</stringProp>
</SwitchController>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `SwitchController.value` | stringProp | `"0"` | Switch value: numeric index or child name |

**Implementation Approach:**
Resolve variable references in value string. If numeric, use as 0-based index into children list. If non-numeric, find first child whose testName matches the value. Execute only that child's subtree via `dispatchNode()`. If no match, execute child at index 0 (default case).

**UI Schema (Zod):**
```typescript
export const switchControllerSchema = z.object({
  value: z.string().default('0'),
});
```

---

### 3.5 Runtime Controller

| Field | Value |
|-------|-------|
| **testclass** | `RunTime` |
| **guiclass** | `RunTimeGui` |
| **Category** | Controller |
| **Description** | Executes its children for a specified number of seconds. Children loop until the time expires. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<RunTime guiclass="RunTimeGui" testclass="RunTime" testname="Runtime Controller" enabled="true">
  <stringProp name="RunTime.seconds">10</stringProp>
</RunTime>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `RunTime.seconds` | stringProp | `"10"` | Duration to run children (seconds) |

**Implementation Approach:**
Record `System.currentTimeMillis()` at start. Loop executing children via `executeChildren()` until elapsed time exceeds `seconds * 1000`. Check elapsed time between child executions.

**UI Schema (Zod):**
```typescript
export const runtimeControllerSchema = z.object({
  seconds: z.string().default('10'),
});
```

---

### 3.6 Random Controller

| Field | Value |
|-------|-------|
| **testclass** | `RandomController` |
| **guiclass** | `RandomControlGui` |
| **Category** | Controller |
| **Description** | Executes exactly one randomly selected child per iteration. Different child is chosen each time. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<RandomController guiclass="RandomControlGui" testclass="RandomController" testname="Random Controller" enabled="true">
  <intProp name="InterleaveControl.style">1</intProp>
</RandomController>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `InterleaveControl.style` | intProp | `1` | Ignored for Random (used by Interleave) |

**Implementation Approach:**
Use `ThreadLocalRandom.current().nextInt(children.size())` to pick a random index. Execute only that child via `dispatchNode()`.

**UI Schema (Zod):**
```typescript
export const randomControllerSchema = z.object({});
```

---

### 3.7 Throughput Controller

| Field | Value |
|-------|-------|
| **testclass** | `ThroughputController` |
| **guiclass** | `ThroughputControllerGui` |
| **Category** | Controller |
| **Description** | Controls what percentage or total count of iterations execute its children. In "Percent Executions" mode, children execute X% of the time. In "Total Executions" mode, children execute exactly N times total. |
| **Priority** | P1 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<ThroughputController guiclass="ThroughputControllerGui" testclass="ThroughputController" testname="Throughput Controller" enabled="true">
  <intProp name="ThroughputController.style">0</intProp>
  <boolProp name="ThroughputController.perThread">false</boolProp>
  <intProp name="ThroughputController.maxThroughput">1</intProp>
  <FloatProperty>
    <name>ThroughputController.percentThroughput</name>
    <value>100.0</value>
  </FloatProperty>
</ThroughputController>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ThroughputController.style` | intProp | `0` | 0 = Percent Executions, 1 = Total Executions |
| `ThroughputController.perThread` | boolProp | `false` | Apply count/percent per thread (vs globally) |
| `ThroughputController.maxThroughput` | intProp | `1` | Total number of executions (style=1) |
| `ThroughputController.percentThroughput` | FloatProperty | `100.0` | Percentage of iterations to execute (style=0) |

**Implementation Approach:**
For percent mode: generate `ThreadLocalRandom.current().nextDouble(100.0)`, execute children if result < percentThroughput. For total mode: use `AtomicInteger` counter (shared) or per-thread counter, execute children if counter < maxThroughput, increment counter.

**UI Schema (Zod):**
```typescript
export const throughputControllerSchema = z.object({
  style: z.enum(['0', '1']).default('0'),
  perThread: z.boolean().default(false),
  maxThroughput: z.number().int().min(1).default(1),
  percentThroughput: z.number().min(0).max(100).default(100.0),
});
```

---

### 3.8 Once Only Controller

| Field | Value |
|-------|-------|
| **testclass** | `OnceOnlyController` |
| **guiclass** | `OnceOnlyControllerGui` |
| **Category** | Controller |
| **Description** | Executes its children only on the first iteration of each thread. Subsequent loop iterations skip this controller. Commonly used for login/setup operations. |
| **Priority** | P1 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<OnceOnlyController guiclass="OnceOnlyControllerGui" testclass="OnceOnlyController" testname="Once Only Controller" enabled="true"/>
```

**JMX Properties:**
None -- no configurable properties.

**Implementation Approach:**
Track execution state per VU using a `Set<String>` of controller IDs in the VU variable map (key: `__once_only_executed`). On first encounter, add controller ID to set and execute children. On subsequent encounters, check set and skip children.

**UI Schema (Zod):**
```typescript
export const onceOnlyControllerSchema = z.object({});
```

---

### 3.9 Interleave Controller

| Field | Value |
|-------|-------|
| **testclass** | `InterleaveControl` |
| **guiclass** | `InterleaveControlGui` |
| **Category** | Controller |
| **Description** | Executes one child per iteration, cycling through children in order (round-robin). Different from Random Controller which picks randomly. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<InterleaveControl guiclass="InterleaveControlGui" testclass="InterleaveControl" testname="Interleave Controller" enabled="true">
  <intProp name="InterleaveControl.style">0</intProp>
  <boolProp name="InterleaveControl.accrossThreads">false</boolProp>
</InterleaveControl>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `InterleaveControl.style` | intProp | `0` | 0 = simple interleave, 1 = interleave across sub-controllers |
| `InterleaveControl.accrossThreads` | boolProp | `false` | Share counter across all VUs (note: typo is intentional -- matches JMeter source) |

**Implementation Approach:**
Maintain a per-VU counter (or shared `AtomicInteger` if `accrossThreads=true`). Each iteration, execute child at index `counter % children.size()`, then increment counter.

**UI Schema (Zod):**
```typescript
export const interleaveControllerSchema = z.object({
  style: z.number().int().default(0),
  accrossThreads: z.boolean().default(false),
});
```

---

### 3.10 Random Order Controller

| Field | Value |
|-------|-------|
| **testclass** | `RandomOrderController` |
| **guiclass** | `RandomOrderControllerGui` |
| **Category** | Controller |
| **Description** | Executes ALL children, but in a random order each iteration. Unlike Random Controller (which picks one), this executes all. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<RandomOrderController guiclass="RandomOrderControllerGui" testclass="RandomOrderController" testname="Random Order Controller" enabled="true"/>
```

**JMX Properties:**
None -- no configurable properties.

**Implementation Approach:**
Copy children list, shuffle with `java.util.Collections.shuffle(list, ThreadLocalRandom.current())`, then execute all children in shuffled order via `executeChildren()`.

**UI Schema (Zod):**
```typescript
export const randomOrderControllerSchema = z.object({});
```

---

### 3.11 Recording Controller

| Field | Value |
|-------|-------|
| **testclass** | `RecordingController` |
| **guiclass** | `RecordController` |
| **Category** | Controller |
| **Description** | Container for recorded HTTP requests. The HTTP(S) Test Script Recorder places captured requests into this controller. Acts as a Simple Controller during execution. |
| **Priority** | P3 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<RecordingController guiclass="RecordController" testclass="RecordingController" testname="Recording Controller" enabled="true"/>
```

**JMX Properties:**
None -- transparent container.

**Implementation Approach:**
Identical to TransactionController/SimpleController: transparent wrapper that executes children in sequence. Register `RecordingController` testclass in NodeInterpreter dispatch as a transparent wrapper case.

**UI Schema (Zod):**
```typescript
export const recordingControllerSchema = z.object({});
```

---

## 4. Missing Assertions

### 4.1 JSON Assertion

| Field | Value |
|-------|-------|
| **testclass** | `JSONPathAssertion` |
| **guiclass** | `JSONPathAssertionGui` |
| **Category** | Assertion |
| **Description** | Validates JSON response using JSONPath expressions. Checks that a JSONPath exists, matches a value, or matches a regex pattern. The primary assertion for REST API testing. |
| **Priority** | P1 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<JSONPathAssertion guiclass="JSONPathAssertionGui" testclass="JSONPathAssertion" testname="JSON Assertion" enabled="true">
  <stringProp name="JSON_PATH">$.status</stringProp>
  <stringProp name="EXPECTED_VALUE">ok</stringProp>
  <boolProp name="JSONVALIDATION">true</boolProp>
  <boolProp name="EXPECT_NULL">false</boolProp>
  <boolProp name="INVERT">false</boolProp>
  <boolProp name="ISREGEX">false</boolProp>
</JSONPathAssertion>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `JSON_PATH` | stringProp | `""` | JSONPath expression to evaluate |
| `EXPECTED_VALUE` | stringProp | `""` | Expected value to match |
| `JSONVALIDATION` | boolProp | `true` | Validate value (false = just check path exists) |
| `EXPECT_NULL` | boolProp | `false` | Expect null value |
| `INVERT` | boolProp | `false` | Invert assertion result |
| `ISREGEX` | boolProp | `false` | Treat expected value as regex |

**Implementation Approach:**
Reuse the JSONPath evaluation already in `ExtractorExecutor` (which handles JSONPostProcessor). Parse response body as JSON, evaluate JSONPath, compare result against expected value (string equality or `java.util.regex.Pattern` match). Set assertion result on SampleResult.

**UI Schema (Zod):**
```typescript
export const jsonAssertionSchema = z.object({
  jsonPath: z.string().default(''),
  expectedValue: z.string().default(''),
  jsonValidation: z.boolean().default(true),
  expectNull: z.boolean().default(false),
  invert: z.boolean().default(false),
  isRegex: z.boolean().default(false),
});
```

---

### 4.2 JSON Path Assertion

| Field | Value |
|-------|-------|
| **testclass** | `JSONPathAssertion2` |
| **guiclass** | `JSONPathAssertionGui2` |
| **Category** | Assertion |
| **Description** | Alternative JSON assertion using a different JSONPath library. Functionally similar to JSON Assertion but with slightly different path syntax. In practice, this is rarely used separately from 4.1. |
| **Priority** | P3 |
| **Effort** | S |

**Note:** This can be implemented as an alias to the JSONPathAssertion (4.1) since the property names are identical. Register `JSONPathAssertion2` in NodeInterpreter dispatch to the same assertion handler.

---

### 4.3 XPath Assertion

| Field | Value |
|-------|-------|
| **testclass** | `XPathAssertion` |
| **guiclass** | `XPathAssertionGui` |
| **Category** | Assertion |
| **Description** | Validates XML/HTML response using XPath expressions. Checks that an XPath evaluates to true, or that matching nodes exist. Essential for SOAP/XML API testing. |
| **Priority** | P1 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<XPathAssertion guiclass="XPathAssertionGui" testclass="XPathAssertion" testname="XPath Assertion" enabled="true">
  <stringProp name="XPath.xpath">/bookstore/book[1]/title</stringProp>
  <boolProp name="XPath.validate">false</boolProp>
  <boolProp name="XPath.whitespace">false</boolProp>
  <boolProp name="XPath.tolerant">false</boolProp>
  <boolProp name="XPath.negate">false</boolProp>
  <boolProp name="XPath.namespace">false</boolProp>
  <boolProp name="XPath.downloadDTDs">false</boolProp>
</XPathAssertion>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `XPath.xpath` | stringProp | `""` | XPath expression |
| `XPath.validate` | boolProp | `false` | Validate XML against DTD |
| `XPath.whitespace` | boolProp | `false` | Ignore whitespace |
| `XPath.tolerant` | boolProp | `false` | Use Tidy for malformed HTML |
| `XPath.negate` | boolProp | `false` | Invert assertion |
| `XPath.namespace` | boolProp | `false` | Enable namespace processing |
| `XPath.downloadDTDs` | boolProp | `false` | Download external DTDs |

**Implementation Approach:**
Use `javax.xml.parsers.DocumentBuilderFactory` + `javax.xml.xpath.XPathFactory` (both in JDK). Parse response as XML Document, evaluate XPath expression. If result is boolean, check true/false. If result is NodeList, check non-empty. Apply negate flag.

**UI Schema (Zod):**
```typescript
export const xpathAssertionSchema = z.object({
  xpath: z.string().default(''),
  validate: z.boolean().default(false),
  whitespace: z.boolean().default(false),
  tolerant: z.boolean().default(false),
  negate: z.boolean().default(false),
  namespace: z.boolean().default(false),
  downloadDTDs: z.boolean().default(false),
});
```

---

### 4.4 XML Assertion

| Field | Value |
|-------|-------|
| **testclass** | `XMLAssertion` |
| **guiclass** | `XMLAssertionGui` |
| **Category** | Assertion |
| **Description** | Validates that the response is well-formed XML. No XPath evaluation -- simply checks parsability. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<XMLAssertion guiclass="XMLAssertionGui" testclass="XMLAssertion" testname="XML Assertion" enabled="true"/>
```

**JMX Properties:**
None.

**Implementation Approach:**
Try to parse response body with `javax.xml.parsers.DocumentBuilder.parse(new InputSource(new StringReader(body)))`. Success = assertion passes. SAXException = assertion fails with parse error message.

**UI Schema (Zod):**
```typescript
export const xmlAssertionSchema = z.object({});
```

---

### 4.5 BeanShell Assertion

| Field | Value |
|-------|-------|
| **testclass** | `BeanShellAssertion` |
| **guiclass** | `BeanShellAssertionGui` |
| **Category** | Assertion |
| **Description** | Runs a BeanShell script to evaluate assertion result. Script has access to `SampleResult`, `Response`, `Failure`, `FailureMessage` variables. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<BeanShellAssertion guiclass="BeanShellAssertionGui" testclass="BeanShellAssertion" testname="BeanShell Assertion" enabled="true">
  <stringProp name="BeanShellAssertion.query"></stringProp>
  <stringProp name="BeanShellAssertion.filename"></stringProp>
  <stringProp name="BeanShellAssertion.parameters"></stringProp>
  <boolProp name="BeanShellAssertion.resetInterpreter">false</boolProp>
</BeanShellAssertion>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `BeanShellAssertion.query` | stringProp | `""` | Inline script |
| `BeanShellAssertion.filename` | stringProp | `""` | Script file |
| `BeanShellAssertion.parameters` | stringProp | `""` | Parameters |
| `BeanShellAssertion.resetInterpreter` | boolProp | `false` | Reset interpreter between calls |

**Implementation Approach:**
Use `javax.script.ScriptEngineManager.getEngineByName("beanshell")`. Bind `SampleResult` (response data, code), `Response` (response body string), `Failure` (boolean, writable), `FailureMessage` (string, writable) to script engine. After execution, read Failure/FailureMessage to set assertion result.

**UI Schema (Zod):**
```typescript
export const beanShellAssertionSchema = z.object({
  script: z.string().default(''),
  filename: z.string().default(''),
  parameters: z.string().default(''),
  resetInterpreter: z.boolean().default(false),
});
```

---

### 4.6 JSR223 Assertion

| Field | Value |
|-------|-------|
| **testclass** | `JSR223Assertion` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Assertion |
| **Description** | Runs a JSR223 script (Groovy, JavaScript, etc.) to evaluate assertion result. Same variable bindings as BeanShell Assertion but with JSR223 scripting support. Preferred over BeanShell. |
| **Priority** | P1 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<JSR223Assertion guiclass="TestBeanGUI" testclass="JSR223Assertion" testname="JSR223 Assertion" enabled="true">
  <stringProp name="scriptLanguage">groovy</stringProp>
  <stringProp name="parameters"></stringProp>
  <stringProp name="filename"></stringProp>
  <stringProp name="cacheKey">true</stringProp>
  <stringProp name="script"></stringProp>
</JSR223Assertion>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `scriptLanguage` | stringProp | `"groovy"` | Scripting language |
| `parameters` | stringProp | `""` | Parameters |
| `filename` | stringProp | `""` | Script file |
| `cacheKey` | stringProp | `"true"` | Cache compiled script |
| `script` | stringProp | `""` | Inline script |

**Implementation Approach:**
Same as JSR223SamplerExecutor pattern. Use `javax.script.ScriptEngineManager`. Bind `SampleResult`, `prev` (previous SampleResult), `vars` (variable map), `Failure`, `FailureMessage`. After script execution, check Failure boolean.

**UI Schema (Zod):**
```typescript
export const jsr223AssertionSchema = z.object({
  scriptLanguage: z.string().default('groovy'),
  parameters: z.string().default(''),
  filename: z.string().default(''),
  cacheKey: z.string().default('true'),
  script: z.string().default(''),
});
```

---

### 4.7 Compare Assertion

| Field | Value |
|-------|-------|
| **testclass** | `CompareAssertion` |
| **guiclass** | `CompareAssertionVisualizer` |
| **Category** | Assertion |
| **Description** | Compares the response of the current sample with a previously stored response. Used for regression testing to detect changes in API responses. |
| **Priority** | P3 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<CompareAssertion guiclass="CompareAssertionVisualizer" testclass="CompareAssertion" testname="Compare Assertion" enabled="true">
  <boolProp name="compareContent">true</boolProp>
  <longProp name="compareTime">-1</longProp>
  <collectionProp name="stringsToSkip"/>
</CompareAssertion>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `compareContent` | boolProp | `true` | Compare response content |
| `compareTime` | longProp | `-1` | Compare response time (-1 = skip time comparison) |
| `stringsToSkip` | collectionProp | `[]` | Regex patterns to exclude from comparison |

**Implementation Approach:**
Store first sample's response as baseline. On subsequent samples, compare response body after applying skip patterns (`java.util.regex.Pattern` to remove matched substrings). If compareTime >= 0, also compare response time within tolerance.

**UI Schema (Zod):**
```typescript
export const compareAssertionSchema = z.object({
  compareContent: z.boolean().default(true),
  compareTime: z.number().default(-1),
  stringsToSkip: z.array(z.string()).default([]),
});
```

---

### 4.8 HTML Assertion

| Field | Value |
|-------|-------|
| **testclass** | `HTMLAssertion` |
| **guiclass** | `HTMLAssertionGui` |
| **Category** | Assertion |
| **Description** | Validates HTML responses using JTidy. Checks for HTML errors and warnings against configurable thresholds. |
| **Priority** | P3 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<HTMLAssertion guiclass="HTMLAssertionGui" testclass="HTMLAssertion" testname="HTML Assertion" enabled="true">
  <longProp name="html_assertion_error_threshold">0</longProp>
  <longProp name="html_assertion_warning_threshold">0</longProp>
  <stringProp name="html_assertion_doctype">omit</stringProp>
  <stringProp name="html_assertion_format">2</stringProp>
  <boolProp name="html_assertion_errorsonly">false</boolProp>
  <stringProp name="html_assertion_filename"></stringProp>
</HTMLAssertion>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `html_assertion_error_threshold` | longProp | `0` | Maximum allowed errors (0 = no errors allowed) |
| `html_assertion_warning_threshold` | longProp | `0` | Maximum allowed warnings |
| `html_assertion_doctype` | stringProp | `"omit"` | Doctype: omit, auto, strict, loose |
| `html_assertion_format` | stringProp | `"2"` | Format: 0=HTML, 1=XHTML, 2=XML |
| `html_assertion_errorsonly` | boolProp | `false` | Only count errors (not warnings) |
| `html_assertion_filename` | stringProp | `""` | File to write validation report |

**Implementation Approach:**
Use `javax.xml.parsers.DocumentBuilder` for XML/XHTML validation (JDK built-in). For HTML validation, implement a simple tag-balance checker: parse with regex for unclosed tags, mismatched tags, etc. Count errors and compare against threshold. Full JTidy-equivalent is complex; a simpler structural validator covers most use cases.

**UI Schema (Zod):**
```typescript
export const htmlAssertionSchema = z.object({
  errorThreshold: z.number().int().min(0).default(0),
  warningThreshold: z.number().int().min(0).default(0),
  doctype: z.enum(['omit', 'auto', 'strict', 'loose']).default('omit'),
  format: z.enum(['0', '1', '2']).default('2'),
  errorsOnly: z.boolean().default(false),
  filename: z.string().default(''),
});
```

---

## 5. Missing Timers

### 5.1 Constant Throughput Timer

| Field | Value |
|-------|-------|
| **testclass** | `ConstantThroughputTimer` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Timer |
| **Description** | Throttles test execution to a target throughput (samples/minute). Introduces delays to maintain the specified rate. Critical for load shaping and rate-limiting tests. |
| **Priority** | P1 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<ConstantThroughputTimer guiclass="TestBeanGUI" testclass="ConstantThroughputTimer" testname="Constant Throughput Timer" enabled="true">
  <intProp name="calcMode">0</intProp>
  <doubleProp>
    <name>throughput</name>
    <value>60.0</value>
  </doubleProp>
</ConstantThroughputTimer>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `throughput` | doubleProp | `60.0` | Target throughput in samples/minute |
| `calcMode` | intProp | `0` | Calculation mode: 0=this thread only, 1=all active threads, 2=all threads in current TG, 3=all active threads in current TG, 4=all active threads (shared) |

**Implementation Approach:**
Calculate interval = `60000.0 / throughput` (ms between samples). Track last sample timestamp. Before each sample, compute required delay = `interval - (now - lastSampleTime)`. If delay > 0, `Thread.sleep(delay)`. For shared modes, use a shared `AtomicLong` for last-sample tracking across VUs.

**UI Schema (Zod):**
```typescript
export const constantThroughputTimerSchema = z.object({
  throughput: z.number().min(0).default(60.0),
  calcMode: z.enum(['0', '1', '2', '3', '4']).default('0'),
});
```

---

### 5.2 Precise Throughput Timer

| Field | Value |
|-------|-------|
| **testclass** | `PreciseThroughputTimer` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Timer |
| **Description** | More accurate version of Constant Throughput Timer. Uses Poisson arrivals to distribute samples evenly over time. Produces more realistic load patterns. |
| **Priority** | P2 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<PreciseThroughputTimer guiclass="TestBeanGUI" testclass="PreciseThroughputTimer" testname="Precise Throughput Timer" enabled="true">
  <doubleProp>
    <name>throughput</name>
    <value>100.0</value>
  </doubleProp>
  <intProp name="throughputPeriod">3600</intProp>
  <longProp name="duration">3600</longProp>
  <intProp name="batchSize">1</intProp>
  <intProp name="batchThreadDelay">0</intProp>
  <doubleProp>
    <name>allowedThroughputSurplus</name>
    <value>1.0</value>
  </doubleProp>
  <longProp name="exactLimit">10000</longProp>
  <longProp name="randomSeed">0</longProp>
</PreciseThroughputTimer>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `throughput` | doubleProp | `100.0` | Target throughput |
| `throughputPeriod` | intProp | `3600` | Period (seconds) for throughput calculation |
| `duration` | longProp | `3600` | Test duration (seconds) |
| `batchSize` | intProp | `1` | Batch size for grouped arrivals |
| `batchThreadDelay` | intProp | `0` | Delay between threads in batch (ms) |
| `allowedThroughputSurplus` | doubleProp | `1.0` | Allowed throughput overshoot |
| `exactLimit` | longProp | `10000` | Maximum samples for exact scheduling |
| `randomSeed` | longProp | `0` | Random seed (0 = random) |

**Implementation Approach:**
Pre-compute a schedule of sample timestamps using Poisson inter-arrival times: `interval = -Math.log(1.0 - random.nextDouble()) * (throughputPeriod * 1000.0 / throughput)`. Before each sample, wait until the next scheduled timestamp. Use a shared `PriorityBlockingQueue` of scheduled times.

**UI Schema (Zod):**
```typescript
export const preciseThroughputTimerSchema = z.object({
  throughput: z.number().min(0).default(100.0),
  throughputPeriod: z.number().int().min(1).default(3600),
  duration: z.number().int().min(1).default(3600),
  batchSize: z.number().int().min(1).default(1),
  batchThreadDelay: z.number().int().min(0).default(0),
  allowedThroughputSurplus: z.number().min(0).default(1.0),
  exactLimit: z.number().int().min(0).default(10000),
  randomSeed: z.number().int().default(0),
});
```

---

### 5.3 Synchronizing Timer

| Field | Value |
|-------|-------|
| **testclass** | `SyncTimer` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Timer |
| **Description** | Rendezvous point that blocks all VUs until N threads are waiting, then releases them simultaneously. Creates a spike of concurrent requests. Essential for concurrency testing. |
| **Priority** | P1 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<SyncTimer guiclass="TestBeanGUI" testclass="SyncTimer" testname="Synchronizing Timer" enabled="true">
  <intProp name="groupSize">0</intProp>
  <longProp name="timeoutInMs">0</longProp>
</SyncTimer>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `groupSize` | intProp | `0` | Number of threads to wait for (0 = all threads in group) |
| `timeoutInMs` | longProp | `0` | Timeout in ms (0 = wait forever) |

**Implementation Approach:**
Use `java.util.concurrent.CyclicBarrier(groupSize)`. Each VU calls `barrier.await(timeout, TimeUnit.MILLISECONDS)`. When groupSize threads are waiting, all are released simultaneously. If groupSize=0, use ThreadGroup's thread count. Share barrier instance via a `ConcurrentHashMap<String, CyclicBarrier>` keyed by timer node ID.

**UI Schema (Zod):**
```typescript
export const syncTimerSchema = z.object({
  groupSize: z.number().int().min(0).default(0),
  timeoutInMs: z.number().int().min(0).default(0),
});
```

---

### 5.4 Poisson Random Timer

| Field | Value |
|-------|-------|
| **testclass** | `PoissonRandomTimer` |
| **guiclass** | `PoissonRandomTimerGui` |
| **Category** | Timer |
| **Description** | Adds a random delay based on Poisson distribution. Produces more realistic user think-time simulation than uniform distribution. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<PoissonRandomTimer guiclass="PoissonRandomTimerGui" testclass="PoissonRandomTimer" testname="Poisson Random Timer" enabled="true">
  <stringProp name="ConstantTimer.delay">300</stringProp>
  <stringProp name="RandomTimer.range">100</stringProp>
</PoissonRandomTimer>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ConstantTimer.delay` | stringProp | `"300"` | Constant offset (ms) |
| `RandomTimer.range` | stringProp | `"100"` | Lambda parameter for Poisson distribution |

**Implementation Approach:**
Generate Poisson-distributed delay: `delay + (long)(-Math.log(1.0 - ThreadLocalRandom.current().nextDouble()) * range)`. Apply `Thread.sleep(totalDelay)`. This is the same formula JMeter uses.

**UI Schema (Zod):**
```typescript
export const poissonTimerSchema = z.object({
  delay: z.string().default('300'),
  range: z.string().default('100'),
});
```

---

### 5.5 BeanShell Timer

| Field | Value |
|-------|-------|
| **testclass** | `BeanShellTimer` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Timer |
| **Description** | Runs a BeanShell script that returns a delay value in milliseconds. Enables custom delay logic. |
| **Priority** | P3 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<BeanShellTimer guiclass="TestBeanGUI" testclass="BeanShellTimer" testname="BeanShell Timer" enabled="true">
  <stringProp name="filename"></stringProp>
  <stringProp name="parameters"></stringProp>
  <boolProp name="resetInterpreter">false</boolProp>
  <stringProp name="script">return "1000";</stringProp>
</BeanShellTimer>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `filename` | stringProp | `""` | Script file |
| `parameters` | stringProp | `""` | Parameters |
| `resetInterpreter` | boolProp | `false` | Reset interpreter |
| `script` | stringProp | `""` | Inline script returning delay (ms) |

**Implementation Approach:**
Use `javax.script.ScriptEngineManager.getEngineByName("beanshell")`. Execute script, parse returned string as long, `Thread.sleep(result)`.

**UI Schema (Zod):**
```typescript
export const beanShellTimerSchema = z.object({
  filename: z.string().default(''),
  parameters: z.string().default(''),
  resetInterpreter: z.boolean().default(false),
  script: z.string().default(''),
});
```

---

## 6. Missing Pre-Processors

### 6.1 JSR223 PreProcessor

| Field | Value |
|-------|-------|
| **testclass** | `JSR223PreProcessor` |
| **guiclass** | `TestBeanGUI` |
| **Category** | PreProcessor |
| **Description** | Runs a JSR223 script before each sampler in its scope. Used for request manipulation, dynamic parameter generation, conditional logic. Highest-priority missing element across all categories. |
| **Priority** | P1 (CRITICAL) |
| **Effort** | S |

**JMX XML Schema:**
```xml
<JSR223PreProcessor guiclass="TestBeanGUI" testclass="JSR223PreProcessor" testname="JSR223 PreProcessor" enabled="true">
  <stringProp name="scriptLanguage">groovy</stringProp>
  <stringProp name="parameters"></stringProp>
  <stringProp name="filename"></stringProp>
  <stringProp name="cacheKey">true</stringProp>
  <stringProp name="script">vars.put("timestamp", String.valueOf(System.currentTimeMillis()));</stringProp>
</JSR223PreProcessor>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `scriptLanguage` | stringProp | `"groovy"` | Scripting language |
| `parameters` | stringProp | `""` | Parameters |
| `filename` | stringProp | `""` | Script file |
| `cacheKey` | stringProp | `"true"` | Cache compiled script |
| `script` | stringProp | `""` | Inline script |

**Implementation Approach:**
Identical pattern to JSR223SamplerExecutor. Use `javax.script.ScriptEngineManager`. Bind `vars` (VU variable map wrapper), `props` (properties), `sampler` (current sampler node), `log` (logger). Execute before the parent sampler. Add pre-processor execution phase to `dispatchNode()`: iterate children of sampler node looking for PreProcessor types before executing the sampler.

**UI Schema (Zod):**
```typescript
export const jsr223PreProcessorSchema = z.object({
  scriptLanguage: z.string().default('groovy'),
  parameters: z.string().default(''),
  filename: z.string().default(''),
  cacheKey: z.string().default('true'),
  script: z.string().default(''),
});
```

---

### 6.2 BeanShell PreProcessor

| Field | Value |
|-------|-------|
| **testclass** | `BeanShellPreProcessor` |
| **guiclass** | `TestBeanGUI` |
| **Category** | PreProcessor |
| **Description** | Runs a BeanShell script before each sampler. Legacy version of JSR223 PreProcessor. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<BeanShellPreProcessor guiclass="TestBeanGUI" testclass="BeanShellPreProcessor" testname="BeanShell PreProcessor" enabled="true">
  <stringProp name="filename"></stringProp>
  <stringProp name="parameters"></stringProp>
  <boolProp name="resetInterpreter">false</boolProp>
  <stringProp name="script"></stringProp>
</BeanShellPreProcessor>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `filename` | stringProp | `""` | Script file |
| `parameters` | stringProp | `""` | Parameters |
| `resetInterpreter` | boolProp | `false` | Reset interpreter |
| `script` | stringProp | `""` | Inline script |

**Implementation Approach:**
Delegate to JSR223 PreProcessor with `scriptLanguage=beanshell`.

**UI Schema (Zod):**
```typescript
export const beanShellPreProcessorSchema = z.object({
  filename: z.string().default(''),
  parameters: z.string().default(''),
  resetInterpreter: z.boolean().default(false),
  script: z.string().default(''),
});
```

---

### 6.3 RegEx User Parameters

| Field | Value |
|-------|-------|
| **testclass** | `RegExUserParameters` |
| **guiclass** | `RegExUserParametersGui` |
| **Category** | PreProcessor |
| **Description** | Extracts values from a previous response using regex and uses them as parameters for the next request. Combines extraction and parameterization in one element. |
| **Priority** | P3 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<RegExUserParameters guiclass="RegExUserParametersGui" testclass="RegExUserParameters" testname="RegEx User Parameters" enabled="true">
  <stringProp name="RegExUserParameters.regex">name="(\w+)" value="(\w+)"</stringProp>
  <stringProp name="RegExUserParameters.param_names_gr_nr">1</stringProp>
  <stringProp name="RegExUserParameters.param_values_gr_nr">2</stringProp>
</RegExUserParameters>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `RegExUserParameters.regex` | stringProp | `""` | Regular expression with groups |
| `RegExUserParameters.param_names_gr_nr` | stringProp | `"1"` | Group number for parameter names |
| `RegExUserParameters.param_values_gr_nr` | stringProp | `"2"` | Group number for parameter values |

**Implementation Approach:**
Apply `java.util.regex.Pattern` to previous response body. For each match, extract group(nameGroupNr) as parameter name and group(valueGroupNr) as parameter value. Store each pair in VU variable map.

**UI Schema (Zod):**
```typescript
export const regExUserParametersSchema = z.object({
  regex: z.string().default(''),
  paramNamesGroupNumber: z.string().default('1'),
  paramValuesGroupNumber: z.string().default('2'),
});
```

---

### 6.4 User Parameters

| Field | Value |
|-------|-------|
| **testclass** | `UserParameters` |
| **guiclass** | `UserParametersGui` |
| **Category** | PreProcessor |
| **Description** | Defines per-thread parameter values. Each VU gets a different set of values from a predefined table. Unlike CSV Data Set (file-based), values are inline in the test plan. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<UserParameters guiclass="UserParametersGui" testclass="UserParameters" testname="User Parameters" enabled="true">
  <collectionProp name="UserParameters.names">
    <stringProp name="">username</stringProp>
    <stringProp name="">password</stringProp>
  </collectionProp>
  <collectionProp name="UserParameters.thread_values">
    <collectionProp name="">
      <stringProp name="">user1</stringProp>
      <stringProp name="">pass1</stringProp>
    </collectionProp>
    <collectionProp name="">
      <stringProp name="">user2</stringProp>
      <stringProp name="">pass2</stringProp>
    </collectionProp>
  </collectionProp>
  <boolProp name="UserParameters.per_iteration">false</boolProp>
</UserParameters>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `UserParameters.names` | collectionProp | `[]` | Variable names |
| `UserParameters.thread_values` | collectionProp | `[[]]` | Per-thread value rows |
| `UserParameters.per_iteration` | boolProp | `false` | Cycle through values per iteration (vs per thread) |

**Implementation Approach:**
Determine VU index (thread number). Select value row at `vuIndex % numRows`. Set each variable name to its corresponding value in the VU variable map.

**UI Schema (Zod):**
```typescript
export const userParametersSchema = z.object({
  names: z.array(z.string()).default([]),
  threadValues: z.array(z.array(z.string())).default([[]]),
  perIteration: z.boolean().default(false),
});
```

---

### 6.5 HTML Link Parser

| Field | Value |
|-------|-------|
| **testclass** | `HTMLParser` |
| **guiclass** | `HtmlExtractorGui` |
| **Category** | PreProcessor |
| **Description** | Parses HTML response from the previous sampler and modifies the next HTTP request to follow links or submit forms found in the HTML. Used for web crawling and form submission simulation. |
| **Priority** | P3 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<HTMLParser guiclass="HtmlExtractorGui" testclass="HTMLParser" testname="HTML Link Parser" enabled="true"/>
```

**JMX Properties:**
None -- behavior is automatic based on previous response HTML.

**Implementation Approach:**
Parse previous response body for `<a href="...">` and `<form action="...">` tags using regex. For forms, also extract `<input>` values. Modify the next sampler's URL/parameters based on extracted links. Use `java.util.regex.Pattern` for HTML tag extraction.

**UI Schema (Zod):**
```typescript
export const htmlLinkParserSchema = z.object({});
```

---

### 6.6 HTTP URL Re-writing Modifier

| Field | Value |
|-------|-------|
| **testclass** | `URLRewritingModifier` |
| **guiclass** | `URLRewritingModifierGui` |
| **Category** | PreProcessor |
| **Description** | Extracts session IDs from URLs or response bodies and appends them to subsequent request URLs. Handles URL-based session tracking (`;jsessionid=...`). |
| **Priority** | P3 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<URLRewritingModifier guiclass="URLRewritingModifierGui" testclass="URLRewritingModifier" testname="HTTP URL Re-writing Modifier" enabled="true">
  <stringProp name="argument_name">jsessionid</stringProp>
  <boolProp name="path_extension">false</boolProp>
  <boolProp name="path_extension_no_equals">false</boolProp>
  <boolProp name="path_extension_no_questionmark">false</boolProp>
  <boolProp name="cache_value">false</boolProp>
  <boolProp name="encode">false</boolProp>
</URLRewritingModifier>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `argument_name` | stringProp | `""` | Session ID parameter name |
| `path_extension` | boolProp | `false` | Append as path extension (;name=value) vs query param |
| `path_extension_no_equals` | boolProp | `false` | Omit = sign in path extension |
| `path_extension_no_questionmark` | boolProp | `false` | Don't use ? for query params |
| `cache_value` | boolProp | `false` | Cache value across iterations |
| `encode` | boolProp | `false` | URL-encode the value |

**Implementation Approach:**
Search previous response body and URL for the argument_name parameter. Extract value using regex. Before next HTTP request, append the session parameter to the URL (either as path extension `;jsessionid=VALUE` or query parameter `?jsessionid=VALUE`). Use `java.net.URLEncoder` if encode=true.

**UI Schema (Zod):**
```typescript
export const urlRewritingModifierSchema = z.object({
  argumentName: z.string().default('jsessionid'),
  pathExtension: z.boolean().default(false),
  pathExtensionNoEquals: z.boolean().default(false),
  pathExtensionNoQuestionmark: z.boolean().default(false),
  cacheValue: z.boolean().default(false),
  encode: z.boolean().default(false),
});
```

---

## 7. Missing Post-Processors

### 7.1 XPath Extractor

| Field | Value |
|-------|-------|
| **testclass** | `XPathExtractor` |
| **guiclass** | `XPathExtractorGui` |
| **Category** | Post-Processor |
| **Description** | Extracts values from XML/HTML responses using XPath expressions. Stores results in JMeter variables. Essential for SOAP/XML API testing. |
| **Priority** | P1 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<XPathExtractor guiclass="XPathExtractorGui" testclass="XPathExtractor" testname="XPath Extractor" enabled="true">
  <stringProp name="XPathExtractor.refname">myVar</stringProp>
  <stringProp name="XPathExtractor.xpathQuery">/root/element/text()</stringProp>
  <stringProp name="XPathExtractor.default">NOT_FOUND</stringProp>
  <stringProp name="XPathExtractor.matchNumber">1</stringProp>
  <boolProp name="XPathExtractor.validate">false</boolProp>
  <boolProp name="XPathExtractor.whitespace">false</boolProp>
  <boolProp name="XPathExtractor.tolerant">false</boolProp>
  <boolProp name="XPathExtractor.namespace">false</boolProp>
  <stringProp name="XPathExtractor.fragment">false</stringProp>
</XPathExtractor>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `XPathExtractor.refname` | stringProp | `""` | Variable name for extracted value |
| `XPathExtractor.xpathQuery` | stringProp | `""` | XPath expression |
| `XPathExtractor.default` | stringProp | `""` | Default value if not found |
| `XPathExtractor.matchNumber` | stringProp | `"1"` | Match number (0=random, -1=all) |
| `XPathExtractor.validate` | boolProp | `false` | Validate XML |
| `XPathExtractor.whitespace` | boolProp | `false` | Ignore whitespace |
| `XPathExtractor.tolerant` | boolProp | `false` | Tolerant parsing (Tidy) |
| `XPathExtractor.namespace` | boolProp | `false` | Namespace-aware |
| `XPathExtractor.fragment` | stringProp | `"false"` | Return XML fragment |

**Implementation Approach:**
Use `javax.xml.parsers.DocumentBuilderFactory` and `javax.xml.xpath.XPathFactory` (JDK built-in). Parse response body as XML Document, evaluate XPath to get NodeList. Store matched values in variables: `refname` = match value, `refname_matchNr` = total matches, `refname_1`, `refname_2`, etc. for multiple matches.

**UI Schema (Zod):**
```typescript
export const xpathExtractorSchema = z.object({
  referenceName: z.string().default(''),
  xpathQuery: z.string().default(''),
  defaultValue: z.string().default(''),
  matchNumber: z.string().default('1'),
  validate: z.boolean().default(false),
  whitespace: z.boolean().default(false),
  tolerant: z.boolean().default(false),
  namespace: z.boolean().default(false),
  fragment: z.boolean().default(false),
});
```

---

### 7.2 JSR223 PostProcessor

| Field | Value |
|-------|-------|
| **testclass** | `JSR223PostProcessor` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Post-Processor |
| **Description** | Runs a JSR223 script after each sampler. Used for response processing, variable extraction, conditional logic based on results. Second most critical missing element. |
| **Priority** | P1 (CRITICAL) |
| **Effort** | S |

**JMX XML Schema:**
```xml
<JSR223PostProcessor guiclass="TestBeanGUI" testclass="JSR223PostProcessor" testname="JSR223 PostProcessor" enabled="true">
  <stringProp name="scriptLanguage">groovy</stringProp>
  <stringProp name="parameters"></stringProp>
  <stringProp name="filename"></stringProp>
  <stringProp name="cacheKey">true</stringProp>
  <stringProp name="script">vars.put("extracted", prev.getResponseDataAsString().split(",")[0]);</stringProp>
</JSR223PostProcessor>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `scriptLanguage` | stringProp | `"groovy"` | Scripting language |
| `parameters` | stringProp | `""` | Parameters |
| `filename` | stringProp | `""` | Script file |
| `cacheKey` | stringProp | `"true"` | Cache compiled script |
| `script` | stringProp | `""` | Inline script |

**Implementation Approach:**
Same pattern as JSR223SamplerExecutor. Bind `prev` (previous SampleResult), `vars` (VU variable map wrapper), `props`, `log`. Execute after sampler. Add to `applyPostProcessors()` method in NodeInterpreter.

**UI Schema (Zod):**
```typescript
export const jsr223PostProcessorSchema = z.object({
  scriptLanguage: z.string().default('groovy'),
  parameters: z.string().default(''),
  filename: z.string().default(''),
  cacheKey: z.string().default('true'),
  script: z.string().default(''),
});
```

---

### 7.3 BeanShell PostProcessor

| Field | Value |
|-------|-------|
| **testclass** | `BeanShellPostProcessor` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Post-Processor |
| **Description** | Runs a BeanShell script after each sampler. Legacy version of JSR223 PostProcessor. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<BeanShellPostProcessor guiclass="TestBeanGUI" testclass="BeanShellPostProcessor" testname="BeanShell PostProcessor" enabled="true">
  <stringProp name="filename"></stringProp>
  <stringProp name="parameters"></stringProp>
  <boolProp name="resetInterpreter">false</boolProp>
  <stringProp name="script"></stringProp>
</BeanShellPostProcessor>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `filename` | stringProp | `""` | Script file |
| `parameters` | stringProp | `""` | Parameters |
| `resetInterpreter` | boolProp | `false` | Reset interpreter |
| `script` | stringProp | `""` | Inline script |

**Implementation Approach:**
Delegate to JSR223 PostProcessor with `scriptLanguage=beanshell`.

**UI Schema (Zod):**
```typescript
export const beanShellPostProcessorSchema = z.object({
  filename: z.string().default(''),
  parameters: z.string().default(''),
  resetInterpreter: z.boolean().default(false),
  script: z.string().default(''),
});
```

---

### 7.4 CSS/JQuery Extractor

| Field | Value |
|-------|-------|
| **testclass** | `HtmlExtractor` |
| **guiclass** | `HtmlExtractorGui` |
| **Category** | Post-Processor |
| **Description** | Extracts values from HTML responses using CSS selectors or JQuery-like syntax. Alternative to XPath for HTML. |
| **Priority** | P2 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<HtmlExtractor guiclass="HtmlExtractorGui" testclass="HtmlExtractor" testname="CSS/JQuery Extractor" enabled="true">
  <stringProp name="HtmlExtractor.refname">myVar</stringProp>
  <stringProp name="HtmlExtractor.expr">div.content > p</stringProp>
  <stringProp name="HtmlExtractor.attribute">text</stringProp>
  <stringProp name="HtmlExtractor.default">NOT_FOUND</stringProp>
  <stringProp name="HtmlExtractor.match_number">1</stringProp>
  <stringProp name="HtmlExtractor.extractor_impl">JSOUP</stringProp>
</HtmlExtractor>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `HtmlExtractor.refname` | stringProp | `""` | Variable name |
| `HtmlExtractor.expr` | stringProp | `""` | CSS selector expression |
| `HtmlExtractor.attribute` | stringProp | `""` | Attribute to extract (empty = text content) |
| `HtmlExtractor.default` | stringProp | `""` | Default value |
| `HtmlExtractor.match_number` | stringProp | `"1"` | Match number |
| `HtmlExtractor.extractor_impl` | stringProp | `"JSOUP"` | Implementation: JSOUP or JODD |

**Implementation Approach:**
Implement a basic CSS selector engine using regex-based HTML parsing, or add `org.jsoup:jsoup` as an optional dependency. Parse HTML, apply CSS selector, extract attribute or text content. Store in variables following the same `refname`, `refname_matchNr`, `refname_N` pattern as other extractors.

**UI Schema (Zod):**
```typescript
export const cssExtractorSchema = z.object({
  referenceName: z.string().default(''),
  expression: z.string().default(''),
  attribute: z.string().default(''),
  defaultValue: z.string().default(''),
  matchNumber: z.string().default('1'),
  extractorImpl: z.enum(['JSOUP', 'JODD']).default('JSOUP'),
});
```

---

### 7.5 Boundary Extractor

| Field | Value |
|-------|-------|
| **testclass** | `BoundaryExtractor` |
| **guiclass** | `BoundaryExtractorGui` |
| **Category** | Post-Processor |
| **Description** | Extracts values from responses using left and right boundary strings. Simpler alternative to regex for extracting values between known delimiters. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<BoundaryExtractor guiclass="BoundaryExtractorGui" testclass="BoundaryExtractor" testname="Boundary Extractor" enabled="true">
  <stringProp name="BoundaryExtractor.refname">myVar</stringProp>
  <stringProp name="BoundaryExtractor.lboundary">token":"</stringProp>
  <stringProp name="BoundaryExtractor.rboundary">"</stringProp>
  <stringProp name="BoundaryExtractor.default">NOT_FOUND</stringProp>
  <stringProp name="BoundaryExtractor.match_number">1</stringProp>
  <stringProp name="BoundaryExtractor.useHeaders">false</stringProp>
</BoundaryExtractor>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `BoundaryExtractor.refname` | stringProp | `""` | Variable name |
| `BoundaryExtractor.lboundary` | stringProp | `""` | Left boundary string |
| `BoundaryExtractor.rboundary` | stringProp | `""` | Right boundary string |
| `BoundaryExtractor.default` | stringProp | `""` | Default value |
| `BoundaryExtractor.match_number` | stringProp | `"1"` | Match number |
| `BoundaryExtractor.useHeaders` | stringProp | `"false"` | Search in headers (vs body) |

**Implementation Approach:**
Use `String.indexOf(leftBoundary)` to find start, then `String.indexOf(rightBoundary, startIdx + leftBoundary.length())` to find end. Extract substring between boundaries. Iterate for all matches. Pure JDK String operations.

**UI Schema (Zod):**
```typescript
export const boundaryExtractorSchema = z.object({
  referenceName: z.string().default(''),
  leftBoundary: z.string().default(''),
  rightBoundary: z.string().default(''),
  defaultValue: z.string().default(''),
  matchNumber: z.string().default('1'),
  useHeaders: z.boolean().default(false),
});
```

---

### 7.6 Debug PostProcessor

| Field | Value |
|-------|-------|
| **testclass** | `DebugPostProcessor` |
| **guiclass** | `TestBeanGUI` |
| **Category** | Post-Processor |
| **Description** | Adds JMeter variables, properties, and system properties to the response data as a sub-result. Used for debugging variable state during test development. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<DebugPostProcessor guiclass="TestBeanGUI" testclass="DebugPostProcessor" testname="Debug PostProcessor" enabled="true">
  <boolProp name="displayJMeterProperties">false</boolProp>
  <boolProp name="displayJMeterVariables">true</boolProp>
  <boolProp name="displaySystemProperties">false</boolProp>
</DebugPostProcessor>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `displayJMeterProperties` | boolProp | `false` | Include JMeter properties |
| `displayJMeterVariables` | boolProp | `true` | Include JMeter variables |
| `displaySystemProperties` | boolProp | `false` | Include system properties |

**Implementation Approach:**
After sampler execution, build a string with variable/property dump. If displayJMeterVariables: iterate VU variable map and append `key=value\n`. If displaySystemProperties: iterate `System.getProperties()`. Append to SampleResult response data or create a sub-result.

**UI Schema (Zod):**
```typescript
export const debugPostProcessorSchema = z.object({
  displayJMeterProperties: z.boolean().default(false),
  displayJMeterVariables: z.boolean().default(true),
  displaySystemProperties: z.boolean().default(false),
});
```

---

## 8. Missing Listeners

### 8.1 View Results in Table

| Field | Value |
|-------|-------|
| **testclass** | `ViewResultsFullVisualizer` |
| **guiclass** | `TableVisualizer` |
| **Category** | Listener |
| **Description** | Displays sample results in a tabular format showing sample #, start time, thread name, label, sample time, status, bytes, sent bytes, latency, connect time. |
| **Priority** | P2 |
| **Effort** | S |

**JMX XML Schema:**
```xml
<ResultCollector guiclass="TableVisualizer" testclass="ResultCollector" testname="View Results in Table" enabled="true">
  <boolProp name="ResultCollector.error_logging">false</boolProp>
  <objProp>
    <name>saveConfig</name>
    <value class="SampleSaveConfiguration">
      <time>true</time>
      <latency>true</latency>
      <timestamp>true</timestamp>
      <success>true</success>
      <label>true</label>
      <code>true</code>
      <message>true</message>
      <threadName>true</threadName>
      <dataType>true</dataType>
      <encoding>false</encoding>
      <assertions>true</assertions>
      <subresults>true</subresults>
      <responseData>false</responseData>
      <samplerData>false</samplerData>
      <xml>false</xml>
      <fieldNames>true</fieldNames>
      <responseHeaders>false</responseHeaders>
      <requestHeaders>false</requestHeaders>
      <responseDataOnError>false</responseDataOnError>
      <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
      <assertionsResultsToSave>0</assertionsResultsToSave>
      <bytes>true</bytes>
      <sentBytes>true</sentBytes>
      <url>true</url>
      <threadCounts>true</threadCounts>
      <sampleCount>true</sampleCount>
      <idleTime>true</idleTime>
      <connectTime>true</connectTime>
    </value>
  </objProp>
  <stringProp name="filename"></stringProp>
</ResultCollector>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ResultCollector.error_logging` | boolProp | `false` | Only log errors |
| `filename` | stringProp | `""` | Output file path |
| `saveConfig` | objProp | (see above) | Column configuration |

**Implementation Approach:**
This is a UI-side concern. Create `ResultsTable.tsx` component that renders SampleResults in an HTML table with sortable columns. Data comes from existing SSE streaming results. Backend already provides all necessary data fields.

**UI Schema (Zod):**
```typescript
export const resultsTableSchema = z.object({
  errorLogging: z.boolean().default(false),
  filename: z.string().default(''),
});
```

---

### 8.2 Simple Data Writer

| Field | Value |
|-------|-------|
| **testclass** | `ResultCollector` |
| **guiclass** | `SimpleDataWriter` |
| **Category** | Listener |
| **Description** | Writes sample results to a file (JTL format -- CSV or XML). Does not display results in the UI. Used for saving results to disk for later analysis. |
| **Priority** | P1 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<ResultCollector guiclass="SimpleDataWriter" testclass="ResultCollector" testname="Simple Data Writer" enabled="true">
  <boolProp name="ResultCollector.error_logging">false</boolProp>
  <objProp>
    <name>saveConfig</name>
    <value class="SampleSaveConfiguration">
      <time>true</time>
      <latency>true</latency>
      <timestamp>true</timestamp>
      <success>true</success>
      <label>true</label>
      <code>true</code>
      <message>true</message>
      <threadName>true</threadName>
      <dataType>true</dataType>
      <encoding>false</encoding>
      <assertions>true</assertions>
      <subresults>true</subresults>
      <responseData>false</responseData>
      <samplerData>false</samplerData>
      <xml>false</xml>
      <fieldNames>true</fieldNames>
      <responseHeaders>false</responseHeaders>
      <requestHeaders>false</requestHeaders>
      <responseDataOnError>false</responseDataOnError>
      <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
      <assertionsResultsToSave>0</assertionsResultsToSave>
      <bytes>true</bytes>
      <sentBytes>true</sentBytes>
      <url>true</url>
      <threadCounts>true</threadCounts>
      <sampleCount>true</sampleCount>
      <idleTime>true</idleTime>
      <connectTime>true</connectTime>
    </value>
  </objProp>
  <stringProp name="filename">results.jtl</stringProp>
</ResultCollector>
```

**JMX Properties:**
Same as View Results in Table (8.1), with `filename` being the critical property.

**Implementation Approach:**
Create a `JtlWriter` that writes sample results in JMeter CSV format: `timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect`. Write header row first, then append each sample as a CSV line. Use `java.io.BufferedWriter` for performance. Wire into the result publishing pipeline.

**UI Schema (Zod):**
```typescript
export const simpleDataWriterSchema = z.object({
  errorLogging: z.boolean().default(false),
  filename: z.string().default('results.jtl'),
});
```

---

### 8.3 Backend Listener (InfluxDB/Graphite)

| Field | Value |
|-------|-------|
| **testclass** | `BackendListener` |
| **guiclass** | `BackendListenerGui` |
| **Category** | Listener |
| **Description** | Sends metrics to external monitoring systems (InfluxDB or Graphite) in real-time. JMeter 5.x includes InfluxDB and Graphite client implementations. Note: jMeter Next already has Prometheus metrics via Micrometer, but JMX compatibility requires supporting the BackendListener testclass. |
| **Priority** | P2 |
| **Effort** | M |

**JMX XML Schema:**
```xml
<BackendListener guiclass="BackendListenerGui" testclass="BackendListener" testname="Backend Listener" enabled="true">
  <elementProp name="arguments" elementType="Arguments">
    <collectionProp name="Arguments.arguments">
      <elementProp name="influxdbMetricsSender" elementType="Argument">
        <stringProp name="Argument.name">influxdbMetricsSender</stringProp>
        <stringProp name="Argument.value">org.apache.jmeter.visualizers.backend.influxdb.HttpMetricsSender</stringProp>
      </elementProp>
      <elementProp name="influxdbUrl" elementType="Argument">
        <stringProp name="Argument.name">influxdbUrl</stringProp>
        <stringProp name="Argument.value">http://localhost:8086/write?db=jmeter</stringProp>
      </elementProp>
      <elementProp name="application" elementType="Argument">
        <stringProp name="Argument.name">application</stringProp>
        <stringProp name="Argument.value">myApp</stringProp>
      </elementProp>
      <elementProp name="measurement" elementType="Argument">
        <stringProp name="Argument.name">measurement</stringProp>
        <stringProp name="Argument.value">jmeter</stringProp>
      </elementProp>
      <elementProp name="summaryOnly" elementType="Argument">
        <stringProp name="Argument.name">summaryOnly</stringProp>
        <stringProp name="Argument.value">false</stringProp>
      </elementProp>
      <elementProp name="samplersRegex" elementType="Argument">
        <stringProp name="Argument.name">samplersRegex</stringProp>
        <stringProp name="Argument.value">.*</stringProp>
      </elementProp>
      <elementProp name="percentiles" elementType="Argument">
        <stringProp name="Argument.name">percentiles</stringProp>
        <stringProp name="Argument.value">90;95;99</stringProp>
      </elementProp>
      <elementProp name="testTitle" elementType="Argument">
        <stringProp name="Argument.name">testTitle</stringProp>
        <stringProp name="Argument.value">Test</stringProp>
      </elementProp>
    </collectionProp>
  </elementProp>
  <stringProp name="classname">org.apache.jmeter.visualizers.backend.influxdb.InfluxdbBackendListenerClient</stringProp>
  <stringProp name="QUEUE_SIZE">5000</stringProp>
</BackendListener>
```

**JMX Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `classname` | stringProp | `"...InfluxdbBackendListenerClient"` | Backend client class |
| `QUEUE_SIZE` | stringProp | `"5000"` | Async queue size |
| `influxdbUrl` | Argument | `""` | InfluxDB write endpoint |
| `application` | Argument | `""` | Application tag |
| `measurement` | Argument | `"jmeter"` | InfluxDB measurement name |
| `summaryOnly` | Argument | `"false"` | Only send summary metrics |
| `samplersRegex` | Argument | `".*"` | Regex to filter samplers |
| `percentiles` | Argument | `"90;95;99"` | Percentile values to compute |
| `testTitle` | Argument | `"Test"` | Test run title tag |

**Implementation Approach:**
For InfluxDB: use `java.net.http.HttpClient` to POST InfluxDB line protocol data to the write endpoint. Format: `measurement,application=app,transaction=label count=N,avg=X,min=Y,max=Z,pctXX=V timestamp`. For Graphite: use `java.net.Socket` to send plaintext protocol `metric.path value timestamp\n`. Aggregate metrics on a timer (every 5 seconds) using existing SampleBucket data.

**UI Schema (Zod):**
```typescript
export const backendListenerSchema = z.object({
  classname: z.string().default('InfluxdbBackendListenerClient'),
  queueSize: z.string().default('5000'),
  influxdbUrl: z.string().default('http://localhost:8086/write?db=jmeter'),
  application: z.string().default(''),
  measurement: z.string().default('jmeter'),
  summaryOnly: z.boolean().default(false),
  samplersRegex: z.string().default('.*'),
  percentiles: z.string().default('90;95;99'),
  testTitle: z.string().default('Test'),
});
```

---

## 9. Missing Other Features

### 9.1 Properties File Loading (`-p` flag)

| Field | Value |
|-------|-------|
| **Category** | CLI Feature |
| **Description** | Load a Java properties file and make all key-value pairs available as JMeter properties. CLI flag is already accepted (`-p`) but file loading is not implemented. |
| **Priority** | P1 |
| **Effort** | S |

**Implementation Approach:**
In `JMeterNextCli`, when `-p <file>` is provided: load file with `java.util.Properties.load(new FileInputStream(file))`. Iterate entries and add each to the engine's property map (same map used by `-J` overrides). Apply before test plan execution.

---

### 9.2 JTL Result File Writing (`-l` flag)

| Field | Value |
|-------|-------|
| **Category** | CLI Feature |
| **Description** | Write all sample results to a JTL file in JMeter CSV format. CLI flag is accepted (`-l`) but output is not generated. Critical for CI/CD integration -- many tools parse JTL files. |
| **Priority** | P1 |
| **Effort** | M |

**Implementation Approach:**
Create `JtlResultWriter` class. Register as a `SampleStreamBroker` subscriber. On each `SampleResult`, write a CSV line: `timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect`. Use `java.io.BufferedWriter` with flush-on-write for crash safety.

**JTL CSV Header:**
```
timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect
```

---

### 9.3 HTML Report Generation (`-e`, `-o` flags)

| Field | Value |
|-------|-------|
| **Category** | CLI Feature |
| **Description** | Generate a static HTML report dashboard from JTL results. JMeter 5.x generates a full HTML report with charts for response times, throughput, error rates, percentiles, etc. CLI flags are accepted but generation is not implemented. |
| **Priority** | P2 |
| **Effort** | L |

**Implementation Approach:**
Parse the JTL CSV file generated by `-l`. Compute aggregate statistics per sampler label: count, error%, avg/min/max/p50/p90/p95/p99, throughput. Generate an HTML file using a template string with embedded charts (use inline SVG for charts or generate Chart.js data inline). Write to the `-o` output directory as `index.html` + `content/` subdirectory. No external template engine needed -- use `String.format()` or `StringBuilder` with HTML template literals.

---

## 10. Prioritized Task List

### P1 -- Critical Backward Compatibility (any JMX using these will fail)

| # | Element | Category | Effort | Dependencies | Notes |
|---|---------|----------|--------|-------------|-------|
| 1 | **JSR223 PreProcessor** | PreProcessor | S | None | Highest-impact gap. Add pre-processor phase to NodeInterpreter |
| 2 | **JSR223 PostProcessor** | PostProcessor | S | None | Add to applyPostProcessors() in NodeInterpreter |
| 3 | **JSR223 Assertion** | Assertion | S | None | Add to applyPostProcessors() in NodeInterpreter |
| 4 | **JSON Assertion (JSONPathAssertion)** | Assertion | S | Existing JSONPath code in ExtractorExecutor | Reuse JSON parsing from ExtractorExecutor |
| 5 | **XPath Assertion** | Assertion | S | None (JDK javax.xml) | Pure JDK implementation |
| 6 | **XPath Extractor** | PostProcessor | S | None (JDK javax.xml) | Same JDK APIs as XPath Assertion |
| 7 | **ForEach Controller** | Controller | S | None | VU variable map iteration |
| 8 | **Once Only Controller** | Controller | S | None | Per-VU execution tracking |
| 9 | **Switch Controller** | Controller | S | None | Index/name-based child selection |
| 10 | **Throughput Controller** | Controller | M | None | AtomicInteger for shared counting |
| 11 | **Constant Throughput Timer** | Timer | M | None | Rate-limiting with timestamp tracking |
| 12 | **Synchronizing Timer** | Timer | S | None | CyclicBarrier rendezvous |
| 13 | **HTTP Cache Manager** | Config | M | HttpSamplerExecutor modification | Cache integration with HTTP executor |
| 14 | **HTTP Authorization Manager** | Config | M | HttpSamplerExecutor modification | Auth header injection |
| 15 | **JDBC Connection Configuration** | Config | M | None | Connection pool before JDBC Sampler works |
| 16 | **Simple Data Writer (JTL)** | Listener | M | SampleResult pipeline | JTL CSV file output |
| 17 | **JTL result file writing (`-l`)** | CLI | M | Simple Data Writer | CLI flag wiring |
| 18 | **Properties file loading (`-p`)** | CLI | S | None | java.util.Properties.load() |

**P1 subtotal: 18 tasks, estimated 5-6 weeks**

---

### P2 -- Commonly Used Elements

| # | Element | Category | Effort | Dependencies | Notes |
|---|---------|----------|--------|-------------|-------|
| 19 | **Random Variable** | Config | S | None | ThreadLocalRandom |
| 20 | **Counter** | Config | S | None | AtomicLong |
| 21 | **DNS Cache Manager** | Config | M | HttpClient DNS config | Custom DNS resolution |
| 22 | **Keystore Configuration** | Config | S | SSLContext config | mTLS support |
| 23 | **User Parameters** | PreProcessor | S | None | Per-VU parameter table |
| 24 | **BeanShell PreProcessor** | PreProcessor | S | JSR223 PreProcessor (#1) | Delegate to JSR223 |
| 25 | **BeanShell PostProcessor** | PostProcessor | S | JSR223 PostProcessor (#2) | Delegate to JSR223 |
| 26 | **BeanShell Assertion** | Assertion | S | ScriptEngine | Same pattern as JSR223 |
| 27 | **XML Assertion** | Assertion | S | None (JDK javax.xml) | Parse validation |
| 28 | **Boundary Extractor** | PostProcessor | S | None | String.indexOf() |
| 29 | **CSS/JQuery Extractor** | PostProcessor | M | Optional jsoup dep | CSS selector engine |
| 30 | **Debug PostProcessor** | PostProcessor | S | None | Variable map dump |
| 31 | **Poisson Random Timer** | Timer | S | None | Poisson distribution |
| 32 | **Precise Throughput Timer** | Timer | M | None | Poisson arrival schedule |
| 33 | **Module Controller** | Controller | M | Plan tree walking | Tree path resolution |
| 34 | **Include Controller** | Controller | M | JmxTreeWalker | JMX fragment parsing |
| 35 | **Runtime Controller** | Controller | S | None | Time-bounded loop |
| 36 | **Random Controller** | Controller | S | None | ThreadLocalRandom child selection |
| 37 | **Interleave Controller** | Controller | S | None | Round-robin counter |
| 38 | **Random Order Controller** | Controller | S | None | Collections.shuffle() |
| 39 | **JMS Publisher** | Sampler | M | jakarta.jms dependency | JMS API |
| 40 | **JMS Subscriber** | Sampler | M | jakarta.jms dependency | JMS API |
| 41 | **View Results in Table** | Listener | S | Existing SSE data | UI component only |
| 42 | **Backend Listener (InfluxDB)** | Listener | M | None | HTTP line protocol |
| 43 | **HTML Report Generation (`-e`, `-o`)** | CLI | L | JTL file (#17) | HTML template generation |

**P2 subtotal: 25 tasks, estimated 8-10 weeks**

---

### P3 -- Rare/Legacy Elements

| # | Element | Category | Effort | Dependencies | Notes |
|---|---------|----------|--------|-------------|-------|
| 44 | **SOAP/XML-RPC Request** | Sampler | S | None | Specialized HTTP POST |
| 45 | **Mail Reader Sampler** | Sampler | M | jakarta.mail dependency | POP3/IMAP |
| 46 | **BSF Sampler** | Sampler | S | JSR223 delegation | Deprecated in JMeter 5.x |
| 47 | **Access Log Sampler** | Sampler | M | HttpSamplerExecutor | Log file parsing |
| 48 | **AJP/1.3 Sampler** | Sampler | M | None | Binary protocol over Socket |
| 49 | **JUnit Request** | Sampler | M | java.lang.reflect | Reflection-based invocation |
| 50 | **Login Config Element** | Config | S | None | Variable map population |
| 51 | **LDAP Request Defaults** | Config | S | None | Default value merge |
| 52 | **Simple Config Element** | Config | S | None | Generic property merge |
| 53 | **RegEx User Parameters** | PreProcessor | M | Regex + VU variables | Previous response parsing |
| 54 | **HTML Link Parser** | PreProcessor | M | None | HTML tag extraction |
| 55 | **HTTP URL Re-writing Modifier** | PreProcessor | S | None | Session ID extraction |
| 56 | **BeanShell Timer** | Timer | S | ScriptEngine | Script-based delay |
| 57 | **JSON Path Assertion (v2)** | Assertion | S | JSON Assertion (#4) | Alias to JSONPathAssertion |
| 58 | **Compare Assertion** | Assertion | M | None | Response comparison |
| 59 | **HTML Assertion** | Assertion | M | None | HTML validation |
| 60 | **Recording Controller** | Controller | S | None | Transparent wrapper |

**P3 subtotal: 17 tasks, estimated 6-8 weeks**

---

## 11. Architecture Changes Required

### 11.1 Pre-Processor Execution Phase

The current `NodeInterpreter.dispatchNode()` only handles post-processors (assertions, extractors, timers) as children of samplers. Pre-processors require a new execution phase:

**Before executing a sampler**, iterate its children looking for pre-processor types (`JSR223PreProcessor`, `BeanShellPreProcessor`, `UserParameters`, etc.) and execute them first. This modifies `dispatchNode()` for every sampler case to:

1. Execute pre-processor children
2. Execute the sampler
3. Execute post-processor children (existing behavior)

### 11.2 Config Element Scope Resolution

Config elements (HTTP Cache Manager, Auth Manager, Defaults, etc.) need scope-based resolution. The interpreter must walk up the tree from the current sampler to find applicable config elements. Add a `resolveConfigElements(node, type)` method that walks ancestor nodes to find config elements of a given type.

### 11.3 Shared State for Cross-VU Elements

Several elements need shared state across VUs:
- **Counter** (shared mode): `ConcurrentHashMap<String, AtomicLong>`
- **Synchronizing Timer**: `ConcurrentHashMap<String, CyclicBarrier>`
- **Throughput Controller** (shared mode): `ConcurrentHashMap<String, AtomicInteger>`
- **Constant Throughput Timer** (shared mode): `ConcurrentHashMap<String, AtomicLong>`

Add a `SharedStateRegistry` to `TestRunContext` for cross-VU element state.

### 11.4 NodeInterpreter.dispatchNode() Additions

The `dispatchNode()` switch statement needs these new case branches:

```java
// Controllers
case "ForeachController" -> new ForEachControllerExecutor(this).execute(node, variables);
case "SwitchController" -> new SwitchControllerExecutor(this).execute(node, variables);
case "OnceOnlyController" -> new OnceOnlyControllerExecutor(this).execute(node, variables);
case "ThroughputController" -> new ThroughputControllerExecutor(this).execute(node, variables);
case "RunTime" -> new RuntimeControllerExecutor(this).execute(node, variables);
case "RandomController" -> new RandomControllerExecutor(this).execute(node, variables);
case "InterleaveControl" -> new InterleaveControllerExecutor(this).execute(node, variables);
case "RandomOrderController" -> new RandomOrderControllerExecutor(this).execute(node, variables);
case "ModuleController" -> new ModuleControllerExecutor(this).execute(node, variables);
case "IncludeController" -> new IncludeControllerExecutor(this).execute(node, variables);
case "RecordingController" -> executeChildren(node.getChildren(), variables); // transparent

// Assertions (add to applyPostProcessors)
case "JSONPathAssertion", "JSONPathAssertion2" -> JsonAssertionExecutor.execute(child, samplerResult, variables);
case "XPathAssertion" -> XPathAssertionExecutor.execute(child, samplerResult, variables);
case "XMLAssertion" -> XmlAssertionExecutor.execute(child, samplerResult, variables);
case "JSR223Assertion" -> JSR223AssertionExecutor.execute(child, samplerResult, variables);
case "BeanShellAssertion" -> BeanShellAssertionExecutor.execute(child, samplerResult, variables);
case "CompareAssertion" -> CompareAssertionExecutor.execute(child, samplerResult, variables);
case "HTMLAssertion" -> HtmlAssertionExecutor.execute(child, samplerResult, variables);
case "DurationAssertion" -> DurationAssertionExecutor.execute(child, samplerResult, variables);
case "SizeAssertion" -> SizeAssertionExecutor.execute(child, samplerResult, variables);

// Timers (add to timer dispatch)
case "ConstantThroughputTimer" -> constantThroughputTimerExecutor.execute(node, variables);
case "PreciseThroughputTimer" -> preciseThroughputTimerExecutor.execute(node, variables);
case "SyncTimer" -> syncTimerExecutor.execute(node, variables);
case "PoissonRandomTimer" -> timerExecutor.execute(node); // same pattern as other random timers
case "BeanShellTimer" -> beanShellTimerExecutor.execute(node, variables);

// Post-Processors (add to applyPostProcessors)
case "XPathExtractor" -> XPathExtractorExecutor.execute(child, samplerResult, variables);
case "JSR223PostProcessor" -> JSR223PostProcessorExecutor.execute(child, samplerResult, variables);
case "BeanShellPostProcessor" -> BeanShellPostProcessorExecutor.execute(child, samplerResult, variables);
case "HtmlExtractor" -> CssExtractorExecutor.execute(child, samplerResult, variables);
case "BoundaryExtractor" -> BoundaryExtractorExecutor.execute(child, samplerResult, variables);
case "DebugPostProcessor" -> DebugPostProcessorExecutor.execute(child, samplerResult, variables);

// Pre-Processors (new phase, execute before sampler)
case "JSR223PreProcessor" -> JSR223PreProcessorExecutor.execute(child, variables);
case "BeanShellPreProcessor" -> BeanShellPreProcessorExecutor.execute(child, variables);
case "UserParameters" -> UserParametersExecutor.execute(child, variables);
case "RegExUserParameters" -> RegExUserParametersExecutor.execute(child, samplerResult, variables);
case "HTMLParser" -> HtmlLinkParserExecutor.execute(child, samplerResult, variables);
case "URLRewritingModifier" -> UrlRewritingModifierExecutor.execute(child, samplerResult, variables);

// Config Elements (resolved during config phase)
case "CacheManager" -> cacheManager.configure(node);
case "AuthManager" -> authManager.configure(node);
case "RandomVariableConfig" -> randomVariableExecutor.execute(node, variables);
case "CounterConfig" -> counterExecutor.execute(node, variables);
case "DNSCacheManager" -> dnsManager.configure(node);
case "KeystoreConfig" -> keystoreConfig.configure(node);
case "LoginConfig" -> loginConfigExecutor.execute(node, variables);
case "JDBCDataSource" -> jdbcConnectionManager.configure(node);
case "ConfigTestElement" -> configElementExecutor.execute(node, variables);
```

---

## 12. Total Effort Summary

| Priority | Tasks | Effort (person-weeks) |
|----------|-------|----------------------|
| P1 (Critical) | 18 | 5-6 |
| P2 (Common) | 25 | 8-10 |
| P3 (Rare/Legacy) | 17 | 6-8 |
| Architecture changes | 4 | 2-3 |
| **TOTAL** | **64** | **21-27 person-weeks** |

### Sprint Plan (2-week sprints)

| Sprint | Focus | Deliverables |
|--------|-------|-------------|
| Sprint 1 | Pre/Post-Processor framework + JSR223 | JSR223 PreProcessor, JSR223 PostProcessor, JSR223 Assertion, pre-processor phase in NodeInterpreter |
| Sprint 2 | Critical extractors + assertions | JSON Assertion, XPath Assertion, XPath Extractor, Boundary Extractor |
| Sprint 3 | Critical controllers | ForEach, Once Only, Switch, Throughput Controller |
| Sprint 4 | Timers + config | Constant Throughput Timer, Synchronizing Timer, HTTP Cache Manager, HTTP Auth Manager |
| Sprint 5 | JDBC + JTL | JDBC Connection Config, Simple Data Writer, JTL output (-l flag), Properties loading (-p flag) |
| Sprint 6 | P2 controllers + config | Runtime, Random, Interleave, Random Order, Counter, Random Variable |
| Sprint 7 | P2 extractors + assertions | CSS Extractor, BeanShell Pre/Post/Assertion, Debug PostProcessor, XML Assertion |
| Sprint 8 | P2 timers + listeners | Poisson Timer, Precise Throughput Timer, Backend Listener, View Results Table |
| Sprint 9 | P2 controllers + samplers | Module, Include, DNS Cache Manager, Keystore Config, User Parameters |
| Sprint 10 | JMS + HTML reports | JMS Publisher, JMS Subscriber, HTML Report Generation |
| Sprint 11 | P3 samplers | SOAP, Mail Reader, BSF, Access Log, AJP, JUnit |
| Sprint 12 | P3 remainder | Login Config, LDAP Defaults, Simple Config, Recording Controller, Compare/HTML Assertion, URL Rewriting, HTML Link Parser, BeanShell Timer |
