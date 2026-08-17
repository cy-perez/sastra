import { describe, expect, it } from 'vitest';

import { esCorreoValido, faltaLaContrasena } from './credentials';

describe('esCorreoValido', () => {
  it.each(['ana@correo.co', 'ana.maria@mi-correo.com.co', 'a+etiqueta@correo.com'])(
    'acepta %s',
    (valor) => {
      expect(esCorreoValido(valor)).toBe(true);
    },
  );

  it.each(['', 'ana', 'ana@', '@correo.co', 'ana@correo', 'ana correo@x.co'])(
    'rechaza %s',
    (valor) => {
      expect(esCorreoValido(valor)).toBe(false);
    },
  );

  // Un correo copiado de otra parte llega con espacios y no es culpa de nadie.
  it('ignora los espacios de los extremos', () => {
    expect(esCorreoValido('  ana@correo.co  ')).toBe(true);
  });
});

describe('faltaLaContrasena', () => {
  it('solo se queja de la cadena vacia', () => {
    expect(faltaLaContrasena('')).toBe(true);
    expect(faltaLaContrasena('x')).toBe(false);
  });

  // Una contrasena puede ser solo espacios: es rara, pero es la suya. Recortarla
  // aqui la convertiria en vacia y la dejaria fuera de su propia cuenta.
  it('no recorta: los espacios son parte de la contrasena', () => {
    expect(faltaLaContrasena('   ')).toBe(false);
  });
});
