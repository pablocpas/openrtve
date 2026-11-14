const MAX_BODY_BYTES = 8_192;
const MAX_EVENTS = 8;
const EVENT_KEYS = ['eventId', 'contentId', 'title', 'kind', 'starts', 'completions', 'watchedMs'];

/** Solo contadores agregados. No se guardan IP, User-Agent, Referer, cookies ni IDs de usuario. */
export async function onRequest({ request, env }) {
  if (request.method !== 'POST') return reply(405, { Allow: 'POST' });
  const origin = new URL(request.url).origin;
  if (request.headers.get('Origin') !== origin) return reply(403);
  const site = request.headers.get('Sec-Fetch-Site');
  if (site && site !== 'same-origin') return reply(403);
  if (request.headers.get('DNT') === '1' || request.headers.get('Sec-GPC') === '1') return reply(204);
  if (request.headers.get('Content-Type')?.split(';')[0].trim() !== 'application/json') return reply(415);
  // No mezclar tráfico de las vistas previas con la web pública.
  if (env.CF_PAGES_BRANCH && env.CF_PAGES_BRANCH !== 'main') return reply(204);

  let body;
  try {
    const reader = request.body?.getReader();
    if (!reader) return reply(400);
    const chunks = [];
    let size = 0;
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY_BYTES) { await reader.cancel(); return reply(413); }
      chunks.push(value);
    }
    const bytes = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
    body = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
  } catch {
    return reply(400);
  }
  if (!body || typeof body !== 'object' || Array.isArray(body)
    || Object.keys(body).some((k) => k !== 'version' && k !== 'events')
    || body.version !== 1 || !Array.isArray(body.events) || !body.events.length || body.events.length > MAX_EVENTS
    || !body.events.every(validEvent)) return reply(400);
  if (!env.PLAYBACK_METRICS) return reply(503);

  const db = env.PLAYBACK_METRICS;
  const statements = [];
  for (const e of body.events) {
    // El batch es una transacción: reintentar un envío nunca vuelve a sumar sus contadores.
    statements.push(db.prepare(`
      INSERT INTO playback_daily (day, content_id, title, kind, starts, completions, watched_ms)
      SELECT date('now'), ?, ?, ?, ?, ?, ?
      WHERE NOT EXISTS (SELECT 1 FROM metric_receipts WHERE event_id = ?)
      ON CONFLICT(day, content_id, kind) DO UPDATE SET
        title = excluded.title,
        starts = starts + excluded.starts,
        completions = completions + excluded.completions,
        watched_ms = watched_ms + excluded.watched_ms
    `).bind(e.contentId, e.title.trim(), e.kind, e.starts, e.completions, e.watchedMs, e.eventId));
    // Solo nonce y fecha de caducidad; sin contenido ni vínculo entre envíos de una misma reproducción.
    statements.push(db.prepare('INSERT OR IGNORE INTO metric_receipts (event_id, expires_at) VALUES (?, unixepoch() + 86400)').bind(e.eventId));
  }
  statements.push(db.prepare('DELETE FROM metric_receipts WHERE expires_at < unixepoch()'));
  statements.push(db.prepare("DELETE FROM playback_daily WHERE day < date('now', '-89 days')"));
  try {
    await db.batch(statements);
    return reply(204);
  } catch {
    // No registrar solicitudes ni datos de conexión en los logs de la aplicación.
    return reply(503);
  }
}

function validEvent(e) {
  return e && typeof e === 'object' && !Array.isArray(e)
    && Object.keys(e).length === EVENT_KEYS.length && Object.keys(e).every((k) => EVENT_KEYS.includes(k))
    && typeof e.eventId === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(e.eventId)
    && typeof e.contentId === 'string' && /^\d{1,12}$/.test(e.contentId)
    && typeof e.title === 'string' && e.title.trim().length > 0 && e.title.length <= 160 && !/[\u0000-\u001f\u007f]/.test(e.title)
    && (e.kind === 'vod' || e.kind === 'live')
    && (e.starts === 0 || e.starts === 1) && (e.completions === 0 || e.completions === 1)
    && (e.kind !== 'live' || e.completions === 0)
    && Number.isInteger(e.watchedMs) && e.watchedMs >= 0 && e.watchedMs <= 60_000
    && (e.starts === 0 || e.watchedMs >= 1_000)
    && (e.watchedMs >= 1_000 || e.completions === 1);
}

function reply(status, extra = {}) {
  return new Response(null, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', ...extra } });
}
