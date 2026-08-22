/**
 * Los cinco motivos por los que se rechaza una verificación de vendedor.
 *
 * TypeScript puro, sin Angular.
 *
 * <p>Vive en `shared` y no dentro de una funcionalidad porque lo necesitan las dos
 * mitades del proceso y **una funcionalidad no importa de otra** (frontend/CLAUDE.md):
 * `seller-verification` lo muestra a quien fue rechazado y `verification-review` lo
 * ofrece a quien decide. Es la misma lista cerrada, y duplicarla en dos features es la
 * forma segura de que un día tengan cuatro motivos y cinco.
 *
 * <p>La lista es la del glosario y la del enum del backend. Es cerrada a propósito: el
 * motivo viaja en un correo a la persona rechazada, y un texto libre ahí acabaría
 * llevando información que no debe salir.
 */
export type RejectionReason =
  | 'ILLEGIBLE_PHOTOS'
  | 'EXPIRED_DOCUMENT'
  | 'HOLDER_MISMATCH'
  | 'DOCUMENT_ALREADY_VERIFIED'
  | 'REQUIREMENTS_NOT_MET';

/**
 * En el orden en que se le ofrecen al moderador.
 *
 * <p>Una constante y no un arreglo suelto en la plantilla: el desplegable de HU-006 y
 * cualquier otra lista que venga salen de aquí, así que agregar un motivo es agregarlo
 * en un sitio. El orden no es alfabético; va de lo más frecuente a lo menos, que es lo
 * que ahorra desplazamientos a quien revisa muchas al día.
 */
export const MOTIVOS_DE_RECHAZO: readonly RejectionReason[] = [
  'ILLEGIBLE_PHOTOS',
  'HOLDER_MISMATCH',
  'EXPIRED_DOCUMENT',
  'DOCUMENT_ALREADY_VERIFIED',
  'REQUIREMENTS_NOT_MET',
];
