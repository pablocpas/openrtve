import type { CatalogItem, CatalogPage } from '../domain/models';
import { isAbort, toLoadError } from '../data/http';
import { emptyPaged, pagedHasMore, type PagedItemsState } from './sections';

/**
 * Lista paginada de una ficha (PagedItems.kt): la primera página o la siguiente,
 * acumulando sin duplicados y dejando el fallo en la propia lista.
 */
export class PagedLoader {
  state = $state<PagedItemsState>(emptyPaged());
  #controller: AbortController | null = null;

  constructor(
    private readonly fetchPage: (page: number, signal: AbortSignal) => Promise<CatalogPage>,
    private readonly onLoaded: () => void = () => {},
  ) {}

  /** La siguiente se ignora si no la hay o ya se está pidiendo algo; la primera reemplaza lo anterior. */
  load(firstPage: boolean) {
    const current = this.state;
    if (!firstPage && (!pagedHasMore(current) || current.isLoading || current.isLoadingMore)) return;
    this.cancel();
    const controller = new AbortController();
    this.#controller = controller;
    this.state = { ...current, isLoading: firstPage, isLoadingMore: !firstPage, error: null };
    const pageNumber = firstPage ? 1 : current.page + 1;
    this.fetchPage(pageNumber, controller.signal).then(
      (result) => {
        if (controller.signal.aborted) return;
        const previous = firstPage ? [] : this.state.items;
        const seen = new Set<string>();
        const items = [...previous, ...result.items].filter((it: CatalogItem) => !seen.has(it.id) && !!seen.add(it.id));
        this.state = { items, page: result.page, totalPages: result.totalPages, isLoading: false, isLoadingMore: false, error: null };
        this.onLoaded();
      },
      (error) => {
        if (controller.signal.aborted || isAbort(error)) return;
        this.state = { ...this.state, isLoading: false, isLoadingMore: false, error: toLoadError(error) };
      },
    );
  }

  loadMore() {
    this.load(false);
  }

  /** Sin nada cargado se vuelve a pedir la primera página; si no, la que falló. */
  retry() {
    this.load(this.state.items.length === 0);
  }

  reset(isLoading = false) {
    this.cancel();
    this.state = emptyPaged(isLoading);
  }

  cancel() {
    this.#controller?.abort();
    this.#controller = null;
  }
}
