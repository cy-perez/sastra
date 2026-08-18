import { describe, expect, it } from 'vitest';

import { laConfirmacionCoincide } from './account';

describe('laConfirmacionCoincide', () => {
  it('acepta el correo exacto', () => {
    expect(laConfirmacionCoincide('ana@correo.co', 'ana@correo.co')).toBe(true);
  });

  // La misma normalizacion que hace el servidor: quien lo escribe con mayusculas
  // o deja un espacio al pegar no se esta equivocando de cuenta.
  it('ignora mayusculas y espacios de los extremos', () => {
    expect(laConfirmacionCoincide('  ANA@Correo.CO ', 'ana@correo.co')).toBe(true);
  });

  it('rechaza cualquier otra cosa', () => {
    expect(laConfirmacionCoincide('otra@correo.co', 'ana@correo.co')).toBe(false);
    expect(laConfirmacionCoincide('', 'ana@correo.co')).toBe(false);
    expect(laConfirmacionCoincide('ana', 'ana@correo.co')).toBe(false);
  });
});
