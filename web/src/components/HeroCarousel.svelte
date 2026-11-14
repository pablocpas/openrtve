<script lang="ts">
  import type { CatalogItem } from '../lib/domain/models';
  import Artwork from './Artwork.svelte';
  import CardParts from './CardParts.svelte';
  import Icon from './Icon.svelte';

  /**
   * Carrusel destacado ("Slide > Home"): una pieza por página, con el título encima e
   * indicador de páginas. Avanza solo cada pocos segundos salvo con el ratón encima,
   * con el foco dentro o si se prefiere menos movimiento.
   */
  let { items, onopen, fullBleed = false }: { items: CatalogItem[]; onopen: (item: CatalogItem) => void; fullBleed?: boolean } =
    $props();

  let track = $state<HTMLDivElement>();
  let current = $state(0);
  let paused = $state(false);
  let viewportWidth = $state(typeof window !== 'undefined' ? window.innerWidth : 1280);

  function go(index: number, smooth = true) {
    if (!track || items.length === 0) return;
    const target = (index + items.length) % items.length;
    track.scrollTo({ left: target * track.clientWidth, behavior: smooth ? 'smooth' : 'instant' });
  }

  function onscroll() {
    if (!track) return;
    current = Math.round(track.scrollLeft / Math.max(1, track.clientWidth));
  }

  $effect(() => {
    if (items.length < 2 || paused) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible' && track?.offsetParent) go(current + 1);
    }, 7000);
    return () => clearInterval(timer);
  });

  function onkeydown(event: KeyboardEvent) {
    if (event.key === 'ArrowRight') {
      go(current + 1);
      event.preventDefault();
    } else if (event.key === 'ArrowLeft') {
      go(current - 1);
      event.preventDefault();
    }
  }
</script>

<svelte:window bind:innerWidth={viewportWidth} />

<div
  class="hero"
  role="region"
  aria-label="Destacados"
  class:full={fullBleed}
  aria-roledescription="carrusel"
  onmouseenter={() => (paused = true)}
  onmouseleave={() => (paused = false)}
  onfocusin={() => (paused = true)}
  onfocusout={() => (paused = false)}
