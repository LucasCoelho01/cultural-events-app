import { describe, expect, it } from 'vitest';
import { formatReais, parseReais } from './money';

describe('parseReais', () => {
  it.each([
    ['25', 2500],
    ['25,5', 2550],
    ['25,50', 2550],
    ['25.50', 2550],
    ['0', 0],
    ['0,05', 5],
    ['  80  ', 8000],
    ['1234,56', 123456],
  ])('parses %j as %d cents', (text, cents) => {
    expect(parseReais(text)).toBe(cents);
  });

  it('does not suffer float rounding (19,99 and 0,29)', () => {
    expect(parseReais('19,99')).toBe(1999);
    expect(parseReais('0,29')).toBe(29);
  });

  it('returns null for empty text', () => {
    expect(parseReais('')).toBeNull();
    expect(parseReais('   ')).toBeNull();
  });

  it.each(['abc', '10,999', '-5', '1,2,3', '1.234,56', 'R$ 10', ',50'])(
    'flags %j as invalid',
    (text) => {
      expect(parseReais(text)).toBe('invalid');
    },
  );
});

describe('formatReais', () => {
  it('formats cents for the form', () => {
    expect(formatReais(2550)).toBe('25,50');
    expect(formatReais(8000)).toBe('80,00');
    expect(formatReais(5)).toBe('0,05');
    expect(formatReais(0)).toBe('0,00');
  });

  it('returns empty text without a value', () => {
    expect(formatReais(null)).toBe('');
    expect(formatReais(undefined)).toBe('');
  });

  it('round-trips with parseReais', () => {
    for (const cents of [0, 5, 99, 100, 2550, 123456]) {
      expect(parseReais(formatReais(cents))).toBe(cents);
    }
  });
});
