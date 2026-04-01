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
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { PropertyPanel } from '../components/PropertyPanel/PropertyPanel.js';
import { useTestPlanStore } from '../store/testPlanStore.js';
import type { TestPlanNode, TestPlanTree } from '../types/test-plan.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeNode(
  id: string,
  type: string,
  properties: Record<string, unknown> = {},
): TestPlanNode {
  return {
    id,
    type,
    name: `Node ${id}`,
    enabled: true,
    properties,
    children: [],
  };
}

function makeTree(root: TestPlanNode): TestPlanTree {
  return { root };
}

/** Set an input value and fire change + blur to trigger react-hook-form. */
function fillInput(input: HTMLElement, value: string) {
  fireEvent.change(input, { target: { value } });
  fireEvent.blur(input);
}

beforeEach(() => {
  useTestPlanStore.setState({ tree: null, selectedNodeId: null });
  useTestPlanStore.temporal.getState().clear();
});

// ---------------------------------------------------------------------------
// Placeholder state
// ---------------------------------------------------------------------------

describe('PropertyPanel — no node selected', () => {
  it('renders the Properties heading', () => {
    render(<PropertyPanel />);
    expect(screen.getByRole('heading', { name: /Properties/i })).toBeInTheDocument();
  });

  it('shows a placeholder when nothing is selected', () => {
    render(<PropertyPanel />);
    expect(screen.getByText(/Select a node to edit its properties/i)).toBeInTheDocument();
  });

  it('shows the placeholder when tree is loaded but selectedNodeId is null', () => {
    useTestPlanStore.getState().setTree(makeTree(makeNode('root', 'TestPlan')));
    render(<PropertyPanel />);
    expect(screen.getByText(/Select a node to edit its properties/i)).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// ThreadGroup form
// ---------------------------------------------------------------------------

describe('PropertyPanel — ThreadGroup form', () => {
  beforeEach(() => {
    const node = makeNode('tg-1', 'ThreadGroup', {
      num_threads: 10,
      ramp_time: 5,
      loops: 1,
      duration: 0,
    });
    useTestPlanStore.getState().setTree(makeTree(node));
    useTestPlanStore.getState().selectNode('tg-1');
  });

  it('renders all ThreadGroup fields', () => {
    render(<PropertyPanel />);
    expect(screen.getByLabelText('Number of Threads (users)')).toBeInTheDocument();
    expect(screen.getByLabelText('Ramp-up period (seconds)')).toBeInTheDocument();
    expect(screen.getByLabelText('Loop Count')).toBeInTheDocument();
    expect(screen.getByLabelText('Duration (seconds)')).toBeInTheDocument();
  });

  it('prepopulates fields from node properties', () => {
    render(<PropertyPanel />);
    const input = screen.getByLabelText('Number of Threads (users)') as HTMLInputElement;
    expect(input.value).toBe('10');
  });

  it('shows "Must be at least 1" error for invalid thread count (-1)', async () => {
    render(<PropertyPanel />);
    const input = screen.getByLabelText('Number of Threads (users)');

    fillInput(input, '-1');

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Must be at least 1');
    });
  });

  it('shows error when thread count is 0', async () => {
    render(<PropertyPanel />);
    const input = screen.getByLabelText('Number of Threads (users)');

    fillInput(input, '0');

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Must be at least 1');
    });
  });

  it('does not show an error for a valid thread count', async () => {
    render(<PropertyPanel />);
    const input = screen.getByLabelText('Number of Threads (users)');

    fillInput(input, '50');

    await waitFor(() => {
      expect(screen.queryByRole('alert')).toBeNull();
    });
  });

  it('updates the store on valid form submission', async () => {
    render(<PropertyPanel />);
    const input = screen.getByLabelText('Number of Threads (users)');

    fillInput(input, '20');

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Apply/i }));
    });

    await waitFor(() => {
      const props = useTestPlanStore.getState().tree?.root.properties;
      expect(props?.['num_threads']).toBe(20);
    });
  });
});

// ---------------------------------------------------------------------------
// HTTPSampler form
// ---------------------------------------------------------------------------

