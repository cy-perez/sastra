import { inject } from '@angular/core';
import { HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { TranslocoService } from '@jsverse/transloco';
import { catchError, throwError } from 'rxjs';

import { APP_CONFIG } from '../config/app-config';
import { AccessTokenStore } from './access-token.store';
import { toApiError } from './api-error';

/** Rutas de sesion: son las unicas que llevan la cookie del token de refresco. */
const AUTH_PATH = '/auth/';

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
 * Token de acceso en la cabecera; cookie solo en las rutas de sesion. Mandar la
 * cookie en todas las peticiones ampliaria sin motivo la superficie de CSRF.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AccessTokenStore).get();
  const needsRefreshCookie = request.url.includes(AUTH_PATH);

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
