import { inject, Injectable, InjectionToken } from '@angular/core';
import {
  defaultIfEmpty,
  finalize,
  first,
  firstValueFrom,
  from,
  map,
  type Observable,
  shareReplay,
} from 'rxjs';

/**
 * Puerto de renovacion de la sesion.
 *
 * <p>`core` sabe *cuando* hay que renovar, no *como*. El como es HTTP contra
 * `auth/refresh` y mapeo de un DTO, que es de la funcionalidad de cuentas: la
 * implementacion la aporta `features/auth` con provideAuthSession(). Sin este
 * puerto, un interceptor de `core` tendria que importar de `features/auth` y la
 * dependencia iria justo al reves de lo que manda la arquitectura.
 *
 * <p>Quien la implementa deja la sesion nueva en el SessionStore antes de
 * completar. Aqui solo importa si lo consiguio.
 */
export type SessionRefresher = () => Observable<void>;

export const SESSION_REFRESHER = new InjectionToken<SessionRefresher>('sendik.session-refresher');

/** Un candado por origen: lo comparten todas las pestanas del mismo sitio. */
const CANDADO = 'sendik.refresco-de-sesion';

/**
 * Renueva la sesion de una en una, aunque se lo pidan varios a la vez.
 *
 * <p>Es lo que hace segura la rotacion del criterio 14, y hacen falta dos
 * candados porque hay dos formas de pisarse.
 *
 * <p><strong>Dentro de la pestana</strong>, con {@code enCurso}. Si tres
 * peticiones reciben 401 al tiempo y cada una pidiera su propio refresco, la
 * primera rotaria el token y las otras dos llegarian con uno ya consumido.
 *
 * <p><strong>Entre pestanas</strong>, con la API de candados del navegador. La
 * cookie no es de la pestana, es del navegador entero: dos pestanas abiertas a la
 * vez arrancan las dos con el mismo token de refresco y {@code enCurso}, que vive
 * en el inyector de una sola aplicacion, no las ve. Con el candado, la segunda
 * espera a que la primera termine y sale con la cookie ya rotada.
 *
 * <p>El servidor tiene ademas su propia defensa, la ventana de gracia de RN-007,
 * porque un candado del navegador no cubre dos navegadores ni una recarga en
 * mitad de la peticion. Las dos mitades son necesarias: sin la del servidor, una
 * carrera cerraba la sesion y mandaba un aviso de incidente falso.
 */
@Injectable({ providedIn: 'root' })
export class SessionRecovery {
  private readonly renovar = inject(SESSION_REFRESHER);

  private enCurso: Observable<void> | null = null;

  /**
   * Espera el turno del navegador antes de renovar.
   *
   * <p>Si la API de candados no esta (un navegador viejo, o el renderizado en
   * servidor) se renueva sin ella: se pierde la coordinacion entre pestanas, no
   * la funcionalidad, y para eso esta la ventana de gracia del servidor.
   */
  private porTurnos(): Observable<void> {
    const candados = typeof navigator === 'undefined' ? undefined : navigator.locks;
    if (candados === undefined) {
      return this.renovar();
    }

    return from(
      candados.request(CANDADO, () =>
        // first() y defaultIfEmpty para no quedarse con el turno tomado: el
        // candado se suelta cuando la promesa termina, y sin esto un puerto que
        // devolviera un observable sin fin bloquearia a todas las demas pestanas.
        firstValueFrom(this.renovar().pipe(first(), defaultIfEmpty(undefined))),
      ),
    ).pipe(map(() => undefined));
  }

  recuperar(): Observable<void> {
    // El hueco se libera al terminar, con exito o con fallo, de modo que el
    // siguiente que lo pida hace un refresco nuevo en vez de recibir el
    // resultado guardado de uno viejo. Un fallo tampoco puede dejarlo ocupado
    // para siempre.
    this.enCurso ??= this.porTurnos().pipe(
      finalize(() => {
        this.enCurso = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    return this.enCurso;
  }
}
