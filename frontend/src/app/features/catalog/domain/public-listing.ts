import {
  TOMAS_DE_LA_SECUENCIA,
  type Category,
  type Condition,
  type ListingImage,
  type Money,
  type Product,
} from '../../../shared/domain/listing';

/**
 * Una publicación tal como la ve cualquiera. HU-009.
 *
 * <p><strong>No es `Listing` con campos opcionales.</strong> `Listing` lleva la cocina de
 * la moderación —el estado, la versión, las marcas de atención, el motivo del rechazo— y
 * el catálogo no recibe nada de eso: el backend responde una forma distinta, no la misma
 * recortada. Declararlo aquí como un tipo propio hace que una plantilla del catálogo no
 * pueda pintar por descuido un campo que nunca va a llegar.
 *
 * <p>El estado tampoco está, y no falta: si algo aparece en el catálogo es porque está
 * publicado (RN-068).
 */
export interface PublicListing {
  readonly id: string;
  readonly sellerId: string;
  readonly product: Product;
  readonly images: readonly ListingImage[];
  readonly publishedAt: string | null;
}

/**
 * Un tramo del catálogo.
 *
 * <p>`nextCursor` es nulo en el último. Van los dos campos porque el cliente los usa para
 * cosas distintas: `hasMore` decide si se ofrece «ver más» y `nextCursor` es lo que se
 * manda al pulsarlo.
 */
export interface CatalogPage {
  readonly items: readonly PublicListing[];
  readonly nextCursor: string | null;
  readonly hasMore: boolean;
}

/** El vendedor, para la ficha y su perfil. */
export interface SellerProfile {
  readonly id: string;
  readonly name: string;
  readonly avatarUrl: string | null;
  readonly verified: boolean;
}

/**
 * La toma frontal, que es la de la tarjeta (RN-016).
 *
 * <p>Se busca por posición y no se toma la primera del arreglo: el orden en el que llegan
 * es cosa del servidor, y una tarjeta que enseñe la toma trasera porque el orden cambió es
 * un fallo que nadie mira dos veces.
 *
 * <p>Si no hay frontal se usa la primera que haya. No debería pasar —RN-017 exige la
 * secuencia completa para enviar a revisión— pero una tarjeta sin foto es peor que una
 * tarjeta con la foto equivocada.
 */
export function portada(publicacion: PublicListing): ListingImage | null {
  const frontal = publicacion.images.find((imagen) => imagen.position === 0);
  return frontal ?? publicacion.images[0] ?? null;
}

/** Si la publicación lleva alguna imagen que no tomó el vendedor (RN-066). */
export function tieneImagenDeReferencia(publicacion: PublicListing): boolean {
  return publicacion.images.some((imagen) => imagen.kind === 'REFERENCE');
}

/**
 * El nombre visible de una categoría en el idioma activo.
 *
 * <p>Vive aquí y no en la plantilla porque el árbol trae los dos idiomas juntos y elegir
 * uno es una decisión, no una interpolación.
 */
export function nombreDeCategoria(categoria: Category, idioma: string): string {
  return idioma.startsWith('en') ? categoria.nameEn : categoria.nameEs;
}

/**
 * La categoría del árbol que corresponde a un par de slugs de la dirección.
 *
 * <p>Devuelve nulo si no existe, que es lo que la pantalla convierte en no encontrado: una
 * categoría retirada del árbol no es un listado vacío (criterio 9).
 */
export function categoriaPorSlugs(
  arbol: readonly Category[],
  familia: string,
  categoria: string | null,
): Category | null {
  const rama = arbol.find((candidata) => candidata.slug === familia) ?? null;
  if (rama === null) {
    return null;
  }
  if (categoria === null) {
    return rama;
  }
  return rama.children.find((hija) => hija.slug === categoria) ?? null;
}

/** Lo que la tarjeta necesita del precio y la condición, sin que la plantilla los arme. */
export interface ResumenDeTarjeta {
  readonly titulo: string;
  readonly precio: Money | null;
  readonly condicion: Condition | null;
}

export function resumen(publicacion: PublicListing): ResumenDeTarjeta {
  return {
    titulo: publicacion.product.title ?? '',
    precio: publicacion.product.price,
    condicion: publicacion.product.condition,
  };
}

/**
 * La secuencia del visor 360: las tomas del vendedor, en orden de giro.
 *
 * <p>**Las imágenes de referencia quedan fuera** (RN-066). Son del fabricante y no del
 * producto que se recibe, así que meterlas en el giro haría que el producto cambiara de
 * aspecto a mitad de vuelta.
 */
export function secuenciaDeGiro(publicacion: PublicListing): readonly ListingImage[] {
  return publicacion.images
    .filter((imagen) => imagen.kind === 'SELLER_SHOT')
    .slice()
    .sort((una, otra) => una.position - otra.position);
}

/**
 * Si a esta publicación se le ofrece el visor giratorio.
 *
 * <p>Solo con la secuencia completa de ocho (RN-017). Es el caso borde de HU-003: «una
 * publicación antigua con menos de ocho tomas no ofrece visor y se muestra solo el
 * carrusel». Se exige la secuencia entera y no el mínimo de cuatro que el propio visor
 * necesita para girar sin saltos: con cuatro se puede girar, pero media vuelta enseñada
 * como si fuera entera engaña sobre lo que se está viendo.
 */
export function ofreceVisor(publicacion: PublicListing): boolean {
  return secuenciaDeGiro(publicacion).length === TOMAS_DE_LA_SECUENCIA;
}
