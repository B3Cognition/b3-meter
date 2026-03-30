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
import type { TestPlanNode, TestPlanTree } from '../types/test-plan.js';

/** Helper: create a minimal node. */
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

function makeTree(root: TestPlanNode): TestPlanTree {
  return { root };
}

/** Reset the store before each test. */
beforeEach(() => {
  useTestPlanStore.setState({ tree: null, selectedNodeId: null });
  useTestPlanStore.temporal.getState().clear();
});

describe('testPlanStore — setTree', () => {
  it('stores the provided tree', () => {
    const tree = makeTree(makeNode('root'));
    useTestPlanStore.getState().setTree(tree);
    expect(useTestPlanStore.getState().tree).toBe(tree);
  });

  it('replaces a previously set tree', () => {
    useTestPlanStore.getState().setTree(makeTree(makeNode('root-1')));
    const newTree = makeTree(makeNode('root-2'));
    useTestPlanStore.getState().setTree(newTree);
    expect(useTestPlanStore.getState().tree?.root.id).toBe('root-2');
  });
});

describe('testPlanStore — selectNode', () => {
  it('sets selectedNodeId', () => {
    useTestPlanStore.getState().selectNode('abc');
    expect(useTestPlanStore.getState().selectedNodeId).toBe('abc');
  });

  it('clears selectedNodeId when null is passed', () => {
    useTestPlanStore.getState().selectNode('abc');
    useTestPlanStore.getState().selectNode(null);
    expect(useTestPlanStore.getState().selectedNodeId).toBeNull();
  });
});

describe('testPlanStore — addNode', () => {
  it('appends a child node under the parent', () => {
    const root = makeNode('root');
    useTestPlanStore.getState().setTree(makeTree(root));
    const child = makeNode('child-1');
    useTestPlanStore.getState().addNode('root', child);
    const tree = useTestPlanStore.getState().tree;
    expect(tree?.root.children).toHaveLength(1);
    expect(tree?.root.children[0]?.id).toBe('child-1');
  });

  it('adds a grandchild under the correct parent', () => {
    const child = makeNode('child-1');
    const root = makeNode('root', [child]);
    useTestPlanStore.getState().setTree(makeTree(root));
    const grandchild = makeNode('gc-1');
    useTestPlanStore.getState().addNode('child-1', grandchild);
    const tree = useTestPlanStore.getState().tree;
    expect(tree?.root.children[0]?.children[0]?.id).toBe('gc-1');
  });

  it('does nothing when tree is null', () => {
    useTestPlanStore.getState().addNode('root', makeNode('x'));
    expect(useTestPlanStore.getState().tree).toBeNull();
  });
});

describe('testPlanStore — deleteNode', () => {
  it('removes a direct child', () => {
    const child = makeNode('child-1');
    useTestPlanStore.getState().setTree(makeTree(makeNode('root', [child])));
    useTestPlanStore.getState().deleteNode('child-1');
    expect(useTestPlanStore.getState().tree?.root.children).toHaveLength(0);
  });

  it('removes a deeply nested node', () => {
    const gc = makeNode('gc');
    const child = makeNode('child', [gc]);
    useTestPlanStore.getState().setTree(makeTree(makeNode('root', [child])));
    useTestPlanStore.getState().deleteNode('gc');
    const tree = useTestPlanStore.getState().tree;
    expect(tree?.root.children[0]?.children).toHaveLength(0);
  });

  it('does nothing when tree is null', () => {
    useTestPlanStore.getState().deleteNode('anything');
    expect(useTestPlanStore.getState().tree).toBeNull();
  });
});

describe('testPlanStore — moveNode', () => {
  it('moves a node to a different parent', () => {
    const child1 = makeNode('c1');
    const child2 = makeNode('c2');
    const root = makeNode('root', [child1, child2]);
    useTestPlanStore.getState().setTree(makeTree(root));
    useTestPlanStore.getState().moveNode('c1', 'c2', 0);
    const tree = useTestPlanStore.getState().tree;
    expect(tree?.root.children).toHaveLength(1);
    expect(tree?.root.children[0]?.id).toBe('c2');
    expect(tree?.root.children[0]?.children[0]?.id).toBe('c1');
  });

  it('respects the insertion index', () => {
    const c1 = makeNode('c1');
    const c2 = makeNode('c2');
    const c3 = makeNode('c3');
    const root = makeNode('root', [c1, c2, c3]);
    useTestPlanStore.getState().setTree(makeTree(root));
    useTestPlanStore.getState().moveNode('c3', 'root', 1);
    const children = useTestPlanStore.getState().tree?.root.children ?? [];
    expect(children.map((n) => n.id)).toEqual(['c1', 'c3', 'c2']);
  });

  it('does nothing when tree is null', () => {
    useTestPlanStore.getState().moveNode('x', 'y', 0);
    expect(useTestPlanStore.getState().tree).toBeNull();
  });
});

