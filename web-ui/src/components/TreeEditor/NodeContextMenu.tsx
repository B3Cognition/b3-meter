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
import type { CSSProperties } from 'react';
import { useEffect, useRef, useState } from 'react';
import { useTestPlanStore } from '../../store/testPlanStore.js';
import type { TestPlanNode } from '../../types/test-plan.js';

export interface ContextMenuPosition {
  x: number;
  y: number;
}

export interface NodeContextMenuProps {
  node: TestPlanNode;
  position: ContextMenuPosition;
  onClose: () => void;
}

/* ─── Clipboard (module-level, shared across renders) ─── */

let clipboardNode: TestPlanNode | null = null;

export function getClipboardNode(): TestPlanNode | null {
  return clipboardNode;
}

export function setClipboardNode(node: TestPlanNode | null): void {
  clipboardNode = node;
}

/* ─── Default properties for each element type ─── */

const DEFAULT_PROPERTIES: Record<string, Record<string, unknown>> = {
  // Samplers
  HTTPSampler: { protocol: 'https', domain: '', port: '', path: '/', method: 'GET', contentEncoding: 'UTF-8' },
  TCPSampler: { server: '', port: '', timeout: '0', reUseConnection: true, closeConnection: false, requestData: '' },
  JDBCSampler: { dataSource: '', queryType: 'Select Statement', query: '', parameterValues: '', resultVariable: '' },
  FTPSampler: { server: '', port: '21', remoteFile: '', localFile: '', binaryMode: false, saveResponse: false },
  SMTPSampler: { server: '', port: '25', from: '', to: '', subject: '', message: '', useSSL: false, useTLS: false },
  GrpcSampler: { host: 'localhost', port: '50051', service: 'greeter.Greeter', method: 'SayHello', requestBody: '{"name":"World"}', useTls: false },
  MQTTSampler: { broker: 'tcp://localhost:1883', topic: 'test/topic', message: '', qos: '0', action: 'publish', timeout: '5000' },
  WebSocketSampler: { url: 'ws://localhost:8082', message: '', connectTimeout: '5000', responseTimeout: '10000' },
  SSESampler: { url: 'http://localhost:8083/events', duration: '5000', eventName: '' },
  HLSSampler: { url: 'http://localhost:8084/live/master.m3u8', quality: 'best', segmentCount: '3', connectTimeout: '5000', responseTimeout: '10000' },
  DASHSampler: { url: 'http://localhost:8086/live/manifest.mpd', quality: 'best', segmentCount: '3', connectTimeout: '5000' },
  LDAPSampler: { serverUrl: '', rootDn: '', searchBase: '', searchFilter: '(objectClass=*)', scope: 'sub', authType: 'simple' },
  WebRTCSampler: { signalingUrl: 'http://localhost:8088/offer', offerSdp: 'generate', connectTimeout: '10000' },
  JSR223Sampler: { script: '// Write your script here\nSampleResult.setResponseData("Hello from script", "UTF-8");\nSampleResult.setResponseCode("200");', scriptLanguage: 'groovy', scriptFile: '', parameters: '' },
  BeanShellSampler: { script: '', filename: '', parameters: '', resetInterpreter: false },
  OSProcessSampler: { command: '', arguments: '', workingDirectory: '.', environment: '', timeout: 60000, expectedReturnCode: 0 },
  DebugSampler: { displayJMeterVariables: true, displaySystemProperties: false },
  // Config Elements
  CSVDataSet: { filename: '', fileEncoding: 'UTF-8', variableNames: '', delimiter: ',', ignoreFirstLine: false, recycle: true, stopThread: false, shareMode: 'shareMode.all' },
  CookieManager: { clearEachIteration: false, cookiePolicy: 'standard' },
  HeaderManager: { headers: [] },
  HTTPRequestDefaults: { protocol: 'https', domain: '', port: '', path: '', contentEncoding: 'UTF-8', connectTimeout: '', responseTimeout: '' },
  UserDefinedVariables: { variables: [] },
  DNSCacheManager: { clearEachIteration: false, isCustomResolver: false, servers: '' },
  KeystoreConfig: { preload: true, startIndex: '0', endIndex: '', clientCertAliasVarName: '' },
  LoginConfig: { username: '', password: '' },
  LDAPSamplerBase: { servername: '', port: '389', rootdn: '', test: '', base_entry_dn: '' },
  // Timers
  ConstantTimer: { delay: '300' },
  GaussianRandomTimer: { delay: '300', deviation: '100' },
  UniformRandomTimer: { delay: '100', range: '500' },
  ConstantThroughputTimer: { throughput: '60', calcMode: '0' },
  SynchronizingTimer: { groupSize: '0', timeoutInMs: '0' },
  PoissonRandomTimer: { lambda: '300', constantDelay: '100' },
  // Assertions
  ResponseAssertion: { testField: 'TEXT', testType: 'CONTAINS', testStrings: [], assumeSuccess: false },
  DurationAssertion: { duration: '1000' },
  SizeAssertion: { size: '0', operator: '<' },
  HTMLAssertion: { errorThreshold: 0, warningThreshold: 0, doctype: '', format: 'html' },
  BeanShellAssertion: { script: '', filename: '', parameters: '', resetInterpreter: false },
  CompareAssertion: { compareContent: true, compareTime: 0, compareHeaders: false, expectedContent: '', expectedTime: 0 },
  // Pre Processors
  HTMLLinkParser: {},
  HTTPURLRewritingModifier: { argument: '', pathExtension: false, pathExtensionNoEquals: false, pathExtensionNoQuestionmark: false },
  // Post Processors
  RegexExtractor: { referenceName: '', regularExpression: '', template: '$1$', matchNumber: '1', defaultValue: '' },
  JSONExtractor: { referenceName: '', jsonPathExpressions: '', matchNumber: '1', defaultValues: '' },
  JSR223PostProcessor: { script: '', scriptLanguage: 'groovy', filename: '', parameters: '' },
  BeanShellPostProcessor: { script: '', filename: '', parameters: '' },
  // Listeners
  ResultCollector: { filename: '', errorLogging: false },
  ViewResultsTable: { filename: '', errorLogging: false },
  Summariser: { name: 'summary' },
  AggregateReport: { filename: '', saveHeaders: true },
  BackendListener: { className: 'graphite', metricPrefix: 'jmeter', flushIntervalMs: '5000', graphiteHost: 'localhost', graphitePort: '2003', influxdbUrl: 'http://localhost:8086', influxdbToken: '', influxdbBucket: 'jmeter', influxdbOrg: '' },
  // Controllers
  LoopController: { loops: '1', continueForever: false },
  IfController: { condition: '', evaluateAll: false, useExpression: true },
  WhileController: { condition: '' },
  TransactionController: { includeTimers: false, generateParentSample: false },
  SimpleController: {},
  ModuleController: { nodePath: '' },
  IncludeController: { includePath: '' },
  // Thread Group
  ThreadGroup: { numThreads: '1', rampTime: '1', loops: '1', scheduler: false, duration: '', delay: '' },
};

