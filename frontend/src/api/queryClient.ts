import { QueryClient } from '@tanstack/react-query';
import { isProblem } from './problem';

const MAX_RETRIES = 2;

// Retry only network failures and 5xx; a Problem with 4xx will not change on retry.
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_RETRIES) return false;
  if (isProblem(error) && error.status >= 400 && error.status < 500) {
    return false;
  }
  return true;
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: shouldRetry } },
  });
}
