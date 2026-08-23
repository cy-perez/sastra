import { isPlatformServer } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { RedirectCommand, Router, type CanActivateFn } from '@angular/router';
import { filter, map, of, take, type Observable } from 'rxjs';

import { SessionStore } from './session.store';

/**
 * Exige un rol para entrar a una ruta. El primer guard del proyecto (HU-006, ADR-0021).
 *
 * <p><strong>Esto no es la cerradura.</strong> La cerradura es el backend, que responde
 * 403 a quien no tiene el rol aunque llegue por otro camino, y lo hace por partida doble:
 * la regla por ruta de `SecurityConfig` y un `@PreAuthorize` en cada metodo. Lo que este
 * guard evita es ensenar una pantalla que no se va a poder llenar, y que quien no tiene
 * el rol descubra que la pantalla existe.
 *
 * <p>Se puede saltar. Cualquiera puede editar el JavaScript de su navegador y forzar la
 * navegacion; lo que se llevaria es una pantalla vacia y una tanda de 403. Ese es el
 * reparto correcto: el cliente decide que se pinta, el servidor decide que se puede.
 */

/**
 * <strong>Espera a que la sesion se resuelva antes de decidir.</strong>
 *
 * <p>Es la unica parte delicada. El token de acceso vive en memoria y se pierde al
 * recargar; la sesion se recupera despues, con la cookie de refresco, en un
 * `provideAppInitializer` que a proposito no bloquea el arranque. Un guard que mirara el
 * rol de inmediato encontraria `desconocida` en toda recarga y echaria al moderador de su
 * propia bandeja.
 *
 * <p>Por eso no se lee la senal: se escucha hasta que deja de ser `desconocida`. Que la
 * respuesta llegue siempre lo garantiza ese inicializador, que marca la sesion como
 * resuelta tambien cuando no hay ninguna que recuperar.
 *
 * <p><strong>En el servidor deniega siempre</strong>, y eso no es una limitacion: es lo
 * que hace que el criterio 2 se cumpla. Alli la sesion se queda en `desconocida` para
 * siempre —el renderizado no tiene la cookie de nadie— asi que esperar seria colgar el
 * SSR; y dejar pasar meteria el titulo de la pantalla en el HTML que recibe cualquiera
 * que pida la direccion. Denegar deja servida la pagina de "no existe", que es
 * exactamente lo que debe ver quien no tiene el rol.
 *
 * <p>Al hidratar, el guard vuelve a correr en el navegador, ahi si espera a la sesion, y
 * quien tenga el rol entra. El coste es que la pagina de "no existe" se ve un instante
 * antes de la bandeja. Se acepta: es una herramienta interna, y ese instante es
 * justamente lo que ve quien no deberia pasar.
 *
 * <p>No se usa `RenderMode.Client` para esto, aunque seria lo natural: `APP_CONFIG` llega
 * por el estado transferido del renderizado en servidor, y una ruta que no se renderiza
 * alli arranca sin configuracion y la aplicacion no levanta.
 */
export function exigirRol(rol: string): CanActivateFn {
  return (): Observable<boolean | RedirectCommand> => {
    const sesion = inject(SessionStore);
    const router = inject(Router);

    if (isPlatformServer(inject(PLATFORM_ID))) {
      return of(denegar(router));
    }

    return toObservable(sesion.status).pipe(
      filter((estado) => estado !== 'desconocida'),
      take(1),
      map(() => (sesion.user()?.roles.includes(rol) === true ? true : denegar(router))),
    );
  };
}

/**
 * La pagina de "no existe", y **sin cambiar la direccion**.
 *
 * <p>No es un ahorro de pantalla: un "no tienes permiso" confirma que detras hay algo, y
 * el criterio 2 de HU-006 pide que quien no es moderador no se entere de que la bandeja
 * existe. Es la misma razon por la que el backend responde 404 a las rutas con la bandera
 * apagada en vez de 403.
 *
 * <p>`skipLocationChange` deja la direccion escrita tal cual. Con una redireccion normal
 * la barra saltaria a otra cosa, y esa sacudida tambien delata; ademas, quien tenga el
 * rol y recargue veria una direccion que ya no es la suya.
 */
function denegar(router: Router): RedirectCommand {
  return new RedirectCommand(router.parseUrl('/no-encontrado'), { skipLocationChange: true });
}
