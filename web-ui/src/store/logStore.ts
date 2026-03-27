/**
 * Log store — manages application log entries for the Log Viewer panel.
 */

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
