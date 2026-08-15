import { describe, expect, it } from 'vitest';
import { decodeJwt } from './jwt';

function fakeJwt(claims: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify(claims));
  return `${header}.${payload}.fake-signature`;
}

describe('decodeJwt', () => {
  it('decodes the payload segment of a JWT', () => {
    const token = fakeJwt({ sub: '1', userId: 1, email: 'a@example.com', roles: ['USER'], iat: 1, exp: 2 });

    const claims = decodeJwt(token);

    expect(claims).toEqual({ sub: '1', userId: 1, email: 'a@example.com', roles: ['USER'], iat: 1, exp: 2 });
  });
});
