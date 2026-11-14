<script lang="ts">
  import { onDestroy, untrack } from 'svelte';
  import FichaFrame from '../components/FichaFrame.svelte';
  import FichaHeader from '../components/FichaHeader.svelte';
  import Icon from '../components/Icon.svelte';
  import ItemCard from '../components/ItemCard.svelte';
  import SectionContent from '../components/SectionContent.svelte';
  import Panels from '../components/Panels.svelte';
  import { isAbort, toLoadError, type LoadError } from '../lib/data/http';
  import { repository } from '../lib/data/repository';
  import { entryProgress, watchHistory } from '../lib/data/watchHistory.svelte';
  import { detailStructure, isCollection } from '../lib/domain/classification';
  import { episodeOrder, isFirstSeason, type EpisodeOrder } from '../lib/domain/episodeOrder';
  import type { CatalogItem, ProgramDetail } from '../lib/domain/models';
  import { openItem, play } from '../lib/ui/actions';
  import { recallItem } from '../lib/ui/nav.svelte';
  import { PagedLoader } from '../lib/ui/paged.svelte';
  import {
    contentSections,
    contentTab,
    programFacts,
    sectionOpensDetails,
    suggestPlay,
    suggestionLabel,
    type ProgramTab,
  } from '../lib/ui/sections';
  import { prefetchTabImages, whenIdle } from '../lib/ui/imagePrefetch';
  import { preloadPlayer } from '../lib/ui/playerPreload';
  import { errorText, htmlToParagraphs, programMetaLine } from '../lib/ui/text';

  /**
   * Ficha de programa (ProgramViewModel.kt + ProgramScreen.kt): la ficha primero
   * (decide temporadas, orden y extras) y después sus listas paginadas. El botón
   * grande reproduce, continúa o pasa al siguiente según el historial.
   */
  let { id, title: initialTitle, scroller }: { id: string; title: string; scroller: HTMLElement | undefined } = $props();

  // Desde una ficha lo normal es reproducir: el reproductor se va preparando.
  preloadPlayer();

  // Cada ficha se monta para un programa concreto: sus datos de partida no cambian.
  const known = untrack(() => recallItem(`program:${id}`));
  let title = $state(untrack(() => initialTitle || known?.title || ''));
  let detail = $state<ProgramDetail | null>(null);
  let detailError = $state<LoadError | null>(null);
  let selectedSeasonId = $state<string | null>(null);
  let order = $state<EpisodeOrder>('NEWEST_FIRST');
  let related = $state<CatalogItem[]>([]);
  /** Páginas de la lista completa ("Todos"): de ellas sale el botón grande, estable al mirar otra temporada. */
  let headerEpisodes = $state<CatalogItem[]>([]);
  let headerOrder = $state<EpisodeOrder>('NEWEST_FIRST');
  let controller: AbortController | null = null;

  const episodes = new PagedLoader(
    (page, signal) => repository.loadProgramVideos(id, { seasonId: selectedSeasonId, page, completeOnly: true, order, signal }).then((r) => r.value),
    () => {
      if (selectedSeasonId == null) {
        headerEpisodes = episodes.state.items;
        headerOrder = order;
      }
    },
  );
  const extras = new PagedLoader((page, signal) => repository.loadProgramExtras(id, page, signal).then((r) => r.value));
  episodes.state = { ...episodes.state, isLoading: true };

  const structure = $derived(detail ? detailStructure(detail) : 'EPISODIC');
  const sections = $derived(contentSections(structure, episodes.state, extras.state, true));
  const tabs = $derived<ProgramTab[]>([
    ...sections.map((s) => s.tab),
    // Un contenedor es un catálogo: sus piezas ya son "lo siguiente que ver".
    ...(!isCollection(structure) && related.length ? (['RELATED'] as const) : []),
  ]);
  let selectedTab = $state<ProgramTab | null>(null);
  const current = $derived(selectedTab && tabs.includes(selectedTab) ? selectedTab : tabs[0] ?? null);

  // Las miniaturas de las otras pestañas se piden antes de abrirlas: RTVE tarda en servir cada una la primera vez.
  $effect(() => {
    const others = [...sections.map((s) => [s.tab, s.items] as const), ['RELATED', related] as const].filter(
      ([tab, items]) => tab !== current && items.length > 0,
    );
    if (others.length === 0) return;
    return whenIdle(() => others.forEach(([tab, items]) => prefetchTabImages(tab, items)));
  });
  const activeSection = $derived(sections.find((s) => s.tab === current) ?? null);

  const suggestion = $derived(
    isCollection(structure)
      ? null
      : suggestPlay(watchHistory.entries, id, headerEpisodes, headerOrder, (it) => contentTab(it, structure) === 'EPISODES'),
  );

  const orderFor = (seasonId: string | null): EpisodeOrder =>
    detail ? episodeOrder(detail, isFirstSeason(detail, seasonId)) : 'NEWEST_FIRST';

  async function loadDetailThenEpisodes() {
    controller?.abort();
    episodes.cancel();
    const ctrl = new AbortController();
    controller = ctrl;
    detailError = null;
    episodes.state = { ...episodes.state, isLoading: true, error: null };
    try {
      const loaded = (await repository.loadProgram(id, { signal: ctrl.signal })).value;
      if (ctrl.signal.aborted) return;
      detail = loaded;
      title = loaded.title || title;
      if (loaded.showFragments) extras.load(true);
      if (!isCollection(detailStructure(loaded))) {
        repository.loadRelated('program', id, ctrl.signal).then(
          (r) => (related = r.value),
          () => {},
        );
      }
    } catch (e) {
      if (isAbort(e) || ctrl.signal.aborted) return;
      detailError = toLoadError(e);
    }
    order = orderFor(selectedSeasonId);
    episodes.load(true);
  }

  function selectSeason(seasonId: string | null) {
    if (seasonId === selectedSeasonId) return;
    selectedSeasonId = seasonId;
    order = orderFor(seasonId);
    episodes.reset(true);
    episodes.load(true);
  }

  function retry() {
    if (!detail) loadDetailThenEpisodes();
    else episodes.retry();
  }

  loadDetailThenEpisodes();
  onDestroy(() => {
    controller?.abort();
    episodes.cancel();
    extras.cancel();
  });

  const synopsis = $derived(htmlToParagraphs(detail?.description));
