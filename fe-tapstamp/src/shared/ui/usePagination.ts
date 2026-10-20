import { useState } from 'react';

/** Səhifə ölçüsü seçimləri (backend-dəki PageQuery.SIZES ilə eyni). */
export const PAGE_SIZES = [5, 10, 20, 50] as const;
export const DEFAULT_PAGE_SIZE = 10;

export interface PageState {
  page: number;
  size: number;
  setPage: (page: number) => void;
  setSize: (size: number) => void;
}

/**
 * Səhifə vəziyyəti (pagination — siyahını hissə-hissə yükləmək). {@code resetKey} dəyişəndə (məs. filtr) birinci
 * səhifəyə qayıdır.
 */
export function usePagination(resetKey?: unknown): PageState {
  const [page, setPage] = useState(0);
  const [size, setSizeState] = useState<number>(DEFAULT_PAGE_SIZE);
  const key = JSON.stringify(resetKey ?? null);
  const [lastKey, setLastKey] = useState(key);
  if (key !== lastKey) {
    setLastKey(key);
    setPage(0);
  }
  return {
    page,
    size,
    setPage,
    setSize: (value: number) => {
      setSizeState(value);
      setPage(0);
    },
  };
}
