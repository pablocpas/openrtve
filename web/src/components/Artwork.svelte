<script lang="ts">
  import { untrack, type Snippet } from 'svelte';
  import { artworkCandidates, toSrcset } from '../lib/data/imageSizing';
  import { nearScreen } from '../lib/ui/nearViewport';

  /**
   * Imagen con proporción fija sobre un fondo neutro. Se pide al servicio de RTVE al
   * tamaño en que se pinta ([width] en px CSS, con variantes para pantallas de alta
   * densidad); si la versión redimensionada fallase, se pide el original, y si
   * tampoco, [fallback]. El tamaño es una mejora, nunca el motivo de una tarjeta vacía.
   */
  let {
    url,
    ratio,
    width,
    fallback = null,
    eager = false,
    rounded = true,
    class: className = '',
    children,
  }: {
    url: string | null | undefined;
    /** Ancho / alto. */
    ratio: number;
    /** Ancho aproximado en que se pinta, en px CSS. */
    width: number;
    fallback?: string | null;
    eager?: boolean;
    rounded?: boolean;
    class?: string;
    children?: Snippet;
  } = $props();

  // Diferida hasta que se acerca a la pantalla (una pantalla antes de verse): entonces se
  // pide ya, sin esperar a que el navegador decida, que en Firefox es al asomar.
  let near = $state(untrack(() => eager));

  // 0 = versión redimensionada, 1 = original, 2 = respaldo, 3 = nada.
  let attempt = $state(0);
  let loaded = $state(false);

  // Una tarjeta reutilizada con otra imagen vuelve a empezar por la redimensionada.
  let shownFor = untrack(() => `${url}|${fallback}`);
  $effect.pre(() => {
    const key = `${url}|${fallback}`;
    if (key !== shownFor) {
      shownFor = key;
      attempt = 0;
      loaded = false;
    }
  });

  const candidates = $derived(artworkCandidates(url, ratio, width));

  const src = $derived.by(() => {
    if (attempt === 0 && candidates.length) return candidates[0].src;
    if (attempt <= 1 && url) return url;
    if (attempt <= 2 && fallback) return fallback;
    return null;
  });

  const srcset = $derived(attempt === 0 && candidates.length > 1 ? toSrcset(candidates) : undefined);

  function onerror() {
    loaded = false;
    if (attempt === 0 && candidates.length && url) attempt = 1;
    else if (attempt <= 1 && fallback && fallback !== url) attempt = 2;
    else attempt = 3;
  }
</script>

<div class="artwork {className}" class:rounded style:aspect-ratio={ratio} use:nearScreen={() => (near = true)}>
  {#if src}
    <img
      {src}
      {srcset}
      sizes={srcset ? `${width}px` : undefined}
      alt=""
      loading={near ? 'eager' : 'lazy'}
      fetchpriority={eager ? 'high' : 'auto'}
      decoding="async"
      class:loaded
      class:instant={eager}
      onload={() => (loaded = true)}
      {onerror}
      draggable="false"
    />
  {/if}
  {@render children?.()}
</div>

<style>
  .artwork {
    position: relative;
    width: 100%;
    overflow: hidden;
    background: var(--surface);
    isolation: isolate;
  }

  .rounded {
    border-radius: var(--radius);
  }

  img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    /* Si hay que recortar, mejor perder los pies que la cara. */
    object-position: top center;
    opacity: 0;
    transition: opacity 0.25s ease;
    z-index: -1;
  }

  img.loaded {
    opacity: 1;
  }

  /* La imagen principal (el LCP) se pinta según llega, sin fundido que la retrase. */
  img.instant {
    opacity: 1;
    transition: none;
  }
</style>
