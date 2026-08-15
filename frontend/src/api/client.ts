import axios, { AxiosHeaders, type AxiosInstance, type AxiosRequestConfig } from 'axios';
import { getAuthToken } from './authToken';

const CONFIRM_TRANSFER_PATH = '/api/v1/transfers/confirm';

/**
 * Factory rather than a bare singleton so tests can build an isolated client
 * with a stub adapter instead of mutating (and having to restore) shared state.
 */
export function createApiClient(config: AxiosRequestConfig = {}): AxiosInstance {
  const client = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
    ...config,
  });

  client.interceptors.request.use((requestConfig) => {
    const token = getAuthToken();
    if (token) {
      const headers = AxiosHeaders.from(requestConfig.headers);
      headers.set('Authorization', `Bearer ${token}`);
      requestConfig.headers = headers;
    }
    return requestConfig;
  });

  // One fresh UUID per confirm call - a genuine retry-after-error should get a
  // new key (it's a new attempt), while backend-side dedup of an in-flight
  // request/response pair is what X-Idempotency-Key on the server protects.
  client.interceptors.request.use((requestConfig) => {
    if (requestConfig.url?.endsWith(CONFIRM_TRANSFER_PATH)) {
      const headers = AxiosHeaders.from(requestConfig.headers);
      if (!headers.has('X-Idempotency-Key')) {
        headers.set('X-Idempotency-Key', crypto.randomUUID());
      }
      requestConfig.headers = headers;
    }
    return requestConfig;
  });

  return client;
}

export const apiClient = createApiClient();
