-- Únicamente sumas por contenido y día (UTC); nunca eventos ni historiales por espectador.
CREATE TABLE IF NOT EXISTS playback_daily (
  day TEXT NOT NULL,
  content_id TEXT NOT NULL,
  title TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('vod', 'live')),
  starts INTEGER NOT NULL DEFAULT 0 CHECK (starts >= 0),
  completions INTEGER NOT NULL DEFAULT 0 CHECK (completions >= 0),
  watched_ms INTEGER NOT NULL DEFAULT 0 CHECK (watched_ms >= 0),
  PRIMARY KEY (day, content_id, kind)
) WITHOUT ROWID;

-- Recibos técnicos para deduplicar reintentos. No contienen títulos, usuarios, IP ni sesiones.
CREATE TABLE IF NOT EXISTS metric_receipts (
  event_id TEXT PRIMARY KEY,
  expires_at INTEGER NOT NULL
) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS metric_receipts_expiry ON metric_receipts(expires_at);

-- Vista privada lista para consultar desde D1 Studio; no existe un endpoint público de lectura.
CREATE VIEW IF NOT EXISTS playback_summary AS
SELECT content_id, MAX(title) AS title, kind,
  SUM(starts) AS reproducciones,
  SUM(completions) AS finales_alcanzados,
  ROUND(SUM(watched_ms) / 60000.0, 2) AS minutos_reproducidos,
  ROUND(SUM(watched_ms) / 1000.0 / NULLIF(SUM(starts), 0), 1) AS segundos_por_reproduccion,
  MIN(day) AS primer_dia, MAX(day) AS ultimo_dia
FROM playback_daily
WHERE day >= date('now', '-89 days')
GROUP BY content_id, kind;
