import { afterEach, describe, expect, it } from 'vitest';
import type { InternalAxiosRequestConfig } from 'axios';
import { createApiClient } from './client';
import { setAuthToken } from './authToken';

function stubbedClient() {
  const capturedConfigs: InternalAxiosRequestConfig[] = [];
  const client = createApiClient({
    adapter: async (config) => {
      capturedConfigs.push(config);
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config };
    },
  });
  return { client, capturedConfigs };
}

describe('apiClient interceptors', () => {
  afterEach(() => {
    setAuthToken(null);
  });

  it('attaches an Authorization header when a token is set', async () => {
    setAuthToken('test-token');
    const { client, capturedConfigs } = stubbedClient();

    await client.get('/api/v1/accounts/me');

    expect(capturedConfigs[0].headers.get('Authorization')).toBe('Bearer test-token');
  });

  it('omits the Authorization header when no token is set', async () => {
    const { client, capturedConfigs } = stubbedClient();

    await client.get('/api/v1/accounts/me');

    expect(capturedConfigs[0].headers.has('Authorization')).toBe(false);
  });

  it('attaches a unique X-Idempotency-Key to each transfer confirm request', async () => {
    const { client, capturedConfigs } = stubbedClient();

    await client.post('/api/v1/transfers/confirm', { challengeId: 'a', otpCode: '111111' });
    await client.post('/api/v1/transfers/confirm', { challengeId: 'a', otpCode: '111111' });

    const key1 = capturedConfigs[0].headers.get('X-Idempotency-Key');
    const key2 = capturedConfigs[1].headers.get('X-Idempotency-Key');
    expect(key1).toBeTruthy();
    expect(key2).toBeTruthy();
    expect(key1).not.toBe(key2);
  });

  it('does not attach X-Idempotency-Key to non-confirm requests', async () => {
    const { client, capturedConfigs } = stubbedClient();

    await client.post('/api/v1/transfers/initiate', {});

    expect(capturedConfigs[0].headers.has('X-Idempotency-Key')).toBe(false);
  });
});
