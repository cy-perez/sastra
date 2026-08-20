import { describe, expect, it } from 'vitest';

import { formatearComision } from './business-figures';

/**
 * Criterio 8 de HU-005: la cifra sale de la configuracion y no de la plantilla.
 * Esta es la mitad que la convierte en texto.
 */
describe('formatearComision', () => {
  it('convierte la fraccion en porcentaje', () => {
    expect(formatearComision(0.05, 'es')).toContain('5');
    expect(formatearComision(0.05, 'es')).toContain('%');
  });

  // 0.05 es "5%", no "5.0%". Un decimal de mas en una cifra que se anuncia
  // parece precision y solo es ruido.
  it('no inventa decimales', () => {
    expect(formatearComision(0.05, 'en')).toBe('5%');
  });

  it('conserva los decimales cuando los hay', () => {
    expect(formatearComision(0.055, 'en')).toBe('5.5%');
  });

  /**
   * El motivo de usar Intl y no `rate * 100 + '%'`: en espanol el porcentaje
   * lleva un espacio duro antes del signo y en ingles no. Compuesto a mano se ve
   * mal en uno de los dos idiomas y nadie vuelve a mirarlo.
   */
  it('respeta la convencion de cada idioma', () => {
    expect(formatearComision(0.05, 'es')).not.toBe(formatearComision(0.05, 'en'));
  });

  // Si alguien cambia COMMISSION_RATE, el texto cambia con el. Es lo que impide
  // que la web anuncie un 5% mientras el sistema cobra otra cosa.
  it('sigue a la configuracion', () => {
    expect(formatearComision(0.08, 'en')).toBe('8%');
  });
});
