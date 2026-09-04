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
import { MyListingsPage } from './my-listings-page';

/**
 * Las publicaciones propias. HU-007.
 *
 * Lo que importa es que el estado de cada una se entienda y que las acciones ofrecidas
 * sean las que ese estado admite: ofrecer «reactivar» sobre una publicada, o «pausar»
 * sobre una vendida, manda al vendedor contra un 409.
 */
describe('MyListingsPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = 'af8b9a52-4a3f-4a52-9a1e-8d9a2f1c4b70';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const publicacion = (cambios: Record<string, unknown> = {}) => ({
    id: ID,
    sellerId: 'vendedor',
    status: 'DRAFT',
    product: {
      categoryId: 'hoja-camisas',
      title: 'Camisa de lino',
      description: null,
      brand: null,
      condition: 'LIKE_NEW',
      size: null,
      measurements: {},
      color: null,
      price: { amount: 185000, currency: 'COP' },
      shipping: null,
      isSealed: null,
      warrantyMonths: null,
    },
    images: [],
    requiredShots: 8,
    requiresAttention: false,
    attentionReasons: [],
    rejectionReason: null,
    rejectionNote: null,
    publishedAt: null,
    createdAt: '2026-08-26T10:00:00Z',
    updatedAt: '2026-08-26T10:00:00Z',
    version: 1,
    ...cambios,
  });

  /** Los siete de RN-061, en el orden del ciclo de vida. Lo que no se diga va en cero. */
  const ESTADOS = [
    'DRAFT',
    'PENDING_REVIEW',
    'PUBLISHED',
    'REJECTED',
    'PAUSED',
    'SOLD',
    'ARCHIVED',
  ] as const;

  const resumen = (cuantas: Partial<Record<(typeof ESTADOS)[number], number>> = {}) => ({
    counts: ESTADOS.map((status) => ({ status, count: cuantas[status] ?? 0 })),
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

  /**
   * Deja pasar unos turnos y vuelve a pintar, sin esperar la estabilidad.
   *
   * <p>`whenStable` no sirve cuando queda alguna peticion sin responder: Angular la
   * cuenta como tarea pendiente y la espera no termina hasta que la prueba conteste.
   */
  const bombear = async (fixture: { detectChanges: () => void }) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  /**
   * Monta la pantalla y responde sus **dos** consultas: la lista y las cifras.
   *
   * <p>Son dos a propósito -el criterio 6 dice que el fallo de una no puede tapar la
   * otra-, así que aquí se responden por separado. Con `cifras` en nulo la del resumen se
   * deja en vuelo, que es lo que necesitan las pruebas del esqueleto y del error; en ese
   * caso no se puede usar `asentar`, porque `whenStable` no vuelve mientras quede una
   * petición sin responder.
   */
  const montar = async (items: object[], cifras: object | null = resumen()) => {
    // La sesión se pone aquí y no en el beforeEach: allí instancia el módulo de pruebas
    // y ninguna prueba puede ya sustituir un proveedor ni montar sin sesión.
    TestBed.inject(SessionStore).set(SESION);

    const fixture = TestBed.createComponent(MyListingsPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne(
        (peticion) => peticion.method === 'GET' && peticion.url === `${API}/users/me/listings`,
      )
      .flush({ items, page: 0, size: 20 });

    const peticionDeCifras = backend.expectOne(
      (peticion) =>
        peticion.method === 'GET' && peticion.url === `${API}/users/me/listings/summary`,
    );

    if (cifras === null) {
      await bombear(fixture);
      return { fixture, backend, peticionDeCifras };
    }

    peticionDeCifras.flush(cifras);
    await asentar(fixture);
    return { fixture, backend, peticionDeCifras };
  };

  /** Las cifras pintadas, cada una con su nombre y su número. */
  const cifrasEnPantalla = (fixture: { nativeElement: HTMLElement }) =>
    [...fixture.nativeElement.querySelectorAll('.mias__cifra')].map((cifra) => ({
      estado: cifra.querySelector('dt')?.textContent?.trim() ?? '',
      numero: cifra.querySelector('dd')?.textContent?.trim() ?? '',
    }));

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
  });

  // --- Las cifras del panel. HU-012 --------------------------------------

  /** Criterios 1 y 2: los siete estados, y el cero se dice en vez de esconderse. */
  it('pinta una cifra por estado, incluidas las que valen cero', async () => {
    const { fixture } = await montar(
      [publicacion()],
      resumen({ DRAFT: 3, PUBLISHED: 1, ARCHIVED: 12 }),
    );

    expect(cifrasEnPantalla(fixture)).toEqual([
      { estado: 'Borrador', numero: '3' },
      { estado: 'En revisión', numero: '0' },
      { estado: 'Publicada', numero: '1' },
      { estado: 'Rechazada', numero: '0' },
      { estado: 'Pausada', numero: '0' },
      { estado: 'Vendida', numero: '0' },
      { estado: 'Archivada', numero: '12' },
    ]);
  });

  /**
   * Criterio 3: cuenta nueva. Las siete cifras en cero **y** el vacío de la lista debajo,
   * que es el que ya existía. No dos mensajes de vacío distintos.
   */
  it('deja los siete en cero y el vacío de la lista debajo en una cuenta nueva', async () => {
    const { fixture } = await montar([]);

    expect(cifrasEnPantalla(fixture)).toHaveLength(7);
    expect(cifrasEnPantalla(fixture).every((cifra) => cifra.numero === '0')).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Todavía no has publicado nada');
  });

  /**
   * Criterio 5: esqueleto, no ceros provisionales.
   *
   * <p>Un cero se lee como un dato: quien lo vea de paso creerá que no tiene nada. Y quien
   * usa lector de pantalla tiene que oír que están cargando.
   */
  it('pinta el esqueleto mientras las cifras cargan, sin ceros provisionales', async () => {
    const { fixture } = await montar([publicacion()], null);

    expect(fixture.nativeElement.querySelectorAll('.mias__cifra-esqueleto')).toHaveLength(7);
    expect(cifrasEnPantalla(fixture)).toHaveLength(0);
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Cargando las cifras',
    );
  });

  /**
   * Criterio 6, y es el que de verdad importa: **una cifra que no llega no puede tapar las
   * publicaciones.** El error se acota a su fila y la lista sigue ahí.
   */
  it('deja ver la lista aunque las cifras fallen, y ofrece reintentar', async () => {
    const { fixture, peticionDeCifras } = await montar([publicacion()], null);

    peticionDeCifras.flush(
      { code: 'COMMON_UNEXPECTED' },
      { status: 500, statusText: 'Server Error' },
    );
    await bombear(fixture);

    expect(
      fixture.nativeElement.querySelector('.mias__cifras-error [role="alert"]'),
    ).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Camisa de lino');
    expect(boton(fixture, 'Reintentar')).toBeDefined();
  });

  /** Doble pulsación en reintentar: una petición, no dos encadenadas. */
  it('no encadena dos peticiones si se pulsa reintentar dos veces', async () => {
    const { fixture, backend, peticionDeCifras } = await montar([publicacion()], null);

    peticionDeCifras.flush(
      { code: 'COMMON_UNEXPECTED' },
      { status: 500, statusText: 'Server Error' },
    );
    await bombear(fixture);

    boton(fixture, 'Reintentar')?.click();
    await bombear(fixture);
    boton(fixture, 'Reintentar')?.click();
    await bombear(fixture);

    backend
      .expectOne(
        (peticion) =>
          peticion.method === 'GET' && peticion.url === `${API}/users/me/listings/summary`,
      )
      .flush(resumen({ DRAFT: 1 }));
    await asentar(fixture);

    expect(cifrasEnPantalla(fixture)[0]).toEqual({ estado: 'Borrador', numero: '1' });
  });

  /**
   * Criterio 4: archivar actualiza las cifras sin recargar.
   *
   * <p>Sale gratis y no por casualidad: la clave del resumen cuelga de la de la lista, así
   * que la invalidación que ya hacía cada mutación arrastra las dos. Esta prueba es lo que
   * impide que alguien las separe sin darse cuenta.
   */
  it('vuelve a pedir las cifras después de archivar', async () => {
    const { fixture, backend } = await montar([publicacion({ status: 'PUBLISHED' })]);

    boton(fixture, 'Archivar')?.click();
    await bombear(fixture);
    boton(fixture, 'Sí, archivar')?.click();
    await bombear(fixture);

    backend
      .expectOne((peticion) => peticion.method === 'POST' && peticion.url.endsWith('/archival'))
      .flush(publicacion({ status: 'ARCHIVED' }));
    await bombear(fixture);

    backend.expectOne(
      (peticion) =>
        peticion.method === 'GET' && peticion.url === `${API}/users/me/listings/summary`,
    );
  });

  /**
   * Un estado que no existe en RN-061 llegando del servidor: se ignora.
   *
   * <p>Ni se pinta una cifra sin nombre -no habría cómo traducirla- ni se rompe la fila.
   * Es lo que permite que el servidor añada un estado antes de que esta pantalla lo sepa.
   */
  it('ignora un estado que no conoce sin romper la fila', async () => {
    const { fixture } = await montar([publicacion()], {
      counts: [
        { status: 'DRAFT', count: 2 },
        { status: 'EN_LA_LUNA', count: 9 },
      ],
    });

    expect(cifrasEnPantalla(fixture)).toEqual([{ estado: 'Borrador', numero: '2' }]);
    expect(fixture.nativeElement.textContent).not.toContain('EN_LA_LUNA');
  });

  /** El estado vacío es un estado de pantalla, no un error. */
  it('invita a publicar cuando no hay nada', async () => {
    const { fixture } = await montar([]);

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('Todavía no has publicado nada');
    expect(texto).toContain('Publicar mi primer producto');
  });

  it('muestra cada publicación con su estado y su precio', async () => {
    const { fixture } = await montar([publicacion()]);

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('Camisa de lino');
    expect(texto).toContain('Borrador');
    // El precio se formatea con Intl en la configuración regional activa.
    expect(texto).toMatch(/185[.,\s]?000/);
  });

  /** Criterio 29: pausar deja de verse, y se reactiva sin pasar por moderación. */
  it('pausa una publicada por su ruta y la fila queda pausada', async () => {
    const { fixture, backend } = await montar([publicacion({ status: 'PUBLISHED' })]);

    expect(boton(fixture, 'Pausar')).toBeDefined();
    expect(boton(fixture, 'Reactivar')).toBeUndefined();

    boton(fixture, 'Pausar')?.click();
    await bombear(fixture);

    backend
      .expectOne(
        (llamada) => llamada.method === 'POST' && llamada.url.endsWith(`/listings/${ID}/pause`),
      )
      .flush(publicacion({ status: 'PAUSED' }));
    await bombear(fixture);

    // Y la lista se vuelve a pedir, porque «mis publicaciones» dejó de ser cierto.
    backend
      .match((llamada) => llamada.url === `${API}/users/me/listings`)
      .forEach((peticion) =>
        peticion.flush({ items: [publicacion({ status: 'PAUSED' })], page: 0, size: 20 }),
      );
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Pausada');
  });

  it('reactiva una pausada por su ruta', async () => {
    const { fixture, backend } = await montar([publicacion({ status: 'PAUSED' })]);

    expect(boton(fixture, 'Pausar')).toBeUndefined();
    boton(fixture, 'Reactivar')?.click();
    await bombear(fixture);

    backend.expectOne(
      (llamada) => llamada.method === 'DELETE' && llamada.url.endsWith(`/listings/${ID}/pause`),
    );
  });

  /** Criterio 30: archivada no vuelve a ningún estado, así que no se ofrece nada. */
  it('no ofrece nada sobre una archivada', async () => {
    const { fixture } = await montar([publicacion({ status: 'ARCHIVED' })]);

    expect(boton(fixture, 'Pausar')).toBeUndefined();
    expect(boton(fixture, 'Reactivar')).toBeUndefined();
    expect(boton(fixture, 'Archivar')).toBeUndefined();
    expect(fixture.nativeElement.textContent).toContain('Archivada');
  });

  /**
   * §9 del informe: esta pantalla no necesita el árbol de categorías.
   *
   * <p>Es la razón por la que existe {@code CategoriesStore} aparte. Sin esta prueba,
   * volver a juntarlos dejaría una petición de más que nadie notaría.
   */
  it('no pide el árbol de categorías', async () => {
    const { backend } = await montar([]);

    expect(backend.match((llamada) => llamada.url.endsWith('/categories'))).toHaveLength(0);
  });

  it('muestra el error cuando el listado falla', async () => {
    TestBed.inject(SessionStore).set(SESION);

    const fixture = TestBed.createComponent(MyListingsPage);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(
        (llamada) => llamada.method === 'GET' && llamada.url === `${API}/users/me/listings`,
      )
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await bombear(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  /**
   * La regresión que fija frontend/CLAUDE.md: el componente nace antes de que la sesión
   * llegue por la cookie de refresco.
   */
  it('pide el listado aunque la sesión llegue después de crear el componente', async () => {
    const fixture = TestBed.createComponent(MyListingsPage);
    await fixture.whenStable();

    TestBed.inject(SessionStore).set(SESION);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne(
        (llamada) => llamada.method === 'GET' && llamada.url === `${API}/users/me/listings`,
      )
      .flush({ items: [publicacion()], page: 0, size: 20 });

    // Las cifras esperan a la sesión por lo mismo que la lista, y son otra consulta: sin
    // responderla, `asentar` no vuelve nunca.
    backend
      .expectOne(
        (llamada) => llamada.method === 'GET' && llamada.url === `${API}/users/me/listings/summary`,
      )
      .flush(resumen({ DRAFT: 1 }));
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Camisa de lino');
    expect(cifrasEnPantalla(fixture)[0]).toEqual({ estado: 'Borrador', numero: '1' });
  });

  it('no ofrece pausar ni archivar una vendida', async () => {
    const { fixture } = await montar([publicacion({ status: 'SOLD' })]);

    expect(boton(fixture, 'Pausar')).toBeUndefined();
    expect(boton(fixture, 'Archivar')).toBeUndefined();
  });

  /**
   * Archivar es la única acción del vendedor que no se puede deshacer, y la única de
   * toda la historia que pide confirmación.
   */
  it('pide confirmación antes de archivar y no llama a la API hasta tenerla', async () => {
    const { fixture, backend } = await montar([publicacion({ status: 'PUBLISHED' })]);

    boton(fixture, 'Archivar')?.click();
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Archivar es para siempre');
    backend.verify();

    boton(fixture, 'Sí, archivar')?.click();
    await bombear(fixture);

    backend.expectOne(
      (peticion) => peticion.method === 'POST' && peticion.url.endsWith('/archival'),
    );
  });

  /**
   * El foco no se pierde al abrir ni al cerrar la confirmación.
   *
   * <p>Es lo más parecido a un diálogo de toda la historia, y el botón que la abre se
   * destruye al abrirla: sin mover el foco, quien navega con teclado se queda en el body
   * sin saber que ha pasado nada.
   */
  it('mueve el foco a la confirmación y lo devuelve al cancelar', async () => {
    const { fixture } = await montar([publicacion({ status: 'PUBLISHED' })]);

    boton(fixture, 'Archivar')?.click();
    await bombear(fixture);

    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('.mias__confirmar'));

    boton(fixture, 'No, dejarla')?.click();
    await bombear(fixture);

    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('.mias__archivar'));
  });

  /** RN-020: la marca es para el moderador; al vendedor se le dice que la mire. */
  it('avisa cuando una publicación necesita atención', async () => {
    const { fixture } = await montar([
      publicacion({ requiresAttention: true, attentionReasons: ['PRICE_OUT_OF_RANGE'] }),
    ]);

    expect(fixture.nativeElement.textContent).toContain('Necesita atención');
  });
});
