import { computed, Injectable, signal } from '@angular/core';

import type { Session } from './session';

/**
 * La sesion vive en memoria y solo en memoria.
 *
 * <p>No va a localStorage ni a sessionStorage: cualquier script inyectado en la
 * pagina podria leer el token de ahi. Al recargar se pierde a proposito y se
 * recupera con el token de refresco, que viaja en una cookie HttpOnly que el
 * JavaScript no puede ver. Ver docs/arquitectura/contrato-api.md.
 *
 * <p>Sustituye al anterior AccessTokenStore, que solo guardaba la cadena del
 * token. El refresco devuelve la sesion completa y el usuario cambia con ella
 * (al verificar el correo, por ejemplo): con dos almacenes separados uno de los
 * dos quedaba viejo sin que nada lo delatara.
 */
/**
 * Los tres estados en que puede estar la sesion.
 *
 * <p>El tercero, `desconocida`, es el que suele faltar y el que se nota. Al
 * cargar la pagina el token vive en memoria y por tanto no existe todavia: hay
 * que preguntarle al servidor con la cookie de refresco. Sin este estado, ese
 * rato se pinta como "no hay sesion", y a quien si la tiene le aparece "Entrar"
 * y despues su nombre. Ese cambio bajo el cursor es peor que esperar un momento.
 */
export type SessionStatus = 'desconocida' | 'anonima' | 'abierta';

@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly sesion = signal<Session | null>(null);

  /**
   * Si ya se sabe la respuesta, sea cual sea. Empieza en falso: nadie ha
   * preguntado todavia. En el servidor se queda asi, y es correcto, porque el
   * renderizado no tiene la cookie de nadie.
   */
  private readonly resuelta = signal(false);

  readonly current = this.sesion.asReadonly();

  readonly status = computed<SessionStatus>(() => {
    if (!this.resuelta()) {
      return 'desconocida';
    }
    return this.sesion() === null ? 'anonima' : 'abierta';
  });

  readonly isAuthenticated = computed(() => this.sesion() !== null);

  readonly user = computed(() => this.sesion()?.user ?? null);

  /**
   * Falso tambien cuando no hay sesion. Quien pregunta por el aviso de
   * verificacion pendiente comprueba antes que haya alguien dentro; devolver
   * "no verificado" a un visitante anonimo seria mentirle a esa comprobacion.
   */
  readonly emailVerified = computed(() => this.sesion()?.user.emailVerified ?? false);

  token(): string | null {
    return this.sesion()?.accessToken ?? null;
  }

  set(sesion: Session): void {
    this.sesion.set(sesion);
    this.resuelta.set(true);
  }

  /**
   * Sin sesion, y ya se sabe.
   *
   * <p>Sirve para las dos cosas que acaban igual: cerrar sesion y descubrir que
   * no habia ninguna que recuperar. En ambos casos la respuesta pasa a ser
   * conocida, que es lo que saca a la interfaz del estado de espera.
   */
  clear(): void {
    this.sesion.set(null);
    this.resuelta.set(true);
  }
}
