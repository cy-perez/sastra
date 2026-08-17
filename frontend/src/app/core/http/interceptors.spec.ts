import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { AccessTokenStore } from './access-token.store';
import { ApiError } from './api-error';
import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from './interceptors';

const API = 'https://api.pruebas.sastra.co/api/v1';

describe('interceptores HTTP', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let tokens: AccessTokenStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withInterceptors([
            apiUrlInterceptor,
            authInterceptor,
            languageInterceptor,
            errorInterceptor,
          ]),
        ),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    tokens = TestBed.inject(AccessTokenStore);
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
      tokens.set('un-token');
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

    it('olvida el token al cerrar la sesion', () => {
      tokens.set('un-token');
      tokens.clear();
      http.get('listings').subscribe();

      expect(backend.expectOne(`${API}/listings`).request.headers.has('Authorization')).toBe(false);
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
