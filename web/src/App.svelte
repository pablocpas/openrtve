<script lang="ts">
  import { onMount } from 'svelte';
  import NavBar from './components/NavBar.svelte';
  import Toast from './components/Toast.svelte';
  import { RtveUrls } from './lib/domain/models';
  import { openIncomingLink } from './lib/ui/actions';
  import { isPlayerRoute, nav, routeToPath, screenKey, type Entry } from './lib/ui/nav.svelte';
  import { loadPlayerScreen } from './lib/ui/playerPreload';
  import Portada from './screens/Portada.svelte';

  /*
   * Solo la portada va en el arranque; el resto de pantallas son trozos aparte que se
   * piden al necesitarlas y, en cuanto el navegador queda libre, todas: así arrancar
   * cuesta menos y cambiar de pantalla sigue siendo instantáneo.
   */
  const lazyScreens = {
    search: () => import('./screens/Search.svelte'),
    explore: () => import('./screens/Explore.svelte'),
    settings: () => import('./screens/Settings.svelte'),
    module: () => import('./screens/Module.svelte'),
    program: () => import('./screens/Program.svelte'),
    video: () => import('./screens/Video.svelte'),
  };
  type LazyName = keyof typeof lazyScreens;
  type LazyScreens = { [K in LazyName]?: Awaited<ReturnType<(typeof lazyScreens)[K]>>['default'] };

  let screens = $state.raw<LazyScreens>({});
  const requested = new Set<string>();

  function need(name: string) {
    if (!(name in lazyScreens) || requested.has(name)) return;
    requested.add(name);
    const key = name as LazyName;
    lazyScreens[key]().then(
      (module) => (screens = { ...screens, [key]: module.default }),
      () => requested.delete(name),
    );
  }

  $effect(() => {
    for (const entry of nav.mounted) need(entry.route.name);
  });

  onMount(() => {
    const prefetch = () => Object.keys(lazyScreens).forEach(need);
    const idle = () => ('requestIdleCallback' in window ? requestIdleCallback(prefetch, { timeout: 5000 }) : setTimeout(prefetch, 2000));
    if (document.readyState === 'complete') idle();
    else window.addEventListener('load', idle, { once: true });
  });

  /*
   * Contenedor de pantallas. Las de la cadena visible siguen montadas (con su scroll
   * y su estado) y solo se muestra la activa; el reproductor se abre encima de la
   * pantalla desde la que se lanzó y se descarga aparte, solo cuando hace falta.
   */
  const activeKey = $derived(screenKey(nav.background));
  const playerEntry = $derived(nav.current && isPlayerRoute(nav.current.route) ? nav.current : null);
  const playerModule = $derived(playerEntry ? loadPlayerScreen() : null);

  const scrollers: Record<string, HTMLElement | undefined> = $state({});

  // Un enlace recibido (compartir hacia la PWA, `#/enlace?url=`) se resuelve y se sustituye.
  $effect(() => {
    const route = nav.current?.route;
    if (route?.name === 'link') void openIncomingLink(route.url, { replace: true });
  });

  // Título de la pestaña del navegador según la pantalla.
  $effect(() => {
    const route = nav.background.route;
    if (playerEntry) return;
    const title =
      route.name === 'program' || route.name === 'video' || route.name === 'portada' || route.name === 'module'
        ? route.title
        : route.name === 'search'
          ? 'Buscar'
          : route.name === 'explore'
            ? 'Explorar'
            : route.name === 'settings'
              ? 'Ajustes'
              : '';
    document.title = title ? `${title} · OpenRTVE` : 'OpenRTVE';
  });

  const keyFor = (entry: Entry) => `${screenKey(entry)}|${routeToPath(entry.route)}`;
</script>

<div class="app" class:playing={!!playerEntry}>
  <NavBar />
  <main class="screens">
    {#each nav.mounted as entry (keyFor(entry))}
      {@const key = screenKey(entry)}
      {@const active = key === activeKey && !playerEntry}
      <div class="screen" class:hidden={key !== activeKey} inert={key !== activeKey || !!playerEntry} bind:this={scrollers[key]}>
        {#if entry.route.name === 'home'}
          <Portada url={RtveUrls.TV_HOME} isRoot {active} scroller={scrollers[key]} />
        {:else if entry.route.name === 'search' && screens.search}
          <screens.search initialQuery={entry.route.query} {active} />
        {:else if entry.route.name === 'explore' && screens.explore}
          <screens.explore {active} scroller={scrollers[key]} />
        {:else if entry.route.name === 'settings' && screens.settings}
          <screens.settings />
        {:else if entry.route.name === 'portada'}
          <Portada url={entry.route.url} title={entry.route.title} {active} scroller={scrollers[key]} />
        {:else if entry.route.name === 'module' && screens.module}
          <screens.module
            url={entry.route.url}
            presentation={entry.route.presentation}
            moduleType={entry.route.moduleType}
            title={entry.route.title}
            {active}
          />
        {:else if entry.route.name === 'program' && screens.program}
          <screens.program id={entry.route.id} title={entry.route.title} scroller={scrollers[key]} />
        {:else if entry.route.name === 'video' && screens.video}
          <screens.video id={entry.route.id} title={entry.route.title} scroller={scrollers[key]} />
        {/if}
      </div>
    {/each}
  </main>
</div>

{#if playerEntry && playerModule}
  {#await playerModule}
    <div class="player-loading" aria-label="Cargando reproductor"><span></span></div>
  {:then { default: Player }}
    {#key routeToPath(playerEntry.route)}
      {#if playerEntry.route.name === 'play'}
        <Player kind="vod" id={playerEntry.route.id} restart={playerEntry.route.restart} />
      {:else if playerEntry.route.name === 'live'}
        <Player kind="live" assetId={playerEntry.route.assetId} drm={playerEntry.route.drm} title={playerEntry.route.title} />
      {/if}
    {/key}
  {/await}
{/if}

<Toast />

<style>
  .app {
    height: 100%;
  }

  .screens {
    position: absolute;
    inset: 0 0 calc(var(--bottom-nav-h) + env(safe-area-inset-bottom)) 0;
  }

  @media (min-width: 840px) {
    .screens {
      inset: 0 0 0 var(--rail-w);
    }
  }

  /* Las pantallas de fondo no se pintan, pero conservan su scroll y su estado. */
  .screen.hidden {
    visibility: hidden;
    content-visibility: hidden;
    pointer-events: none;
  }

  .player-loading {
    position: fixed;
    inset: 0;
    z-index: 200;
    display: grid;
    place-items: center;
    background: black;
  }

  .player-loading span {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    border: 4px solid rgb(255 255 255 / 0.2);
    border-top-color: var(--accent);
    animation: spin 0.9s linear infinite;
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
