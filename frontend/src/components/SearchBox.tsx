import { useState, type FormEvent } from 'react';
import {
  MAX_SEARCH_LENGTH,
  MIN_SEARCH_LENGTH,
  normalizeSearch,
} from './filterParams';

type SearchBoxProps = {
  /** Search currently applied (from the URL); the field starts with it. */
  value: string | undefined;
  /** Called on submit with the trimmed text, or `undefined` for an empty field. */
  onSearch: (q: string | undefined) => void;
};

/** Search field applied on submit (Enter or button), never on each keystroke. */
export function SearchBox({ value, onSearch }: SearchBoxProps) {
  const [draft, setDraft] = useState(value ?? '');
  const [tooShort, setTooShort] = useState(false);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const text = draft.trim();
    if (text === '') {
      setTooShort(false);
      onSearch(undefined);
      return;
    }
    const q = normalizeSearch(text);
    setTooShort(q === undefined);
    if (q !== undefined) onSearch(q);
  };

  return (
    <form
      role="search"
      aria-label="Busca"
      className="mb-4 flex flex-wrap items-end gap-2"
      onSubmit={submit}
    >
      <label className="flex flex-col gap-1 text-sm">
        Buscar eventos
        <input
          type="search"
          className="w-64 rounded border border-gray-300 bg-white px-2 py-1"
          value={draft}
          maxLength={MAX_SEARCH_LENGTH}
          aria-invalid={tooShort}
          onChange={(event) => setDraft(event.target.value)}
        />
      </label>
      <button
        type="submit"
        className="rounded border border-gray-300 bg-white px-3 py-1"
      >
        Buscar
      </button>
      {tooShort && (
        <p role="alert" className="w-full text-sm text-red-700">
          Digite ao menos {MIN_SEARCH_LENGTH} caracteres
        </p>
      )}
    </form>
  );
}
