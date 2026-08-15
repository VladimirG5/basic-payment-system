import { AxiosError } from 'axios';

interface ProblemDetail {
  title?: string;
  detail?: string;
}

const FALLBACK_MESSAGE = 'Something went wrong. Please try again.';

// Backend errors are RFC 7807 problem-detail bodies (see GlobalExceptionHandler
// in both Java services) - `detail` is always the human-readable part.
export function getErrorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const problem = error.response?.data as ProblemDetail | undefined;
    return problem?.detail ?? error.message ?? FALLBACK_MESSAGE;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return FALLBACK_MESSAGE;
}
