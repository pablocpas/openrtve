<script lang="ts">
  import type { CatalogItem } from '../lib/domain/models';
  import { entryProgress, isInProgress, watchHistory } from '../lib/data/watchHistory.svelte';
  import { sectionBadge, showsPosters, type ContentSection, type PlaySuggestion } from '../lib/ui/sections';
  import { errorText } from '../lib/ui/text';
  import EpisodeRow from './EpisodeRow.svelte';
  import ItemCard from './ItemCard.svelte';
  import Panels from './Panels.svelte';

  /**
   * Una sección de ficha (capítulos, películas, extras...), con paginación infinita:
   * al acercarse al final se pide la página siguiente, y "Cargar más" queda de respaldo.
   */
  let {
    section,
    onselect,
    onloadmore,
    onretry,
    suggestion = null,
    currentItemId = null,
  }: {
    section: ContentSection;
    onselect: (item: CatalogItem) => void;
    onloadmore: () => void;
    onretry: () => void;
    suggestion?: PlaySuggestion | null;
    currentItemId?: string | null;
  } = $props();

  const episodic = $derived(section.tab === 'EPISODES');
  let sentinel = $state<HTMLElement>();

  $effect(() => {
    if (!sentinel || !section.hasMore || section.error || section.isLoading || section.isLoadingMore) return;
    // La raíz es la pantalla que desplaza: así el margen adelanta la petición de verdad.
    const observer = new IntersectionObserver((entries) => entries.some((e) => e.isIntersecting) && onloadmore(), {
      root: sentinel.closest('.screen'),
      rootMargin: '600px 0px',
    });
    observer.observe(sentinel);
    return () => observer.disconnect();
  });
</script>

{#if showsPosters(section.tab)}
  <div class="grid poster">
    {#each section.items as item (item.id)}
      <ItemCard {item} layout="POSTER" width={220} onclick={() => onselect(item)} />
    {/each}
  </div>
{:else}
  <div class="episodes">
    {#each section.items as item (item.id)}
      {@const entry = watchHistory.byId.get(item.id)}
      <EpisodeRow
        {item}
        onclick={() => onselect(item)}
        progress={entry && isInProgress(entry) ? entryProgress(entry) : null}
        badge={sectionBadge(section, item, entry, suggestion, currentItemId)}
        highlighted={episodic && (item.id === currentItemId || suggestion?.item.id === item.id)}
        {episodic}
      />
    {/each}
  </div>
{/if}

{#if section.error}
  <Panels kind="message" compact message={errorText(section.error)} {onretry} />
{:else if section.items.length === 0 && !section.isLoading && !section.hasMore}
  <Panels kind="message" compact message={episodic ? 'Este programa no tiene vídeos disponibles' : 'Este módulo no tiene contenidos ahora mismo'} />
{/if}

{#if section.isLoading || section.isLoadingMore}
  <Panels kind="loading" compact />
{:else if section.hasMore && !section.error}
  <div class="more" bind:this={sentinel}>
    <button class="btn btn-text" onclick={onloadmore}>Cargar más</button>
  </div>
{/if}

<style>
  .episodes {
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
</style>
