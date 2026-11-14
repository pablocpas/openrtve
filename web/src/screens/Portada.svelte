<script lang="ts">
  import { onDestroy } from 'svelte';
  import Carousel from '../components/Carousel.svelte';
  import ContinueWatching from '../components/ContinueWatching.svelte';
  import FeaturedCard from '../components/FeaturedCard.svelte';
  import HeroCarousel from '../components/HeroCarousel.svelte';
  import Icon from '../components/Icon.svelte';
  import ImageTile from '../components/ImageTile.svelte';
  import ItemCard from '../components/ItemCard.svelte';
  import Panels from '../components/Panels.svelte';
  import TopBar from '../components/TopBar.svelte';
  import { isAbort, toLoadError, type LoadError } from '../lib/data/http';
  import { repository } from '../lib/data/repository';
  import { watchHistory } from '../lib/data/watchHistory.svelte';
  import {
    SEE_ALL_MIN_ITEMS,
    isInlineRow,
    isLiveRow,
    isRenderableRow,
    type CatalogItem,
    type HomeRow,
    type RowLayout,
  } from '../lib/domain/models';
  import { openItem, openLink, openRow, play, removeFromHistory } from '../lib/ui/actions';
  import { nav } from '../lib/ui/nav.svelte';
  import { whenIdle } from '../lib/ui/idle';
  import { nearViewport } from '../lib/ui/nearViewport';
  import { errorText, portadaTitle } from '../lib/ui/text';

  /**
   * Una portada (`index_apps.json`): la principal o una temática (PortadaViewModel.kt).
   * Primero la copia local, aunque esté caducada: aparece al instante y la red solo
   * la revalida. Cada fila se carga por separado para que una lenta no bloquee al resto.
   * Primero las que se ven (la portada principal tiene más de 30 filas) y después, en
   * segundo plano, las demás: al bajar ya están pintadas.
   */
  let {
    url,
    title = '',
    isRoot = false,
    active,
    scroller,
  }: { url: string; title?: string; isRoot?: boolean; active: boolean; scroller: HTMLElement | undefined } = $props();

  type SectionState =
    | { type: 'loading' }
    | { type: 'loaded'; items: CatalogItem[]; isStale: boolean }
    | { type: 'links' }
    | { type: 'failed'; error: LoadError };

  interface Section {
    row: HomeRow;
    state: SectionState;
    title: string;
  }

  let feedTitle = $state('');
  let sections = $state<Section[]>([]);
  let isLoading = $state(true);
  let isFeedStale = $state(false);
  let error = $state<LoadError | null>(null);
  let lastLoadedAt = 0;

  const isStale = $derived(isFeedStale || sections.some((s) => s.state.type === 'loaded' && s.state.isStale));
  const heroFirst = $derived(sections[0]?.row.layout === 'HERO' && sections[0]?.state.type !== 'failed');
  const resumable = $derived(isRoot ? watchHistory.resumable : []);

  let controller: AbortController | null = null;
  const MAX_PARALLEL = 6;
  let running = 0;
  const queue: (() => void)[] = [];

  function withPermit<T>(task: () => Promise<T>): Promise<T> {
    return new Promise((resolve, reject) => {
      const start = () => {
        running++;
        task().then(resolve, reject).finally(() => {
          running--;
          queue.shift()?.();
        });
      };
      if (running < MAX_PARALLEL) start();
      else queue.push(start);
    });
  }

  /** Filas que ya se han acercado a la pantalla (por su fuente): se cargan y se revalidan. */
  const activated = new Set<string>();

  function activate(row: HomeRow) {
    if (isInlineRow(row) || !row.contentUrl || activated.has(row.contentUrl)) return;
    activated.add(row.contentUrl);
    if (controller && !controller.signal.aborted) void loadSection(row, false, controller.signal);
  }

  /**
   * Las filas que aún no se han acercado a la pantalla se cargan después, de una en
   * una y cuando el navegador queda libre. Cargarlas justo al llegar a ellas cambiaba la
   * página mientras se deslizaba y en el móvil cortaba la inercia del scroll. Empiezan
   * cuando ya han llegado las visibles y la página ha terminado de cargar, para no
   * quitarle red a la imagen principal.
   */
  let backgroundRun = 0;
  let lastScrollAt = 0;
  function loadRestInBackground(signal: AbortSignal, first: Promise<unknown>[]) {
    const run = ++backgroundRun;
    const next = () => {
      if (signal.aborted || run !== backgroundRun) return;
      // Nunca a mitad de un deslizamiento: se espera a que el scroll se quede quieto.
      if (performance.now() - lastScrollAt < 400) {
        setTimeout(() => whenIdle(next), 400);
        return;
      }
      const row = sections.map((s) => s.row).find((r) => !isInlineRow(r) && r.contentUrl && !activated.has(r.contentUrl));
      if (!row?.contentUrl) return;
      activated.add(row.contentUrl);
      void loadSection(row, false, signal).finally(() => whenIdle(next));
    };
    const pageLoaded = new Promise<void>((resolve) =>
      document.readyState === 'complete' ? resolve() : window.addEventListener('load', () => resolve(), { once: true }),
    );
    void Promise.allSettled([...first, pageLoaded]).then(() => setTimeout(() => whenIdle(next, 4000), 1000));
  }

  const isActivated = (row: HomeRow) => !isInlineRow(row) && !!row.contentUrl && activated.has(row.contentUrl);

  const initialSection = (row: HomeRow): Section => ({
    row,
    state: isInlineRow(row) ? { type: 'links' } : { type: 'loading' },
    title: row.title,
  });

  function updateSection(rowId: number, state: SectionState, moduleTitle?: string) {
    sections = sections.map((s) => (s.row.id === rowId ? { ...s, state, title: moduleTitle?.trim() || s.title } : s));
  }

  async function loadSection(row: HomeRow, forceRefresh: boolean, signal: AbortSignal) {
    let state: SectionState;
    let moduleTitle: string | undefined;
    // Una fila aún sin contenido enseña antes su copia local, si la hay, y luego se revalida.
    if (!forceRefresh && sections.find((s) => s.row.id === row.id)?.state.type === 'loading') {
      try {
        const cached = await repository.loadModule(row, { cachedOnly: true, signal });
        if (signal.aborted) return;
        sections = sections.map((s) =>
          s.row.id === row.id && s.state.type === 'loading'
            ? { ...s, state: { type: 'loaded', items: cached.value.items, isStale: false }, title: cached.value.title.trim() || s.title }
            : s,
        );
      } catch {
        // Sin copia de esta fila: la red la traerá.
      }
    }
    try {
      const module = await withPermit(() => repository.loadModule(row, { forceRefresh, signal }));
      moduleTitle = module.value.title;
      state = { type: 'loaded', items: module.value.items, isStale: module.isStale };
    } catch (e) {
      if (isAbort(e) || signal.aborted) return;
      state = { type: 'failed', error: toLoadError(e) };
    }
    if (signal.aborted) return;
    // Una sección vacía desaparece en vez de mostrar un hueco.
    if (state.type === 'loaded' && state.items.length === 0) sections = sections.filter((s) => s.row.id !== row.id);
    else updateSection(row.id, state, moduleTitle);
  }

  async function load(forceRefresh: boolean) {
    controller?.abort();
    const ctrl = new AbortController();
    controller = ctrl;
    const signal = ctrl.signal;
    // La red y la copia local a la vez: la petición no espera a IndexedDB.
    const network = repository.loadPortada(url, { forceRefresh, signal });
    network.catch(() => {});

    if (sections.length === 0) {
      try {
        const cached = await repository.loadPortada(url, { cachedOnly: true, signal });
        feedTitle = cached.value.title.trim();
        sections = cached.value.rows.filter(isRenderableRow).map(initialSection);
        isLoading = false;
      } catch {
        // Sin copia (NotCachedError), sin IndexedDB o copia ilegible: se sigue con la red.
      }
    }
    if (signal.aborted) return;

    const hadSections = sections.length > 0;
    isLoading = !hadSections;
    error = null;
    let feed;
    try {
      feed = await network;
    } catch (e) {
      if (isAbort(e) || signal.aborted) return;
      isLoading = false;
      // Con copia local mostrada, un fallo de red solo la marca como antigua.
      isFeedStale = hadSections;
      error = hadSections ? null : toLoadError(e);
      const first = sections.filter((s) => s.state.type === 'loading' && isActivated(s.row)).map((s) => loadSection(s.row, false, signal));
      loadRestInBackground(signal, first);
      return;
    }
    if (signal.aborted) return;
    const rows = feed.value.rows.filter(isRenderableRow);
    lastLoadedAt = Date.now();
    // Las filas remotas que ya estaban conservan su contenido mientras se revalidan.
    const previous = new Map(sections.filter((s) => !isInlineRow(s.row)).map((s) => [s.row.contentUrl, s]));
    feedTitle = feed.value.title.trim();
    sections = rows.map((row) => {
      const old = previous.get(row.contentUrl);
      return old ? { ...old, row } : initialSection(row);
    });
    isLoading = false;
    isFeedStale = feed.isStale;
    const first = rows.filter(isActivated).map((row) => loadSection(row, forceRefresh, signal));
    loadRestInBackground(signal, first);
  }

  function retrySection(row: HomeRow) {
    if (!controller) return;
    updateSection(row.id, { type: 'loading' });
    void loadSection(row, true, controller.signal);
  }

  /** Las filas de directos cambian cada pocos minutos: se recargan solas mientras se ven. */
  function refreshLiveSections() {
    if (!controller) return;
    for (const s of sections) if (isLiveRow(s.row) && s.state.type !== 'loading') void loadSection(s.row, true, controller.signal);
  }

  load(false);
  onDestroy(() => controller?.abort());

  $effect(() => {
    if (!active) return;
    // Al volver a la pantalla o a la pestaña: si la portada tiene más de 2 min, se revalida.
    if (lastLoadedAt && Date.now() - lastLoadedAt > 120_000 && !isLoading) load(false);
    const onVisible = () => {
      if (document.visibilityState === 'visible' && Date.now() - lastLoadedAt > 120_000 && !isLoading) load(false);
    };
    document.addEventListener('visibilitychange', onVisible);
    const timer = setInterval(() => document.visibilityState === 'visible' && refreshLiveSections(), 60_000);
    return () => {
      document.removeEventListener('visibilitychange', onVisible);
      clearInterval(timer);
    };
  });

  // Volver a pulsar Inicio sube al principio.
  let handledReselect = nav.reselect.home;
  $effect(() => {
    const request = nav.reselect.home;
    if (isRoot && request !== handledReselect) {
      handledReselect = request;
      scroller?.scrollTo({ top: 0, behavior: 'smooth' });
    }
  });

  let scrolled = $state(false);
  $effect(() => {
    if (!scroller) return;
    const el = scroller;
    const onScroll = () => {
      lastScrollAt = performance.now();
      if (isRoot) scrolled = el.scrollTop > 120;
    };
    el.addEventListener('scroll', onScroll, { passive: true });
    return () => el.removeEventListener('scroll', onScroll);
  });

  const cardWidth: Record<RowLayout, number> = { POSTER: 200, POSTER_TALL: 200, SQUARE: 220, LANDSCAPE: 340, RANKED: 340, HERO: 1280, FEATURED: 900 };
  const cardClass = (layout: RowLayout) =>
    layout === 'POSTER' || layout === 'POSTER_TALL' ? 'poster' : layout === 'SQUARE' ? 'square' : 'landscape';
  const shownTitle = $derived(portadaTitle(title, feedTitle));
  const resumeItem = (item: CatalogItem) => play(item);
