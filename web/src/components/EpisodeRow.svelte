<script lang="ts">
  import { canHaveNextEpisode } from '../lib/domain/classification';
  import type { CatalogItem } from '../lib/domain/models';
  import { itemMetaLine } from '../lib/ui/text';
  import Artwork from './Artwork.svelte';
  import CardParts from './CardParts.svelte';
  import Icon from './Icon.svelte';

  /** Fila de episodio: miniatura a la izquierda y título + datos a la derecha. */
  let {
    item,
    onclick,
    progress = null,
    badge = null,
    highlighted = false,
    episodic = canHaveNextEpisode(item),
  }: {
    item: CatalogItem;
    onclick: () => void;
    progress?: number | null;
    badge?: string | null;
    highlighted?: boolean;
    episodic?: boolean;
  } = $props();

  const meta = $derived(itemMetaLine(item, episodic));
</script>

<button class="row" class:highlighted {onclick}>
  <span class="thumb">
    <Artwork url={item.imageUrl} ratio={16 / 9} width={200}>
      {#if progress != null}<CardParts part="progress" value={progress} />{/if}
      <span class="play"><Icon name="play" size={28} /></span>
    </Artwork>
  </span>
  <span class="text">
    {#if badge}<span class="badge">{badge}</span>{/if}
    <span class="title">{item.title}</span>
    {#if meta}<span class="meta">{meta}</span>{/if}
  </span>
</button>

<style>
  .row {
    display: flex;
    gap: 14px;
    align-items: flex-start;
    width: 100%;
    padding: 8px;
    border-radius: var(--radius);
    text-align: left;
    transition: background-color 0.15s;
  }

  .row:hover {
    background: var(--surface);
  }

  .row.highlighted {
    background: var(--surface-2);
  }

  .thumb {
    flex: none;
    width: clamp(132px, 30%, 200px);
  }

  .play {
    position: absolute;
    inset: 0;
    display: grid;
    place-items: center;
    color: white;
    background: rgb(0 0 0 / 0.35);
    opacity: 0;
    transition: opacity 0.15s;
  }

  .row:hover .play,
  .row:focus-visible .play {
    opacity: 1;
  }

  .text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
    padding-top: 2px;
  }

  .badge {
    color: var(--accent);
    font-size: 0.75rem;
    font-weight: 700;
  }

  .title {
    font-weight: 500;
    line-height: 1.3;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .meta {
    color: var(--muted);
    font-size: 0.82rem;
  }
</style>
