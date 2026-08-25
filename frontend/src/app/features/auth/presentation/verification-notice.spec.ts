import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { VerificationNotice } from './verification-notice';

/** Criterio 13 de HU-001. */
describe('VerificationNotice', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const sesionCon = (emailVerified: boolean): Session => ({
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana', emailVerified, roles: [] },
  });

  const render = async () => {
    const fixture = TestBed.createComponent(VerificationNotice);
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

  // Sin sesion, la ausencia de verificacion no significa nada: no hay cuenta de
  // la que hablar.
  it('no aparece para quien no ha entrado', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });

  /**
   * Este componente vive en la raiz del sitio, asi que su AuthStore se construye
   * en **todas** las paginas, tambien durante el renderizado en servidor, donde
   * no hay sesion nunca (session.store.ts).
   *
   * <p>Las consultas de perfil y de sesiones son rutas autenticadas: sin token
   * solo pueden responder 401. Cuando salian igual, cada renderizado gastaba dos
   * peticiones para descubrir lo que ya se sabia, y con la configuracion de
   * relleno (apiBaseUrl vacia) el interceptor lanzaba dentro del render, la
   * consulta no se resolvia y la pagina se quedaba colgada sin un solo error en
   * el registro. Medido: 2,95s por pagina con las dos peticiones, 0,27s sin
   * ellas.
   */
  it('no pide perfil ni sesiones mientras no haya sesion', async () => {
    await render();

    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('no aparece cuando el correo ya esta verificado', async () => {
    TestBed.inject(SessionStore).set(sesionCon(true));
    const fixture = await render();

    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });

  it('avisa y ofrece el reenvio a quien entro sin verificar', async () => {
    TestBed.inject(SessionStore).set(sesionCon(false));
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('Falta confirmar tu correo');
    expect(fixture.nativeElement.querySelector('button')).not.toBeNull();
  });

  /**
   * La direccion sale del token, nunca de la pantalla: por eso el endpoint es
   * /users/me y la peticion no lleva cuerpo con el correo.
   */
  it('pide el enlace nuevo contra la cuenta autenticada', async () => {
    TestBed.inject(SessionStore).set(sesionCon(false));
    const fixture = await render();

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    await fixture.whenStable();

    const peticion = TestBed.inject(HttpTestingController).expectOne(
      `${API}/users/me/email-verification`,
    );
    expect(peticion.request.headers.get('Authorization')).toBe('Bearer un-token');

    peticion.flush(null, { status: 202, statusText: 'Accepted' });
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('te enviamos un enlace nuevo');
  });

  /**
   * Ni deshabilitarlo ni sustituirlo por el mensaje: las dos cosas se llevan por
   * delante el foco de quien lo pulso con el teclado y lo devuelven al principio
   * del documento.
   */
  it('conserva el boton y el foco despues de reenviar', async () => {
    TestBed.inject(SessionStore).set(sesionCon(false));
    const fixture = await render();

    const boton = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    boton.focus();
    boton.click();
    // La mutacion pasa a "en curso" en una microtarea, no en el mismo tic. No se
    // usa whenStable aqui: con la peticion todavia en vuelo no vuelve.
    await new Promise((listo) => setTimeout(listo, 0));
    fixture.detectChanges();

    expect(boton.disabled).toBe(false);
    expect(boton.getAttribute('aria-busy')).toBe('true');

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/users/me/email-verification`)
      .flush(null, { status: 202, statusText: 'Accepted' });
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('button')).toBe(boton);
    expect(document.activeElement).toBe(boton);
  });

  // El aside conserva su landmark: con role="status" encima, este bloque y su
  // boton no pertenecerian a ninguna region de la pagina.
  it('mantiene el landmark y deja la region viva en el mensaje', async () => {
    TestBed.inject(SessionStore).set(sesionCon(false));
    const fixture = await render();

    const aside = fixture.nativeElement.querySelector('aside') as HTMLElement;
    expect(aside.getAttribute('role')).toBeNull();
    expect(aside.getAttribute('aria-label')).toBe('Verificación de correo pendiente');

    // Presentes y vacias desde el principio: una region viva que nace con su
    // texto dentro no se anuncia.
    expect(aside.querySelector('[role="status"]')).not.toBeNull();
    expect(aside.querySelector('[role="alert"]')).not.toBeNull();
    expect(aside.querySelector('[role="status"]')?.textContent?.trim()).toBe('');
  });

  it('traduce el limite de reenvios en vez de mostrar texto del servidor', async () => {
    TestBed.inject(SessionStore).set(sesionCon(false));
    const fixture = await render();

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/users/me/email-verification`)
      .flush(
        { code: 'AUTH_RESEND_LIMIT_REACHED', detail: 'rate limit bucket empty' },
        { status: 429, statusText: 'Too Many Requests' },
      );
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Espera una hora');
    expect(fixture.nativeElement.textContent).not.toContain('rate limit bucket empty');
  });
});
