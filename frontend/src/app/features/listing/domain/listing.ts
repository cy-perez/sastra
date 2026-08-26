/**
 * La publicación de producto tal como la ve la pantalla. HU-007.
 *
 * TypeScript puro: sin Angular y sin `rxjs`, para que se pruebe sin TestBed
 * (frontend/CLAUDE.md).
 *
 * Los nombres son los del glosario y del contrato de la API. Las reglas que hay aquí
 * **no duplican** las del dominio del backend: son las que la pantalla necesita para
 * decidir qué pinta y qué habilita. Que la publicación se pueda enviar de verdad lo
 * decide el servidor, y si dice que no, lo dice con un 422 y su lista de campos.
 */

/** Los siete estados del glosario. */
export type ListingStatus =
  'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'PAUSED' | 'SOLD' | 'ARCHIVED';

/** Las cuatro del glosario. No hay una quinta. */
export type Condition = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'WITH_FLAWS';

export type SizeSystem = 'ALPHA' | 'NUMERIC_CO' | 'WAIST_INCHES' | 'FOOTWEAR_CO' | 'ONE_SIZE';

export type MeasurementKind =
  | 'CHEST'
  | 'WAIST'
  | 'HIP'
  | 'RISE'
  | 'SHOULDERS'
  | 'SLEEVE'
  | 'LENGTH'
  | 'INSOLE'
  | 'HEIGHT'
  | 'WIDTH'
  | 'DEPTH';

/** Lista cerrada, porque es filtro de catálogo y en texto libre no filtra nada. */
export type Color =
  | 'BLACK'
  | 'WHITE'
  | 'GRAY'
  | 'BEIGE'
  | 'BROWN'
  | 'RED'
  | 'PINK'
  | 'ORANGE'
  | 'YELLOW'
  | 'GREEN'
  | 'BLUE'
  | 'PURPLE'
  | 'GOLD'
  | 'SILVER'
  | 'MULTICOLOR';

/** Una toma del vendedor, o una imagen del fabricante. Nunca cuentan igual (RN-066). */
export type ImageKind = 'SELLER_SHOT' | 'REFERENCE';

export type ListingRejectionReason =
  | 'PHOTOS_UNUSABLE'
  | 'PHOTOS_MISMATCH'
  | 'MEASUREMENTS_UNRELIABLE'
  | 'CONDITION_MISDECLARED'
  | 'PROHIBITED_ITEM'
  | 'SUSPECTED_COUNTERFEIT'
  | 'PRICE_OUT_OF_RANGE';

export type AttentionReason = 'PRICE_OUT_OF_RANGE' | 'GALLERY_UPLOAD';

/** Siempre objeto explícito, nunca un número suelto (contrato-api.md). */
export interface Money {
  readonly amount: number;
  readonly currency: string;
}

export interface Size {
  readonly system: SizeSystem;
  readonly value: string;
}

export interface Shipping {
  readonly weightGrams: number;
  readonly lengthCm: number;
  readonly widthCm: number;
  readonly heightCm: number;
}

export interface ListingImage {
  readonly id: string;
  readonly kind: ImageKind;
  readonly position: number;
  readonly angleDegrees: number | null;
  readonly url: string;
}

export interface Product {
  readonly categoryId: string;
  readonly title: string | null;
  readonly description: string | null;
  readonly brand: string | null;
  readonly condition: Condition | null;
  readonly size: Size | null;
  readonly measurements: Readonly<Record<string, number>>;
  readonly color: Color | null;
  readonly price: Money | null;
  readonly shipping: Shipping | null;
  readonly isSealed: boolean | null;
  readonly warrantyMonths: number | null;
}

export interface Listing {
  readonly id: string;
  readonly sellerId: string;
  readonly status: ListingStatus;
  readonly product: Product;
  readonly images: readonly ListingImage[];
  readonly requiredShots: number;
  readonly requiresAttention: boolean;
  readonly attentionReasons: readonly AttentionReason[];
  readonly rejectionReason: ListingRejectionReason | null;
  readonly rejectionNote: string | null;
  readonly publishedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly version: number;
}

/** Una categoría del árbol, con lo que el formulario necesita de ella. */
export interface Category {
  readonly id: string;
  readonly slug: string;
  readonly nameEs: string;
  readonly nameEn: string;
  readonly familySlug: string | null;
  readonly sizeSystems: readonly SizeSystem[];
  readonly requiredMeasurements: readonly MeasurementKind[];
  readonly allowsUsed: boolean;
  readonly children: readonly Category[];
}

/** RN-017: ocho tomas, una cada 45 grados. */
export const TOMAS_DE_LA_SECUENCIA = 8;

/** RN-065: la tecnología sellada baja a cuatro, las del empaque. */
export const TOMAS_SI_ESTA_SELLADO = 4;

/**
 * Las cuatro que no pueden faltar: frente, lado, atrás y el otro lado (RN-016).
 *
 * <p>Son posiciones y no grados porque es lo que viaja a la API. Los grados se calculan
 * de la posición y solo existen para rotular.
 */
export const POSICIONES_CANONICAS: readonly number[] = [0, 2, 4, 6];

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

/** Las tomas del vendedor. Una imagen de referencia nunca cuenta (RN-066). */
export function tomasDelVendedor(publicacion: Listing): readonly ListingImage[] {
  return publicacion.images.filter((imagen) => imagen.kind === 'SELLER_SHOT');
}

export function imagenesDeReferencia(publicacion: Listing): readonly ListingImage[] {
  return publicacion.images.filter((imagen) => imagen.kind === 'REFERENCE');
}

/** La toma que ocupa una posición, si hay alguna. */
export function tomaEn(publicacion: Listing, posicion: number): ListingImage | null {
  return tomasDelVendedor(publicacion).find((imagen) => imagen.position === posicion) ?? null;
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

/** Las posiciones que el formulario pinta, según cuántas tomas se exigen. */
export function posicionesAPintar(publicacion: Listing): readonly number[] {
  if (publicacion.requiredShots === TOMAS_SI_ESTA_SELLADO) {
    return POSICIONES_CANONICAS;
  }
  return Array.from({ length: TOMAS_DE_LA_SECUENCIA }, (_, posicion) => posicion);
}

/** Todas las categorías hoja del árbol, aplanadas para buscar por identificador. */
export function categoriasHoja(arbol: readonly Category[]): readonly Category[] {
  return arbol.flatMap((familia) => familia.children);
}

export function categoriaPorId(arbol: readonly Category[], id: string): Category | null {
  return categoriasHoja(arbol).find((categoria) => categoria.id === id) ?? null;
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

/**
 * El precio, formateado en la configuración regional activa.
 *
 * <p>Con `Intl` y no con el pipe de moneda de Angular: es lo que pide
 * frontend/CLAUDE.md y no obliga a registrar datos de configuración regional en el
 * paquete. Sin decimales, porque el peso colombiano no los usa en precios de venta
 * (RN-029).
 */
export function precioFormateado(precio: Money, idioma: string): string {
  return new Intl.NumberFormat(idioma === 'en' ? 'en-CO' : 'es-CO', {
    style: 'currency',
    currency: precio.currency,
    maximumFractionDigits: 0,
  }).format(precio.amount);
}
