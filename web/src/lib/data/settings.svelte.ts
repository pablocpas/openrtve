/** Preferencias del usuario (AppSettings.kt), en `localStorage`. */
export interface Settings {
  /** Altura máxima de vídeo (0 = automática). 576 equivale al ahorro de datos de la app oficial. */
  maxVideoHeight: number;
  /** Activar subtítulos en español cuando existan. */
  subtitlesByDefault: boolean;
  /** Encadenar el siguiente episodio al terminar. */
  autoplayNext: boolean;
  /** Volumen del reproductor (0–1) y silencio, recordados entre sesiones. */
  volume: number;
  muted: boolean;
}

const KEY = 'openrtve.settings.v1';
const DEFAULTS: Settings = { maxVideoHeight: 0, subtitlesByDefault: false, autoplayNext: true, volume: 1, muted: false };
export const QUALITY_CHOICES = [0, 1080, 720, 576, 360];

function read(): Settings {
  try {
    const stored = JSON.parse(localStorage.getItem(KEY) ?? '{}');
    return { ...DEFAULTS, ...(typeof stored === 'object' && stored ? stored : {}) };
  } catch {
    return { ...DEFAULTS };
  }
}

class AppSettings {
  current = $state<Settings>(read());

  update(patch: Partial<Settings>) {
    this.current = { ...this.current, ...patch };
    try {
      localStorage.setItem(KEY, JSON.stringify(this.current));
    } catch {
      // Sin almacenamiento, los ajustes duran lo que la sesión.
    }
  }
}

export const settings = new AppSettings();