/* ─── Submenu definitions ─── */

interface MenuCategory {
  label: string;
  items: { type: string; label: string }[];
}

const ADD_SUBMENU: MenuCategory[] = [
  {
    label: 'Sampler',
    items: [
      { type: 'HTTPSampler', label: 'HTTP Request' },
      { type: 'TCPSampler', label: 'TCP Sampler' },
      { type: 'JDBCSampler', label: 'JDBC Request' },
      { type: 'FTPSampler', label: 'FTP Request' },
      { type: 'SMTPSampler', label: 'SMTP Sampler' },
      { type: 'GrpcSampler', label: 'gRPC Request' },
      { type: 'MQTTSampler', label: 'MQTT Request' },
      { type: 'WebSocketSampler', label: 'WebSocket Request' },
      { type: 'SSESampler', label: 'SSE Request' },
      { type: 'HLSSampler', label: 'HLS Request' },
      { type: 'DASHSampler', label: 'DASH Request' },
      { type: 'LDAPSampler', label: 'LDAP Request' },
      { type: 'WebRTCSampler', label: 'WebRTC Signaling' },
      { type: 'JSR223Sampler', label: 'JSR223 Sampler' },
      { type: 'BeanShellSampler', label: 'BeanShell Sampler' },
      { type: 'OSProcessSampler', label: 'OS Process Sampler' },
      { type: 'DebugSampler', label: 'Debug Sampler' },
    ],
  },
  {
    label: 'Config Element',
    items: [
      { type: 'CSVDataSet', label: 'CSV Data Set Config' },
      { type: 'CookieManager', label: 'HTTP Cookie Manager' },
      { type: 'HeaderManager', label: 'HTTP Header Manager' },
      { type: 'HTTPRequestDefaults', label: 'HTTP Request Defaults' },
      { type: 'UserDefinedVariables', label: 'User Defined Variables' },
      { type: 'DNSCacheManager', label: 'DNS Cache Manager' },
      { type: 'KeystoreConfig', label: 'Keystore Configuration' },
      { type: 'LoginConfig', label: 'Login Config Element' },
      { type: 'LDAPSamplerBase', label: 'LDAP Request Defaults' },
    ],
  },
  {
    label: 'Timer',
    items: [
      { type: 'ConstantTimer', label: 'Constant Timer' },
      { type: 'GaussianRandomTimer', label: 'Gaussian Random Timer' },
      { type: 'UniformRandomTimer', label: 'Uniform Random Timer' },
      { type: 'ConstantThroughputTimer', label: 'Constant Throughput Timer' },
      { type: 'SynchronizingTimer', label: 'Synchronizing Timer' },
      { type: 'PoissonRandomTimer', label: 'Poisson Random Timer' },
    ],
  },
  {
    label: 'Assertion',
    items: [
      { type: 'ResponseAssertion', label: 'Response Assertion' },
      { type: 'DurationAssertion', label: 'Duration Assertion' },
      { type: 'SizeAssertion', label: 'Size Assertion' },
      { type: 'HTMLAssertion', label: 'HTML Assertion' },
      { type: 'BeanShellAssertion', label: 'BeanShell Assertion' },
      { type: 'CompareAssertion', label: 'Compare Assertion' },
    ],
  },
  {
    label: 'Pre Processor',
    items: [
      { type: 'HTMLLinkParser', label: 'HTML Link Parser' },
      { type: 'HTTPURLRewritingModifier', label: 'HTTP URL Re-writing Modifier' },
    ],
  },
  {
    label: 'Post Processor',
    items: [
      { type: 'RegexExtractor', label: 'Regular Expression Extractor' },
      { type: 'JSONExtractor', label: 'JSON Extractor' },
      { type: 'JSR223PostProcessor', label: 'JSR223 PostProcessor' },
      { type: 'BeanShellPostProcessor', label: 'BeanShell PostProcessor' },
    ],
  },
  {
    label: 'Listener',
    items: [
      { type: 'ResultCollector', label: 'View Results Tree' },
      { type: 'ViewResultsTable', label: 'View Results in Table' },
      { type: 'Summariser', label: 'Summary Report' },
      { type: 'AggregateReport', label: 'Aggregate Report' },
      { type: 'BackendListener', label: 'Backend Listener' },
    ],
  },
  {
    label: 'Controller',
    items: [
      { type: 'LoopController', label: 'Loop Controller' },
      { type: 'IfController', label: 'If Controller' },
      { type: 'WhileController', label: 'While Controller' },
      { type: 'TransactionController', label: 'Transaction Controller' },
      { type: 'SimpleController', label: 'Simple Controller' },
      { type: 'ModuleController', label: 'Module Controller' },
      { type: 'IncludeController', label: 'Include Controller' },
    ],
  },
];

