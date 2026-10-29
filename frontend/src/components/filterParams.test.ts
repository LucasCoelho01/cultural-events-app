import { describe, expect, it } from 'vitest';
import {
  clearFilters,
  type CategoriesState,
  hasFilters,
  parseFilters,
  resolveCategory,
  withFilters,
} from './filterParams';

const parse = (query: string) => parseFilters(new URLSearchParams(query));

describe('parseFilters', () => {
  it('reads every filter', () => {
    expect(parse('categoria=shows&periodo=hoje')).toEqual({
      categoria: 'shows',
      periodo: 'hoje',
    });
    expect(parse('de=2099-03-01&ate=2099-03-03')).toEqual({
      categoria: undefined,
      de: '2099-03-01',
      ate: '2099-03-03',
    });
  });

  it('drops an unknown period and invalid dates', () => {
    expect(parse('periodo=amanha')).toEqual({});
    expect(parse('de=2099-02-31&ate=abc')).toEqual({});
    expect(parse('de=2099-3-1')).toEqual({});
  });

  it('lets `periodo` win over `de`/`ate`', () => {
    expect(parse('periodo=7-dias&de=2099-03-01&ate=2099-03-03')).toEqual({
      categoria: undefined,
      periodo: '7-dias',
    });
  });

  it('keeps the dates when the period is unknown', () => {
    expect(parse('periodo=amanha&de=2099-03-01').de).toBe('2099-03-01');
  });
});

describe('hasFilters', () => {
  it('is true only when a valid filter is present', () => {
    expect(hasFilters(parse('page=2'))).toBe(false);
    expect(hasFilters(parse('periodo=amanha'))).toBe(false);
    expect(hasFilters(parse('categoria=shows'))).toBe(true);
  });
});

describe('withFilters', () => {
  it('drops `page` when a filter changes', () => {
    const next = withFilters(new URLSearchParams('page=3'), {
      categoria: 'teatro',
    });
    expect(next.toString()).toBe('categoria=teatro');
  });

  it('removes a filter set to undefined and keeps the others', () => {
    const next = withFilters(
      new URLSearchParams('categoria=shows&periodo=hoje'),
      {
        categoria: undefined,
      },
    );
    expect(next.toString()).toBe('periodo=hoje');
  });

  it('replaces a custom range with a shortcut and vice versa', () => {
    expect(
      withFilters(new URLSearchParams('de=2099-03-01&ate=2099-03-03'), {
        periodo: 'hoje',
      }).toString(),
    ).toBe('periodo=hoje');
    expect(
      withFilters(new URLSearchParams('periodo=hoje'), {
        de: '2099-03-01',
      }).toString(),
    ).toBe('de=2099-03-01');
  });
});

describe('clearFilters', () => {
  it('removes every filter and the page', () => {
    const next = clearFilters(
      new URLSearchParams('categoria=shows&periodo=hoje&de=2099-03-01&page=2'),
    );
    expect(next.toString()).toBe('');
  });
});

describe('resolveCategory', () => {
  const loaded: CategoriesState = {
    status: 'success',
    slugs: ['shows', 'rock'],
  };

  it('needs no categories without a slug', () => {
    expect(resolveCategory(undefined, { status: 'pending' })).toEqual({
      ready: true,
    });
  });

  it('waits while categories load', () => {
    expect(resolveCategory('shows', { status: 'pending' })).toEqual({
      ready: false,
    });
  });

  it('keeps a slug that exists', () => {
    expect(resolveCategory('rock', loaded)).toEqual({
      category: 'rock',
      ready: true,
    });
  });

  it('ignores an unknown slug', () => {
    expect(resolveCategory('inexistente', loaded)).toEqual({ ready: true });
  });

  it('ignores the category when loading fails', () => {
    expect(resolveCategory('shows', { status: 'error' })).toEqual({
      ready: true,
    });
  });
});
