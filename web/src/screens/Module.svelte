<script lang="ts">
  import { onDestroy, untrack } from 'svelte';
  import Icon from '../components/Icon.svelte';
  import ItemGrid from '../components/ItemGrid.svelte';
  import Panels from '../components/Panels.svelte';
  import TopBar from '../components/TopBar.svelte';
  import { isAbort, toLoadError, type LoadError } from '../lib/data/http';
  import { repository } from '../lib/data/repository';
  import { isLiveRow, type CatalogItem, type HomeRow } from '../lib/domain/models';
  import { rowLayout } from '../lib/data/parser';
  import { openItem } from '../lib/ui/actions';
  import { messages } from '../lib/ui/messages.svelte';
  import { errorText } from '../lib/ui/text';

  /** Rejilla completa de una fila ("Ver todo"), de una colección o de un canal temático. */
  let {
    url,
    presentation,
    moduleType,
    title: initialTitle,
    active,
  }: { url: string; presentation: string | null; moduleType: string | null; title: string; active: boolean } = $props();

  // Cada pantalla se monta para una ruta concreta: sus datos de partida no cambian.
  const row: HomeRow = untrack(() => ({
    id: -1,
    title: initialTitle,
    order: 0,
    moduleType,
    presentation,
    contentUrl: url,
    layout: rowLayout(presentation),
    links: [],
  }));

  let title = $state(row.title);
  let items = $state<CatalogItem[]>([]);
  let isLoading = $state(true);
  let isStale = $state(false);
  let failure = $state<LoadError | null>(null);
  let controller: AbortController | null = null;

  async function load(forceRefresh: boolean) {
    controller?.abort();
    const ctrl = new AbortController();
    controller = ctrl;
    const hasItems = items.length > 0;
    isLoading = !hasItems;
    failure = null;
    try {
      const module = await repository.loadModule(row, { forceRefresh, signal: ctrl.signal });
      title = module.value.title.trim() || title;
      items = module.value.items;
      isStale = module.isStale;
    } catch (e) {
      if (isAbort(e) || ctrl.signal.aborted) return;
      // Con contenido ya pintado, un aviso; sin él, el fallo ocupa su sitio.
      if (hasItems) messages.show(errorText(toLoadError(e)));
      else failure = toLoadError(e);
    } finally {
      if (!ctrl.signal.aborted) isLoading = false;
    }
  }

  load(false);
  onDestroy(() => controller?.abort());

  // Los canales temáticos en directo cambian de programa: se renuevan cada minuto en pantalla.
  $effect(() => {
    if (!active || !isLiveRow(row)) return;
    const timer = setInterval(() => document.visibilityState === 'visible' && load(true), 60_000);
    return () => clearInterval(timer);
  });
</script>

<TopBar {title}>
  {#snippet actions()}
    <button class="icon-btn" onclick={() => load(true)} aria-label="Actualizar"><Icon name="replay" /></button>
  {/snippet}
</TopBar>

<div class="screen-body">
  {#if isStale}<p class="stale-banner">Mostrando la última copia disponible</p>{/if}
  {#if isLoading}
    <Panels kind="loading" />
  {:else if items.length === 0}
    <Panels kind="message" message={failure ? errorText(failure) : 'Este módulo no tiene contenidos ahora mismo'} onretry={() => load(true)} />
  {:else}
    <ItemGrid {items} layout={row.layout} onopen={openItem} />
  {/if}
</div>
