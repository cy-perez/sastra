import { describe, expect, it } from 'vitest';

import { cumpleElLargoMinimo, fuerzaDe, LARGO_MINIMO_DE_CONTRASENA } from './password-policy';

describe('politica de contrasena', () => {
  it('exige diez caracteres, ni uno menos RN-005', () => {
    expect(LARGO_MINIMO_DE_CONTRASENA).toBe(10);
    expect(cumpleElLargoMinimo('123456789')).toBe(false);
    expect(cumpleElLargoMinimo('1234567890')).toBe(true);
  });

  // RN-005 es explicita: la longitud protege mas que la complejidad artificial.
  it('acepta una frase larga sin simbolos ni mayusculas RN-005', () => {
    expect(cumpleElLargoMinimo('caballo bateria grapa')).toBe(true);
  });

  // Un emoji son dos unidades de codigo en JavaScript: contarlas con .length
  // dejaria pasar contrasenas mas cortas de lo que parecen.
  it('cuenta caracteres y no unidades de codigo RN-005', () => {
    expect(cumpleElLargoMinimo('👗👗👗👗👗')).toBe(false);
    expect(cumpleElLargoMinimo('ñáéíóúüñáé')).toBe(true);
  });

  it('la fuerza es orientativa y distingue tres niveles', () => {
    expect(fuerzaDe('corta')).toBe('corta');
    expect(fuerzaDe('doceletras1')).toBe('aceptable');
    expect(fuerzaDe('una frase larga')).toBe('buena');
    expect(fuerzaDe('dieciseiscaracte')).toBe('buena');
  });
});
