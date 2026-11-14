<script lang="ts">
  import Artwork from '../components/Artwork.svelte';
  import Icon from '../components/Icon.svelte';
  import Panels from '../components/Panels.svelte';
  import { favorites } from '../lib/data/favorites.svelte';
  import { openItem, removeFromMyList } from '../lib/ui/actions';
  import { nav } from '../lib/ui/nav.svelte';

  /**
   * "Mi lista" (MyListScreen.kt): lo guardado con "+" desde las fichas, lo último
   * primero. La tarjeta abre la ficha; su menú lo quita (con deshacer).
   */
  let { scroller }: { scroller: HTMLElement | undefined } = $props();

  /** Menú abierto y dónde: va fijo en la ventana, como el de "Seguir viendo". */
  let menu = $state<{ id: string; top: number; right: number } | null>(null);

  function toggleMenu(event: MouseEvent, id: string) {
    if (menu?.id === id) {
      menu = null;
      return;
    }
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    menu = { id, top: rect.bottom + 4, right: Math.max(8, window.innerWidth - rect.right) };
  }

  function closeMenus(event: MouseEvent) {
    if (!(event.target as Element).closest('.menu-anchor')) menu = null;
  }

  // Volver a pulsar Mi lista sube al principio.
  let handledReselect = nav.reselect.mylist;
  $effect(() => {
    const request = nav.reselect.mylist;
    if (request !== handledReselect) {
      handledReselect = request;
      scroller?.scrollTo({ top: 0, behavior: 'smooth' });
    }
  });

  // Con el menú abierto, cualquier scroll lo cierra: va fijo en la ventana y se quedaría flotando.
  $effect(() => {
    if (!menu) return;
    const close = () => (menu = null);
    document.addEventListener('scroll', close, { capture: true, passive: true });
    return () => document.removeEventListener('scroll', close, { capture: true });
  });
</script>

<svelte:window onclick={closeMenus} onkeydown={(e) => e.key === 'Escape' && (menu = null)} onresize={() => (menu = null)} />

<div class="screen-body">
  <h1 class="screen-title">Mi lista</h1>
  {#if favorites.items.length === 0}
    <Panels kind="message" message="Aún no has guardado nada. Añade programas y películas con «+» desde su ficha." />
  {:else}
    <div class="grid landscape">
      {#each favorites.items as item (item.id)}
        <div class="card">
          <button class="media" onclick={() => openItem(item)} aria-label={item.title}>
            <Artwork url={item.imageUrl} ratio={16 / 9} width={340} />
          </button>
          <div class="caption">
            <button class="text" onclick={() => openItem(item)} tabindex="-1">
              <span class="title">{item.title}</span>
              {#if item.subtitle}<span class="meta">{item.subtitle}</span>{/if}
            </button>
            <div class="menu-anchor">
              <button
                class="icon-btn"
                aria-label="Más opciones"
                aria-haspopup="menu"
                aria-expanded={menu?.id === item.id}
                onclick={(e) => toggleMenu(e, item.id)}
              >
                <Icon name="more" size={22} />
              </button>
              {#if menu?.id === item.id}
                <div class="menu" role="menu" style:top="{menu.top}px" style:right="{menu.right}px">
                  <button role="menuitem" onclick={() => { menu = null; removeFromMyList(item); }}>
                    <Icon name="delete" size={20} /> Quitar de Mi lista
                  </button>
                </div>
              {/if}
            </div>
          </div>
        </div>
      {/each}
    </div>
  {/if}
</div>

<style>
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
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
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