describe('PropertyPanel — HTTPSampler form', () => {
  beforeEach(() => {
    const node = makeNode('http-1', 'HTTPSampler', {
      domain: 'example.com',
      port: 443,
      path: '/api',
      method: 'GET',
      protocol: 'https',
    });
    useTestPlanStore.getState().setTree(makeTree(node));
    useTestPlanStore.getState().selectNode('http-1');
  });

  it('renders all HTTPSampler fields', () => {
    render(<PropertyPanel />);
    expect(screen.getByLabelText('Server Name or IP')).toBeInTheDocument();
    expect(screen.getByLabelText('Port Number')).toBeInTheDocument();
    expect(screen.getByLabelText('Path')).toBeInTheDocument();
    expect(screen.getByLabelText('Method')).toBeInTheDocument();
    expect(screen.getByLabelText('Protocol [http]')).toBeInTheDocument();
  });

  it('shows "Must be between 1 and 65535" for port 99999', async () => {
    render(<PropertyPanel />);
    const input = screen.getByLabelText('Port Number');

    fillInput(input, '99999');

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Must be between 1 and 65535');
    });
  });

  it('shows error for port 0 (below minimum)', async () => {
    render(<PropertyPanel />);
    const input = screen.getByLabelText('Port Number');

    fillInput(input, '0');

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Must be between 1 and 65535');
    });
  });

  it('does not show an error for a valid port', async () => {
    render(<PropertyPanel />);
    const input = screen.getByLabelText('Port Number');

    fillInput(input, '8080');

    await waitFor(() => {
      expect(screen.queryByRole('alert')).toBeNull();
    });
  });

  it('updates the store on valid form submission', async () => {
    render(<PropertyPanel />);
    const domainInput = screen.getByLabelText('Server Name or IP');

    fillInput(domainInput, 'api.example.com');

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Apply/i }));
    });

    await waitFor(() => {
      const props = useTestPlanStore.getState().tree?.root.properties;
      expect(props?.['domain']).toBe('api.example.com');
    });
  });

  it('renders method select with HTTP method options', () => {
    render(<PropertyPanel />);
    const select = screen.getByLabelText('Method') as HTMLSelectElement;
    const options = Array.from(select.options).map((o) => o.value);
    expect(options).toContain('GET');
    expect(options).toContain('POST');
    expect(options).toContain('PUT');
    expect(options).toContain('DELETE');
  });

  it('renders protocol select with http and https options', () => {
    render(<PropertyPanel />);
    const select = screen.getByLabelText('Protocol [http]') as HTMLSelectElement;
    const options = Array.from(select.options).map((o) => o.value);
    expect(options).toContain('http');
    expect(options).toContain('https');
  });
});

// ---------------------------------------------------------------------------
// Unknown node type
// ---------------------------------------------------------------------------

describe('PropertyPanel — unknown node type', () => {
  it('shows "no editable properties" message for unregistered types', async () => {
    const node = makeNode('x-1', 'ResponseAssertion');
    useTestPlanStore.getState().setTree(makeTree(node));
    useTestPlanStore.getState().selectNode('x-1');

    render(<PropertyPanel />);

    // T036: unregistered types now go through DynamicFormFallback which fetches
    // from the API asynchronously. In the test environment fetch fails (no server),
    // so the loading state resolves to the placeholder. waitFor handles both
    // the loading→resolved transition and any sync rendering.
    await waitFor(() => {
      expect(screen.getByText(/No editable properties for/i)).toBeInTheDocument();
      expect(screen.getByText(/ResponseAssertion/i)).toBeInTheDocument();
    }, { timeout: 5000 });
  });
});

// ---------------------------------------------------------------------------
// Store integration — updateProperty called for each field
// ---------------------------------------------------------------------------

describe('PropertyPanel — store integration', () => {
  it('persists ThreadGroup field value to the store on submit', async () => {
    const node = makeNode('tg-2', 'ThreadGroup', {
      num_threads: 1,
      ramp_time: 0,
      loops: 1,
      duration: 0,
    });
    useTestPlanStore.getState().setTree(makeTree(node));
    useTestPlanStore.getState().selectNode('tg-2');

    render(<PropertyPanel />);

    const rampInput = screen.getByLabelText('Ramp-up period (seconds)');
    fillInput(rampInput, '30');

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Apply/i }));
    });

    await waitFor(() => {
      const props = useTestPlanStore.getState().tree?.root.properties;
      expect(props?.['ramp_time']).toBe(30);
    });
  });

  it('does not update the store when validation fails', async () => {
    const node = makeNode('tg-3', 'ThreadGroup', {
      num_threads: 5,
      ramp_time: 1,
      loops: 1,
      duration: 0,
    });
    useTestPlanStore.getState().setTree(makeTree(node));
    useTestPlanStore.getState().selectNode('tg-3');

    render(<PropertyPanel />);

    // Set an invalid num_threads — form should not submit
    const threadsInput = screen.getByLabelText('Number of Threads (users)');
    fillInput(threadsInput, '-5');

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /Apply/i }));
    });

    // num_threads should remain unchanged in the store
    const props = useTestPlanStore.getState().tree?.root.properties;
    expect(props?.['num_threads']).toBe(5);
  });
});
