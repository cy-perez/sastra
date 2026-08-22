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
import { VerificationPage } from './verification-page';

/** El progreso de la verificación de vendedor. HU-002. */
describe('VerificationPage', () => {
  const API = 'https://api.pruebas.sastra.co/api/v1';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const VACIA = {
    status: 'IN_PROGRESS',
    attempts: 0,
    remainingAttempts: 3,
    complete: false,
    documentSubmitted: false,
    documentType: null,
    documentNumberLastFour: null,
    documentHolderName: null,
    selfieSubmitted: false,
    bank: null,
    bankAccountType: null,
    bankAccountLastFour: null,
    bankAccountHolderName: null,
    rejectionReason: null,
    rejectionNote: null,
    updatedAt: '2026-08-21T15:00:00Z',
  };

  const COMPLETA = {
    ...VACIA,
    complete: true,
    documentSubmitted: true,
    documentType: 'CC',
    documentNumberLastFour: '2947',
    documentHolderName: 'Ana Maria Garcia',
    selfieSubmitted: true,
    bank: 'bancolombia',
    bankAccountType: 'SAVINGS',
    bankAccountLastFour: '3456',
    bankAccountHolderName: 'Ana Maria Garcia',
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

  const esperarEstado = (backend: HttpTestingController) =>
    backend.expectOne(
      (peticion) => peticion.method === 'GET' && peticion.url === `${API}/users/me/verification`,
    );

  const montar = async (respuesta: object | 'sin-empezar') => {
    const fixture = TestBed.createComponent(VerificationPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    const peticion = esperarEstado(backend);

    if (respuesta === 'sin-empezar') {
      peticion.flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    } else {
      peticion.flush(respuesta);
    }
    await asentar(fixture);

    return { fixture, backend };
  };

  const boton = (fixture: { nativeElement: HTMLElement }, texto: string) =>
    [...fixture.nativeElement.querySelectorAll('button')].find((candidato) =>
      candidato.textContent?.includes(texto),
    ) as HTMLButtonElement | undefined;

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

  /**
   * La regresión que frontend/CLAUDE.md deja fijada: en una carga de página el
   * componente nace **antes** de que la sesión llegue por la cookie de refresco. Si la
   * señal se leyera dentro de la función de opciones, la consulta nacería deshabilitada y
   * no se reactivaría nunca, y la pantalla se quedaría cargando para siempre. Es lo que
   * dejó `/mi-cuenta` sin cargar.
   */
  it('pide el estado aunque la sesión llegue después de crear el componente', async () => {
    TestBed.inject(SessionStore).clear();

    const fixture = TestBed.createComponent(VerificationPage);
    await fixture.whenStable();

    TestBed.inject(SessionStore).set(SESION);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    esperarEstado(backend).flush(VACIA);
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Te falta algo por entregar');
  });

  // --- Sin empezar ----------------------------------------------------------

  /** El 404 no es un error: es no haber empezado. */
  it('ofrece empezar cuando no hay solicitud', async () => {
    const { fixture } = await montar('sin-empezar');

    expect(boton(fixture, 'Empezar')).toBeDefined();
    expect(fixture.nativeElement.textContent).toContain('máximo 2 días hábiles');
  });

  it('inicia la solicitud al pulsar empezar', async () => {
    const { fixture, backend } = await montar('sin-empezar');

    boton(fixture, 'Empezar')?.click();
    await fixture.whenStable();

    backend
      .expectOne(
        (peticion) => peticion.method === 'POST' && peticion.url === `${API}/users/me/verification`,
      )
      .flush(VACIA);
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Tu documento de identidad');
  });

  // --- Los tres pasos -------------------------------------------------------

  it('muestra los tres pasos con su estado en palabras, no solo en color', async () => {
    const { fixture } = await montar({ ...VACIA, documentSubmitted: true });

    const pasos = [...fixture.nativeElement.querySelectorAll('.paso')];

    expect(pasos).toHaveLength(3);
    expect(pasos[0].textContent).toContain('Listo');
    expect(pasos[1].textContent).toContain('Falta');
    expect(pasos[2].textContent).toContain('Falta');
  });

  /**
   * Criterio 11: lo único que el servidor manda son cuatro dígitos, y la pantalla no
   * puede pintar nada más porque no lo tiene.
   */
  it('muestra solo los cuatro últimos dígitos', async () => {
    const { fixture } = await montar(COMPLETA);
    const texto = fixture.nativeElement.textContent as string;

    expect(texto).toContain('2947');
    expect(texto).toContain('3456');
    expect(texto).not.toContain('1053812947');
    expect(texto).not.toContain('documentos/');
    expect(texto).not.toContain('selfies/');
  });

  // --- Enviar ---------------------------------------------------------------

  it('ofrece enviar cuando está completa', async () => {
    const { fixture } = await montar(COMPLETA);

    expect(boton(fixture, 'Enviar para revisión')).toBeDefined();
  });

  it('explica qué falta en lugar de ofrecer enviar', async () => {
    const { fixture } = await montar(VACIA);

    expect(boton(fixture, 'Enviar para revisión')).toBeUndefined();
    expect(fixture.nativeElement.textContent).toContain('Completa los tres pasos');
  });

  /**
   * `complete` viene del servidor e incluye la coincidencia de titular de RN-012: los
   * tres pasos pueden estar entregados y aun así no poder enviarse. Comparar los nombres
   * aquí sería reimplementar la regla con otro criterio.
   */
  it('no ofrece enviar si el servidor dice que no está completa, aunque los tres pasos estén', async () => {
    const { fixture } = await montar({
      ...COMPLETA,
      complete: false,
      bankAccountHolderName: 'Pedro Ramirez',
    });

    expect(boton(fixture, 'Enviar para revisión')).toBeUndefined();
  });

  it('envía a revisión y refleja el estado nuevo', async () => {
    const { fixture, backend } = await montar(COMPLETA);

    boton(fixture, 'Enviar para revisión')?.click();
    await fixture.whenStable();

    backend
      .expectOne(
        (peticion) =>
          peticion.method === 'POST' && peticion.url === `${API}/users/me/verification/submission`,
      )
      .flush({ ...COMPLETA, status: 'PENDING_REVIEW', attempts: 1, remainingAttempts: 2 });
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Estamos revisando tu solicitud');
    expect(boton(fixture, 'Enviar para revisión')).toBeUndefined();
  });

  it('no ofrece enviar lo que ya está en revisión', async () => {
    const { fixture } = await montar({ ...COMPLETA, status: 'PENDING_REVIEW', attempts: 1 });

    expect(boton(fixture, 'Enviar para revisión')).toBeUndefined();
  });

  // --- Rechazo y RN-014 -----------------------------------------------------

  it('muestra el motivo del rechazo traducido y la nota', async () => {
    const { fixture } = await montar({
      ...COMPLETA,
      status: 'REJECTED',
      attempts: 1,
      remainingAttempts: 2,
      rejectionReason: 'ILLEGIBLE_PHOTOS',
      rejectionNote: 'El reverso sale oscuro',
    });

    const texto = fixture.nativeElement.textContent as string;

    expect(texto).toContain('No pudimos verificarte');
    expect(texto).toContain('Las fotos no se pueden leer');
    expect(texto).toContain('El reverso sale oscuro');
    expect(texto).toContain('Te quedan 2 intentos');
  });

  it('avisa cuando se agotaron los tres intentos, sin ofrecer enviar', async () => {
    const { fixture } = await montar({
      ...COMPLETA,
      status: 'REJECTED',
      attempts: 3,
      remainingAttempts: 0,
      rejectionReason: 'ILLEGIBLE_PHOTOS',
    });

    expect(boton(fixture, 'Enviar para revisión')).toBeUndefined();
    expect(fixture.nativeElement.textContent).toContain('Usaste tus tres intentos');
  });

  it('anuncia el sello cuando ya está verificada', async () => {
    const { fixture } = await montar({ ...COMPLETA, status: 'VERIFIED', attempts: 1 });

    expect(fixture.nativeElement.textContent).toContain('Ya eres vendedor verificado');
  });

  // --- Errores --------------------------------------------------------------

  it('traduce el error del servidor al enviar', async () => {
    const { fixture, backend } = await montar(COMPLETA);

    boton(fixture, 'Enviar para revisión')?.click();
    await fixture.whenStable();

    backend
      .expectOne(
        (peticion) =>
          peticion.method === 'POST' && peticion.url === `${API}/users/me/verification/submission`,
      )
      .flush({ code: 'SELLER_DOCUMENT_ALREADY_VERIFIED' }, { status: 409, statusText: 'Conflict' });
    await asentar(fixture);

    const aviso = fixture.nativeElement.querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Ese documento ya está verificado en otra cuenta');
  });
});
