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
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { getFieldsets } from './constants.js';
import type { SchemaEntry } from './schemas/index.js';

// ---------------------------------------------------------------------------
// PropertyForm — the inner react-hook-form component (generic fallback)
// ---------------------------------------------------------------------------

interface PropertyFormProps {
  nodeType: string;
  schemaEntry: SchemaEntry;
  initialValues: Record<string, unknown>;
  onSubmit: (values: Record<string, unknown>) => void;
}

/**
 * Schema-driven form component.  Iterates over the Zod schema's shape keys and
 * renders an appropriate input element for each field.
 *
 * When the node type has fieldset grouping (e.g. HTTPSampler), fields are
 * wrapped in <fieldset>/<legend> elements.  Ungrouped fields render after.
 *
 * Supported schema types detected at runtime:
 *   - ZodNumber  -> <input type="number">
 *   - ZodBoolean -> <input type="checkbox">
 *   - ZodEnum    -> <select>
 *   - ZodString  -> <input type="text">
 */
export function PropertyForm({ nodeType, schemaEntry, initialValues, onSubmit }: PropertyFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Record<string, unknown>>({
    resolver: zodResolver(schemaEntry.schema),
    defaultValues: initialValues,
    mode: 'onBlur',
  });

  // Zod object schema exposes `.shape`; cast through unknown to access it
  // without requiring generics.
  const shape = (schemaEntry.schema as unknown as { shape: Record<string, unknown> }).shape ?? {};
  const fieldKeys = Object.keys(shape);

  function getFieldType(key: string): 'number' | 'enum' | 'boolean' | 'text' {
    const fieldSchema = shape[key] as
      | { _def: { typeName: string; values?: string[]; innerType?: { _def: { typeName: string; values?: string[] } } } }
      | undefined;
    if (fieldSchema === undefined) return 'text';
    const typeName = fieldSchema._def?.typeName ?? '';
    // Handle ZodDefault and ZodOptional wrappers
    if (typeName === 'ZodDefault' || typeName === 'ZodOptional') {
      const innerTypeName = fieldSchema._def?.innerType?._def?.typeName ?? '';
      if (innerTypeName === 'ZodNumber') return 'number';
      if (innerTypeName === 'ZodBoolean') return 'boolean';
      if (innerTypeName === 'ZodEnum') return 'enum';
      return 'text';
    }
    if (typeName === 'ZodNumber') return 'number';
    if (typeName === 'ZodBoolean') return 'boolean';
    if (typeName === 'ZodEnum') return 'enum';
    return 'text';
  }

  function getEnumValues(key: string): string[] {
    const fieldSchema = shape[key] as
      | { _def: { values?: string[]; innerType?: { _def: { values?: string[] } } } }
      | undefined;
    return fieldSchema?._def?.values ?? fieldSchema?._def?.innerType?._def?.values ?? [];
  }

  const submitHandler = handleSubmit((data) => {
    onSubmit(data as Record<string, unknown>);
  });

  // Build a single form field element
  function renderField(key: string) {
    const fieldType = getFieldType(key);
    const error = errors[key];
    const errorMessage = error?.message as string | undefined;
    const inputClass = `${errorMessage !== undefined ? 'field-error' : ''}`;

    if (fieldType === 'boolean') {
      return (
        <div className="form-field" key={key}>
          <label className="checkbox-label">
            <input
              id={`field-${key}`}
              type="checkbox"
              {...register(key)}
            />
            {key}
          </label>
          {errorMessage !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorMessage}
            </span>
          )}
        </div>
      );
    }

    return (
      <div className="form-field" key={key}>
        <label htmlFor={`field-${key}`}>{key}</label>

        {fieldType === 'enum' ? (
          <select
            id={`field-${key}`}
            className={inputClass}
            {...register(key)}
          >
            {getEnumValues(key).map((v) => (
              <option key={v} value={v}>
                {v}
              </option>
            ))}
          </select>
        ) : (
          <input
            id={`field-${key}`}
            type={fieldType === 'number' ? 'number' : 'text'}
            className={inputClass}
            {...register(key, {
              valueAsNumber: fieldType === 'number',
            })}
          />
        )}

        {errorMessage !== undefined && (
          <span className="field-error-msg" role="alert">
            {errorMessage}
          </span>
        )}
      </div>
    );
  }

  // Check for fieldset grouping
  const fieldsets = getFieldsets(nodeType);

  if (fieldsets !== null) {
    const groupedFields = new Set(fieldsets.flatMap((fs) => fs.fields));
    const ungroupedKeys = fieldKeys.filter((key) => !groupedFields.has(key));

    return (
      <form className="property-form" onSubmit={(e) => void submitHandler(e)} noValidate>
        {fieldsets.map((fs) => (
          <fieldset key={fs.legend} className="property-fieldset">
            <legend>{fs.legend}</legend>
            {fs.fields
              .filter((f) => fieldKeys.includes(f))
              .map((key) => renderField(key))}
          </fieldset>
        ))}

        {ungroupedKeys.length > 0 && ungroupedKeys.map((key) => renderField(key))}

        <button type="submit" className="form-submit-btn">
          Apply
        </button>
      </form>
    );
  }

  return (
    <form className="property-form" onSubmit={(e) => void submitHandler(e)} noValidate>
      {fieldKeys.map((key) => renderField(key))}

      <button type="submit" className="form-submit-btn">
        Apply
      </button>
    </form>
  );
}
