/**
 * DynamicForm — renders a React Hook Form backed form from a ComponentSchema
 * received from the backend API (GET /api/v1/schemas/{componentName}).
 *
 * Design contract (T036 — MAVERICK Alternative 3):
 *   - The form is driven entirely by the schema; no hard-coded field list.
 *   - Hand-coded forms in the schema registry (T024) take priority.
 *     PropertyPanel checks the registry first and falls back to DynamicForm
 *     only when no hand-coded schema exists for the component type.
 *   - String  → <input type="text">
 *   - integer → <input type="number">
 *   - number  → <input type="number">
 *   - boolean → <input type="checkbox">
 *   - enum    → <select> with enumValues as <option> elements
 */

import { useForm } from 'react-hook-form';
import './PropertyPanel.css';

// ---------------------------------------------------------------------------
// Schema types (mirror of the backend ComponentSchemaDto / PropertySchemaDto)
// ---------------------------------------------------------------------------

/** Mirrors the backend PropertySchemaDto */
export interface PropertySchemaDto {
  name: string;
  displayName: string;
  /** "string" | "integer" | "number" | "boolean" | "enum" */
  type: string;
  defaultValue?: unknown;
  required: boolean;
  /** Only set when type === "enum" */
  enumValues?: string[] | null;
}

/** Mirrors the backend ComponentSchemaDto */
export interface ComponentSchemaDto {
  componentName: string;
  componentCategory: string;
  properties: PropertySchemaDto[];
}

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

export interface DynamicFormProps {
  /** The schema retrieved from GET /api/v1/schemas/{componentName} */
  schema: ComponentSchemaDto;
  /** Initial property values already stored on the node */
  initialValues?: Record<string, unknown>;
  /** Called with the full form values after successful submission */
  onSubmit: (values: Record<string, unknown>) => void;
}

// ---------------------------------------------------------------------------
// DynamicForm component
// ---------------------------------------------------------------------------

/**
 * Generates a form from a {@link ComponentSchemaDto} without any hand-coded
 * panel.  Every property in the schema becomes a form field whose input type
 * is chosen from the JSON Schema type.
 *
 * The component uses React Hook Form in uncontrolled mode for performance.
 * Validation is minimal by default (required check only); richer Zod
 * validation is supplied by the hand-coded schemas (T024) which take priority
 * over this dynamic fallback.
 */
export function DynamicForm({ schema, initialValues = {}, onSubmit }: DynamicFormProps) {
  const defaultValues = buildDefaultValues(schema, initialValues);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Record<string, unknown>>({
    defaultValues,
    mode: 'onBlur',
  });

  const submitHandler = handleSubmit((data) => {
    // Coerce numeric fields back to numbers (RHF returns strings for text inputs)
    const coerced = coerceValues(data, schema);
    onSubmit(coerced);
  });

  return (
    <form
      className="property-form dynamic-form"
      onSubmit={(e) => void submitHandler(e)}
      noValidate
      aria-label={`${schema.componentName} properties`}
    >
      {schema.properties.map((prop) => (
        <DynamicField
          key={prop.name}
          prop={prop}
          register={register}
          error={errors[prop.name]?.message as string | undefined}
        />
      ))}

      <button type="submit" className="form-submit-btn">
        Apply
      </button>
    </form>
  );
}

// ---------------------------------------------------------------------------
// DynamicField — renders one field based on its schema type
// ---------------------------------------------------------------------------

interface DynamicFieldProps {
  prop: PropertySchemaDto;
  register: ReturnType<typeof useForm>['register'];
  error: string | undefined;
}

function DynamicField({ prop, register, error }: DynamicFieldProps) {
  const inputClass = error !== undefined ? 'field-error' : '';
  const fieldId = `dynamic-field-${prop.name}`;

  return (
    <div className="form-field" key={prop.name}>
      <label htmlFor={fieldId}>{prop.displayName}</label>

      {prop.type === 'boolean' ? (
        <input
          id={fieldId}
          type="checkbox"
          className={inputClass}
          {...register(prop.name, { required: prop.required })}
        />
      ) : prop.type === 'enum' ? (
        <select
          id={fieldId}
          className={inputClass}
          {...register(prop.name, { required: prop.required })}
        >
          {(prop.enumValues ?? []).map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      ) : prop.type === 'integer' || prop.type === 'number' ? (
        <input
          id={fieldId}
          type="number"
          className={inputClass}
          step={prop.type === 'integer' ? '1' : 'any'}
          {...register(prop.name, {
            required: prop.required,
            valueAsNumber: true,
          })}
        />
      ) : (
        /* Default: string */
        <input
          id={fieldId}
          type="text"
          className={inputClass}
          {...register(prop.name, { required: prop.required })}
        />
      )}

      {error !== undefined && (
        <span className="field-error-msg" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Merges schema defaults with already-stored node values.
 * Schema defaultValue acts as the baseline; stored values override it.
 */
function buildDefaultValues(
  schema: ComponentSchemaDto,
  stored: Record<string, unknown>,
): Record<string, unknown> {
  const defaults: Record<string, unknown> = {};
  for (const prop of schema.properties) {
    defaults[prop.name] = prop.defaultValue ?? '';
  }
  return { ...defaults, ...stored };
}

/**
 * After form submission, RHF returns everything as strings for text/number
 * inputs (when valueAsNumber is not set).  This function re-coerces fields
 * that the schema marks as integer/number back to JS numbers.
 */
function coerceValues(
  data: Record<string, unknown>,
  schema: ComponentSchemaDto,
): Record<string, unknown> {
  const result: Record<string, unknown> = { ...data };
  for (const prop of schema.properties) {
    const raw = result[prop.name];
    if ((prop.type === 'integer' || prop.type === 'number') && typeof raw === 'string') {
      const parsed = Number(raw);
      if (!Number.isNaN(parsed)) {
        result[prop.name] = parsed;
      }
    }
  }
  return result;
}
