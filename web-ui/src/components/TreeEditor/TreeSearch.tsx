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
import { useState, useEffect, useRef, useCallback } from 'react';
import { Search, X, ChevronUp, ChevronDown } from 'lucide-react';
import { useTestPlanStore } from '../../store/testPlanStore.js';
import type { TestPlanNode } from '../../types/test-plan.js';
import './TreeSearch.css';

/** Collect all nodes from the tree into a flat array (depth-first). */
function flattenTree(node: TestPlanNode): TestPlanNode[] {
  const result: TestPlanNode[] = [node];
  for (const child of node.children) {
    result.push(...flattenTree(child));
  }
  return result;
}

interface TreeSearchProps {
  /** Callback when a match is focused — the parent should select this node. */
  onSelectMatch?: (nodeId: string) => void;
}

export function TreeSearch({ onSelectMatch }: TreeSearchProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [currentIndex, setCurrentIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  const tree = useTestPlanStore((s) => s.tree);
  const selectNode = useTestPlanStore((s) => s.selectNode);

  // Compute matches
  const matches: TestPlanNode[] = (() => {
    if (!tree || !query.trim()) return [];
    const allNodes = flattenTree(tree.root);
    const lowerQuery = query.toLowerCase();
    return allNodes.filter((n) => n.name.toLowerCase().includes(lowerQuery));
  })();

  // Clamp current index when matches change
  useEffect(() => {
    if (matches.length === 0) {
      setCurrentIndex(0);
    } else if (currentIndex >= matches.length) {
      setCurrentIndex(0);
    }
  }, [matches.length, currentIndex]);

  // Navigate to current match
  useEffect(() => {
    if (matches.length > 0 && matches[currentIndex]) {
      const node = matches[currentIndex];
      selectNode(node.id);
      onSelectMatch?.(node.id);
    }
  }, [currentIndex, matches, selectNode, onSelectMatch]);

  // Global Ctrl+F handler
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      const ctrl = e.ctrlKey || e.metaKey;
      if (ctrl && e.key.toLowerCase() === 'f') {
        e.preventDefault();
        setOpen(true);
        // Defer focus to next tick so the input is mounted
        setTimeout(() => inputRef.current?.focus(), 0);
      }
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, []);

  const close = useCallback(() => {
    setOpen(false);
    setQuery('');
    setCurrentIndex(0);
  }, []);

  const goNext = useCallback(() => {
    if (matches.length === 0) return;
    setCurrentIndex((prev) => (prev + 1) % matches.length);
  }, [matches.length]);

  const goPrev = useCallback(() => {
    if (matches.length === 0) return;
    setCurrentIndex((prev) => (prev - 1 + matches.length) % matches.length);
  }, [matches.length]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        close();
      } else if (e.key === 'Enter') {
        e.preventDefault();
        if (e.shiftKey) {
          goPrev();
        } else {
          goNext();
        }
      }
    },
    [close, goNext, goPrev],
  );

  if (!open) return null;

  return (
    <div className="tree-search">
      <div className="tree-search-icon">
        <Search size={14} />
      </div>
      <input
        ref={inputRef}
        className="tree-search-input"
        type="text"
        placeholder="Search tree..."
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setCurrentIndex(0);
        }}
        onKeyDown={handleKeyDown}
      />
      <span className="tree-search-count">
        {query.trim()
          ? matches.length > 0
            ? `${currentIndex + 1} of ${matches.length}`
            : 'No matches'
          : ''}
      </span>
      <button
        className="tree-search-nav"
        title="Previous match (Shift+Enter)"
        onClick={goPrev}
        disabled={matches.length === 0}
      >
        <ChevronUp size={14} />
      </button>
      <button
        className="tree-search-nav"
        title="Next match (Enter)"
        onClick={goNext}
        disabled={matches.length === 0}
      >
        <ChevronDown size={14} />
      </button>
      <button className="tree-search-close" title="Close (Esc)" onClick={close}>
        <X size={14} />
      </button>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Exported helper: highlight matching text in a node name
// ---------------------------------------------------------------------------

/**
 * Returns a React element with the matching substring highlighted.
 * Used by NodeRenderer to show search highlights.
 */
export function highlightMatch(name: string, query: string): React.ReactNode {
  if (!query.trim()) return name;
  const lowerName = name.toLowerCase();
  const lowerQuery = query.toLowerCase();
  const idx = lowerName.indexOf(lowerQuery);
  if (idx === -1) return name;

  const before = name.slice(0, idx);
  const match = name.slice(idx, idx + query.length);
  const after = name.slice(idx + query.length);

  return (
    <>
      {before}
      <mark className="tree-search-highlight">{match}</mark>
      {after}
    </>
  );
}

/** Store for sharing the current search query with NodeRenderer. */
let _currentSearchQuery = '';

export function setSearchQuery(q: string) {
  _currentSearchQuery = q;
}

export function getSearchQuery(): string {
  return _currentSearchQuery;
}
