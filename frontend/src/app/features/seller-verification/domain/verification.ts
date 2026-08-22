/**
 * La verificación de vendedor tal como la ve la pantalla. HU-002.
 *
 * TypeScript puro: sin Angular y sin `rxjs`, para que se pruebe sin TestBed
 * (frontend/CLAUDE.md).
 *
 * Los nombres son los del glosario y del contrato de la API. Lo que **no** hay aquí es
 * lo que el servidor no manda: ni claves de archivo, ni números completos. El criterio
 * 11 de HU-002 lo garantiza allá, y el tipo de aquí lo refleja para que nadie escriba
 * una plantilla que espere un dato que nunca va a llegar.
 */

/** Los seis estados del glosario. */
export type VerificationStatus =
  'NOT_STARTED' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'VERIFIED' | 'REJECTED' | 'REVOKED';

/** Los cinco motivos de la lista cerrada. Se traducen por su código. */
export type RejectionReason =
  | 'ILLEGIBLE_PHOTOS'
  | 'EXPIRED_DOCUMENT'
  | 'HOLDER_MISMATCH'
  | 'DOCUMENT_ALREADY_VERIFIED'
  | 'REQUIREMENTS_NOT_MET';

export type IdentityDocumentType = 'CC' | 'CE' | 'PPT';

export type BankAccountType = 'SAVINGS' | 'CHECKING' | 'ELECTRONIC_DEPOSIT';

export interface SellerVerification {
  readonly status: VerificationStatus;
  readonly attempts: number;
  readonly remainingAttempts: number;
  readonly complete: boolean;
  readonly documentSubmitted: boolean;
  readonly documentType: IdentityDocumentType | null;
  readonly documentNumberLastFour: string | null;
  readonly documentHolderName: string | null;
  readonly selfieSubmitted: boolean;
  readonly bank: string | null;
  readonly bankAccountType: BankAccountType | null;
  readonly bankAccountLastFour: string | null;
  readonly bankAccountHolderName: string | null;
  readonly rejectionReason: RejectionReason | null;
  readonly rejectionNote: string | null;
  readonly updatedAt: string;
}

/**
 * Una entidad del catálogo, tal como la ofrece el formulario.
 *
 * `wallet` no es decoración: una billetera solo recibe en depósito electrónico, y sin
 * ese dato el formulario ofrecería «ahorros» en Nequi.
 */
export interface FinancialInstitution {
  readonly code: string;
  readonly name: string;
  readonly wallet: boolean;
}

/**
 * Los tipos de cuenta que admite una entidad.
 *
 * <p>Es la única regla de esta clase que vive en el cliente, y vive aquí porque es de
 * presentación: qué opciones ofrecer. El servidor **no** la impone —la clasificación
 * entre banco y billetera está por confirmar (HU-002)— así que esto es una ayuda para no
 * ofrecer lo imposible, no una validación.
 */
export function tiposDeCuentaDe(entidad: FinancialInstitution): readonly BankAccountType[] {
  return entidad.wallet ? ['ELECTRONIC_DEPOSIT'] : ['SAVINGS', 'CHECKING'];
}

/** Los tres pasos, en el orden en que la pantalla los presenta. */
export const PASOS = ['document', 'selfie', 'bank'] as const;

export type Paso = (typeof PASOS)[number];

/** Si ese paso ya está entregado. */
export function pasoEntregado(verificacion: SellerVerification, paso: Paso): boolean {
  switch (paso) {
    case 'document':
      return verificacion.documentSubmitted;
    case 'selfie':
      return verificacion.selfieSubmitted;
    case 'bank':
      return verificacion.bank !== null;
  }
}

/**
 * El primer paso que falta, o `null` si están los tres.
 *
 * Es lo que decide a dónde llevar a alguien que retoma el proceso: el caso borde de
 * HU-002 pide que se retome donde iba, y «donde iba» es el primer hueco, no el último
 * paso que tocó.
 */
export function siguientePaso(verificacion: SellerVerification): Paso | null {
  return PASOS.find((paso) => !pasoEntregado(verificacion, paso)) ?? null;
}

/**
 * Si la pantalla puede ofrecer el envío.
 *
 * Exige los tres datos **y** que el servidor diga que está completa: `complete` incluye
 * la coincidencia de titular de RN-012, que aquí no se puede comprobar porque el nombre
 * del documento y el de la cuenta llegan los dos y compararlos sería reimplementar la
 * regla en el cliente. Si el servidor dice que no está completa, no se ofrece enviar
 * aunque los tres pasos se vean en verde.
 */
export function puedeEnviar(verificacion: SellerVerification): boolean {
  return (
    verificacion.status === 'IN_PROGRESS' &&
    verificacion.complete &&
    verificacion.remainingAttempts > 0
  );
}

/** Si la persona puede corregir y volver a intentarlo (RN-014). */
export function puedeReintentar(verificacion: SellerVerification): boolean {
  return (
    (verificacion.status === 'REJECTED' || verificacion.status === 'REVOKED') &&
    verificacion.remainingAttempts > 0
  );
}

/** Si agotó los tres intentos y necesita que alguien intervenga. */
export function agotoIntentos(verificacion: SellerVerification): boolean {
  return verificacion.remainingAttempts <= 0;
}

/**
 * Si la solicitud está en un estado donde tocar los datos tiene sentido.
 *
 * En revisión no: una solicitud enviada no se toca mientras alguien la mira, y eso lo
 * impone el servidor (RN-059). La pantalla lo respeta para no ofrecer un botón que va a
 * fallar.
 */
export function admiteEdicion(verificacion: SellerVerification): boolean {
  return verificacion.status === 'IN_PROGRESS';
}
