/**
 * Typed HTTP client for the jmeter-next backend API.
 *
 * All requests are sent to /api/v1. Responses are expected to follow the
 * ApiResponse<T> envelope: { data: T } on success, { error: string } on failure.
 *
 * Throws ApiError on any non-2xx response.
 */

const BASE_URL = '/api/v1';

/** Error thrown when the backend returns a non-2xx status. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** Shape of the JSON error envelope returned by the backend. */
interface ErrorEnvelope {
  error?: string;
  code?: string;
  message?: string;
}

/** Parse a non-2xx response body and throw an ApiError. */
async function throwApiError(response: Response): Promise<never> {
  let code = 'UNKNOWN';
  let message = `HTTP ${response.status}`;

  try {
    const body = (await response.json()) as ErrorEnvelope;
    code = body.code ?? code;
    message = body.message ?? body.error ?? message;
  } catch {
    // Body is not JSON — use defaults
  }

  throw new ApiError(response.status, code, message);
}

/** Issue a GET request and return the parsed response body. */
export async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    await throwApiError(response);
  }

  return response.json() as Promise<T>;
}

/** Issue a POST request with an optional JSON body and return the parsed response. */
export async function post<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!response.ok) {
    await throwApiError(response);
  }

  return response.json() as Promise<T>;
}

/** Issue a PUT request with a JSON body and return the parsed response. */
export async function put<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    await throwApiError(response);
  }

  return response.json() as Promise<T>;
}

/** Issue a DELETE request. Resolves when the server confirms deletion. */
export async function del(path: string): Promise<void> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'DELETE',
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    await throwApiError(response);
  }
}

/** Upload a file via multipart/form-data POST and return the parsed response. */
export async function uploadFile<T>(path: string, file: File): Promise<T> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: { Accept: 'application/json' },
    body: formData,
  });

  if (!response.ok) {
    await throwApiError(response);
  }

  return response.json() as Promise<T>;
}

/** Download a binary response and return it as a Blob. */
export async function downloadBlob(path: string): Promise<Blob> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'GET',
  });

  if (!response.ok) {
    await throwApiError(response);
  }

  return response.blob();
}