/* ─── Helpers ─── */

function makeChildNode(parentId: string, type: string, label: string): TestPlanNode {
  return {
    id: `${parentId}-${type}-${Date.now()}`,
    type,
    name: label,
    enabled: true,
    properties: { ...(DEFAULT_PROPERTIES[type] ?? {}) },
    children: [],
  };
}

function deepCloneNode(node: TestPlanNode, idSuffix: string): TestPlanNode {
  return {
    ...node,
    id: `${node.id}-${idSuffix}`,
    name: `${node.name} (copy)`,
    properties: { ...node.properties },
    children: node.children.map((c) => deepCloneNode(c, idSuffix)),
  };
}

/** Find the parent of a node by id. Returns undefined if node is root. */
function findParent(root: TestPlanNode, targetId: string): TestPlanNode | undefined {
  for (const child of root.children) {
    if (child.id === targetId) return root;
    const found = findParent(child, targetId);
    if (found) return found;
  }
  return undefined;
}

/** Find the index of a child within its parent. */
function findChildIndex(parent: TestPlanNode, childId: string): number {
  return parent.children.findIndex((c) => c.id === childId);
}

/**
 * NodeContextMenu renders a positioned context menu anchored to the given
 * (x, y) coordinates with full JMeter 5.x cascading Add submenus.
 * Closes on any outside click or Escape key.
 */
