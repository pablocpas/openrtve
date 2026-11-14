<script lang="ts">
  import type { Snippet } from 'svelte';
  import { nav } from '../lib/ui/nav.svelte';
  import Icon from './Icon.svelte';

  /** Barra superior común: flecha atrás, título y acciones. Se queda pegada al desplazar. */
  let { title, back = true, actions }: { title: string; back?: boolean; actions?: Snippet } = $props();
</script>

<header class="topbar">
  {#if back}<button class="icon-btn" onclick={() => nav.back()} aria-label="Atrás"><Icon name="back" /></button>{/if}
  <h1 class:padded={!back}>{title}</h1>
  {@render actions?.()}
</header>

<style>
  .topbar {
    position: sticky;
    top: 0;
    z-index: 10;
    display: flex;
    align-items: center;
    gap: 8px;
    height: 64px;
    padding: 0 calc(var(--gutter) - 8px);
    background: rgb(9 10 15 / 0.97);
  }

  h1 {
    flex: 1;
    min-width: 0;
    font-size: 1.3rem;
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  h1.padded {
    padding-left: 8px;
  }
</style>
