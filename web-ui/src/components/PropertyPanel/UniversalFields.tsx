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
import { useEffect, useState, useCallback } from 'react';
import { useTestPlanStore } from '../../store/testPlanStore.js';
import type { TestPlanNode } from '../../types/test-plan.js';

// ---------------------------------------------------------------------------
// UniversalFields — Name and Comments
// ---------------------------------------------------------------------------

interface UniversalFieldsProps {
  node: TestPlanNode;
}

/**
 * Renders Name and Comments inputs that are common to all JMeter elements.
 * Name is bound to node.name (via renameNode), Comments to node.properties.comments.
 */
export function UniversalFields({ node }: UniversalFieldsProps) {
  const { renameNode, updateProperty } = useTestPlanStore();

  const [name, setName] = useState(node.name);
  const [comments, setComments] = useState(
    (node.properties.comments as string) ?? '',
  );

  // Sync local state when a different node is selected
  useEffect(() => {
    setName(node.name);
    setComments((node.properties.comments as string) ?? '');
  }, [node.id, node.name, node.properties.comments]);

  const handleNameBlur = useCallback(() => {
    if (name !== node.name) {
      renameNode(node.id, name);
    }
  }, [name, node.id, node.name, renameNode]);

  const handleCommentsBlur = useCallback(() => {
    const current = (node.properties.comments as string) ?? '';
    if (comments !== current) {
      updateProperty(node.id, 'comments', comments);
    }
  }, [comments, node.id, node.properties.comments, updateProperty]);

  return (
    <div className="universal-fields">
      <div className="form-field">
        <label htmlFor="universal-name">Name</label>
        <input
          id="universal-name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          onBlur={handleNameBlur}
        />
      </div>
      <div className="form-field">
        <label htmlFor="universal-comments">Comments</label>
        <input
          id="universal-comments"
          type="text"
          value={comments}
          onChange={(e) => setComments(e.target.value)}
          onBlur={handleCommentsBlur}
        />
      </div>
    </div>
  );
}
