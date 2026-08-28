import { describe, expect, it } from 'vitest';

import {
  ALTO_MINIMO,
  ANCHO_MINIMO,
  RELACION_FOTO,
  TOPE_DE_BYTES,
  comprimirBajoTope,
  cumpleMinimo,
  rectanguloDeRecorte,
} from './photo-crop';

/**
 * El recorte a 3:4 de toda foto de producto (RN-018) y la puerta del mínimo (RN-019).
 *
 * Lo que **no** se prueba aquí es que la imagen subida cumpla: eso lo comprueba el
 * servidor sobre los bytes que recibe, y la pantalla no reimplementa esa respuesta.
 */
describe('el recorte de una foto de producto', () => {
  /** La proporción del recorte, con la tolerancia del redondeo a píxel entero. */
  function relacionDe(ancho: number, alto: number): number {
    return rectanguloDeRecorte(ancho, alto).ancho / rectanguloDeRecorte(ancho, alto).alto;
  }

  describe('la proporción', () => {
    it('deja 3:4 una foto apaisada, que es lo que da la cámara en horizontal', () => {
      expect(relacionDe(4000, 3000)).toBeCloseTo(RELACION_FOTO, 3);
    });

    it('deja 3:4 una foto vertical más estrecha que 3:4', () => {
      // 9:16, que es lo que dan casi todos los teléfonos en vertical.
      expect(relacionDe(1080, 1920)).toBeCloseTo(RELACION_FOTO, 3);
    });

    it('deja 3:4 una foto cuadrada', () => {
      expect(relacionDe(2000, 2000)).toBeCloseTo(RELACION_FOTO, 3);
    });

    it('no toca una que ya viene en 3:4', () => {
      expect(rectanguloDeRecorte(900, 1200)).toEqual({ x: 0, y: 0, ancho: 900, alto: 1200 });
    });
  });

  describe('el encuadre', () => {
    it('centra el recorte cuando sobra ancho', () => {
      const recorte = rectanguloDeRecorte(4000, 3000);

      // 3000 de alto dan 2250 de ancho; sobran 1750, la mitad a cada lado.
      expect(recorte).toEqual({ x: 875, y: 0, ancho: 2250, alto: 3000 });
    });

    it('centra el recorte cuando sobra alto', () => {
      const recorte = rectanguloDeRecorte(1080, 1920);

      // 1080 de ancho dan 1440 de alto; sobran 480, la mitad arriba y abajo.
      expect(recorte).toEqual({ x: 0, y: 240, ancho: 1080, alto: 1440 });
    });

    /** Un borde transparente en el lienzo es lo que produce salirse por un píxel. */
    it('nunca se sale de la imagen de origen, ni con medidas impares', () => {
      const medidas: readonly (readonly [number, number])[] = [
        [1001, 1333],
        [999, 777],
        [1367, 1367],
        [3, 5],
      ];

      for (const [ancho, alto] of medidas) {
        const recorte = rectanguloDeRecorte(ancho, alto);

        expect(recorte.x).toBeGreaterThanOrEqual(0);
        expect(recorte.y).toBeGreaterThanOrEqual(0);
        expect(recorte.x + recorte.ancho).toBeLessThanOrEqual(ancho);
        expect(recorte.y + recorte.alto).toBeLessThanOrEqual(alto);
      }
    });

    it('se planta si la imagen de origen no tiene tamaño', () => {
      expect(() => rectanguloDeRecorte(0, 1200)).toThrow();
      expect(() => rectanguloDeRecorte(900, 0)).toThrow();
    });
  });

  describe('el mínimo de RN-019', () => {
    it('deja pasar el recorte que da 900 x 1200 justos', () => {
      expect(cumpleMinimo({ x: 0, y: 0, ancho: ANCHO_MINIMO, alto: ALTO_MINIMO })).toBe(true);
    });

    it('deja pasar lo que sobra de mínimo', () => {
      expect(cumpleMinimo(rectanguloDeRecorte(4000, 3000))).toBe(true);
    });

    /**
     * El caso que justifica medir sobre el recorte y no sobre el origen: los dos lados del
     * original pasan el mínimo y el recorte 3:4 se queda corto de ancho.
     */
    it('rechaza una cuadrada de 1000, cuyos dos lados pasarían el mínimo por separado', () => {
      const recorte = rectanguloDeRecorte(1000, 1000);

      expect(recorte).toEqual({ x: 125, y: 0, ancho: 750, alto: 1000 });
      expect(cumpleMinimo(recorte)).toBe(false);
    });

    it('rechaza lo que se queda corto por un píxel', () => {
      expect(cumpleMinimo({ x: 0, y: 0, ancho: ANCHO_MINIMO - 1, alto: ALTO_MINIMO })).toBe(false);
      expect(cumpleMinimo({ x: 0, y: 0, ancho: ANCHO_MINIMO, alto: ALTO_MINIMO - 1 })).toBe(false);
    });
  });

  /**
   * Criterio 9: «ninguna toma sale del dispositivo por encima de 500 KB».
   *
   * <p>El tope y la escalera de calidades son regla de producto, no detalle del lienzo, y
   * por eso se prueban aquí y no dentro del worker.
   */
  describe('el apretón hasta los 500 KB', () => {
    /** Un codificador falso: el tamaño baja conforme baja la calidad. */
    const codificadorQueDa = (porCalidad: Readonly<Record<string, number>>) => {
      const pedidas: number[] = [];
      const codificar = async (calidad: number): Promise<Blob> => {
        pedidas.push(calidad);
        return { size: porCalidad[String(calidad)] ?? 0 } as Blob;
      };
      return { codificar, pedidas };
    };

    it('se queda con la mejor calidad que cabe, y no prueba más', async () => {
      const { codificar, pedidas } = codificadorQueDa({ '0.85': 200_000 });

      const salida = await comprimirBajoTope(codificar);

      expect(salida?.size).toBe(200_000);
      expect(pedidas).toEqual([0.85]);
    });

    it('baja de calidad hasta que cabe', async () => {
      const { codificar, pedidas } = codificadorQueDa({
        '0.85': 900_000,
        '0.75': 700_000,
        '0.65': 400_000,
      });

      const salida = await comprimirBajoTope(codificar);

      expect(salida?.size).toBe(400_000);
      expect(pedidas).toEqual([0.85, 0.75, 0.65]);
    });

    it('acepta lo que cae justo en el tope', async () => {
      const { codificar } = codificadorQueDa({ '0.85': TOPE_DE_BYTES });

      expect((await comprimirBajoTope(codificar))?.size).toBe(TOPE_DE_BYTES);
    });

    it('rechaza un píxel por encima del tope en la calidad más baja', async () => {
      const { codificar } = codificadorQueDa({
        '0.85': 900_000,
        '0.75': 800_000,
        '0.65': 700_000,
        '0.55': TOPE_DE_BYTES + 1,
      });

      // Devolverla igualmente incumpliría el criterio 9, que no admite matices.
      expect(await comprimirBajoTope(codificar)).toBeNull();
    });
  });
});
