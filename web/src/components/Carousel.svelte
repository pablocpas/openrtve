<script lang="ts">
  import type { Snippet } from 'svelte';
  import Icon from './Icon.svelte';

  /**
   * Fila horizontal de tarjetas. Con el dedo se desliza; con ratón aparecen flechas
   * a los lados que avanzan una pantalla; con el teclado, el foco la recorre.
   */
  let { children, label = '' }: { children: Snippet; label?: string } = $props();

  let track = $state<HTMLDivElement>();
  let canPrev = $state(false);
  let canNext = $state(false);

  function update() {
    if (!track) return;
    canPrev = track.scrollLeft > 4;
    canNext = track.scrollLeft + track.clientWidth < track.scrollWidth - 4;
  }

  function page(direction: 1 | -1) {
    if (!track) return;
    track.scrollBy({ left: direction * track.clientWidth * 0.85, behavior: 'smooth' });
  }

  $effect(() => {
    if (!track) return;
    update();
    const observer = new ResizeObserver(update);
    observer.observe(track);
    return () => observer.disconnect();
  });
</script>

<div class="carousel" role="group" aria-label={label || undefined}>
  <div class="track" bind:this={track} onscroll={update}>
    {@render children()}
  </div>
  {#if canPrev}
    <button class="arrow prev" onclick={() => page(-1)} aria-label="Anteriores" tabindex="-1">
      <Icon name="chevronLeft" size={32} />
    </button>
  {/if}
  {#if canNext}
    <button class="arrow next" onclick={() => page(1)} aria-label="Siguientes" tabindex="-1">
      <Icon name="chevronRight" size={32} />
    </button>
  {/if}
</div>

<style>
  .carousel {
    position: relative;
  }

  .track {
    display: flex;
    gap: var(--row-gap);
    overflow-x: auto;
    overflow-y: hidden;
    padding: 4px var(--gutter) 6px;
    scroll-padding-inline: var(--gutter);
    scroll-snap-type: x proximity;
    scrollbar-width: none;
    overscroll-behavior-x: contain;
  }

  .track::-webkit-scrollbar {
    display: none;
  }

  .track > :global(*) {
    flex: none;
    scroll-snap-align: start;
  }

  .arrow {
    position: absolute;
    top: 4px;
    bottom: 48px;
    width: calc(var(--gutter) + 8px);
    min-width: 40px;
    display: grid;
    place-items: center;
    color: white;
    opacity: 0;
    transition: opacity 0.2s;
    z-index: 2;
  }

  .prev {
    left: 0;
    background: linear-gradient(to right, rgb(9 10 15 / 0.9), transparent);
  }

  .next {
    right: 0;
    background: linear-gradient(to left, rgb(9 10 15 / 0.9), transparent);
  }

  @media (hover: hover) and (pointer: fine) {
    .carousel:hover .arrow {
      opacity: 1;
    }
  }

  .arrow:hover :global(svg) {
    transform: scale(1.15);
  }
</style>
