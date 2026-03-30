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
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { DynamicForm } from '../components/PropertyPanel/DynamicForm.js';
import type { ComponentSchemaDto, PropertySchemaDto } from '../components/PropertyPanel/DynamicForm.js';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const stringProp: PropertySchemaDto = {
  name: 'domain',
  displayName: 'Server Name or IP',
  type: 'string',
  defaultValue: 'localhost',
  required: true,
  enumValues: null,
};

const intProp: PropertySchemaDto = {
  name: 'port',
  displayName: 'Port Number',
  type: 'integer',
  defaultValue: 80,
  required: true,
  enumValues: null,
};

const numberProp: PropertySchemaDto = {
  name: 'ratio',
  displayName: 'Sampling Ratio',
  type: 'number',
  defaultValue: 0.5,
  required: false,
  enumValues: null,
};

const boolProp: PropertySchemaDto = {
  name: 'keepAlive',
  displayName: 'Keep Alive',
  type: 'boolean',
  defaultValue: false,
  required: false,
  enumValues: null,
};

const enumProp: PropertySchemaDto = {
  name: 'method',
  displayName: 'HTTP Method',
  type: 'enum',
  defaultValue: 'GET',
  required: true,
  enumValues: ['GET', 'POST', 'PUT', 'DELETE'],
};

const fullSchema: ComponentSchemaDto = {
  componentName: 'HTTPSampler',
  componentCategory: 'sampler',
  properties: [stringProp, intProp, boolProp, enumProp],
};

const threadGroupSchema: ComponentSchemaDto = {
  componentName: 'ThreadGroup',
  componentCategory: 'thread',
  properties: [
    {
      name: 'num_threads',
      displayName: 'Number of Threads',
      type: 'integer',
      defaultValue: 1,
      required: true,
      enumValues: null,
    },
    {
      name: 'ramp_time',
      displayName: 'Ramp-Up Period (seconds)',
      type: 'integer',
      defaultValue: 1,
      required: true,
      enumValues: null,
    },
    {
      name: 'on_sample_error',
      displayName: 'Action After Sampler Error',
      type: 'enum',
      defaultValue: 'CONTINUE',
      required: false,
      enumValues: ['CONTINUE', 'STOP_THREAD', 'STOP_TEST'],
    },
  ],
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function fillInput(input: HTMLElement, value: string) {
  fireEvent.change(input, { target: { value } });
  fireEvent.blur(input);
}

// ---------------------------------------------------------------------------
// Tests: field rendering by type
// ---------------------------------------------------------------------------

describe('DynamicForm — field types', () => {
  it('renders a string property as a text input', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [stringProp] }}
        onSubmit={vi.fn()}
      />,
    );
    const input = screen.getByLabelText('Server Name or IP') as HTMLInputElement;
    expect(input).toBeInTheDocument();
    expect(input.type).toBe('text');
  });

  it('renders an integer property as a number input', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [intProp] }}
        onSubmit={vi.fn()}
      />,
    );
    const input = screen.getByLabelText('Port Number') as HTMLInputElement;
    expect(input).toBeInTheDocument();
    expect(input.type).toBe('number');
  });

  it('integer input has step="1"', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [intProp] }}
        onSubmit={vi.fn()}
      />,
    );
    const input = screen.getByLabelText('Port Number') as HTMLInputElement;
    expect(input.step).toBe('1');
  });

  it('renders a number (float) property as a number input with step=any', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [numberProp] }}
        onSubmit={vi.fn()}
      />,
    );
    const input = screen.getByLabelText('Sampling Ratio') as HTMLInputElement;
    expect(input.type).toBe('number');
    expect(input.step).toBe('any');
  });

  it('renders a boolean property as a checkbox', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [boolProp] }}
        onSubmit={vi.fn()}
      />,
    );
    const input = screen.getByLabelText('Keep Alive') as HTMLInputElement;
    expect(input).toBeInTheDocument();
    expect(input.type).toBe('checkbox');
  });

  it('renders an enum property as a select element', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [enumProp] }}
        onSubmit={vi.fn()}
      />,
    );
    const select = screen.getByLabelText('HTTP Method') as HTMLSelectElement;
    expect(select.tagName).toBe('SELECT');
  });

  it('enum select contains all enumValues as options', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [enumProp] }}
        onSubmit={vi.fn()}
      />,
    );
    const select = screen.getByLabelText('HTTP Method') as HTMLSelectElement;
    const options = Array.from(select.options).map((o) => o.value);
    expect(options).toContain('GET');
    expect(options).toContain('POST');
    expect(options).toContain('PUT');
    expect(options).toContain('DELETE');
  });

  it('enum select has exactly the number of options matching enumValues', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [enumProp] }}
        onSubmit={vi.fn()}
      />,
    );
    const select = screen.getByLabelText('HTTP Method') as HTMLSelectElement;
    expect(select.options.length).toBe(4);
  });
});

// ---------------------------------------------------------------------------
// Tests: displayName as label
// ---------------------------------------------------------------------------

