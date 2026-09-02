import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  apiUrlInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { FavoritesPage } from './favorites-page';

/**
 * La lista propia de favoritos. HU-011, criterios 11 a 16.
 *
 * <p>La sesión se abre después de crear el componente, nunca antes: en una carga de página
 * el componente nace primero y la sesión llega luego, por la cookie de refresco.
 */
describe('FavoritesPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const publicacion = (id: string, titulo: string) => ({
    id,
    sellerId: '01a04385-47b7-79c7-b3f2-62c03a8d4a99',
    publishedAt: '2026-08-27T15:00:00Z',
    images: [
      { id: `toma-${id}`, kind: 'SELLER_SHOT', position: 0, angleDegrees: 0, url: '/toma.jpg' },
    ],
    product: {
      categoryId: 'camisas',
      title: titulo,
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
  });

  const sesion: Session = {
    accessToken: 'un-token',
    user: {
      email: 'ana@correo.co',
      displayName: 'Ana María',
      emailVerified: true,
      roles: ['BUYER'],
    },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(
          withInterceptors([apiUrlInterceptor, languageInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
  });

  const bombear = async (fixture: ComponentFixture<FavoritesPage>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const montar = async (conSesion: boolean) => {
    const fixture = TestBed.createComponent(FavoritesPage);
    const backend = TestBed.inject(HttpTestingController);
    const almacen = TestBed.inject(SessionStore);

    fixture.detectChanges();
    await bombear(fixture);

    if (conSesion) {
      almacen.set(sesion);
    } else {
      almacen.clear();
    }
    await bombear(fixture);

    return { fixture, backend };
  };

  const responder = async (
    fixture: ComponentFixture<FavoritesPage>,
    backend: HttpTestingController,
    cuerpo: object,
  ) => {
    backend.expectOne((llamada) => llamada.url === `${API}/users/me/favorites`).flush(cuerpo);
    await bombear(fixture);
  };

  /** Criterio 11: se ven, y en el orden en que llegan del servidor. */
  it('enseña los favoritos que llegan', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, {
      items: [publicacion('uno', 'Camisa de lino'), publicacion('dos', 'Jeans rectos')],
      nextCursor: null,
      hasMore: false,
    });

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Camisa de lino');
    expect(texto).toContain('Jeans rectos');
  });

  /** Criterio 15: el vacío es una pantalla, no una línea. Explica y lleva al catálogo. */
  it('enseña el estado vacío con su explicación y su salida', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, { items: [], nextCursor: null, hasMore: false });

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Todavía no has guardado nada');
    expect(texto).toContain('Solo tú ves esta lista');

    const salida = fixture.nativeElement.querySelector('a[href="/catalogo"]');
    expect(salida).not.toBeNull();
  });

  /** Criterio 16: sin sesión no se ve la lista de nadie. Se explica y se ofrece entrar. */
  it('no enseña ninguna lista sin sesión y ofrece entrar', async () => {
    const { fixture, backend } = await montar(false);

    expect(fixture.nativeElement.textContent).toContain('Entra para verlos');
    expect(fixture.nativeElement.querySelector('a[href="/ingresar"]')).not.toBeNull();
    backend.expectNone((llamada) => llamada.url === `${API}/users/me/favorites`);
  });

  /**
   * Mientras la sesión no está resuelta se enseña el esqueleto, no la invitación a entrar.
   *
   * <p>Es la diferencia entre «todavía no sé» y «no hay nadie»: sin ella, quien recarga su
   * lista ve un instante «entra para verlos» antes de sus propios favoritos.
   */
  it('no ofrece entrar mientras la sesión no está resuelta', async () => {
    const fixture = TestBed.createComponent(FavoritesPage);
    fixture.detectChanges();
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).not.toContain('Entra para verlos');
    expect(fixture.nativeElement.querySelector('.esqueleto')).not.toBeNull();
  });

  it('ofrece reintentar cuando la lista falla', async () => {
    const { fixture, backend } = await montar(true);

    backend
      .expectOne((llamada) => llamada.url === `${API}/users/me/favorites`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar tus favoritos');
    expect(fixture.nativeElement.querySelector('[role="alert"] button')).not.toBeNull();
  });

  /** Criterio 12: «ver más» solo cuando de verdad hay más, y manda el cursor. */
  it('pide el siguiente tramo con el cursor que le dieron', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, {
      items: [publicacion('uno', 'Camisa de lino')],
      nextCursor: 'el-cursor',
      hasMore: true,
    });

    const mas = fixture.nativeElement.querySelector('.favoritos__mas button') as HTMLButtonElement;
    expect(mas).not.toBeNull();

    mas.click();
    await bombear(fixture);

    const siguiente = backend.expectOne(
      (llamada) =>
        llamada.url === `${API}/users/me/favorites` && llamada.params.get('cursor') === 'el-cursor',
    );
    siguiente.flush({
      items: [publicacion('dos', 'Jeans rectos')],
      nextCursor: null,
      hasMore: false,
    });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Jeans rectos');
    expect(fixture.nativeElement.querySelector('.favoritos__mas button')).toBeNull();
  });

  /** El último tramo no ofrece «ver más»: un botón que no lleva a nada es peor que ninguno. */
  it('no ofrece ver más cuando no hay más', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, {
      items: [publicacion('uno', 'Camisa de lino')],
      nextCursor: null,
      hasMore: false,
    });

    expect(fixture.nativeElement.querySelector('.favoritos__mas button')).toBeNull();
  });
});
