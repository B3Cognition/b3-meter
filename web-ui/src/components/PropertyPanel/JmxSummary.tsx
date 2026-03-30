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
import { useMemo } from 'react';
import { useUiStore } from '../../store/uiStore.js';

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
export function JmxSummary({ planId }: JmxSummaryProps) {
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
