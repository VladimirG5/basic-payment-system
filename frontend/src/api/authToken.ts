// In-memory only, deliberately not localStorage - keeps the token out of
// persistent storage (XSS-exfiltration surface) at the cost of losing the
// session on a page refresh, which is an acceptable trade-off for this scope.
// The auth context (added when the login screen is built) is the sole writer.
let token: string | null = null;

export function getAuthToken(): string | null {
  return token;
}

export function setAuthToken(newToken: string | null): void {
  token = newToken;
}
