/**
 * PropertyPanel — displays and edits properties of the currently selected
 * test plan node.
 *
 * Reads `selectedNodeId` from the testPlanStore, resolves the corresponding
 * node, looks up a Zod schema from the registry, and renders a react-hook-form
 * backed form with inline validation.  On successful submission each changed
 * field is persisted via `updateProperty`.
 *
 * T036 — DynamicForm fallback:
 * When no hand-coded schema is registered for a node type, the component falls
 * back to {@link DynamicForm} driven by a pre-fetched API schema.  Hand-coded
 * Zod schemas (T024) always take priority over the dynamic fallback.
 *
 * Architecture: Title + Name/Comments + Fieldsets (JMeter style).
 */

import { useEffect, useState, useCallback, useMemo } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTestPlanStore } from '../../store/testPlanStore.js';
import { useUiStore } from '../../store/uiStore.js';
import type { TestPlanNode } from '../../types/test-plan.js';
import { getSchemaEntry } from './schemas/index.js';
import type { SchemaEntry } from './schemas/index.js';
import type { UserDefinedVariable } from './schemas/index.js';
import { DynamicForm } from './DynamicForm.js';
import type { ComponentSchemaDto } from './DynamicForm.js';
import { EditableTable } from '../EditableTable/EditableTable.js';
import type { Column, Row } from '../EditableTable/EditableTable.js';
import './PropertyPanel.css';

// ---------------------------------------------------------------------------
// Human-readable type names
// ---------------------------------------------------------------------------

const TYPE_DISPLAY_NAMES: Record<string, string> = {
  TestPlan: 'Test Plan',
  ThreadGroup: 'Thread Group',
  HTTPSampler: 'HTTP Request',
  HTTPSamplerProxy: 'HTTP Request',
  TCPSampler: 'TCP Sampler',
  JDBCSampler: 'JDBC Request',
  FTPSampler: 'FTP Request',
  SMTPSampler: 'SMTP Sampler',
  BoltSampler: 'Bolt Request',
  LDAPSampler: 'LDAP Request',
  JMSSampler: 'JMS Sampler',
  ResponseAssertion: 'Response Assertion',
  DurationAssertion: 'Duration Assertion',
  SizeAssertion: 'Size Assertion',
  ConstantTimer: 'Constant Timer',
  GaussianRandomTimer: 'Gaussian Random Timer',
  UniformRandomTimer: 'Uniform Random Timer',
  CSVDataSet: 'CSV Data Set Config',
  HeaderManager: 'HTTP Header Manager',
  HTTPHeaderManager: 'HTTP Header Manager',
  CookieManager: 'HTTP Cookie Manager',
  HTTPCookieManager: 'HTTP Cookie Manager',
  UserDefinedVariables: 'User Defined Variables',
  HTTPRequestDefaults: 'HTTP Request Defaults',
  LoopController: 'Loop Controller',
  IfController: 'If Controller',
  WhileController: 'While Controller',
  TransactionController: 'Transaction Controller',
  SimpleController: 'Simple Controller',
  RegexExtractor: 'Regular Expression Extractor',
  JSONPathExtractor: 'JSON Path Extractor',
  JSONPostProcessor: 'JSON Extractor',
  ResultCollector: 'View Results Tree',
  ViewResultsTree: 'View Results Tree',
  Summariser: 'Generate Summary Results',
  SummaryReport: 'Summary Report',
  AggregateReport: 'Aggregate Report',
};

function getDisplayName(type: string): string {
  return TYPE_DISPLAY_NAMES[type] ?? type.replace(/([A-Z])/g, ' $1').trim();
}

// ---------------------------------------------------------------------------
// HTTP Sampler fieldset grouping
// ---------------------------------------------------------------------------

/** Defines field groups for HTTPSampler / HTTPSamplerProxy fieldsets. */
const HTTP_FIELDSETS: { legend: string; fields: string[] }[] = [
  { legend: 'Web Server', fields: ['protocol', 'domain', 'port'] },
  { legend: 'HTTP Request', fields: ['method', 'path', 'contentEncoding'] },
];

/** Returns the set of fields belonging to fieldsets for a given type. */
function getFieldsets(nodeType: string): { legend: string; fields: string[] }[] | null {
  if (nodeType === 'HTTPSampler' || nodeType === 'HTTPSamplerProxy') {
    return HTTP_FIELDSETS;
  }
  return null;
}

// ---------------------------------------------------------------------------
// HTTP Sampler sub-tab table column definitions
// ---------------------------------------------------------------------------

const HTTP_PARAM_COLUMNS: Column[] = [
  { key: 'name', label: 'Name', type: 'text', width: '30%' },
  { key: 'value', label: 'Value', type: 'text', width: '30%' },
  { key: 'urlEncode', label: 'URL Encode?', type: 'checkbox', width: '15%' },
  { key: 'contentType', label: 'Content-Type', type: 'text', width: '25%' },
];

const HTTP_FILES_COLUMNS: Column[] = [
  { key: 'filePath', label: 'File Path', type: 'text', width: '40%' },
  { key: 'paramName', label: 'Parameter Name', type: 'text', width: '30%' },
  { key: 'mimeType', label: 'MIME Type', type: 'text', width: '30%' },
];

