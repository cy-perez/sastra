import { describe, expect, it } from 'vitest';

import { esMayorDeEdad } from './registration';

const HOY = new Date(2026, 7, 17); // 17 de agosto de 2026

describe('mayoria de edad RN-008', () => {
  it('acepta a quien cumple dieciocho hoy mismo', () => {
    expect(esMayorDeEdad('2008-08-17', HOY)).toBe(true);
  });

  it('rechaza a quien los cumple manana', () => {
    expect(esMayorDeEdad('2008-08-18', HOY)).toBe(false);
  });

  it('acepta a quien ya los cumplio hace anos', () => {
    expect(esMayorDeEdad('1990-03-04', HOY)).toBe(true);
  });

  it('rechaza una fecha ilegible en vez de dejarla pasar', () => {
    expect(esMayorDeEdad('', HOY)).toBe(false);
    expect(esMayorDeEdad('no-es-fecha', HOY)).toBe(false);
  });

  it('rechaza una fecha futura', () => {
    expect(esMayorDeEdad('2030-01-01', HOY)).toBe(false);
  });
});
