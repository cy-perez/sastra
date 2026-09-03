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

  const navegacionDe = (fixture: { nativeElement: HTMLElement }) =>
    fixture.nativeElement.querySelector('nav[aria-label="Páginas de la bandeja"]');

  const esperarBandeja = (backend: HttpTestingController) =>
    backend.expectOne((p) => p.method === 'GET' && p.url === `${API}/verifications`);

  /** La bandeja responde una página, no una lista pelada: es un listado administrativo. */
  const pagina = (filas: unknown[], numero = 0, hayMas = false) => ({
    items: filas,
    page: numero,
    size: 20,
    hasMore: hayMas,
  });

  /**
   * Una página llena. **Que esté llena no dice nada sobre si hay otra**, y ese es el
   * punto: quien lo dice es `hasMore`, que llega aparte y por eso se pasa aparte.
   */
  const paginaLlena = (numero = 0, hayMas = false) =>
    pagina(
      Array.from({ length: 20 }, (_, cual) => solicitud({ id: `solicitud-${numero}-${cual}` })),
      numero,
      hayMas,
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

  const montar = async (respuesta: unknown[] | 'falla', hayMas = false) => {
    const fixture = TestBed.createComponent(InboxPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    const peticion = esperarBandeja(backend);

    if (respuesta === 'falla') {
      peticion.flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Error' });
    } else {
      peticion.flush(pagina(respuesta, 0, hayMas));
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
    const fixture = await montar(paginaLlena().items, true);
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
    const fixture = await montar(paginaLlena().items, true);
    const backend = TestBed.inject(HttpTestingController);

    botonDe(fixture, 'Siguiente')?.click();
    await fixture.whenStable();
    esperarBandeja(backend).flush(paginaLlena(1));
    await asentar(fixture);

    botonDe(fixture, 'Anterior')?.click();
    await fixture.whenStable();

    expect(esperarBandeja(backend).request.params.get('page')).toBe('0');
  });

  /**
   * <strong>El defecto que este campo vino a arreglar.</strong> Cuando el total es múltiplo
   * exacto del tamaño —veinte pendientes, veinte por página— la última página viene llena.
   * Deducir de eso que hay otra ofrecía un «Siguiente» hacia una página vacía: quien revisa
   * pulsaba, no encontraba nada, y no podía saber si la cola se acabó o si algo se rompió.
   *
   * <p>La página de esta prueba viene llena y el servidor dice que no hay más. Es el caso
   * que la longitud de `items` no puede distinguir.
   */
  it('no ofrece pasar de página cuando la página llena es la última', async () => {
    const fixture = await montar(paginaLlena().items, false);

    expect(botonDe(fixture, 'Siguiente')).toBeUndefined();
  });

  /**
   * Con una sola página no hay navegación que pintar. **Se afirma la ausencia de la región
   * y no la del literal**: preguntando por el texto del botón, esta prueba seguiría en
   * verde el día que «Siguiente» pase a decir «Siguiente →».
   */
  it('no pinta la navegación cuando todo cabe en una página', async () => {
    const fixture = await montar([solicitud()]);

    expect(navegacionDe(fixture)).toBeNull();
  });

  /**
   * <strong>La forma peligrosa del mismo defecto.</strong> En la página 0 la navegación
   * entera desaparece, así que no hay nada que pulsar. En la página 1 sí está —hace falta
   * «Anterior» para volver— y «Siguiente» sigue en el DOM y sigue recibiendo el clic,
   * porque se marca con `aria-disabled` y no con `disabled` para no perder el foco.
   *
   * <p>Lo que sujeta que no lleve a ninguna parte es el guardián de `paginaSiguiente()` en
   * el store, y no había ninguna prueba que lo dijera: quitándolo, la suite entera pasaba.
   */
  it('en la última página no lleva a ningún sitio aunque el botón siga ahí', async () => {
    const fixture = await montar(paginaLlena().items, true);
    const backend = TestBed.inject(HttpTestingController);

    botonDe(fixture, 'Siguiente')?.click();
    await fixture.whenStable();
    esperarBandeja(backend).flush(paginaLlena(1));
    await asentar(fixture);

    const siguiente = botonDe(fixture, 'Siguiente');
    expect(siguiente?.getAttribute('aria-disabled')).toBe('true');
    expect(siguiente?.disabled).toBe(false);

    siguiente?.click();
    await asentar(fixture);

    backend.expectNone((p) => p.method === 'GET' && p.url === `${API}/verifications`);
    expect(fixture.nativeElement.textContent).toContain('Página 2');
  });

  /**
   * La página que se queda vacía porque se decidieron todas sus filas conserva «Anterior».
   * Es el motivo por el que la navegación vive fuera de los tres estados de carga, y no
   * había prueba que lo sujetara.
   */
  it('deja volver desde una página que se quedó vacía', async () => {
    const fixture = await montar(paginaLlena().items, true);
    const backend = TestBed.inject(HttpTestingController);

    botonDe(fixture, 'Siguiente')?.click();
    await fixture.whenStable();
    esperarBandeja(backend).flush(pagina([], 1));
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('No hay nada por revisar');
    expect(botonDe(fixture, 'Anterior')?.getAttribute('aria-disabled')).toBe('false');
  });

  /**
   * <strong>La lección que esta base ya pagó una vez.</strong> Un botón que se deshabilita
   * con el atributo `disabled` en el mismo tick del clic, con el foco dentro, manda el foco
   * a `body`: quien navega con teclado tiene que tabular desde el principio del documento.
   * Aquí pasaría justo al llegar al extremo, que es cuando alguien está recorriendo.
   */
  it('marca el extremo sin quitarle el foco al botón', async () => {
    const fixture = await montar(paginaLlena().items, true);

    const anterior = botonDe(fixture, 'Anterior');

    expect(anterior?.getAttribute('aria-disabled')).toBe('true');
    expect(anterior?.disabled).toBe(false);
  });
});
