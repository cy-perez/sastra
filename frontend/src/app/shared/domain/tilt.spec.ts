import { describe, expect, it } from 'vitest';

import { BETA_DE_REFERENCIA, INCLINACION_MAXIMA_GRADOS, desviacion, estaNivelado } from './tilt';

/** El nivel del asistente de captura. HU-003 criterios 3 y 4. */
describe('el nivel del asistente de captura', () => {
  /** El teléfono de pie y sin ladear: la postura desde la que se rodea el producto. */
  const NIVELADO = { beta: BETA_DE_REFERENCIA, gamma: 0 };

  describe('la desviación', () => {
    it('es cero con el teléfono de pie y sin ladear', () => {
      expect(desviacion(NIVELADO.beta, NIVELADO.gamma)).toEqual({ frontal: 0, lateral: 0 });
    });

    it('mide el cabeceo contra la vertical, no contra el cero del sensor', () => {
      // 80 grados de beta es el teléfono echado hacia atrás 10, no 80.
      expect(desviacion(80, 0).frontal).toBe(10);
      expect(desviacion(100, 0).frontal).toBe(10);
    });

    it('mide el alabeo hacia los dos lados por igual', () => {
      expect(desviacion(BETA_DE_REFERENCIA, -12).lateral).toBe(12);
      expect(desviacion(BETA_DE_REFERENCIA, 12).lateral).toBe(12);
    });
  });

  describe('el obturador', () => {
    it('habilita con el teléfono nivelado', () => {
      expect(estaNivelado(NIVELADO.beta, NIVELADO.gamma)).toBe(true);
    });

    it('habilita justo en el límite de los 5 grados, en los dos ejes', () => {
      const limite = BETA_DE_REFERENCIA + INCLINACION_MAXIMA_GRADOS;

      expect(estaNivelado(limite, 0)).toBe(true);
      expect(estaNivelado(BETA_DE_REFERENCIA, INCLINACION_MAXIMA_GRADOS)).toBe(true);
    });

    /** Criterio 3: pasado el límite «en cualquier eje», el obturador se deshabilita. */
    it('deshabilita al pasarse de cabeceo aunque el alabeo esté perfecto', () => {
      expect(estaNivelado(BETA_DE_REFERENCIA + 6, 0)).toBe(false);
      expect(estaNivelado(BETA_DE_REFERENCIA - 6, 0)).toBe(false);
    });

    it('deshabilita al pasarse de alabeo aunque el cabeceo esté perfecto', () => {
      expect(estaNivelado(BETA_DE_REFERENCIA, 6)).toBe(false);
      expect(estaNivelado(BETA_DE_REFERENCIA, -6)).toBe(false);
    });

    it('deshabilita con el teléfono tumbado boca arriba, que es beta en cero', () => {
      expect(estaNivelado(0, 0)).toBe(false);
    });
  });

  /**
   * Criterio 4. Sin sensores el asistente sigue y avisa; **nunca se bloquea la
   * publicación por esto**. Un obturador muerto sin forma de salir sería justo eso.
   */
  describe('sin lectura del sensor', () => {
    it('habilita el obturador cuando no hay ninguna de las dos lecturas', () => {
      expect(estaNivelado(null, null)).toBe(true);
    });

    it('habilita el obturador aunque solo falte una de las dos', () => {
      expect(estaNivelado(null, 40)).toBe(true);
      expect(estaNivelado(0, null)).toBe(true);
    });
  });
});
