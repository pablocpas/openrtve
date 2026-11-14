import type { CatalogItem } from '../domain/models';

/*
 * Navegación con el historial del navegador (Navigation.kt adaptado a la web).
 * Cada entrada tiene una clave en `history.state`; las pantallas de la cadena
 * visible se mantienen montadas (con su scroll y su estado) y las que se dejan
 * atrás se liberan, como los ViewModelStore de cada entrada en Android.
 */

export type Tab = 'home' | 'search' | 'explore' | 'mylist';

export type Route =
  | { name: 'home' }
  | { name: 'search'; query?: string }
  | { name: 'explore' }
  | { name: 'mylist' }
  | { name: 'settings' }
  | { name: 'portada'; url: string; title: string }
  | { name: 'module'; url: string; presentation: string | null; moduleType: string | null; title: string }
  | { name: 'program'; id: string; title: string }
  | { name: 'video'; id: string; title: string }
  | { name: 'play'; id: string; restart: boolean }
  | { name: 'live'; assetId: string; drm: boolean; title: string }
  | { name: 'link'; url: string };

export interface Entry {
  key: string;
  route: Route;
  tab: Tab;
}

export const ROOT_TABS: Record<Tab, Route> = {
  home: { name: 'home' },
  search: { name: 'search' },
  explore: { name: 'explore' },
  mylist: { name: 'mylist' },
};

const rootTab = (route: Route): Tab | null =>
  route.name === 'home' || route.name === 'search' || route.name === 'explore' || route.name === 'mylist' ? route.name : null;

export const isPlayerRoute = (route: Route) => route.name === 'play' || route.name === 'live';

/** Clave de la instancia que pinta la entrada: las raíces de pestaña son únicas y viven siempre. */
export const screenKey = (entry: Entry) => {
  const tab = rootTab(entry.route);
  return tab ? `root:${tab}` : entry.key;
};

// ---- Rutas <-> URL ----

export function routeToPath(route: Route): string {
  const q = (params: Record<string, string | null | undefined | false>) => {
    const search = new URLSearchParams();
    for (const [k, v] of Object.entries(params)) if (v) search.set(k, v);
    const s = search.toString();
    return s ? `?${s}` : '';
  };
  const id = encodeURIComponent;
  switch (route.name) {
    case 'home': return '/';
    case 'search': return `/buscar${q({ q: route.query })}`;
    case 'explore': return '/explorar';
    case 'mylist': return '/mi-lista';
    case 'settings': return '/ajustes';
    case 'portada': return `/portada${q({ u: route.url, t: route.title })}`;
    case 'module': return `/fila${q({ u: route.url, p: route.presentation, m: route.moduleType, t: route.title })}`;
    case 'program': return `/programa/${id(route.id)}${q({ t: route.title })}`;
    case 'video': return `/video/${id(route.id)}${q({ t: route.title })}`;
    case 'play': return `/ver/${id(route.id)}${q({ desde: route.restart && 'inicio' })}`;
    case 'live': return `/directo/${id(route.assetId)}${q({ drm: route.drm && '1', t: route.title })}`;
    case 'link': return `/enlace${q({ url: route.url })}`;
  }
}

export function parsePath(path: string): Route {
  const [rawPath, rawQuery = ''] = path.split('?');
  const query = new URLSearchParams(rawQuery);
  const segments = rawPath.split('/').filter(Boolean).map((s) => {
    try {
      return decodeURIComponent(s);
    } catch {
      return s;
    }
  });
  const get = (k: string) => query.get(k) ?? '';
  switch (segments[0]) {
    case undefined: return { name: 'home' };
    case 'buscar': return { name: 'search', query: get('q') || undefined };
    case 'explorar': return { name: 'explore' };
    case 'mi-lista': return { name: 'mylist' };
    case 'ajustes': return { name: 'settings' };
    case 'portada': return get('u') ? { name: 'portada', url: get('u'), title: get('t') } : { name: 'home' };
    case 'fila':
      return get('u')
        ? { name: 'module', url: get('u'), presentation: query.get('p'), moduleType: query.get('m'), title: get('t') }
        : { name: 'home' };
    case 'programa': return segments[1] ? { name: 'program', id: segments[1], title: get('t') } : { name: 'home' };
    case 'video': return segments[1] ? { name: 'video', id: segments[1], title: get('t') } : { name: 'home' };
    case 'ver': return segments[1] ? { name: 'play', id: segments[1], restart: get('desde') === 'inicio' } : { name: 'home' };
    case 'directo':
      return segments[1] ? { name: 'live', assetId: segments[1], drm: get('drm') === '1', title: get('t') } : { name: 'home' };
    case 'enlace': return get('url') ? { name: 'link', url: get('url') } : { name: 'home' };
    default: return { name: 'home' };
  }
}

const currentPath = () => decodeHash(location.hash);
const decodeHash = (hash: string) => (hash.startsWith('#') ? hash.slice(1) : hash) || '/';

