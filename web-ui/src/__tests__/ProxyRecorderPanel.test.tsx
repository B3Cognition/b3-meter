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
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { ProxyRecorderPanel } from '../components/ProxyRecorder/ProxyRecorderPanel.js';

// ---------------------------------------------------------------------------
// Mock the API module
// ---------------------------------------------------------------------------

vi.mock('../api/proxyRecorder.js', () => ({
  startRecording:       vi.fn(),
  stopRecording:        vi.fn(),
  getRecorderStatus:    vi.fn(),
  getCapturedRequests:  vi.fn(),
  applyToTestPlan:      vi.fn(),
  submitCapturedRequest: vi.fn(),
  DEFAULT_CONFIG: {
    port: 8888,
    targetBaseUrl: null,
    includePatterns: [],
    excludePatterns: ['.*\\.(gif|jpg|jpeg|png|css|js|ico|woff|woff2|ttf|svg|webp)(\\?.*)?$'],
    captureHeaders: true,
    captureBody: true,
  },
}));

import * as proxyApi from '../api/proxyRecorder.js';
import type { CapturedRequest, AppliedNode } from '../api/proxyRecorder.js';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const IDLE_STATUS = {
  recording: false,
  config: {
    port: 8888,
    targetBaseUrl: null,
    includePatterns: [],
    excludePatterns: ['.*\\.(gif|jpg|jpeg|png|css|js|ico|woff|woff2|ttf|svg|webp)(\\?.*)?$'],
    captureHeaders: true,
    captureBody: true,
  },
};

const RECORDING_STATUS = { ...IDLE_STATUS, recording: true };

function makeCapturedRequest(overrides: Partial<CapturedRequest> = {}): CapturedRequest {
  return {
    id:           'req-1',
    timestamp:    '2026-03-25T10:00:00Z',
    method:       'GET',
    url:          'https://api.example.com/users',
    headers:      {},
    bodyBase64:   null,
    responseCode: 200,
    elapsedMs:    42,
    ...overrides,
  };
}

function makeAppliedNode(id: string): AppliedNode {
  return {
    id,
    type: 'HTTPSampler',
    name: 'GET https://api.example.com/users',
    enabled: true,
    properties: { method: 'GET', server: 'api.example.com', path: '/users' },
    children: [],
  };
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(proxyApi.getRecorderStatus).mockResolvedValue(IDLE_STATUS);
  vi.mocked(proxyApi.getCapturedRequests).mockResolvedValue([]);
});

// ---------------------------------------------------------------------------
// Initial render
// ---------------------------------------------------------------------------

