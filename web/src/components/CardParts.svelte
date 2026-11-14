<script lang="ts" module>
  export type CardPart = 'progress' | 'liveDot' | 'liveBadge' | 'rank' | 'logo';
</script>

<script lang="ts">
  /** Piezas pequeñas sobre las tarjetas: progreso, "● Directo", ranking y logo del canal. */
  let { part, value = 0, src = '' }: { part: CardPart; value?: number; src?: string } = $props();
</script>

{#if part === 'progress'}
  <div class="progress" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={Math.round(value * 100)}>
    <div style:width="{Math.min(1, Math.max(0, value)) * 100}%"></div>
  </div>
{:else if part === 'liveDot'}
  <span class="live-dot">Directo</span>
{:else if part === 'liveBadge'}
  <span class="live-badge">DIRECTO</span>
{:else if part === 'rank'}
  <span class="rank">{value}</span>
{:else if part === 'logo'}
  <img class="logo" {src} alt="" loading="lazy" decoding="async" />
{/if}

<style>
  .progress {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 4px;
    background: rgb(255 255 255 / 0.35);
  }

  .progress > div {
    height: 100%;
    background: var(--accent);
  }

  .live-dot {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: var(--live);
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.02em;
  }

  .live-dot::before {
    content: '';
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--live);
  }

  .live-badge {
    position: absolute;
    top: 6px;
    left: 6px;
    padding: 2px 6px;
    border-radius: 4px;
    background: #d32f2f;
    color: white;
    font-size: 0.68rem;
    font-weight: 700;
  }

  .rank {
    position: absolute;
    left: 8px;
    bottom: 4px;
    padding: 0 10px;
    border-radius: var(--radius);
    background: rgb(0 0 0 / 0.55);
    color: white;
    font-size: clamp(1.8rem, 1.4rem + 1vw, 2.4rem);
    font-weight: 900;
    line-height: 1.2;
  }

  .logo {
    position: absolute;
    top: 8px;
    left: 8px;
    height: 22px;
    max-width: 64px;
    object-fit: contain;
    object-position: left center;
    filter: drop-shadow(0 1px 2px rgb(0 0 0 / 0.5));
  }
</style>
