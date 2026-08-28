import {
  POSICIONES_CANONICAS,
  TOMAS_DE_LA_SECUENCIA,
  tomaEn,
  type Listing,
} from '../../../shared/domain/listing';
import { gradosDe } from './publish-rules';

/**
 * Los ocho pasos del asistente de captura. HU-003 criterios 1 y 6.
 *
 * TypeScript puro: sin Angular y sin `rxjs`, para que se pruebe sin TestBed
 * (frontend/CLAUDE.md).
 *
 * <p>Vive en `features/listing` y no en `shared` porque es del formulario del vendedor y
 * de nadie más: el moderador ve las tomas ya hechas y el catálogo también. Lo que las dos
 * mitades comparten —cuántas son, cuáles son canónicas, en qué posición va cada una— ya
 * está en `shared/domain/listing.ts` y aquí se reusa, no se repite.
 */

/** Un paso del asistente: qué posición se está tomando y cómo se rotula. */
export interface PasoDeCaptura {
  /** La posición que viaja a la API. */
  readonly posicion: number;
  /** Los grados que rotulan la posición. Solo para el texto. */
  readonly grados: number;
  /** Si es una de las cuatro que no pueden faltar (RN-016). */
  readonly canonica: boolean;
  /** La clave de Transloco con el nombre de la toma. Nunca el texto. */
  readonly nombre: string;
  /** Si ya hay una toma puesta en esa posición. */
  readonly hecha: boolean;
}

/**
 * El nombre de cada toma, por posición.
 *
 * <p>Las cuatro canónicas se llaman como las nombra RN-016 —frontal, lateral derecha,
 * posterior, lateral izquierda—; las cuatro intermedias, por las dos que tienen a cada
 * lado. Es una constante y no una plantilla con los grados dentro porque el criterio 1 pide
 * **el nombre** de cada toma: «45 grados» no le dice a nadie hacia dónde girar el producto,
 * y «frontal derecha» sí.
 */
const NOMBRES: readonly string[] = [
  'listing.capture.shot.front',
  'listing.capture.shot.frontRight',
  'listing.capture.shot.right',
  'listing.capture.shot.backRight',
  'listing.capture.shot.back',
  'listing.capture.shot.backLeft',
  'listing.capture.shot.left',
  'listing.capture.shot.frontLeft',
];

/**
 * Si a esta publicación se le puede ofrecer el asistente.
 *
 * <p>Solo a la secuencia de ocho. **La tecnología declarada sellada queda fuera** y lo dice
 * la propia historia: son cuatro tomas del empaque, no hay giro que guiar y admite imágenes
 * que no toma nadie con esta cámara (RN-065, RN-066). Ahí se sigue usando la rejilla, que
 * es lo que HU-007 dejó.
 *
 * <p>Se pregunta por `requiredShots`, que **viene del servidor**: cuántas se exigen es una
 * regla del dominio de allá, y calcularla aquí sería tenerla en dos sitios con dos formas
 * de estar mal.
 */
export function admiteAsistente(publicacion: Listing): boolean {
  return publicacion.requiredShots === TOMAS_DE_LA_SECUENCIA;
}

/** Los ocho pasos, en orden de giro, con lo que ya esté hecho marcado. */
export function pasosDeCaptura(publicacion: Listing): readonly PasoDeCaptura[] {
  return NOMBRES.map((nombre, posicion) => ({
    posicion,
    grados: gradosDe(posicion),
    canonica: POSICIONES_CANONICAS.includes(posicion),
    nombre,
    hecha: tomaEn(publicacion, posicion) !== null,
  }));
}

/**
 * En qué paso abre el asistente: el primero que falte.
 *
 * <p>Lo ya subido se da por bueno y no se vuelve a pedir. Quien retoma un borrador a medias
 * quiere seguir, no repetir; y repetir **una** toma concreta ya se hace desde la rejilla,
 * que es donde el criterio 6 lo resuelve sin obligar a recorrer las ocho.
 *
 * <p>Con las ocho puestas devuelve la primera, que es lo que tiene sentido si alguien entra
 * a rehacerlas: el asistente abre por el principio y va sobrescribiendo.
 */
export function primerPasoPendiente(publicacion: Listing): number {
  const pendiente = pasosDeCaptura(publicacion).find((paso) => !paso.hecha);

  return pendiente?.posicion ?? 0;
}

/** Cuántas de las ocho están puestas. Es lo que llena la barra de progreso. */
export function pasosHechos(publicacion: Listing): number {
  return pasosDeCaptura(publicacion).filter((paso) => paso.hecha).length;
}
