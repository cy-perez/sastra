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

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const publicacion = (cambios: Record<string, unknown> = {}) => ({
    id: 'af8b9a52-4a3f-4a52-9a1e-8d9a2f1c4b70',
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

  const montar = async (items: object[]) => {
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
    TestBed.inject(SessionStore).set(SESION);
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

  it('ofrece pausar una publicada y reactivar una pausada', async () => {
    const { fixture } = await montar([publicacion({ status: 'PUBLISHED' })]);
    expect(boton(fixture, 'Pausar')).toBeDefined();
    expect(boton(fixture, 'Reactivar')).toBeUndefined();
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
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Archivar es para siempre');
    backend.verify();

    boton(fixture, 'Sí, archivar')?.click();
    await fixture.whenStable();

    backend.expectOne(
      (peticion) => peticion.method === 'POST' && peticion.url.endsWith('/archival'),
    );
  });

  /** RN-020: la marca es para el moderador; al vendedor se le dice que la mire. */
  it('avisa cuando una publicación necesita atención', async () => {
    const { fixture } = await montar([
      publicacion({ requiresAttention: true, attentionReasons: ['PRICE_OUT_OF_RANGE'] }),
    ]);

    expect(fixture.nativeElement.textContent).toContain('Necesita atención');
  });
});
