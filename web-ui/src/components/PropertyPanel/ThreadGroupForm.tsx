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
import { useEffect } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { SchemaEntry } from './schemas/index.js';
import { ON_SAMPLE_ERROR_OPTIONS } from './constants.js';

// ---------------------------------------------------------------------------
// ThreadGroupForm — custom hand-coded form with JMeter fieldsets
// ---------------------------------------------------------------------------

interface ThreadGroupFormProps {
  node: { id: string };
  schemaEntry: SchemaEntry;
  initialValues: Record<string, unknown>;
  onSubmit: (values: Record<string, unknown>) => void;
}

export function ThreadGroupForm({ node: _node, schemaEntry, initialValues, onSubmit }: ThreadGroupFormProps) {
  const {
    register,
    handleSubmit,
    setValue,
    control,
    formState: { errors },
  } = useForm<Record<string, unknown>>({
    resolver: zodResolver(schemaEntry.schema),
    defaultValues: initialValues,
    mode: 'onBlur',
  });

  // Watch reactive values for conditional rendering
  const infiniteValue = useWatch({ control, name: 'infinite' }) as boolean;
  const schedulerValue = useWatch({ control, name: 'scheduler' }) as boolean;

  // When infinite is toggled, set loops accordingly
  useEffect(() => {
    if (infiniteValue) {
      setValue('loops', -1);
    } else {
      const current = initialValues.loops as number;
      if (current === -1) {
        setValue('loops', 1);
      }
    }
  }, [infiniteValue, setValue, initialValues.loops]);

  const submitHandler = handleSubmit((data) => {
    onSubmit(data as Record<string, unknown>);
  });

  const errorFor = (key: string) => errors[key]?.message as string | undefined;

  return (
    <form className="property-form" onSubmit={(e) => void submitHandler(e)} noValidate>
      {/* Action on Sampler Error */}
      <fieldset className="property-fieldset">
        <legend>Action to be taken after a Sampler error</legend>
        <div className="form-field form-field-radios">
          {ON_SAMPLE_ERROR_OPTIONS.map((opt) => (
            <label key={opt.value} className="radio-label">
              <input
                type="radio"
                value={opt.value}
                {...register('onSampleError')}
              />
              {opt.label}
            </label>
          ))}
          {errorFor('onSampleError') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('onSampleError')}
            </span>
          )}
        </div>
      </fieldset>

      {/* Thread Properties */}
      <fieldset className="property-fieldset">
        <legend>Thread Properties</legend>

        <div className="form-field">
          <label htmlFor="field-num_threads">Number of Threads (users)</label>
          <input
            id="field-num_threads"
            type="number"
            className={errorFor('num_threads') !== undefined ? 'field-error' : ''}
            {...register('num_threads', { valueAsNumber: true })}
          />
          {errorFor('num_threads') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('num_threads')}
            </span>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="field-ramp_time">Ramp-up period (seconds)</label>
          <input
            id="field-ramp_time"
            type="number"
            className={errorFor('ramp_time') !== undefined ? 'field-error' : ''}
            {...register('ramp_time', { valueAsNumber: true })}
          />
          {errorFor('ramp_time') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('ramp_time')}
            </span>
          )}
        </div>

        <div className="form-field form-field-inline">
          <label htmlFor="field-loops">Loop Count</label>
          <div className="form-field-row">
            <input
              id="field-loops"
              type="number"
              className={errorFor('loops') !== undefined ? 'field-error' : ''}
              disabled={infiniteValue}
              {...register('loops', { valueAsNumber: true })}
            />
            <label className="checkbox-label">
              <input type="checkbox" {...register('infinite')} />
              Infinite
            </label>
          </div>
          {errorFor('loops') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('loops')}
            </span>
          )}
        </div>

        <div className="form-field">
          <label className="checkbox-label">
            <input type="checkbox" {...register('sameUserOnNextIteration')} />
            Same user on each iteration
          </label>
        </div>
      </fieldset>

      {/* Scheduler Configuration */}
      <fieldset className="property-fieldset">
        <legend>Scheduler Configuration</legend>

        <div className="form-field">
          <label className="checkbox-label">
            <input type="checkbox" {...register('scheduler')} />
            Scheduler
          </label>
        </div>

        <div className="form-field">
          <label htmlFor="field-duration">Duration (seconds)</label>
          <input
            id="field-duration"
            type="number"
            className={errorFor('duration') !== undefined ? 'field-error' : ''}
            disabled={!schedulerValue}
            {...register('duration', { valueAsNumber: true })}
          />
          {errorFor('duration') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('duration')}
            </span>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="field-delay">Startup delay (seconds)</label>
          <input
            id="field-delay"
            type="number"
            className={errorFor('delay') !== undefined ? 'field-error' : ''}
            disabled={!schedulerValue}
            {...register('delay', { valueAsNumber: true })}
          />
          {errorFor('delay') !== undefined && (
            <span className="field-error-msg" role="alert">
              {errorFor('delay')}
            </span>
          )}
        </div>
      </fieldset>

      <button type="submit" className="form-submit-btn">
        Apply
      </button>
    </form>
  );
}
