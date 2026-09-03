import type { AttentionReason, Money } from '../../../shared/domain/listing';

/**
 * Una publicación en la bandeja del moderador. HU-008.
 *
 * TypeScript puro: sin Angular y sin `rxjs`, para que se pruebe sin TestBed
 * (frontend/CLAUDE.md).
 *
 * <p>Es una **fila**, no la publicación entera. La bandeja sirve para elegir cuál abrir,
 * y para eso bastan el título, el precio, cuánto lleva esperando y si algo pide mirarla
 * con más cuidado. Lo demás —las ocho tomas, las medidas, la descripción— llega al abrir
 * el detalle, que pide la publicación completa.
 *
 * <p>Aquí **no viaja el identificador del vendedor**, y es deliberado: una bandeja que
 * los repartiera sería de paso una lista de quién vende qué, disponible para cualquiera
 * con el rol. Lo único que se dice es si la fila es tuya, con {@link own}.
 */
export interface PendingListing {
  /** Con esto se decide: es lo que viaja en la ruta y en las dos acciones. */
  readonly id: string;
  readonly title: string;
  readonly price: Money;
  /**
   * Cuándo entró a revisión, en ISO 8601. Es lo que ordena la bandeja (criterio 1).
   *
   * <p>No es «cuándo se tocó por última vez»: una publicación que espera turno puede
   * cambiar de precio, y con la fecha de modificación el tiempo de espera se reiniciaría
   * solo. Lo sella la columna `submitted_at`.
   */
  readonly waitingSince: string;
  /** RN-020: pide mirarse con más cuidado. No cambia el estado ni bloquea nada. */
  readonly requiresAttention: boolean;
  /**
   * Por qué la pide.
   *
   * <p>Al moderador sí se le dice, al contrario que al vendedor: `listing.mine.attention`
   * calla el motivo para no invitarlo a cambiar el precio y esquivar la revisión. Quien
   * revisa es justo quien necesita el dato.
   */
  readonly attentionReasons: readonly AttentionReason[];
  /**
   * La toma frontal, para reconocer la publicación sin abrirla. Nula si no está.
   *
   * <p>No se cae a otra toma cuando falta: una lista donde la miniatura es a veces la
   * espalda de la prenda enseña a desconfiar de la miniatura.
   */
  readonly coverUrl: string | null;
  /**
   * Si la publicación es de quien está mirando. RN-063 le prohíbe decidir sobre ella.
   *
   * <p>Lo calcula el servidor y llega como booleano, no como el identificador del
   * vendedor. Sin este dato la interfaz no podría cumplir el criterio 12: el servidor
   * rechazaría igual, pero quien revisa se enteraría después de pulsar, con un correo ya
   * prometido.
   */
  readonly own: boolean;
}

/** La cola, tal como la pagina el servidor. */
export interface PendingListingsPage {
  readonly items: readonly PendingListing[];
  readonly page: number;
  readonly size: number;
  /**
   * Si detrás de esta página queda al menos una publicación.
   *
   * <p><strong>Lo dice el servidor.</strong> Deducirlo de que `items` venga lleno se
   * equivoca justo cuando el total es múltiplo exacto del tamaño: la última página viene
   * llena y la pantalla ofrecería un «Siguiente» hacia una página vacía. Es la misma
   * lección que ya pagó la bandeja de verificaciones.
   */
  readonly hasMore: boolean;
}

/**
 * La más vieja primero, que es el orden del criterio 1.
 *
 * <p>El servidor ya las manda así. Se ordena igualmente porque el orden **es** el
 * criterio, y depender de que el otro lado lo mantenga es depender de algo que ninguna
 * prueba de esta mitad comprueba. Es la misma decisión que tomó HU-006.
 */
export function porEspera(cola: readonly PendingListing[]): readonly PendingListing[] {
  return [...cola].sort((a, b) => a.waitingSince.localeCompare(b.waitingSince));
}
