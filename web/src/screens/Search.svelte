<script lang="ts">
  import { onDestroy, tick, untrack } from 'svelte';
  import Carousel from '../components/Carousel.svelte';
  import EpisodeRow from '../components/EpisodeRow.svelte';
  import Icon from '../components/Icon.svelte';
  import ImageTile from '../components/ImageTile.svelte';
  import ItemCard from '../components/ItemCard.svelte';
  import ItemGrid from '../components/ItemGrid.svelte';
  import Panels from '../components/Panels.svelte';
  import { isAbort, toLoadError, type LoadError } from '../lib/data/http';
  import { repository } from '../lib/data/repository';
  import { mediaForm } from '../lib/domain/classification';
  import { parseDeepLink } from '../lib/domain/deepLink';
  import { hasMoreVideos, type CatalogItem, type ExploreCategory, type QuickFilter, type SearchResults } from '../lib/domain/models';
  import { openCategory, openIncomingLink, openItem } from '../lib/ui/actions';
  import { nav } from '../lib/ui/nav.svelte';
  import { errorText } from '../lib/ui/text';

  /**
   * Buscador al estilo de la app oficial (SearchViewModel.kt): campo de texto y,
   * mientras no se escribe, las búsquedas predefinidas. Pegar un enlace de RTVE
   * ofrece abrirlo directamente.
   */
  let { initialQuery = '', active }: { initialQuery?: string; active: boolean } = $props();

  const MIN_QUERY_LENGTH = 3;
  const DEBOUNCE_MS = 400;

  let query = $state(untrack(() => initialQuery));
  let filters = $state<QuickFilter[]>([]);
  let selectedFilter = $state<QuickFilter | null>(null);
  /** Si RTVE no diera búsquedas predefinidas, el buscador ofrece las temáticas del menú. */
  let topics = $state<ExploreCategory[]>([]);
  let filterState = $state<{ type: 'loading' } | { type: 'loaded'; items: CatalogItem[] } | { type: 'failed'; error: LoadError }>({ type: 'loading' });
  let results = $state<SearchResults | null>(null);
  let isSearching = $state(false);
  let isLoadingMore = $state(false);
  let searchError = $state<LoadError | null>(null);
  let attempt = $state(0);
  let input = $state<HTMLInputElement>();

  const trimmed = $derived(query.trim());
  const link = $derived(parseDeepLink(trimmed) ? trimmed : null);
  const isTextSearch = $derived(!link && trimmed.length >= MIN_QUERY_LENGTH);

  let filterController: AbortController | null = null;

  async function loadFilters() {
    filterController?.abort();
    const ctrl = new AbortController();
    filterController = ctrl;
    filterState = { type: 'loading' };
    try {
      filters = (await repository.loadQuickFilters(ctrl.signal)).value;
      if (filters[0]) {
        selectFilter(filters[0]);
      } else {
        const groups = (await repository.loadExplore({ signal: ctrl.signal })).value;
        topics = groups.find((g) => g.title == null && !g.isKids)?.categories.filter((c) => !c.isLive) ?? [];
        filterState = { type: 'loaded', items: [] };
      }
    } catch (e) {
      if (isAbort(e) || ctrl.signal.aborted) return;
      filterState = { type: 'failed', error: toLoadError(e) };
    }
  }

  async function selectFilter(filter: QuickFilter, force = false) {
    if (!force && filter === selectedFilter && filterState.type === 'loaded') return;
    filterController?.abort();
    const ctrl = new AbortController();
    filterController = ctrl;
    selectedFilter = filter;
    filterState = { type: 'loading' };
    try {
      const items = (await repository.loadQuickFilterItems(filter, ctrl.signal)).value.items;
      if (!ctrl.signal.aborted) filterState = { type: 'loaded', items };
    } catch (e) {
      if (isAbort(e) || ctrl.signal.aborted) return;
      filterState = { type: 'failed', error: toLoadError(e) };
    }
  }

  const retryFilter = () => (selectedFilter ? selectFilter(selectedFilter, true) : loadFilters());

  // Búsqueda con espera: se cancela la anterior antes de esperar, para no publicarla tarde.
  $effect(() => {
    const q = trimmed;
    void attempt;
    if (link || q.length < MIN_QUERY_LENGTH) {
      results = null;
      isSearching = false;
      isLoadingMore = false;
      searchError = null;
      return;
    }
    const ctrl = new AbortController();
    results = null;
    isSearching = true;
    searchError = null;
    isLoadingMore = false;
    const timer = setTimeout(async () => {
      try {
        const found = await repository.search(q, ctrl.signal);
        if (!ctrl.signal.aborted) results = found;
      } catch (e) {
        if (isAbort(e) || ctrl.signal.aborted) return;
        searchError = toLoadError(e);
      } finally {
        if (!ctrl.signal.aborted) isSearching = false;
      }
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      ctrl.abort();
    };
  });

  let moreController: AbortController | null = null;

  async function loadMoreVideos() {
    const current = results;
    if (!current?.videosQuery || !hasMoreVideos(current) || isLoadingMore || isSearching) return;
    const relative = current.videosQuery;
    moreController?.abort();
    const ctrl = new AbortController();
    moreController = ctrl;
    isLoadingMore = true;
    try {
      const page = await repository.searchPage(relative, current.videosPage + 1, ctrl.signal);
      // Solo si la consulta no cambió mientras tanto.
      if (results?.videosQuery === relative) {
        const seen = new Set(results.videos.map((v) => v.id));
        results = {
          ...results,
          videos: [...results.videos, ...page.items.filter((v) => !seen.has(v.id))],
          videosPage: page.page,
          videosTotalPages: page.totalPages,
        };
      }
    } catch {
      // La lista sigue ahí; el pie vuelve a ofrecer "Cargar más".
    } finally {
      if (!ctrl.signal.aborted) isLoadingMore = false;
    }
  }

  let sentinel = $state<HTMLElement>();
  $effect(() => {
    if (!sentinel || !results || !hasMoreVideos(results) || isLoadingMore) return;
    const observer = new IntersectionObserver((e) => e.some((x) => x.isIntersecting) && loadMoreVideos(), {
      root: sentinel.closest('.screen'),
      rootMargin: '600px 0px',
    });
    observer.observe(sentinel);
    return () => observer.disconnect();
  });

  // La búsqueda queda en la URL (sin crear entradas): recargar o compartir la conserva.
  $effect(() => {
    const q = trimmed;
    if (!active) return;
    const timer = setTimeout(() => {
      const target = `#/buscar${q ? `?q=${encodeURIComponent(q)}` : ''}`;
      if (location.hash !== target) history.replaceState(history.state, '', target);
    }, 600);
    return () => clearTimeout(timer);
  });

  // Volver a pulsar Buscar enfoca el campo.
  let handledReselect = nav.reselect.search;
  $effect(() => {
    const request = nav.reselect.search;
    if (request !== handledReselect) {
      handledReselect = request;
      input?.focus();
      input?.select();
    }
  });

  loadFilters();
  tick().then(() => {
    if (window.matchMedia('(pointer: fine)').matches) input?.focus();
  });
  onDestroy(() => {
    filterController?.abort();
    moreController?.abort();
  });
</script>

<div class="search-head">
  <h1 class="screen-title">Buscar</h1>
  <form class="field" role="search" onsubmit={(e) => { e.preventDefault(); input?.blur(); }}>
    <Icon name="search" />
    <input
      bind:this={input}
      bind:value={query}
      type="search"
      placeholder="Programas, series, películas…"
      aria-label="Buscar en RTVE Play"
      autocomplete="off"
      spellcheck="false"
      enterkeyhint="search"
    />
    {#if query}
      <button type="button" class="icon-btn" onclick={() => { query = ''; input?.focus(); }} aria-label="Borrar"><Icon name="close" /></button>
    {/if}
  </form>
  {#if isSearching}<div class="bar-loading" role="progressbar" aria-label="Buscando"></div>{/if}
</div>

<div class="screen-body">
  {#if link}
    <div class="link-offer">
      <Icon name="link" />
      <span>Es un enlace de RTVE.</span>
      <button class="btn btn-primary" onclick={() => openIncomingLink(link)}>Abrir enlace</button>
    </div>
  {:else if isTextSearch}
    {#if searchError}
      <Panels kind="message" message={errorText(searchError)} onretry={() => attempt++} />
    {:else if results && results.programs.length === 0 && results.videos.length === 0}
      <Panels kind="message" message="No hay resultados" />
    {:else if results}
      {#if results.programs.length}
        <h2 class="section-title block-title">Programas</h2>
        <Carousel label="Programas">
          {#each results.programs as item (item.id)}
            <div class="w-poster"><ItemCard {item} layout="POSTER" width={200} onclick={() => openItem(item)} /></div>
          {/each}
        </Carousel>
      {/if}
      {#if results.videos.length}
        <h2 class="section-title block-title">Vídeos</h2>
        <div class="videos">
          {#each results.videos as item (item.id)}
            <EpisodeRow {item} onclick={() => openItem(item)} badge={mediaForm(item) === 'EXTRA' ? item.mediaTypeName : item.mediaSubtypeName} />
          {/each}
        </div>
        {#if isLoadingMore}
          <Panels kind="loading" compact />
        {:else if hasMoreVideos(results)}
          <div class="more" bind:this={sentinel}><button class="btn btn-text" onclick={loadMoreVideos}>Cargar más</button></div>
        {/if}
      {/if}
    {/if}
  {:else}
    {#if filters.length}
      <div class="chips" role="group" aria-label="Búsquedas predefinidas">
        {#each filters as filter (filter.contentUrl)}
          <button class="chip" aria-pressed={filter === selectedFilter} onclick={() => selectFilter(filter)}>{filter.title}</button>
        {/each}
      </div>
    {/if}
    {#if filterState.type === 'loading'}
      {#if filters.length}<Panels kind="loading" />{/if}
    {:else if filterState.type === 'failed'}
      <Panels kind="message" message={errorText(filterState.error)} onretry={retryFilter} />
    {:else if topics.length}
      <h2 class="section-title block-title">Explora por temática</h2>
      <div class="grid landscape">
        {#each topics as category (category.contentUrl)}
          <ImageTile title={category.title} imageUrl={category.imageUrl} fallback={category.fallbackImageUrl} onclick={() => openCategory(category)} />
        {/each}
      </div>
    {:else if filterState.items.length === 0}
      <Panels kind="message" message="Este módulo no tiene contenidos ahora mismo" onretry={retryFilter} />
    {:else}
      <ItemGrid items={filterState.items} layout="POSTER" onopen={openItem} />
    {/if}
  {/if}
</div>

<style>
  .search-head {
    position: sticky;
    top: 0;
    z-index: 10;
    background: rgb(9 10 15 / 0.97);
    padding-bottom: 12px;
  }

  .field {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 4px var(--gutter) 0;
    max-width: 760px;
    height: 54px;
    padding: 0 6px 0 16px;
    border-radius: 28px;
    background: var(--surface-2);
    color: var(--muted);
    border: 1px solid transparent;
    transition: border-color 0.15s;
  }

  .field:focus-within {
    border-color: var(--accent);
  }

  input {
    flex: 1;
    min-width: 0;
    height: 100%;
    border: 0;
    outline: none;
    background: none;
    color: var(--text);
    font: inherit;
    font-size: 1.05rem;
  }

  input::-webkit-search-cancel-button {
    display: none;
  }

  .bar-loading {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 3px;
    background: linear-gradient(90deg, transparent, var(--accent), transparent);
    background-size: 40% 100%;
    background-repeat: no-repeat;
    animation: slide 1.1s ease-in-out infinite;
  }

  @keyframes slide {
    from {
      background-position: -40% 0;
    }
    to {
      background-position: 140% 0;
    }
  }

  .block-title {
    padding: 16px var(--gutter) 10px;
  }

  .w-poster {
    width: var(--poster-w);
  }

  .videos {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 460px), 1fr));
    gap: 4px 16px;
    padding: 0 calc(var(--gutter) - 8px);
  }

  .more {
    display: flex;
    justify-content: center;
    padding: 12px;
  }

  .link-offer {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    margin: 16px var(--gutter);
    padding: 16px;
    border-radius: var(--radius-lg);
    background: var(--surface-2);
    color: var(--muted);
  }

  .link-offer span {
    flex: 1;
  }
</style>
