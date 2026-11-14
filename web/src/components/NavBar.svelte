<script lang="ts">
  import { nav, type Tab } from '../lib/ui/nav.svelte';
  import Icon, { type IconName } from './Icon.svelte';

  /**
   * Navegación principal, como NavigationSuiteScaffold: barra inferior en el móvil y
   * carril lateral en pantallas anchas. Volver a pulsar la sección activa sube al
   * principio (o enfoca el buscador).
   */
  const items: { tab: Tab; label: string; icon: IconName }[] = [
    { tab: 'home', label: 'Inicio', icon: 'home' },
    { tab: 'search', label: 'Buscar', icon: 'search' },
    { tab: 'explore', label: 'Explorar', icon: 'menu' },
  ];

  const settingsOpen = $derived(nav.background.route.name === 'settings');
</script>

<nav class="navbar" aria-label="Secciones">
  <a class="brand" href="#/" onclick={(e) => { e.preventDefault(); nav.selectTab('home'); }} aria-label="OpenRTVE, inicio">
    <img src="./icons/icon-192.png" alt="" width="40" height="40" />
  </a>
  {#each items as item (item.tab)}
    {@const active = nav.tab === item.tab && !settingsOpen}
    <button class="item" class:active aria-current={active ? 'page' : undefined} onclick={() => nav.selectTab(item.tab)}>
      <span class="pill"><Icon name={item.icon} /></span>
      <span class="label">{item.label}</span>
    </button>
  {/each}
  <button
    class="item settings"
    class:active={settingsOpen}
    aria-current={settingsOpen ? 'page' : undefined}
    onclick={() => !settingsOpen && nav.navigate({ name: 'settings' })}
  >
    <span class="pill"><Icon name="settings" /></span>
    <span class="label">Ajustes</span>
  </button>
</nav>

<style>
  .navbar {
    position: fixed;
    z-index: 50;
    left: 0;
    right: 0;
    bottom: 0;
    height: calc(var(--bottom-nav-h) + env(safe-area-inset-bottom));
    padding-bottom: env(safe-area-inset-bottom);
    display: flex;
    justify-content: space-around;
    align-items: center;
    background: rgb(20 22 32 / 0.96);
    backdrop-filter: blur(16px);
    border-top: 1px solid rgb(255 255 255 / 0.04);
  }

  .brand {
    display: none;
  }

  .item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    color: var(--muted);
    font-size: 0.75rem;
    font-weight: 600;
    padding: 6px 0;
  }

  .pill {
    display: grid;
    place-items: center;
    width: 60px;
    height: 30px;
    border-radius: 16px;
    transition: background-color 0.2s;
  }

  .item:hover .pill {
    background: rgb(255 255 255 / 0.06);
  }

  .item.active {
    color: var(--text);
  }

  .item.active .pill {
    background: rgb(255 106 71 / 0.22);
    color: var(--accent);
  }

  @media (min-width: 840px) {
    .navbar {
      top: 0;
      right: auto;
      width: var(--rail-w);
      height: auto;
      padding: 16px 0 24px;
      flex-direction: column;
      justify-content: flex-start;
      gap: 12px;
      border-top: 0;
      border-right: 1px solid rgb(255 255 255 / 0.04);
      background: var(--bg);
      backdrop-filter: none;
    }

    .brand {
      display: block;
      margin-bottom: 20px;
    }

    .brand img {
      border-radius: 10px;
    }

    .item {
      flex: none;
      width: 100%;
    }

    .pill {
      width: 56px;
      height: 32px;
    }

    .settings {
      display: flex;
      margin-top: auto;
    }
  }
</style>
