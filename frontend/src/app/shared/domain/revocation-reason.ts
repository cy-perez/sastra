/**
 * Los cinco motivos por los que se le quita el sello a quien ya lo tenía. RN-069.
 *
 * TypeScript puro, sin Angular.
 *
 * <p>Vive en `shared` por lo mismo que `rejection-reason.ts`, que está al lado: la acción
 * de revocar se pinta en el perfil público del vendedor, que es de `catalog`, y quien
 * conoce la verificación es `verification-review`. **Una funcionalidad no importa de otra**
 * (frontend/CLAUDE.md), así que la lista sube aquí en vez de duplicarse.
 *
 * <p><strong>No es `RejectionReason` y no se mezclan.</strong> Aquella juzga una solicitud
 * que todavía no se aprobó; esta se lo quita a alguien que ya vende. El backend las
 * declara como dos enumeraciones distintas y el endpoint de revocación rechaza un valor de
 * la otra.
 *
 * <p>Los valores describen hechos y no delitos: ninguno dice fraude ni suplantación. El
 * porqué está en RN-069, y se nota justamente aquí, porque de estas claves salen los
 * textos que la persona lee.
 */
export type RevocationReason =
  | 'DOCUMENT_NOT_ITS_HOLDER'
  | 'BANK_ACCOUNT_NOT_HOLDER'
  | 'REPEATED_PROHIBITED_LISTINGS'
  | 'HOLDER_REQUEST'
  | 'REQUIREMENTS_NO_LONGER_MET';

/**
 * En el orden en que se le ofrecen al moderador.
 *
 * <p>El genérico va último a propósito. RN-069 dice que es el último recurso y no el
 * primero, y un desplegable que lo ofrece de primero es la forma más rápida de que acabe
 * siendo el motivo de todas las revocaciones.
 */
export const MOTIVOS_DE_REVOCACION: readonly RevocationReason[] = [
  'DOCUMENT_NOT_ITS_HOLDER',
  'BANK_ACCOUNT_NOT_HOLDER',
  'REPEATED_PROHIBITED_LISTINGS',
  'HOLDER_REQUEST',
  'REQUIREMENTS_NO_LONGER_MET',
];
