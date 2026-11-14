/**
 * Copia local de los documentos del catálogo, en IndexedDB (RawDocumentCache.kt).
 * La portada aparece al instante con la última copia y, sin red, se muestra lo
 * último que se vio. Si IndexedDB no está disponible (modo privado estricto), la
 * app funciona igual, solo que sin copia.
 */
export interface CachedDocument {
  url: string;
  raw: string;
  savedAt: number;
}

const DB_NAME = 'openrtve';
const STORE = 'documents';
/** Más que esto no aporta: la portada, sus filas y las fichas recientes. */
const MAX_ENTRIES = 400;

let dbPromise: Promise<IDBDatabase | null> | null = null;

function openDb(): Promise<IDBDatabase | null> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve) => {
    try {
      const request = indexedDB.open(DB_NAME, 1);
      request.onupgradeneeded = () => {
        const store = request.result.createObjectStore(STORE, { keyPath: 'url' });
        store.createIndex('savedAt', 'savedAt');
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => resolve(null);
      request.onblocked = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
  return dbPromise;
}

function run<T>(mode: IDBTransactionMode, body: (store: IDBObjectStore) => IDBRequest<T> | void): Promise<T | undefined> {
  return openDb().then(
    (db) =>
      new Promise((resolve) => {
        if (!db) return resolve(undefined);
        try {
          const tx = db.transaction(STORE, mode);
          const request = body(tx.objectStore(STORE));
          tx.oncomplete = () => resolve(request ? request.result : undefined);
          tx.onerror = () => resolve(undefined);
          tx.onabort = () => resolve(undefined);
        } catch {
          resolve(undefined);
        }
      }),
  );
}

export const documentCache = {
  read(url: string): Promise<CachedDocument | undefined> {
    return run<CachedDocument>('readonly', (store) => store.get(url));
  },

  async write(url: string, raw: string, savedAt: number): Promise<void> {
    await run('readwrite', (store) => {
      store.put({ url, raw, savedAt } satisfies CachedDocument);
    });
    writesSinceTrim++;
    if (writesSinceTrim >= 50) {
      writesSinceTrim = 0;
      void trim();
    }
  },

  async touch(url: string, savedAt: number): Promise<void> {
    const doc = await this.read(url);
    if (doc) await run('readwrite', (store) => { store.put({ ...doc, savedAt }); });
  },

  async clear(): Promise<void> {
    await run('readwrite', (store) => { store.clear(); });
  },
};

let writesSinceTrim = 0;

/** Quita las copias más antiguas cuando hay demasiadas. */
async function trim(): Promise<void> {
  const count = await run<number>('readonly', (store) => store.count());
  if (!count || count <= MAX_ENTRIES) return;
  let toDelete = count - MAX_ENTRIES;
  await run('readwrite', (store) => {
    const cursor = store.index('savedAt').openCursor();
    cursor.onsuccess = () => {
      const c = cursor.result;
      if (!c || toDelete <= 0) return;
      c.delete();
      toDelete--;
      c.continue();
    };
  });
}
