import type { components } from './schema';

export type Problem = components['schemas']['Problem'];

export function isProblem(value: unknown): value is Problem {
  return (
    typeof value === 'object' &&
    value !== null &&
    'title' in value &&
    'status' in value
  );
}