>
  <div class="track" bind:this={track} {onscroll} {onkeydown} role="presentation">
    {#each items as item, index (item.id)}
      <button
        class="slide"
        onclick={() => onopen(item)}
        aria-label={item.title}
        tabindex={index === current ? 0 : -1}
        aria-hidden={index !== current}
      >
        <Artwork url={item.imageUrl} ratio={16 / 9} width={viewportWidth} rounded={!fullBleed} eager={index === 0} class="hero-art">
          {#if item.live?.channelLogoUrl}
            <CardParts part="logo" src={item.live.channelLogoUrl} />
          {:else if item.kind === 'LIVE'}
            <CardParts part="liveBadge" />
          {/if}
        </Artwork>
        <span class="shade"></span>
        <span class="text">
          {#if item.live?.isOnAir}<CardParts part="liveDot" />{/if}
          <span class="title">{item.title}</span>
          {#if item.subtitle}<span class="subtitle">{item.subtitle}</span>{/if}
          <span class="cta"><Icon name="play" size={20} /> Ver ahora</span>
        </span>
      </button>
    {/each}
  </div>

  {#if items.length > 1}
    <button class="nav prev" onclick={() => go(current - 1)} aria-label="Anterior" tabindex="-1"><Icon name="chevronLeft" size={36} /></button>
    <button class="nav next" onclick={() => go(current + 1)} aria-label="Siguiente" tabindex="-1"><Icon name="chevronRight" size={36} /></button>
    <div class="dots" role="tablist" aria-label="Destacados">
      {#each items as item, index (item.id)}
        <button
          class="dot"
          class:active={index === current}
          role="tab"
          aria-selected={index === current}
          aria-label={item.title}
          onclick={() => go(index)}
          tabindex="-1"
        ></button>
      {/each}
    </div>
  {/if}
</div>

<style>
  .hero {
    position: relative;
    padding: 0 var(--gutter);
  }

  .hero.full {
    padding: 0;
  }

  .track {
    display: flex;
    overflow-x: auto;
    scroll-snap-type: x mandatory;
    scrollbar-width: none;
    border-radius: var(--radius-lg);
    overscroll-behavior-x: contain;
  }

  .full .track {
    border-radius: 0;
  }

  .track::-webkit-scrollbar {
    display: none;
  }

  .slide {
    position: relative;
    flex: 0 0 100%;
    scroll-snap-align: start;
    scroll-snap-stop: always;
    text-align: left;
    /* Siempre 16:9 en el móvil: los carteles llevan el título dibujado. En pantallas
       grandes se limita la altura para que se vea la fila siguiente. */
    height: min(56.25vw, 74vh);
    min-height: 220px;
    overflow: hidden;
  }

  .full .slide {
    height: min(56.25vw, 78vh);
  }

  .slide :global(.hero-art) {
    position: absolute;
    inset: 0;
    height: 100%;
    aspect-ratio: auto !important;
  }

  .slide :global(.hero-art img) {
    object-position: center 20%;
  }

  .shade {
    position: absolute;
    inset: 0;
    background:
      linear-gradient(to top, rgb(9 10 15 / 0.95) 0%, rgb(9 10 15 / 0.5) 30%, transparent 58%),
      linear-gradient(to right, rgb(9 10 15 / 0.7) 0%, transparent 50%);
    pointer-events: none;
  }

  .full .shade {
    background:
      linear-gradient(to top, var(--bg) 0%, rgb(9 10 15 / 0.6) 28%, transparent 55%),
      linear-gradient(to bottom, rgb(0 0 0 / 0.55) 0%, transparent 22%),
      linear-gradient(to right, rgb(9 10 15 / 0.75) 0%, transparent 55%);
  }

  .text {
    position: absolute;
    left: clamp(16px, 4vw, 64px);
    right: clamp(16px, 4vw, 64px);
    bottom: clamp(16px, 5vw, 72px);
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    max-width: 720px;
  }

  .full .text {
    left: var(--gutter);
  }

  .title {
    font-size: clamp(1.35rem, 0.9rem + 2.2vw, 3.2rem);
    font-weight: 800;
    line-height: 1.08;
    letter-spacing: -0.01em;
    color: white;
    text-shadow: 0 2px 12px rgb(0 0 0 / 0.4);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .subtitle {
    color: rgb(255 255 255 / 0.88);
    font-size: clamp(0.9rem, 0.8rem + 0.4vw, 1.2rem);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100%;
  }

  .cta {
    display: none;
    align-items: center;
    gap: 8px;
    margin-top: 14px;
    padding: 0 22px 0 16px;
    height: 44px;
    border-radius: 999px;
    background: var(--accent);
    color: var(--on-accent);
    font-weight: 600;
  }

  @media (min-width: 900px) {
    .cta {
      display: inline-flex;
    }
  }

  .slide:hover .cta {
    filter: brightness(1.08);
  }

  .nav {
    position: absolute;
    top: 50%;
    transform: translateY(-50%);
    width: 48px;
    height: 48px;
    border-radius: 50%;
    display: grid;
    place-items: center;
    background: rgb(0 0 0 / 0.4);
    color: white;
    opacity: 0;
    transition: opacity 0.2s, background-color 0.2s;
  }

  .nav:hover {
    background: rgb(0 0 0 / 0.65);
  }

  .prev {
    left: calc(var(--gutter) + 12px);
  }

  .next {
    right: calc(var(--gutter) + 12px);
  }

  .full .prev {
    left: 12px;
  }

  .full .next {
    right: 12px;
  }

  @media (hover: hover) and (pointer: fine) {
    .hero:hover .nav {
      opacity: 1;
    }
  }

  .dots {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 6px;
    height: 8px;
    margin-top: 12px;
  }

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: rgb(242 242 245 / 0.3);
    transition: background-color 0.2s, width 0.2s;
  }

  .dot.active {
    width: 18px;
    border-radius: 4px;
    background: var(--accent);
  }
</style>
