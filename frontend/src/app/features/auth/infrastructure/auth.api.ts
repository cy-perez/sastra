import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

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

interface VerifyEmailResponseDto {
  readonly email: string;
  readonly alreadyVerified: boolean;
}

export interface VerifiedEmail {
  readonly email: string;
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

    return { email: respuesta.email, alreadyVerified: respuesta.alreadyVerified };
  }

  async resendVerification(expiredToken: string): Promise<void> {
    await firstValueFrom(this.http.post<void>('auth/resend-verification', { expiredToken }));
  }
}
