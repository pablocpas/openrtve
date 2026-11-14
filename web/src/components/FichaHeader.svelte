<script lang="ts">
  import type { Snippet } from 'svelte';
  import type { Facts } from '../lib/ui/sections';
  import Artwork from './Artwork.svelte';
  import CardParts from './CardParts.svelte';
  import Icon from './Icon.svelte';

  /**
   * Cabecera de ficha (Ficha.kt): imagen a sangre con el título encima y, debajo,
   * subtítulo, línea de datos, reclamo, acciones, progreso, sinopsis y ficha técnica.
   * Es lo que hace falta para decidir y darle al play, así que nunca va en una
   * pestaña. En pantallas anchas la información se superpone a la imagen, a la
   * izquierda, como en Google TV.
   */
  let {
    backdropUrl,
    title,
    subtitle = null,
    onsubtitle,
    subtitleLinkText = null,
    meta = null,
    claim = null,
    synopsis = [],
    facts = null,
    progress = null,
    actions,
  }: {
    backdropUrl: string | null;
    title: string;
    subtitle?: string | null;
    /** Si el subtítulo es el programa o la colección, lleva a él. */
    onsubtitle?: () => void;
    subtitleLinkText?: string | null;
    meta?: string | null;
    claim?: string | null;
    synopsis?: string[];
    facts?: Facts | null;
    progress?: number | null;
    actions?: Snippet;
  } = $props();

  let viewportWidth = $state(typeof window !== 'undefined' ? window.innerWidth : 1280);
  let expanded = $state(false);
  let truncated = $state(false);
  let about = $state<HTMLElement>();

  const rows = $derived(facts?.rows ?? []);
  const visibleRows = $derived(expanded ? rows : rows.slice(0, facts?.compactRowCount ?? 0));

  // Plegado se ven tres líneas de sinopsis y una de reparto y de dirección; "más" lo despliega todo.
  $effect(() => {
    void synopsis;
    void visibleRows;
    if (!about || expanded) return;
    const check = () => {
      truncated = [...about!.querySelectorAll<HTMLElement>('.clamp')].some((el) => el.scrollHeight > el.clientHeight + 1);
    };
    check();
    const observer = new ResizeObserver(check);
    observer.observe(about);
    return () => observer.disconnect();
  });
</script>

<svelte:window bind:innerWidth={viewportWidth} />

