import { describe, expect, it } from 'vitest';

import {
  admiteEdicion,
  admiteEnvio,
  canonicasQueFaltan,
  categoriaPorId,
  condicionesAdmitidas,
  editarDevuelveARevision,
  esTecnologia,
  gradosDe,
  imagenesDeReferencia,
  posicionesAPintar,
  puedeIntentarEnviar,
  tomaEn,
  tomasDelVendedor,
  tomasQueFaltan,
  type Category,
  type Listing,
  type ListingImage,
  type ListingStatus,
} from './listing';

/**
 * Las reglas que la pantalla necesita para decidir qué pinta y qué habilita.
 *
 * Lo que **no** se prueba aquí es si la publicación está completa: eso lo decide el
 * servidor con la categoría delante, y la pantalla no lo reimplementa.
 */
describe('listing, lo que la pantalla decide', () => {
  describe('estados', () => {
    it('deja editar en borrador, publicada, pausada y rechazada', () => {
      const editables: ListingStatus[] = ['DRAFT', 'PUBLISHED', 'PAUSED', 'REJECTED'];

      expect(editables.every(admiteEdicion)).toBe(true);
    });

    /** Criterio 19: en revisión no se edita. Criterio 32: vendida es terminal. */
    it('no deja editar en revisión, vendida ni archivada', () => {
      expect(admiteEdicion('PENDING_REVIEW')).toBe(false);
      expect(admiteEdicion('SOLD')).toBe(false);
      expect(admiteEdicion('ARCHIVED')).toBe(false);
    });

    /** RN-062: editar lo que describe el producto devuelve a moderación. */
    it('avisa de que editar una viva la devuelve a revisión', () => {
      expect(editarDevuelveARevision('PUBLISHED')).toBe(true);
      expect(editarDevuelveARevision('PAUSED')).toBe(true);
      expect(editarDevuelveARevision('DRAFT')).toBe(false);
    });

    /** Desde rechazada hay que retomar primero: no es una transición válida. */
    it('solo ofrece enviar desde borrador', () => {
      expect(admiteEnvio('DRAFT')).toBe(true);
      expect(admiteEnvio('REJECTED')).toBe(false);
      expect(admiteEnvio('PUBLISHED')).toBe(false);
    });
  });

  describe('las tomas', () => {
    it('cuenta cuántas faltan contra lo que exige el servidor', () => {
      expect(tomasQueFaltan(publicacionCon(tomas(0, 1, 2)))).toBe(5);
      expect(tomasQueFaltan(publicacionCon(tomas(0, 1, 2, 3, 4, 5, 6, 7)))).toBe(0);
    });

    /** Nunca negativo: con más tomas que las exigidas, faltan cero y no menos que cero. */
    it('no cuenta negativo cuando sobran', () => {
      const sellada = { ...publicacionCon(tomas(0, 2, 4, 6)), requiredShots: 4 };

      expect(tomasQueFaltan(sellada)).toBe(0);
    });

    /** RN-066: una imagen de referencia nunca cuenta como toma. */
    it('no cuenta las imágenes de referencia', () => {
      const conReferencia = publicacionCon([...tomas(0, 1), referencia(0)]);

      expect(tomasDelVendedor(conReferencia)).toHaveLength(2);
      expect(imagenesDeReferencia(conReferencia)).toHaveLength(1);
      expect(tomasQueFaltan(conReferencia)).toBe(6);
    });

    /** RN-016: las cuatro canónicas se piden aunque el total cuadre. */
    it('dice qué canónicas faltan', () => {
      expect(canonicasQueFaltan(publicacionCon(tomas(0, 1, 2, 3)))).toEqual([4, 6]);
      expect(canonicasQueFaltan(publicacionCon(tomas(0, 2, 4, 6)))).toEqual([]);
    });

    it('encuentra la toma de una posición', () => {
      const publicacion = publicacionCon(tomas(0, 3));

      expect(tomaEn(publicacion, 3)?.position).toBe(3);
      expect(tomaEn(publicacion, 5)).toBeNull();
    });

    it('rotula cada posición con sus grados', () => {
      expect(gradosDe(0)).toBe(0);
      expect(gradosDe(2)).toBe(90);
      expect(gradosDe(7)).toBe(315);
    });

    /** RN-065: la sellada pide cuatro, y son las canónicas del empaque. */
    it('pinta cuatro casillas en la tecnología sellada y ocho en el resto', () => {
      const sellada = { ...publicacionCon([]), requiredShots: 4 };

      expect(posicionesAPintar(sellada)).toEqual([0, 2, 4, 6]);
      expect(posicionesAPintar(publicacionCon([]))).toEqual([0, 1, 2, 3, 4, 5, 6, 7]);
    });
  });

  describe('poder enviar', () => {
    it('ofrece enviar con las ocho y las cuatro canónicas', () => {
      expect(puedeIntentarEnviar(publicacionCon(tomas(0, 1, 2, 3, 4, 5, 6, 7)))).toBe(true);
    });

    it('no ofrece enviar si falta una canónica aunque el total cuadre', () => {
      expect(puedeIntentarEnviar(publicacionCon(tomas(0, 1, 2, 3, 5, 6, 7, 8)))).toBe(false);
    });

    it('no ofrece enviar desde un estado que no lo admite', () => {
      const enRevision = {
        ...publicacionCon(tomas(0, 1, 2, 3, 4, 5, 6, 7)),
        status: 'PENDING_REVIEW' as ListingStatus,
      };

      expect(puedeIntentarEnviar(enRevision)).toBe(false);
    });
  });

  describe('categorías', () => {
    const camisas: Category = hoja('camisas-y-blusas', 'tops', true);
    const celulares: Category = hoja('celulares-y-tabletas', 'tech', false);
    const arbol: Category[] = [familia('tops', [camisas]), familia('tech', [celulares])];

    it('encuentra una hoja por su identificador', () => {
      expect(categoriaPorId(arbol, camisas.id)?.slug).toBe('camisas-y-blusas');
      expect(categoriaPorId(arbol, 'no-existe')).toBeNull();
    });

    /** Una familia no es publicable: no puede salir de la búsqueda por identificador. */
    it('no devuelve una familia', () => {
      expect(categoriaPorId(arbol, 'familia-tops')).toBeNull();
    });

    /** RN-064: la tecnología solo se vende nueva. */
    it('ofrece solo «nuevo» donde no se admite lo usado', () => {
      expect(condicionesAdmitidas(celulares)).toEqual(['NEW']);
      expect(condicionesAdmitidas(camisas)).toHaveLength(4);
    });

    /** Sin categoría elegida todavía se ofrecen las cuatro: no hay nada que restringir. */
    it('ofrece las cuatro cuando no hay categoría', () => {
      expect(condicionesAdmitidas(null)).toHaveLength(4);
    });

    it('reconoce la tecnología por su familia', () => {
      expect(esTecnologia(celulares)).toBe(true);
      expect(esTecnologia(camisas)).toBe(false);
      expect(esTecnologia(null)).toBe(false);
    });
  });
});

