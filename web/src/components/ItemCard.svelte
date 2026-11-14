<script lang="ts">
  import { imageFor, isUpcomingAt, liveProgressAt, type CatalogItem, type RowLayout } from '../lib/domain/models';
  import { clock } from '../lib/ui/messages.svelte';
  import { scheduleLabel } from '../lib/ui/text';
  import Artwork from './Artwork.svelte';
  import CardParts from './CardParts.svelte';

  /**
   * Tarjeta de catálogo con el texto debajo. Apaisada por defecto; los directos
   * llevan el logo del canal, "● Directo" y el progreso de la emisión, y los
   * programados su horario. Con [layout] vertical o cuadrado es un póster.
   */
  let {
    item,
    onclick,
    layout = 'LANDSCAPE',
    rank = null,
    progress = null,
    width = 340,
  }: {
    item: CatalogItem;
    onclick: () => void;
    layout?: RowLayout;
    rank?: number | null;
    progress?: number | null;
    /** Ancho en que se pinta, para pedir la imagen a su tamaño. */
    width?: number;
  } = $props();

  const ratio = $derived(layout === 'POSTER' ? 2 / 3 : layout === 'POSTER_TALL' ? 1 / 2 : layout === 'SQUARE' ? 1 : 16 / 9);
  const vertical = $derived(layout === 'POSTER' || layout === 'POSTER_TALL');
  const live = $derived(item.live ?? null);
  const upcoming = $derived(live ? isUpcomingAt(live, clock.now) : false);
  const liveProgress = $derived(live?.isOnAir ? liveProgressAt(live, clock.now) : null);
</script>

<button class="card" class:vertical {onclick} title={item.title}>
  <Artwork url={imageFor(item, layout)} {ratio} {width}>
    {#if live?.channelLogoUrl}
      <CardParts part="logo" src={live.channelLogoUrl} />
    {:else if item.kind === 'LIVE' && layout === 'SQUARE'}
      <CardParts part="liveBadge" />
    {/if}
    {#if rank != null}<CardParts part="rank" value={rank} />{/if}
    {#if liveProgress != null}<CardParts part="progress" value={liveProgress} />{/if}
    {#if progress != null}<CardParts part="progress" value={progress} />{/if}
  </Artwork>
  <span class="caption">
    {#if live && upcoming}
      <span class="schedule">{[scheduleLabel(live, clock.now), live.category].filter(Boolean).join(' · ')}</span>
    {:else if live && layout !== 'SQUARE'}
      <CardParts part="liveDot" />
    {/if}
    <span class="title">{item.title}</span>
    {#if item.subtitle && !vertical && !upcoming}
      <span class="subtitle">{item.subtitle}</span>
    {/if}
  </span>
</button>

<style>
  .card {
    display: flex;
    flex-direction: column;
    width: 100%;
    text-align: left;
    border-radius: var(--radius);
    outline-offset: 3px;
  }

  .card :global(.artwork) {
    transition: transform 0.2s ease, box-shadow 0.2s ease;
  }

  @media (hover: hover) {
    .card:hover :global(.artwork) {
      transform: translateY(-2px);
      box-shadow: 0 0 0 2px rgb(255 255 255 / 0.85), 0 10px 24px rgb(0 0 0 / 0.45);
    }
  }

  .card:focus-visible {
    outline: none;
  }

  .card:focus-visible :global(.artwork) {
    box-shadow: 0 0 0 3px var(--accent);
  }

  .caption {
    display: flex;
    flex-direction: column;
    gap: 1px;
    padding: 8px 2px 4px;
    min-width: 0;
  }

  .title {
    font-size: 0.92rem;
    font-weight: 500;
    line-height: 1.3;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .subtitle,
  .schedule {
    font-size: 0.8rem;
    color: var(--muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
</style>
