import { beforeEach, describe, expect, it, vi } from 'vitest';
import { referenceItem, type CatalogItem, type ContentKind } from '../src/lib/domain/models';

/** `localStorage` mínimo: los tests corren en Node. */
function installStorage() {
  const data = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => void data.set(k, v),
    removeItem: (k: string) => void data.delete(k),
    clear: () => data.clear(),
  });
}

const item = (id: string, kind: ContentKind = 'VIDEO'): CatalogItem => referenceItem(id, kind, `Título ${id}`, `https://img.rtve.es/v/${id}/horizontal2`);

/** Una instancia nueva, como al abrir la app otra vez. */
async function freshFavorites() {
  vi.resetModules();
  return (await import('../src/lib/data/favorites.svelte')).favorites;
}

describe('Mi lista', () => {
  let now = 1_000_000;
  beforeEach(() => {
    installStorage();
    now = 1_000_000;
    vi.spyOn(Date, 'now').mockImplementation(() => now);
  });

  it('lo último añadido va primero y pulsar otra vez lo quita', async () => {
    const favorites = await freshFavorites();
    expect(favorites.toggle(item('v1'))).toBe(true);
    now += 10;
    expect(favorites.toggle(item('p1', 'PROGRAM'))).toBe(true);
    expect(favorites.items.map((i) => i.id)).toEqual(['p1', 'v1']);
    expect(favorites.toggle(item('v1'))).toBe(false);
    expect(favorites.contains('v1')).toBe(false);
    expect(favorites.items.map((i) => i.id)).toEqual(['p1']);
  });

  it('deshacer un quitar lo devuelve a su sitio', async () => {
    const favorites = await freshFavorites();
    for (const id of ['v1', 'v2', 'v3']) {
      favorites.toggle(item(id));
      now += 10;
    }
    const removed = favorites.remove('v2')!;
    expect(favorites.items.map((i) => i.id)).toEqual(['v3', 'v1']);
    favorites.restore(removed);
    expect(favorites.items.map((i) => i.id)).toEqual(['v3', 'v2', 'v1']);
  });

  it('los directos no se guardan', async () => {
    const favorites = await freshFavorites();
    expect(favorites.toggle(item('la1', 'LIVE'))).toBe(false);
    expect(favorites.items).toEqual([]);
  });

  it('la lista y su orden sobreviven a cerrar la app', async () => {
    const first = await freshFavorites();
    first.toggle(item('v1'));
    now += 10;
    first.toggle(item('p1', 'PROGRAM'));
    const reopened = await freshFavorites();
    expect(reopened.items.map((i) => [i.id, i.kind, i.title])).toEqual([
      ['p1', 'PROGRAM', 'Título p1'],
      ['v1', 'VIDEO', 'Título v1'],
    ]);
  });
});
