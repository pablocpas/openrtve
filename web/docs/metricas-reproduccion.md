# Métricas de reproducción

Los datos se consultan en **Cloudflare → Storage & databases → D1 →
openrtve-playback-metrics → Studio**. Son privados para quien tenga acceso a esa
cuenta. No existe ninguna ruta web que permita leerlos públicamente.

La vista `playback_summary` muestra los resultados de los últimos 90 días por
contenido. En la consola de D1 Studio:

```sql
SELECT * FROM playback_summary ORDER BY minutos_reproducidos DESC;
```

Para ver las cifras por día:

```sql
SELECT day AS dia_utc, title AS contenido, kind AS tipo,
       starts AS reproducciones, completions AS finales_alcanzados,
       ROUND(watched_ms / 60000.0, 2) AS minutos_reproducidos
FROM playback_daily
ORDER BY day DESC, watched_ms DESC;
```

Y para un resumen de toda la web:

```sql
SELECT SUM(reproducciones) AS reproducciones,
       SUM(finales_alcanzados) AS finales_alcanzados,
       ROUND(SUM(minutos_reproducidos), 2) AS minutos_reproducidos
FROM playback_summary;
```

## Qué significa cada cifra

- **Reproducciones:** aperturas del reproductor con al menos un segundo de avance
  real. Pausar y continuar no vuelve a contar. Volver a abrir un contenido o
  repetirlo después del final sí cuenta otra reproducción. No son personas únicas.
- **Tiempo:** segundos de reloj durante los que avanza el vídeo. No suma pausas,
  cargas ni desplazamientos por la barra. A velocidad 2×, diez segundos delante
  del vídeo son diez segundos de tiempo, no veinte. Las suspensiones del navegador
  de más de cinco segundos se descartan para no inventar minutos de visionado.
- **Finales alcanzados:** eventos de final de los vídeos a la carta. Llegar al final
  tras adelantar también cuenta: **no acredita que se haya visto todo el vídeo**.
  No se mide una finalización de directos.
- **Segundos por reproducción:** tiempo agregado dividido entre inicios dentro
  del periodo. Puede haber pequeños desajustes entre días cuando una reproducción
  cruza medianoche. Los días se calculan en UTC al recibir el envío.

Los datos empiezan con este despliegue, no reconstruyen el pasado y son estimaciones:
pueden faltar navegadores con DNT/GPC, bloqueadores, desconexiones, cierres forzados
y usos de versiones antiguas o de la app Android. Se basan en lo informado por el
cliente: no son métricas auditadas y no prueban ingresos publicitarios perdidos.
Un cliente modificado puede fabricar datos; las comprobaciones de origen y límites
de carga no son autenticación de personas.

## Recogida y privacidad

El navegador agrupa los incrementos y los envía cada 30 segundos, y al pausar,
terminar o cerrar. Un nonce distinto por **envío** permite reintentar sin sumar dos
veces. No se genera un identificador de usuario ni de sesión de reproducción.
La cola de reintentos solo vive en memoria, con un máximo de ocho envíos y cinco
minutos. No se crean cookies, claves en localStorage ni registros en IndexedDB para
las métricas. Desarrollo y previews no alimentan la base de producción.

El endpoint solo admite los campos previstos (contenido, título público, tipo,
contadores y nonce), un máximo de ocho eventos, 8 KB y 60 segundos por evento. Los
valores se enlazan a SQL parametrizado. Una transacción agrega los incrementos y
registra los nonces aceptados; los reintentos se deduplican, también dentro de un
mismo lote. Si las métricas fallan, el vídeo sigue funcionando.

`playback_daily` contiene únicamente sumas por contenido y día. `metric_receipts`
contiene solo nonces aleatorios y su caducidad: no conserva qué contenido ni qué
duración correspondían a cada envío, ni permite unir los envíos de un espectador.
La aplicación no guarda ni registra en logs IP, User-Agent, Referer, cookies,
cuentas, posición del vídeo o URLs/licencias de reproducción. Cloudflare sí procesa
la conexión como proveedor del alojamiento; esto no equivale a que el proveedor
no reciba direcciones IP.

Los recibos caducan a las 24 horas y los totales tienen una ventana de 90 días. Cada
ingesta elimina los datos caducados; sin nuevas ingestas, permanecen hasta la
siguiente limpieza. Las copias automáticas de D1 siguen la retención del proveedor.
No hay envíos automáticos a RTVE ni otros sistemas publicitarios. La finalidad es
conocer el uso de este proyecto. El aviso inicial y Ajustes explican el tratamiento
y muestran el contacto del responsable.

El diseño limita la recogida a medición de audiencia propia y estadísticas
agregadas. Como referencia, la [guía de la AEPD sobre medición de audiencia](https://www.aepd.es/guias/guia-cookies-analiticas-externas.pdf)
describe las condiciones de la exención de consentimiento; no se presupone que
cualquier analítica sin cookies esté automáticamente exenta. Añadir perfiles,
identificadores persistentes, publicidad o compartir datos individuales exigiría
revisar este diseño y su base jurídica.

## Operación

La base está restringida a la jurisdicción de la Unión Europea. El binding
`PLAYBACK_METRICS` y su migración están en `wrangler.toml` y `migrations/`.

```sh
npx wrangler d1 migrations apply PLAYBACK_METRICS --remote
npx wrangler d1 execute PLAYBACK_METRICS --remote --command 'SELECT * FROM playback_summary ORDER BY minutos_reproducidos DESC'
```

Para un clon del proyecto, crea una base propia y cambia el UUID del binding antes
de desplegar. La base de este repositorio no es accesible con ese UUID sin
autorización de la cuenta de Cloudflare.

Para pruebas locales:

```sh
npx wrangler d1 migrations apply PLAYBACK_METRICS --local
npm run build
npx wrangler pages dev dist
```

La API solo acepta POST del mismo origen. GET y otros métodos devuelven 405; las
previews se ignoran. Las pruebas de SQL usan SQLite real en memoria y comprueban
deduplicación, agregación, validación y limpieza. Las del cliente comprueban
pausas, saltos, velocidad, señales de privacidad y cierre del reproductor.
