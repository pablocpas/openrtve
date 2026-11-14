const KEY = 'openrtve.content-notice.v1';

function alreadyRead(): boolean {
  try {
    return sessionStorage.getItem(KEY) === 'read';
  } catch {
    return false;
  }
}

/** El aviso se muestra al entrar en cada pestaña y se puede volver a abrir en Ajustes. */
export const contentNotice = $state({
  open: !alreadyRead(),

  show() {
    this.open = true;
  },

  dismiss() {
    this.open = false;
    try {
      sessionStorage.setItem(KEY, 'read');
    } catch {
      // Sin almacenamiento, se puede continuar durante esta visita igualmente.
    }
  },
});
