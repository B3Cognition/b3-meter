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

export type SaveStatus = 'idle' | 'saving' | 'saved' | 'error';

export interface UiState {
  /** Whether the tree panel is visible. */
  isTreePanelOpen: boolean;
  /** Whether the property panel is visible. */
  isPropertyPanelOpen: boolean;
  /** Whether the metrics panel is visible. */
  isMetricsPanelOpen: boolean;
  /** True when the test plan has unsaved changes. */
  isDirty: boolean;
  /** Current save operation status. */
  saveStatus: SaveStatus;
  /** Last save error message, if any. */
  saveError: string | null;
  /** Map of plan ID to raw JMX XML string for display in the XML editor. */
  planXmlMap: Record<string, string>;
  setTreePanelOpen: (open: boolean) => void;
  setPropertyPanelOpen: (open: boolean) => void;
  setMetricsPanelOpen: (open: boolean) => void;
  setDirty: (dirty: boolean) => void;
  setSaveStatus: (status: SaveStatus, error?: string) => void;
  setPlanXmlMap: (map: Record<string, string>) => void;
  setPlanXml: (planId: string, xml: string) => void;
}

export const useUiStore = create<UiState>()((set) => ({
  isTreePanelOpen: true,
  isPropertyPanelOpen: true,
  isMetricsPanelOpen: false,
  isDirty: false,
  saveStatus: 'idle',
  saveError: null,
  planXmlMap: {},

  setTreePanelOpen: (open) => set({ isTreePanelOpen: open }),

  setPropertyPanelOpen: (open) => set({ isPropertyPanelOpen: open }),

  setMetricsPanelOpen: (open) => set({ isMetricsPanelOpen: open }),

  setDirty: (dirty) => set({ isDirty: dirty }),

  setSaveStatus: (status, error) =>
    set({ saveStatus: status, saveError: error ?? null }),

  setPlanXmlMap: (map) => set({ planXmlMap: map }),

  setPlanXml: (planId, xml) =>
    set((state) => ({ planXmlMap: { ...state.planXmlMap, [planId]: xml } })),
}));
