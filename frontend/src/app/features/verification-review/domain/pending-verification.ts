/**
 * Una solicitud de verificación tal como la ve quien la revisa. HU-006.
 *
 * TypeScript puro: sin Angular y sin `rxjs`, para que se pruebe sin TestBed
 * (frontend/CLAUDE.md).
 *
 * <p><strong>Aquí no hay números completos, y eso incluye al moderador.</strong> El
 * criterio 11 de HU-002 no hace excepciones por rol: de la cédula y de la cuenta solo
 * llegan los cuatro últimos dígitos. Tampoco hay ninguna dirección de imagen; las tres
 * se piden una por una a un endpoint que comprueba el rol y anota la lectura.
 *
 * <p>Eso deja una limitación real que conviene tener presente: **quien revisa compara la
 * foto del documento contra cuatro dígitos**, no contra el número entero. Es lo que el
 * criterio 11 permite; si hiciera falta más, es una decisión de producto con su motivo.
 */
export interface PendingVerification {
  /** Con esto se decide: es lo que viaja en la ruta y en las tres acciones. */
  readonly id: string;
  /** RN-014: de tres. Quien revisa necesita saberlo antes de rechazar. */
  readonly attempts: number;
  /**
   * `CC`, `CE` o `PPT`. Cadena y no unión cerrada a propósito: aquí solo se muestra
   * traducido, y un tipo nuevo en el backend tiene que pintar la clave que falte, no
   * romper la compilación de una pantalla que solo lo enseña.
   */
  readonly documentType: string | null;
  readonly documentNumberLastFour: string | null;
  readonly documentHolderName: string | null;
  readonly documentSubmitted: boolean;
  readonly selfieSubmitted: boolean;
  readonly bank: string | null;
  readonly bankAccountType: string | null;
  readonly bankAccountLastFour: string | null;
  readonly bankAccountHolderName: string | null;
  /** Desde cuándo espera, en ISO 8601. Es lo que ordena la bandeja. */
  readonly waitingSince: string;
}

/** Las tres imágenes, con el nombre que espera la ruta del endpoint. */
export type VerificationImage = 'document-front' | 'document-back' | 'selfie';

export const IMAGENES: readonly VerificationImage[] = ['document-front', 'document-back', 'selfie'];

/**
 * RN-012 dicha como función: el titular de la cuenta tiene que ser el del documento.
 *
 * <p>El servidor ya la impone —rechaza la cuenta que no coincide— así que esto no es una
 * validación: es lo que permite **señalar** la discrepancia en el detalle (criterio 7).
 * Puede llegar una solicitud vieja, guardada antes de que la regla existiera, o dos
 * nombres que el backend consideró iguales y a la vista no lo parecen.
 *
 * <p>Compara sin distinguir mayúsculas ni espacios de más, que es como los escribe la
 * gente. Lo que **no** hace es quitar tildes ni reordenar apellidos: «Ana García» y «Ana
 * Garcia» son distintos aquí, y tienen que serlo, porque marcar de más hace que quien
 * revisa deje de mirar el aviso.
 *
 * <p>Con cualquiera de los dos ausente devuelve `false`: una solicitud a medias no tiene
 * discrepancia, tiene un paso sin entregar, y decir «no coinciden» de algo que todavía no
 * existe es ruido.
 */
export function hayDiscrepanciaDeTitular(solicitud: PendingVerification): boolean {
  const documento = solicitud.documentHolderName;
  const cuenta = solicitud.bankAccountHolderName;

  if (documento === null || cuenta === null) {
    return false;
  }

  return normalizar(documento) !== normalizar(cuenta);
}

const normalizar = (nombre: string): string =>
  nombre.trim().replace(/\s+/g, ' ').toLocaleLowerCase('es-CO');

/**
 * Cuánto lleva esperando, en milisegundos.
 *
 * <p>Se calcula contra un instante que se recibe y no contra `Date.now()`: la pantalla se
 * renderiza también en el servidor y una función que lee el reloj por su cuenta no se
 * puede probar sin congelarlo.
 */
export function esperaEnMilisegundos(solicitud: PendingVerification, ahora: Date): number {
  return Math.max(0, ahora.getTime() - new Date(solicitud.waitingSince).getTime());
}

/**
 * La más vieja primero, que es el orden de la bandeja.
 *
 * <p>El servidor ya las manda así. Se ordena igualmente porque el orden es parte del
 * criterio 1 y depender de que el otro lado lo mantenga es depender de algo que ninguna
 * prueba de esta mitad comprueba.
 */
export function porAntiguedad(
  solicitudes: readonly PendingVerification[],
): readonly PendingVerification[] {
  return [...solicitudes].sort((a, b) => a.waitingSince.localeCompare(b.waitingSince));
}
