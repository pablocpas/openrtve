<script lang="ts">
  import { onDestroy, untrack } from 'svelte';
  import FichaFrame from '../components/FichaFrame.svelte';
  import FichaHeader from '../components/FichaHeader.svelte';
  import Icon from '../components/Icon.svelte';
  import ItemCard from '../components/ItemCard.svelte';
  import Panels from '../components/Panels.svelte';
  import SectionContent from '../components/SectionContent.svelte';
  import { isAbort, toLoadError, type LoadError } from '../lib/data/http';
  import { repository } from '../lib/data/repository';
  import { entryProgress, isInProgress, watchHistory } from '../lib/data/watchHistory.svelte';
  import { belongsToEpisodicProgram, mediaForm, mediaSubject, parentStructure } from '../lib/domain/classification';
  import { episodeOrder, isFirstSeason, type EpisodeOrder } from '../lib/domain/episodeOrder';
  import { referenceItem, type CatalogItem, type RelatedKind, type VideoDetail } from '../lib/domain/models';
  import { openItem, openProgram, play } from '../lib/ui/actions';
  import { recallItem } from '../lib/ui/nav.svelte';
  import { PagedLoader } from '../lib/ui/paged.svelte';
  import { contentSections, sectionOpensDetails, videoFacts, type ProgramTab } from '../lib/ui/sections';
  import { collectionLinkText, errorText, htmlToParagraphs, videoMetaLine } from '../lib/ui/text';

  /**
   * Ficha de vídeo o película (VideoViewModel.kt + VideoScreen.kt). Debajo de la
   * cabecera solo va lo que no está en ella: los capítulos de su temporada si es un
   * episodio (y los extras del programa), o "Similares" si es una película o un
   * documental. Se puede reproducir antes de que llegue la ficha.
   */
  let { id, title: initialTitle, scroller }: { id: string; title: string; scroller: HTMLElement | undefined } = $props();

  // Cada ficha se monta para un vídeo concreto: lo ya conocido se pinta sin esperar.
  let item = $state<CatalogItem>(untrack(() => recallItem(`video:${id}`) ?? referenceItem(id, 'VIDEO', initialTitle)));
  let detail = $state<VideoDetail | null>(null);
  let isLoading = $state(true);
  let error = $state<LoadError | null>(null);
  let related = $state<CatalogItem[]>([]);
  let chaptersOrder: EpisodeOrder | null = null;
  let controller: AbortController | null = null;

  /** El mismo orden que la ficha del programa; sin ficha, del más nuevo al más antiguo. */
  async function orderForChapters(programId: string, seasonId: string | null, signal: AbortSignal): Promise<EpisodeOrder> {
    if (chaptersOrder) return chaptersOrder;
    try {
      const program = (await repository.loadProgram(programId, { signal })).value;
      chaptersOrder = episodeOrder(program, isFirstSeason(program, seasonId));
    } catch (e) {
      if (isAbort(e)) throw e;
      chaptersOrder = 'NEWEST_FIRST';
    }
    return chaptersOrder;
  }

  const chapters = new PagedLoader(async (page, signal) => {
    const programId = item.programId!;
    const seasonId = item.seasonId ?? null;
    const order = await orderForChapters(programId, seasonId, signal);
    return (await repository.loadProgramVideos(programId, { seasonId, page, completeOnly: true, order, signal })).value;
  });
  const extras = new PagedLoader((page, signal) => repository.loadProgramExtras(item.programId!, page, signal).then((r) => r.value));

  /** Solo películas y documentales tienen recomendaciones propias. */
  function relatedKind(media: CatalogItem): RelatedKind | null {
    if (mediaForm(media) === 'EXTRA') return null;
    const subject = mediaSubject(media);
    return subject === 'MOVIE' ? 'film' : subject === 'DOCUMENTARY' ? 'documental' : null;
  }

  async function load() {
    controller?.abort();
    chapters.cancel();
    extras.cancel();
    const ctrl = new AbortController();
    controller = ctrl;
    isLoading = true;
    error = null;
    try {
      const loaded = (await repository.loadVideo(item.playbackId ?? item.id, { signal: ctrl.signal })).value;
      if (ctrl.signal.aborted) return;
      // La ficha trae los derechos reales, que faltan en muchos feeds de portada.
      detail = loaded;
      item = loaded.item;
      isLoading = false;
      const media = loaded.item;
      if (media.programId && belongsToEpisodicProgram(media)) {
        chapters.load(true);
        // Los extras de un ciclo pertenecen a todo el ciclo, no a esta película.
        if (media.programShowsFragments) extras.load(true);
      }
      const kind = relatedKind(media);
      if (kind) {
        repository.loadRelated(kind, media.id, ctrl.signal).then(
          (r) => (related = r.value),
          () => {},
        );
      }
    } catch (e) {
      if (isAbort(e) || ctrl.signal.aborted) return;
      isLoading = false;
      error = toLoadError(e);
    }
  }

  load();
  onDestroy(() => {
    controller?.abort();
    chapters.cancel();
    extras.cancel();
  });

  const structure = $derived(parentStructure(item));
  const sections = $derived(contentSections(structure, chapters.state, extras.state, false));
  const tabs = $derived<ProgramTab[]>([...sections.map((s) => s.tab), ...(related.length ? (['RELATED'] as const) : [])]);
  let selectedTab = $state<ProgramTab | null>(null);
  const current = $derived(selectedTab && tabs.includes(selectedTab) ? selectedTab : tabs[0] ?? null);
  const activeSection = $derived(sections.find((s) => s.tab === current) ?? null);

  const resume = $derived.by(() => {
    const entry = watchHistory.byId.get(item.id);
    return entry && isInProgress(entry) ? entry : null;
  });
  const programTitle = $derived(detail?.programTitle ?? item.subtitle);
  const synopsis = $derived(htmlToParagraphs(detail?.description));
