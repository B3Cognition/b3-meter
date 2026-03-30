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
import { useTestPlanStore } from '../../store/testPlanStore.js';
import type { TestPlanNode } from '../../types/test-plan.js';
import { getSchemaEntry } from './schemas/index.js';
import { getDisplayName } from './constants.js';
import { UniversalFields } from './UniversalFields.js';
import { JmxSummary } from './JmxSummary.js';
import { DynamicFormFallback } from './DynamicFormFallback.js';
import { PropertyForm } from './PropertyForm.js';
import { TestPlanForm } from './TestPlanForm.js';
import { ThreadGroupForm } from './ThreadGroupForm.js';
import { HTTPSamplerForm } from './HTTPSamplerForm.js';
import './PropertyPanel.css';

function findNode(node: TestPlanNode, id: string): TestPlanNode | undefined {
  if (node.id === id) return node;
  for (const child of node.children) {
    const found = findNode(child, id);
    if (found !== undefined) return found;
  }
  return undefined;
}

function buildInitialValues(
  defaults: Record<string, unknown>,
  stored: Record<string, unknown>,
): Record<string, unknown> {
  return { ...defaults, ...stored };
}

/** Dispatches to the correct form component for the given node type. */
function GenericPropertyForm({
  node,
  onSubmit,
}: {
  node: TestPlanNode;
  onSubmit: (values: Record<string, unknown>) => void;
}) {
  const entry = getSchemaEntry(node.type);
  if (entry !== undefined) {
    const initialValues = buildInitialValues(entry.defaults, node.properties);
    if (node.type === 'ThreadGroup')
      return <ThreadGroupForm key={node.id} node={node} schemaEntry={entry} initialValues={initialValues} onSubmit={onSubmit} />;
    if (node.type === 'HTTPSampler' || node.type === 'HTTPSamplerProxy')
      return <HTTPSamplerForm key={node.id} node={node} schemaEntry={entry} initialValues={initialValues} onSubmit={onSubmit} />;
    if (node.type === 'TestPlan')
      return <TestPlanForm key={node.id} node={node} schemaEntry={entry} initialValues={initialValues} onSubmit={onSubmit} />;
    return <PropertyForm key={node.id} nodeType={node.type} schemaEntry={entry} initialValues={initialValues} onSubmit={onSubmit} />;
  }
  return <DynamicFormFallback key={node.id} node={node} onSubmit={onSubmit} />;
}

/**
 * PropertyPanel is the right-hand pane that shows editable properties for the
 * node currently selected in the tree.  When nothing is selected it renders a
 * placeholder message.
 */
export function PropertyPanel() {
  const { tree, selectedNodeId, updateProperty } = useTestPlanStore();
  const selectedNode: TestPlanNode | undefined =
    selectedNodeId !== null && tree !== null
      ? findNode(tree.root, selectedNodeId)
      : undefined;

  function handleSubmit(values: Record<string, unknown>) {
    if (selectedNode === undefined) return;
    for (const [key, value] of Object.entries(values)) {
      updateProperty(selectedNode.id, key, value);
    }
  }

  return (
    <div className="property-panel-container">
      {selectedNode === undefined ? (
        <>
          <h2 className="property-panel-heading">Properties</h2>
          <p className="property-panel-empty">Select a node to edit its properties.</p>
        </>
      ) : (
        <>
          <h2 className="property-panel-type-title">{getDisplayName(selectedNode.type)}</h2>
          <UniversalFields node={selectedNode} />
          <hr className="property-panel-separator" />
          <GenericPropertyForm node={selectedNode} onSubmit={handleSubmit} />
          {selectedNode.type === 'TestPlan' && <JmxSummary planId={selectedNode.id} />}
        </>
      )}
    </div>
  );
}
