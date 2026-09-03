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
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const SESION: Session = {
    accessToken: 'un-token',
    user: {
      email: 'moderadora@sendik.co',
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
    // Los completos NO viajan (criterio 11). Se dejan como campos ajenos al tipo para que
    // la asercion de mas abajo pueda fallar de verdad.
    documentNumberFull: '1053812947',
    bankAccountNumberFull: '91500123456',
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

  /** La bandeja responde una página, no una lista pelada: es un listado administrativo. */
  const pagina = (filas: unknown[], numero = 0) => ({ items: filas, page: numero, size: 20 });

  /**
   * Una página **llena**, que es lo que hace pensar que puede haber otra.
   *
   * <p>El servidor no dice cuántas hay en total, así que «hay más» se deduce de que la
   * página venga completa. Con menos filas que el tamaño no habría botón que pulsar.
   */
  const paginaLlena = (numero = 0) =>
    pagina(
      Array.from({ length: 20 }, (_, cual) => solicitud({ id: `solicitud-${numero}-${cual}` })),
      numero,
    );

  const botonDe = (fixture: { nativeElement: HTMLElement }, texto: string) =>
    [...fixture.nativeElement.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === texto,
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

  const montar = async (respuesta: unknown[] | 'falla') => {
    const fixture = TestBed.createComponent(InboxPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    const peticion = esperarBandeja(backend);

    if (respuesta === 'falla') {
      peticion.flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Error' });
    } else {
      peticion.flush(pagina(respuesta));
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

    // Por el nombre accesible del enlace, no por una clase: renombrar `.titular` no
    // cambia nada de lo que ve quien usa la pantalla y no puede romper esta prueba.
    const nombres = [...fixture.nativeElement.querySelectorAll('a')].map((enlace: Element) =>
      enlace.querySelector('.titular')?.textContent?.trim(),
    );

    expect(nombres).toEqual(['Lleva Esperando', 'Recien Llegada']);
  });

  /** Criterio 3: el estado vacío del sistema, no una tabla sin filas. */
  it('dice que no hay nada por revisar cuando la bandeja está vacía', async () => {
    const fixture = await montar([]);

    expect(fixture.nativeElement.textContent).toContain('No hay nada por revisar');
    expect(fixture.nativeElement.querySelectorAll('li')).toHaveLength(0);
  });

  /**
   * Criterio 4, la mitad que faltaba: mientras carga se pinta el esqueleto del sistema.
   *
   * <p>Y se esconde de la accesibilidad: para quien no ve la pantalla, un esqueleto es
   * una lista de tres elementos vacios. Lo que se anuncia es que esta cargando.
   */
  it('muestra el esqueleto mientras carga, sin anunciarlo como una lista', async () => {
    const fixture = TestBed.createComponent(InboxPage);
    await fixture.whenStable();
    esperarBandeja(TestBed.inject(HttpTestingController));
    fixture.detectChanges();

    const lista = fixture.nativeElement.querySelector('ul');
    expect(fixture.nativeElement.querySelectorAll('.esqueleto').length).toBeGreaterThan(0);
    expect(lista?.getAttribute('aria-hidden')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('Cargando');
  });

  /** El boton de reintentar tiene que reintentar de verdad. */
  it('vuelve a pedir la bandeja al reintentar', async () => {
    const fixture = await montar('falla');

    const reintentar = [...fixture.nativeElement.querySelectorAll('button')].find((b: Element) =>
      b.textContent?.includes('Reintentar'),
    ) as HTMLButtonElement;
    reintentar.click();
    await new Promise((listo) => setTimeout(listo, 0));

    esperarBandeja(TestBed.inject(HttpTestingController)).flush(pagina([solicitud()]));
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Ana Maria Garcia');
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

    esperarBandeja(TestBed.inject(HttpTestingController)).flush(pagina([solicitud()]));
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

  // --- La paginación --------------------------------------------------------

  /**
   * <strong>Es lo que le faltaba a esta pantalla.</strong> La bandeja pedía una sola página
   * y no ofrecía forma de pasar de ella, así que una solicitud que no estuviera entre las
   * primeras veinte no se podía alcanzar por ningún camino: ni buscándola, ni esperando.
   */
  it('pide la página siguiente al pulsar', async () => {
    const fixture = await montar(paginaLlena().items);
    const backend = TestBed.inject(HttpTestingController);

    botonDe(fixture, 'Siguiente')?.click();
    await fixture.whenStable();

    const peticion = esperarBandeja(backend);
    expect(peticion.request.params.get('page')).toBe('1');

    peticion.flush(pagina([solicitud({ id: 'de-la-segunda' })], 1));
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Página 2');
  });

  it('vuelve a la página anterior', async () => {
    const fixture = await montar(paginaLlena().items);
    const backend = TestBed.inject(HttpTestingController);

    botonDe(fixture, 'Siguiente')?.click();
    await fixture.whenStable();
    esperarBandeja(backend).flush(paginaLlena(1));
    await asentar(fixture);

    botonDe(fixture, 'Anterior')?.click();
    await fixture.whenStable();

    expect(esperarBandeja(backend).request.params.get('page')).toBe('0');
  });

  /** Sin página llena no hay a dónde ir, así que la navegación no se pinta. */
  it('no ofrece paginación cuando todo cabe en una página', async () => {
    const fixture = await montar([solicitud()]);

    expect(botonDe(fixture, 'Siguiente')).toBeUndefined();
    expect(botonDe(fixture, 'Anterior')).toBeUndefined();
  });

  /**
   * <strong>La lección que esta base ya pagó una vez.</strong> Un botón que se deshabilita
   * con el atributo `disabled` en el mismo tick del clic, con el foco dentro, manda el foco
   * a `body`: quien navega con teclado tiene que tabular desde el principio del documento.
   * Aquí pasaría justo al llegar al extremo, que es cuando alguien está recorriendo.
   */
  it('marca el extremo sin quitarle el foco al botón', async () => {
    const fixture = await montar(paginaLlena().items);

    const anterior = botonDe(fixture, 'Anterior');

    expect(anterior?.getAttribute('aria-disabled')).toBe('true');
    expect(anterior?.disabled).toBe(false);
  });
});