describe('testPlanStore — updateProperty', () => {
  it('updates a property on the target node', () => {
    const root = makeNode('root');
    useTestPlanStore.getState().setTree(makeTree(root));
    useTestPlanStore.getState().updateProperty('root', 'url', 'https://example.com');
    expect(useTestPlanStore.getState().tree?.root.properties['url']).toBe('https://example.com');
  });

  it('preserves existing properties', () => {
    const root: TestPlanNode = {
      ...makeNode('root'),
      properties: { method: 'GET' },
    };
    useTestPlanStore.getState().setTree(makeTree(root));
    useTestPlanStore.getState().updateProperty('root', 'url', 'https://example.com');
    const props = useTestPlanStore.getState().tree?.root.properties;
    expect(props?.['method']).toBe('GET');
    expect(props?.['url']).toBe('https://example.com');
  });

  it('does nothing when tree is null', () => {
    useTestPlanStore.getState().updateProperty('root', 'url', 'x');
    expect(useTestPlanStore.getState().tree).toBeNull();
  });
});

describe('testPlanStore — undo/redo', () => {
  it('undoes an addNode operation', () => {
    useTestPlanStore.getState().setTree(makeTree(makeNode('root')));
    // Clear history from setTree so only addNode is in history
    useTestPlanStore.temporal.getState().clear();

    useTestPlanStore.getState().addNode('root', makeNode('c1'));
    expect(useTestPlanStore.getState().tree?.root.children).toHaveLength(1);

    useTestPlanStore.temporal.getState().undo();
    expect(useTestPlanStore.getState().tree?.root.children).toHaveLength(0);
  });

  it('redoes after undo', () => {
    useTestPlanStore.getState().setTree(makeTree(makeNode('root')));
    useTestPlanStore.temporal.getState().clear();

    useTestPlanStore.getState().addNode('root', makeNode('c1'));
    useTestPlanStore.temporal.getState().undo();
    useTestPlanStore.temporal.getState().redo();

    expect(useTestPlanStore.getState().tree?.root.children).toHaveLength(1);
  });

  it('supports 50 consecutive undo operations', () => {
    const root = makeNode('root');
    useTestPlanStore.getState().setTree(makeTree(root));
    useTestPlanStore.temporal.getState().clear();

    // Perform 50 addNode operations
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }
    expect(useTestPlanStore.getState().tree?.root.children).toHaveLength(50);

    // Undo all 50
    for (let i = 0; i < 50; i++) {
      useTestPlanStore.temporal.getState().undo();
    }
    expect(useTestPlanStore.getState().tree?.root.children).toHaveLength(0);
  });

  it('does not exceed the 50-operation limit (51st undo has no effect on oldest op)', () => {
    const root = makeNode('root');
    useTestPlanStore.getState().setTree(makeTree(root));
    useTestPlanStore.temporal.getState().clear();

    // 51 operations — the first should be dropped from history
    for (let i = 0; i < 51; i++) {
      useTestPlanStore.getState().addNode('root', makeNode(`c-${i}`));
    }

    // Undo 51 times — 51st undo should be a no-op
    for (let i = 0; i < 51; i++) {
      useTestPlanStore.temporal.getState().undo();
    }

    // At most 50 operations were tracked; at minimum 1 child remains
    const count = useTestPlanStore.getState().tree?.root.children.length ?? 0;
    expect(count).toBeGreaterThanOrEqual(1);
  });

  it('undoes a deleteNode operation', () => {
    const child = makeNode('c1');
    useTestPlanStore.getState().setTree(makeTree(makeNode('root', [child])));
    useTestPlanStore.temporal.getState().clear();

    useTestPlanStore.getState().deleteNode('c1');
    expect(useTestPlanStore.getState().tree?.root.children).toHaveLength(0);

    useTestPlanStore.temporal.getState().undo();
    expect(useTestPlanStore.getState().tree?.root.children).toHaveLength(1);
  });

  it('undoes an updateProperty operation', () => {
    const root = makeNode('root');
    useTestPlanStore.getState().setTree(makeTree(root));
    useTestPlanStore.temporal.getState().clear();

    useTestPlanStore.getState().updateProperty('root', 'url', 'https://example.com');
    useTestPlanStore.temporal.getState().undo();

    const props = useTestPlanStore.getState().tree?.root.properties;
    expect(props?.['url']).toBeUndefined();
  });
});