</script>

<FichaFrame title={item.title} shareUrl={detail?.webUrl ?? null} {tabs} {current} onselect={(tab) => (selectedTab = tab)} {scroller}>
  {#snippet header()}
    <FichaHeader
      backdropUrl={detail?.backdropUrl ?? item.imageUrl}
      title={item.title}
      subtitle={programTitle}
      onsubtitle={item.programId ? () => openProgram(item.programId!, programTitle ?? '') : undefined}
      subtitleLinkText={collectionLinkText(structure, programTitle)}
      meta={detail ? videoMetaLine(detail) : null}
      {synopsis}
      facts={detail ? videoFacts(detail) : null}
      progress={resume ? entryProgress(resume) : null}
    >
      {#snippet actions()}
        <div class="play-actions">
          <button class="btn btn-primary big" onclick={() => play(item)}>
            <Icon name="play" />
            <span>{resume ? 'Continuar' : 'Reproducir'}</span>
          </button>
          {#if resume}
            <button class="icon-btn tonal" onclick={() => play(item, true)} aria-label="Desde el inicio" title="Desde el inicio">
              <Icon name="replay" />
            </button>
          {/if}
        </div>
      {/snippet}
    </FichaHeader>
  {/snippet}

  {#if !detail && isLoading}
    <Panels kind="loading" compact />
  {:else if !detail && error}
    <Panels kind="message" message={errorText(error)} onretry={load} />
  {:else if current === 'RELATED'}
    <div class="grid poster">
      {#each related as rel (rel.id)}
        <ItemCard item={rel} layout="POSTER" width={220} onclick={() => openItem(rel)} />
      {/each}
    </div>
  {:else if activeSection}
    <SectionContent
      section={activeSection}
      currentItemId={item.id}
      onselect={(video) => (sectionOpensDetails(activeSection, video, structure) ? openItem(video) : play(video))}
      onloadmore={() => {
        if (activeSection.loadsComplete) chapters.loadMore();
        if (activeSection.loadsExtras) extras.loadMore();
      }}
      onretry={() => {
        if (activeSection.loadsComplete) chapters.retry();
        if (activeSection.loadsExtras) extras.retry();
      }}
    />
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
    min-height: 48px;
    font-size: 1rem;
  }

  @media (min-width: 900px) {
    .big {
      flex: 0 1 auto;
      min-width: 200px;
      padding: 0 28px 0 22px;
    }
  }

  .icon-btn.tonal {
    width: 48px;
    height: 48px;
  }
</style>