</script>

{#if !isRoot}
  <TopBar title={shownTitle}>
    {#snippet actions()}
      <button class="icon-btn" onclick={() => load(true)} aria-label="Actualizar"><Icon name="replay" /></button>
    {/snippet}
  </TopBar>
{:else}
  <header class="brandbar" class:hidden={scrolled && sections.length > 0}>
    <span class="brand">OpenRTVE</span>
  </header>
{/if}

<div class="screen-body portada" class:root={isRoot} class:no-hero={!heroFirst}>
  {#if isLoading}
    <div class="loading-hero skeleton" class:full={isRoot}></div>
    {#each [0, 1] as i (i)}
      <div class="placeholder-row">
        <div class="skeleton bar"></div>
        <div class="cards">{#each [0, 1, 2, 3, 4] as j (j)}<div class="skeleton card"></div>{/each}</div>
      </div>
    {/each}
  {:else if sections.length === 0}
    <Panels kind="message" message={error ? errorText(error) : 'La portada no tiene módulos públicos ahora mismo'} onretry={() => load(true)} />
  {:else}
    {#if isStale}<p class="stale-banner">Mostrando la última copia disponible</p>{/if}
    {#if resumable.length && !heroFirst}
      <ContinueWatching entries={resumable} onresume={resumeItem} ondetails={openItem} onremove={removeFromHistory} />
    {/if}
    {#each sections as section, index (section.row.id)}
      {@const layout = section.row.layout}
      {@const state = section.state}
      <section
        class="section"
        class:hero-section={layout === 'HERO'}
        use:nearViewport={{ root: scroller, key: section.row.contentUrl, onnear: () => activate(section.row) }}
      >
        {#if layout !== 'HERO' && section.title.trim()}
          <div class="section-head">
            {#if state.type === 'loaded' && state.items.length >= SEE_ALL_MIN_ITEMS}
              <button class="head-link" onclick={() => openRow(section.row, section.title)}>
                <h2 class="section-title">{section.title}</h2>
                <Icon name="chevronRight" />
              </button>
            {:else}
              <h2 class="section-title">{section.title}</h2>
            {/if}
          </div>
        {/if}

        {#if state.type === 'loading'}
          {#if layout === 'HERO' || layout === 'FEATURED'}
            <div class="loading-hero skeleton" class:full={isRoot && index === 0}></div>
          {:else}
            <div class="cards {cardClass(layout)}">{#each [0, 1, 2, 3, 4, 5] as j (j)}<div class="skeleton card"></div>{/each}</div>
          {/if}
        {:else if state.type === 'links'}
          <Carousel label={section.title}>
            {#each section.row.links as link (link.url)}
              <div class="w-landscape"><ImageTile title={link.title} imageUrl={link.imageUrl} onclick={() => openLink(link)} /></div>
            {/each}
          </Carousel>
        {:else if state.type === 'failed'}
          <div class="failed">
            <span>{errorText(state.error)}</span>
            <button class="btn btn-text" onclick={() => retrySection(section.row)}>Reintentar</button>
          </div>
        {:else if layout === 'HERO'}
          <HeroCarousel items={state.items} onopen={openItem} fullBleed={isRoot && index === 0} />
        {:else if layout === 'FEATURED'}
          {#if state.items[0]}<FeaturedCard item={state.items[0]} onopen={() => openItem(state.items[0])} />{/if}
        {:else}
          <Carousel label={section.title}>
            {#each state.items as item, i (item.id)}
              <div class="w-{cardClass(layout)}">
                <ItemCard {item} {layout} width={cardWidth[layout]} rank={layout === 'RANKED' ? i + 1 : null} onclick={() => openItem(item)} />
              </div>
            {/each}
          </Carousel>
        {/if}
      </section>
      <!-- Tras el hero, como en la app oficial. -->
      {#if index === 0 && heroFirst && resumable.length}
        <ContinueWatching entries={resumable} onresume={resumeItem} ondetails={openItem} onremove={removeFromHistory} />
      {/if}
    {/each}
  {/if}
</div>

<style>
  .portada {
    display: flex;
    flex-direction: column;
    gap: var(--section-gap);
    padding-top: 8px;
  }

  .portada.root {
    padding-top: 0;
  }

  .portada.root.no-hero {
    padding-top: 64px;
  }

  .section-head {
    padding: 0 var(--gutter) 10px;
    display: flex;
    align-items: center;
  }

  .head-link {
    min-height: 40px;
    display: inline-flex;
    align-items: center;
    gap: 2px;
    border-radius: 6px;
  }

  .head-link :global(svg) {
    transition: transform 0.15s;
    color: var(--muted);
  }

  .head-link:hover :global(svg) {
    transform: translateX(3px);
    color: var(--text);
  }

  .w-landscape {
    width: var(--landscape-w);
  }

  .w-poster {
    width: var(--poster-w);
  }

  .w-square {
    width: var(--square-w);
  }

  .failed {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 var(--gutter);
    color: var(--muted);
    font-size: 0.92rem;
  }

  .loading-hero {
    margin: 0 var(--gutter);
    height: min(56.25vw, 74vh);
    border-radius: var(--radius-lg);
  }

  .loading-hero.full {
    margin: 0;
    border-radius: 0;
  }

  .placeholder-row .bar {
    width: 180px;
    height: 20px;
    border-radius: 6px;
    margin: 0 var(--gutter) 12px;
  }

  .cards {
    display: flex;
    gap: var(--row-gap);
    padding: 4px var(--gutter);
    overflow: hidden;
  }

  .cards .card {
    flex: none;
    width: var(--landscape-w);
    aspect-ratio: 16 / 9;
    border-radius: var(--radius);
  }

  .cards.poster .card {
    width: var(--poster-w);
    aspect-ratio: 2 / 3;
  }

  .cards.square .card {
    width: var(--square-w);
    aspect-ratio: 1;
  }

  .brandbar {
    /* Flota sobre el hero sin ocupar sitio: pegada arriba y con margen negativo. */
    position: sticky;
    top: 0;
    margin-bottom: -64px;
    z-index: 10;
    display: flex;
    align-items: center;
    height: 64px;
    padding: 0 var(--gutter);
    background: linear-gradient(to bottom, rgb(0 0 0 / 0.65), transparent);
    pointer-events: none;
    transition: opacity 0.25s;
  }

  .brandbar.hidden {
    opacity: 0;
  }

  .brand {
    font-size: 1.35rem;
    font-weight: 800;
    color: white;
    letter-spacing: -0.01em;
  }

  @media (min-width: 840px) {
    .brandbar {
      display: none;
    }

    .portada.root.no-hero {
      padding-top: 24px;
    }
  }

</style>