export function NodeContextMenu({ node, position, onClose }: NodeContextMenuProps) {
  const { tree, addNode, deleteNode, moveNode, updateProperty } = useTestPlanStore();
  const menuRef = useRef<HTMLDivElement>(null);
  const [openSubmenu, setOpenSubmenu] = useState<string | null>(null);
  const [openCategory, setOpenCategory] = useState<string | null>(null);

  useEffect(() => {
    function handleOutsideClick(e: MouseEvent) {
      if (menuRef.current !== null && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    }

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        onClose();
      }
    }

    document.addEventListener('mousedown', handleOutsideClick);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose]);

  const menuStyle: CSSProperties = {
    top: position.y,
    left: position.x,
  };

  const isTestPlan = node.type === 'TestPlan';

  function handleAddChild(type: string, label: string) {
    addNode(node.id, makeChildNode(node.id, type, label));
    onClose();
  }

  function handleCopy() {
    clipboardNode = deepCloneNode(node, `clip-${Date.now()}`);
    onClose();
  }

  function handlePaste() {
    if (!clipboardNode) return;
    const pasted = deepCloneNode(clipboardNode, `paste-${Date.now()}`);
    addNode(node.id, pasted);
    onClose();
  }

  function handleDuplicate() {
    if (!tree) return;
    const parent = findParent(tree.root, node.id);
    if (!parent) return; // cannot duplicate root
    const clone = deepCloneNode(node, `dup-${Date.now()}`);
    const idx = findChildIndex(parent, node.id);
    // addNode appends to end; we use moveNode to reposition after add
    addNode(parent.id, clone);
    // After add, clone is at end of parent.children. Move to idx+1.
    // Since addNode already mutated, we need a fresh parent ref. Use moveNode instead.
    moveNode(clone.id, parent.id, idx + 1);
    onClose();
  }

  function handleMoveUp() {
    if (!tree) return;
    const parent = findParent(tree.root, node.id);
    if (!parent) return;
    const idx = findChildIndex(parent, node.id);
    if (idx <= 0) return;
    moveNode(node.id, parent.id, idx - 1);
    onClose();
  }

  function handleMoveDown() {
    if (!tree) return;
    const parent = findParent(tree.root, node.id);
    if (!parent) return;
    const idx = findChildIndex(parent, node.id);
    if (idx < 0 || idx >= parent.children.length - 1) return;
    // moveNode removes first then inserts at index, so target is idx+1 in the original array
    // After removal, the item at idx+1 shifts to idx, so inserting at idx+1 puts us after it
    moveNode(node.id, parent.id, idx + 2);
    onClose();
  }

  function handleToggleEnabled() {
    updateProperty(node.id, 'enabled', !node.enabled);
    onClose();
  }

  function handleDelete() {
    deleteNode(node.id);
    onClose();
  }

  return (
    <div ref={menuRef} className="context-menu" style={menuStyle} role="menu">
      {/* ─── Add Submenu ─── */}
      <div
        className="context-menu-item context-menu-submenu-trigger"
        onMouseEnter={() => setOpenSubmenu('add')}
        onMouseLeave={() => setOpenSubmenu(null)}
        role="menuitem"
      >
        <span>Add</span>
        <span className="context-menu-arrow">{'\u25B6'}</span>
        {openSubmenu === 'add' && (
          <div className="context-menu context-menu-sub" role="menu">
            {ADD_SUBMENU.map((category) => (
              <div
                key={category.label}
                className="context-menu-item context-menu-submenu-trigger"
                onMouseEnter={() => setOpenCategory(category.label)}
                onMouseLeave={() => setOpenCategory(null)}
                role="menuitem"
              >
                <span>{category.label}</span>
                <span className="context-menu-arrow">{'\u25B6'}</span>
                {openCategory === category.label && (
                  <div className="context-menu context-menu-sub" role="menu">
                    {category.items.map((item) => (
                      <button
                        key={item.type}
                        className="context-menu-item"
                        onClick={() => handleAddChild(item.type, item.label)}
                        role="menuitem"
                      >
                        {item.label}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}

            {/* Thread Group — only when parent is TestPlan */}
            {isTestPlan && (
              <>
                <div className="context-menu-separator" />
                <button
                  className="context-menu-item"
                  onClick={() => handleAddChild('ThreadGroup', 'Thread Group')}
                  role="menuitem"
                >
                  Thread Group
                </button>
              </>
            )}
          </div>
        )}
      </div>

      <div className="context-menu-separator" />

      {/* ─── Copy / Paste ─── */}
      <button className="context-menu-item" onClick={handleCopy} role="menuitem">
        <span>Copy</span>
        <span className="context-menu-shortcut">Ctrl+C</span>
      </button>
      <button
        className="context-menu-item"
        onClick={handlePaste}
        disabled={!clipboardNode}
        role="menuitem"
      >
        <span>Paste</span>
        <span className="context-menu-shortcut">Ctrl+V</span>
      </button>
      <button className="context-menu-item" onClick={handleDuplicate} role="menuitem">
        <span>Duplicate</span>
        <span className="context-menu-shortcut">Ctrl+D</span>
      </button>

      <div className="context-menu-separator" />

      {/* ─── Move Up / Down ─── */}
      <button className="context-menu-item" onClick={handleMoveUp} role="menuitem">
        <span>Move Up</span>
        <span className="context-menu-shortcut">Alt+{'\u2191'}</span>
      </button>
      <button className="context-menu-item" onClick={handleMoveDown} role="menuitem">
        <span>Move Down</span>
        <span className="context-menu-shortcut">Alt+{'\u2193'}</span>
      </button>

      <div className="context-menu-separator" />

      {/* ─── Enable / Disable ─── */}
      <button className="context-menu-item" onClick={handleToggleEnabled} role="menuitem">
        <span>{node.enabled ? 'Disable' : 'Enable'}</span>
        <span className="context-menu-shortcut">Ctrl+T</span>
      </button>

      <div className="context-menu-separator" />

      {/* ─── Delete ─── */}
      <button className="context-menu-item danger" onClick={handleDelete} role="menuitem">
        <span>Delete</span>
        <span className="context-menu-shortcut">Del</span>
      </button>
    </div>
  );
}
