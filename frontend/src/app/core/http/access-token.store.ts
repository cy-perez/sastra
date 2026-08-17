import { Injectable, signal } from '@angular/core';

/**
 * El token de acceso vive en memoria y solo en memoria.
 *
 * No va a localStorage ni a sessionStorage: cualquier script inyectado en la
 * pagina podria leerlo de ahi. La sesion se recupera con el token de refresco,
 * que viaja en una cookie HttpOnly que el JavaScript no puede ver.
 * Ver docs/arquitectura/contrato-api.md.
 */
@Injectable({ providedIn: 'root' })
export class AccessTokenStore {
  private readonly token = signal<string | null>(null);

  readonly isAuthenticated = signal(false);

  get(): string | null {
    return this.token();
  }

  set(token: string): void {
    this.token.set(token);
    this.isAuthenticated.set(true);
  }

  clear(): void {
    this.token.set(null);
    this.isAuthenticated.set(false);
  }
}
