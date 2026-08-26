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

  const montar = async (items: object[]) => {
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

    TestBed.inject(HttpTestingController)
      .expectOne(
        (llamada) => llamada.method === 'GET' && llamada.url === `${API}/users/me/listings`,
      )
      .flush({ items: [publicacion()], page: 0, size: 20 });
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Camisa de lino');
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
