/** Contacto con el responsable de OpenRTVE, compartido por el aviso y Ajustes. */
export const rightsContactEmail = 'pablocpascual@gmail.com';

const subject = 'OpenRTVE: derechos de contenido / solicitud de retirada';
const body = [
  'Contenido o enlace afectado:',
  '',
  'Derechos afectados y motivo de la solicitud:',
  '',
  'Relación con el titular de los derechos, si procede:',
  '',
  'Solicitud de revisión o retirada:',
  '',
].join('\r\n');

export const rightsContactUrl = `mailto:${rightsContactEmail}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
