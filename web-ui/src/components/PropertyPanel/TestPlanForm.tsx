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
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTestPlanStore } from '../../store/testPlanStore.js';
import type { TestPlanNode } from '../../types/test-plan.js';
import type { SchemaEntry } from './schemas/index.js';
import type { UserDefinedVariable } from './schemas/index.js';
import { EditableTable } from '../EditableTable/EditableTable.js';
import type { Row } from '../EditableTable/EditableTable.js';
import { UDV_COLUMNS } from './constants.js';

// ---------------------------------------------------------------------------
// TestPlanForm — custom hand-coded form with User Defined Variables table
// ---------------------------------------------------------------------------

interface TestPlanFormProps {
  node: TestPlanNode;
  schemaEntry: SchemaEntry;
  initialValues: Record<string, unknown>;
  onSubmit: (values: Record<string, unknown>) => void;
}

export function TestPlanForm({ node, schemaEntry, initialValues, onSubmit }: TestPlanFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Record<string, unknown>>({
    resolver: zodResolver(schemaEntry.schema),
    defaultValues: initialValues,
    mode: 'onBlur',
  });

  // User-defined variables managed as local state and persisted on change
  const { updateProperty } = useTestPlanStore();
  const [udvRows, setUdvRows] = useState<Row[]>(() => {
    const stored = node.properties.userDefinedVariables;
    if (Array.isArray(stored)) {
      return stored.map((v: unknown) => {
        const item = v as UserDefinedVariable;
        return {
          name: item.name ?? '',
          value: item.value ?? '',
          description: item.description ?? '',
        };
      });
    }
    return [];
  });

  // Sync local UDV state when a different node is selected
  useEffect(() => {
    const stored = node.properties.userDefinedVariables;
    if (Array.isArray(stored)) {
      setUdvRows(
        stored.map((v: unknown) => {
          const item = v as UserDefinedVariable;
          return {
            name: item.name ?? '',
            value: item.value ?? '',
            description: item.description ?? '',
          };
        }),
      );
    } else {
      setUdvRows([]);
    }
  }, [node.id, node.properties.userDefinedVariables]);

  const handleUdvChange = useCallback(
    (rows: Row[]) => {
      setUdvRows(rows);
      // Persist immediately to the store
      updateProperty(node.id, 'userDefinedVariables', rows);
    },
    [node.id, updateProperty],
  );

  const submitHandler = handleSubmit((data) => {
    // Include the UDV rows in the submitted data
    onSubmit({ ...data, userDefinedVariables: udvRows });
  });

  const errorFor = (key: string) => errors[key]?.message as string | undefined;

  return (
    <form className="property-form" onSubmit={(e) => void submitHandler(e)} noValidate>
      {/* Checkboxes — these are already in the schema but name/comments are universal */}
      <fieldset className="property-fieldset">
        <legend>Test Plan Options</legend>

        <div className="form-field">
          <label className="checkbox-label">
            <input type="checkbox" {...register('functional_mode')} />
            Functional Test Mode (i.e. save Response Data and Sampler Data)
          </label>
        </div>

        <div className="form-field">
          <label className="checkbox-label">
            <input type="checkbox" {...register('teardown_on_shutdown')} />
            Run tearDown Thread Groups after shutdown of main threads
          </label>
        </div>

        <div className="form-field">
          <label className="checkbox-label">
            <input type="checkbox" {...register('serialize_threadgroups')} />
            Run Thread Groups consecutively (i.e. one at a time)
          </label>
        </div>

        {errorFor('functional_mode') !== undefined && (
          <span className="field-error-msg" role="alert">
            {errorFor('functional_mode')}
          </span>
        )}
      </fieldset>

      {/* User Defined Variables */}
      <fieldset className="property-fieldset">
        <legend>User Defined Variables</legend>
        <EditableTable
          columns={UDV_COLUMNS}
          rows={udvRows}
          onChange={handleUdvChange}
        />
      </fieldset>

      <button type="submit" className="form-submit-btn">
        Apply
      </button>
    </form>
  );
}
