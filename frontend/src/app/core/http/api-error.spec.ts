import { describe, expect, it } from 'vitest';

import { toApiError } from './api-error';

describe('toApiError', () => {
  it('conserva el codigo, la traza y los errores por campo', () => {
    const error = toApiError(409, {
      type: 'https://sastra.co/errors/email-already-registered',
      title: 'email-already-registered',
      status: 409,
      detail: 'Codigo interno para trazabilidad',
      code: 'AUTH_EMAIL_TAKEN',
      traceId: '0af7651916cd43dd8448eb211c80319c',
      errors: [{ field: 'email', code: 'VALIDATION_ALREADY_EXISTS' }],
    });

    expect(error.status).toBe(409);
    expect(error.code).toBe('AUTH_EMAIL_TAKEN');
    expect(error.traceId).toBe('0af7651916cd43dd8448eb211c80319c');
    expect(error.fieldErrors).toEqual([{ field: 'email', code: 'VALIDATION_ALREADY_EXISTS' }]);
  });

  // El backend nunca manda texto para mostrar. Si "detail" se colara a la
  // pantalla, el usuario veria un mensaje interno y sin traducir.
  it('no expone ningun texto del servidor', () => {
    const error = toApiError(422, { code: 'AUTH_EMAIL_TAKEN', detail: 'texto interno' });

    expect(JSON.stringify(error.fieldErrors)).not.toContain('texto interno');
    expect(error.translationKey).toBe('errors.byCode.AUTH_EMAIL_TAKEN');
  });

  it('se degrada a un error sin codigo si el cuerpo no cumple el contrato', () => {
    for (const body of [null, undefined, 'vaya', 42]) {
      const error = toApiError(500, body);

      expect(error.code).toBeNull();
      expect(error.fieldErrors).toEqual([]);
      expect(error.translationKey).toBe('errors.fallback');
    }
  });

  it('descarta las entradas de errores mal formadas', () => {
    const error = toApiError(400, {
      errors: [
        { field: 'email' },
        { code: 'X' },
        null,
        { field: 'email', code: 'VALIDATION_ALREADY_EXISTS' },
      ],
    });

    expect(error.fieldErrors).toEqual([{ field: 'email', code: 'VALIDATION_ALREADY_EXISTS' }]);
  });

  it('reconoce el fallo de red por el estado cero', () => {
    const error = toApiError(0, null);

    expect(error.isNetworkFailure).toBe(true);
    expect(error.translationKey).toBe('errors.network');
  });

  it('sigue siendo un Error, para no romper quien lo capture como tal', () => {
    expect(toApiError(404, { code: 'AUTH_EMAIL_TAKEN' })).toBeInstanceOf(Error);
  });
});
