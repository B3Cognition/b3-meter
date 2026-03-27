/**
 * TestPlanTree — the main tree editor component.
 *
 * Renders the JMeter test plan as an interactive tree using react-arborist,
 * supporting node selection, drag-and-drop reordering, and deletion.
 */

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
