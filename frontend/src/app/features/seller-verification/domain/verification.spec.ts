import { describe, expect, it } from 'vitest';

import {
  admiteEdicion,
  agotoIntentos,
  pasoEntregado,
  puedeEnviar,
  puedeReintentar,
  siguientePaso,
  type SellerVerification,
} from './verification';

/** Sin TestBed: es TypeScript puro (frontend/CLAUDE.md). */
describe('verificación de vendedor', () => {
  const vacia: SellerVerification = {
    status: 'IN_PROGRESS',
    attempts: 0,
    remainingAttempts: 3,
    complete: false,
    documentSubmitted: false,
    documentType: null,
    documentNumberLastFour: null,
    documentHolderName: null,
    selfieSubmitted: false,
    bank: null,
    bankAccountType: null,
    bankAccountLastFour: null,
    bankAccountHolderName: null,
    rejectionReason: null,
    rejectionNote: null,
    updatedAt: '2026-08-21T15:00:00Z',
  };

  const completa: SellerVerification = {
    ...vacia,
    complete: true,
    documentSubmitted: true,
    documentType: 'CC',
    documentNumberLastFour: '2947',
    documentHolderName: 'Ana Maria Garcia',
    selfieSubmitted: true,
    bank: 'bancolombia',
    bankAccountType: 'SAVINGS',
    bankAccountLastFour: '3456',
    bankAccountHolderName: 'Ana Maria Garcia',
  };

  it('el siguiente paso es el primer hueco, no el último que se tocó', () => {
    // El caso borde de HU-002: se retoma donde iba.
    expect(siguientePaso(vacia)).toBe('document');
    expect(siguientePaso({ ...vacia, documentSubmitted: true })).toBe('selfie');
    expect(siguientePaso({ ...vacia, documentSubmitted: true, selfieSubmitted: true })).toBe(
      'bank',
    );
  });

  it('lleva al hueco aunque los pasos se hayan hecho en otro orden', () => {
    const soloBanco = { ...vacia, bank: 'nequi' };

    expect(siguientePaso(soloBanco)).toBe('document');
  });

  it('no queda ningún paso cuando están los tres', () => {
    expect(siguientePaso(completa)).toBeNull();
  });

  it('sabe qué paso está entregado', () => {
    expect(pasoEntregado(completa, 'document')).toBe(true);
    expect(pasoEntregado(completa, 'selfie')).toBe(true);
    expect(pasoEntregado(completa, 'bank')).toBe(true);
    expect(pasoEntregado(vacia, 'bank')).toBe(false);
  });

  it('permite enviar cuando está completa y quedan intentos', () => {
    expect(puedeEnviar(completa)).toBe(true);
  });

  it('no permite enviar si falta un paso', () => {
    expect(puedeEnviar({ ...completa, complete: false })).toBe(false);
  });

  /**
   * `complete` viene del servidor e incluye la coincidencia de titular de RN-012.
   * Comparar los dos nombres aquí sería reimplementar la regla en el cliente, y con otro
   * criterio: el servidor normaliza acentos y espacios.
   */
  it('no permite enviar si el servidor dice que no está completa, aunque los tres pasos se vean', () => {
    const conTitularDistinto = {
      ...completa,
      complete: false,
      bankAccountHolderName: 'Pedro Ramirez',
    };

    expect(puedeEnviar(conTitularDistinto)).toBe(false);
  });

  it('no permite enviar lo que ya está en revisión', () => {
    expect(puedeEnviar({ ...completa, status: 'PENDING_REVIEW' })).toBe(false);
  });

  it('no permite enviar sin intentos, aunque esté completa', () => {
    expect(puedeEnviar({ ...completa, remainingAttempts: 0 })).toBe(false);
  });

  it('permite reintentar tras un rechazo o una revocación', () => {
    expect(
      puedeReintentar({ ...completa, status: 'REJECTED', attempts: 1, remainingAttempts: 2 }),
    ).toBe(true);
    expect(puedeReintentar({ ...completa, status: 'REVOKED', remainingAttempts: 2 })).toBe(true);
  });

  it('no permite reintentar sin intentos: RN-014 exige revisión manual', () => {
    expect(
      puedeReintentar({ ...completa, status: 'REJECTED', attempts: 3, remainingAttempts: 0 }),
    ).toBe(false);
    expect(agotoIntentos({ ...completa, remainingAttempts: 0 })).toBe(true);
  });

  /** Una solicitud enviada no se toca mientras alguien la mira (RN-059). */
  it('no admite edición en revisión ni una vez verificada', () => {
    expect(admiteEdicion({ ...completa, status: 'PENDING_REVIEW' })).toBe(false);
    expect(admiteEdicion({ ...completa, status: 'VERIFIED' })).toBe(false);
    expect(admiteEdicion(completa)).toBe(true);
  });
});
