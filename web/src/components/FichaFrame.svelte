<script lang="ts">
  import type { Snippet } from 'svelte';
  import { share } from '../lib/ui/actions';
  import { nav } from '../lib/ui/nav.svelte';
  import { TAB_TITLES, type ProgramTab } from '../lib/ui/sections';
  import Icon from './Icon.svelte';

  /**
   * Estructura de una ficha: barra flotante (transparente sobre la imagen, opaca y con
   * el título al desplazar), la cabecera y, si hay más de una colección, pestañas que
   * se quedan pegadas bajo la barra. Con una sola, va su título.
   */
  let {
    title,
    shareUrl = null,
    tabs,
    current,
    onselect,
    scroller,
    header,
    children,
  }: {
    title: string;
    shareUrl?: string | null;
    tabs: ProgramTab[];
    current: ProgramTab | null;
    onselect: (tab: ProgramTab) => void;
    scroller: HTMLElement | undefined;
    header: Snippet;
    children: Snippet;
  } = $props();

  let collapsed = $state(false);
  let headerEl = $state<HTMLElement>();

  $effect(() => {
    if (!scroller) return;
    const el = scroller;
    const onScroll = () => {
      const limit = (headerEl?.offsetHeight ?? 400) - 160;
      collapsed = el.scrollTop > Math.max(80, limit);
    };
    onScroll();
    el.addEventListener('scroll', onScroll, { passive: true });
    return () => el.removeEventListener('scroll', onScroll);
  });

  function selectTab(tab: ProgramTab) {
    onselect(tab);
    // Al cambiar de pestaña con la cabecera ya fuera, se vuelve al principio de la lista.
    if (scroller && headerEl && scroller.scrollTop > headerEl.offsetHeight - 56) {
      scroller.scrollTo({ top: headerEl.offsetHeight - 56 });
    }
  }

  function onTabKey(event: KeyboardEvent) {
    if (!current || (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft')) return;
    const i = tabs.indexOf(current);
    const next = tabs[(i + (event.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length];
    selectTab(next);
    (event.currentTarget as HTMLElement).querySelector<HTMLElement>(`[data-tab="${next}"]`)?.focus();
    event.preventDefault();
  }
</script>

<div class="bar" class:collapsed>
  <button class="icon-btn veil" onclick={() => nav.back()} aria-label="Atrás"><Icon name="back" /></button>
  <span class="bar-title" aria-hidden={!collapsed}>{title}</span>
  {#if shareUrl}
    <button class="icon-btn veil" onclick={() => share(title, shareUrl)} aria-label="Compartir"><Icon name="share" /></button>
  {/if}
</div>

<div bind:this={headerEl}>{@render header()}</div>

{#if tabs.length > 1}
  <div class="tabs" role="tablist" tabindex="-1" onkeydown={onTabKey}>
    {#each tabs as tab (tab)}
      <button
        role="tab"
        data-tab={tab}
        aria-selected={tab === current}
        tabindex={tab === current ? 0 : -1}
        class:active={tab === current}
        onclick={() => selectTab(tab)}>{TAB_TITLES[tab]}</button
      >
    {/each}
  </div>
{:else if current}
  <h2 class="section-title single">{TAB_TITLES[current]}</h2>
{/if}

<div class="content" role={tabs.length > 1 ? 'tabpanel' : undefined}>
  {@render children()}
</div>

<style>
  .bar {
    position: sticky;
    top: 0;
    z-index: 20;
    display: flex;
    align-items: center;
    gap: 8px;
    height: 60px;
    margin-bottom: -60px;
    padding: 0 calc(var(--gutter) - 10px);
    transition: background-color 0.25s;
  }

  .bar.collapsed {
    background: rgb(9 10 15 / 0.97);
  }

  .veil {
    background: rgb(0 0 0 / 0.35);
    color: white;
  }

  .collapsed .veil {
    background: transparent;
    color: var(--text);
  }

  .veil:hover {
    background: rgb(0 0 0 / 0.55);
  }

  .collapsed .veil:hover {
    background: rgb(255 255 255 / 0.08);
  }

  .bar-title {
    flex: 1;
    min-width: 0;
    font-size: 1.2rem;
    font-weight: 700;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    opacity: 0;
    transition: opacity 0.2s;
  }

  .collapsed .bar-title {
    opacity: 1;
  }

  .tabs {
    position: sticky;
    top: 60px;
    z-index: 15;
    display: flex;
    gap: 4px;
    overflow-x: auto;
    scrollbar-width: none;
    padding: 0 var(--gutter);
    background: var(--bg);
    border-bottom: 1px solid var(--outline);
  }

  .tabs::-webkit-scrollbar {
    display: none;
  }

  .tabs button {
    position: relative;
    flex: none;
    height: 48px;
    padding: 0 18px;
    color: var(--muted);
    font-weight: 600;
    font-size: 0.95rem;
  }

  .tabs button:hover {
    color: var(--text);
  }

  .tabs button.active {
    color: var(--accent);
  }

  .tabs button.active::after {
    content: '';
    position: absolute;
    left: 12px;
    right: 12px;
    bottom: 0;
    height: 3px;
    border-radius: 3px 3px 0 0;
    background: var(--accent);
  }

  .single {
    padding: 8px var(--gutter) 4px;
  }

  .content {
    padding-top: 8px;
    padding-bottom: 40px;
    min-height: 50vh;
  }
</style>
