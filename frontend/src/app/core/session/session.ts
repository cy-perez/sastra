/**
 * La sesion tal como la usa la interfaz. TypeScript puro, sin Angular.
 *
 * <p>Vive en `core` y no en `features/auth` porque la necesitan tres sitios que
 * no pueden depender de una funcionalidad concreta: el interceptor que adjunta
 * el token, el que lo renueva, y la cabecera del sitio. Lo que si es de la
 * funcionalidad es como se obtiene, y eso vive en su adaptador.
 *
 * <p>Es distinta del DTO que viaja por HTTP. En particular, aqui no esta el
 * token de refresco: viaja solo en la cookie HttpOnly y el JavaScript no lo ve
 * nunca (ADR-0003).
 */
export interface SessionUser {
  readonly email: string;
  readonly displayName: string;
  /**
   * Criterio 13 de HU-001: una cuenta sin verificar entra igual, pero solo ve el
   * aviso pendiente. Es esta bandera la que lo decide en pantalla.
   */
  readonly emailVerified: boolean;
  readonly roles: readonly string[];
}

export interface Session {
  readonly accessToken: string;
  readonly user: SessionUser;
}
