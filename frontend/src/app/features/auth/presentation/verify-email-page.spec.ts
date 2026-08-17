import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import { SessionStore } from '../../../core/session/session.store';
import { VerifyEmailPage } from './verify-email-page';

/** Criterios 7 a 9 de HU-001. */
describe('VerifyEmailPage', () => {
  const API = 'https://api.pruebas.sastra.co/api/v1';

  const SESION = {
    accessToken: 'token-de-acceso',
    expiresIn: 900,
    user: {
      email: 'ana@correo.co',
      displayName: 'Ana Maria',
      emailVerified: true,
      roles: ['BUYER'],
    },
  };

  const render = async (token?: string) => {
    const fixture = TestBed.createComponent(VerifyEmailPage);
    if (token !== undefined) {
      fixture.componentRef.setInput('token', token);
    }
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
   * Criterio 9: al verificar se entra directamente. El servidor devuelve la
   * sesion con el resultado y aqui se guarda, asi que la persona no vuelve a
   * escribir su contrasena.
   */
  it('deja la sesion abierta al verificar el correo', async () => {
    const fixture = await render('un-token-del-correo');

    const peticion = TestBed.inject(HttpTestingController).expectOne(`${API}/auth/verify-email`);
    expect(peticion.request.body).toEqual({ token: 'un-token-del-correo' });

    peticion.flush({ session: SESION, alreadyVerified: false });
    await asentar(fixture);

    const sesion = TestBed.inject(SessionStore);
    expect(sesion.isAuthenticated()).toBe(true);
    expect(sesion.user()?.displayName).toBe('Ana Maria');
    expect(fixture.nativeElement.textContent).toContain('Tu cuenta quedó activa');
  });

  // Volver a abrir el enlace no es un error: la cuenta ya estaba activa y la
  // persona entra igual.
  it('entra tambien cuando la cuenta ya estaba verificada', async () => {
    const fixture = await render('un-token-del-correo');

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/verify-email`)
      .flush({ session: SESION, alreadyVerified: true });
    await asentar(fixture);

    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(true);
  });

  // Sin token no hay nada que consumir: se explica en vez de fallar en silencio.
  it('no llama a la API si falta el token', async () => {
    const fixture = await render();

    TestBed.inject(HttpTestingController).expectNone(`${API}/auth/verify-email`);
    expect(fixture.nativeElement.textContent).toContain('Falta el enlace');
  });

  it('ofrece reenviar cuando el enlace caduco y no deja sesion abierta', async () => {
    const fixture = await render('un-token-vencido');

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/verify-email`)
      .flush(
        { code: 'AUTH_VERIFICATION_TOKEN_EXPIRED' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('venció');

    const boton = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    boton.click();
    await fixture.whenStable();

    const reenvio = TestBed.inject(HttpTestingController).expectOne(
      `${API}/auth/resend-verification`,
    );
    expect(reenvio.request.body).toEqual({ expiredToken: 'un-token-vencido' });
  });

  // Un enlace ya usado no se reenvia: no hay nada que renovar, hay que pedir uno
  // nuevo desde el principio.
  it('no ofrece reenviar cuando el enlace es invalido', async () => {
    const fixture = await render('un-token-usado');

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/verify-email`)
      .flush(
        { code: 'AUTH_VERIFICATION_TOKEN_INVALID' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });
});
