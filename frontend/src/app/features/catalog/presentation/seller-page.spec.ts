import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { BehaviorSubject } from 'rxjs';

import {
  apiUrlInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import { SellerPage } from './seller-page';

/**
 * El perfil público del vendedor. HU-009, criterios 18 a 20.
 */
describe('SellerPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const VENDEDOR = '01a04385-47b7-79c7-b3f2-62c03a8d4a99';

  const publicacion = (id: string, titulo: string) => ({
    id,
    sellerId: VENDEDOR,
    publishedAt: '2026-08-27T15:00:00Z',
    images: [
      { id: `${id}-0`, kind: 'SELLER_SHOT', position: 0, angleDegrees: 0, url: `/${id}.jpg` },
    ],
    product: {
      categoryId: 'camisas',
      title: titulo,
      description: 'Usada dos veces.',
      brand: null,
      condition: 'LIKE_NEW',
      size: { system: 'ALPHA', value: 'M' },
      measurements: {},
      color: 'BEIGE',
      price: { amount: 185000, currency: 'COP' },
      shipping: null,
      isSealed: null,
      warrantyMonths: null,
    },
  });

  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(() => {
    parametros = new BehaviorSubject(convertToParamMap({ id: VENDEDOR }));

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: parametros.asObservable() } },
        provideHttpClient(
          withInterceptors([apiUrlInterceptor, languageInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
  });

  const bombear = async (fixture: ComponentFixture<SellerPage>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const montar = async () => {
    const fixture = TestBed.createComponent(SellerPage);
    const backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await bombear(fixture);
    return { fixture, backend };
  };

  /** Criterio 18: quién es y qué vende. */
  it('muestra el nombre, la insignia y sus publicaciones', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}`)
      .flush({ id: VENDEDOR, name: 'Ana María', avatarUrl: null, verified: true });
    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}/listings`)
      .flush({
        items: [publicacion('uno', 'Camisa de lino')],
        nextCursor: null,
        hasMore: false,
      });
    await bombear(fixture);

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Ana María');
    expect(texto).toContain('Camisa de lino');
    expect(fixture.nativeElement.querySelector('.insignia-verificado')).not.toBeNull();
    expect(texto).toContain('Sendik confirmó su identidad y su cuenta bancaria');
  });

  /**
   * Criterio 19: no sale nada personal.
   *
   * <p>Se comprueba de la única forma que tiene sentido: la respuesta no trae esos campos,
   * así que aunque el servidor los mandara la pantalla no tendría dónde pintarlos. Esta
   * prueba fija que no se agreguen.
   */
  it('no muestra datos personales del vendedor, criterio 19', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}`)
      .flush({
        id: VENDEDOR,
        name: 'Ana María',
        avatarUrl: null,
        verified: true,
        // Lo que el servidor no manda, pero por si algún día lo mandara.
        email: 'ana@example.test',
        documentNumber: '1234567890',
      });
    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}/listings`)
      .flush({ items: [], nextCursor: null, hasMore: false });
    await bombear(fixture);

    const texto = fixture.nativeElement.textContent;
    expect(texto).not.toContain('ana@example.test');
    expect(texto).not.toContain('1234567890');
  });

  /** Criterio 20: sin nada publicado se dice, y no es un error. */
  it('dice que no tiene nada publicado en vez de fallar, criterio 20', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}`)
      .flush({ id: VENDEDOR, name: 'Ana María', avatarUrl: null, verified: false });
    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}/listings`)
      .flush({ items: [], nextCursor: null, hasMore: false });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('no tiene nada publicado');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  /** Criterio 19: no existe, no es de nadie y cuenta cerrada responden lo mismo. */
  it('dice que no encontró al vendedor cuando la API responde 404', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}`)
      .flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}/listings`)
      .flush({ items: [], nextCursor: null, hasMore: false });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('No encontramos a este vendedor');
  });
});
