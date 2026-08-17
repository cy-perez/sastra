import { inject, Injectable } from '@angular/core';
import { injectMutation } from '@tanstack/angular-query-experimental';

import { ApiError } from '../../../core/http/api-error';
import { AuthApi } from '../infrastructure/auth.api';
import type { Registration } from '../domain/registration';

/**
 * Casos de uso de cuentas para la interfaz.
 *
 * <p>Envuelve TanStack Query: los componentes no ven la libreria, solo senales
 * de estado y una funcion que ejecutar (frontend/CLAUDE.md).
 *
 * <p>Un registro no es una consulta, es una mutacion: no se cachea, no se
 * reintenta solo y no se vuelve a ejecutar al volver a la pestana.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);

  readonly registration = injectMutation(() => ({
    mutationFn: (registro: Registration) => this.api.register(registro),
    // Sin reintentos: un registro repetido por la libreria hashearia la
    // contrasena dos veces en el servidor sin que nadie lo pidiera.
    retry: false,
  }));

  readonly verification = injectMutation(() => ({
    mutationFn: (token: string) => this.api.verifyEmail(token),
    retry: false,
  }));

  readonly resend = injectMutation(() => ({
    mutationFn: (expiredToken: string) => this.api.resendVerification(expiredToken),
    retry: false,
  }));

  /**
   * Traduce el fallo de una mutacion a una clave de Transloco.
   *
   * <p>El servidor nunca manda texto para mostrar: manda un codigo estable y el
   * texto sale de aqui (docs/arquitectura/contrato-api.md).
   */
  static claveDeError(error: unknown): string {
    return error instanceof ApiError ? error.translationKey : 'errors.fallback';
  }
}
