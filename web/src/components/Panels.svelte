<script lang="ts">
  /** Estados de una zona de contenido: cargando, vacío o con error (con su reintento). */
  let {
    kind,
    message = '',
    onretry,
    compact = false,
  }: { kind: 'loading' | 'message'; message?: string; onretry?: () => void; compact?: boolean } = $props();
</script>

<div class="panel" class:compact role={kind === 'loading' ? 'status' : undefined}>
  {#if kind === 'loading'}
    <span class="spinner" aria-label="Cargando…"></span>
  {:else}
    <p>{message}</p>
    {#if onretry}<button class="btn btn-tonal" onclick={onretry}>Reintentar</button>{/if}
  {/if}
</div>

<style>
  .panel {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 14px;
    min-height: 40vh;
    padding: 24px;
    text-align: center;
    color: var(--muted);
  }

  .compact {
    min-height: 0;
    padding: 16px;
  }

  .spinner {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    border: 4px solid rgb(255 106 71 / 0.2);
    border-top-color: var(--accent);
    animation: spin 0.9s linear infinite;
  }

  .compact .spinner {
    width: 28px;
    height: 28px;
    border-width: 3px;
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
