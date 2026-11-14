/** Ejecuta [task] cuando el navegador quede libre (o, como mucho, a los [timeout] ms); devuelve cómo cancelarlo. */
export function whenIdle(task: () => void, timeout = 1500): () => void {
  if (typeof requestIdleCallback === 'function') {
    const handle = requestIdleCallback(task, { timeout });
    return () => cancelIdleCallback(handle);
  }
  const handle = setTimeout(task, 300);
  return () => clearTimeout(handle);
}