<header class="ficha-header" class:no-image={!backdropUrl}>
  {#if backdropUrl}
    <div class="backdrop">
      <Artwork url={backdropUrl} ratio={16 / 9} width={Math.min(1920, viewportWidth)} rounded={false} eager />
      <div class="shade"></div>
    </div>
  {/if}
  <div class="info">
    <h1>{title}</h1>
    {#if subtitle?.trim()}
      {#if onsubtitle}
        <button class="program-link" onclick={onsubtitle} aria-label="Ir a {subtitle}">
          <span>{subtitleLinkText ?? subtitle}</span><Icon name="chevronRight" />
        </button>
      {:else}
        <p class="subtitle">{subtitle}</p>
      {/if}
    {/if}
    {#if meta?.trim()}<p class="meta">{meta}</p>{/if}
    {#if claim?.trim()}<p class="claim">{claim}</p>{/if}
    {#if actions}<div class="actions">{@render actions()}</div>{/if}
    {#if progress != null}<div class="progress"><CardParts part="progress" value={progress} /></div>{/if}
    {#if synopsis.length || rows.length}
      <div class="about" class:expanded bind:this={about}>
        {#if synopsis.length}
          <div class="synopsis clamp">
            {#each synopsis as paragraph, i (i)}<p>{paragraph}</p>{/each}
          </div>
        {/if}
        {#each visibleRows as row (row.label)}
          <p class="fact clamp"><strong>{row.label}:</strong> {row.value}</p>
        {/each}
        {#if expanded || truncated || rows.length > visibleRows.length}
          <button class="more" onclick={() => (expanded = !expanded)} aria-expanded={expanded}>{expanded ? 'menos' : 'más'}</button>
        {/if}
      </div>
    {/if}
  </div>
</header>

<style>
  .ficha-header {
    position: relative;
    --backdrop-h: min(56.25vw, 72vh);
  }

  .backdrop {
    position: absolute;
    inset: 0 0 auto 0;
    height: var(--backdrop-h);
    overflow: hidden;
  }

  .backdrop :global(.artwork) {
    position: absolute;
    inset: 0;
    height: 100%;
    aspect-ratio: auto !important;
  }

  .shade {
    position: absolute;
    inset: 0;
    background: linear-gradient(to bottom, rgb(0 0 0 / 0.25) 0%, transparent 25%, transparent 45%, var(--bg) 100%);
  }

  .info {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    padding: calc(var(--backdrop-h) - 72px) var(--gutter) 8px;
  }

  .no-image .info {
    padding-top: 72px;
  }

  @media (min-width: 900px) {
    .ficha-header {
      --backdrop-h: min(56.25vw, 78vh);
    }

    /* El borde izquierdo de la imagen se funde con el fondo: sin máscara asomaba una línea de 1 px. */
    .backdrop {
      left: 18%;
      mask-image: linear-gradient(to right, transparent 0, #000 22%);
    }

    .shade {
      background:
        linear-gradient(to right, var(--bg) 0%, rgb(9 10 15 / 0.85) 18%, rgb(9 10 15 / 0.2) 50%, transparent 70%),
        linear-gradient(to bottom, rgb(0 0 0 / 0.3) 0%, transparent 20%, transparent 60%, var(--bg) 100%);
    }

    .info {
      max-width: min(760px, 60vw);
      min-height: var(--backdrop-h);
      justify-content: flex-end;
      padding-top: 96px;
      padding-bottom: 32px;
    }
  }

  h1 {
    font-size: clamp(1.75rem, 1.2rem + 2.4vw, 3.4rem);
    font-weight: 800;
    line-height: 1.08;
    letter-spacing: -0.015em;
    text-shadow: 0 2px 16px rgb(0 0 0 / 0.45);
    display: -webkit-box;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .subtitle {
    margin-top: 4px;
    font-size: 1.05rem;
    color: var(--muted);
  }

  .program-link {
    display: inline-flex;
    align-items: center;
    min-height: 40px;
    margin-top: 2px;
    color: var(--accent);
    font-size: 1.05rem;
    font-weight: 500;
    text-align: left;
  }

  .program-link:hover span {
    text-decoration: underline;
    text-underline-offset: 3px;
  }

  .meta {
    margin-top: 6px;
    font-size: 0.9rem;
    color: var(--muted);
  }

  .claim {
    margin-top: 8px;
    color: var(--accent);
    font-weight: 500;
    font-size: 0.95rem;
  }

  .actions {
    margin-top: 16px;
    width: 100%;
  }

  .progress {
    position: relative;
    width: 100%;
    max-width: 480px;
    height: 4px;
    margin-top: 10px;
    border-radius: 2px;
    overflow: hidden;
  }

  /* Todo el ancho disponible (sin él, una línea larga lo ensanchaba más allá de la pantalla del móvil). */
  .about {
    width: 100%;
    margin-top: 16px;
    color: var(--muted);
    font-size: 0.95rem;
    line-height: 1.5;
    max-width: 72ch;
  }

  .synopsis p + p {
    margin-top: 0.6em;
  }

  .about:not(.expanded) .synopsis {
    display: -webkit-box;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .fact {
    margin-top: 6px;
  }

  .about:not(.expanded) .fact {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .fact strong {
    color: var(--text);
    font-weight: 500;
  }

  .more {
    min-height: 40px;
    color: var(--accent);
    font-weight: 600;
    font-size: 0.9rem;
  }
</style>
