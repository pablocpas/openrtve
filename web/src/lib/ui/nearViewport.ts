/**
 * Avisa una vez cuando un elemento se acerca a la parte visible de [root] (la
 * pantalla que hace scroll), con una pantalla de margen por arriba y por abajo: así
 * una fila se pide antes de llegar a ella y no se ve el esqueleto al bajar.
 *
 * Un solo IntersectionObserver por contenedor, compartido por todas sus filas.
 */
type Callback = () => void;

interface Watch {
  observer: IntersectionObserver;
  callbacks: Map<Element, Callback>;
}

const byRoot = new WeakMap<Element, Watch>();
let byViewport: Watch | undefined;

function observerFor(root: Element | null): Watch {
  let entry = root ? byRoot.get(root) : byViewport;
  if (!entry) {
    const callbacks = new Map<Element, Callback>();
    const observer = new IntersectionObserver(
      (records) => {
        for (const record of records) {
          if (!record.isIntersecting) continue;
          const callback = callbacks.get(record.target);
          callbacks.delete(record.target);
          observer.unobserve(record.target);
          callback?.();
        }
      },
      { root, rootMargin: '100% 0px' },
    );
    entry = { observer, callbacks };
    if (root) byRoot.set(root, entry);
    else byViewport = entry;
  }
  return entry;
}

/** Acción de Svelte: `use:nearViewport={{ root, onnear }}`. */
interface Params {
  root: Element | null | undefined;
  onnear: Callback;
  /** Si cambia (otra fila en el mismo sitio), se vuelve a avisar. */
  key?: unknown;
}

export function nearViewport(node: Element, params: Params) {
  if (typeof IntersectionObserver === 'undefined') {
    params.onnear();
    return {};
  }
  let current = observerFor(params.root ?? null);
  current.callbacks.set(node, () => params.onnear());
  current.observer.observe(node);
  const stop = () => {
    current.callbacks.delete(node);
    current.observer.unobserve(node);
  };
  return {
    update(next: Params) {
      const target = observerFor(next.root ?? null);
      const rearm = next.key !== params.key;
      params = next;
      // El contenedor llega después del primer pintado, o la fila es otra: se vuelve a observar.
      const pending = current.callbacks.has(node);
      if (rearm || (target !== current && pending)) {
        stop();
        current = target;
        current.callbacks.set(node, () => params.onnear());
        current.observer.observe(node);
      }
    },
    destroy: stop,
  };
}

/**
 * Acción de Svelte para lo que vive dentro de una pantalla (`.screen`, la que hace
 * scroll): avisa una vez cuando se acerca. Firefox y Safari no adelantan la carga de
 * las imágenes diferidas dentro de un contenedor con scroll; esto sí, igual en todos.
 */
export function nearScreen(node: Element, onnear: Callback) {
  return nearViewport(node, { root: node.closest('.screen'), onnear: () => onnear() });
}
