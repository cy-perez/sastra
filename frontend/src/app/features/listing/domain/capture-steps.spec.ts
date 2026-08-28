import { describe, expect, it } from 'vitest';

import {
  POSICIONES_CANONICAS,
  TOMAS_DE_LA_SECUENCIA,
  TOMAS_SI_ESTA_SELLADO,
  type Listing,
  type ListingImage,
} from '../../../shared/domain/listing';
import { admiteAsistente, pasosDeCaptura, pasosHechos, primerPasoPendiente } from './capture-steps';

/** Los ocho pasos del asistente de captura. HU-003 criterios 1 y 6. */
describe('los pasos del asistente de captura', () => {
  const toma = (posicion: number, kind: ListingImage['kind'] = 'SELLER_SHOT'): ListingImage => ({
    id: `imagen-${posicion}`,
    kind,
    position: posicion,
    angleDegrees: kind === 'SELLER_SHOT' ? posicion * 45 : null,
    url: `https://cdn.sendik.co/productos/${posicion}.jpg`,
  });

  const conImagenes = (
    images: readonly ListingImage[],
    requiredShots = TOMAS_DE_LA_SECUENCIA,
  ): Listing => ({
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
    requiredShots,
    requiresAttention: false,
    attentionReasons: [],
    rejectionReason: null,
    rejectionNote: null,
    publishedAt: null,
    createdAt: '2026-08-26T10:00:00Z',
    updatedAt: '2026-08-26T10:00:00Z',
    version: 1,
  });

  const publicacion = (
    posiciones: readonly number[],
    requiredShots = TOMAS_DE_LA_SECUENCIA,
  ): Listing =>
    conImagenes(
      posiciones.map((posicion) => toma(posicion)),
      requiredShots,
    );

  describe('a quién se le ofrece', () => {
    it('se ofrece a la secuencia de ocho', () => {
      expect(admiteAsistente(publicacion([]))).toBe(true);
    });

    /**
     * La historia lo deja fuera: cuatro tomas del empaque, sin giro que guiar y con
     * imágenes que no toma nadie con esta cámara (RN-065, RN-066).
     */
    it('no se ofrece a la tecnología declarada sellada', () => {
      expect(admiteAsistente(publicacion([], TOMAS_SI_ESTA_SELLADO))).toBe(false);
    });
  });

  describe('los ocho pasos', () => {
    it('son ocho, en orden de giro', () => {
      const pasos = pasosDeCaptura(publicacion([]));

      expect(pasos).toHaveLength(TOMAS_DE_LA_SECUENCIA);
      expect(pasos.map((paso) => paso.posicion)).toEqual([0, 1, 2, 3, 4, 5, 6, 7]);
    });

    it('van de 45 en 45 grados, empezando por el frente', () => {
      expect(pasosDeCaptura(publicacion([])).map((paso) => paso.grados)).toEqual([
        0, 45, 90, 135, 180, 225, 270, 315,
      ]);
    });

    /** RN-016: frontal, lateral derecha, posterior y lateral izquierda. */
    it('marca como canónicas las cuatro que no pueden faltar', () => {
      const canonicas = pasosDeCaptura(publicacion([]))
        .filter((paso) => paso.canonica)
        .map((paso) => paso.posicion);

      expect(canonicas).toEqual([...POSICIONES_CANONICAS]);
    });

    /** Criterio 1: el asistente da **el nombre** de cada toma, no solo sus grados. */
    it('da a cada paso una clave de nombre distinta, y ningún texto', () => {
      const nombres = pasosDeCaptura(publicacion([])).map((paso) => paso.nombre);

      expect(new Set(nombres).size).toBe(TOMAS_DE_LA_SECUENCIA);
      expect(nombres.every((nombre) => nombre.startsWith('listing.capture.shot.'))).toBe(true);
    });

    it('marca como hechas las posiciones que ya tienen toma', () => {
      const pasos = pasosDeCaptura(publicacion([0, 3]));

      expect(pasos.filter((paso) => paso.hecha).map((paso) => paso.posicion)).toEqual([0, 3]);
    });

    /** Una imagen de referencia nunca cuenta como toma del vendedor (RN-066). */
    it('no da por hecha una posición que solo tiene imagen de referencia', () => {
      const conReferencia = conImagenes([toma(0, 'REFERENCE')]);

      expect(pasosDeCaptura(conReferencia)[0]?.hecha).toBe(false);
    });
  });

  describe('dónde abre', () => {
    it('abre en el frente cuando no hay nada', () => {
      expect(primerPasoPendiente(publicacion([]))).toBe(0);
    });

    /** Quien retoma un borrador a medias quiere seguir, no repetir. */
    it('abre en la primera que falte, saltándose lo ya subido', () => {
      expect(primerPasoPendiente(publicacion([0, 1, 2]))).toBe(3);
    });

    it('salta al primer hueco aunque haya tomas después', () => {
      expect(primerPasoPendiente(publicacion([0, 1, 3, 4]))).toBe(2);
    });

    it('abre por el principio cuando ya están las ocho', () => {
      expect(primerPasoPendiente(publicacion([0, 1, 2, 3, 4, 5, 6, 7]))).toBe(0);
    });
  });

  describe('el progreso', () => {
    it('cuenta las que están puestas', () => {
      expect(pasosHechos(publicacion([]))).toBe(0);
      expect(pasosHechos(publicacion([0, 4]))).toBe(2);
      expect(pasosHechos(publicacion([0, 1, 2, 3, 4, 5, 6, 7]))).toBe(TOMAS_DE_LA_SECUENCIA);
    });
  });
});