// ---------------------------------------------------------------------------
// HTTPSamplerForm — custom form with Basic/Advanced tabs and sub-tabs
// ---------------------------------------------------------------------------

type HttpTab = 'basic' | 'advanced';
type HttpSubTab = 'parameters' | 'bodyData' | 'filesUpload';

interface HTTPSamplerFormProps {
  node: TestPlanNode;
  schemaEntry: SchemaEntry;
  initialValues: Record<string, unknown>;
  onSubmit: (values: Record<string, unknown>) => void;
}

function HTTPSamplerForm({ node, schemaEntry, initialValues, onSubmit }: HTTPSamplerFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Record<string, unknown>>({
    resolver: zodResolver(schemaEntry.schema),
    defaultValues: initialValues,
    mode: 'onBlur',
  });

  const { updateProperty } = useTestPlanStore();
  const [activeTab, setActiveTab] = useState<HttpTab>('basic');
  const [activeSubTab, setActiveSubTab] = useState<HttpSubTab>('parameters');

  // Parameters table state
  const [paramRows, setParamRows] = useState<Row[]>(() => {
    const stored = node.properties.parameters;
    return Array.isArray(stored)
      ? stored.map((p: any) => ({
          name: p.name ?? '',
          value: p.value ?? '',
          urlEncode: p.urlEncode ?? false,
          contentType: p.contentType ?? '',
        }))
      : [];
  });

  // Body data state
  const [bodyData, setBodyData] = useState<string>(
    (node.properties.bodyData as string) ?? '',
  );

  // Files upload table state
  const [fileRows, setFileRows] = useState<Row[]>(() => {
    const stored = node.properties.filesUpload;
    return Array.isArray(stored)
      ? stored.map((f: any) => ({
          filePath: f.filePath ?? '',
          paramName: f.paramName ?? '',
          mimeType: f.mimeType ?? '',
        }))
      : [];
  });

  // Advanced tab state
  const [connectTimeout, setConnectTimeout] = useState(
    String(node.properties.connectTimeout ?? ''),
  );
  const [responseTimeout, setResponseTimeout] = useState(
    String(node.properties.responseTimeout ?? ''),
  );
  const [proxyHost, setProxyHost] = useState(
    (node.properties.proxyHost as string) ?? '',
  );
  const [proxyPort, setProxyPort] = useState(
    String(node.properties.proxyPort ?? ''),
  );
  const [proxyUser, setProxyUser] = useState(
    (node.properties.proxyUser as string) ?? '',
  );
  const [proxyPass, setProxyPass] = useState(
    (node.properties.proxyPass as string) ?? '',
  );

  // Sync when node changes
  useEffect(() => {
    const stored = node.properties.parameters;
    setParamRows(
      Array.isArray(stored)
        ? stored.map((p: any) => ({
            name: p.name ?? '',
            value: p.value ?? '',
            urlEncode: p.urlEncode ?? false,
            contentType: p.contentType ?? '',
          }))
        : [],
    );
    setBodyData((node.properties.bodyData as string) ?? '');
    const files = node.properties.filesUpload;
    setFileRows(
      Array.isArray(files)
        ? files.map((f: any) => ({
            filePath: f.filePath ?? '',
            paramName: f.paramName ?? '',
            mimeType: f.mimeType ?? '',
          }))
        : [],
    );
    setConnectTimeout(String(node.properties.connectTimeout ?? ''));
    setResponseTimeout(String(node.properties.responseTimeout ?? ''));
    setProxyHost((node.properties.proxyHost as string) ?? '');
    setProxyPort(String(node.properties.proxyPort ?? ''));
    setProxyUser((node.properties.proxyUser as string) ?? '');
    setProxyPass((node.properties.proxyPass as string) ?? '');
  }, [node.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleParamChange = useCallback(
    (rows: Row[]) => {
      setParamRows(rows);
      updateProperty(node.id, 'parameters', rows);
    },
    [node.id, updateProperty],
  );

  const handleFileChange = useCallback(
    (rows: Row[]) => {
      setFileRows(rows);
      updateProperty(node.id, 'filesUpload', rows);
    },
    [node.id, updateProperty],
  );

  const handleBodyDataBlur = useCallback(() => {
    updateProperty(node.id, 'bodyData', bodyData);
  }, [node.id, bodyData, updateProperty]);

  const handleAdvancedBlur = useCallback(
    (key: string, value: string) => {
      updateProperty(node.id, key, value);
    },
    [node.id, updateProperty],
  );

  const submitHandler = handleSubmit((data) => {
    onSubmit({
      ...data,
      parameters: paramRows,
      bodyData,
      filesUpload: fileRows,
      connectTimeout,
      responseTimeout,
      proxyHost,
      proxyPort,
      proxyUser,
      proxyPass,
    });
  });

  const errorFor = (key: string) => errors[key]?.message as string | undefined;

  return (
    <form className="property-form" onSubmit={(e) => void submitHandler(e)} noValidate>
      {/* Basic / Advanced tab bar */}
      <div className="http-tab-bar">
        <button
          type="button"
          className={`http-tab ${activeTab === 'basic' ? 'http-tab-active' : ''}`}
          onClick={() => setActiveTab('basic')}
        >
          Basic
        </button>
        <button
          type="button"
          className={`http-tab ${activeTab === 'advanced' ? 'http-tab-active' : ''}`}
          onClick={() => setActiveTab('advanced')}
        >
          Advanced
        </button>
      </div>

      {activeTab === 'basic' && (
        <>
          {/* Web Server fieldset */}
          <fieldset className="property-fieldset">
            <legend>Web Server</legend>
            <div className="form-field">
              <label htmlFor="http-protocol">Protocol [http]</label>
              <select id="http-protocol" {...register('protocol')}>
                <option value="http">http</option>
                <option value="https">https</option>
              </select>
            </div>
            <div className="form-field">
              <label htmlFor="http-domain">Server Name or IP</label>
              <input
                id="http-domain"
                type="text"
                className={errorFor('domain') ? 'field-error' : ''}
                {...register('domain')}
              />
              {errorFor('domain') && <span className="field-error-msg" role="alert">{errorFor('domain')}</span>}
            </div>
            <div className="form-field">
              <label htmlFor="http-port">Port Number</label>
              <input
                id="http-port"
                type="number"
                className={errorFor('port') ? 'field-error' : ''}
                {...register('port', { valueAsNumber: true })}
              />
              {errorFor('port') && <span className="field-error-msg" role="alert">{errorFor('port')}</span>}
            </div>
          </fieldset>

          {/* HTTP Request fieldset */}
          <fieldset className="property-fieldset">
            <legend>HTTP Request</legend>
            <div className="form-field">
              <label htmlFor="http-method">Method</label>
              <select id="http-method" {...register('method')}>
                {['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map((m) => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label htmlFor="http-path">Path</label>
              <input id="http-path" type="text" {...register('path')} />
            </div>
            <div className="form-field">
              <label htmlFor="http-encoding">Content Encoding</label>
              <input id="http-encoding" type="text" {...register('contentEncoding')} />
            </div>
          </fieldset>

          {/* Checkboxes */}
          <div className="form-field form-field-row" style={{ gap: 16, flexWrap: 'wrap' }}>
            <label className="checkbox-label">
              <input type="checkbox" checked={!!node.properties.followRedirects} onChange={(e) => updateProperty(node.id, 'followRedirects', e.target.checked)} />
              Follow Redirects
            </label>
            <label className="checkbox-label">
              <input type="checkbox" checked={!!node.properties.autoRedirects} onChange={(e) => updateProperty(node.id, 'autoRedirects', e.target.checked)} />
              Auto Redirects
            </label>
            <label className="checkbox-label">
              <input type="checkbox" checked={!!node.properties.useKeepAlive} onChange={(e) => updateProperty(node.id, 'useKeepAlive', e.target.checked)} />
              Use KeepAlive
            </label>
          </div>

          {/* Parameters / Body Data / Files Upload sub-tabs */}
          <div className="http-subtab-bar">
            <button
              type="button"
              className={`http-subtab ${activeSubTab === 'parameters' ? 'http-subtab-active' : ''}`}
              onClick={() => setActiveSubTab('parameters')}
            >
              Parameters
            </button>
            <button
              type="button"
              className={`http-subtab ${activeSubTab === 'bodyData' ? 'http-subtab-active' : ''}`}
              onClick={() => setActiveSubTab('bodyData')}
            >
              Body Data
            </button>
            <button
              type="button"
              className={`http-subtab ${activeSubTab === 'filesUpload' ? 'http-subtab-active' : ''}`}
              onClick={() => setActiveSubTab('filesUpload')}
            >
              Files Upload
            </button>
          </div>

          <div className="http-subtab-content">
            {activeSubTab === 'parameters' && (
              <EditableTable
                columns={HTTP_PARAM_COLUMNS}
                rows={paramRows}
                onChange={handleParamChange}
              />
            )}

            {activeSubTab === 'bodyData' && (
              <textarea
                className="http-body-textarea"
                value={bodyData}
                onChange={(e) => setBodyData(e.target.value)}
                onBlur={handleBodyDataBlur}
                placeholder="Enter request body here..."
                rows={8}
              />
            )}

            {activeSubTab === 'filesUpload' && (
              <EditableTable
                columns={HTTP_FILES_COLUMNS}
                rows={fileRows}
                onChange={handleFileChange}
              />
            )}
          </div>
        </>
      )}

      {activeTab === 'advanced' && (
        <>
          {/* Timeouts fieldset */}
          <fieldset className="property-fieldset">
            <legend>Timeouts (milliseconds)</legend>
            <div className="form-field">
              <label htmlFor="http-connect-timeout">Connect Timeout</label>
              <input
                id="http-connect-timeout"
                type="number"
                value={connectTimeout}
                onChange={(e) => setConnectTimeout(e.target.value)}
                onBlur={() => handleAdvancedBlur('connectTimeout', connectTimeout)}
                placeholder="0 = no timeout"
              />
            </div>
            <div className="form-field">
              <label htmlFor="http-response-timeout">Response Timeout</label>
              <input
                id="http-response-timeout"
                type="number"
                value={responseTimeout}
                onChange={(e) => setResponseTimeout(e.target.value)}
                onBlur={() => handleAdvancedBlur('responseTimeout', responseTimeout)}
                placeholder="0 = no timeout"
              />
            </div>
          </fieldset>

          {/* Proxy fieldset */}
          <fieldset className="property-fieldset">
            <legend>Proxy Server</legend>
            <div className="form-field">
              <label htmlFor="http-proxy-host">Server Name or IP</label>
              <input
                id="http-proxy-host"
                type="text"
                value={proxyHost}
                onChange={(e) => setProxyHost(e.target.value)}
                onBlur={() => handleAdvancedBlur('proxyHost', proxyHost)}
              />
            </div>
            <div className="form-field">
              <label htmlFor="http-proxy-port">Port Number</label>
              <input
                id="http-proxy-port"
                type="number"
                value={proxyPort}
                onChange={(e) => setProxyPort(e.target.value)}
                onBlur={() => handleAdvancedBlur('proxyPort', proxyPort)}
              />
            </div>
            <div className="form-field">
              <label htmlFor="http-proxy-user">Username</label>
              <input
                id="http-proxy-user"
                type="text"
                value={proxyUser}
                onChange={(e) => setProxyUser(e.target.value)}
                onBlur={() => handleAdvancedBlur('proxyUser', proxyUser)}
              />
            </div>
            <div className="form-field">
              <label htmlFor="http-proxy-pass">Password</label>
              <input
                id="http-proxy-pass"
                type="password"
                value={proxyPass}
                onChange={(e) => setProxyPass(e.target.value)}
                onBlur={() => handleAdvancedBlur('proxyPass', proxyPass)}
              />
            </div>
          </fieldset>
        </>
      )}

      <button type="submit" className="form-submit-btn">
        Apply
      </button>
    </form>
  );
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Recursively find a node by id within a node tree. */
function findNode(node: TestPlanNode, id: string): TestPlanNode | undefined {
  if (node.id === id) return node;
  for (const child of node.children) {
    const found = findNode(child, id);
    if (found !== undefined) return found;
  }
  return undefined;
}

/**
 * Build the initial form values by merging schema defaults with whatever is
 * already stored in node.properties.
 */
function buildInitialValues(
  defaults: Record<string, unknown>,
  stored: Record<string, unknown>,
): Record<string, unknown> {
  return { ...defaults, ...stored };
}

// ---------------------------------------------------------------------------
// UniversalFields — Name and Comments
// ---------------------------------------------------------------------------

interface UniversalFieldsProps {
  node: TestPlanNode;
}

/**
 * Renders Name and Comments inputs that are common to all JMeter elements.
 * Name is bound to node.name (via renameNode), Comments to node.properties.comments.
 */
function UniversalFields({ node }: UniversalFieldsProps) {
  const { renameNode, updateProperty } = useTestPlanStore();

  const [name, setName] = useState(node.name);
  const [comments, setComments] = useState(
    (node.properties.comments as string) ?? '',
  );

  // Sync local state when a different node is selected
  useEffect(() => {
    setName(node.name);
    setComments((node.properties.comments as string) ?? '');
  }, [node.id, node.name, node.properties.comments]);

  const handleNameBlur = useCallback(() => {
    if (name !== node.name) {
      renameNode(node.id, name);
    }
  }, [name, node.id, node.name, renameNode]);

  const handleCommentsBlur = useCallback(() => {
    const current = (node.properties.comments as string) ?? '';
    if (comments !== current) {
      updateProperty(node.id, 'comments', comments);
    }
  }, [comments, node.id, node.properties.comments, updateProperty]);

  return (
    <div className="universal-fields">
      <div className="form-field">
        <label htmlFor="universal-name">Name</label>
        <input
          id="universal-name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          onBlur={handleNameBlur}
        />
      </div>
      <div className="form-field">
        <label htmlFor="universal-comments">Comments</label>
        <input
          id="universal-comments"
          type="text"
          value={comments}
          onChange={(e) => setComments(e.target.value)}
          onBlur={handleCommentsBlur}
        />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// ON_SAMPLE_ERROR labels for radio buttons
// ---------------------------------------------------------------------------

const ON_SAMPLE_ERROR_OPTIONS: { value: string; label: string }[] = [
  { value: 'continue', label: 'Continue' },
  { value: 'startnextloop', label: 'Start Next Thread Loop' },
  { value: 'stopthread', label: 'Stop Thread' },
  { value: 'stoptest', label: 'Stop Test' },
  { value: 'stoptestnow', label: 'Stop Test Now' },
];

// ---------------------------------------------------------------------------
// ThreadGroupForm — custom hand-coded form with JMeter fieldsets
// ---------------------------------------------------------------------------

interface ThreadGroupFormProps {
  node: TestPlanNode;
  schemaEntry: SchemaEntry;
  initialValues: Record<string, unknown>;
  onSubmit: (values: Record<string, unknown>) => void;
}

function ThreadGroupForm({ node: _node, schemaEntry, initialValues, onSubmit }: ThreadGroupFormProps) {
  const {
    register,
    handleSubmit,
    setValue,
    control,
    formState: { errors },
  } = useForm<Record<string, unknown>>({
    resolver: zodResolver(schemaEntry.schema),
    defaultValues: initialValues,
    mode: 'onBlur',
  });

  // Watch reactive values for conditional rendering
  const infiniteValue = useWatch({ control, name: 'infinite' }) as boolean;
  const schedulerValue = useWatch({ control, name: 'scheduler' }) as boolean;

  // When infinite is toggled, set loops accordingly
  useEffect(() => {
    if (infiniteValue) {
      setValue('loops', -1);
    } else {
      const current = initialValues.loops as number;
      if (current === -1) {
        setValue('loops', 1);
      }
    }
  }, [infiniteValue, setValue, initialValues.loops]);

  const submitHandler = handleSubmit((data) => {
    onSubmit(data as Record<string, unknown>);
  });

  const errorFor = (key: string) => errors[key]?.message as string | undefined;

  return (
    <form className="property-form" onSubmit={(e) => void submitHandler(e)} noValidate>
      {/* Action on Sampler Error */}
      <fieldset className="property-fieldset">
        <legend>Action to be taken after a Sampler error</legend>
        <div className="form-field form-field-radios">
          {ON_SAMPLE_ERROR_OPTIONS.map((opt) => (
            <label key={opt.value} className="radio-label">
              <input
                type="radio"
                value={opt.value}
                {...register('onSampleError')}
              />
              {opt.label}
            </label>
          ))}
          {errorFor('onSampleError') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('onSampleError')}
            </span>
          )}
        </div>
      </fieldset>

      {/* Thread Properties */}
      <fieldset className="property-fieldset">
        <legend>Thread Properties</legend>

        <div className="form-field">
          <label htmlFor="field-num_threads">Number of Threads (users)</label>
          <input
            id="field-num_threads"
            type="number"
            className={errorFor('num_threads') !== undefined ? 'field-error' : ''}
            {...register('num_threads', { valueAsNumber: true })}
          />
          {errorFor('num_threads') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('num_threads')}
            </span>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="field-ramp_time">Ramp-up period (seconds)</label>
          <input
            id="field-ramp_time"
            type="number"
            className={errorFor('ramp_time') !== undefined ? 'field-error' : ''}
            {...register('ramp_time', { valueAsNumber: true })}
          />
          {errorFor('ramp_time') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('ramp_time')}
            </span>
          )}
        </div>

        <div className="form-field form-field-inline">
          <label htmlFor="field-loops">Loop Count</label>
          <div className="form-field-row">
            <input
              id="field-loops"
              type="number"
              className={errorFor('loops') !== undefined ? 'field-error' : ''}
              disabled={infiniteValue}
              {...register('loops', { valueAsNumber: true })}
            />
            <label className="checkbox-label">
              <input type="checkbox" {...register('infinite')} />
              Infinite
            </label>
          </div>
          {errorFor('loops') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('loops')}
            </span>
          )}
        </div>

        <div className="form-field">
          <label className="checkbox-label">
            <input type="checkbox" {...register('sameUserOnNextIteration')} />
            Same user on each iteration
          </label>
        </div>
      </fieldset>

      {/* Scheduler Configuration */}
      <fieldset className="property-fieldset">
        <legend>Scheduler Configuration</legend>

        <div className="form-field">
          <label className="checkbox-label">
            <input type="checkbox" {...register('scheduler')} />
            Scheduler
          </label>
        </div>

        <div className="form-field">
          <label htmlFor="field-duration">Duration (seconds)</label>
          <input
            id="field-duration"
            type="number"
            className={errorFor('duration') !== undefined ? 'field-error' : ''}
            disabled={!schedulerValue}
            {...register('duration', { valueAsNumber: true })}
          />
          {errorFor('duration') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('duration')}
            </span>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="field-delay">Startup delay (seconds)</label>
          <input
            id="field-delay"
            type="number"
            className={errorFor('delay') !== undefined ? 'field-error' : ''}
            disabled={!schedulerValue}
            {...register('delay', { valueAsNumber: true })}
          />
          {errorFor('delay') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('delay')}
            </span>
          )}
        </div>
      </fieldset>

      <button type="submit" className="form-submit-btn">
        Apply
      </button>
    </form>
  );
}

