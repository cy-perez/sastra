import { describe, expect, it } from 'vitest';

import type { Category, ListingImage } from '../../../shared/domain/listing';
import {
  categoriaPorSlugs,
  nombreDeCategoria,
  portada,
  tieneImagenDeReferencia,
  type PublicListing,
} from './public-listing';

/**
 * El vocabulario del catálogo. TypeScript puro, sin TestBed.
 */
describe('dominio del catálogo público', () => {
  describe('portada', () => {
    /** RN-016: la frontal es la de posición 0, no la primera del arreglo. */
    it('toma la de posición 0 aunque llegue en otro orden', () => {
      const publicacion = conImagenes([toma('trasera', 4), toma('frontal', 0), toma('lateral', 2)]);

      expect(portada(publicacion)?.id).toBe('frontal');
    });

    /**
     * Sin frontal se usa la que haya.
     *
     * <p>No debería pasar —RN-017 exige la secuencia completa para enviar a revisión— pero
     * una tarjeta sin foto es peor que una con la foto equivocada.
     */
    it('cae en la primera que haya si no hay ninguna en la posición 0', () => {
      const publicacion = conImagenes([toma('lateral', 2)]);

      expect(portada(publicacion)?.id).toBe('lateral');
    });

    it('devuelve nulo cuando no hay ninguna imagen', () => {
      expect(portada(conImagenes([]))).toBeNull();
    });
  });

  /** RN-066: hay que rotularla, así que primero hay que saber que está. */
  it('reconoce una imagen de referencia entre las tomas', () => {
    const conReferencia = conImagenes([toma('frontal', 0), referencia('caja')]);

    expect(tieneImagenDeReferencia(conReferencia)).toBe(true);
    expect(tieneImagenDeReferencia(conImagenes([toma('frontal', 0)]))).toBe(false);
  });

  describe('nombreDeCategoria', () => {
    it('usa el nombre en inglés solo cuando el idioma activo lo es', () => {
      const camisas = hoja('camisas-y-blusas', 'Camisas y blusas', 'Shirts and blouses');

      expect(nombreDeCategoria(camisas, 'es')).toBe('Camisas y blusas');
      expect(nombreDeCategoria(camisas, 'en')).toBe('Shirts and blouses');
    });

    /** El idioma activo llega como `es-CO`, no como `es` pelado. */
    it('reconoce una configuración regional completa', () => {
      const camisas = hoja('camisas-y-blusas', 'Camisas y blusas', 'Shirts and blouses');

      expect(nombreDeCategoria(camisas, 'es-CO')).toBe('Camisas y blusas');
      expect(nombreDeCategoria(camisas, 'en-US')).toBe('Shirts and blouses');
    });
  });

  describe('categoriaPorSlugs', () => {
    const camisas = hoja('camisas-y-blusas', 'Camisas y blusas', 'Shirts');
    const arbol: readonly Category[] = [familia('tops', 'Parte superior', 'Tops', [camisas])];

    it('resuelve una familia sola', () => {
      expect(categoriaPorSlugs(arbol, 'tops', null)?.slug).toBe('tops');
    });

    it('resuelve una hoja dentro de su familia', () => {
      expect(categoriaPorSlugs(arbol, 'tops', 'camisas-y-blusas')?.slug).toBe('camisas-y-blusas');
    });

    /**
     * Criterio 9: lo que no está en el árbol no existe.
     *
     * <p>Nulo y no la familia: devolver la familia haría que una dirección con una hoja
     * inventada pintara el listado de la familia entera, y quien compartió ese enlace
     * nunca sabría que apuntaba a otra cosa.
     */
    it('no resuelve una hoja que no está en el árbol', () => {
      expect(categoriaPorSlugs(arbol, 'tops', 'inventada')).toBeNull();
    });

    it('no resuelve una familia que no está en el árbol', () => {
      expect(categoriaPorSlugs(arbol, 'inventada', null)).toBeNull();
    });
  });
});

// --- apoyo -----------------------------------------------------------------

function conImagenes(images: readonly ListingImage[]): PublicListing {
  return {
    id: 'publicacion',
    sellerId: 'vendedor',
    images,
    publishedAt: '2026-08-27T15:00:00Z',
    product: {
      categoryId: 'camisas',
      title: 'Camisa de lino',
      description: 'Usada dos veces.',
      brand: null,
      condition: 'LIKE_NEW',
      size: { system: 'ALPHA', value: 'M' },
      measurements: {},
      color: 'BEIGE',
      price: { amount: 185000, currency: 'COP' },
      shipping: null,
      isSealed: null,
      warrantyMonths: null,
    },
  };
}

function toma(id: string, position: number): ListingImage {
  return { id, kind: 'SELLER_SHOT', position, angleDegrees: position * 45, url: `/${id}.jpg` };
}

function referencia(id: string): ListingImage {
  return { id, kind: 'REFERENCE', position: 0, angleDegrees: null, url: `/${id}.jpg` };
}

function familia(slug: string, nameEs: string, nameEn: string, children: Category[]): Category {
  return {
    id: slug,
    slug,
    nameEs,
    nameEn,
    familySlug: null,
    sizeSystems: [],
    requiredMeasurements: [],
    allowsUsed: true,
    children,
  };
}

function hoja(slug: string, nameEs: string, nameEn: string): Category {
  return {
    id: slug,
    slug,
    nameEs,
    nameEn,
    familySlug: 'tops',
    sizeSystems: ['ALPHA'],
    requiredMeasurements: ['CHEST'],
    allowsUsed: true,
    children: [],
  };
}
