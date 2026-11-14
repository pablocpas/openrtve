<script lang="ts">
  import { canAddToMyList, favorites } from '../lib/data/favorites.svelte';
  import type { CatalogItem } from '../lib/domain/models';
  import Icon from './Icon.svelte';

  /**
   * "+" / "✓" de "Mi lista" junto a Reproducir (Ficha.kt). Sin botón de reproducir al
   * lado ([labeled]), va con su texto: "Mi lista" o "En Mi lista".
   */
  let { item, labeled = false }: { item: CatalogItem; labeled?: boolean } = $props();

  const listed = $derived(favorites.ids.has(item.id));
  const label = $derived(listed ? 'Quitar de Mi lista' : 'Añadir a Mi lista');
</script>

{#if canAddToMyList(item)}
  {#if labeled}
    <button class="btn btn-tonal labeled" onclick={() => favorites.toggle(item)} aria-pressed={listed}>
      <Icon name={listed ? 'check' : 'add'} />
      <span>{listed ? 'En Mi lista' : 'Mi lista'}</span>
    </button>
  {:else}
    <button class="icon-btn tonal" onclick={() => favorites.toggle(item)} aria-label={label} title={label} aria-pressed={listed}>
      <Icon name={listed ? 'check' : 'add'} />
    </button>
  {/if}
{/if}

<style>
  .icon-btn {
    width: 48px;
    height: 48px;
  }

  .labeled {
    min-height: 48px;
    padding: 0 24px 0 18px;
  }
</style>
