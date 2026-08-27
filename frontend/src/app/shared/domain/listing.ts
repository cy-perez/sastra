/**
 * El vocabulario de la publicación: los tipos y lo que las dos mitades comparten.
 *
 * TypeScript puro: sin Angular y sin `rxjs`, para que se pruebe sin TestBed
 * (frontend/CLAUDE.md).
 *
 * <p>Vive en `shared` porque lo usan las dos mitades del proceso y **una funcionalidad no
 * importa de otra** (frontend/CLAUDE.md): `listing` es lo que el vendedor rellena y
 * `listing-review` lo que el moderador mira. Es el mismo modelo —los mismos siete estados,
 * los mismos siete motivos de rechazo, las mismas ocho tomas— y duplicarlo en dos features
 * es la forma segura de que un día no coincidan.
 *
 * <p><strong>Aquí está solo lo que de verdad comparten.</strong> Con HU-008 subió el
 * archivo entero y eso dejó a `features/listing` sin capa `domain`, cuando la mitad de lo
 * que traía —qué condiciones ofrece una categoría, cuántas tomas faltan para enviar— es
 * del formulario del vendedor y de nadie más. Eso volvió a `features/listing/domain/`.
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

/**
 * En el orden en que se le ofrecen al moderador (HU-008).
 *
 * <p>Una constante y no un arreglo suelto en la plantilla: agregar un motivo es
 * agregarlo en un sitio. El orden no es alfabético; va de lo que más se rechaza a lo que
 * menos, que es lo que ahorra desplazamientos a quien revisa muchas al día. Es la misma
 * decisión que `MOTIVOS_DE_RECHAZO` en la verificación de vendedor.
 */
export const MOTIVOS_DE_RECHAZO_DE_PUBLICACION: readonly ListingRejectionReason[] = [
  'PHOTOS_UNUSABLE',
  'PHOTOS_MISMATCH',
  'MEASUREMENTS_UNRELIABLE',
  'CONDITION_MISDECLARED',
  'PRICE_OUT_OF_RANGE',
  'SUSPECTED_COUNTERFEIT',
  'PROHIBITED_ITEM',
];

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
  /**
   * Si la publicación es de quien pregunta. Solo llega cuando quien pregunta modera:
   * RN-063 le prohíbe decidir sobre lo suyo, y sin esto la pantalla del moderador no
   * puede avisarlo antes de que lo intente.
   *
   * <p>Nulo para el vendedor, que ya sabe que es suya.
   */
  readonly own?: boolean | null;
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

/** Las tomas del vendedor. Una imagen de referencia nunca cuenta (RN-066). */
export function tomasDelVendedor(publicacion: Listing): readonly ListingImage[] {
  return publicacion.images.filter((imagen) => imagen.kind === 'SELLER_SHOT');
}

/** La toma que ocupa una posición, si hay alguna. */
export function tomaEn(publicacion: Listing, posicion: number): ListingImage | null {
  return tomasDelVendedor(publicacion).find((imagen) => imagen.position === posicion) ?? null;
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
