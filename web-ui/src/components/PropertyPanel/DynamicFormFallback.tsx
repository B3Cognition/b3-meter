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
import { useEffect, useState } from 'react';
import { DynamicForm } from './DynamicForm.js';
import type { ComponentSchemaDto } from './DynamicForm.js';
import type { TestPlanNode } from '../../types/test-plan.js';

// ---------------------------------------------------------------------------
// DynamicFormFallback — fetches schema from API and renders DynamicForm
// ---------------------------------------------------------------------------

interface DynamicFormFallbackProps {
  node: TestPlanNode;
  onSubmit: (values: Record<string, unknown>) => void;
}

/**
 * Fetches the component schema from {@code GET /api/v1/schemas/{type}} and
 * renders a {@link DynamicForm}.  Shows a loading state while the request is
 * in-flight and falls back to the "no editable properties" placeholder when
 * the schema is not available (404 or network error).
 */
export function DynamicFormFallback({ node, onSubmit }: DynamicFormFallbackProps) {
  const [dynamicSchema, setDynamicSchema] = useState<ComponentSchemaDto | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setDynamicSchema(null);

    fetch(`/api/v1/schemas/${encodeURIComponent(node.type)}`, {
      headers: { Accept: 'application/json' },
    })
      .then((res) => {
        if (!res.ok) return null;
        return res.json() as Promise<ComponentSchemaDto>;
      })
      .then((schema) => {
        if (!cancelled) {
          setDynamicSchema(schema);
          setLoading(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [node.type]);

  if (loading) {
    return <p className="property-panel-empty">Loading properties...</p>;
  }

  if (dynamicSchema === null) {
    return (
      <p className="property-panel-empty">
        No editable properties for <strong>{node.type}</strong>.
      </p>
    );
  }

  return (
    <DynamicForm
      schema={dynamicSchema}
      initialValues={node.properties}
      onSubmit={onSubmit}
    />
  );
}
