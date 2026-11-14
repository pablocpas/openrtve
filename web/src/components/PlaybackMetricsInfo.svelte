<script lang="ts">
  import { rightsContactEmail, rightsContactUrl } from '../lib/ui/rightsContact';
</script>

<details>
  <summary>Métricas de reproducción y privacidad</summary>
  <div class="info">
    <p>
      Para conocer el uso de OpenRTVE, sumamos por contenido y día las reproducciones iniciadas,
      las llegadas al final y el tiempo de reproducción. No contamos las pausas, las cargas ni los saltos en el vídeo.
    </p>
    <p>
      Enviamos a nuestro servicio en Cloudflare el identificador y título del contenido, si es un directo o un vídeo
      a la carta, y los incrementos de esos contadores. La base de métricas no guarda direcciones IP, datos del navegador
      ni identificadores de personas. No se crean cookies de seguimiento ni historiales individuales.
    </p>
    <p>
      Cada envío tiene un código aleatorio nuevo para evitar contar dos veces un reintento; no permite relacionar
      las reproducciones de una persona. Estos recibos caducan a las 24 horas. Los totales se conservan durante 90 días.
      La limpieza de datos caducados se realiza al recibir nuevos envíos.
    </p>
    <p>
      Cloudflare procesa la conexión, incluida su dirección IP, como proveedor del alojamiento.
      Respetamos las señales «No rastrear» (DNT) y Global Privacy Control del navegador y no enviamos métricas cuando están activadas.
      Las estadísticas no se envían automáticamente a RTVE.
    </p>
    <p>
      El responsable es el mantenedor de OpenRTVE. Para cuestiones de privacidad:
      <a href={rightsContactUrl}>{rightsContactEmail}</a>.
    </p>
  </div>
</details>

<style>
  details { color: var(--muted); font-size: 0.88rem; line-height: 1.55; }
  summary { color: var(--accent); cursor: pointer; min-height: 44px; padding-block: 10px; }
  .info { display: grid; gap: 12px; padding-block: 4px 12px; }
  a { color: var(--accent); text-decoration: underline; text-underline-offset: 3px; overflow-wrap: anywhere; }
</style>
