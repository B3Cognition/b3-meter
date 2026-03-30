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
import { useEffect, useRef, useState } from 'react';
import { ChevronDown, ChevronUp, Trash2, X } from 'lucide-react';
import { useLogStore } from '../../store/logStore.js';
import type { LogLevel } from '../../store/logStore.js';
import './LogViewer.css';

/** Format an ISO timestamp to a short time string. */
function formatTime(iso: string): string {
  try {
    const d = new Date(iso);
    return d.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
      + '.' + String(d.getMilliseconds()).padStart(3, '0');
  } catch {
    return iso;
  }
}

/** CSS class for a log level badge. */
function levelClass(level: LogLevel): string {
  switch (level) {
    case 'ERROR': return 'log-level-error';
    case 'WARN': return 'log-level-warn';
    default: return 'log-level-info';
  }
}

export function LogViewer() {
  const { entries, visible, clearLogs, setVisible } = useLogStore();
  const [collapsed, setCollapsed] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [autoScroll, setAutoScroll] = useState(true);

  // Auto-scroll to bottom when new entries arrive
  useEffect(() => {
    if (autoScroll && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [entries.length, autoScroll]);

  // Detect if user scrolled away from bottom
  const handleScroll = () => {
    if (!scrollRef.current) return;
    const el = scrollRef.current;
    const isAtBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 30;
    setAutoScroll(isAtBottom);
  };

  if (!visible) return null;

  return (
    <div className={`log-viewer ${collapsed ? 'log-viewer-collapsed' : ''}`}>
      <div className="log-viewer-header">
        <button
          className="log-viewer-toggle"
          title={collapsed ? 'Expand' : 'Collapse'}
          onClick={() => setCollapsed(!collapsed)}
        >
          {collapsed ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </button>
        <span className="log-viewer-title">
          Log ({entries.length})
        </span>
        <div className="log-viewer-actions">
          <button
            className="log-viewer-btn"
            title="Clear logs"
            onClick={clearLogs}
          >
            <Trash2 size={13} />
          </button>
          <button
            className="log-viewer-btn"
            title="Close log viewer"
            onClick={() => setVisible(false)}
          >
            <X size={13} />
          </button>
        </div>
      </div>
      {!collapsed && (
        <div className="log-viewer-body" ref={scrollRef} onScroll={handleScroll}>
          {entries.length === 0 ? (
            <div className="log-viewer-empty">No log entries</div>
          ) : (
            entries.map((entry) => (
              <div key={entry.id} className={`log-entry ${levelClass(entry.level)}`}>
                <span className="log-entry-time">{formatTime(entry.timestamp)}</span>
                <span className={`log-entry-level ${levelClass(entry.level)}`}>{entry.level}</span>
                <span className="log-entry-msg">{entry.message}</span>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