describe('ProxyRecorderPanel — initial render', () => {
  it('renders the panel title', async () => {
    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    expect(screen.getByText(/HTTP Proxy Recorder/i)).toBeInTheDocument();
  });

  it('shows Idle status badge initially', async () => {
    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await waitFor(() => {
      expect(screen.getByTestId('recorder-status')).toHaveTextContent('Idle');
    });
  });

  it('renders port field with default value 8888', async () => {
    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    const portInput = screen.getByLabelText('Proxy port') as HTMLInputElement;
    expect(portInput.value).toBe('8888');
  });

  it('renders capture headers checkbox checked by default', async () => {
    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    const cb = screen.getByLabelText('Capture headers') as HTMLInputElement;
    expect(cb.checked).toBe(true);
  });

  it('renders capture body checkbox checked by default', async () => {
    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    const cb = screen.getByLabelText('Capture body') as HTMLInputElement;
    expect(cb.checked).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Button states
// ---------------------------------------------------------------------------

describe('ProxyRecorderPanel — button initial states', () => {
  it('Start button enabled before recording starts', async () => {
    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await waitFor(() => {
      expect(screen.getByTestId('start-recording-btn')).not.toBeDisabled();
    });
  });

  it('Stop button disabled before recording starts', async () => {
    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await waitFor(() => {
      expect(screen.getByTestId('stop-recording-btn')).toBeDisabled();
    });
  });

  it('Apply button disabled when no captures', async () => {
    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    expect(screen.getByTestId('apply-btn')).toBeDisabled();
  });

  it('Start and Stop buttons disabled when no planId', async () => {
    await act(async () => { render(<ProxyRecorderPanel />); });
    expect(screen.getByTestId('start-recording-btn')).toBeDisabled();
    expect(screen.getByTestId('stop-recording-btn')).toBeDisabled();
  });
});

// ---------------------------------------------------------------------------
// Start recording
// ---------------------------------------------------------------------------

describe('ProxyRecorderPanel — start recording', () => {
  it('calls startRecording API with planId and config', async () => {
    vi.mocked(proxyApi.startRecording).mockResolvedValue(IDLE_STATUS.config);

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await waitFor(() => screen.getByTestId('start-recording-btn'));

    await act(async () => {
      fireEvent.click(screen.getByTestId('start-recording-btn'));
    });

    expect(proxyApi.startRecording).toHaveBeenCalledWith(
      'plan-1',
      expect.objectContaining({ port: 8888 }),
    );
  });

  it('transitions status badge to Recording after start', async () => {
    vi.mocked(proxyApi.startRecording).mockResolvedValue(RECORDING_STATUS.config);

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await waitFor(() => screen.getByTestId('start-recording-btn'));

    await act(async () => {
      fireEvent.click(screen.getByTestId('start-recording-btn'));
    });

    await waitFor(() => {
      expect(screen.getByTestId('recorder-status')).toHaveTextContent('Recording');
    });
  });

  it('disables config fields while recording', async () => {
    vi.mocked(proxyApi.startRecording).mockResolvedValue(RECORDING_STATUS.config);

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await waitFor(() => screen.getByTestId('start-recording-btn'));

    await act(async () => {
      fireEvent.click(screen.getByTestId('start-recording-btn'));
    });

    await waitFor(() => {
      expect(screen.getByLabelText('Proxy port')).toBeDisabled();
    });
  });

  it('shows error when startRecording API fails', async () => {
    vi.mocked(proxyApi.startRecording).mockRejectedValue(new Error('connection refused'));

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await waitFor(() => screen.getByTestId('start-recording-btn'));

    await act(async () => {
      fireEvent.click(screen.getByTestId('start-recording-btn'));
    });

    await waitFor(() => {
      expect(screen.getByTestId('recorder-error')).toHaveTextContent('connection refused');
    });
  });

  it('shows validation error when port is invalid', async () => {
    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await waitFor(() => screen.getByLabelText('Proxy port'));

    fireEvent.change(screen.getByLabelText('Proxy port'), { target: { value: '99999' } });

    await act(async () => {
      fireEvent.click(screen.getByTestId('start-recording-btn'));
    });

    expect(screen.getByTestId('recorder-error')).toHaveTextContent(/Port must be/i);
    expect(proxyApi.startRecording).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// Stop recording
// ---------------------------------------------------------------------------

describe('ProxyRecorderPanel — stop recording', () => {
  it('calls stopRecording API and transitions to Idle', async () => {
    vi.mocked(proxyApi.startRecording).mockResolvedValue(RECORDING_STATUS.config);
    vi.mocked(proxyApi.stopRecording).mockResolvedValue(undefined);
    vi.mocked(proxyApi.getCapturedRequests).mockResolvedValue([]);

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await waitFor(() => screen.getByTestId('start-recording-btn'));

    // Start first
    await act(async () => {
      fireEvent.click(screen.getByTestId('start-recording-btn'));
    });
    await waitFor(() =>
      expect(screen.getByTestId('recorder-status')).toHaveTextContent('Recording'),
    );

    // Now stop
    await act(async () => {
      fireEvent.click(screen.getByTestId('stop-recording-btn'));
    });

    expect(proxyApi.stopRecording).toHaveBeenCalledWith('plan-1');
    await waitFor(() => {
      expect(screen.getByTestId('recorder-status')).toHaveTextContent('Idle');
    });
  });
});

// ---------------------------------------------------------------------------
// Captured requests list
// ---------------------------------------------------------------------------

describe('ProxyRecorderPanel — captured list', () => {
  it('shows empty state message initially', async () => {
    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    expect(
      screen.getByText(/No requests captured/i),
    ).toBeInTheDocument();
  });

  it('renders captured request with method and URL', async () => {
    vi.mocked(proxyApi.getCapturedRequests).mockResolvedValue([
      makeCapturedRequest({ method: 'POST', url: 'https://api.example.com/items' }),
    ]);

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });

    // Trigger a refresh to load the captures
    await act(async () => {
      fireEvent.click(screen.getByTestId('refresh-captured-btn'));
    });

    await waitFor(() => {
      expect(screen.getByText('POST')).toBeInTheDocument();
      expect(screen.getByText('https://api.example.com/items')).toBeInTheDocument();
    });
  });

  it('renders status code for captured request', async () => {
    vi.mocked(proxyApi.getCapturedRequests).mockResolvedValue([
      makeCapturedRequest({ responseCode: 201 }),
    ]);

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await act(async () => {
      fireEvent.click(screen.getByTestId('refresh-captured-btn'));
    });

    await waitFor(() => {
      expect(screen.getByTestId('captured-status-code')).toHaveTextContent('201');
    });
  });

  it('shows correct capture count', async () => {
    vi.mocked(proxyApi.getCapturedRequests).mockResolvedValue([
      makeCapturedRequest({ id: 'r1' }),
      makeCapturedRequest({ id: 'r2' }),
      makeCapturedRequest({ id: 'r3' }),
    ]);

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await act(async () => {
      fireEvent.click(screen.getByTestId('refresh-captured-btn'));
    });

    await waitFor(() => {
      expect(screen.getByTestId('captured-count')).toHaveTextContent('3 requests');
    });
  });

  it('shows "1 request" (singular) for a single capture', async () => {
    vi.mocked(proxyApi.getCapturedRequests).mockResolvedValue([makeCapturedRequest()]);

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await act(async () => {
      fireEvent.click(screen.getByTestId('refresh-captured-btn'));
    });

    await waitFor(() => {
      expect(screen.getByTestId('captured-count')).toHaveTextContent('1 request');
    });
  });
});

// ---------------------------------------------------------------------------
// Apply to Test Plan
// ---------------------------------------------------------------------------

describe('ProxyRecorderPanel — apply to test plan', () => {
  it('Apply button enabled when captures exist', async () => {
    vi.mocked(proxyApi.getCapturedRequests).mockResolvedValue([makeCapturedRequest()]);

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await act(async () => {
      fireEvent.click(screen.getByTestId('refresh-captured-btn'));
    });

    await waitFor(() => {
      expect(screen.getByTestId('apply-btn')).not.toBeDisabled();
    });
  });

  it('calls applyToTestPlan and invokes onApply callback', async () => {
    const onApply = vi.fn();
    const nodes = [makeAppliedNode('node-1')];
    vi.mocked(proxyApi.getCapturedRequests).mockResolvedValue([makeCapturedRequest()]);
    vi.mocked(proxyApi.applyToTestPlan).mockResolvedValue(nodes);

    await act(async () => {
      render(<ProxyRecorderPanel planId="plan-1" onApply={onApply} />);
    });
    await act(async () => {
      fireEvent.click(screen.getByTestId('refresh-captured-btn'));
    });
    await waitFor(() => screen.getByTestId('apply-btn'));

    await act(async () => {
      fireEvent.click(screen.getByTestId('apply-btn'));
    });

    expect(proxyApi.applyToTestPlan).toHaveBeenCalledWith('plan-1');
    expect(onApply).toHaveBeenCalledWith(nodes);
  });

  it('shows error when applyToTestPlan fails', async () => {
    vi.mocked(proxyApi.getCapturedRequests).mockResolvedValue([makeCapturedRequest()]);
    vi.mocked(proxyApi.applyToTestPlan).mockRejectedValue(new Error('apply error'));

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    await act(async () => {
      fireEvent.click(screen.getByTestId('refresh-captured-btn'));
    });
    await waitFor(() => screen.getByTestId('apply-btn'));

    await act(async () => {
      fireEvent.click(screen.getByTestId('apply-btn'));
    });

    await waitFor(() => {
      expect(screen.getByTestId('recorder-error')).toHaveTextContent('apply error');
    });
  });
});

// ---------------------------------------------------------------------------
// Refresh captured list
// ---------------------------------------------------------------------------

describe('ProxyRecorderPanel — refresh', () => {
  it('Refresh button calls getCapturedRequests', async () => {
    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });
    vi.clearAllMocks();
    vi.mocked(proxyApi.getCapturedRequests).mockResolvedValue([]);

    await act(async () => {
      fireEvent.click(screen.getByTestId('refresh-captured-btn'));
    });

    expect(proxyApi.getCapturedRequests).toHaveBeenCalledWith('plan-1');
  });

  it('shows error when getCapturedRequests fails on refresh', async () => {
    vi.mocked(proxyApi.getCapturedRequests).mockRejectedValue(new Error('fetch failed'));

    await act(async () => { render(<ProxyRecorderPanel planId="plan-1" />); });

    await act(async () => {
      fireEvent.click(screen.getByTestId('refresh-captured-btn'));
    });

    await waitFor(() => {
      expect(screen.getByTestId('recorder-error')).toHaveTextContent('fetch failed');
    });
  });
});