</script>

<FichaFrame
  {title}
  shareUrl={detail?.webUrl ?? null}
  {tabs}
  {current}
  onselect={(tab) => (selectedTab = tab)}
  {scroller}
>
  {#snippet header()}
    <FichaHeader
      backdropUrl={detail?.imageUrl ?? known?.imageUrl ?? null}
      {title}
      meta={detail ? programMetaLine(detail) : null}
      claim={detail?.emission}
      {synopsis}
      facts={detail && !isCollection(structure) ? programFacts(detail) : null}
      progress={suggestion?.type === 'continue' ? entryProgress(suggestion.entry) : null}
    >
      {#snippet actions()}
        {#if suggestion}
          <div class="play-actions">
            <button class="btn btn-primary big" onclick={() => play(suggestion.item)}>
              <Icon name="play" />
              <span>{suggestionLabel(suggestion)}</span>
            </button>
            {#if suggestion.type === 'continue'}
              <button class="icon-btn tonal" onclick={() => play(suggestion.item, true)} aria-label="Desde el inicio" title="Desde el inicio">
                <Icon name="replay" />
              </button>
            {/if}
          </div>
        {/if}
      {/snippet}
    </FichaHeader>
  {/snippet}

  {#if detailError}
    <div class="detail-error">
      <span>{errorText(detailError)}</span>
      <button class="btn btn-text" onclick={retry}>Reintentar</button>
    </div>
  {/if}

  {#if current === 'RELATED'}
    <div class="grid poster">
      {#each related as item (item.id)}
        <ItemCard {item} layout="POSTER" width={220} onclick={() => openItem(item)} />
      {/each}
    </div>
  {:else if activeSection}
    {#if activeSection.tab === 'EPISODES' && detail?.seasons.length}
      <div class="chips seasons" role="group" aria-label="Temporadas">
        <button class="chip" aria-pressed={selectedSeasonId == null} onclick={() => selectSeason(null)}>Todos</button>
        {#each detail.seasons as season (season.id)}
          <button class="chip" aria-pressed={selectedSeasonId === season.id} onclick={() => selectSeason(season.id)}>{season.title}</button>
        {/each}
      </div>
    {/if}
    <SectionContent
      section={activeSection}
      {suggestion}
      onselect={(item) => (sectionOpensDetails(activeSection, item, structure) ? openItem(item) : play(item))}
      onloadmore={() => {
        if (activeSection.loadsComplete) episodes.loadMore();
        if (activeSection.loadsExtras) extras.loadMore();
      }}
      onretry={() => {
        if (activeSection.loadsComplete) retry();
        if (activeSection.loadsExtras) extras.retry();
      }}
    />
  {:else if !detailError}
    <Panels kind="loading" compact />
  {/if}
</FichaFrame>

<style>
  .play-actions {
    display: flex;
    gap: 8px;
    align-items: center;
    width: 100%;
    max-width: 560px;
  }

  .big {
    flex: 1;
    min-width: 0;
    min-height: 48px;
    font-size: 1rem;
  }

  .big span {
    overflow: hidden;
    text-overflow: ellipsis;
  }

  @media (min-width: 900px) {
    .big {
      flex: 0 1 auto;
      padding: 0 28px 0 22px;
    }
  }

  .icon-btn.tonal {
    width: 48px;
    height: 48px;
  }

  .seasons {
    padding-top: 8px;
    padding-bottom: 12px;
  }

  .detail-error {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px var(--gutter);
    color: var(--muted);
  }
</style>
