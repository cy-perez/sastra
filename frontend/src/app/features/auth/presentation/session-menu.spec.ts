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
import { SessionMenu } from './session-menu';

describe('SessionMenu', () => {
  const API = 'https://api.pruebas.sastra.co/api/v1';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const render = async () => {
    const fixture = TestBed.createComponent(SessionMenu);
    await fixture.whenStable();
    return fixture;
  };

  const asentar = async (fixture: {
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    await new Promise((listo) => setTimeout(listo, 0));
    fixture.detectChanges();
    await fixture.whenStable();
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
  });

  /**
   * Al cargar la pagina todavia no se sabe si hay sesion: el token vive en
   * memoria y hay que preguntarle al servidor con la cookie. Pintar "Entrar"
   * mientras tanto le cambiaria el boton bajo el cursor a quien si la tiene.
   */
  /**
   * Asienta la pantalla respondiendo por el camino las peticiones de la propia
   * cuenta.
   *
   * <p>Al abrirse la sesion, el almacen de raiz pide el perfil y la lista de
   * sesiones: sus consultas se habilitan en cuanto hay sesion (auth.store.ts) y
   * este almacen lo instancia la cabecera en cada carga. No son de lo que esta
   * prueba comprueba, pero hay que contestarlas: una peticion pendiente mantiene
   * la aplicacion inestable, asi que responderlas **despues** de esperar a que se
   * estabilice no funciona —se espera para siempre—. Por eso se contestan dentro
   * del mismo bucle que asienta.
   */
  const asentarRespondiendoLaCuenta = async (fixture: {
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    const backend = TestBed.inject(HttpTestingController);
    for (let vuelta = 0; vuelta < 8; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
      backend
        .match((peticion) => peticion.url.startsWith(`${API}/users/me`))
        .forEach((peticion) => {
          peticion.flush(peticion.request.url.endsWith('/sessions') ? [] : null);
        });
    }
    await fixture.whenStable();
  };

  it('no adelanta nada mientras la sesion es desconocida', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('a')).toBeNull();
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
    expect(fixture.nativeElement.querySelector('.esqueleto')).not.toBeNull();
  });

  it('ofrece entrar a quien no tiene sesion', async () => {
    // clear() no es solo "no hay sesion": es "ya se pregunto y no la hay".
    TestBed.inject(SessionStore).clear();
    const fixture = await render();
    const enlace = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;

    expect(enlace.textContent?.trim()).toBe('Entrar');
    expect(enlace.getAttribute('href')).toBe('/ingresar');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  /**
   * El nombre es ademas el enlace a la propia cuenta, y se ve tambien en movil:
   * ocultarlo alli dejaria /mi-cuenta inalcanzable desde un telefono.
   */
  it('muestra a quien pertenece la sesion y ofrece salir', async () => {
    TestBed.inject(SessionStore).set(SESION);
    const fixture = await render();

    const aLaCuenta = fixture.nativeElement.querySelector(
      'a[href="/mi-cuenta"]',
    ) as HTMLAnchorElement;
    expect(aLaCuenta.textContent?.trim()).toBe('Ana Maria');
    expect(fixture.nativeElement.textContent).toContain('Ana Maria');
    expect(
      (fixture.nativeElement.querySelector('button') as HTMLButtonElement).textContent?.trim(),
    ).toBe('Salir');
  });

  /**
   * Criterio 16: cerrar sesion revoca el token en el servidor, no solo en el
   * navegador. Limpiar solo el estado local dejaria vivo un token de refresco de
   * 30 dias en el ordenador prestado del que la persona acaba de salir.
   */
  it('revoca en el servidor al salir', async () => {
    TestBed.inject(SessionStore).set(SESION);
    const fixture = await render();

    await asentarRespondiendoLaCuenta(fixture);

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/logout`)
      .flush(null, { status: 204, statusText: 'No Content' });
    await asentar(fixture);

    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(false);
    expect(fixture.nativeElement.querySelector('a')).not.toBeNull();
  });

  /**
   * Si la llamada falla, el servidor puede haber revocado igual y el navegador no
   * tiene forma de saberlo. Dejar la pantalla como si la persona siguiera dentro
   * es lo unico que seguro esta mal: pulso salir.
   */
  it('cierra la sesion local aunque la llamada falle', async () => {
    TestBed.inject(SessionStore).set(SESION);
    const fixture = await render();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    await asentarRespondiendoLaCuenta(fixture);

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/logout`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await asentar(fixture);

    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(false);
    expect(navegar).toHaveBeenCalledWith('/');
  });
});
