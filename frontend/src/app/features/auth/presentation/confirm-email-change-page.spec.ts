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
import { ConfirmEmailChangePage } from './confirm-email-change-page';

/** Criterio 21 de HU-001: confirmar el correo nuevo con el enlace. */
describe('ConfirmEmailChangePage', () => {
  const API = 'https://api.pruebas.sastra.co/api/v1';

  const render = async (token?: string) => {
    const fixture = TestBed.createComponent(ConfirmEmailChangePage);
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
    for (let vuelta = 0; vuelta < 3; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
      await fixture.whenStable();
    }
  };

  const confirmar = (fixture: { nativeElement: HTMLElement }) =>
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();

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
   * Lo esencial: abrir la pagina no consume el enlace. Una vista previa de enlace
   * en WhatsApp o un antivirus de correo abren la direccion sin que nadie la haya
   * visto, y gastarian el enlace antes de que la persona llegara a el.
   */
  it('no consume el enlace al abrir la pagina criterio_21', async () => {
    const fixture = await render('un-token');
    await asentar(fixture);

    TestBed.inject(HttpTestingController).expectNone(`${API}/auth/confirm-email-change`);
  });

  it('confirma el cambio al pulsar el boton criterio_21', async () => {
    const fixture = await render('un-token');
    await asentar(fixture);

    confirmar(fixture);
    await fixture.whenStable();

    const peticion = TestBed.inject(HttpTestingController).expectOne(
      `${API}/auth/confirm-email-change`,
    );
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ token: 'un-token' });
  });

  it('avisa de que quedo hecho criterio_21', async () => {
    const fixture = await render('un-token');
    await asentar(fixture);

    confirmar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/confirm-email-change`)
      .flush(null, { status: 204, statusText: 'No Content' });
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('cambiamos tu correo');
  });

  // Sin token no hay nada que confirmar, y no se manda una peticion vacia.
  it('explica que falta el enlace cuando no hay token criterio_21', async () => {
    const fixture = await render();
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Falta el enlace');
    TestBed.inject(HttpTestingController).expectNone(`${API}/auth/confirm-email-change`);
  });

  /**
   * Entre pedir el cambio y confirmarlo pasa hasta un dia, y en ese hueco alguien
   * pudo registrarse con esa direccion. El servidor manda el codigo y el texto
   * sale de aqui.
   */
  it('traduce que la direccion ya tiene cuenta criterio_21', async () => {
    const fixture = await render('un-token');
    await asentar(fixture);

    confirmar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/confirm-email-change`)
      .flush(
        { code: 'AUTH_EMAIL_TAKEN', title: 'da igual', traceId: 'x' },
        { status: 409, statusText: 'Conflict' },
      );
    await asentar(fixture);

    const aviso = fixture.nativeElement.querySelector('[role="alert"]');
    expect(aviso?.textContent).toContain('ya tiene una cuenta');
  });

  it('traduce un enlace que ya no sirve criterio_21', async () => {
    const fixture = await render('un-token');
    await asentar(fixture);

    confirmar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/confirm-email-change`)
      .flush(
        { code: 'AUTH_VERIFICATION_TOKEN_EXPIRED', title: 'da igual', traceId: 'x' },
        { status: 410, statusText: 'Gone' },
      );
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('venció');
  });
});
