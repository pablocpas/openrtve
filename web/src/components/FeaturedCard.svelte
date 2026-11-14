<script lang="ts">
  import type { CatalogItem } from '../lib/domain/models';
  import Artwork from './Artwork.svelte';
  import CardParts from './CardParts.svelte';
  import Icon from './Icon.svelte';

  /** Destacado único (`ColeccionSuperDestacado`): imagen, título, descripción y botón. */
  let { item, onopen }: { item: CatalogItem; onopen: () => void } = $props();
</script>

<div class="featured">
  <button class="media" onclick={onopen} tabindex="-1" aria-hidden="true">
    <Artwork url={item.imageUrl} ratio={16 / 9} width={900} rounded={false}>
      {#if item.live?.channelLogoUrl}<CardParts part="logo" src={item.live.channelLogoUrl} />{/if}
    </Artwork>
  </button>
  <div class="body">
    <h3>{item.title}</h3>
    {#if item.subtitle}<p>{item.subtitle}</p>{/if}
    <button class="btn btn-primary" onclick={onopen}><Icon name="play" size={20} /> Ver ahora</button>
  </div>
</div>

<style>
  .featured {
    margin: 0 var(--gutter);
    display: grid;
    border-radius: var(--radius-lg);
    overflow: hidden;
    background: var(--surface-2);
  }

  @media (min-width: 900px) {
    .featured {
      grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
      align-items: center;
    }
  }

  .media {
    display: block;
    width: 100%;
  }

  .body {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    padding: 14px 18px 18px;
  }

  @media (min-width: 900px) {
    .body {
      padding: 24px clamp(20px, 3vw, 48px);
      gap: 10px;
    }
  }

  h3 {
    font-size: clamp(1.2rem, 1rem + 0.9vw, 2rem);
    font-weight: 700;
    line-height: 1.15;
  }

  p {
    color: var(--muted);
    display: -webkit-box;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .btn {
    margin-top: 8px;
  }
</style>
