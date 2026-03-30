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
import { useEffect, useState, useCallback } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTestPlanStore } from '../../store/testPlanStore.js';
import type { TestPlanNode } from '../../types/test-plan.js';
import type { SchemaEntry } from './schemas/index.js';
import { EditableTable } from '../EditableTable/EditableTable.js';
import type { Row } from '../EditableTable/EditableTable.js';
import { HTTP_PARAM_COLUMNS, HTTP_FILES_COLUMNS } from './constants.js';

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

export function HTTPSamplerForm({ node, schemaEntry, initialValues, onSubmit }: HTTPSamplerFormProps) {
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
