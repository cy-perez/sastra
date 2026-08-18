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
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { EmailChangeForm } from './email-change-form';

/** Criterio 21 de HU-001: pedir el cambio de correo, que no lo cambia. */
describe('EmailChangeForm', () => {
  const API = 'https://api.pruebas.sastra.co/api/v1';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const PERFIL = {
    email: 'ana@correo.co',
    emailVerified: true,
    displayName: 'Ana Maria',
    city: null,
    phone: null,
  };

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

  const montar = async () => {
    const fixture = TestBed.createComponent(EmailChangeForm);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne((peticion) => peticion.method === 'GET' && peticion.url === `${API}/users/me`)
      .flush(PERFIL);
    await asentar(fixture);

    return { fixture, backend };
  };

  const escribir = (fixture: { nativeElement: HTMLElement }, valor: string) => {
    const control = fixture.nativeElement.querySelector('#correo-nuevo') as HTMLInputElement;
    control.value = valor;
    control.dispatchEvent(new Event('input'));
  };

  const enviar = (fixture: { nativeElement: HTMLElement }) =>
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).requestSubmit();

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

  it('muestra el correo actual criterio_21', async () => {
    const { fixture } = await montar();

    expect(fixture.nativeElement.textContent).toContain('ana@correo.co');
  });

  it('pide el cambio con la direccion escrita criterio_21', async () => {
    const { fixture, backend } = await montar();

    escribir(fixture, 'nueva@correo.co');
    enviar(fixture);
    await fixture.whenStable();

    const peticion = backend.expectOne(`${API}/users/me/email`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ newEmail: 'nueva@correo.co' });
  });

  /**
   * El mismo aviso este la direccion libre u ocupada: el servidor responde 202 en
   * los dos casos y aqui no hay nada que distinguir. Cualquier diferencia
   * convertiria este formulario en un detector de cuentas (criterio 2).
   */
  it('avisa igual sin decir si la direccion tenia cuenta criterio_21', async () => {
    const { fixture, backend } = await montar();

    escribir(fixture, 'ocupada@correo.co');
    enviar(fixture);
    await fixture.whenStable();

    backend.expectOne(`${API}/users/me/email`).flush(null, { status: 202, statusText: 'Accepted' });
    await asentar(fixture);

    const aviso = fixture.nativeElement.querySelector('[role="status"]');
    expect(aviso?.textContent).toContain('ocupada@correo.co');
    expect(fixture.nativeElement.textContent).not.toContain('ya tiene');
  });

  it('no viaja si el correo no tiene forma de correo criterio_21', async () => {
    const { fixture, backend } = await montar();

    escribir(fixture, 'no-es-un-correo');
    enviar(fixture);
    await asentar(fixture);

    backend.expectNone(`${API}/users/me/email`);
    expect(
      (fixture.nativeElement.querySelector('#correo-nuevo') as HTMLInputElement).getAttribute(
        'aria-invalid',
      ),
    ).toBe('true');
  });

  // No es un error, pero no hay nada que hacer: se evita el viaje y se dice.
  it('no viaja si es el correo que ya se tiene criterio_21', async () => {
    const { fixture, backend } = await montar();

    escribir(fixture, 'ANA@correo.co');
    enviar(fixture);
    await asentar(fixture);

    backend.expectNone(`${API}/users/me/email`);
    expect(fixture.nativeElement.textContent).toContain('Ese ya es tu correo actual');
  });
});