// ---------------------------------------------------------------------------
// TestPlanForm — custom hand-coded form with User Defined Variables table
// ---------------------------------------------------------------------------

/** EditableTable column definitions for User Defined Variables. */
const UDV_COLUMNS: Column[] = [
  { key: 'name', label: 'Name', type: 'text', width: '30%' },
  { key: 'value', label: 'Value', type: 'text', width: '35%' },
  { key: 'description', label: 'Description', type: 'text', width: '35%' },
];

interface TestPlanFormProps {
  node: TestPlanNode;
  schemaEntry: SchemaEntry;
  initialValues: Record<string, unknown>;
  onSubmit: (values: Record<string, unknown>) => void;
}

function TestPlanForm({ node, schemaEntry, initialValues, onSubmit }: TestPlanFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Record<string, unknown>>({
    resolver: zodResolver(schemaEntry.schema),
    defaultValues: initialValues,
    mode: 'onBlur',
  });

  // User-defined variables managed as local state and persisted on change
  const { updateProperty } = useTestPlanStore();
  const [udvRows, setUdvRows] = useState<Row[]>(() => {
    const stored = node.properties.userDefinedVariables;
    if (Array.isArray(stored)) {
      return stored.map((v: unknown) => {
        const item = v as UserDefinedVariable;
        return {
          name: item.name ?? '',
          value: item.value ?? '',
          description: item.description ?? '',
        };
      });
    }
    return [];
  });

  // Sync local UDV state when a different node is selected
  useEffect(() => {
    const stored = node.properties.userDefinedVariables;
    if (Array.isArray(stored)) {
      setUdvRows(
        stored.map((v: unknown) => {
          const item = v as UserDefinedVariable;
          return {
            name: item.name ?? '',
            value: item.value ?? '',
            description: item.description ?? '',
          };
        }),
      );
    } else {
      setUdvRows([]);
    }
  }, [node.id, node.properties.userDefinedVariables]);

  const handleUdvChange = useCallback(
    (rows: Row[]) => {
      setUdvRows(rows);
      // Persist immediately to the store
      updateProperty(node.id, 'userDefinedVariables', rows);
    },
    [node.id, updateProperty],
  );

  const submitHandler = handleSubmit((data) => {
    // Include the UDV rows in the submitted data
    onSubmit({ ...data, userDefinedVariables: udvRows });
  });

  const errorFor = (key: string) => errors[key]?.message as string | undefined;

  return (
    <form className="property-form" onSubmit={(e) => void submitHandler(e)} noValidate>
      {/* Checkboxes — these are already in the schema but name/comments are universal */}
      <fieldset className="property-fieldset">
        <legend>Test Plan Options</legend>

        <div className="form-field">
          <label className="checkbox-label">
            <input type="checkbox" {...register('functional_mode')} />
            Functional Test Mode (i.e. save Response Data and Sampler Data)
          </label>
        </div>

        <div className="form-field">
          <label className="checkbox-label">
            <input type="checkbox" {...register('teardown_on_shutdown')} />
            Run tearDown Thread Groups after shutdown of main threads
          </label>
        </div>

        <div className="form-field">
          <label className="checkbox-label">
            <input type="checkbox" {...register('serialize_threadgroups')} />
            Run Thread Groups consecutively (i.e. one at a time)
          </label>
        </div>

        {errorFor('functional_mode') !== undefined && (
          <span className="field-error-msg" role="alert">
            {errorFor('functional_mode')}
          </span>
        )}
      </fieldset>

      {/* User Defined Variables */}
      <fieldset className="property-fieldset">
        <legend>User Defined Variables</legend>
        <EditableTable
          columns={UDV_COLUMNS}
          rows={udvRows}
          onChange={handleUdvChange}
        />
      </fieldset>

      <button type="submit" className="form-submit-btn">
        Apply
      </button>
    </form>
  );
}

