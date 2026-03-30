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
import { useState, useEffect } from 'react';
import { SummaryReport } from './SummaryReport.js';
import { AggregateReport } from './AggregateReport.js';
import { ViewResultsTree } from './ViewResultsTree.js';
import { LiveDashboard } from './LiveDashboard.js';
import { useRunStream } from '../../hooks/useRunStream.js';
import { useRunStore } from '../../store/runStore.js';
import { getMetrics } from '../../api/runs.js';

export type TabId = 'summary' | 'aggregate' | 'tree' | 'charts';

interface Tab {
  id: TabId;
  label: string;
}

const TABS: Tab[] = [
  { id: 'summary',   label: 'Summary Report'    },
  { id: 'aggregate', label: 'Aggregate Report'   },
  { id: 'tree',      label: 'View Results Tree'  },
  { id: 'charts',    label: 'Charts'             },
];

interface ResultsTabsProps {
  /** Active run ID, forwarded to LiveDashboard. Pass null when idle. */
  runId?: string | null;
  /** Initial tab to display. Defaults to 'summary'. */
  defaultTab?: TabId;
}

export function ResultsTabs({ runId: propRunId = null, defaultTab = 'summary' }: ResultsTabsProps) {
  const [activeTab, setActiveTab] = useState<TabId>(defaultTab);
  const status = useRunStore((s) => s.status);
  // Read runId directly from store — props may lag behind during React batching
  const storeRunId = useRunStore((s) => s.runId);
  const runId = storeRunId ?? propRunId;

  const addSample = useRunStore((s) => s.addSample);
  const setStatus = useRunStore((s) => s.setStatus);
  // Keep SSE connection alive across all tab switches
  useRunStream(runId ?? null, { enabled: status === 'running' || status === 'starting' });

  // Simple polling: whenever we have a runId, poll every 1s for both status and metrics
  useEffect(() => {
    if (!runId) return;
    let stopped = false;

    const poll = async () => {
      try {
        // Check run status
        const runRes = await fetch(`/api/v1/runs/${runId}`);
        if (runRes.ok) {
          const run = await runRes.json();
          const s = (run.status || '').toUpperCase();
          if (s === 'STOPPED' || s === 'ERROR' || s === 'COMPLETED') {
            setStatus(s === 'ERROR' ? 'error' : 'stopped');
          }
        }
        // Fetch metrics
        const m = await getMetrics(runId);
        if (m.sampleCount > 0) {
          addSample(m as any);
          stopped = true; // Got data, stop polling
        }
      } catch { /* ignore */ }
    };

    const interval = setInterval(() => {
      if (!stopped) poll();
    }, 1000);
    // Also poll immediately
    poll();

    return () => clearInterval(interval);
  }, [runId, addSample, setStatus]);

  return (
    <div className="results-tabs" data-testid="results-tabs" style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Tab bar */}
        <div className="results-tabs__bar" role="tablist" aria-label="Result views">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              role="tab"
              aria-selected={activeTab === tab.id}
              aria-controls={`tabpanel-${tab.id}`}
              id={`tab-${tab.id}`}
              className={`results-tabs__tab${activeTab === tab.id ? ' results-tabs__tab--active' : ''}`}
              onClick={() => setActiveTab(tab.id)}
              data-testid={`tab-${tab.id}`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Tab panels */}
        <div className="results-tabs__panels">
          <div
            role="tabpanel"
            id="tabpanel-summary"
            aria-labelledby="tab-summary"
            hidden={activeTab !== 'summary'}
            data-testid="tabpanel-summary"
          >
            {activeTab === 'summary' && <SummaryReport />}
          </div>

          <div
            role="tabpanel"
            id="tabpanel-aggregate"
            aria-labelledby="tab-aggregate"
            hidden={activeTab !== 'aggregate'}
            data-testid="tabpanel-aggregate"
          >
            {activeTab === 'aggregate' && <AggregateReport />}
          </div>

          <div
            role="tabpanel"
            id="tabpanel-tree"
            aria-labelledby="tab-tree"
            hidden={activeTab !== 'tree'}
            data-testid="tabpanel-tree"
          >
            {activeTab === 'tree' && <ViewResultsTree />}
          </div>

          <div
            role="tabpanel"
            id="tabpanel-charts"
            aria-labelledby="tab-charts"
            hidden={activeTab !== 'charts'}
            data-testid="tabpanel-charts"
          >
            {activeTab === 'charts' && <LiveDashboard runId={runId} />}
          </div>
        </div>
    </div>
  );
}
