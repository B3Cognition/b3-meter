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
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { get, post, put, del, uploadFile, downloadBlob, ApiError } from '../api/client.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

type FetchMock = ReturnType<typeof vi.fn>;

function makeFetchMock(status: number, body: unknown, headers?: Record<string, string>): FetchMock {
  const responseHeaders = new Headers({ 'Content-Type': 'application/json', ...headers });
  const responseBody =
    body instanceof Blob ? body : typeof body === 'string' ? body : JSON.stringify(body);

  return vi.fn().mockResolvedValue(
    new Response(responseBody as BodyInit, {
      status,
      headers: responseHeaders,
    }),
  );
}

function mockFetch(status: number, body: unknown, headers?: Record<string, string>): void {
  vi.stubGlobal('fetch', makeFetchMock(status, body, headers));
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

// ---------------------------------------------------------------------------
// ApiError
// ---------------------------------------------------------------------------

describe('ApiError', () => {
  it('exposes status, code, and message', () => {
    const err = new ApiError(404, 'NOT_FOUND', 'Resource not found');
    expect(err.status).toBe(404);
    expect(err.code).toBe('NOT_FOUND');
    expect(err.message).toBe('Resource not found');
  });

  it('is an instance of Error', () => {
    const err = new ApiError(500, 'SERVER_ERROR', 'Internal error');
    expect(err).toBeInstanceOf(Error);
  });

  it('has name ApiError', () => {
    const err = new ApiError(400, 'BAD_REQUEST', 'Bad input');
    expect(err.name).toBe('ApiError');
  });
});

// ---------------------------------------------------------------------------
// get<T>
// ---------------------------------------------------------------------------

describe('get', () => {
  it('sends GET to /api/v1/{path} and returns parsed JSON', async () => {
    const payload = { planId: 'p1', name: 'My Plan' };
    mockFetch(200, payload);

    const result = await get<typeof payload>('/plans/p1');

    expect(result).toEqual(payload);
    const call = (fetch as unknown as FetchMock).mock.calls[0] as [string, RequestInit];
    expect(call[0]).toBe('/api/v1/plans/p1');
    expect(call[1].method).toBe('GET');
  });

  it('throws ApiError on 404 with parsed error envelope', async () => {
    mockFetch(404, { code: 'NOT_FOUND', message: 'Plan not found' });

    await expect(get('/plans/missing')).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
      message: 'Plan not found',
    });
  });

  it('throws ApiError on 500 with fallback message when body is not JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('Internal Server Error', {
          status: 500,
          headers: { 'Content-Type': 'text/plain' },
        }),
      ),
    );

    await expect(get('/plans')).rejects.toMatchObject({
      status: 500,
    });
  });

  it('throws ApiError on 401 unauthorized', async () => {
    mockFetch(401, { code: 'UNAUTHORIZED', message: 'Unauthorized' });

    await expect(get('/plans')).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
  });
});

// ---------------------------------------------------------------------------
// post<T>
// ---------------------------------------------------------------------------

describe('post', () => {
  it('sends POST with JSON body', async () => {
    const response = { planId: 'p2', name: 'New Plan' };
    mockFetch(201, response);

    const body = { name: 'New Plan' };
    const result = await post<typeof response>('/plans', body);

    expect(result).toEqual(response);
    const call = (fetch as unknown as FetchMock).mock.calls[0] as [string, RequestInit];
    expect(call[1].method).toBe('POST');
    expect(call[1].body).toBe(JSON.stringify(body));
  });

  it('sends POST without body when omitted', async () => {
    mockFetch(200, { runId: 'r1' });

    await post('/runs/r1/stop');

    const call = (fetch as unknown as FetchMock).mock.calls[0] as [string, RequestInit];
    expect(call[1].body).toBeUndefined();
  });

  it('throws ApiError on 422 validation error', async () => {
    mockFetch(422, { code: 'VALIDATION_ERROR', message: 'name is required' });

    await expect(post('/plans', {})).rejects.toMatchObject({
      status: 422,
      code: 'VALIDATION_ERROR',
    });
  });
});

// ---------------------------------------------------------------------------
// put<T>
// ---------------------------------------------------------------------------

describe('put', () => {
  it('sends PUT with JSON body', async () => {
    const response = { planId: 'p1', updatedAt: '2024-01-01T00:00:00Z' };
    mockFetch(200, response);

    const body = { tree: { root: { id: 'root', type: 'TestPlan', name: 'Plan', enabled: true, properties: {}, children: [] } } };
    const result = await put<typeof response>('/plans/p1', body);

    expect(result).toEqual(response);
    const call = (fetch as unknown as FetchMock).mock.calls[0] as [string, RequestInit];
    expect(call[1].method).toBe('PUT');
    expect(call[1].body).toBe(JSON.stringify(body));
  });

  it('throws ApiError on 404', async () => {
    mockFetch(404, { code: 'NOT_FOUND', message: 'Plan not found' });

    await expect(put('/plans/missing', {})).rejects.toMatchObject({ status: 404 });
  });
});

// ---------------------------------------------------------------------------
// del
// ---------------------------------------------------------------------------

describe('del', () => {
  it('sends DELETE and resolves on 204', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
    );

    await expect(del('/plans/p1')).resolves.toBeUndefined();
    const call = (fetch as unknown as FetchMock).mock.calls[0] as [string, RequestInit];
    expect(call[1].method).toBe('DELETE');
  });

  it('throws ApiError on 404', async () => {
    mockFetch(404, { code: 'NOT_FOUND', message: 'Not found' });

    await expect(del('/plans/missing')).rejects.toMatchObject({ status: 404 });
  });
});

// ---------------------------------------------------------------------------
// uploadFile<T>
// ---------------------------------------------------------------------------

describe('uploadFile', () => {
  it('sends multipart POST and returns parsed response', async () => {
    const response = { planId: 'p3', name: 'Imported' };
    mockFetch(201, response);

    const file = new File(['<jmx/>'], 'plan.jmx', { type: 'application/xml' });
    const result = await uploadFile<typeof response>('/plans/import', file);

    expect(result).toEqual(response);
    const call = (fetch as unknown as FetchMock).mock.calls[0] as [string, RequestInit];
    expect(call[1].method).toBe('POST');
    expect(call[1].body).toBeInstanceOf(FormData);
  });

  it('throws ApiError on server error', async () => {
    mockFetch(500, { code: 'PARSE_ERROR', message: 'Invalid JMX' });

    const file = new File(['bad'], 'plan.jmx');
    await expect(uploadFile('/plans/import', file)).rejects.toMatchObject({ status: 500 });
  });
});

// ---------------------------------------------------------------------------
// downloadBlob
// ---------------------------------------------------------------------------

describe('downloadBlob', () => {
  it('returns a Blob on success', async () => {
    const blobContent = new Blob(['<jmx/>'], { type: 'application/xml' });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(blobContent, { status: 200, headers: { 'Content-Type': 'application/xml' } }),
      ),
    );

    const result = await downloadBlob('/plans/p1/export');
    expect(result).toBeInstanceOf(Blob);
  });

  it('throws ApiError on 404', async () => {
    mockFetch(404, { code: 'NOT_FOUND', message: 'Plan not found' });

    await expect(downloadBlob('/plans/missing/export')).rejects.toMatchObject({ status: 404 });
  });
});
