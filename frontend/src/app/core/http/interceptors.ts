import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { TranslocoService } from '@jsverse/transloco';
import { catchError, switchMap, throwError } from 'rxjs';

import { APP_CONFIG } from '../config/app-config';
import { SessionRecovery } from '../session/session-recovery';
import { SessionStore } from '../session/session.store';
import { ApiError, toApiError } from './api-error';

/** Rutas de sesion: son las unicas que llevan la cookie del token de refresco. */
const AUTH_PATH = 'auth/';

/**
 * Si la peticion va a nuestra API o a otro sitio.
 *
 * <p>Se compara contra la base configurada y no se busca una subcadena. La
 * diferencia importa: el token de acceso es un secreto
 * (docs/operacion/datos-personales.md) y no puede acompanar a una peticion que
 * no sea para nosotros. Sin esta comprobacion ya se filtraba hoy, porque el
 * cargador de traducciones pide /i18n/es.json con el mismo cliente HTTP y se
 * llevaba la cabecera al servidor de estaticos, que la escribe en su registro
 * de accesos.
 *
 * <p>La base vacia no casa con nada. Es la configuracion de relleno que solo
 * existe al construir, y ahi lo correcto es no adjuntar credencial a nada.
 */
function esDeLaApi(url: string, apiBaseUrl: string): boolean {
  return apiBaseUrl !== '' && (url === apiBaseUrl || url.startsWith(`${apiBaseUrl}/`));
}

/**
 * Las rutas relativas van a la API; las absolutas y las que empiezan por barra
 * se dejan como estan, porque son activos del propio sitio (por ejemplo
 * /i18n/es.json). Asi un adaptador escribe `auth/login` y no repite la base.
 */
export const apiUrlInterceptor: HttpInterceptorFn = (request, next) => {
  if (/^[a-z][a-z0-9+.-]*:/i.test(request.url) || request.url.startsWith('/')) {
    return next(request);
  }

  const { apiBaseUrl } = inject(APP_CONFIG);
  if (apiBaseUrl === '') {
    throw new Error(
      'API_BASE_URL no esta configurada: la aplicacion se esta ejecutando con la ' +
        'configuracion de relleno que solo deberia existir al construir. Revisa el entorno ' +
        'del servidor (docs/operacion/configuracion.md).',
    );
  }

  return next(request.clone({ url: `${apiBaseUrl}/${request.url}` }));
};

/**
 * Token de acceso en la cabecera; cookie solo en las rutas de sesion.
 *
 * <p>Ni una cosa ni la otra salen de nuestra API: una credencial solo se
 * presenta a quien tiene que comprobarla. Mandar ademas la cookie en todas las
 * peticiones de la API, y no solo en las de sesion, ampliaria sin motivo la
 * superficie de CSRF.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const { apiBaseUrl } = inject(APP_CONFIG);

  if (!esDeLaApi(request.url, apiBaseUrl)) {
    return next(request);
  }

  const token = inject(SessionStore).token();
  const needsRefreshCookie = request.url.startsWith(`${apiBaseUrl}/${AUTH_PATH}`);

  if (token === null && !needsRefreshCookie) {
    return next(request);
  }

  return next(
    request.clone({
      ...(token === null ? {} : { setHeaders: { Authorization: `Bearer ${token}` } }),
      withCredentials: needsRefreshCookie,
    }),
  );
};

/**
 * Renueva la sesion y reintenta cuando el servidor responde 401.
 *
 * <p>El token de acceso dura 15 minutos y el de refresco 30 dias: entre los dos
 * hay muchas ocasiones de encontrarse con uno caducado. Reintentar aqui evita
 * que eso se note en pantalla, que es lo que separa "la sesion dura 30 dias" de
 * "hay que volver a entrar cada cuarto de hora".
 *
 * <p><strong>Va antes que authInterceptor en la cadena, y eso lo es todo.</strong>
 * Al reintentar con next(request) la peticion vuelve a pasar por los de mas
 * abajo, asi que recoge el token nuevo. Si estuviera despues, el reintento
 * repetiria la cabecera caducada y fallaria igual. Estar antes tambien significa
 * estar por fuera de errorInterceptor, que es quien convierte el fallo en
 * ApiError: por eso aqui se comprueba ApiError y no HttpErrorResponse.
 *
 * <p>El reintento no vuelve a entrar por este interceptor, de modo que un 401
 * persistente falla a la segunda en vez de reintentar sin fin.
 */
export const refreshInterceptor: HttpInterceptorFn = (request, next) => {
  // En el servidor no hay cookie de refresco que presentar: el renderizado no es
  // el navegador de nadie. Intentarlo solo gastaria una peticion por pagina.
  const enElNavegador = isPlatformBrowser(inject(PLATFORM_ID));
  const { apiBaseUrl } = inject(APP_CONFIG);

  // Solo nuestra API, y dentro de ella todo menos las rutas de sesion.
  //
  // Lo primero, porque un 401 de un tercero no dice nada de nuestra sesion:
  // renovarla por eso gastaria una rotacion y reenviaria la peticion, ya con el
  // token nuevo, a ese tercero. Lo segundo, porque un refresco que devuelve 401
  // dispararia otro refresco, y ese otro, sin final.
  const renovable =
    enElNavegador &&
    esDeLaApi(request.url, apiBaseUrl) &&
    !request.url.startsWith(`${apiBaseUrl}/${AUTH_PATH}`);

  if (!renovable) {
    return next(request);
  }

  const recuperacion = inject(SessionRecovery);
  const sesion = inject(SessionStore);

  return next(request).pipe(
    catchError((fallo: unknown) => {
      if (!(fallo instanceof ApiError) || fallo.status !== 401) {
        return throwError(() => fallo);
      }

      return recuperacion.recuperar().pipe(
        // Este catchError va antes del switchMap a proposito: asi solo atrapa el
        // fallo del refresco. Detras del switchMap atraparia tambien el del
        // reintento, y un 500 del servidor cerraria una sesion que estaba bien.
        catchError(() => {
          sesion.clear();
          // Se propaga el 401 original y no el del refresco: la pantalla tiene
          // que explicar que fallo la peticion que la persona pidio.
          return throwError(() => fallo);
        }),
        switchMap(() => next(request)),
      );
    }),
  );
};

/**
 * El idioma activo viaja en cada peticion. Solo afecta al contenido que el
 * servidor traduce; los mensajes de error se traducen aqui a partir del codigo.
 */
export const languageInterceptor: HttpInterceptorFn = (request, next) => {
  const language = inject(TranslocoService).getActiveLang();
  return next(request.clone({ setHeaders: { 'Accept-Language': language } }));
};

/**
 * Toda respuesta de error sale de HTTP convertida en ApiError. A partir de este
 * punto nadie mas tiene que saber como es un ProblemDetail, y ningun texto del
 * servidor puede acabar en pantalla por descuido.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) =>
  next(request).pipe(
    catchError((failure: unknown) => {
      if (failure instanceof HttpErrorResponse) {
        return throwError(() => toApiError(failure.status, failure.error));
      }
      return throwError(() => failure);
    }),
  );