/** Items ya conocidos al navegar: la pantalla de destino los pinta sin esperar a la red. */
const itemMemory = new Map<string, CatalogItem>();
export const rememberItem = (key: string, item: CatalogItem) => {
  itemMemory.set(key, item);
  if (itemMemory.size > 300) itemMemory.delete(itemMemory.keys().next().value!);
};
export const recallItem = (key: string) => itemMemory.get(key);

/** Cuántas pantallas no raíz se mantienen montadas hacia atrás. */
const MAX_ALIVE = 8;

let counter = 0;
const newKey = () => `${Date.now().toString(36)}-${(counter++).toString(36)}`;

class Navigation {
  entries = $state<Entry[]>([]);
  index = $state(0);
  /** Peticiones a la raíz al volver a pulsar su pestaña activa (subir o enfocar el buscador). */
  reselect = $state<Record<Tab, number>>({ home: 0, search: 0, explore: 0, mylist: 0 });

  current = $derived(this.entries[this.index]);

  /** La pantalla de fondo: la entrada actual o, bajo el reproductor, la anterior que no lo es. */
  background = $derived.by(() => {
    for (let i = this.index; i >= 0; i--) if (!isPlayerRoute(this.entries[i].route)) return this.entries[i];
    return { key: 'root:home', route: ROOT_TABS.home, tab: 'home' as Tab };
  });

  tab = $derived(this.background.tab);

  /** Pantallas montadas: las raíces visitadas y las últimas de la cadena hasta la actual. */
  mounted = $derived.by(() => {
    const chain = this.entries.slice(0, this.index + 1).filter((e) => !isPlayerRoute(e.route));
    const roots = new Map<string, Entry>();
    const others: Entry[] = [];
    for (const entry of chain) {
      const key = screenKey(entry);
      if (key.startsWith('root:')) roots.set(key, entry);
      else others.push(entry);
    }
    for (const tab of this.#visitedRoots) {
      const key = `root:${tab}`;
      if (!roots.has(key)) roots.set(key, { key, route: ROOT_TABS[tab], tab });
    }
    return [...roots.values(), ...others.slice(-MAX_ALIVE)];
  });

  #visitedRoots = new Set<Tab>();

  init() {
    const route = parsePath(currentPath());
    const key = (history.state as { key?: string } | null)?.key ?? newKey();
    const tab = rootTab(route) ?? 'home';
    history.replaceState({ key, tab }, '');
    this.entries = [{ key, route, tab }];
    this.index = 0;
    this.#visit(route);
    window.addEventListener('popstate', (event) => this.#onPop(event));
  }

  navigate(route: Route, { replace = false }: { replace?: boolean } = {}) {
    const tab = rootTab(route) ?? this.current?.tab ?? 'home';
    const entry: Entry = { key: newKey(), route, tab };
    const url = `#${routeToPath(route)}`;
    if (replace) {
      history.replaceState({ key: entry.key, tab }, '', url);
      this.entries = [...this.entries.slice(0, this.index), entry];
    } else {
      history.pushState({ key: entry.key, tab }, '', url);
      this.entries = [...this.entries.slice(0, this.index + 1), entry];
      this.index = this.entries.length - 1;
    }
    this.#visit(route);
  }

  /** Pestañas: la activa vuelve a su raíz (o pide subir si ya está en ella); otra, se abre. */
  selectTab(tab: Tab) {
    const current = this.background;
    if (current.tab === tab && rootTab(current.route) === tab && !isPlayerRoute(this.current.route)) {
      this.reselect = { ...this.reselect, [tab]: this.reselect[tab] + 1 };
      return;
    }
    this.navigate(ROOT_TABS[tab]);
  }

  back() {
    if (this.index > 0) history.back();
    else this.navigate(ROOT_TABS.home, { replace: true });
  }

  #visit(route: Route) {
    const tab = rootTab(route);
    if (tab) this.#visitedRoots.add(tab);
  }

  #onPop(event: PopStateEvent) {
    const state = event.state as { key?: string; tab?: Tab } | null;
    const route = parsePath(currentPath());
    const found = state?.key ? this.entries.findIndex((e) => e.key === state.key) : -1;
    if (found >= 0 && sameRoute(this.entries[found].route, route)) {
      // Atrás libera lo que queda delante; adelante, si existe, se reconstruye.
      this.entries = this.entries.slice(0, found + 1);
      this.index = found;
    } else {
      const key = state?.key ?? newKey();
      const tab = rootTab(route) ?? state?.tab ?? this.current?.tab ?? 'home';
      if (!state?.key) history.replaceState({ key, tab }, '');
      this.entries = [...this.entries.slice(0, this.index + 1), { key, route, tab }];
      this.index = this.entries.length - 1;
      this.#visit(route);
    }
  }
}

const sameRoute = (a: Route, b: Route) => routeToPath(a) === routeToPath(b);

export const nav = new Navigation();
