<script lang="ts">
  import Artwork from './Artwork.svelte';

  /**
   * Tarjeta 16:9 con el título sobre la imagen: categorías de Explorar y enlaces de
   * portada. RTVE deja a veces imágenes rotas: primero la propia, luego [fallback] y,
   * si tampoco hay, un fondo con el color de la marca.
   */
  let {
    title,
    imageUrl,
    fallback = null,
    onclick,
    width = 340,
  }: { title: string; imageUrl: string | null; fallback?: string | null; onclick: () => void; width?: number } = $props();
</script>

<button class="tile" {onclick}>
  <Artwork url={imageUrl ?? fallback} fallback={imageUrl ? fallback : null} ratio={16 / 9} {width} class="brand">
    <span class="scrim"></span>
    <span class="title">{title}</span>
  </Artwork>
</button>

<style>
  .tile {
    display: block;
    width: 100%;
    border-radius: var(--radius);
    text-align: left;
  }

  .tile :global(.brand) {
    background: linear-gradient(135deg, var(--accent), #5a2519);
    transition: transform 0.2s ease, box-shadow 0.2s ease;
  }

  @media (hover: hover) {
    .tile:hover :global(.brand) {
      transform: translateY(-2px);
      box-shadow: 0 0 0 2px rgb(255 255 255 / 0.85), 0 10px 24px rgb(0 0 0 / 0.45);
    }
  }

  .scrim {
    position: absolute;
    inset: 0;
    background: linear-gradient(to bottom, transparent 30%, rgb(0 0 0 / 0.8));
  }

  .title {
    position: absolute;
    left: 10px;
    right: 10px;
    bottom: 10px;
    color: white;
    font-weight: 700;
    font-size: 0.95rem;
    line-height: 1.25;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
</style>
