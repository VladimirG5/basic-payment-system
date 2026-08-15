export interface JwtClaims {
  sub: string;
  userId: number;
  email: string;
  roles: string[];
  iat: number;
  exp: number;
}

// Display-only decode of the JWT's claims - the token has already been
// verified by the gateway; the frontend never needs to (and can't) verify
// the signature itself.
export function decodeJwt(token: string): JwtClaims {
  const payload = token.split('.')[1];
  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  const json = atob(base64);
  return JSON.parse(json) as JwtClaims;
}
