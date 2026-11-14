import type { CatalogItem } from '../domain/models';
import { slim } from './watchHistory.svelte';

/*
 * "Mi lista" local, sin cuenta (Favorites.kt): programas, películas y vídeos que se
 * guardan para después, lo último añadido primero, en `localStorage`. Los directos
 * no se guardan: lo que se emite ahora no es lo que habrá después.
 */

export interface FavoriteEntry {
  item: CatalogItem;
  addedAt: number;
}

const KEY = 'openrtve.favorites.v1';
const MAX_ENTRIES = 200;

export const canAddToMyList = (item: CatalogItem) => item.kind === 'PROGRAM' || item.kind === 'VIDEO';

function load(): FavoriteEntry[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((e) => e && typeof e.item?.id === 'string' && typeof e.item?.title === 'string' && canAddToMyList(e.item))
      .map((e) => ({ item: slim(e.item), addedAt: Number(e.addedAt) || 0 }))
      .sort((a, b) => b.addedAt - a.addedAt);
  } catch {
    return [];
  }
}

class Favorites {
  entries = $state<FavoriteEntry[]>(load());

  items = $derived(this.entries.map((e) => e.item));

  ids = $derived(new Set(this.entries.map((e) => e.item.id)));

  constructor() {
    // Otra pestaña con la app abierta también escribe: se adopta su versión.
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', (e) => {
        if (e.key === KEY) this.entries = load();
      });
    }
  }

  contains(itemId: string): boolean {
    return this.ids.has(itemId);
  }

  /** Añade o quita; devuelve si queda en la lista. */
  toggle(item: CatalogItem): boolean {
    if (this.contains(item.id)) {
      this.remove(item.id);
      return false;
    }
    if (!canAddToMyList(item)) return false;
    this.#save([{ item: slim(item), addedAt: Date.now() }, ...this.entries]);
    return true;
  }

  /** Quita el título y lo devuelve, para poder deshacerlo con [restore]. */
  remove(itemId: string): FavoriteEntry | undefined {
    const removed = this.entries.find((e) => e.item.id === itemId);
    if (removed) this.#save(this.entries.filter((e) => e.item.id !== itemId));
    return removed;
  }

  /** Vuelve a poner un título quitado en su sitio. */
  restore(entry: FavoriteEntry) {
    this.#save([...this.entries.filter((e) => e.item.id !== entry.item.id), entry]);
  }

  #save(entries: FavoriteEntry[]) {
    const seen = new Set<string>();
    this.entries = entries
      .filter((e) => !seen.has(e.item.id) && seen.add(e.item.id))
      .sort((a, b) => b.addedAt - a.addedAt)
      .slice(0, MAX_ENTRIES);
    // Pocas escrituras (una por pulsación): se guarda al momento.
    try {
      localStorage.setItem(KEY, JSON.stringify(this.entries));
    } catch {
      // Sin espacio o sin almacenamiento: la lista vive mientras dure la sesión.
    }
  }
}

export const favorites = new Favorites();
