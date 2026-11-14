/** Avisos efímeros (snackbar): uno a la vez, con acción opcional ("Deshacer"). */
export interface Message {
  id: number;
  text: string;
  action?: { label: string; run: () => void };
  /** Sin duración, hasta que se descarte (p. ej. "Abriendo enlace…"). */
  persistent?: boolean;
}

let nextId = 1;

class Messages {
  current = $state<Message | null>(null);
  #timer: ReturnType<typeof setTimeout> | null = null;

  show(text: string, options: { action?: Message['action']; persistent?: boolean } = {}): number {
    const message: Message = { id: nextId++, text, ...options };
    this.current = message;
    if (this.#timer) clearTimeout(this.#timer);
    this.#timer = null;
    // Con acción dura más: cuatro segundos no dan para pulsarla.
    if (!options.persistent) this.#timer = setTimeout(() => this.dismiss(message.id), options.action ? 8000 : 4000);
    return message.id;
  }

  /** Quita el aviso; con [id], solo si sigue siendo ese. */
  dismiss(id?: number) {
    if (id == null || this.current?.id === id) {
      this.current = null;
      if (this.#timer) clearTimeout(this.#timer);
      this.#timer = null;
    }
  }
}

export const messages = new Messages();

/** Reloj compartido para el progreso de los directos: un tic cada 30 s. */
class Clock {
  now = $state(Date.now());

  constructor() {
    if (typeof window !== 'undefined') setInterval(() => (this.now = Date.now()), 30_000);
  }
}

export const clock = new Clock();
