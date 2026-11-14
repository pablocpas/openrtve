<script lang="ts">
  import { gridLayout, type CatalogItem, type RowLayout } from '../lib/domain/models';
  import ItemCard from './ItemCard.svelte';

  /** Rejilla completa de una fila ("Ver todo") o de un filtro de búsqueda. */
  let { items, layout, onopen }: { items: CatalogItem[]; layout: RowLayout; onopen: (item: CatalogItem) => void } = $props();

  const cardLayout = $derived(gridLayout(layout));
  const kind = $derived(cardLayout === 'POSTER' || cardLayout === 'POSTER_TALL' ? 'poster' : cardLayout === 'SQUARE' ? 'square' : 'landscape');
</script>

<div class="grid {kind}">
  {#each items as item, index (item.id)}
    <ItemCard
      {item}
      layout={cardLayout}
      rank={cardLayout === 'RANKED' ? index + 1 : null}
      width={kind === 'landscape' ? 340 : 220}
      onclick={() => onopen(item)}
    />
  {/each}
</div>