// ---------------------------------------------------------------------------
// GenericPropertyForm
// ---------------------------------------------------------------------------

interface GenericPropertyFormProps {
  node: TestPlanNode;
  onSubmit: (values: Record<string, unknown>) => void;
}

/**
 * Renders a generic form for any node whose type appears in the schema
 * registry.  Each schema field becomes a labelled input/select.
 *
 * Priority (T036):
 *   1. Hand-coded custom forms for ThreadGroup and TestPlan.
 *   2. Hand-coded Zod schema from the registry (T024) — full validation.
 *   3. Dynamic schema fetched from GET /api/v1/schemas/{type} — auto-generated.
 *   4. "No editable properties" placeholder when neither is available.
 */
function GenericPropertyForm({ node, onSubmit }: GenericPropertyFormProps) {
  const entry = getSchemaEntry(node.type);

  // --- Priority 1: custom hand-coded forms for specific types ---
  if (entry !== undefined) {
    const initialValues = buildInitialValues(entry.defaults, node.properties);

    if (node.type === 'ThreadGroup') {
      return (
        <ThreadGroupForm
          key={node.id}
          node={node}
          schemaEntry={entry}
          initialValues={initialValues}
          onSubmit={onSubmit}
        />
      );
    }

    if (node.type === 'HTTPSampler' || node.type === 'HTTPSamplerProxy') {
      return (
        <HTTPSamplerForm
          key={node.id}
          node={node}
          schemaEntry={entry}
          initialValues={initialValues}
          onSubmit={onSubmit}
        />
      );
    }

    if (node.type === 'TestPlan') {
      return (
        <TestPlanForm
          key={node.id}
          node={node}
          schemaEntry={entry}
          initialValues={initialValues}
          onSubmit={onSubmit}
        />
      );
    }

    // --- Priority 2: generic Zod-schema-driven form ---
    return (
      <PropertyForm
        key={node.id}
        nodeType={node.type}
        schemaEntry={entry}
        initialValues={initialValues}
        onSubmit={onSubmit}
      />
    );
  }

  // --- Priority 3 & 4: dynamic form with API schema fallback ---
  return (
    <DynamicFormFallback
      key={node.id}
      node={node}
      onSubmit={onSubmit}
    />
  );
}

