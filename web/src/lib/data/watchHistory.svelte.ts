import type { CatalogItem, ContentKind } from '../domain/models';

/*
 * "Seguir viendo" local, sin cuenta (WatchHistory.kt): las últimas reproducciones y
 * su posición, en `localStorage`. El estado es reactivo; el guardado se agrupa para
 * que la escritura cada pocos segundos durante la reproducción no cueste nada.
 */

export interface WatchEntry {
  item: CatalogItem;
  positionMs: number;
  durationMs: number;
  updatedAt: number;
  /** Visto hasta el final: fuera de "Seguir viendo", pero sirve para saber cuál es el siguiente. */
  finished: boolean;
}

const KEY = 'openrtve.history.v1';
const MAX_ENTRIES = 60;
const MAX_AGE_MS = 60 * 24 * 60 * 60 * 1000;
export const MIN_POSITION_MS = 30_000;
const FINISHED_FRACTION = 0.95;

export const entryProgress = (e: WatchEntry) => (e.durationMs > 0 ? Math.min(1, Math.max(0, e.positionMs / e.durationMs)) : 0);
export const isInProgress = (e: WatchEntry) => !e.finished && e.positionMs >= MIN_POSITION_MS;

/** Solo lo que hace falta para volver a abrir y reproducir el item. */
const STORED_KEYS = [
  'id', 'playbackId', 'assetId', 'title', 'subtitle', 'imageUrl', 'posterUrl', 'kind', 'programId', 'programTypeId',
  'programType', 'mediaTypeId', 'mediaTypeName', 'mediaSubtypeId', 'mediaSubtypeName', 'seasonId', 'seasonTitle',
  'episode', 'drm', 'paid', 'durationMs', 'publicationDate',
] as const;

function slim(item: CatalogItem): CatalogItem {
  const out: Record<string, unknown> = {};
  for (const key of STORED_KEYS) if (item[key] != null) out[key] = item[key];
  return { allowedInCountry: null, loginRequired: false, paid: false, drm: false, subtitle: null, imageUrl: null, playbackId: null, assetId: null, ...out } as CatalogItem;
}

function load(): WatchEntry[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((e) => e && typeof e.item?.id === 'string' && typeof e.item?.title === 'string').map((e) => ({
      item: { ...slim(e.item), kind: (['PROGRAM', 'VIDEO', 'LIVE'].includes(e.item.kind) ? e.item.kind : 'VIDEO') as ContentKind },
      positionMs: Number(e.positionMs) || 0,
      durationMs: Number(e.durationMs) || 0,
      updatedAt: Number(e.updatedAt) || 0,
      finished: e.finished === true,
    }));
  } catch {
    return [];
  }
}

class WatchHistory {
  entries = $state<WatchEntry[]>(load());

  /** Solo lo empezado y no terminado, lo más reciente primero. */
  resumable = $derived(this.entries.filter(isInProgress).sort((a, b) => b.updatedAt - a.updatedAt));

  byId = $derived(new Map(this.entries.map((e) => [e.item.id, e])));

  #saveTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // Otra pestaña con la app abierta también escribe: se adopta su versión.
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', (e) => {
        if (e.key === KEY) this.entries = load();
      });
      window.addEventListener('pagehide', () => this.flush());
    }
  }

  entryFor(itemId: string): WatchEntry | undefined {
    return this.byId.get(itemId);
  }

  /** Registra el item antes de reproducirlo para que el progreso tenga a qué asociarse. */
  register(item: CatalogItem) {
    if (item.kind === 'LIVE') return;
    const existing = this.entryFor(item.id);
    this.#save([
      ...this.entries.filter((e) => e.item.id !== item.id),
      {
        item: slim(item),
        positionMs: existing?.positionMs ?? 0,
        durationMs: existing?.durationMs ?? 0,
        updatedAt: Date.now(),
        finished: existing?.finished ?? false,
      },
    ]);
  }

  updateProgress(itemId: string, positionMs: number, durationMs: number) {
    const entry = this.entryFor(itemId);
    if (!entry || !(durationMs > 0)) return;
    const finished = positionMs >= durationMs * FINISHED_FRACTION;
    if (!finished && positionMs < MIN_POSITION_MS) return;
    this.#save([
      ...this.entries.filter((e) => e.item.id !== itemId),
      { ...entry, positionMs: finished ? durationMs : Math.round(positionMs), durationMs: Math.round(durationMs), updatedAt: Date.now(), finished },
    ]);
  }

  /** Quita la entrada y la devuelve, para poder deshacerlo con [restore]. */
  remove(itemId: string): WatchEntry | undefined {
    const removed = this.entryFor(itemId);
    if (removed) this.#save(this.entries.filter((e) => e.item.id !== itemId));
    return removed;
  }

  restore(entry: WatchEntry) {
    this.#save([...this.entries.filter((e) => e.item.id !== entry.item.id), entry]);
  }

  flush() {
    if (this.#saveTimer) {
      clearTimeout(this.#saveTimer);
      this.#saveTimer = null;
    }
    try {
      localStorage.setItem(KEY, JSON.stringify(this.entries));
    } catch {
      // Sin espacio o sin almacenamiento: el historial vive mientras dure la sesión.
    }
  }

  #save(entries: WatchEntry[]) {
    const now = Date.now();
    this.entries = entries
      .filter((e) => now - e.updatedAt < MAX_AGE_MS)
      .sort((a, b) => b.updatedAt - a.updatedAt)
      .slice(0, MAX_ENTRIES);
    if (!this.#saveTimer) this.#saveTimer = setTimeout(() => this.flush(), 500);
  }
}

export const watchHistory = new WatchHistory();
