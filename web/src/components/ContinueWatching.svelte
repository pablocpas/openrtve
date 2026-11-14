<script lang="ts">
  import { entryProgress, type WatchEntry } from '../lib/data/watchHistory.svelte';
  import type { CatalogItem } from '../lib/domain/models';
  import Artwork from './Artwork.svelte';
  import CardParts from './CardParts.svelte';
  import Carousel from './Carousel.svelte';
  import Icon from './Icon.svelte';

  /**
   * "Seguir viendo": tocar la tarjeta continúa donde se dejó; su menú lleva a la ficha
   * o la quita de la fila (con deshacer).
   */
  let {
    entries,
    onresume,
    ondetails,
    onremove,
  }: {
    entries: WatchEntry[];
    onresume: (item: CatalogItem) => void;
    ondetails: (item: CatalogItem) => void;
    onremove: (item: CatalogItem) => void;
  } = $props();

  /** Menú abierto y dónde: va fijo en la ventana para que el carrusel no lo recorte. */
  let menu = $state<{ id: string; top: number; right: number } | null>(null);
  const openMenu = $derived(menu?.id ?? null);

  function toggleMenu(event: MouseEvent, id: string) {
    if (menu?.id === id) {
      menu = null;
      return;
    }
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    menu = { id, top: rect.bottom + 4, right: Math.max(8, window.innerWidth - rect.right) };
  }

  /** Minutos que faltan, redondeando hacia arriba. */
  function remaining(entry: WatchEntry): string | null {
    const left = entry.durationMs - entry.positionMs;
    if (!(entry.durationMs > 0) || left <= 0) return null;
    const minutes = Math.ceil(left / 60_000);
    return minutes === 1 ? 'Queda 1 min' : `Quedan ${minutes} min`;
  }

  function closeMenus(event: MouseEvent) {
    if (!(event.target as Element).closest('.menu-anchor')) menu = null;
  }
</script>

<svelte:window
  onclick={closeMenus}
  onkeydown={(e) => e.key === 'Escape' && (menu = null)}
  onresize={() => (menu = null)}
  onwheel={() => (menu = null)}
/>

<section class="continue">
  <h2 class="section-title">Seguir viendo</h2>
  <Carousel label="Seguir viendo">
    {#each entries as entry (entry.item.id)}
      <div class="card">
        <button class="media" onclick={() => onresume(entry.item)} aria-label="Continuar {entry.item.title}">
          <Artwork url={entry.item.imageUrl} ratio={16 / 9} width={340}>
            <CardParts part="progress" value={entryProgress(entry)} />
            <span class="play"><Icon name="play" size={34} /></span>
          </Artwork>
        </button>
        <div class="caption">
          <button class="text" onclick={() => onresume(entry.item)} tabindex="-1">
            <span class="title">{entry.item.title}</span>
            {#if remaining(entry)}<span class="meta">{remaining(entry)}</span>{/if}
          </button>
          <div class="menu-anchor">
            <button
              class="icon-btn"
              aria-label="Más opciones"
              aria-haspopup="menu"
              aria-expanded={openMenu === entry.item.id}
              onclick={(e) => toggleMenu(e, entry.item.id)}
            >
              <Icon name="more" size={22} />
            </button>
            {#if menu && openMenu === entry.item.id}
              <div class="menu" role="menu" style:top="{menu.top}px" style:right="{menu.right}px">
                <button role="menuitem" onclick={() => { menu = null; onresume(entry.item); }}><Icon name="play" size={20} /> Continuar</button>
                <button role="menuitem" onclick={() => { menu = null; ondetails(entry.item); }}><Icon name="list" size={20} /> Ver ficha</button>
                <button role="menuitem" onclick={() => { menu = null; onremove(entry.item); }}><Icon name="delete" size={20} /> Quitar de Seguir viendo</button>
              </div>
            {/if}
          </div>
        </div>
      </div>
    {/each}
  </Carousel>
</section>

<style>
  .continue h2 {
    padding: 0 var(--gutter) 10px;
  }

  .card {
    width: var(--landscape-w);
  }

  .media {
    display: block;
    width: 100%;
    border-radius: var(--radius);
  }

  .media :global(.artwork) {
    transition: box-shadow 0.2s;
  }

  .media:hover :global(.artwork) {
    box-shadow: 0 0 0 2px rgb(255 255 255 / 0.85);
  }

  .play {
    position: absolute;
    inset: 0;
    display: grid;
    place-items: center;
    color: white;
    background: rgb(0 0 0 / 0.3);
    opacity: 0;
    transition: opacity 0.15s;
  }

  .media:hover .play,
  .media:focus-visible .play {
    opacity: 1;
  }

  .caption {
    display: flex;
    align-items: flex-start;
  }

  .text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    text-align: left;
    padding: 8px 2px 0;
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

  .meta {
    font-size: 0.8rem;
    color: var(--muted);
  }

  .menu-anchor {
    position: relative;
    margin-top: 2px;
  }

  .menu-anchor .icon-btn {
    width: 38px;
    height: 38px;
  }

  .menu {
    position: fixed;
    z-index: 60;
    min-width: 240px;
    padding: 6px 0;
    border-radius: 8px;
    background: var(--surface-3);
    box-shadow: 0 12px 32px rgb(0 0 0 / 0.5);
  }

  .menu button {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    padding: 10px 16px;
    text-align: left;
    font-size: 0.92rem;
  }

  .menu button:hover {
    background: rgb(255 255 255 / 0.07);
  }
</style>