function publicacionCon(images: readonly ListingImage[]): Listing {
  return {
    id: 'af8b9a52-4a3f-4a52-9a1e-8d9a2f1c4b70',
    sellerId: 'b1c2d3e4-0000-4000-8000-000000000001',
    status: 'DRAFT',
    product: {
      categoryId: 'c1c2d3e4-0000-4000-8000-000000000002',
      title: 'Camisa de lino color hueso',
      description: 'Usada dos veces.',
      brand: null,
      condition: 'LIKE_NEW',
      size: { system: 'ALPHA', value: 'M' },
      measurements: { CHEST: 52 },
      color: 'BEIGE',
      price: { amount: 185000, currency: 'COP' },
      shipping: { weightGrams: 600, lengthCm: 30, widthCm: 20, heightCm: 10 },
      isSealed: null,
      warrantyMonths: null,
    },
    images,
    requiredShots: 8,
    requiresAttention: false,
    attentionReasons: [],
    rejectionReason: null,
    rejectionNote: null,
    publishedAt: null,
    createdAt: '2026-08-26T10:00:00Z',
    updatedAt: '2026-08-26T10:00:00Z',
    version: 1,
  };
}

function tomas(...posiciones: number[]): ListingImage[] {
  return posiciones.map((position) => ({
    id: `toma-${position}`,
    kind: 'SELLER_SHOT',
    position,
    angleDegrees: gradosDe(position),
    url: `https://cdn.sendik.co/productos/${position}.jpg`,
  }));
}

function referencia(position: number): ListingImage {
  return {
    id: `referencia-${position}`,
    kind: 'REFERENCE',
    position,
    angleDegrees: null,
    url: `https://cdn.sendik.co/productos/ref-${position}.jpg`,
  };
}

function familia(slug: string, children: Category[]): Category {
  return {
    id: `familia-${slug}`,
    slug,
    nameEs: slug,
    nameEn: slug,
    familySlug: null,
    sizeSystems: [],
    requiredMeasurements: [],
    allowsUsed: true,
    children,
  };
}

function hoja(slug: string, familySlug: string, allowsUsed: boolean): Category {
  return {
    id: `hoja-${slug}`,
    slug,
    nameEs: slug,
    nameEn: slug,
    familySlug,
    sizeSystems: ['ALPHA'],
    requiredMeasurements: ['CHEST'],
    allowsUsed,
    children: [],
  };
}
