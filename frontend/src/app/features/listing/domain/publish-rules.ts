import {
  POSICIONES_CANONICAS,
  TOMAS_DE_LA_SECUENCIA,
  tomaEn,
  tomasDelVendedor,
  type Category,
  type Color,
  type Condition,
  type Listing,
  type ListingImage,
  type ListingStatus,
} from '../../../shared/domain/listing';

/**
 * Las reglas del formulario de publicación. HU-007.
 *
 * TypeScript puro: sin Angular y sin `rxjs`, para que se pruebe sin TestBed
 * (frontend/CLAUDE.md).
 *
 * <p>Es lo que el vendedor necesita y el moderador no: qué condiciones ofrece una
 * categoría, cuántas tomas faltan para poder enviar, si el estado deja editar. El
 * vocabulario común —los tipos, las ocho tomas, el precio formateado— vive en
 * `shared/domain/listing.ts`, porque lo usan las dos mitades.
 *
 * <p>Estas reglas **no duplican** las del backend: son las que la pantalla necesita para
 * decidir qué pinta y qué habilita. Que la publicación se pueda enviar de verdad lo decide
 * el servidor, y si dice que no, lo dice con un 422 y su lista de campos.
 */

/** Las cuatro condiciones, en el orden en que se ofrecen. */
export const CONDICIONES: readonly Condition[] = ['NEW', 'LIKE_NEW', 'GOOD', 'WITH_FLAWS'];

/** Los quince colores, en el orden en que se ofrecen. */
export const COLORES: readonly Color[] = [
  'BLACK',
  'WHITE',
  'GRAY',
  'BEIGE',
  'BROWN',
  'RED',
  'PINK',
  'ORANGE',
  'YELLOW',
  'GREEN',
  'BLUE',
  'PURPLE',
  'GOLD',
  'SILVER',
  'MULTICOLOR',
];

/** Los grados que rotulan una posición de la secuencia. */
export function gradosDe(posicion: number): number {
  return posicion * (360 / TOMAS_DE_LA_SECUENCIA);
}

/**
 * Solo un borrador se edita libremente.
 *
 * <p>Sobre una publicada también se puede escribir, y por eso esto **no** decide si el
 * formulario está habilitado: decide si al guardar la publicación se queda donde está.
 * Lo que de verdad bloquea es `PENDING_REVIEW`, que el criterio 19 deja sin editar.
 */
export function admiteEdicion(estado: ListingStatus): boolean {
  return estado !== 'PENDING_REVIEW' && estado !== 'SOLD' && estado !== 'ARCHIVED';
}

/** Editar el contenido de una viva la devuelve a moderación (RN-062, criterio 27). */
export function editarDevuelveARevision(estado: ListingStatus): boolean {
  return estado === 'PUBLISHED' || estado === 'PAUSED';
}

/** Los estados desde los que tiene sentido ofrecer «enviar a revisión». */
export function admiteEnvio(estado: ListingStatus): boolean {
  return estado === 'DRAFT';
}

export function imagenesDeReferencia(publicacion: Listing): readonly ListingImage[] {
  return publicacion.images.filter((imagen) => imagen.kind === 'REFERENCE');
}

/**
 * Cuántas tomas faltan para poder enviar.
 *
 * <p>Se cuenta contra `requiredShots`, que **viene del servidor**: son ocho, o cuatro si
 * es tecnología declarada sellada, y esa regla es del dominio de allá (RN-065). Calcularla
 * aquí sería tener la misma regla en dos sitios con dos formas de estar mal.
 */
export function tomasQueFaltan(publicacion: Listing): number {
  return Math.max(publicacion.requiredShots - tomasDelVendedor(publicacion).length, 0);
}

/** Las canónicas que faltan, que son las que el envío rechaza aunque el total cuadre. */
export function canonicasQueFaltan(publicacion: Listing): readonly number[] {
  return POSICIONES_CANONICAS.filter((posicion) => tomaEn(publicacion, posicion) === null);
}

/**
 * Si tiene sentido ofrecer el botón de enviar.
 *
 * <p>Mira solo las fotos, que es lo que la pantalla puede contar sin equivocarse. **Que
 * los datos estén completos no se comprueba aquí**: lo decide el servidor con la categoría
 * delante, y responde 422 con la lista de campos que faltan. Reimplementar esa
 * comprobación en el cliente daría dos respuestas distintas a la misma pregunta.
 */
export function puedeIntentarEnviar(publicacion: Listing): boolean {
  return (
    admiteEnvio(publicacion.status) &&
    tomasQueFaltan(publicacion) === 0 &&
    canonicasQueFaltan(publicacion).length === 0
  );
}

/**
 * Las condiciones que una categoría admite.
 *
 * <p>RN-064: una categoría que no admite lo usado solo ofrece «nuevo». Esconder las otras
 * tres **no es la regla** —el servidor la comprueba igual y responde 422—, pero ofrecer
 * una opción que se va a rechazar es hacerle perder el tiempo a quien publica.
 */
export function condicionesAdmitidas(categoria: Category | null): readonly Condition[] {
  if (categoria !== null && !categoria.allowsUsed) {
    return ['NEW'];
  }
  return CONDICIONES;
}

/** Solo la tecnología declara sellado y garantía: es la única con medidas de dispositivo. */
export function esTecnologia(categoria: Category | null): boolean {
  return categoria !== null && categoria.familySlug === 'tech';
}
