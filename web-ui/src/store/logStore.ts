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
import { create } from 'zustand';

export type LogLevel = 'INFO' | 'WARN' | 'ERROR';

export interface LogEntry {
  id: string;
  timestamp: string;
  level: LogLevel;
  message: string;
}

/** Maximum number of log entries retained. */
const MAX_ENTRIES = 1000;

export interface LogState {
  entries: LogEntry[];
  visible: boolean;
  addLog: (level: LogLevel, message: string) => void;
  clearLogs: () => void;
  setVisible: (visible: boolean) => void;
  toggleVisible: () => void;
}

let logCounter = 0;

export const useLogStore = create<LogState>()((set) => ({
  entries: [],
  visible: false,

  addLog: (level, message) =>
    set((state) => {
      const entry: LogEntry = {
        id: `log-${++logCounter}`,
        timestamp: new Date().toISOString(),
        level,
        message,
      };
      const entries =
        state.entries.length < MAX_ENTRIES
          ? [...state.entries, entry]
          : [...state.entries.slice(1), entry];
      return { entries };
    }),

  clearLogs: () => set({ entries: [] }),

  setVisible: (visible) => set({ visible }),

  toggleVisible: () => set((state) => ({ visible: !state.visible })),
}));
