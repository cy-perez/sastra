import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import type { Session } from '../../../core/session/session';
import type { Credentials } from '../domain/credentials';
import type { PasswordReset, PasswordResetRequest } from '../domain/password-reset';
import type { Registration } from '../domain/registration';

/**
 * Cuerpos que viajan por HTTP. Son de esta capa y no salen de ella: la plantilla
 * ve modelos de dominio, nunca un DTO (frontend/CLAUDE.md).
 */
interface RegisterRequestDto {
  readonly email: string;
  readonly password: string;
  readonly displayName: string;
  readonly birthDate: string;
  readonly locale: string;
  readonly acceptsTerms: boolean;
  readonly acceptsPrivacy: boolean;
}

/**
 * Respuesta de todo lo que abre o renueva una sesion.
 *
 * <p>No trae el token de refresco y no falta: viaja solo en la cookie HttpOnly
 * y el JavaScript no puede leerlo (ADR-0003).
 */
interface SessionResponseDto {
  readonly accessToken: string;
  readonly expiresIn: number;
  readonly user: {
    readonly email: string;
    readonly displayName: string;
    readonly emailVerified: boolean;
    readonly roles: readonly string[];
  };
}

interface VerifyEmailResponseDto {
  readonly session: SessionResponseDto;
  readonly alreadyVerified: boolean;
}

/** Verificar deja sesion abierta (criterio 9); la bandera solo cambia el texto. */
export interface VerifiedEmail {
  readonly session: Session;
  readonly alreadyVerified: boolean;
}

/**
 * Adaptador HTTP de las cuentas.
 *
 * <p>Las rutas van relativas: el interceptor les antepone la base de la API, que
 * es configuracion de ejecucion y no algo que esta capa deba conocer.
 */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  /**
   * El servidor responde 202 sin cuerpo tanto si la cuenta se creo como si el
   * correo ya existia. Es deliberado: distinguirlo aqui seria distinguirlo para
   * cualquiera (criterio 2 de HU-001).
   */
  async register(registro: Registration): Promise<void> {
    const cuerpo: RegisterRequestDto = {
      email: registro.email,
      password: registro.password,
      displayName: registro.displayName,
      birthDate: registro.birthDate,
      locale: registro.locale,
      acceptsTerms: registro.acceptsTerms,
      acceptsPrivacy: registro.acceptsPrivacy,
    };

    await firstValueFrom(this.http.post<void>('auth/register', cuerpo));
  }

  async verifyEmail(token: string): Promise<VerifiedEmail> {
    const respuesta = await firstValueFrom(
      this.http.post<VerifyEmailResponseDto>('auth/verify-email', { token }),
    );

    return {
      session: comoSesion(respuesta.session),
      alreadyVerified: respuesta.alreadyVerified,
    };
  }

  async resendVerification(expiredToken: string): Promise<void> {
    await firstValueFrom(this.http.post<void>('auth/resend-verification', { expiredToken }));
  }

  /** Criterio 10. La cookie del refresco la pone el servidor en la respuesta. */
  async login(credenciales: Credentials): Promise<Session> {
    const respuesta = await firstValueFrom(
      this.http.post<SessionResponseDto>('auth/login', {
        email: credenciales.email,
        password: credenciales.password,
      }),
    );

    return comoSesion(respuesta);
  }

  /**
   * Criterio 14. No lleva cuerpo: la credencial es la cookie, y mandar el token
   * tambien en el cuerpo anularia que sea HttpOnly.
   */
  async refresh(): Promise<Session> {
    const respuesta = await firstValueFrom(this.http.post<SessionResponseDto>('auth/refresh', {}));

    return comoSesion(respuesta);
  }

  /** Criterio 16: revoca en el servidor. Responde 204 aunque no hubiera cookie. */
  async logout(): Promise<void> {
    await firstValueFrom(this.http.post<void>('auth/logout', {}));
  }

  /** Criterio 13: el reenvio de quien ya entro pero no ha verificado su correo. */
  async requestEmailVerification(): Promise<void> {
    await firstValueFrom(this.http.post<void>('users/me/email-verification', {}));
  }

  /**
   * Criterio 19: el servidor responde 202 exista o no el correo, asi que aqui no
   * hay nada que distinguir. Devolver algo distinto en cada caso seria imposible,
   * y es justamente el punto.
   */
  async requestPasswordReset(peticion: PasswordResetRequest): Promise<void> {
    await firstValueFrom(this.http.post<void>('auth/forgot-password', { email: peticion.email }));
  }

  /** Criterio 20: responde 204 y ninguna sesion. Se vuelve a entrar a mano. */
  async resetPassword(cambio: PasswordReset): Promise<void> {
    await firstValueFrom(
      this.http.post<void>('auth/reset-password', {
        token: cambio.token,
        newPassword: cambio.newPassword,
      }),
    );
  }
}

/**
 * Se descarta expiresIn a proposito.
 *
 * <p>El refresco de esta version es reactivo: se dispara con el 401 que devuelve
 * el servidor, no con un temporizador. Guardar una duracion que nadie mira solo
 * invitaria a creer que hay un refresco programado que no existe. El dia que se
 * programe, el campo vuelve, y lo hace desde el DTO, que si lo trae.
 */
function comoSesion(dto: SessionResponseDto): Session {
  return {
    accessToken: dto.accessToken,
    user: {
      email: dto.user.email,
      displayName: dto.user.displayName,
      emailVerified: dto.user.emailVerified,
      roles: dto.user.roles,
    },
  };
}
