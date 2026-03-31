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
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { TestPlanTree } from '../components/TreeEditor/TestPlanTree.js';
import { NodeRenderer } from '../components/TreeEditor/NodeRenderer.js';
import { NodeContextMenu } from '../components/TreeEditor/NodeContextMenu.js';
import { useTestPlanStore } from '../store/testPlanStore.js';
import type { TestPlanNode, TestPlanTree as TestPlanTreeType } from '../types/test-plan.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeNode(
  id: string,
  overrides: Partial<TestPlanNode> = {},
  children: TestPlanNode[] = [],
): TestPlanNode {
  return {
    id,
    type: 'HTTPSampler',
    name: `Node ${id}`,
    enabled: true,
    properties: {},
    children,
    ...overrides,
  };
}

function makeTree(root: TestPlanNode): TestPlanTreeType {
  return { root };
}

// Reset store before each test
beforeEach(() => {
  useTestPlanStore.setState({ tree: null, selectedNodeId: null });
  useTestPlanStore.temporal.getState().clear();
});

// ---------------------------------------------------------------------------
// TestPlanTree — null state
// ---------------------------------------------------------------------------

describe('TestPlanTree — no plan loaded', () => {
  it('renders the empty placeholder when tree is null', () => {
    render(<TestPlanTree />);
    expect(screen.getByText('No test plan loaded')).toBeInTheDocument();
  });

  it('does not render a tree element when tree is null', () => {
    render(<TestPlanTree />);
    expect(document.querySelector('[role="tree"]')).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// TestPlanTree — with data
// ---------------------------------------------------------------------------

describe('TestPlanTree — with plan loaded', () => {
  it('renders the tree when a plan is set in the store', () => {
    const root = makeNode('root', { type: 'TestPlan', name: 'My Plan' });
    useTestPlanStore.getState().setTree(makeTree(root));

    render(<TestPlanTree />);

    // react-arborist renders node names as text
    expect(screen.getByText('My Plan')).toBeInTheDocument();
  });

  it('does not show the empty placeholder when a plan is loaded', () => {
    const root = makeNode('root', { type: 'TestPlan', name: 'My Plan' });
    useTestPlanStore.getState().setTree(makeTree(root));

    render(<TestPlanTree />);

    expect(screen.queryByText('No test plan loaded')).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// NodeRenderer — unit tests (rendered in isolation via a stub tree structure)
// ---------------------------------------------------------------------------

describe('NodeRenderer — enabled node', () => {
  it('renders node name', () => {
    const node = makeNode('n1', { name: 'My Sampler', type: 'HTTPSampler', enabled: true });

    // Render with a minimal stub that matches NodeRendererProps shape
    const stubNode = {
      id: node.id,
      data: node,
      isSelected: false,
      select: vi.fn(),
      isOpen: false,
      isFocused: false,
      level: 0,
    } as unknown as Parameters<typeof NodeRenderer>[0]['node'];

    render(
      <NodeRenderer
        node={stubNode}
        style={{}}
        dragHandle={undefined}
        tree={{} as Parameters<typeof NodeRenderer>[0]['tree']}
      />,
    );

    expect(screen.getByText('My Sampler')).toBeInTheDocument();
  });

  it('does not render the disabled badge when node is enabled', () => {
    const node = makeNode('n1', { name: 'Active Node', enabled: true });
    const stubNode = {
      id: node.id,
      data: node,
      isSelected: false,
      select: vi.fn(),
    } as unknown as Parameters<typeof NodeRenderer>[0]['node'];

    render(
      <NodeRenderer
        node={stubNode}
        style={{}}
        dragHandle={undefined}
        tree={{} as Parameters<typeof NodeRenderer>[0]['tree']}
      />,
    );

    expect(screen.queryByText('disabled')).toBeNull();
  });

  it('applies selected class when node.isSelected is true', () => {
    const node = makeNode('n1', { name: 'Selected Node' });
    const stubNode = {
      id: node.id,
      data: node,
      isSelected: true,
      select: vi.fn(),
    } as unknown as Parameters<typeof NodeRenderer>[0]['node'];

    const { container } = render(
      <NodeRenderer
        node={stubNode}
        style={{}}
        dragHandle={undefined}
        tree={{} as Parameters<typeof NodeRenderer>[0]['tree']}
      />,
    );

    expect(container.querySelector('.tree-node.selected')).not.toBeNull();
  });
});

describe('NodeRenderer — disabled node', () => {
  it('renders the disabled badge when node.data.enabled is false', () => {
    const node = makeNode('n1', { name: 'Disabled Node', enabled: false });
    const stubNode = {
      id: node.id,
      data: node,
      isSelected: false,
      select: vi.fn(),
    } as unknown as Parameters<typeof NodeRenderer>[0]['node'];

    render(
      <NodeRenderer
        node={stubNode}
        style={{}}
        dragHandle={undefined}
        tree={{} as Parameters<typeof NodeRenderer>[0]['tree']}
      />,
    );

    expect(screen.getByText('disabled')).toBeInTheDocument();
  });

  it('applies disabled class when node.data.enabled is false', () => {
    const node = makeNode('n1', { enabled: false });
    const stubNode = {
      id: node.id,
      data: node,
      isSelected: false,
      select: vi.fn(),
    } as unknown as Parameters<typeof NodeRenderer>[0]['node'];

    const { container } = render(
      <NodeRenderer
        node={stubNode}
        style={{}}
        dragHandle={undefined}
        tree={{} as Parameters<typeof NodeRenderer>[0]['tree']}
      />,
    );

    expect(container.querySelector('.tree-node.disabled')).not.toBeNull();
  });

  it('calls node.select() when the node row is clicked', () => {
    const selectFn = vi.fn();
    const node = makeNode('n1', { name: 'Clickable' });
    const stubNode = {
      id: node.id,
      data: node,
      isSelected: false,
      select: selectFn,
    } as unknown as Parameters<typeof NodeRenderer>[0]['node'];

    render(
      <NodeRenderer
        node={stubNode}
        style={{}}
        dragHandle={undefined}
        tree={{} as Parameters<typeof NodeRenderer>[0]['tree']}
      />,
    );

    fireEvent.click(screen.getByText('Clickable').closest('.tree-node')!);
    expect(selectFn).toHaveBeenCalledOnce();
  });
});

// ---------------------------------------------------------------------------
// NodeContextMenu
// ---------------------------------------------------------------------------

describe('NodeContextMenu', () => {
  const position = { x: 100, y: 200 };

  it('renders all menu items', () => {
    const node = makeNode('n1', { enabled: true });
    const onClose = vi.fn();

    render(
      <NodeContextMenu node={node} position={position} onClose={onClose} />,
    );

    expect(screen.getByText('Add')).toBeInTheDocument();
    expect(screen.getByText('Duplicate')).toBeInTheDocument();
    expect(screen.getByText('Disable')).toBeInTheDocument();
    expect(screen.getByText('Delete')).toBeInTheDocument();
  });

  it('shows Enable when node is disabled', () => {
    const node = makeNode('n1', { enabled: false });
    const onClose = vi.fn();

    render(
      <NodeContextMenu node={node} position={position} onClose={onClose} />,
    );

    expect(screen.getByText('Enable')).toBeInTheDocument();
  });

  it('calls onClose after clicking Delete', () => {
    const child = makeNode('child-del', { name: 'To Delete' });
    useTestPlanStore.getState().setTree(makeTree(makeNode('root', {}, [child])));

    const onClose = vi.fn();

    render(
      <NodeContextMenu node={child} position={position} onClose={onClose} />,
    );

    fireEvent.click(screen.getByText('Delete'));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('calls deleteNode when Delete is clicked', () => {
    const child = makeNode('child-x');
    useTestPlanStore
      .getState()
      .setTree(makeTree(makeNode('root-del', {}, [child])));

    const onClose = vi.fn();

    render(
      <NodeContextMenu node={child} position={position} onClose={onClose} />,
    );

    fireEvent.click(screen.getByText('Delete'));

    // child should be removed from the tree
    const tree = useTestPlanStore.getState().tree;
    expect(tree?.root.children).toHaveLength(0);
  });

  it('calls onClose after pressing Escape', () => {
    const node = makeNode('n1', { enabled: true });
    const onClose = vi.fn();

    render(
      <NodeContextMenu node={node} position={position} onClose={onClose} />,
    );

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('calls addNode when a sampler is selected from Add submenu', () => {
    const root = makeNode('root');
    useTestPlanStore.getState().setTree(makeTree(root));
    const onClose = vi.fn();

    render(
      <NodeContextMenu node={root} position={position} onClose={onClose} />,
    );

    // Hover the "Add" trigger to open the submenu
    fireEvent.mouseEnter(screen.getByText('Add').closest('.context-menu-submenu-trigger')!);
    // Hover the "Sampler" category to open the sampler submenu
    fireEvent.mouseEnter(screen.getByText('Sampler').closest('.context-menu-submenu-trigger')!);
    // Click a sampler item
    fireEvent.click(screen.getByText('HTTP Request'));

    const tree = useTestPlanStore.getState().tree;
    expect(tree?.root.children).toHaveLength(1);
    expect(onClose).toHaveBeenCalledOnce();
  });
});
