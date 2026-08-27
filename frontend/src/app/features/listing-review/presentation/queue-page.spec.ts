import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { QueuePage } from './queue-page';

/** La cola de moderación de publicaciones. HU-008, criterios 1, 4, 5, 6 y 12. */
describe('QueuePage', () => {
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

  const fila = (cambios: Record<string, unknown> = {}) => ({
    id: 'una-publicacion',
    title: 'Camisa de lino color hueso',
    price: { amount: 185000, currency: 'COP' },
    waitingSince: '2026-08-20T10:00:00Z',
    requiresAttention: false,
    attentionReasons: [],
    coverUrl: 'https://cdn.sendik.co/frontal.jpg',
    // No viaja, y la bandeja no puede empezar a repartirlo: se deja como campo ajeno al
    // tipo para que la asercion de mas abajo pueda fallar de verdad.
    sellerId: '0198f2aa-0000-7000-8000-000000000001',
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

  const esperarCola = (backend: HttpTestingController) =>
    backend.expectOne((p) => p.method === 'GET' && p.url === `${API}/moderation/listings`);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'moderacion/publicaciones', component: QueuePage }],
          withComponentInputBinding(),
        ),
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
    const fixture = TestBed.createComponent(QueuePage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    const peticion = esperarCola(backend);

    if (respuesta === 'falla') {
      peticion.flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Error' });
    } else {
      peticion.flush({ items: respuesta, page: 0, size: 20 });
    }
    await asentar(fixture);

    return fixture;
  };

  const titulos = (fixture: { nativeElement: HTMLElement }) =>
    [...fixture.nativeElement.querySelectorAll('a')].map((enlace: Element) =>
      enlace.querySelector('.titulo')?.textContent?.trim(),
    );

  /**
   * Criterio 8, la mitad que se olvida: al volver de decidir se dice qué se hizo.
   *
   * <p>El mecanismo entero —leer el estado de la navegación y anunciarlo en una región
   * viva— no tenía ninguna prueba: se podía borrar con la suite en verde.
   *
   * <p>Se navega de verdad con el router para que `getCurrentNavigation()` exista, que es
   * de donde sale el dato y solo vive durante la navegación.
   */
  it('anuncia la decisión al volver a la bandeja', async () => {
    // Por el router de verdad: el dato sale de `getCurrentNavigation()`, que solo existe
    // mientras la navegacion ocurre. Con `createComponent` directo no hay ninguna, y la
    // prueba diria que el mecanismo no funciona cuando en la aplicacion si.
    const harness = await RouterTestingHarness.create();
    // El estado va en la navegacion, que es como llega de verdad: el detalle navega con
    // `state: { decision }` despues de decidir.
    await TestBed.inject(Router).navigateByUrl('/moderacion/publicaciones', {
      state: { decision: 'approved' },
    });
    harness.detectChanges();

    esperarCola(TestBed.inject(HttpTestingController)).flush({ items: [], page: 0, size: 20 });
    harness.detectChanges();

    const aviso = harness.routeNativeElement?.querySelector('.aviso-hecho');
    expect(aviso?.getAttribute('role')).toBe('status');
    expect(aviso?.textContent).toContain('Publicación aprobada');
  });

  it('no anuncia nada si no se viene de decidir', async () => {
    const fixture = await montar([]);

    expect(fixture.nativeElement.querySelector('.aviso-hecho')).toBeNull();
  });

  /** Criterio 1: la que lleva más tiempo esperando, primero. */
  it('muestra las publicaciones con la más vieja arriba', async () => {
    const fixture = await montar([
      fila({ id: 'nueva', title: 'Recien Llegada', waitingSince: '2026-08-22T10:00:00Z' }),
      fila({ id: 'vieja', title: 'Lleva Esperando', waitingSince: '2026-08-01T10:00:00Z' }),
    ]);

    expect(titulos(fixture)).toEqual(['Lleva Esperando', 'Recien Llegada']);
  });

  /** Criterio 4: el estado vacío del sistema, no una tabla sin filas. */
  it('dice que no hay nada por revisar cuando la cola está vacía', async () => {
    const fixture = await montar([]);

    expect(fixture.nativeElement.textContent).toContain('No hay nada por revisar');
    expect(fixture.nativeElement.querySelectorAll('li')).toHaveLength(0);
  });

  /**
   * Criterio 5, la mitad que se olvida: mientras carga se pinta el esqueleto del sistema.
   *
   * <p>Y se esconde de la accesibilidad: para quien no ve la pantalla, un esqueleto es
   * una lista de tres elementos vacios. Lo que se anuncia es que esta cargando.
   */
  it('muestra el esqueleto mientras carga, sin anunciarlo como una lista', async () => {
    const fixture = TestBed.createComponent(QueuePage);
    await fixture.whenStable();
    esperarCola(TestBed.inject(HttpTestingController));
    fixture.detectChanges();

    const lista = fixture.nativeElement.querySelector('ul');
    expect(fixture.nativeElement.querySelectorAll('.esqueleto').length).toBeGreaterThan(0);
    expect(lista?.getAttribute('aria-hidden')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('Cargando');
  });

  /** Criterio 5: si falla, se dice y se puede reintentar. */
  it('ofrece reintentar cuando la cola no carga', async () => {
    const fixture = await montar('falla');

    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar la bandeja');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });

  it('vuelve a pedir la cola al reintentar', async () => {
    const fixture = await montar('falla');

    const reintentar = [...fixture.nativeElement.querySelectorAll('button')].find((b: Element) =>
      b.textContent?.includes('Reintentar'),
    ) as HTMLButtonElement;
    reintentar.click();
    await new Promise((listo) => setTimeout(listo, 0));

    esperarCola(TestBed.inject(HttpTestingController)).flush({
      items: [fila()],
      page: 0,
      size: 20,
    });
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Camisa de lino color hueso');
  });

  /**
   * Criterio 6: la marca se anuncia con texto, no solo con color.
   *
   * <p>Es la prueba que impide que alguien la convierta en un punto de color: un color no
   * puede ser el unico portador de informacion, y quien revisa muchas al dia necesita
   * leerlo para ordenar su trabajo.
   */
  it('anuncia con texto que una publicación necesita atención', async () => {
    const fixture = await montar([
      fila({ requiresAttention: true, attentionReasons: ['PRICE_OUT_OF_RANGE'] }),
    ]);

    expect(fixture.nativeElement.textContent).toContain('Necesita atención');
  });

  it('no marca las que no lo necesitan', async () => {
    const fixture = await montar([fila()]);

    expect(fixture.nativeElement.textContent).not.toContain('Necesita atención');
  });

  /** Criterio 12 y RN-063: se sabe antes de abrir que sobre ésta no se va a poder decidir. */
  it('dice en la lista cuál publicación es de quien está mirando', async () => {
    const fixture = await montar([
      fila({ id: 'mia', title: 'La Mia', own: true }),
      fila({ id: 'ajena', title: 'La Ajena', own: false, waitingSince: '2026-08-21T10:00:00Z' }),
    ]);

    const filas = [...fixture.nativeElement.querySelectorAll('li')];
    expect(filas[0].textContent).toContain('Esta publicación es tuya');
    expect(filas[1].textContent).not.toContain('Esta publicación es tuya');
  });

  /**
   * La bandeja no reparte identificadores de vendedores.
   *
   * <p>Con el rol basta para verla, asi que si llevara el vendedor de cada fila seria de
   * paso una lista de quien vende que. Lo unico que se dice es si la fila es tuya.
   */
  it('no deja el identificador del vendedor en lo que se pinta', async () => {
    const fixture = await montar([fila()]);

    expect(fixture.nativeElement.innerHTML).not.toContain('0198f2aa-0000-7000-8000-000000000001');
  });

  /** El precio se formatea con Intl y la configuración regional, no con un `toFixed`. */
  it('muestra el precio en pesos y no el número crudo', async () => {
    const fixture = await montar([fila()]);

    expect(fixture.nativeElement.textContent).not.toContain('185000');
    expect(fixture.nativeElement.textContent).toContain('185');
  });

  /**
   * La sesión llega **después** de crear el componente, como en una carga real.
   *
   * <p>`frontend/CLAUDE.md` la exige y HU-006 la tiene; aquí faltaba, y es justo lo que
   * protege el `enabled` del almacén.
   */
  it('pide la cola aunque la sesión llegue después de crear el componente', async () => {
    TestBed.inject(SessionStore).clear();

    const fixture = TestBed.createComponent(QueuePage);
    await fixture.whenStable();

    TestBed.inject(SessionStore).set(SESION);
    // Un turno, sin esperar estabilidad: la peticion que acaba de nacer sigue viva y
    // Angular la cuenta como tarea pendiente.
    await new Promise((listo) => setTimeout(listo, 0));
    fixture.detectChanges();

    esperarCola(TestBed.inject(HttpTestingController)).flush({
      items: [fila()],
      page: 0,
      size: 20,
    });
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }

    expect(fixture.nativeElement.textContent).toContain('Camisa de lino color hueso');
  });

  /** Caso borde: la toma frontal puede faltar, y la fila sigue siendo abrible. */
  it('pinta la fila aunque no haya toma frontal', async () => {
    const fixture = await montar([fila({ coverUrl: null })]);

    expect(fixture.nativeElement.querySelector('.portada--ausente')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Camisa de lino color hueso');
  });
});
