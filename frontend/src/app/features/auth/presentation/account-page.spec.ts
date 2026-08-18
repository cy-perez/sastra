import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { AccountPage } from './account-page';

/** Criterios 17, 22 y 23 de HU-001. */
describe('AccountPage', () => {
  const API = 'https://api.pruebas.sastra.co/api/v1';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const LISTA = [
    {
      id: 'la-de-ahora',
      userAgent: 'Chrome',
      startedAt: '2026-08-17T15:00:00Z',
      expiresAt: '2026-09-16T15:00:00Z',
      current: true,
    },
    {
      id: 'la-del-movil',
      userAgent: 'Firefox',
      startedAt: '2026-08-10T15:00:00Z',
      expiresAt: '2026-09-09T15:00:00Z',
      current: false,
    },
  ];

  const render = async () => {
    const fixture = TestBed.createComponent(AccountPage);
    await fixture.whenStable();
    return fixture;
  };

  /**
   * Una consulta de TanStack necesita mas vueltas que una mutacion: la respuesta
   * pasa por su observador antes de llegar a las senales, y con una sola vuelta
   * la pantalla se queda en "cargando".
   */
  const asentar = async (fixture: {
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
      await fixture.whenStable();
    }
  };

  const escribir = (fixture: { nativeElement: HTMLElement }, valor: string) => {
    const campo = fixture.nativeElement.querySelector('#confirmacion') as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
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
    TestBed.inject(SessionStore).set(SESION);
  });

  // Criterio 17: la lista dice cual es la sesion desde la que se mira.
  it('lista las sesiones y senala la actual criterio_17', async () => {
    const fixture = await render();

    TestBed.inject(HttpTestingController).expectOne(`${API}/users/me/sessions`).flush(LISTA);
    await asentar(fixture);

    const filas = fixture.nativeElement.querySelectorAll('.sesion');
    expect(filas).toHaveLength(2);
    expect(filas[0].textContent).toContain('Chrome');
    expect(filas[0].textContent).toContain('esta sesión');
    expect(filas[1].textContent).not.toContain('esta sesión');
  });

  // Cerrar la propia dice lo que va a pasar: no es lo mismo que cerrar otra.
  it('distingue cerrar la propia de cerrar otra criterio_17', async () => {
    const fixture = await render();

    TestBed.inject(HttpTestingController).expectOne(`${API}/users/me/sessions`).flush(LISTA);
    await asentar(fixture);

    const botones = fixture.nativeElement.querySelectorAll('.sesion button');
    expect(botones[0].textContent?.trim()).toBe('Cerrar y salir');
    expect(botones[1].textContent?.trim()).toBe('Cerrar');
  });

  it('cierra la sesion elegida y recarga la lista criterio_17', async () => {
    const fixture = await render();
    const backend = TestBed.inject(HttpTestingController);

    backend.expectOne(`${API}/users/me/sessions`).flush(LISTA);
    await asentar(fixture);

    (fixture.nativeElement.querySelectorAll('.sesion button')[1] as HTMLButtonElement).click();
    await new Promise((listo) => setTimeout(listo, 0));

    backend
      .expectOne(`${API}/users/me/sessions/la-del-movil`)
      .flush(null, { status: 204, statusText: 'No Content' });
    await new Promise((listo) => setTimeout(listo, 0));

    // Se vuelve a consultar: la lista tiene que reflejar lo que acaba de pasar.
    backend.expectOne(`${API}/users/me/sessions`).flush([LISTA[0]]);
  });

  it('pide el archivo de datos al descargarlo criterio_22', async () => {
    const fixture = await render();
    const backend = TestBed.inject(HttpTestingController);
    backend.expectOne(`${API}/users/me/sessions`).flush([]);
    await asentar(fixture);

    const boton = Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      (b as HTMLButtonElement).textContent?.includes('Descargar'),
    ) as HTMLButtonElement;
    boton.click();
    await fixture.whenStable();

    backend.expectOne(`${API}/users/me/export`).flush('{"cuenta":{}}');
  });

  /**
   * Criterio 23: cerrar no es una accion de paso. El formulario no esta a la
   * vista hasta que la persona lo pide.
   */
  it('no muestra el formulario de cierre hasta que se pide criterio_23', async () => {
    const fixture = await render();
    TestBed.inject(HttpTestingController).expectOne(`${API}/users/me/sessions`).flush([]);
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('#confirmacion')).toBeNull();
  });

  it('no cierra la cuenta si lo escrito no es el propio correo criterio_23', async () => {
    const fixture = await render();
    const backend = TestBed.inject(HttpTestingController);
    backend.expectOne(`${API}/users/me/sessions`).flush([]);
    await asentar(fixture);

    (
      Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
        (b as HTMLButtonElement).textContent?.includes('Quiero cerrar'),
      ) as HTMLButtonElement
    ).click();
    await asentar(fixture);

    escribir(fixture, 'otra@correo.co');
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).requestSubmit();
    await asentar(fixture);

    backend.expectNone(`${API}/users/me`);
    expect(fixture.nativeElement.querySelector('[aria-invalid="true"]')).not.toBeNull();
  });

  it('cierra la cuenta con la confirmacion correcta criterio_23', async () => {
    const fixture = await render();
    const backend = TestBed.inject(HttpTestingController);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    backend.expectOne(`${API}/users/me/sessions`).flush([]);
    await asentar(fixture);

    (
      Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
        (b as HTMLButtonElement).textContent?.includes('Quiero cerrar'),
      ) as HTMLButtonElement
    ).click();
    await asentar(fixture);

    escribir(fixture, 'ana@correo.co');
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).requestSubmit();
    await fixture.whenStable();

    const peticion = backend.expectOne(`${API}/users/me`);
    expect(peticion.request.method).toBe('DELETE');
    expect(peticion.request.body).toEqual({ confirmation: 'ana@correo.co' });

    peticion.flush(null, { status: 204, statusText: 'No Content' });
    await asentar(fixture);

    // La cuenta ya no existe: la sesion local no puede sobrevivirla.
    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(false);
    expect(navegar).toHaveBeenCalledWith('/');
  });
});
