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
import { Tree } from 'react-arborist';
import type { MoveHandler, DeleteHandler } from 'react-arborist';
import { useTestPlanStore } from '../../store/testPlanStore.js';
import type { TestPlanNode } from '../../types/test-plan.js';
import { NodeRenderer } from './NodeRenderer.js';
import './TreeEditor.css';

/** Height of a single tree row in pixels. */
const ROW_HEIGHT = 28;

/** Default visible height of the tree in pixels. */
const TREE_HEIGHT = 600;

/**
 * Converts a TestPlanNode tree into the flat data format expected by
 * react-arborist. react-arborist uses `children` accessor by default, so
 * we pass the node directly — it already has a `children` array.
 */
function convertToArboristData(root: TestPlanNode): readonly TestPlanNode[] {
  return [root];
}

/**
 * TestPlanTree renders the test plan tree.
 *
 * When the store's `tree` is null (no plan loaded), renders a placeholder message.
 */
export function TestPlanTree() {
  const { tree, selectNode, deleteNode, moveNode } = useTestPlanStore();

  if (tree === null) {
    return <div className="tree-empty">No test plan loaded</div>;
  }

  const handleMove: MoveHandler<TestPlanNode> = ({ dragIds, parentId, index }) => {
    const dragId = dragIds[0];
    if (dragId !== undefined && parentId !== null) {
      moveNode(dragId, parentId, index);
    }
  };

  const handleDelete: DeleteHandler<TestPlanNode> = ({ ids }) => {
    ids.forEach((id) => deleteNode(id));
  };

  return (
    <Tree<TestPlanNode>
      data={convertToArboristData(tree.root)}
      idAccessor="id"
      childrenAccessor="children"
      onSelect={(nodes) => selectNode(nodes[0]?.id ?? null)}
      onMove={handleMove}
      onDelete={handleDelete}
      width="100%"
      height={TREE_HEIGHT}
      rowHeight={ROW_HEIGHT}
    >
      {NodeRenderer}
    </Tree>
  );
}
