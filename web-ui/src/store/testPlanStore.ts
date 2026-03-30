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
import { temporal } from 'zundo';
import type { TestPlanNode, TestPlanTree } from '../types/test-plan.js';

/** Maximum number of undo states retained. */
const UNDO_LIMIT = 50;

export interface TestPlanState {
  tree: TestPlanTree | null;
  selectedNodeId: string | null;
  setTree: (tree: TestPlanTree) => void;
  selectNode: (id: string | null) => void;
  addNode: (parentId: string, node: TestPlanNode) => void;
  deleteNode: (id: string) => void;
  moveNode: (id: string, newParentId: string, index: number) => void;
  updateProperty: (nodeId: string, key: string, value: unknown) => void;
  renameNode: (nodeId: string, name: string) => void;
}

/** Recursively find a node by id, returning a new tree with the node replaced. */
function mapNode(
  node: TestPlanNode,
  id: string,
  fn: (n: TestPlanNode) => TestPlanNode,
): TestPlanNode {
  if (node.id === id) {
    return fn(node);
  }
  return {
    ...node,
    children: node.children.map((child) => mapNode(child, id, fn)),
  };
}

/** Add a child node under the parent with the given id. */
function addNodeToTree(
  node: TestPlanNode,
  parentId: string,
  newNode: TestPlanNode,
): TestPlanNode {
  if (node.id === parentId) {
    return { ...node, children: [...node.children, newNode] };
  }
  return {
    ...node,
    children: node.children.map((child) => addNodeToTree(child, parentId, newNode)),
  };
}

/** Remove a node by id from the tree (cannot remove root). */
function removeNode(node: TestPlanNode, id: string): TestPlanNode {
  return {
    ...node,
    children: node.children
      .filter((child) => child.id !== id)
      .map((child) => removeNode(child, id)),
  };
}

/** Extract a node by id (returns undefined if not found). */
function findNode(node: TestPlanNode, id: string): TestPlanNode | undefined {
  if (node.id === id) return node;
  for (const child of node.children) {
    const found = findNode(child, id);
    if (found !== undefined) return found;
  }
  return undefined;
}

/** Insert a node under newParentId at the given index. */
function insertNode(
  node: TestPlanNode,
  parentId: string,
  newNode: TestPlanNode,
  index: number,
): TestPlanNode {
  if (node.id === parentId) {
    const children = [...node.children];
    children.splice(index, 0, newNode);
    return { ...node, children };
  }
  return {
    ...node,
    children: node.children.map((child) => insertNode(child, parentId, newNode, index)),
  };
}

export const useTestPlanStore = create<TestPlanState>()(
  temporal(
    (set, get) => ({
      tree: null,
      selectedNodeId: null,

      setTree: (tree) => set({ tree }),

      selectNode: (id) => set({ selectedNodeId: id }),

      addNode: (parentId, node) => {
        const { tree } = get();
        if (tree === null) return;
        set({ tree: { root: addNodeToTree(tree.root, parentId, node) } });
      },

      deleteNode: (id) => {
        const { tree } = get();
        if (tree === null) return;
        set({ tree: { root: removeNode(tree.root, id) } });
      },

      moveNode: (id, newParentId, index) => {
        const { tree } = get();
        if (tree === null) return;
        const nodeToMove = findNode(tree.root, id);
        if (nodeToMove === undefined) return;
        const withoutNode = removeNode(tree.root, id);
        const withNode = insertNode(withoutNode, newParentId, nodeToMove, index);
        set({ tree: { root: withNode } });
      },

      updateProperty: (nodeId, key, value) => {
        const { tree } = get();
        if (tree === null) return;
        const updated = mapNode(tree.root, nodeId, (n) => ({
          ...n,
          properties: { ...n.properties, [key]: value },
        }));
        set({ tree: { root: updated } });
      },

      renameNode: (nodeId, name) => {
        const { tree } = get();
        if (tree === null) return;
        const updated = mapNode(tree.root, nodeId, (n) => ({
          ...n,
          name,
        }));
        set({ tree: { root: updated } });
      },
    }),
    {
      limit: UNDO_LIMIT,
      /** Only track the tree in temporal history, not selectedNodeId. */
      partialize: (state) => ({ tree: state.tree }),
    },
  ),
);
