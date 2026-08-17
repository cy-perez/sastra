import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  type TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, type Mock, vi } from 'vitest';

import { SESSION_REFRESHER, type SessionRefresher } from '../session/session-recovery';
import { SessionStore } from '../session/session.store';
import { ApiError } from './api-error';
import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
  refreshInterceptor,
} from './interceptors';

const API = 'https://api.pruebas.sastra.co/api/v1';

const UNA_SESION = {
  accessToken: 'un-token',
  user: { email: 'ana@correo.co', displayName: 'Ana', emailVerified: true, roles: [] },
};

describe('interceptores HTTP', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let sesion: SessionStore;
  let renovar: Mock<SessionRefresher>;

  /**
   * El refresco se simula en el puerto y no con otra peticion de prueba: lo que
   * aqui se comprueba es la reaccion al 401, no como se habla con /auth/refresh,
   * que es cosa del adaptador de cuentas y tiene sus propias pruebas.
   */
  const renovacionQueFunciona: SessionRefresher = () => {
    sesion.set({ ...UNA_SESION, accessToken: 'token-nuevo' });
    return of(undefined);
  };

  beforeEach(() => {
    renovar = vi.fn(renovacionQueFunciona);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withInterceptors([
            apiUrlInterceptor,
            refreshInterceptor,
            authInterceptor,
            languageInterceptor,
            errorInterceptor,
          ]),
        ),
        provideHttpClientTesting(),
        { provide: SESSION_REFRESHER, useValue: renovar },
      ],
    });

    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    sesion = TestBed.inject(SessionStore);
  });

  describe('apiUrlInterceptor', () => {
    it('antepone la base de la API a una ruta relativa', () => {
      http.get('auth/me').subscribe();

      backend.expectOne(`${API}/auth/me`).flush({});
    });

    // /i18n/es.json es un activo del propio sitio: si se le pusiera la base de
    // la API, las traducciones se pedirian al backend y no existirian ahi.
    it('no toca las rutas que empiezan por barra', () => {
      http.get('/i18n/es.json').subscribe();

      backend.expectOne('/i18n/es.json').flush({});
    });

    it('no toca las direcciones absolutas', () => {
      http.get('https://otro.servicio.co/datos').subscribe();

      backend.expectOne('https://otro.servicio.co/datos').flush({});
    });
  });

  describe('authInterceptor', () => {
    it('no manda cabecera de autorizacion si no hay sesion', () => {
      http.get('listings').subscribe();

      expect(backend.expectOne(`${API}/listings`).request.headers.has('Authorization')).toBe(false);
    });

    it('manda el token de acceso cuando hay sesion', () => {
      sesion.set(UNA_SESION);
      http.get('listings').subscribe();

      expect(backend.expectOne(`${API}/listings`).request.headers.get('Authorization')).toBe(
        'Bearer un-token',
      );
    });

    // La cookie del token de refresco solo viaja a /auth. Mandarla en todas las
    // peticiones ampliaria sin motivo la superficie de CSRF.
    it('solo envia credenciales en las rutas de sesion', () => {
      http.post('auth/refresh', {}).subscribe();
      expect(backend.expectOne(`${API}/auth/refresh`).request.withCredentials).toBe(true);

      http.get('listings').subscribe();
      expect(backend.expectOne(`${API}/listings`).request.withCredentials).toBe(false);
    });

    /**
     * El token de acceso es un secreto y solo se le presenta a quien tiene que
     * comprobarlo. El cargador de traducciones usa este mismo cliente HTTP, asi
     * que sin esta regla el JWT acababa en el registro de accesos del servidor
     * de estaticos (docs/operacion/datos-personales.md).
     */
    it('no manda la credencial fuera de la API', () => {
      sesion.set(UNA_SESION);

      http.get('/i18n/es.json').subscribe();
      const traducciones = backend.expectOne('/i18n/es.json').request;
      expect(traducciones.headers.has('Authorization')).toBe(false);
      expect(traducciones.withCredentials).toBe(false);

      http.get('https://otro.servicio.co/datos').subscribe();
      const tercero = backend.expectOne('https://otro.servicio.co/datos').request;
      expect(tercero.headers.has('Authorization')).toBe(false);
      expect(tercero.withCredentials).toBe(false);
    });

    // Un host que empiece igual que nuestra base no es nuestra base.
    it('no se deja enganar por una direccion que solo se parece a la API', () => {
      sesion.set(UNA_SESION);
      http.get('https://api.pruebas.sastra.co.atacante.example/listings').subscribe();

      expect(
        backend
          .expectOne('https://api.pruebas.sastra.co.atacante.example/listings')
          .request.headers.has('Authorization'),
      ).toBe(false);
    });

    it('olvida el token al cerrar la sesion', () => {
      sesion.set(UNA_SESION);
      sesion.clear();
      http.get('listings').subscribe();

      expect(backend.expectOne(`${API}/listings`).request.headers.has('Authorization')).toBe(false);
    });
  });

  describe('refreshInterceptor', () => {
    const responder401 = (peticion: TestRequest) =>
      peticion.flush({ code: 'AUTH_SESSION_INVALID' }, { status: 401, statusText: 'Unauthorized' });

    it('renueva la sesion y reintenta con el token nuevo', async () => {
      sesion.set(UNA_SESION);
      const respuesta = new Promise((listo) => http.get('listings').subscribe(listo));

      responder401(backend.expectOne(`${API}/listings`));
      await Promise.resolve();

      // El reintento es lo que demuestra que el interceptor va por fuera de
      // authInterceptor: si fuera por dentro repetiria la cabecera caducada.
      const reintento = backend.expectOne(`${API}/listings`);
      expect(reintento.request.headers.get('Authorization')).toBe('Bearer token-nuevo');

      reintento.flush({ ok: true });
      expect(await respuesta).toEqual({ ok: true });
    });

    it('cierra la sesion y propaga el error si la renovacion falla', async () => {
      sesion.set(UNA_SESION);
      renovar.mockReturnValue(throwError(() => new Error('sin cookie')));

      const fallo = new Promise<unknown>((resolve) => {
        http.get('listings').subscribe({ error: resolve });
      });

      responder401(backend.expectOne(`${API}/listings`));

      const error = await fallo;
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).status).toBe(401);
      expect(sesion.isAuthenticated()).toBe(false);
    });

    /**
     * Es la proteccion del criterio 15. Con un refresco por peticion, la primera
     * rotaria el token y las demas llegarian con uno ya usado: el servidor lo lee
     * como reutilizacion y revoca la familia entera. El cliente se echaria solo.
     */
    it('renueva una sola vez aunque varias peticiones fallen a la vez', async () => {
      sesion.set(UNA_SESION);

      // Un Subject sin cerrar es un refresco que sigue en vuelo, que es la
      // situacion que hay que reproducir. Con un refresco que termina al
      // instante las dos peticiones nunca coincidirian y la prueba pasaria sin
      // comprobar nada.
      const renovacionEnVuelo = new Subject<void>();
      renovar.mockReturnValue(renovacionEnVuelo);

      http.get('listings').subscribe({ error: () => undefined });
      http.get('categories').subscribe({ error: () => undefined });

      responder401(backend.expectOne(`${API}/listings`));
      responder401(backend.expectOne(`${API}/categories`));
      await Promise.resolve();

      expect(renovar).toHaveBeenCalledTimes(1);

      sesion.set({ ...UNA_SESION, accessToken: 'token-nuevo' });
      renovacionEnVuelo.next();
      renovacionEnVuelo.complete();
      await Promise.resolve();

      // Las dos esperaban al mismo refresco y las dos siguen con el token nuevo.
      for (const ruta of ['listings', 'categories']) {
        expect(backend.expectOne(`${API}/${ruta}`).request.headers.get('Authorization')).toBe(
          'Bearer token-nuevo',
        );
      }
    });

    // Sin esto, un refresco que devuelve 401 dispararia otro refresco sin final.
    it('no intenta renovar cuando la que falla es una ruta de sesion', async () => {
      const fallo = new Promise<unknown>((resolve) => {
        http.post('auth/login', {}).subscribe({ error: resolve });
      });

      responder401(backend.expectOne(`${API}/auth/login`));

      await fallo;
      expect(renovar).not.toHaveBeenCalled();
    });

    // Un 401 de un tercero no dice nada de nuestra sesion. Renovarla por eso
    // gastaria una rotacion y le reenviaria la peticion con el token nuevo.
    it('no renueva por un 401 que viene de fuera de la API', async () => {
      sesion.set(UNA_SESION);
      const fallo = new Promise<unknown>((resolve) => {
        http.get('https://otro.servicio.co/datos').subscribe({ error: resolve });
      });

      responder401(backend.expectOne('https://otro.servicio.co/datos'));

      await fallo;
      expect(renovar).not.toHaveBeenCalled();
    });

    it('no reacciona a un error que no sea 401', async () => {
      const fallo = new Promise<unknown>((resolve) => {
        http.get('listings').subscribe({ error: resolve });
      });

      backend
        .expectOne(`${API}/listings`)
        .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });

      await fallo;
      expect(renovar).not.toHaveBeenCalled();
    });
  });

  describe('languageInterceptor', () => {
    it('declara el idioma activo en cada peticion', () => {
      http.get('categories').subscribe();

      expect(backend.expectOne(`${API}/categories`).request.headers.get('Accept-Language')).toBe(
        'es',
      );
    });
  });

  describe('errorInterceptor', () => {
    it('convierte un ProblemDetail en ApiError', async () => {
      const failure = new Promise<unknown>((resolve) => {
        http.post('auth/register', {}).subscribe({ error: resolve });
      });

      backend
        .expectOne(`${API}/auth/register`)
        .flush(
          { code: 'AUTH_EMAIL_TAKEN', traceId: 'abc', errors: [] },
          { status: 409, statusText: 'Conflict' },
        );

      const error = await failure;
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).code).toBe('AUTH_EMAIL_TAKEN');
      expect((error as ApiError).status).toBe(409);
    });

    it('convierte tambien un fallo de red', async () => {
      const failure = new Promise<unknown>((resolve) => {
        http.get('categories').subscribe({ error: resolve });
      });

      backend.expectOne(`${API}/categories`).error(new ProgressEvent('error'));

      const error = await failure;
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).isNetworkFailure).toBe(true);
      expect((error as ApiError).translationKey).toBe('errors.network');
    });
  });
});