// ---------------------------------------------------------------------------
// DynamicFormFallback — fetches schema from API and renders DynamicForm
// ---------------------------------------------------------------------------

interface DynamicFormFallbackProps {
  node: TestPlanNode;
  onSubmit: (values: Record<string, unknown>) => void;
}

/**
 * Fetches the component schema from {@code GET /api/v1/schemas/{type}} and
 * renders a {@link DynamicForm}.  Shows a loading state while the request is
 * in-flight and falls back to the "no editable properties" placeholder when
 * the schema is not available (404 or network error).
 */
function DynamicFormFallback({ node, onSubmit }: DynamicFormFallbackProps) {
  const [dynamicSchema, setDynamicSchema] = useState<ComponentSchemaDto | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setDynamicSchema(null);

    fetch(`/api/v1/schemas/${encodeURIComponent(node.type)}`, {
      headers: { Accept: 'application/json' },
    })
      .then((res) => {
        if (!res.ok) return null;
        return res.json() as Promise<ComponentSchemaDto>;
      })
      .then((schema) => {
        if (!cancelled) {
          setDynamicSchema(schema);
          setLoading(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [node.type]);

  if (loading) {
    return <p className="property-panel-empty">Loading properties...</p>;
  }

  if (dynamicSchema === null) {
    return (
      <p className="property-panel-empty">
        No editable properties for <strong>{node.type}</strong>.
      </p>
    );
  }

  return (
    <DynamicForm
      schema={dynamicSchema}
      initialValues={node.properties}
      onSubmit={onSubmit}
    />
  );
}

// ---------------------------------------------------------------------------
// PropertyForm — the inner react-hook-form component (generic fallback)
// ---------------------------------------------------------------------------

interface PropertyFormProps {
  nodeType: string;
  schemaEntry: SchemaEntry;
  initialValues: Record<string, unknown>;
  onSubmit: (values: Record<string, unknown>) => void;
}

/**
 * Schema-driven form component.  Iterates over the Zod schema's shape keys and
 * renders an appropriate input element for each field.
 *
 * When the node type has fieldset grouping (e.g. HTTPSampler), fields are
 * wrapped in <fieldset>/<legend> elements.  Ungrouped fields render after.
 *
 * Supported schema types detected at runtime:
 *   - ZodNumber  -> <input type="number">
 *   - ZodBoolean -> <input type="checkbox">
 *   - ZodEnum    -> <select>
 *   - ZodString  -> <input type="text">
 */
function PropertyForm({ nodeType, schemaEntry, initialValues, onSubmit }: PropertyFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Record<string, unknown>>({
    resolver: zodResolver(schemaEntry.schema),
    defaultValues: initialValues,
    mode: 'onBlur',
  });

  // Zod object schema exposes `.shape`; cast through unknown to access it
  // without requiring generics.
  const shape = (schemaEntry.schema as unknown as { shape: Record<string, unknown> }).shape ?? {};
  const fieldKeys = Object.keys(shape);

  function getFieldType(key: string): 'number' | 'enum' | 'boolean' | 'text' {
    const fieldSchema = shape[key] as
      | { _def: { typeName: string; values?: string[]; innerType?: { _def: { typeName: string; values?: string[] } } } }
      | undefined;
    if (fieldSchema === undefined) return 'text';
    const typeName = fieldSchema._def?.typeName ?? '';
    // Handle ZodDefault and ZodOptional wrappers
    if (typeName === 'ZodDefault' || typeName === 'ZodOptional') {
      const innerTypeName = fieldSchema._def?.innerType?._def?.typeName ?? '';
      if (innerTypeName === 'ZodNumber') return 'number';
      if (innerTypeName === 'ZodBoolean') return 'boolean';
      if (innerTypeName === 'ZodEnum') return 'enum';
      return 'text';
    }
    if (typeName === 'ZodNumber') return 'number';
    if (typeName === 'ZodBoolean') return 'boolean';
    if (typeName === 'ZodEnum') return 'enum';
    return 'text';
  }

  function getEnumValues(key: string): string[] {
    const fieldSchema = shape[key] as
      | { _def: { values?: string[]; innerType?: { _def: { values?: string[] } } } }
      | undefined;
    return fieldSchema?._def?.values ?? fieldSchema?._def?.innerType?._def?.values ?? [];
  }

  const submitHandler = handleSubmit((data) => {
    onSubmit(data as Record<string, unknown>);
  });

  // Build a single form field element
  function renderField(key: string) {
    const fieldType = getFieldType(key);
    const error = errors[key];
    const errorMessage = error?.message as string | undefined;
    const inputClass = `${errorMessage !== undefined ? 'field-error' : ''}`;

    if (fieldType === 'boolean') {
      return (
        <div className="form-field" key={key}>
          <label className="checkbox-label">
            <input
              id={`field-${key}`}
              type="checkbox"
              {...register(key)}
            />
            {key}
          </label>
          {errorMessage !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorMessage}
            </span>
          )}
        </div>
      );
    }

    return (
      <div className="form-field" key={key}>
        <label htmlFor={`field-${key}`}>{key}</label>

        {fieldType === 'enum' ? (
          <select
            id={`field-${key}`}
            className={inputClass}
            {...register(key)}
          >
            {getEnumValues(key).map((v) => (
              <option key={v} value={v}>
                {v}
              </option>
            ))}
          </select>
        ) : (
          <input
            id={`field-${key}`}
            type={fieldType === 'number' ? 'number' : 'text'}
            className={inputClass}
            {...register(key, {
              valueAsNumber: fieldType === 'number',
            })}
          />
        )}

        {errorMessage !== undefined && (
          <span className="field-error-msg" role="alert">
            {errorMessage}
          </span>
        )}
      </div>
    );
  }

  // Check for fieldset grouping
  const fieldsets = getFieldsets(nodeType);

  if (fieldsets !== null) {
    const groupedFields = new Set(fieldsets.flatMap((fs) => fs.fields));
    const ungroupedKeys = fieldKeys.filter((key) => !groupedFields.has(key));

    return (
      <form className="property-form" onSubmit={(e) => void submitHandler(e)} noValidate>
        {fieldsets.map((fs) => (
          <fieldset key={fs.legend} className="property-fieldset">
            <legend>{fs.legend}</legend>
            {fs.fields
              .filter((f) => fieldKeys.includes(f))
              .map((key) => renderField(key))}
          </fieldset>
        ))}

        {ungroupedKeys.length > 0 && ungroupedKeys.map((key) => renderField(key))}

        <button type="submit" className="form-submit-btn">
          Apply
        </button>
      </form>
    );
  }

  return (
    <form className="property-form" onSubmit={(e) => void submitHandler(e)} noValidate>
      {fieldKeys.map((key) => renderField(key))}

      <button type="submit" className="form-submit-btn">
        Apply
      </button>
    </form>
  );
}

// ---------------------------------------------------------------------------
// PropertyPanel — public component
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// JmxSummary — read-only summary of JMX XML structure for TestPlan nodes
// ---------------------------------------------------------------------------

interface JmxSummaryProps {
  planId: string;
}

/**
 * Parses the raw JMX XML string using simple regex counting and displays a
 * compact summary of the plan structure (thread groups, samplers, assertions).
 */
function JmxSummary({ planId }: JmxSummaryProps) {
  const jmxXml = useUiStore((s) => s.planXmlMap[planId]);

  const summary = useMemo(() => {
    if (!jmxXml) return null;

    const count = (pattern: RegExp): number => {
      const matches = jmxXml.match(pattern);
      return matches ? matches.length : 0;
    };

    return {
      threadGroups: count(/<ThreadGroup\b/g) + count(/<SetupThreadGroup\b/g) + count(/<PostThreadGroup\b/g),
      samplers:
        count(/<HTTPSamplerProxy\b/g) +
        count(/<HTTPSampler\b/g) +
        count(/<TCPSampler\b/g) +
        count(/<JDBCSampler\b/g) +
        count(/<FTPSampler\b/g) +
        count(/<SMTPSampler\b/g) +
        count(/<LDAPSampler\b/g) +
        count(/<JMSSampler\b/g) +
        count(/<BoltSampler\b/g),
      assertions:
        count(/<ResponseAssertion\b/g) +
        count(/<DurationAssertion\b/g) +
        count(/<SizeAssertion\b/g) +
        count(/<JSONPathAssertion\b/g) +
        count(/<XPathAssertion\b/g),
      timers:
        count(/<ConstantTimer\b/g) +
        count(/<GaussianRandomTimer\b/g) +
        count(/<UniformRandomTimer\b/g),
      controllers:
        count(/<LoopController\b/g) +
        count(/<IfController\b/g) +
        count(/<WhileController\b/g) +
        count(/<TransactionController\b/g),
      listeners:
        count(/<ResultCollector\b/g) +
        count(/<Summariser\b/g),
    };
  }, [jmxXml]);

  if (!summary) {
    return (
      <fieldset className="property-fieldset">
        <legend>JMX Structure</legend>
        <p className="property-panel-empty" style={{ fontSize: 12 }}>
          Source: Created in UI
        </p>
      </fieldset>
    );
  }

  return (
    <fieldset className="property-fieldset">
      <legend>JMX Structure</legend>
      <div style={{ fontSize: 12, lineHeight: 1.8 }}>
        <div><strong>Thread Groups:</strong> {summary.threadGroups}</div>
        <div><strong>Samplers:</strong> {summary.samplers}</div>
        <div><strong>Assertions:</strong> {summary.assertions}</div>
        <div><strong>Timers:</strong> {summary.timers}</div>
        <div><strong>Controllers:</strong> {summary.controllers}</div>
        <div><strong>Listeners:</strong> {summary.listeners}</div>
        <div style={{ marginTop: 6, color: 'var(--text-secondary, #94a3b8)' }}>
          Source: Imported JMX
        </div>
      </div>
    </fieldset>
  );
}

// ---------------------------------------------------------------------------
// PropertyPanel — public component
// ---------------------------------------------------------------------------

/**
 * PropertyPanel is the right-hand pane that shows editable properties for the
 * node currently selected in the tree.  When nothing is selected it renders a
 * placeholder message.
 */
export function PropertyPanel() {
  const { tree, selectedNodeId, updateProperty } = useTestPlanStore();

  // Resolve the currently selected node
  const selectedNode: TestPlanNode | undefined =
    selectedNodeId !== null && tree !== null
      ? findNode(tree.root, selectedNodeId)
      : undefined;

  function handleSubmit(values: Record<string, unknown>) {
    if (selectedNode === undefined) return;
    for (const [key, value] of Object.entries(values)) {
      updateProperty(selectedNode.id, key, value);
    }
  }

  return (
    <div className="property-panel-container">
      {selectedNode === undefined ? (
        <>
          <h2 className="property-panel-heading">Properties</h2>
          <p className="property-panel-empty">Select a node to edit its properties.</p>
        </>
      ) : (
        <>
          <h2 className="property-panel-type-title">
            {getDisplayName(selectedNode.type)}
          </h2>
          <UniversalFields node={selectedNode} />
          <hr className="property-panel-separator" />
          <GenericPropertyForm node={selectedNode} onSubmit={handleSubmit} />
          {selectedNode.type === 'TestPlan' && (
            <JmxSummary planId={selectedNode.id} />
          )}
        </>
      )}
    </div>
  );
}
