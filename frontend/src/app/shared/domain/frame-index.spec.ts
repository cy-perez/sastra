import { describe, expect, it } from 'vitest';

import { indiceDesde, normalizar } from './frame-index';
import { TOMAS_DE_LA_SECUENCIA } from './listing';

/**
 * El giro del visor 360. HU-003 criterio 12.
 *
 * Ocho fotogramas y un visor de 800 px dan 100 px por fotograma, que es la cuenta que
 * hace legibles todos los casos de abajo.
 */
describe('el giro del visor', () => {
  const ANCHO = 800;
  const POR_FOTOGRAMA = ANCHO / TOMAS_DE_LA_SECUENCIA;

  function desde(desplazamiento: number, indiceInicial = 0): number {
    return indiceDesde(desplazamiento, ANCHO, TOMAS_DE_LA_SECUENCIA, indiceInicial);
  }

  describe('el sentido', () => {
    /** Criterio 12: el sentido del giro coincide con el del movimiento. */
    it('arrastrar a la derecha avanza el fotograma', () => {
      expect(desde(POR_FOTOGRAMA)).toBe(1);
      expect(desde(POR_FOTOGRAMA * 2)).toBe(2);
    });

    it('arrastrar a la izquierda retrocede el fotograma', () => {
      expect(desde(-POR_FOTOGRAMA, 4)).toBe(3);
      expect(desde(-POR_FOTOGRAMA * 2, 4)).toBe(2);
    });

    it('no se mueve sin gesto', () => {
      expect(desde(0, 3)).toBe(3);
    });
  });

  describe('la escala', () => {
    it('da la vuelta entera al cruzar el visor de lado a lado', () => {
      // Ocho pasos sobre el inicial: la vuelta completa devuelve al mismo fotograma.
      expect(desde(ANCHO, 0)).toBe(0);
      expect(desde(ANCHO, 5)).toBe(5);
    });

    it('cambia de fotograma a mitad de camino entre uno y el siguiente', () => {
      expect(desde(POR_FOTOGRAMA * 0.49)).toBe(0);
      expect(desde(POR_FOTOGRAMA * 0.51)).toBe(1);
    });

    /** El gesto se siente igual en un teléfono estrecho que en una pantalla grande. */
    it('es relativo al ancho del visor, no a un número fijo de píxeles', () => {
      const estrecho = indiceDesde(160, 320, TOMAS_DE_LA_SECUENCIA, 0);
      const ancho = indiceDesde(400, 800, TOMAS_DE_LA_SECUENCIA, 0);

      // Medio visor es media vuelta en los dos: cuatro fotogramas de ocho.
      expect(estrecho).toBe(4);
      expect(ancho).toBe(4);
    });
  });

  describe('el círculo', () => {
    it('pasa del último al primero al seguir arrastrando a la derecha', () => {
      expect(desde(POR_FOTOGRAMA, 7)).toBe(0);
    });

    it('pasa del primero al último al arrastrar a la izquierda', () => {
      expect(desde(-POR_FOTOGRAMA, 0)).toBe(7);
    });

    it('aguanta varias vueltas en los dos sentidos', () => {
      expect(desde(ANCHO * 3 + POR_FOTOGRAMA, 0)).toBe(1);
      expect(desde(-(ANCHO * 3 + POR_FOTOGRAMA), 0)).toBe(7);
    });
  });

  describe('los bordes', () => {
    /**
     * Pasa entre hidratar y medir el diseño. Dividir por cero daría `NaN`, y un índice
     * `NaN` deja el visor en blanco sin que nada explique por qué.
     */
    it('no gira mientras el visor no tenga ancho medido', () => {
      expect(indiceDesde(200, 0, TOMAS_DE_LA_SECUENCIA, 3)).toBe(3);
    });

    it('se planta con una secuencia sin fotogramas', () => {
      expect(() => indiceDesde(100, ANCHO, 0, 0)).toThrow();
    });

    /** El caso borde de la historia: una publicación antigua con menos de ocho tomas. */
    it('gira igual con una secuencia de cuatro', () => {
      // Con cuatro fotogramas el visor de 800 px da 200 por fotograma.
      expect(indiceDesde(200, ANCHO, 4, 0)).toBe(1);
      expect(indiceDesde(-200, ANCHO, 4, 0)).toBe(3);
    });
  });

  describe('normalizar', () => {
    it('deja quieto lo que ya está dentro', () => {
      expect(normalizar(0, 8)).toBe(0);
      expect(normalizar(7, 8)).toBe(7);
    });

    /** `-1 % 8` es `-1` en JavaScript, y con eso el visor se saldría del arreglo. */
    it('devuelve al final lo negativo, que es donde el resto de JavaScript falla', () => {
      expect(normalizar(-1, 8)).toBe(7);
      expect(normalizar(-9, 8)).toBe(7);
    });

    it('devuelve al principio lo que se pasa', () => {
      expect(normalizar(8, 8)).toBe(0);
      expect(normalizar(17, 8)).toBe(1);
    });
  });
});