describe('DynamicForm — labels', () => {
  it('uses displayName as the field label text', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [stringProp] }}
        onSubmit={vi.fn()}
      />,
    );
    expect(screen.getByText('Server Name or IP')).toBeInTheDocument();
  });

  it('label is associated with the input via htmlFor', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [stringProp] }}
        onSubmit={vi.fn()}
      />,
    );
    // getByLabelText succeeds only if label.htmlFor matches input.id
    expect(screen.getByLabelText('Server Name or IP')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Tests: initial values
// ---------------------------------------------------------------------------

describe('DynamicForm — initial values', () => {
  it('prepopulates a string field from initialValues', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [stringProp] }}
        initialValues={{ domain: 'api.example.com' }}
        onSubmit={vi.fn()}
      />,
    );
    const input = screen.getByLabelText('Server Name or IP') as HTMLInputElement;
    expect(input.value).toBe('api.example.com');
  });

  it('falls back to schema defaultValue when initialValues does not override', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [stringProp] }}
        initialValues={{}}
        onSubmit={vi.fn()}
      />,
    );
    const input = screen.getByLabelText('Server Name or IP') as HTMLInputElement;
    expect(input.value).toBe('localhost');
  });

  it('prepopulates an integer field from initialValues', () => {
    render(
      <DynamicForm
        schema={{ componentName: 'Test', componentCategory: 'test', properties: [intProp] }}
        initialValues={{ port: 8443 }}
        onSubmit={vi.fn()}
      />,
    );
    const input = screen.getByLabelText('Port Number') as HTMLInputElement;
    expect(input.value).toBe('8443');
  });
});

// ---------------------------------------------------------------------------
// Tests: multi-property schema
// ---------------------------------------------------------------------------

describe('DynamicForm — full schema', () => {
  it('renders all properties from the schema', () => {
    render(<DynamicForm schema={fullSchema} onSubmit={vi.fn()} />);
    expect(screen.getByLabelText('Server Name or IP')).toBeInTheDocument();
    expect(screen.getByLabelText('Port Number')).toBeInTheDocument();
    expect(screen.getByLabelText('Keep Alive')).toBeInTheDocument();
    expect(screen.getByLabelText('HTTP Method')).toBeInTheDocument();
  });

  it('renders the Apply button', () => {
    render(<DynamicForm schema={fullSchema} onSubmit={vi.fn()} />);
    expect(screen.getByRole('button', { name: /Apply/i })).toBeInTheDocument();
  });

  it('form has aria-label with componentName', () => {
    render(<DynamicForm schema={fullSchema} onSubmit={vi.fn()} />);
    expect(screen.getByRole('form')).toHaveAttribute(
      'aria-label',
      'HTTPSampler properties',
    );
  });
});

// ---------------------------------------------------------------------------
// Tests: onSubmit callback
// ---------------------------------------------------------------------------

describe('DynamicForm — onSubmit', () => {
  it('calls onSubmit with form values when Apply is clicked', async () => {
    const handleSubmit = vi.fn();
    render(
      <DynamicForm
        schema={threadGroupSchema}
        initialValues={{ num_threads: 5, ramp_time: 10, on_sample_error: 'CONTINUE' }}
        onSubmit={handleSubmit}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Apply/i }));
    });

    await waitFor(() => {
      expect(handleSubmit).toHaveBeenCalledOnce();
    });
  });

  it('onSubmit receives integer value as a number (not a string)', async () => {
    const handleSubmit = vi.fn();
    render(
      <DynamicForm
        schema={threadGroupSchema}
        initialValues={{ num_threads: 1, ramp_time: 1, on_sample_error: 'CONTINUE' }}
        onSubmit={handleSubmit}
      />,
    );

    const threadsInput = screen.getByLabelText('Number of Threads');
    fillInput(threadsInput, '25');

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Apply/i }));
    });

    await waitFor(() => {
      expect(handleSubmit).toHaveBeenCalled();
      const submitted = handleSubmit.mock.calls[0]?.[0] as Record<string, unknown>;
      expect(typeof submitted['num_threads']).toBe('number');
      expect(submitted['num_threads']).toBe(25);
    });
  });

  it('onSubmit receives the selected enum value as a string', async () => {
    const handleSubmit = vi.fn();
    render(
      <DynamicForm
        schema={threadGroupSchema}
        initialValues={{ num_threads: 1, ramp_time: 1, on_sample_error: 'CONTINUE' }}
        onSubmit={handleSubmit}
      />,
    );

    const select = screen.getByLabelText('Action After Sampler Error') as HTMLSelectElement;
    fireEvent.change(select, { target: { value: 'STOP_TEST' } });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Apply/i }));
    });

    await waitFor(() => {
      expect(handleSubmit).toHaveBeenCalled();
      const submitted = handleSubmit.mock.calls[0]?.[0] as Record<string, unknown>;
      expect(submitted['on_sample_error']).toBe('STOP_TEST');
    });
  });
});

// ---------------------------------------------------------------------------
// Tests: hand-coded override priority (documented behaviour, not enforced here)
// ---------------------------------------------------------------------------

describe('DynamicForm — hand-coded override contract', () => {
  it('renders correctly for a component that also has a hand-coded schema (ThreadGroup)', () => {
    // DynamicForm has no knowledge of the schema registry — it just renders the
    // schema it receives.  PropertyPanel is responsible for choosing between
    // hand-coded and dynamic.  This test documents that DynamicForm works for
    // ThreadGroup properties so it could serve as a fallback if the hand-coded
    // schema were removed.
    render(
      <DynamicForm
        schema={threadGroupSchema}
        initialValues={{ num_threads: 3, ramp_time: 5, on_sample_error: 'CONTINUE' }}
        onSubmit={vi.fn()}
      />,
    );
    expect(screen.getByLabelText('Number of Threads')).toBeInTheDocument();
    expect(screen.getByLabelText('Ramp-Up Period (seconds)')).toBeInTheDocument();
    expect(screen.getByLabelText('Action After Sampler Error')).toBeInTheDocument();
  });
});
