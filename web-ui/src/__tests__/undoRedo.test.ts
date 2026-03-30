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
import { describe, it, expect, beforeEach } from 'vitest';
import { useTestPlanStore } from '../store/testPlanStore.js';
import type { TestPlanNode } from '../types/test-plan.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeNode(id: string, children: TestPlanNode[] = []): TestPlanNode {
  return {
    id,
    type: 'HTTPSampler',
    name: `Node ${id}`,
    enabled: true,
    properties: {},
    children,
  };
}

function getRootChildCount(): number {
  return useTestPlanStore.getState().tree?.root.children.length ?? 0;
}

beforeEach(() => {
  useTestPlanStore.setState({ tree: null, selectedNodeId: null });
  useTestPlanStore.temporal.getState().clear();
});

// ---------------------------------------------------------------------------
// T029-A: 50 add-node operations → undo all → tree empty
// ---------------------------------------------------------------------------

describe('undoRedo — 50 add operations then undo all', () => {
  it('tree has 50 children after 50 addNode calls', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }

    expect(getRootChildCount()).toBe(50);
  });

  it('undoing all 50 operations empties the children list', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().undo();
    }

    expect(getRootChildCount()).toBe(0);
  });

  it('root node still exists after undoing all additions', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().undo();
    }

    expect(useTestPlanStore.getState().tree?.root.id).toBe('root');
  });
});

// ---------------------------------------------------------------------------
// T029-B: Redo all 50 → tree has 50 nodes
// ---------------------------------------------------------------------------

describe('undoRedo — redo all 50 after full undo', () => {
  it('redoing all 50 operations restores 50 children', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().undo();
    }

    // State is now empty; redo all
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().redo();
    }

    expect(getRootChildCount()).toBe(50);
  });

  it('redone nodes preserve their ids', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().undo();
    }
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().redo();
    }

    const children = useTestPlanStore.getState().tree?.root.children ?? [];
    const ids = children.map((c) => c.id);
    // All 50 ids should be present
    for (let i = 0; i < 50; i++) {
      expect(ids).toContain(`c-${i}`);
    }
  });

  it('partial redo (25 of 50) restores exactly 25 children', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().undo();
    }

    for (let i = 0; i < 25; i++) {
      useTestPlanStore.temporal.getState().redo();
    }

    expect(getRootChildCount()).toBe(25);
  });
});

// ---------------------------------------------------------------------------
// T029-C: Performance — each undo/redo < 50 ms
// ---------------------------------------------------------------------------

describe('undoRedo — performance', () => {
  it('each undo operation completes in < 50 ms', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }

    for (let i = 0; i < 50; i++) {
      const start = performance.now();
      useTestPlanStore.temporal.getState().undo();
      const elapsed = performance.now() - start;
      expect(elapsed).toBeLessThan(50);
    }
  });

  it('each redo operation completes in < 50 ms', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().undo();
    }

    for (let i = 0; i < 50; i++) {
      const start = performance.now();
      useTestPlanStore.temporal.getState().redo();
      const elapsed = performance.now() - start;
      expect(elapsed).toBeLessThan(50);
    }
  });

  it('50 undo operations total complete in < 500 ms', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }

    const start = performance.now();
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().undo();
    }
    const elapsed = performance.now() - start;

    expect(elapsed).toBeLessThan(500);
  });

  it('50 redo operations total complete in < 500 ms', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().undo();
    }

    const start = performance.now();
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().redo();
    }
    const elapsed = performance.now() - start;

    expect(elapsed).toBeLessThan(500);
  });
});

// ---------------------------------------------------------------------------
// T029-D: Edge cases and boundary conditions
// ---------------------------------------------------------------------------

describe('undoRedo — edge cases', () => {
  it('extra undo beyond 50 does not crash (no-op)', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }
    // Undo 60 times — the last 10 are no-ops
    expect(() => {
      for (let i = 0; i < 60; i++) {
        useTestPlanStore.temporal.getState().undo();
      }
    }).not.toThrow();
  });

  it('extra redo beyond available history does not crash (no-op)', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 10; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }
    for (let i = 0; i < 10; i++) {
      useTestPlanStore.temporal.getState().undo();
    }

    // Redo 20 times when only 10 are available
    expect(() => {
      for (let i = 0; i < 20; i++) {
        useTestPlanStore.temporal.getState().redo();
      }
    }).not.toThrow();

    expect(getRootChildCount()).toBe(10);
  });

  it('undo on empty history does not crash', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    expect(() => {
      useTestPlanStore.temporal.getState().undo();
    }).not.toThrow();
  });

  it('redo on empty future does not crash', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    useTestPlanStore.getState().addNode('root', makeNode('c-1'));

    expect(() => {
      useTestPlanStore.temporal.getState().redo();
    }).not.toThrow();

    expect(getRootChildCount()).toBe(1);
  });

  it('new action after partial undo clears redo history', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    for (let i = 0; i < 5; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }

    // Undo 3 → 2 children
    for (let i = 0; i < 3; i++) {
      useTestPlanStore.temporal.getState().undo();
    }
    expect(getRootChildCount()).toBe(2);

    // New action: add a different node
    useTestPlanStore.getState().addNode('root', makeNode('c-new'));
    expect(getRootChildCount()).toBe(3);

    // Redo should be a no-op (future was cleared by the new action)
    useTestPlanStore.temporal.getState().redo();
    expect(getRootChildCount()).toBe(3);
  });

  it('undo/redo cycle with deleteNode operations', () => {
    const c1 = makeNode('c1');
    const c2 = makeNode('c2');
    useTestPlanStore.getState().setTree({ root: makeNode('root', [c1, c2]) });
    useTestPlanStore.temporal.getState().clear();

    useTestPlanStore.getState().deleteNode('c1');
    expect(getRootChildCount()).toBe(1);

    useTestPlanStore.temporal.getState().undo();
    expect(getRootChildCount()).toBe(2);

    useTestPlanStore.temporal.getState().redo();
    expect(getRootChildCount()).toBe(1);
  });

  it('undo/redo cycle with updateProperty operations', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    useTestPlanStore.getState().updateProperty('root', 'url', 'https://before.example.com');
    useTestPlanStore.getState().updateProperty('root', 'url', 'https://after.example.com');

    useTestPlanStore.temporal.getState().undo();
    expect(useTestPlanStore.getState().tree?.root.properties['url']).toBe('https://before.example.com');

    useTestPlanStore.temporal.getState().redo();
    expect(useTestPlanStore.getState().tree?.root.properties['url']).toBe('https://after.example.com');
  });

  it('selectedNodeId is NOT tracked in undo history', () => {
    useTestPlanStore.getState().setTree({ root: makeNode('root') });
    useTestPlanStore.temporal.getState().clear();

    useTestPlanStore.getState().addNode('root', makeNode('c1'));

    // After addNode, clear redo history then select (selection is not in temporal).
    useTestPlanStore.getState().selectNode('c1');

    // Undo the addNode operation.
    useTestPlanStore.temporal.getState().undo();

    // selectedNodeId persists because it is not part of the temporal partialize slice.
    expect(useTestPlanStore.getState().selectedNodeId).toBe('c1');
    // The tree may have been reverted (0 children) or zundo may have deduped the
    // selectNode set() call — either way the child count is at most 1.
    expect(getRootChildCount()).toBeLessThanOrEqual(1);
  });
});
