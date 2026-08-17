import { inject, Injectable } from '@angular/core';
import { injectMutation } from '@tanstack/angular-query-experimental';

import { ApiError } from '../../../core/http/api-error';
import { SessionStore } from '../../../core/session/session.store';
import { AuthApi } from '../infrastructure/auth.api';
import type { Credentials } from '../domain/credentials';
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
  private readonly sesion = inject(SessionStore);

  readonly registration = injectMutation(() => ({
    mutationFn: (registro: Registration) => this.api.register(registro),
    // Sin reintentos: un registro repetido por la libreria hashearia la
    // contrasena dos veces en el servidor sin que nadie lo pidiera.
    retry: false,
  }));

  /** Criterio 9: al verificar se entra directamente, sin escribir la contrasena. */
  readonly verification = injectMutation(() => ({
    mutationFn: (token: string) => this.api.verifyEmail(token),
    retry: false,
    onSuccess: (verificado) => {
      this.sesion.set(verificado.session);
    },
  }));

  readonly resend = injectMutation(() => ({
    mutationFn: (expiredToken: string) => this.api.resendVerification(expiredToken),
    retry: false,
  }));

  /**
   * Criterio 10. Sin reintentos por partida doble: un reintento automatico
   * gastaria uno de los cinco intentos que RN-006 permite antes de bloquear, sin
   * que la persona hubiera escrito nada dos veces.
   */
  readonly login = injectMutation(() => ({
    mutationFn: (credenciales: Credentials) => this.api.login(credenciales),
    retry: false,
    onSuccess: (abierta) => {
      this.sesion.set(abierta);
      // Solo la otra: reset() sobre la mutacion que esta corriendo cancela los
      // callbacks que le paso quien la invoco, y el ingreso se quedaria sin
      // navegar. La suya la limpia la pantalla, que es la ultima en actuar.
      this.registration.reset();
    },
  }));

  /**
   * Criterio 16. La sesion local se limpia pase lo que pase.
   *
   * <p>Si la llamada falla, el servidor puede haber revocado igual y el navegador
   * no tiene forma de saberlo. Dejar la pantalla como si la persona siguiera
   * dentro es lo unico que seguro esta mal: pulso salir.
   */
  readonly logout = injectMutation(() => ({
    mutationFn: () => this.api.logout(),
    retry: false,
    onSettled: () => {
      this.sesion.clear();
      this.olvidarLoEscrito();
    },
  }));

  /** Criterio 13: el reenvio desde dentro, para quien entro sin verificar. */
  readonly emailVerificationRequest = injectMutation(() => ({
    mutationFn: () => this.api.requestEmailVerification(),
    retry: false,
  }));

  /**
   * Borra del estado de las mutaciones lo que la persona escribio.
   *
   * <p>TanStack conserva en cada mutacion sus {@code variables} y su
   * {@code data} mientras viva la pestana, y este almacen es de raiz: destruir
   * el formulario no borra esa copia. Sin esto, despues de entrar y salir,
   * {@code login.variables()} seguia devolviendo la contrasena en claro y
   * {@code login.data()} el token de la sesion anterior. En un equipo compartido
   * "cerre sesion" tiene que significar que no queda nada recuperable
   * (docs/operacion/datos-personales.md).
   *
   * <p>Es el ultimo cierre, no el unico: cada pantalla limpia lo suyo en cuanto
   * deja de necesitarlo. Esto recoge lo que quede.
   */
  private olvidarLoEscrito(): void {
    this.login.reset();
    this.registration.reset();
  }

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
