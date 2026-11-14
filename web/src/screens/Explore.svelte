<script lang="ts">
  import { onDestroy } from 'svelte';
  import ImageTile from '../components/ImageTile.svelte';
  import ItemCard from '../components/ItemCard.svelte';
  import Panels from '../components/Panels.svelte';
  import SettingsButton from '../components/SettingsButton.svelte';
  import { isAbort, toLoadError, type LoadError } from '../lib/data/http';
  import { repository } from '../lib/data/repository';
  import { liveRow, type CatalogItem, type ExploreGroup } from '../lib/domain/models';
  import { openCategory, openItem } from '../lib/ui/actions';
  import { nav } from '../lib/ui/nav.svelte';
  import { errorText, exploreHeader } from '../lib/ui/text';

  /**
   * Categorías del menú de la app oficial (ExploreViewModel.kt), leídas de la
   * configuración remota. Los canales temáticos son directos: su tarjeta es la de lo
   * que emiten ahora y se reproducen al tocarlos.
   */
  let { active, scroller }: { active: boolean; scroller: HTMLElement | undefined } = $props();

  let groups = $state<ExploreGroup[]>([]);
  let isLoading = $state(true);
  let error = $state<LoadError | null>(null);
  let controller: AbortController | null = null;
  let liveBusy = false;

  async function load() {
    controller?.abort();
    const ctrl = new AbortController();
    controller = ctrl;
    isLoading = true;
    error = null;
    try {
      groups = (await repository.loadExplore({ signal: ctrl.signal })).value;
      isLoading = false;
      await refreshLive(false, ctrl.signal);
    } catch (e) {
      if (isAbort(e) || ctrl.signal.aborted) return;
      isLoading = false;
      error = toLoadError(e);
    }
  }

  /**
   * Cada canal temático con lo que emite ahora, de su feed de directo (que también
   * trae la imagen que el menú no tiene). Un fallo conserva lo que ya había.
   */
  async function refreshLive(forceRefresh: boolean, signal: AbortSignal) {
    const channels = groups.flatMap((g) => g.categories).filter((c) => c.isLive);
    if (!channels.length || liveBusy) return;
    liveBusy = true;
    try {
      const playing = new Map<string, CatalogItem>();
      await Promise.all(
        channels.map(async (c) => {
          try {
            const module = await repository.loadModule(liveRow(c.contentUrl, c.title), { forceRefresh, signal });
            const live = module.value.items.find((it) => it.kind === 'LIVE');
            if (live) playing.set(c.contentUrl, live);
          } catch {
            // Sin directo: queda la tarjeta de categoría.
          }
        }),
      );
      if (signal.aborted) return;
      groups = groups.map((g) => ({
        ...g,
        categories: g.categories.map((c) => {
          const now = playing.get(c.contentUrl) ?? c.nowPlaying;
          return { ...c, nowPlaying: now, imageUrl: c.imageUrl ?? now?.imageUrl ?? null };
        }),
      }));
    } finally {
      liveBusy = false;
    }
  }

  load();
  onDestroy(() => controller?.abort());

  // Lo que emiten los canales temáticos se renueva cada minuto mientras se ve Explorar.
  $effect(() => {
    if (!active) return;
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible' && controller && !isLoading) refreshLive(true, controller.signal);
    }, 60_000);
    return () => clearInterval(timer);
  });

  let handledReselect = nav.reselect.explore;
  $effect(() => {
    const request = nav.reselect.explore;
    if (request !== handledReselect) {
      handledReselect = request;
      scroller?.scrollTo({ top: 0, behavior: 'smooth' });
    }
  });
</script>

<div class="title-row">
  <h1 class="screen-title">Explorar</h1>
  <SettingsButton />
</div>

<div class="screen-body">
  {#if isLoading}
    <Panels kind="loading" />
  {:else if groups.length === 0 && error}
    <Panels kind="message" message={errorText(error)} onretry={load} />
  {:else}
    {#each groups as group, gi (gi)}
      <section>
        <h2 class="section-title">{exploreHeader(group)}</h2>
        <div class="grid landscape">
          {#each group.categories as category (category.contentUrl)}
            {#if category.nowPlaying}
              {@const live = category.nowPlaying}
              <ItemCard item={live} onclick={() => openItem(live)} />
            {:else}
              <ImageTile title={category.title} imageUrl={category.imageUrl} fallback={category.fallbackImageUrl} onclick={() => openCategory(category)} />
            {/if}
          {/each}
        </div>
      </section>
    {/each}
  {/if}
</div>

<style>
  .title-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-right: calc(var(--gutter) - 4px);
  }

  .title-row :global(.settings-btn) {
    margin-top: 12px;
  }

  section h2 {
    padding: 16px var(--gutter) 2px;
  }

  section .grid {
    padding-bottom: 12px;
  }
</style>
