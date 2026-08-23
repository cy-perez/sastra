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
import { InboxPage } from './inbox-page';

/** La bandeja del moderador. HU-006, criterios 1, 3, 4 y 7. */
describe('InboxPage', () => {
  const API = 'https://api.pruebas.sastra.co/api/v1';

  const SESION: Session = {
    accessToken: 'un-token',
    user: {
      email: 'moderadora@sastra.co',
      displayName: 'Quien Modera',
      emailVerified: true,
      roles: ['MODERATOR'],
    },
  };

  const solicitud = (cambios: Record<string, unknown> = {}) => ({
    id: 'una-solicitud',
    attempts: 1,
    documentType: 'CC',
    documentNumberLastFour: '2947',
    documentHolderName: 'Ana Maria Garcia',
    documentSubmitted: true,
    selfieSubmitted: true,
    bank: 'bancolombia',
    bankAccountType: 'SAVINGS',
    bankAccountLastFour: '3456',
    bankAccountHolderName: 'Ana Maria Garcia',
    waitingSince: '2026-08-20T10:00:00Z',
    own: false,
    ...cambios,
  });

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

  const esperarBandeja = (backend: HttpTestingController) =>
    backend.expectOne((p) => p.method === 'GET' && p.url === `${API}/verifications`);

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

  const montar = async (respuesta: unknown[] | 'falla') => {
    const fixture = TestBed.createComponent(InboxPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    const peticion = esperarBandeja(backend);

    if (respuesta === 'falla') {
      peticion.flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Error' });
    } else {
      peticion.flush(respuesta);
    }
    await asentar(fixture);

    return fixture;
  };

  /** Criterio 1: la que lleva más tiempo esperando, primero. */
  it('muestra las solicitudes con la más vieja arriba', async () => {
    const fixture = await montar([
      solicitud({
        id: 'nueva',
        documentHolderName: 'Recien Llegada',
        waitingSince: '2026-08-22T10:00:00Z',
      }),
      solicitud({
        id: 'vieja',
        documentHolderName: 'Lleva Esperando',
        waitingSince: '2026-08-01T10:00:00Z',
      }),
    ]);

    const nombres = [...fixture.nativeElement.querySelectorAll('.titular')].map((n: Element) =>
      n.textContent?.trim(),
    );

    expect(nombres).toEqual(['Lleva Esperando', 'Recien Llegada']);
  });

  /** Criterio 3: el estado vacío del sistema, no una tabla sin filas. */
  it('dice que no hay nada por revisar cuando la bandeja está vacía', async () => {
    const fixture = await montar([]);

    expect(fixture.nativeElement.textContent).toContain('No hay nada por revisar');
    expect(fixture.nativeElement.querySelectorAll('.solicitud')).toHaveLength(0);
  });

  /** Criterio 4: si falla, se dice y se puede reintentar. */
  it('ofrece reintentar cuando la bandeja no carga', async () => {
    const fixture = await montar('falla');

    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar la bandeja');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });

  /**
   * Criterio 7. Con texto y no solo con color: quien revisa muchas al día tiene que
   * poder leerlo, y un color no puede ser el único portador de información.
   */
  it('señala con texto que el titular de la cuenta no coincide', async () => {
    const fixture = await montar([solicitud({ bankAccountHolderName: 'Carlos Perez' })]);

    expect(fixture.nativeElement.textContent).toContain('no coincide');
  });

  it('no señala discrepancia cuando los titulares coinciden', async () => {
    const fixture = await montar([solicitud()]);

    expect(fixture.nativeElement.textContent).not.toContain('no coincide');
  });

  /**
   * La regresión que frontend/CLAUDE.md deja fijada: en una carga de página el
   * componente nace **antes** de que la sesión llegue por la cookie de refresco. Si la
   * señal se leyera dentro de la función de opciones, la consulta nacería deshabilitada
   * y la bandeja se quedaría cargando para siempre. Es lo que dejó `/mi-cuenta` sin
   * cargar.
   */
  it('pide la bandeja aunque la sesión llegue después de crear el componente', async () => {
    TestBed.inject(SessionStore).clear();

    const fixture = TestBed.createComponent(InboxPage);
    await fixture.whenStable();

    TestBed.inject(SessionStore).set(SESION);
    await fixture.whenStable();

    esperarBandeja(TestBed.inject(HttpTestingController)).flush([solicitud()]);
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Ana Maria Garcia');
  });

  /**
   * Criterio 13 sobre lo que la lista pinta: solo los cuatro últimos. El servidor ya lo
   * garantiza, pero si un día mandara de más, la pantalla no puede ser cómplice.
   */
  it('no pinta ningún número completo', async () => {
    const fixture = await montar([solicitud()]);

    expect(fixture.nativeElement.textContent).not.toContain('1053812947');
    expect(fixture.nativeElement.textContent).not.toContain('91500123456');
  });
});
